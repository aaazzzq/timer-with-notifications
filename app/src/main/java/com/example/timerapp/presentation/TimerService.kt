package com.example.timerapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.timerapp.R // Assuming you have a main R file
import com.example.timerapp.presentation.CueType
import com.example.timerapp.presentation.MainActivity
import com.example.timerapp.presentation.NotificationCue
import com.example.timerapp.presentation.TimerPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

// TimerServiceConstants.kt (New File or inside TimerService)
object TimerServiceConstants {
    const val ACTION_START = "com.example.timerapp.ACTION_START"
    const val ACTION_PAUSE = "com.example.timerapp.ACTION_PAUSE"
    const val ACTION_RESUME = "com.example.timerapp.ACTION_RESUME"
    const val ACTION_CANCEL = "com.example.timerapp.ACTION_CANCEL"
    const val ACTION_UPDATE_NOTIFICATION = "com.example.timerapp.ACTION_UPDATE_NOTIFICATION" // For updating time

    const val EXTRA_PRESET = "com.example.timerapp.EXTRA_PRESET"
    const val EXTRA_REMAINING_MILLIS = "com.example.timerapp.EXTRA_REMAINING_MILLIS"

    const val NOTIFICATION_CHANNEL_ID = "timer_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Active Timer"
    const val NOTIFICATION_ID = 1 // Must be > 0
}

class TimerService : Service() {

    private val serviceJob = SupervisorJob()
    // Use Dispatchers.Default for CPU-intensive work or long delays
    // Use Dispatchers.Main for UI-related tasks within service if needed (like ringtone)
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private lateinit var prefs: SharedPreferences
    private var ticker: Job? = null
    private var currentPreset: TimerPreset? = null
    private var endTimeMillis: Long = 0L // Based on SystemClock.elapsedRealtime()
    private var pausedRemainingMillis: Long? = null // Store remaining time when paused

    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null

    // Shared Flow to communicate state back to the ViewModel/UI
    companion object {
        // Holds Preset, Remaining Time, Running State
        data class ServiceState(
            val preset: TimerPreset,
            val millisRemaining: Long,
            val isRunning: Boolean
        )
        private val _serviceStateFlow = MutableStateFlow<ServiceState?>(null)
        val serviceStateFlow: StateFlow<ServiceState?> = _serviceStateFlow.asStateFlow()

        // Constants for SharedPreferences persistence within the service
        private const val KEY_SERVICE_END_TIME = "service_end_time"
        private const val KEY_SERVICE_PAUSED_REMAINING = "service_paused_remaining"
        private const val KEY_SERVICE_ACTIVE_PRESET = "service_active_preset" // Store the whole preset JSON

        // Helper to check if service should be running based on persisted state
        fun shouldBeRunning(context: Context): Boolean {
            val prefs = context.getSharedPreferences("timer_service_state", Context.MODE_PRIVATE)
            return prefs.contains(KEY_SERVICE_ACTIVE_PRESET)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TimerService", "onCreate")
        prefs = getSharedPreferences("timer_service_state", Context.MODE_PRIVATE)
        vibrator = getSystemService(Vibrator::class.java)
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // Ensure ringtone is obtained on the Main thread if required by underlying impl
        serviceScope.launch(Dispatchers.Main) {
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
        }
        createNotificationChannel()
        restoreState() // Try to restore if service was killed
    }

    // Restore state from SharedPreferences if service restarts
    private fun restoreState() {
        if (prefs.contains(KEY_SERVICE_ACTIVE_PRESET)) {
            try {
                val presetJson = prefs.getString(KEY_SERVICE_ACTIVE_PRESET, null) ?: return
                currentPreset = Json.decodeFromString(presetJson)
                endTimeMillis = prefs.getLong(KEY_SERVICE_END_TIME, 0L)
                pausedRemainingMillis = if (prefs.contains(KEY_SERVICE_PAUSED_REMAINING)) {
                    prefs.getLong(KEY_SERVICE_PAUSED_REMAINING, 0L)
                } else null

                Log.d("TimerService", "Restored state: Preset=${currentPreset?.id}, EndTime=$endTimeMillis, Paused=$pausedRemainingMillis")


                val state: ServiceState? = currentPreset?.let { preset ->
                    if (pausedRemainingMillis != null) {
                        ServiceState(preset, pausedRemainingMillis!!, false)
                    } else if (endTimeMillis > SystemClock.elapsedRealtime()) {
                        val remaining = endTimeMillis - SystemClock.elapsedRealtime()
                        ServiceState(preset, remaining, true)
                    } else {
                        Log.d("TimerService", "Timer finished while service was down.")
                        clearPersistedState()
                        null // Timer finished
                    }
                }

                _serviceStateFlow.value = state

                if (state != null) {
                    // ***** FIX: Use qualified constant *****
                    startForeground(TimerServiceConstants.NOTIFICATION_ID, createNotification(state))
                    if (state.isRunning) {
                        scheduleTicker() // Only schedule if it was running
                    }
                }

            } catch (e: Exception) {
                Log.e("TimerService", "Error restoring state", e)
                clearPersistedState()
                _serviceStateFlow.value = null
            }
        } else {
            Log.d("TimerService", "No previous state found to restore.")
            _serviceStateFlow.value = null
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TimerService", "onStartCommand: Action=${intent?.action}")
        val action = intent?.action ?: return START_NOT_STICKY // Should not happen

        when (action) {
            TimerServiceConstants.ACTION_START -> {
                val presetJson = intent.getStringExtra(TimerServiceConstants.EXTRA_PRESET)
                if (presetJson != null) {
                    try {
                        val preset: TimerPreset = Json.decodeFromString(presetJson)
                        handleStart(preset)
                    } catch (e: Exception) {
                        Log.e("TimerService", "Failed to decode preset", e)
                        stopSelf() // Stop if invalid preset received
                        return START_NOT_STICKY
                    }
                } else {
                    Log.w("TimerService", "Start action received without Preset data")
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            TimerServiceConstants.ACTION_PAUSE -> handlePause()
            TimerServiceConstants.ACTION_RESUME -> handleResume()
            TimerServiceConstants.ACTION_CANCEL -> handleCancel()
            TimerServiceConstants.ACTION_UPDATE_NOTIFICATION -> {
                _serviceStateFlow.value?.let {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    // ***** FIX: Use qualified constant *****
                    notificationManager.notify(TimerServiceConstants.NOTIFICATION_ID, createNotification(it))
                }
            }
        }

        // If the service is killed, try to restart it and restore state
        return START_STICKY
    }

    private fun handleStart(preset: TimerPreset) {
        Log.d("TimerService", "Handling START for Preset ID: ${preset.id}")
        currentPreset = preset
        endTimeMillis = SystemClock.elapsedRealtime() + preset.durationMillis
        pausedRemainingMillis = null // Ensure not paused

        persistState()

        val initialState = ServiceState(preset, preset.durationMillis, true)
        _serviceStateFlow.value = initialState
        startForeground(TimerServiceConstants.NOTIFICATION_ID, createNotification(initialState))
        scheduleTicker()
    }

    private fun handlePause() {
        Log.d("TimerService", "Handling PAUSE")
        ticker?.cancel()
        ticker = null
        val remaining = endTimeMillis - SystemClock.elapsedRealtime()
        if (remaining > 0 && currentPreset != null) {
            pausedRemainingMillis = remaining
            persistState() // Save paused state
            val newState = ServiceState(currentPreset!!, remaining, false)
            _serviceStateFlow.value = newState
            updateNotification(newState)
        } else {
            // Timer likely finished or no preset, cancel instead
            handleCancel()
        }
    }

    private fun handleResume() {
        Log.d("TimerService", "Handling RESUME")
        if (currentPreset != null && pausedRemainingMillis != null && pausedRemainingMillis!! > 0) {
            endTimeMillis = SystemClock.elapsedRealtime() + pausedRemainingMillis!!
            pausedRemainingMillis = null // Clear paused state
            persistState() // Save resumed state (new end time, no paused time)
            val newState = ServiceState(currentPreset!!, endTimeMillis - SystemClock.elapsedRealtime(), true)
            _serviceStateFlow.value = newState
            updateNotification(newState) // Update notification first
            scheduleTicker() // Then schedule ticker
        } else {
            // Cannot resume, maybe timer finished or wasn't paused
            Log.w("TimerService", "Cannot resume, state invalid. CurrentPreset=$currentPreset, PausedMillis=$pausedRemainingMillis")
            // Optionally handle error or just ignore
        }
    }

    private fun handleCancel() {
        Log.d("TimerService", "Handling CANCEL")
        ticker?.cancel()
        ticker = null
        currentPreset = null
        endTimeMillis = 0L
        pausedRemainingMillis = null
        clearPersistedState()
        _serviceStateFlow.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun persistState() {
        if (currentPreset == null) {
            clearPersistedState()
            return
        }
        try {
            val presetJson = Json.encodeToString(currentPreset!!)
            prefs.edit().apply {
                putString(KEY_SERVICE_ACTIVE_PRESET, presetJson)
                if (pausedRemainingMillis != null) {
                    // Timer is Paused
                    putLong(KEY_SERVICE_PAUSED_REMAINING, pausedRemainingMillis!!)
                    remove(KEY_SERVICE_END_TIME) // Remove end time when paused
                } else {
                    // Timer is Running
                    putLong(KEY_SERVICE_END_TIME, endTimeMillis)
                    remove(KEY_SERVICE_PAUSED_REMAINING) // Remove paused time when running
                }
                apply()
            }
            Log.d("TimerService", "Persisted state: Preset=${currentPreset?.id}, EndTime=$endTimeMillis, Paused=$pausedRemainingMillis")
        } catch (e: Exception) {
            Log.e("TimerService", "Error persisting state", e)
        }
    }

    private fun clearPersistedState() {
        Log.d("TimerService", "Clearing persisted state")
        prefs.edit().clear().apply()
    }

    /* ---------- Cue Executor Actor (Now inside Service) ---------- */
    private val cueActor = serviceScope.actor<NotificationCue>(capacity = Channel.UNLIMITED) {
        for (cue in channel) {
            executeCue(cue)
        }
    }

    // Make sure this runs on the Main thread if ringtone interaction needs it
    private suspend fun executeCue(cue: NotificationCue) = withContext(Dispatchers.Main) {
        val repeats = cue.repeats.coerceIn(1, 10)
        Log.d("TimerService", "Executing Cue: Type=${cue.type}, Repeats=$repeats")
        repeat(repeats) {
            try {
                when (cue.type) {
                    CueType.SOUND -> {
                        ringtone?.stop() // Stop previous play just in case
                        ringtone?.play()
                    }
                    CueType.VIBRATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(300)
                        }
                    }
                    CueType.BOTH -> {
                        ringtone?.stop()
                        ringtone?.play()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(300)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TimerService", "Error executing cue", e)
            }
            // Short delay between repeats if repeats > 1
            if (repeats > 1) delay(400)
        }
    }


    private fun scheduleTicker() {
        ticker?.cancel() // Cancel any existing ticker
        if (currentPreset == null || pausedRemainingMillis != null) {
            Log.d("TimerService", "Not scheduling ticker: No preset or timer is paused.")
            return // Don't schedule if no preset or paused
        }
        Log.d("TimerService", "Scheduling ticker...")

        ticker = serviceScope.launch {
            val firedOffsets = mutableSetOf<Long>()
            val initialPresetDuration = currentPreset!!.durationMillis
            val initialStartTime = SystemClock.elapsedRealtime()

            while (isActive && currentPreset != null && pausedRemainingMillis == null) {
                val now = SystemClock.elapsedRealtime()
                val remaining = endTimeMillis - now
                val elapsedSinceTickerStart = now - initialStartTime // How long this specific ticker has run
                val elapsedTotal = initialPresetDuration - remaining // How long the timer has run overall

                // Calculate the elapsed time *just before* this tick for accurate cue detection
                // Use the previous state's remaining time or estimate based on last tick
                val prevRemaining = _serviceStateFlow.value?.millisRemaining ?: (remaining + 1000) // Estimate if no prev state
                val prevElapsedTotal = initialPresetDuration - prevRemaining


                // Log remaining time periodically
                // Log.d("TimerServiceTicker", "Tick: Remaining=${remaining / 1000}s, Elapsed=${elapsedTotal / 1000}s, PrevElapsed=${prevElapsedTotal/1000}s")


                // Update state flow immediately for UI responsiveness
                if (remaining >= 0) {
                    _serviceStateFlow.value = ServiceState(currentPreset!!, remaining, true)
                    // Optionally send intent to update notification time less frequently
                    // if (remaining % 5000 < 1000) { // Update every 5 seconds approx
                    //    sendBroadcast(Intent(this@TimerService, TimeUpdateReceiver::class.java).apply {
                    //        putExtra(TimerServiceConstants.EXTRA_REMAINING_MILLIS, remaining)
                    //    })
                    // }
                    // Or update notification directly (might be resource intensive)
                    // updateNotification(_serviceStateFlow.value!!) // This can be too frequent
                }


                // Check for cues based on total elapsed time
                currentPreset!!.cues.forEach { cue ->
                    if (cue.offsetMillis !in firedOffsets &&
                        prevElapsedTotal < cue.offsetMillis && // Must have passed the threshold *since* the last check
                        elapsedTotal >= cue.offsetMillis) {

                        Log.d("TimerService", "Firing cue for offset: ${cue.offsetMillis}")
                        firedOffsets += cue.offsetMillis
                        cueActor.trySend(cue) // Send cue to actor for execution
                    }
                }

                // Check for timer completion
                if (remaining <= 0) {
                    Log.d("TimerService", "Timer finished.")
                    _serviceStateFlow.value = ServiceState(currentPreset!!, 0L, false) // Update state first
                    updateNotification(_serviceStateFlow.value!!) // Show 00:00 in notification

                    // Send final completion cue
                    cueActor.trySend(NotificationCue(0L, CueType.BOTH, repeats = 3))

                    // Wait a moment for the final cue to potentially finish, then stop
                    delay(1500) // Give cues time to play out
                    handleCancel() // Clean up and stop service
                    break // Exit loop
                }

                // Calculate delay until the next second boundary for more accuracy
                val delayMillis = 1000 - (SystemClock.elapsedRealtime() % 1000)
                delay(delayMillis)
                // delay(1000) // Simple 1-second delay
            }
            Log.d("TimerService", "Ticker loop finished.")
        }
    }


    private fun createNotification(state: ServiceState): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rem = state.millisRemaining
        val mm = (rem / 60000).toInt().coerceAtLeast(0)
        val ss = (rem % 60000 / 1000).toInt().coerceAtLeast(0)
        val timeString = "%02d:%02d".format(mm, ss)
        val title = state.preset.label

        // Pause/Resume Action
        val pauseResumeIntent = Intent(this, TimerService::class.java).apply {
            action = if (state.isRunning) TimerServiceConstants.ACTION_PAUSE else TimerServiceConstants.ACTION_RESUME
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseResumeActionText = if (state.isRunning) "Pause" else "Resume"
        // Use standard Wear icons if available, otherwise use placeholder drawables
        val pauseResumeIcon = if (state.isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        // Cancel Action
        val cancelIntent = Intent(this, TimerService::class.java).apply {
            action = TimerServiceConstants.ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 2, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT // Ensure cancel works
        )
        val cancelIcon = android.R.drawable.ic_menu_close_clear_cancel

        // ***** FIX: Use qualified constant for channel ID *****
        // ***** FIX: Use standard mipmap icon *****
        return NotificationCompat.Builder(this, TimerServiceConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(timeString)
            // ***** FIX: Use R.mipmap.ic_launcher (or your actual launcher icon) *****
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent) // This should now resolve correctly
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(NotificationCompat.Action(pauseResumeIcon, pauseResumeActionText, pauseResumePendingIntent))
            .addAction(NotificationCompat.Action(cancelIcon, "Cancel", cancelPendingIntent))
            .extend(NotificationCompat.WearableExtender()
                .setContentAction(0)
                .setHintHideIcon(true))
            .build()
    }

    private fun updateNotification(state: ServiceState) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // ***** FIX: Use qualified constant *****
        notificationManager.notify(TimerServiceConstants.NOTIFICATION_ID, createNotification(state))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // ***** FIX: Use qualified constants *****
            val channel = NotificationChannel(
                TimerServiceConstants.NOTIFICATION_CHANNEL_ID,
                TimerServiceConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Use LOW to avoid sound *for the notification itself*
            ).apply {
                description = "Displays the active timer"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d("TimerService", "Notification channel created.")
        }
    }

    override fun onDestroy() {
        Log.d("TimerService", "onDestroy")
        serviceJob.cancel() // Cancel all coroutines
        // Release resources if needed (e.g., ringtone if not managed by system)
        // ringtone?.stop() // May not be necessary depending on how it's used
        clearPersistedState() // Optional: clear state on clean shutdown
        _serviceStateFlow.value = null // Clear shared state
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
}
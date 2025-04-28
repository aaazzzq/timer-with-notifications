package com.example.timerapp.presentation

import android.app.Application
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update // Use update for cleaner state changes
import kotlinx.serialization.Serializable // Needed for data classes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File // For file-based storage example


// ---------------------------------------------------------------------------

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    /* ---------- Preset Storage (Using internal file for slightly better robustness) ---------- */
    private val storageFile = File(app.filesDir, "timer_presets.json")

    private val _presets = MutableStateFlow<List<TimerPreset>>(emptyList())
    val presets: StateFlow<List<TimerPreset>> = _presets.asStateFlow()

    init {
        loadPresets()
    }

    private fun loadPresets() {
        try {
            if (storageFile.exists()) {
                val jsonString = storageFile.readText()
                if (jsonString.isNotBlank()) {
                    _presets.value = Json.decodeFromString<List<TimerPreset>>(jsonString)
                        .sortedBy { it.label.lowercase() } // Keep presets sorted alphabetically
                } else {
                    _presets.value = emptyList()
                }
            } else {
                _presets.value = emptyList()
            }
        } catch (e: Exception) {
            // Handle error (e.g., log, default to empty list)
            println("Error loading presets: ${e.message}")
            _presets.value = emptyList()
        }
    }

    private fun persist() {
        try {
            storageFile.writeText(Json.encodeToString(_presets.value))
        } catch (e: Exception) {
            // Handle error (e.g., log)
            println("Error persisting presets: ${e.message}")
        }
    }

    fun addOrUpdate(p: TimerPreset) {
        // Use update for atomic operation (though maybe overkill here)
        _presets.update { currentList ->
            (currentList.filterNot { it.id == p.id } + p)
                .sortedBy { it.label.lowercase() } // Maintain sort order
        }
        persist()
    }

    fun delete(id: Long) {
        _presets.update { currentList ->
            currentList.filterNot { it.id == id }
            // No need to re-sort after deletion
        }
        persist()
    }

    /* ---------- Active Timer ---------- */

    // Renamed to avoid conflict if ActiveTimerState exists elsewhere
    data class ActiveTimerInternalState(
        val preset: TimerPreset,
        val millisRemaining: Long,
        val isRunning: Boolean = true
    )

    private val _active = MutableStateFlow<ActiveTimerInternalState?>(null)
    val active: StateFlow<ActiveTimerInternalState?> = _active.asStateFlow()

    private var ticker: Job? = null

    fun startTimer(id: Long) {
        // Ensure we don't start if already active with same ID? Or allow restart?
        // Current logic allows restarting same timer.
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        ticker?.cancel() // Cancel any existing ticker first
        _active.value = ActiveTimerInternalState(preset, preset.durationMillis, true) // Start as running
        scheduleTicker()
    }

    fun pauseOrResume() {
        _active.update { currentState ->
            currentState?.copy(isRunning = !currentState.isRunning)
        }
        // Schedule/cancel ticker based on the *new* state
        if (_active.value?.isRunning == true) {
            scheduleTicker()
        } else {
            ticker?.cancel()
        }
    }

    fun cancelTimer() {
        ticker?.cancel()
        _active.value = null
    }

    private fun scheduleTicker() {
        // Prevent multiple tickers if already running
        if (ticker?.isActive == true) return

        ticker = viewModelScope.launch(Dispatchers.Default) {
            // Get system services safely
            val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java)
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            var ringtone: Ringtone? = null // Use nullable Ringtone
            withContext(Dispatchers.Main) { // Get ringtone on Main thread
                if (ringtoneUri != null) {
                    ringtone = RingtoneManager.getRingtone(getApplication(), ringtoneUri)
                }
            }

            try {
                while (isActive) { // Use coroutine scope's isActive
                    delay(1000) // Delay 1 second

                    val state = _active.value // Get current state safely
                    if (state == null || !state.isRunning) {
                        // If state becomes null or paused externally, stop the ticker
                        ticker?.cancel() // Cancel self
                        break
                    }

                    val nextMillis = state.millisRemaining - 1000

                    if (nextMillis <= 0) {
                        // Timer finished
                        withContext(Dispatchers.Main) { // Trigger final cue on Main thread
                            triggerCue(CueType.BOTH, 3, vibrator, ringtone) // Final alert
                        }
                        _active.value = state.copy(millisRemaining = 0, isRunning = false) // Update state to finished
                        ticker?.cancel() // Cancel self
                        break
                    } else {
                        // Check for cues at the *start* time corresponding to nextMillis
                        val currentTimeFromStart = state.preset.durationMillis - nextMillis
                        state.preset.cues
                            .filter { it.offsetMillis == currentTimeFromStart } // Compare offset from start
                            .forEach { cue ->
                                withContext(Dispatchers.Main) { // Trigger cue on Main thread
                                    triggerCue(cue.type, cue.repeats, vibrator, ringtone)
                                }
                            }
                        _active.value = state.copy(millisRemaining = nextMillis) // Update remaining time
                    }
                }
            } finally {
                // Ensure ringtone is stopped if ticker loop exits unexpectedly
                withContext(Dispatchers.Main) {
                    ringtone?.stop()
                }
            }
        }
    }

    // Make triggerCue non-suspending, called from Main context
    private fun triggerCue(
        type: CueType,
        repeats: Int,
        vibrator: Vibrator?,
        ringtone: Ringtone?
    ) {
        // This function should now be called within withContext(Dispatchers.Main)
        viewModelScope.launch { // Launch separate short-lived coroutine for repeats/delays
            repeat(repeats.coerceIn(1, 5)) {
                try {
                    when (type) {
                        CueType.SOUND -> ringtone?.play()
                        CueType.VIBRATION ->
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(
                                    300,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        CueType.BOTH -> {
                            ringtone?.play()
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(
                                    300,
                                    VibrationEffect.DEFAULT_AMPLITUDE
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    println("Error triggering cue: ${e.message}") // Log errors
                }
                delay(400) // Gap between repetitions
            }
        }
    }
}
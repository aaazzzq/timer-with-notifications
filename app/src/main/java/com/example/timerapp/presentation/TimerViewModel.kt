package com.example.timerapp.presentation
import android.util.Log

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
            var ringtone: Ringtone? = null
            withContext(Dispatchers.Main) {
                ringtone = RingtoneManager.getRingtone(getApplication(), ringtoneUri)
            }

            // Track which cue offsets we've already fired
            val firedOffsets = mutableSetOf<Long>()

            try {
                while (isActive) {
                    delay(1000)
                    val state = _active.value ?: break

                    // Calculate the “from start” times before and after this tick
                    val prevFromStart = state.preset.durationMillis - state.millisRemaining
                    val nextMillis = state.millisRemaining - 1000
                    val currFromStart = state.preset.durationMillis - nextMillis

                    // Debug log: show window and all cue offsets
                    Log.d("TimerViewModel", "Window: $prevFromStart → $currFromStart   cues=${state.preset.cues.map { it.offsetMillis }}   fired=$firedOffsets")

                    // Fire any cues whose offset falls in this window
                    state.preset.cues.forEach { cue ->
                        if (cue.offsetMillis !in firedOffsets
                            && prevFromStart < cue.offsetMillis
                            && currFromStart >= cue.offsetMillis
                        ) {
                            Log.d("TimerViewModel", "Triggering cue at ${cue.offsetMillis}ms")
                            firedOffsets += cue.offsetMillis
                            withContext(Dispatchers.Main) {
                                triggerCue(cue.type, cue.repeats, vibrator, ringtone)
                            }
                        }
                    }

                    // Update remaining time (and handle completion)
                    if (nextMillis <= 0) {
                        withContext(Dispatchers.Main) {
                            triggerCue(CueType.BOTH, 3, vibrator, ringtone)
                        }
                        _active.value = state.copy(millisRemaining = 0, isRunning = false)
                        ticker?.cancel()
                        break
                    } else {
                        _active.value = state.copy(millisRemaining = nextMillis)
                    }
                }
            } finally {
                // Stop any ringing if we exit unexpectedly
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
        viewModelScope.launch {
            repeat(repeats.coerceIn(1, 10)) {
                try {
                    when (type) {
                        CueType.SOUND -> {
                            // ⚡ Stop any in-flight playback before starting again
                            ringtone?.stop()
                            ringtone?.play()
                        }
                        CueType.VIBRATION ->
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        CueType.BOTH -> {
                            ringtone?.stop()
                            ringtone?.play()
                            vibrator?.vibrate(
                                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TimerViewModel", "Error triggering cue", e)
                }
                // you can shorten this gap if you like,
                // but be sure your tone actually stops first!
                delay(400)
            }
        }
    }
}
package com.example.timerapp.presentation

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("active_timer", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_END_TIME = "end_time"
        const val KEY_ACTIVE_ID = "active_id"
    }

    /* ---------- Preset Storage ---------- */
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
                _presets.value = if (jsonString.isNotBlank()) {
                    Json.decodeFromString<List<TimerPreset>>(jsonString)
                        .sortedBy { it.label.lowercase() }
                } else emptyList()
            }
        } catch (e: Exception) {
            Log.e("TimerViewModel", "Error loading presets", e)
            _presets.value = emptyList()
        }
    }

    private fun persist() {
        try {
            storageFile.writeText(Json.encodeToString(_presets.value))
        } catch (e: Exception) {
            Log.e("TimerViewModel", "Error persisting presets", e)
        }
    }

    fun addOrUpdate(p: TimerPreset) {
        _presets.update { currentList ->
            (currentList.filterNot { it.id == p.id } + p)
                .sortedBy { it.label.lowercase() }
        }
        persist()
    }

    fun delete(id: Long) {
        _presets.update { currentList ->
            currentList.filterNot { it.id == id }
        }
        persist()
    }

    /* ---------- Active Timer ---------- */
    data class ActiveTimerInternalState(
        val preset: TimerPreset,
        val millisRemaining: Long,
        val isRunning: Boolean = true
    )

    private val _active = MutableStateFlow<ActiveTimerInternalState?>(null)
    val active: StateFlow<ActiveTimerInternalState?> = _active.asStateFlow()

    private var ticker: Job? = null

    /* ---------- Cue Executor Actor ---------- */
    private val cueActor = viewModelScope.actor<NotificationCue>(capacity = Channel.UNLIMITED) {
        // Prepare system services once
        val application = getApplication<Application>()
        val vibrator = application.getSystemService(Vibrator::class.java)
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone: Ringtone? = withContext(Dispatchers.Main) {
            RingtoneManager.getRingtone(application, ringtoneUri)
        }

        for (cue in channel) {
            executeCue(cue, vibrator, ringtone)
        }
    }

    private suspend fun executeCue(
        cue: NotificationCue,
        vibrator: Vibrator?,
        ringtone: Ringtone?
    ) = withContext(Dispatchers.Main) {
        repeat(cue.repeats.coerceIn(1, 10)) {
            try {
                when (cue.type) {
                    CueType.SOUND -> {
                        ringtone?.stop()
                        ringtone?.play()
                    }
                    CueType.VIBRATION -> {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(
                                300,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }
                    CueType.BOTH -> {
                        ringtone?.stop()
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
                Log.e("TimerViewModel", "Error executing cue", e)
            }
            delay(400)
        }
    }

    fun startTimer(id: Long) {
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        val endTime = SystemClock.elapsedRealtime() + preset.durationMillis
        prefs.edit()
            .putLong(KEY_END_TIME, endTime)
            .putLong(KEY_ACTIVE_ID, id)
            .apply()

        _active.value = ActiveTimerInternalState(preset, preset.durationMillis, true)
        scheduleTicker()
    }

    fun resumeIfNeeded() {
        val endTime = prefs.getLong(KEY_END_TIME, 0L)
        val id = prefs.getLong(KEY_ACTIVE_ID, -1L)
        if (endTime > SystemClock.elapsedRealtime() && id != -1L) {
            val preset = _presets.value.firstOrNull { it.id == id } ?: return
            val remaining = endTime - SystemClock.elapsedRealtime()
            _active.value = ActiveTimerInternalState(preset, remaining, true)
            scheduleTicker()
        }
    }

    fun pauseOrResume() {
        val state = _active.value ?: return
        if (state.isRunning) {
            // Pause
            ticker?.cancel()
            prefs.edit().remove(KEY_END_TIME).apply()
            _active.value = state.copy(isRunning = false)
        } else {
            // Resume
            val endTime = SystemClock.elapsedRealtime() + state.millisRemaining
            prefs.edit()
                .putLong(KEY_END_TIME, endTime)
                .putLong(KEY_ACTIVE_ID, state.preset.id)
                .apply()
            _active.value = state.copy(isRunning = true)
            scheduleTicker()
        }
    }

    fun cancelTimer() {
        ticker?.cancel()
        prefs.edit().clear().apply()
        _active.value = null
    }

    private fun scheduleTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch(Dispatchers.Default) {
            val firedOffsets = mutableSetOf<Long>()
            while (isActive) {
                val endTime = prefs.getLong(KEY_END_TIME, 0L)
                val now = SystemClock.elapsedRealtime()
                val remaining = endTime - now
                val state = _active.value ?: break
                val elapsedFromStart = state.preset.durationMillis - remaining
                val prevElapsed = state.preset.durationMillis - state.millisRemaining

                state.preset.cues.forEach { cue ->
                    if (cue.offsetMillis !in firedOffsets &&
                        prevElapsed < cue.offsetMillis &&
                        elapsedFromStart >= cue.offsetMillis) {

                        firedOffsets += cue.offsetMillis
                        cueActor.trySend(cue)
                    }
                }

                if (remaining <= 0) {
                    cueActor.trySend(NotificationCue(0L, CueType.BOTH, repeats = 3))
                    _active.value = state.copy(millisRemaining = 0L, isRunning = false)
                    prefs.edit().clear().apply()
                    break
                } else {
                    _active.value = state.copy(millisRemaining = remaining)
                }
                delay(1000)
            }
        }
    }
}

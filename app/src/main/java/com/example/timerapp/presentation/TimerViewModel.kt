package com.example.timerapp.presentation

import android.app.Application
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    /* ---------- preset storage ---------- */

    private val prefs = app.getSharedPreferences("timer_prefs", 0)

    private val _presets = MutableStateFlow<List<TimerPreset>>(emptyList())
    val presets: StateFlow<List<TimerPreset>> = _presets

    init { loadPresets() }

    private fun loadPresets() {
        prefs.getString("presets", null)?.let {
            _presets.value = Json.decodeFromString(it)
        }
    }

    private fun persist() {
        prefs.edit().putString("presets", Json.encodeToString(_presets.value)).apply()
    }

    fun addOrUpdate(p: TimerPreset) {
        _presets.value = _presets.value.filterNot { it.id == p.id } + p
        persist()
    }

    fun delete(id: Long) {
        _presets.value = _presets.value.filterNot { it.id == id }
        persist()
    }

    /* ---------- active timer ---------- */

    data class ActiveState(
        val preset: TimerPreset,
        val millisRemaining: Long,
        val isRunning: Boolean = true
    )

    private val _active = MutableStateFlow<ActiveState?>(null)
    val active: StateFlow<ActiveState?> = _active

    private var ticker: Job? = null

    fun startTimer(id: Long) {
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        ticker?.cancel()
        _active.value = ActiveState(preset, preset.durationMillis)
        scheduleTicker()
    }

    fun pauseOrResume() {
        _active.value?.let { cur ->
            _active.value = cur.copy(isRunning = !cur.isRunning)
            if (_active.value!!.isRunning) scheduleTicker() else ticker?.cancel()
        }
    }

    fun cancelTimer() { ticker?.cancel(); _active.value = null }

    private fun scheduleTicker() {
        ticker = viewModelScope.launch(Dispatchers.Default) {
            val vibrator = getApplication<Application>()
                .getSystemService(Vibrator::class.java)
            val ringtone = RingtoneManager.getRingtone(
                getApplication(),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )

            while (true) {
                delay(1_000)
                val state = _active.value ?: break
                if (!state.isRunning) continue

                val next = state.millisRemaining - 1_000
                if (next <= 0) {
                    triggerCue(CueType.SOUND, 1, vibrator, ringtone)
                    _active.value = null
                    break
                } else {
                    state.preset.cues
                        .filter { it.offsetMillis == next }
                        .forEach { triggerCue(it.type, it.repeats, vibrator, ringtone) }
                    _active.value = state.copy(millisRemaining = next)
                }
            }
        }
    }

    private suspend fun triggerCue(
        type: CueType,
        repeats: Int,
        vibrator: Vibrator?,
        ringtone: android.media.Ringtone
    ) = withContext(Dispatchers.Main) {
        when (type) {
            CueType.SOUND      -> ringtone.play()
            CueType.VIBRATION  ->
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        300L * repeats,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
        }
    }
}

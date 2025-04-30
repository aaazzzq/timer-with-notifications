package com.example.timerapp.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timerapp.presentation.TimerPreset
import com.example.timerapp.service.TimerService
import com.example.timerapp.service.TimerServiceConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TimerViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext

    // ─── 1) Mirror the service state ───────────────────────────────────────────
    data class ActiveTimerInternalState(
        val preset: TimerPreset,
        val millisRemaining: Long,
        val isRunning: Boolean
    )

    private val _active = MutableStateFlow<ActiveTimerInternalState?>(null)
    val active: StateFlow<ActiveTimerInternalState?> = _active.asStateFlow()

    // ─── 2) Preset storage (unchanged) ────────────────────────────────────────
    private val storageFile = File(app.filesDir, "timer_presets.json")

    private val _presets = MutableStateFlow<List<TimerPreset>>(emptyList())
    val presets: StateFlow<List<TimerPreset>> = _presets.asStateFlow()

    init {
        loadPresets()                  // from your original implementation :contentReference[oaicite:4]{index=4}&#8203;:contentReference[oaicite:5]{index=5}
        observeServiceState()          // now safe: `_active` already initialized
        reAttachIfServiceRunning()     // public helper, so UI can re-attach after process death
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
        _presets.update { current ->
            (current.filterNot { it.id == p.id } + p)
                .sortedBy { it.label.lowercase() }
        }
        persist()
    }

    fun delete(id: Long) {
        _presets.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    // ─── 3) Observe service state ─────────────────────────────────────────────
    private fun observeServiceState() {
        viewModelScope.launch {
            TimerService.serviceStateFlow.collect { s ->
                _active.value = s?.let {
                    ActiveTimerInternalState(it.preset, it.millisRemaining, it.isRunning)
                }
            }
        }
    }

    // ─── 4) Public helper to re-attach after process-death ────────────────────
    fun reAttachIfServiceRunning() {
        if (TimerService.shouldBeRunning(ctx)) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, TimerService::class.java)
            )
        }
    }

    // ─── 5) Translate UI intents into service commands ────────────────────────
    private fun send(action: String, extra: Intent.() -> Unit = {}) {
        Intent(ctx, TimerService::class.java).apply {
            this.action = action
            extra()
            ContextCompat.startForegroundService(ctx, this)
        }
    }

    fun startTimer(presetId: Long) {
        val preset = presets.value.firstOrNull { it.id == presetId } ?: return
        send(TimerServiceConstants.ACTION_START) {
            putExtra(
                TimerServiceConstants.EXTRA_PRESET,
                Json.encodeToString(preset)
            )
        }
    }

    fun pauseOrResume() {
        when (active.value?.isRunning) {
            true  -> send(TimerServiceConstants.ACTION_PAUSE)
            false -> send(TimerServiceConstants.ACTION_RESUME)
            null  -> Unit
        }
    }

    fun cancelTimer() = send(TimerServiceConstants.ACTION_CANCEL)
}

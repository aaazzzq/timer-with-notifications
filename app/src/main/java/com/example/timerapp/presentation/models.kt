package com.example.timerapp.presentation

import kotlinx.serialization.Serializable

@Serializable
data class TimerPreset(
    val id: Long            = System.currentTimeMillis(),
    val label: String       = "Timer",
    val durationMillis: Long,
    val cues: List<NotificationCue>
)

@Serializable
data class NotificationCue(
    /** How long *before* finish (e.g. 60_000 for 1 min) */
    val offsetMillis: Long,
    val type: CueType,
    val repeats: Int        = 1          // 1-5
)

enum class CueType { SOUND, VIBRATION, BOTH }

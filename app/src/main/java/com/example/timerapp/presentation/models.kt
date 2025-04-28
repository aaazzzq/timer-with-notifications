package com.example.timerapp.presentation

import kotlinx.serialization.Serializable

@Serializable
data class TimerPreset(
    val id: Long = System.currentTimeMillis(),
    val label: String = "Timer",
    val durationMillis: Long,
    val cues: List<NotificationCue>
)

@Serializable
data class NotificationCue(
    val offsetMillis: Long,   // how long *before* finish (e.g. 60_000 for 1 min)
    val type: CueType,
    val repeats: Int = 1      // for vibration pattern
)

enum class CueType { SOUND, VIBRATION }

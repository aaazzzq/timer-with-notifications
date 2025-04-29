package com.example.timerapp.presentation // Or your data package

import kotlinx.serialization.Serializable

@Serializable
data class TimerPreset(
    val id: Long            = System.currentTimeMillis(),
    val label: String       = "Timer",
    val durationMillis: Long,
    // Cues should ideally be stored sorted by offsetMillis for predictability
    val cues: List<NotificationCue>
)

@Serializable
data class NotificationCue(
    /** Milliseconds *from the start* of the timer when this cue should trigger. */ // <<< UPDATED COMMENT
    val offsetMillis: Long,
    val type: CueType,
    val repeats: Int        = 1          // 1-5
)

@Serializable // Add if you plan to maybe serialize CueType directly elsewhere
enum class CueType { SOUND, VIBRATION, BOTH }

    
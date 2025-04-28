package com.example.timerapp.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*   // Chip, Button, Card, Picker, etc.

/* ---------- HomeScreen ---------- */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: TimerViewModel,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit
) {
    val presets by vm.presets.collectAsState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(VignettePosition.TopAndBottom) }
    ) {
        if (presets.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No timers yet", textAlign = TextAlign.Center)
            }
        } else {
            ScalingLazyColumn {
                items(presets, key = { it.id }) { preset ->
                    Card(
                        onClick = { onStart(preset.id) },
                        modifier = Modifier.combinedClickable(
                            onClick     = { onStart(preset.id) },
                            onLongClick = { onEdit(preset.id) }
                        )
                    ) {
                        Text("${preset.label}: ${preset.durationMillis / 60000} min")
                    }
                }
            }
        }

        /* Bottom-anchored action chip */
        Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
            Chip(
                label       = { Text("New") },
                onClick     = onCreate,
                icon        = { Icon(Icons.Filled.Add, contentDescription = null) },
                colors      = ChipDefaults.primaryChipColors()
            )
        }
    }
}

/* ---------- EditTimerScreen ---------- */
@Composable
fun EditTimerScreen(vm: TimerViewModel, presetId: Long?, onDone: () -> Unit) {
    // Use mutableIntStateOf for primitive types like Int - addresses the lint warning
    var minutes by remember { mutableIntStateOf(1) }
    var label   by remember { mutableStateOf("") }
    var cues    by remember { mutableStateOf(listOf<NotificationCue>()) }

    LaunchedEffect(presetId) {
        vm.presets.value.firstOrNull { it.id == presetId }?.let { p ->
            label   = p.label
            minutes = (p.durationMillis / 60000).toInt()
            cues    = p.cues
        }
    }

    // Define the range of options for the picker
    val minuteOptions = remember { (1..120).toList() }

    ScalingLazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize() // Added for better layout often
    ) {
        /* duration picker – Corrected API usage */
        item {
            Spacer(Modifier.height(16.dp)) // Add some spacing
            Text("Duration (minutes)")
            Spacer(Modifier.height(8.dp)) // Add some spacing

            // Calculate the initial index based on the current 'minutes' value
            // Ensure the index is valid even if 'minutes' is somehow outside 1-120
            val initialIndex = (minutes - 1).coerceIn(0, minuteOptions.size - 1)

            val pickerState = rememberPickerState(
                initialNumberOfOptions = minuteOptions.size,
                initiallySelectedOption = initialIndex
            )

            // Update the 'minutes' state when the picker selection changes
            LaunchedEffect(pickerState.selectedOption) {
                // selectedOption is the INDEX, add 1 to get the minute value (since range starts at 1)
                minutes = pickerState.selectedOption + 1
            }

            Picker(
                state = pickerState,
                modifier = Modifier.height(100.dp) // Give the picker some explicit height
            ) { optionIndex ->
                // Display the actual minute value (index + 1)
                Text("${optionIndex + 1}")
            }
            Spacer(Modifier.height(16.dp)) // Add some spacing
        }

        // TODO: Add Label TextField item here if needed
        // item { OutlinedTextField(...) }

        /* cue list */
        items(cues.withIndex().toList()) { (idx, cue) ->
            Card(onClick = {
                cues = cues.toMutableList().also { list ->
                    list[idx] = cue.copy(
                        type = if (cue.type == CueType.SOUND) CueType.VIBRATION else CueType.SOUND
                    )
                }
            }) {
                // Provide padding inside the card for better appearance
                Text(
                    "-${cue.offsetMillis / 60000} m · ${cue.type}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp)) // Add space between cue cards
        }

        item {
            Spacer(Modifier.height(8.dp)) // Add space before buttons
            Button(onClick = {
                // Calculate offset based on the *current* duration, not just size+1
                val newOffset = minutes * 60_000L - 60_000L * (cues.size + 1)
                cues += NotificationCue(
                    // Ensure offset is not negative and relative to the end
                    offsetMillis = maxOf(0L, newOffset),
                    type         = CueType.VIBRATION
                )
            }) { Text("Add cue (-1 min)") } // Clarify button action if offset logic is complex
            Spacer(Modifier.height(8.dp)) // Add space between buttons
        }

        item {
            Button(onClick = {
                vm.addOrUpdate(
                    TimerPreset(
                        id             = presetId ?: System.currentTimeMillis(),
                        label          = label.ifBlank { "Timer" },
                        durationMillis = minutes * 60_000L,
                        cues           = cues
                    )
                )
                onDone()
            }) { Text("Save") }
            Spacer(Modifier.height(16.dp)) // Add some bottom padding
        }
    }
}

/* ---------- ActiveTimerScreen ---------- */
@Composable
fun ActiveTimerScreen(vm: TimerViewModel, presetId: Long?, onDone: () -> Unit) {
    val state by vm.active.collectAsState()

    LaunchedEffect(presetId) {
        if (state == null && presetId != null) vm.startTimer(presetId)
    }

    val rem = state?.millisRemaining ?: 0L
    val mm  = (rem / 60000).toInt()
    val ss  = (rem % 60000 / 1000).toInt()

    Scaffold(timeText = { TimeText() }) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("%02d:%02d".format(mm, ss), style = MaterialTheme.typography.title1)
            Row {
                Button(onClick = { vm.pauseOrResume() }) {
                    Text(if (state?.isRunning == true) "Pause" else "Resume")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.cancelTimer(); onDone() }) {
                    Text("Cancel")
                }
            }
        }
    }
}
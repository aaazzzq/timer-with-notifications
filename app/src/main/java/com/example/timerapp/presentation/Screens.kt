package com.example.timerapp.presentation

import android.util.Log
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons // <-- IMPORT ADDED
import androidx.compose.material.icons.filled.Add // <-- IMPORT ADDED
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api // <-- IMPORT ADDED
import androidx.compose.material3.MaterialTheme // Make sure this is androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton // <-- IMPORT ADDED
import androidx.compose.material3.SegmentedButtonDefaults // <-- IMPORT ADDED
import androidx.compose.material3.SingleChoiceSegmentedButtonRow // <-- IMPORT ADDED
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text as M3Text // Keep alias for clarity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.* // Imports Wear Text, Card, Chip, Icon, Picker, etc.
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.wear.compose.material.ButtonDefaults as WearButtonDefaults // Alias Wear Button Defaults
import androidx.wear.compose.material.MaterialTheme as WearMaterialTheme // Alias Wear Theme

/* ---------- HomeScreen ---------- */
@OptIn(ExperimentalFoundationApi::class) // <<< Essential for combinedClickable
@Composable
fun HomeScreen(
    vm: TimerViewModel,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit
) {
    val presets by vm.presets.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (presets.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No timers yet", textAlign = TextAlign.Center) // wear.compose.material.Text
            }
        } else {
            ScalingLazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                itemsIndexed(presets, key = { _, preset -> preset.id }) { _, preset ->
                    // --- CHANGE HERE ---
                    // Card still requires onClick, provide an empty lambda.
                    Card(
                        onClick = { /* Required by Wear Card, but logic is handled by Row now */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                // --- CHANGE HERE ---
                                // Apply combinedClickable to the Row
                                .combinedClickable(
                                    onClick = {
                                        Log.d("HomeScreen", "Row onClick triggered for preset ID: ${preset.id}")
                                        // Call onStart (short tap)
                                        onStart(preset.id)
                                    },
                                    onLongClick = {
                                        Log.d("HomeScreen", "Row onLongClick triggered for preset ID: ${preset.id}")
                                        // Call onEdit (long press)
                                        onEdit(preset.id)
                                    }
                                )
                                .padding(8.dp), // Keep padding on the Row
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.label, Modifier.weight(1f))
                            Text("${preset.durationMillis / 60000} m")
                        }
                    }
                    // --- END CHANGES for Card/Row ---
                }
            }
        }

        Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
            // --- CHIP DELETED ---
            // Chip(
            //     label = { Text("New") },
            //     onClick = onCreate,
            //     icon = { Icon(Icons.Default.Add, contentDescription = null) },
            //     colors = ChipDefaults.primaryChipColors()
            // )

            // --- ROW ADDED ---
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Span the full width
                    .height(48.dp) // Consistent height
                    // Use primary color for background
                    .background(WearMaterialTheme.colors.primary)
                    // Use the onCreate lambda passed into HomeScreen
                    .clickable(onClick = onCreate)
                    // Add internal padding for the content
                    .padding(horizontal = 16.dp),
                // Center the Icon and Text vertically
                verticalAlignment = Alignment.CenterVertically,
                // Center the content horizontally
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Timer", // Provide a meaningful description
                    // Ensure content color contrasts with background
                    tint = WearMaterialTheme.colors.onPrimary
                )
                Spacer(Modifier.width(8.dp)) // Space between Icon and Text
                Text(
                    text = "New",
                    // Ensure content color contrasts with background
                    color = WearMaterialTheme.colors.onPrimary
                )
            }
        }
    }
}

/* ---------- EditTimerScreen ---------- */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditTimerScreen(
    vm: TimerViewModel,
    presetId: Long?,
    onDone: () -> Unit
) {
    Log.d("EditTimerScreen", "Received presetId: $presetId")
    // State for hours and minutes separately
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(1) } // Default to 1 min if new
    var label by remember { mutableStateOf("") }
    var cues by remember { mutableStateOf(listOf<NotificationCue>()) }
    var showDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Load existing preset data
    LaunchedEffect(presetId) {
        // Only load if presetId is not null (i.e., we are editing)
        if (presetId != null) {
            vm.presets.value.firstOrNull { it.id == presetId }?.let { p ->
                label = p.label
                val totalMinutes = p.durationMillis / 60000L
                hours = (totalMinutes / 60).toInt()
                minutes = (totalMinutes % 60).toInt()
                cues = p.cues
            }
        } else {
            // Optionally reset fields if creating new after editing
            hours = 0
            minutes = 1
            label = ""
            cues = emptyList()
        }
    }

    val listState = rememberScalingLazyListState()
    // Options for the pickers
    val hourOptions = remember { (0..23).toList() }
    val minuteOptions = remember { (0..59).toList() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 20.dp) // Increased bottom padding for Chip
        ) {
            // Label field (remains the same)
            item {
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { M3Text("Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(bottom = 12.dp)
                )
            }

            // Duration Pickers (Hours and Minutes)
            item {
                // Wrap Duration Text and Picker Row in a Column for better structure
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Apply the width modifier to this wrapper Column
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    // 1. Duration Text is now inside the wrapper Column
                    Text("Duration")
                    Spacer(Modifier.height(8.dp)) // Space between "Duration" and the pickers

                    // The Row containing the pickers remains largely the same
                    Row(
                        // Remove width modifier from Row, it's on the parent Column now
                        // modifier = Modifier.fillMaxWidth(0.9f) // <<< REMOVE this line
                        modifier = Modifier.height(110.dp), // Keep height
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Hours Picker Column (No structural changes inside)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hours")
                            // ... (rest of Hours Picker code) ...
                            val hourPickerState = rememberPickerState(
                                initialNumberOfOptions = hourOptions.size,
                                initiallySelectedOption = hourOptions.indexOf(hours).coerceAtLeast(0)
                            )
                            LaunchedEffect(hourPickerState.selectedOption) {
                                hours = hourOptions[hourPickerState.selectedOption]
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Picker( state = hourPickerState, modifier = Modifier.fillMaxWidth()) { h -> Text("$h") }
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        // Minutes Picker Column (No structural changes inside)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Minutes")
                            // ... (rest of Minutes Picker code) ...
                            val minutePickerState = rememberPickerState(
                                initialNumberOfOptions = minuteOptions.size,
                                initiallySelectedOption = minuteOptions.indexOf(minutes).coerceAtLeast(0)
                            )
                            LaunchedEffect(minutePickerState.selectedOption) {
                                minutes = minuteOptions[minutePickerState.selectedOption]
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Picker( state = minutePickerState, modifier = Modifier.fillMaxWidth()) { m -> Text("%02d".format(m)) }
                            }
                        }
                    } // End Row
                } // End wrapper Column

                Spacer(Modifier.height(12.dp)) // Space after the picker section
            }


            // Cues list (remains the same)
            itemsIndexed(
                items = cues,
                key = { index, cueItem -> cueItem.hashCode() + index } // Ensure unique key
            ) { idx, cue ->
                Card(
                    onClick = {
                        Log.d("EditTimerScreen", "Card onClick triggered for cue index: $idx")
                        /* future edit */
                    },
                    modifier = Modifier.fillMaxWidth(0.9f)
                    // REMOVE .pointerInput from Card's modifier
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth() // Make Row fill Card
                            .pointerInput(idx, cue) { // Keyed pointerInput on the Row
                                detectTapGestures(
                                    onLongPress = { _ ->
                                        Log.d("EditTimerScreen", "ROW onLongPress triggered for cue index: $idx, cue: $cue") // Log LONG PRESS
                                        cues = cues.toMutableList().apply { removeAt(idx) }
                                    }
                                    // Optional: onTap logging
                                    // onTap = { Log.d("EditTimerScreen", "ROW onTap triggered for cue index: $idx") }
                                )
                            }
                            .padding(8.dp), // Padding on Row
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Calculate offset display based on total duration
                        val totalDurationMillis = (hours * 60L + minutes) * 60000L
                        val displayOffsetMinutes = (totalDurationMillis - cue.offsetMillis) / 60000L
                        Text("-$displayOffsetMinutes m", Modifier.weight(1f))
                        Text("${cue.type} ×${cue.repeats}")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Save button (update duration calculation)
            item {
                Button(
                    onClick = {
                        // Calculate total duration in milliseconds from hours and minutes
                        val totalMillis = (hours * 60L + minutes) * 60_000L
                        // Prevent saving a 0 duration timer
                        if (totalMillis > 0) {
                            vm.addOrUpdate(
                                TimerPreset(
                                    id = presetId ?: System.currentTimeMillis(),
                                    label = label.ifBlank { "Timer ${hours}h ${minutes}m" }, // Updated default label
                                    durationMillis = totalMillis, // Use calculated total duration
                                    cues = cues
                                )
                            )
                            onDone()
                        } else {
                            // Optionally show a message that duration must be > 0
                        }
                    },
                    modifier = Modifier
                        // Keep existing padding if you like the space above
                        .padding(top = 16.dp)
                        // ADD this line to make the button wide
                        .fillMaxWidth(0.9f),                     // Disable save if total duration is zero
                    enabled = (hours > 0 || minutes > 0)
                ) {
                    Text("Save")
                }
            }
            if (presetId != null) {
                Log.d("EditTimerScreen", "presetId ($presetId) is not null, attempting to compose Delete Button item...")
                item {
                    Spacer(Modifier.height(8.dp)) // Keep space ABOVE
                    // Use Wear Button
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = true
                            Log.d("EditTimerScreen", "Delete button clicked, showing dialog")
                        },
                        colors = WearButtonDefaults.buttonColors(
                            backgroundColor = WearMaterialTheme.colors.error,
                            contentColor = WearMaterialTheme.colors.onError
                        ),
                        // REMOVE the modifier parameter from here:
                        modifier = Modifier.fillMaxWidth(0.9f)
                        // modifier = Modifier.padding(bottom = 8.dp) // <<< DELETE THIS LINE
                    ) {
                        Text("Delete")
                    }
                }
            }

        }


        // +Cue chip (Update text)
        Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
            // Determine enabled state first for clarity
            val isActionEnabled = (hours > 0 || minutes > 0)

            Row(
                modifier = Modifier
                    .fillMaxWidth() // Span the full width
                    .height(48.dp) // Set a fixed height (adjust as needed)
                    // Use primary color for background like the Chip did
                    .background(WearMaterialTheme.colors.primary)
                    // Apply click listener and enabled state
                    .clickable(
                        enabled = isActionEnabled,
                        onClick = { showDialog = true }
                    )
                    // Adjust alpha based on enabled state for visual feedback
                    .alpha(if (isActionEnabled) 1f else 0.6f)
                    // Add internal padding for the content
                    .padding(horizontal = 16.dp),
                // Center the Icon and Text vertically within the Row's height
                verticalAlignment = Alignment.CenterVertically,
                // Center the content horizontally within the Row
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Signal", // Add description
                    // Ensure content color contrasts with background
                    tint = WearMaterialTheme.colors.onPrimary
                )
                Spacer(Modifier.width(8.dp)) // Space between Icon and Text
                Text(
                    text = "New Signal",
                    // Ensure content color contrasts with background
                    color = WearMaterialTheme.colors.onPrimary
                )
            }
        }
    }

    // Cue dialog (Update offset logic based on total duration)
    if (showDialog) {
        var offset by remember { mutableIntStateOf(1) } // Offset in minutes before the end
        var repeats by remember { mutableIntStateOf(1) }
        var type by remember { mutableStateOf(CueType.VIBRATION) }

        // Calculate total duration in minutes for the dialog logic
        val totalDurationMinutes = remember(hours, minutes) { hours * 60 + minutes }

        // --- DECLARE offsetOptions HERE, outside the 'text' lambda ---
        val maxOffsetMinutes = remember(totalDurationMinutes) { maxOf(1, totalDurationMinutes - 1) }
        val offsetOptions = remember(maxOffsetMinutes) { (1..maxOffsetMinutes).toList() }
        // -------------------------------------------------------------

        AlertDialog( // material3.AlertDialog
            onDismissRequest = { showDialog = false },
            title = { M3Text("New cue") }, // material3.Text
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Offset picker (max offset depends on total duration)
                    M3Text("When (minutes before end):") // material3.Text

                    // Now just use the offsetOptions declared outside
                    if (offsetOptions.isNotEmpty()) {
                        val offState = rememberPickerState(
                            initialNumberOfOptions = offsetOptions.size,
                            initiallySelectedOption = (offset - 1).coerceIn(0, offsetOptions.size - 1)
                        )
                        // Update offset state when picker changes
                        // Make sure offset actually uses the value from the list index
                        LaunchedEffect(offState.selectedOption) {
                            offset = offsetOptions[offState.selectedOption.coerceIn(0, offsetOptions.size - 1)]
                        }

                        Box(
                            Modifier.height(80.dp).fillMaxWidth(),
                            Alignment.Center
                        ) {
                            // Warning: Picker is deprecated
                            Picker(state = offState) { i -> Text("${offsetOptions[i]} min") } // wear.compose.material.Picker & Text
                        }
                    } else {
                        M3Text("Duration too short for offset cues.")
                    }

                    Spacer(Modifier.height(8.dp))

                    // Type selector (remains the same)
                    SingleChoiceSegmentedButtonRow( // <-- CHANGED to SingleChoice...
                        modifier = Modifier.fillMaxWidth(0.9f) // <-- Added modifier
                    ) {
                        // Use Enum.entries <-- CHANGED from .values()
                        CueType.entries.forEachIndexed { index, ct ->
                            SegmentedButton( // material3.SegmentedButton
                                // Use 'selected' and 'onClick' <-- CHANGED parameters
                                selected = ct == type,
                                onClick = { type = ct },
                                // Shape is required for SegmentedButton inside SingleChoice...Row
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = CueType.entries.size) // <-- ADDED shape
                            ) {
                                // This Text call should now be fine
                                M3Text(ct.name.first().toString()) // Use M3Text for consistency in M3 component
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Repeats picker (remains the same)
                    M3Text("Repeats:") // material3.Text
                    val repeatOptions = remember { (1..5).toList() } // Explicit options
                    val repState = rememberPickerState(
                        initialNumberOfOptions = repeatOptions.size,
                        initiallySelectedOption = (repeats - 1).coerceIn(0, repeatOptions.size - 1)
                    )
                    // Update repeats state when picker changes
                    // Make sure repeats actually uses the value from the list index
                    LaunchedEffect(repState.selectedOption) {
                        repeats = repeatOptions[repState.selectedOption.coerceIn(0, repeatOptions.size - 1)]
                    }
                    Box( Modifier.height(80.dp).fillMaxWidth(), Alignment.Center) {
                        // Warning: Picker is deprecated
                        Picker(state = repState) { i -> Text("${repeatOptions[i]}") } // wear.compose.material.Picker & Text
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Calculate offset in milliseconds *from the start* for storage,
                        // but based on user selection (minutes from end)
                        val totalMillis = totalDurationMinutes * 60_000L
                        val offsetFromEndMillis = offset * 60_000L // Use the current 'offset' state variable
                        val actualOffsetMillis = (totalMillis - offsetFromEndMillis).coerceAtLeast(0L) // Offset from start

                        // Add the cue using the calculated offset from start
                        cues = cues + NotificationCue(offsetMillis = actualOffsetMillis, type = type, repeats = repeats)
                        showDialog = false
                    },
                    // Disable adding if duration is too short for offsets
                    // Now offsetOptions is in scope!
                    enabled = offsetOptions.isNotEmpty()
                ) {
                    M3Text("Add") // material3.Text
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { // material3.TextButton
                    M3Text("Cancel") // material3.Text
                }
            }
        )
    } // End of if(showDialog)
    if (showDeleteConfirmDialog) {
        AlertDialog( // Use androidx.compose.material3.AlertDialog
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { M3Text("Delete Preset?") }, // Use M3 Text
            text = { M3Text("Are you sure you want to delete '${label.ifBlank { "this timer" }}'?") }, // M3 Text
            confirmButton = {
                TextButton( // Use M3 TextButton
                    onClick = {
                        if (presetId != null) { // Safety check
                            Log.d("EditTimerScreen", "Confirming delete for ID: $presetId") // Add log
                            vm.delete(presetId) // Call your ViewModel's delete function
                            showDeleteConfirmDialog = false
                            onDone() // Navigate back
                        } else {
                            Log.w("EditTimerScreen", "Delete confirmation clicked but presetId was null") // Add warning log
                        }
                    }
                ) {
                    // Use M3 Text, styled with M3 Theme's error color
                    M3Text("Delete", color = M3MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { // M3 TextButton
                    M3Text("Cancel") // M3 Text
                }
            }
        )
    }
}

// --- Remember to have these defined elsewhere in your project ---
// data class TimerPreset(val id: Long, val label: String, val durationMillis: Long, val cues: List<NotificationCue>)
// @Serializable // Add if not already present
// data class NotificationCue(val offsetMillis: Long, val type: CueType, val repeats: Int)
// @Serializable // Add if not already present
// enum class CueType { VIBRATION, SOUND, BOTH } // Assuming BOTH might be needed based on ViewModel
// interface TimerViewModel { ... }
/* ---------- ActiveTimerScreen ---------- */
@Composable
fun ActiveTimerScreen(
    vm: TimerViewModel,
    presetId: Long?,
    onDone: () -> Unit
) {
    val state by vm.active.collectAsState()

    LaunchedEffect(presetId) {
        if (state == null && presetId != null) vm.startTimer(presetId)
    }

    // Handle case where timer finishes or is cancelled externally
    LaunchedEffect(state) {
        // Navigate back if the timer associated with *this screen instance* finishes.
        // Check presetId from argument against state's preset id.
        if (presetId != null && state?.preset?.id == presetId && state?.millisRemaining == 0L) {
            onDone() // Automatically navigate back when timer finishes
        }
        // Also navigate back if state becomes null while this screen is active
        // (e.g., timer cancelled from notification)
        // Optional: Add check ` && state == null` if needed, but finish check might be enough
        // if (presetId != null && state?.preset?.id == presetId && state == null) {
        //    onDone()
        // }
    }

    // Display data, using 0 if state is null initially or after cancellation
    val rem = state?.millisRemaining ?: 0L
    val mm = (rem / 60000).toInt()
    val ss = (rem % 60000 / 1000).toInt()
    val isRunning = state?.isRunning == true
    val initialDuration = state?.preset?.durationMillis ?: 1L // Avoid division by zero

    // Get the Wear Material theme's colors and typography
    val colors = androidx.wear.compose.material.MaterialTheme.colors
    val typography = androidx.wear.compose.material.MaterialTheme.typography

    Scaffold(
        timeText = {
            // Show TimeText only if timer is running or paused
            if (state != null) TimeText(modifier = Modifier.padding(top = 8.dp))
        },
        positionIndicator = {
            // Show a progress indicator
            if (state != null && initialDuration > 0) {
                PositionIndicator(
                    value = { (initialDuration - rem).toFloat() / initialDuration.toFloat() },
                    modifier = Modifier
                )
            }
        }
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Use Wear Text and Wear Typography ---
            Text( // Changed from M3Text
                text = "%02d:%02d".format(mm, ss),
                color = colors.onBackground, // Explicitly use Wear theme color (usually white/light grey)
                style = typography.display1 // Use Wear typography (display1 is large)
            )
            // -----------------------------------------

            Spacer(Modifier.height(16.dp)) // Increased spacing a bit

            Row {
                // Pause/Resume Button
                Button( // wear.compose.material.Button
                    onClick = { vm.pauseOrResume() },
                    enabled = state != null && rem > 0 // Enable only if active and not finished
                ) {
                    Text(if (isRunning) "Pause" else "Resume") // wear.compose.material.Text
                }
                Spacer(Modifier.width(8.dp))
                // Cancel Button
                Button( // wear.compose.material.Button
                    onClick = { vm.cancelTimer(); onDone() },
                    // Disable cancel if timer hasn't started or already finished
                    enabled = state != null
                ) {
                    Text("Cancel") // wear.compose.material.Text
                }
            }
        }
    }
}

package com.example.timerapp.presentation

import android.util.Log
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.collectAsState

import androidx.compose.material3.Button as M3Button
import androidx.wear.compose.material.Text

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.wear.compose.material.ButtonDefaults
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
import androidx.compose.material3.Card as M3Card

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
            Row(
                modifier = Modifier
                    .fillMaxWidth() // Span the full width
                    .height(48.dp) // Consistent height
                    // Use primary color for background
                    .background(Color(0xFF4CAF50))
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalWearFoundationApi::class)
@Composable
fun EditTimerScreen(
    vm: TimerViewModel,
    presetId: Long?,
    onDone: () -> Unit
) {
    Log.d("EditTimerScreen", "Composable Start. PresetId: $presetId")

    val presets by vm.presets.collectAsState()

    val initialPreset = remember(presetId, presets) {
        presets.firstOrNull { it.id == presetId }
    }

    var hours by rememberSaveable(presetId) {
        mutableIntStateOf(
            ((initialPreset?.durationMillis ?: 0L) / 3_600_000L).toInt()
        )
    }
    var minutes by rememberSaveable(presetId) {
        mutableIntStateOf(
            ((initialPreset?.durationMillis ?: 60_000L) / 60_000L % 60).toInt()
                .coerceAtLeast(1)
        )
    }
    var label by rememberSaveable(presetId) {
        mutableStateOf(initialPreset?.label ?: "")
    }
    var cues by rememberSaveable(presetId) {
        mutableStateOf(
            initialPreset?.cues
                ?.sortedBy { it.offsetMillis }
                ?: emptyList()
        )
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var addingCue             by remember { mutableStateOf(false) }


    if (addingCue) {
        val totalDurationMinutes = remember(hours, minutes) { hours * 60 + minutes }
        AddCueScreen(
            totalDurationMinutes = totalDurationMinutes,
            existingCues = cues,
            onAddCue = { newCue ->
                cues = (cues + newCue).sortedBy { it.offsetMillis }
                addingCue = false
            },
            onCancel = { addingCue = false }
        )
    } else {
        // *** SHOW EDIT TIMER SCREEN ***
        Log.d("EditTimerScreen", "Displaying EditTimerScreen")
        val listState = rememberScalingLazyListState()
        val hourOptions = remember { (0..23).toList() }
        val minuteOptions = remember { (0..59).toList() }

        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            // Use Box to allow positioning the "Add Signal" button at the bottom
            Box(modifier = Modifier.fillMaxSize()) {

                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(), // Fill the box
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Increase bottom padding significantly to avoid overlap with the fixed Add button
                    contentPadding = PaddingValues(top = 28.dp, bottom = 70.dp) // Needs space for button row
                ) {
                    // --- Label field ---
                    item {
                        Log.d("EditTimerScreen", "Composing Label TextField")
                        TextField( // M3 TextField
                            value = label,
                            onValueChange = { label = it },
                            placeholder = { M3Text("Label (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(bottom = 12.dp)
                        )
                    }

                    // --- Duration Pickers ---
                    item {
                        Log.d("EditTimerScreen", "Composing Duration Pickers")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Duration") // Wear Text
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.height(110.dp), // Fixed height for pickers
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Hours Picker
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Hours") // Wear Text
                                    val hourPickerState = rememberPickerState(
                                        initialNumberOfOptions = hourOptions.size,
                                        // Ensure initial selection matches state
                                        initiallySelectedOption = hourOptions.indexOf(hours).coerceAtLeast(0)
                                    )
                                    // Update state when picker selection changes
                                    LaunchedEffect(hourPickerState.selectedOption) {
                                        val newHours = hourOptions[hourPickerState.selectedOption]
                                        if (newHours != hours) {
                                            Log.d("EditTimerScreen", "Hour picker changed to: $newHours")
                                            hours = newHours
                                        }
                                    }
                                    // Update picker if state changes externally (e.g., on load)
                                    LaunchedEffect(hours) {
                                        val targetIndex = hourOptions.indexOf(hours).coerceAtLeast(0)
                                        if (hourPickerState.selectedOption != targetIndex) {
                                            Log.d("EditTimerScreen", "Scrolling hour picker to index: $targetIndex for hours: $hours")
                                            hourPickerState.scrollToOption(targetIndex)
                                        }
                                    }
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Picker( state = hourPickerState, modifier = Modifier.fillMaxWidth()) { h -> Text("$h") } // Wear Picker & Text
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                // Minutes Picker
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Minutes") // Wear Text
                                    val minutePickerState = rememberPickerState(
                                        initialNumberOfOptions = minuteOptions.size,
                                        initiallySelectedOption = minuteOptions.indexOf(minutes).coerceAtLeast(0)
                                    )
                                    // Update state when picker selection changes
                                    LaunchedEffect(minutePickerState.selectedOption) {
                                        val newMinutes = minuteOptions[minutePickerState.selectedOption]
                                        if (newMinutes != minutes) {
                                            Log.d("EditTimerScreen", "Minute picker changed to: $newMinutes")
                                            minutes = newMinutes
                                        }
                                    }
                                    // Update picker if state changes externally
                                    LaunchedEffect(minutes) {
                                        val targetIndex = minuteOptions.indexOf(minutes).coerceAtLeast(0)
                                        if (minutePickerState.selectedOption != targetIndex) {
                                            Log.d("EditTimerScreen", "Scrolling minute picker to index: $targetIndex for minutes: $minutes")
                                            minutePickerState.scrollToOption(targetIndex)
                                        }
                                    }
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Picker( state = minutePickerState, modifier = Modifier.fillMaxWidth()) { m -> Text("%02d".format(m)) } // Wear Picker & Text
                                    }
                                }
                            } // End Row
                        } // End wrapper Column
                        Spacer(Modifier.height(12.dp))
                    } // End Duration item


                    // --- Cues list ---
                    if (cues.isNotEmpty()) {
                        item { M3Text("Signals", modifier = Modifier.padding(bottom = 4.dp)) } // Title for cues list
                    }
                    Log.d("EditTimerScreen", "Composing Cues List (${cues.size} items)")
                    itemsIndexed(
                        items = cues,
                        key = { index, cueItem -> cueItem.hashCode().toLong() + index } // More robust key
                    ) { idx, cue ->
                        Log.d("EditTimerScreen", "Composing Cue Item $idx: $cue")
                        // Use M3Card for consistency maybe? Or keep Wear Card? Let's try M3Card
                        M3Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .combinedClickable( // <<< ADD combinedClickable
                                    onClick = {
                                        Log.d("EditTimerScreen", "Cue Card $idx clicked (for future edit)")
                                        // TODO: Implement edit functionality if needed later
                                    },
                                    onLongClick = {
                                        Log.d("EditTimerScreen", "Cue Card $idx LONG PRESSED via combinedClickable. Removing cue: $cue")
                                        // Same state update logic
                                        cues = cues.toMutableList().apply { removeAt(idx) }
                                    }
                                )
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp), // Padding inside card
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween // Space out elements
                            ) {
                                // Calculate display offset (minutes before end)
                                val totalDurationMillis = (hours * 60L + minutes) * 60000L
                                val millisFromEnd = (totalDurationMillis - cue.offsetMillis).coerceAtLeast(0)
                                val displayOffsetMinutes = millisFromEnd / 60000L

                                // Display Format: "-Xm" or "Start" or "End"
                                val displayOffsetString = when {
                                    // Handle cue exactly at the start (offsetMillis = 0)
                                    cue.offsetMillis == 0L && totalDurationMillis > 0 -> "Start"
                                    // Handle cue exactly at the end (offsetMillis = totalDurationMillis)
                                    cue.offsetMillis == totalDurationMillis && totalDurationMillis > 0 -> "End"
                                    // Handle typical offset from end
                                    displayOffsetMinutes > 0 -> "-${displayOffsetMinutes}m"
                                    // Handle cases very close to the end (less than a minute)
                                    millisFromEnd > 0 -> "< 1m"
                                    // Default/fallback (should ideally not happen with valid data)
                                    else -> "End" // Or consider showing seconds "-${millisFromEnd/1000}s"
                                }

                                M3Text(displayOffsetString, style = M3MaterialTheme.typography.bodyLarge) // Make it clear
                                M3Text(
                                    "${cue.type.name.first()} ×${cue.repeats}", // e.g., V ×2
                                    style = M3MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp)) // Space between cue cards
                    } // End itemsIndexed

                    // --- Spacer to push buttons down ---
                    // Add enough space so buttons are clearly below cues, especially if list is short
                    item { Spacer(Modifier.height(16.dp)) }

                    // --- Save button ---
                    item {
                        Log.d("EditTimerScreen", "Composing Save Button")
                        Button( // M3 Button
                            onClick = {
                                Log.d("EditTimerScreen", "Save Button Clicked")
                                val totalMillis = (hours * 60L + minutes) * 60_000L
                                if (totalMillis > 0) {
                                    val finalLabel = label.ifBlank {
                                        // Generate a more descriptive default label
                                        val parts = mutableListOf<String>()
                                        if (hours > 0) parts.add("${hours}h")
                                        if (minutes > 0) parts.add("${minutes}m")
                                        "Timer ${parts.joinToString(" ")}"
                                    }
                                    val presetToSave = TimerPreset(
                                        id = presetId ?: System.currentTimeMillis(), // Use existing ID or generate new
                                        label = finalLabel,
                                        durationMillis = totalMillis,
                                        cues = cues // Save the current list of cues
                                    )
                                    Log.d("EditTimerScreen", "Saving preset: $presetToSave")
                                    vm.addOrUpdate(presetToSave)
                                    onDone() // Navigate back
                                } else {
                                    Log.w("EditTimerScreen", "Save attempt with 0 duration.")
                                    // Optionally show a Toast/Snackbar message to the user
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            enabled = (hours > 0 || minutes > 0), // Can save if duration > 0
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50),
                                contentColor    = Color.White
                            )
                        ) {
                            M3Text("Save") // M3 Text
                        }
                    } // End Save Button item

                    // --- Delete button (Conditional) ---
                    if (presetId != null) {
                        item {
                            Log.d("EditTimerScreen", "Composing Delete Button")
                            Spacer(Modifier.height(8.dp))
                            M3Button( // M3 Button
                                onClick = {
                                    Log.d("EditTimerScreen", "Delete Button Clicked")
                                    showDeleteConfirmDialog = true
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336),  // Material Red 500
                                    contentColor   = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth(0.9f),
                            ) {
                                M3Text("Delete") // M3 Text
                            }
                        } // End Delete Button item
                    }

                } // End ScalingLazyColumn

                // --- "+ Add Signal" Button Row at Bottom --- (Fixed Position)
                // This Box is aligned to the bottom of the parent Box (which fills the screen)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter) // Position at bottom
                        .fillMaxWidth() // Take full width
                        .height(48.dp) // Fixed height
                        // Use Surface/background for color - M3 Surface is often better
                        .background(M3MaterialTheme.colorScheme.primary) // Use M3 primary color
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize() // Fill the Box
                            .clickable(
                                enabled = ((hours * 60 + minutes) > 1), // Enable only if duration > 0
                                onClick = {
                                    Log.d("EditTimerScreen", "Add Signal Button Clicked")
                                    addingCue = true // <<< Set state to show AddCueScreen
                                }
                            )
                            .alpha(if ((hours * 60 + minutes) > 1) 1f else 0.6f) // Visual feedback for disabled state
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon( // M3 Icon
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Cue",
                            tint = M3MaterialTheme.colorScheme.onPrimary // M3 onPrimary color
                        )
                        Spacer(Modifier.width(8.dp))
                        M3Text( // M3 Text
                            text = "Add Signal",
                            color = M3MaterialTheme.colorScheme.onPrimary // M3 onPrimary color
                        )
                    }
                }
                // ------------------------------------

            } // End outer Box for positioning Add button
        } // End Scaffold
    } // --- End of `else` block (showing EditTimerScreen) ---


    // --- Delete Confirmation Dialog (Remains the same, outside the conditional display) ---
    if (showDeleteConfirmDialog) {
        Log.d("EditTimerScreen", "Displaying Delete Confirmation Dialog")
        AlertDialog( // M3 AlertDialog
            onDismissRequest = {
                Log.d("EditTimerScreen", "Delete Confirmation Dismissed")
                showDeleteConfirmDialog = false
            },
            title = { M3Text("Delete Preset?") },
            text = { M3Text("Are you sure you want to delete '${label.ifBlank { "this timer" }}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetId != null) { // Safety check
                            Log.d("EditTimerScreen", "Confirming delete for ID: $presetId")
                            vm.delete(presetId)
                            showDeleteConfirmDialog = false
                            onDone() // Navigate back
                        } else {
                            Log.e("EditTimerScreen", "Delete confirmation clicked but presetId was null!")
                            showDeleteConfirmDialog = false // Dismiss even on error
                        }
                    }
                ) {
                    M3Text("Delete", color = M3MaterialTheme.colorScheme.error) // Use M3 error color
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    Log.d("EditTimerScreen", "Delete Confirmation Cancelled")
                    showDeleteConfirmDialog = false
                }) {
                    M3Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalWearFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddCueScreen(
    totalDurationMinutes: Int,
    existingCues: List<NotificationCue>,
    onAddCue: (NotificationCue) -> Unit,
    onCancel: () -> Unit
) {
    // --- State Variables ---
    var offset by remember { mutableIntStateOf(1) }
    var repeats by remember { mutableIntStateOf(1) }
    var type by remember { mutableStateOf(CueType.VIBRATION) }
    val scrollState = rememberScrollState()

    // --- Calculate Valid Offset Options ---
    val offsetOptions = remember(totalDurationMinutes) {
        if (totalDurationMinutes > 1) (1 until totalDurationMinutes).toList()
        else emptyList()
    }
    val isOffsetPossible = offsetOptions.isNotEmpty()

    // --- Picker States ---
    val offsetPickerState = rememberPickerState(
        initialNumberOfOptions = offsetOptions.size,
        initiallySelectedOption = offsetOptions.indexOf(offset).coerceIn(0, offsetOptions.lastIndex)
    )
    val repeatOptions = remember { (1..10).toList() }
    val repeatsPickerState = rememberPickerState(
        initialNumberOfOptions = repeatOptions.size,
        initiallySelectedOption = (repeats - 1).coerceIn(0, repeatOptions.lastIndex)
    )

    // Sync pickers → state
    LaunchedEffect(offsetPickerState.selectedOption) {
        if (isOffsetPossible) offset = offsetOptions[offsetPickerState.selectedOption]
    }
    LaunchedEffect(repeatsPickerState.selectedOption) {
        repeats = repeatOptions[repeatsPickerState.selectedOption]
    }

    val usedOffsetsMinutes = remember(existingCues, totalDurationMinutes) {
        existingCues.map { cue ->
            ((totalDurationMinutes * 60_000L - cue.offsetMillis) / 60_000L).toInt()
        }.toSet()
    }
    val isDuplicate = offset in usedOffsetsMinutes

    // --- UI ---
    Scaffold(timeText = { TimeText() }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            M3Text("New Signal", style = M3MaterialTheme.typography.titleMedium)

            // Offset picker
            M3Text("Minutes before end:", color = WearMaterialTheme.colors.onBackground)
            if (isOffsetPossible) {
                Box(Modifier.height(80.dp).fillMaxWidth(0.8f), Alignment.Center) {
                    Picker(state = offsetPickerState, modifier = Modifier.fillMaxWidth()) { idx ->
                        Text("${offsetOptions[idx]} min")
                    }
                }
            } else {
                M3Text(
                    "Duration too short",
                    style = M3MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            // Type selector
            M3Text("Signal Type:", color = WearMaterialTheme.colors.onBackground)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth(0.9f).height(40.dp)) {
                CueType.entries.forEachIndexed { i, ct ->
                    SegmentedButton(
                        selected = ct == type,
                        onClick = { type = ct },
                        shape = SegmentedButtonDefaults.itemShape(i, CueType.entries.size),
                        icon = {},
                        label = { M3Text(ct.name.first().toString()) }
                    )
                }
            }

            // Repeats picker
            M3Text("Repeats:", color = WearMaterialTheme.colors.onBackground)
            Box(Modifier.height(80.dp).fillMaxWidth(0.8f), Alignment.Center) {
                Picker(state = repeatsPickerState, modifier = Modifier.fillMaxWidth()) { idx ->
                    Text("${repeatOptions[idx]}")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Error message for duplicate
            if (isDuplicate) {
                M3Text(
                    "Cue at $offset m already exists",
                    style = M3MaterialTheme.typography.bodySmall,
                    color = M3MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Action buttons

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel
                M3Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336),  // Material Red 500
                        contentColor   = Color.White
                    )
                ) {
                    Text(
                        text = "Cancel",
                        style = M3MaterialTheme.typography.labelMedium,               // ②
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false
                    )
                }

                // Add
                M3Button(
                    onClick = {
                        val totalMs = totalDurationMinutes * 60_000L
                        val actualOffset = (totalMs - offset * 60_000L).coerceAtLeast(0L)
                        onAddCue(NotificationCue(actualOffset, type, repeats))
                    },
                    enabled = isOffsetPossible && !isDuplicate,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp), // keep paddings identical
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor   = Color.White
                    )
                ) {
                    Text(
                        text = "Add",
                        style = M3MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/* ---------- ActiveTimerScreen ---------- */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveTimerScreen(
    vm: TimerViewModel,
    presetId: Long?,
    onDone: () -> Unit
) {
    val state by vm.active.collectAsState()

    LaunchedEffect(Unit) {
        vm.resumeIfNeeded()
    }

    LaunchedEffect(presetId) {
        if (state == null && presetId != null) vm.startTimer(presetId)
    }

    LaunchedEffect(state) {
        if (presetId != null && state?.preset?.id == presetId && state?.millisRemaining == 0L) {
            onDone()
        }
    }

    val rem = state?.millisRemaining ?: 0L
    val mm = (rem / 60000).toInt()
    val ss = (rem % 60000 / 1000).toInt()
    val isRunning = state?.isRunning == true
    val initialDuration = state?.preset?.durationMillis ?: 1L

    val colors = WearMaterialTheme.colors
    val typography = WearMaterialTheme.typography

    Scaffold(
        timeText = {
            if (state != null) TimeText(modifier = Modifier.padding(top = 8.dp))
        },
        positionIndicator = {
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
            Text(
                text = "%02d:%02d".format(mm, ss),
                color = colors.onBackground,
                style = typography.display1
            )

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Pause/Resume Button (Stays as a Button)
                Button(
                    onClick = { vm.pauseOrResume() },
                    modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
                    enabled = state != null && rem > 0,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF4CAF50),
                        contentColor    = Color.White
                    )
                ) {
                    Text(if (isRunning) "Pause" else "Resume")
                }

                Spacer(Modifier.width(12.dp))

                // --- VVV Cancel Button Replaced with Box VVV ---
                val cancelEnabled = state != null
                // Define colors based on enabled state
                val cancelBackgroundColor = if (cancelEnabled) colors.error else colors.error.copy(alpha = 0.3f)
                val cancelContentColor = if (cancelEnabled) colors.onError else colors.onError.copy(alpha = 0.5f)

                Box( // Use a Box instead of Button
                    modifier = Modifier
                        .size(ButtonDefaults.LargeButtonSize) // Make the Box button-sized
                        .clip(CircleShape) // Clip it to a circle like a button
                        .background(cancelBackgroundColor) // Apply the red/disabled background
                        .combinedClickable( // Apply interaction directly to the Box
                            enabled = cancelEnabled, // Control gesture detection
                            onClick = {
                                Log.d("ActiveTimerScreen", "Cancel Box tapped (ignored)")
                                // Do nothing on short click
                            },
                            onLongClick = {
                                Log.d("ActiveTimerScreen", "Cancel Box long-pressed")
                                vm.cancelTimer()
                                onDone()
                            }
                        ),
                    contentAlignment = Alignment.Center // Center the Text inside the Box
                ) {
                    Text(
                        text = "Cancel",
                        color = cancelContentColor // Use the determined content color
                    )
                }
            }
        }
    }
}
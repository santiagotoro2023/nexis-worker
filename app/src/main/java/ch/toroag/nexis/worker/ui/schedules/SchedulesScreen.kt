package ch.toroag.nexis.worker.ui.schedules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchedulesScreen(
    onBack: () -> Unit,
    vm: SchedulesViewModel = viewModel(),
) {
    val schedules by vm.schedules.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.errorMessage.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var showAdd         by remember { mutableStateOf(false) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("delete schedule", color = NxFg) },
            text  = { Text("remove this schedule permanently?", color = NxFg2) },
            confirmButton = {
                TextButton(onClick = { vm.delete(pendingDeleteId!!); pendingDeleteId = null }) {
                    Text("delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("cancel", color = NxFg2) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showAdd) {
        AddScheduleDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, expr, prompt ->
                vm.addSchedule(name, expr, prompt) { showAdd = false }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("schedules", style = MaterialTheme.typography.titleMedium, color = NxFg) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, "Add", tint = NxFg2)
                    }
                    IconButton(onClick = { vm.loadSchedules() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (error != null) {
                Text(
                    error!!,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NxOrange)
                }
                schedules.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no schedules yet", color = NxFg2, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleItem(
                            schedule      = schedule,
                            onToggle      = { vm.toggle(schedule.id, !schedule.active) },
                            onRunNow      = { vm.runNow(schedule.id) },
                            onLongPress   = { pendingDeleteId = schedule.id },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleItem(
    schedule:    Schedule,
    onToggle:    () -> Unit,
    onRunNow:    () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(4.dp))
            .combinedClickable(onLongClick = onLongPress, onClick = {})
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(
            checked         = schedule.active,
            onCheckedChange = { onToggle() },
            colors          = SwitchDefaults.colors(
                checkedTrackColor   = NxOrangeDim,
                checkedThumbColor   = NxFg,
                uncheckedTrackColor = NxBorder,
                uncheckedThumbColor = NxFg2,
            ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(schedule.name, color = NxFg, style = MaterialTheme.typography.bodyMedium)
            Text(schedule.expr, color = NxOrangeDim, style = MaterialTheme.typography.labelSmall)
            Text(
                schedule.prompt,
                color    = NxFg2,
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (schedule.lastRun != null) {
                Text(
                    "last run: ${schedule.lastRun}",
                    color = NxFg2,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        IconButton(onClick = onRunNow, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.PlayArrow, "Run now", tint = NxFg2, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name   by remember { mutableStateOf("") }
    var expr   by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("new schedule", color = NxFg) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NxField(value = name, label = "name", onValueChange = { name = it })
                NxField(value = expr, label = "cron expr (e.g. 0 8 * * *)", onValueChange = { expr = it })
                NxField(value = prompt, label = "prompt", onValueChange = { prompt = it }, singleLine = false)
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onConfirm(name, expr, prompt) },
                enabled  = name.isNotBlank() && expr.isNotBlank() && prompt.isNotBlank(),
            ) { Text("add", color = NxOrange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("cancel", color = NxFg2) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun NxField(
    value:         String,
    label:         String,
    onValueChange: (String) -> Unit,
    singleLine:    Boolean = true,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label, color = NxFg2) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = singleLine,
        shape         = RoundedCornerShape(4.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = NxOrangeDim,
            unfocusedBorderColor    = NxBorder,
            focusedTextColor        = NxFg,
            unfocusedTextColor      = NxFg,
            cursorColor             = NxOrange,
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        textStyle = MaterialTheme.typography.bodySmall,
    )
}

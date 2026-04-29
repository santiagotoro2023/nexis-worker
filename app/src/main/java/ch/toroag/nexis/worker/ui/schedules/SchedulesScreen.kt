package ch.toroag.nexis.worker.ui.schedules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
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
        Box(Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("DELETE SCHEDULE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxRed,
                     modifier = Modifier.padding(bottom = 12.dp))
                Text("Remove this schedule permanently?",
                     fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pendingDeleteId = null },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    Button(
                        onClick = { vm.delete(pendingDeleteId!!); pendingDeleteId = null },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NxRed, contentColor = NxBg),
                    ) { Text("DELETE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }
        return
    }

    if (showAdd) {
        AddScheduleSheet(
            onDismiss = { showAdd = false },
            onConfirm = { name, expr, prompt -> vm.addSchedule(name, expr, prompt) { showAdd = false } },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(NxBg).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("SCHEDULES", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadSchedules() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { showAdd = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Add, null, tint = NxOrange, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        if (error != null) {
            Text(error!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxRed,
                 modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange, strokeWidth = 2.dp)
            }
            schedules.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO SCHEDULES YET", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2)
            }
            else -> LazyColumn(
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleItem(
                        schedule    = schedule,
                        onToggle    = { vm.toggle(schedule.id, !schedule.active) },
                        onRunNow    = { vm.runNow(schedule.id) },
                        onLongPress = { pendingDeleteId = schedule.id },
                    )
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
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
            .combinedClickable(onLongClick = onLongPress, onClick = {})
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(schedule.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                 fontWeight = FontWeight.Bold, color = NxFg)
            Text(schedule.expr, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = NxOrangeDim)
            Text(schedule.prompt, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxFg2,
                 maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (schedule.lastRun != null) {
                Text("last: ${schedule.lastRun}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2)
            }
        }
        IconButton(onClick = onRunNow, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.PlayArrow, null, tint = NxOrange, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddScheduleSheet(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name   by remember { mutableStateOf("") }
    var expr   by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor   = NxBg2,
        unfocusedContainerColor = NxBg2,
        focusedBorderColor      = NxOrangeDim,
        unfocusedBorderColor    = NxBorder,
        focusedTextColor        = NxFg,
        unfocusedTextColor      = NxFg,
        cursorColor             = NxOrange,
    )

    Box(Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .background(NxBg3, RoundedCornerShape(16.dp))
                .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("NEW SCHEDULE", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxOrange)

            Column {
                Text("NAME", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp), colors = fieldColors,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg))
            }
            Column {
                Text("CRON EXPRESSION", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(expr, { expr = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(10.dp), colors = fieldColors,
                    placeholder = { Text("0 8 * * *", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2.copy(alpha = 0.5f)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg))
            }
            Column {
                Text("PROMPT", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(prompt, { prompt = it }, modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(10.dp), colors = fieldColors,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = NxFg))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                ) { Text("CANCEL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                Button(
                    onClick  = { onConfirm(name, expr, prompt) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled  = name.isNotBlank() && expr.isNotBlank() && prompt.isNotBlank(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
                ) { Text("ADD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
        }
    }
}

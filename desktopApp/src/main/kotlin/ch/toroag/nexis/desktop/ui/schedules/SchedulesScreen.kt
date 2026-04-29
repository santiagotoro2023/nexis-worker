package ch.toroag.nexis.desktop.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun SchedulesScreen(vm: SchedulesViewModel) {
    val schedules by vm.schedules.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.errorMessage.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor   = NxBg2,
            title = { Text("delete schedule", color = NxFg) },
            text  = { Text("Remove this scheduled task?", color = NxFg2) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSchedule(pendingDeleteId!!); pendingDeleteId = null }) {
                    Text("delete", color = NxRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("cancel", color = NxFg2)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(NxBg).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("schedules", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NxFg,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadSchedules() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (error != null) {
            Text(error!!, color = NxRed, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && schedules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange)
            }
        } else if (schedules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("no schedules", color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("use //schedule in chat to create one",
                         color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedules, key = { it.id }) { sched ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(4.dp),
                        border   = androidx.compose.foundation.BorderStroke(0.5.dp, NxBorder),
                        colors   = CardDefaults.outlinedCardColors(containerColor = NxBg3),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (sched.name.isNotEmpty()) {
                                    Text(sched.name,
                                         fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                         color = NxFg)
                                }
                                Text(sched.prompt,
                                     fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                     color = NxFg)
                                Text(sched.expr,
                                     fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                     color = NxOrange)
                                if (!sched.lastRun.isNullOrEmpty()) {
                                    Text("last: ${sched.lastRun.take(16)}",
                                         fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                         color = NxFg2)
                                }
                            }
                            IconButton(
                                onClick  = { pendingDeleteId = sched.id },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Default.Delete, "Delete", tint = NxFg2, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

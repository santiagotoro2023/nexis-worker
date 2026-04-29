package ch.toroag.nexis.desktop.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val history   by vm.history.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.errorMessage.collectAsState()
    val listState = rememberLazyListState()

    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.size - 1)
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = NxBg2,
            title = { Text("clear history", color = NxFg) },
            text  = { Text("Clear the conversation history? This starts a fresh context.", color = NxFg2) },
            confirmButton = {
                TextButton(onClick = { vm.clearHistory(); showClearConfirm = false }) {
                    Text("clear", color = NxRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("cancel", color = NxFg2)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(NxBg).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("conversation history", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NxFg,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadHistory() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
            }
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(Icons.Default.Delete, "Clear history", tint = NxFg2)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (error != null) {
            Text(error!!, color = NxRed, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange)
            }
        } else if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("no history", color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.role + it.content.hashCode() }) { entry ->
                    val isUser = entry.role == "user"
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    ) {
                        Column(
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                            modifier = Modifier.widthIn(max = 560.dp),
                        ) {
                            Text(
                                if (isUser) "you" else "nexis",
                                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                color    = if (isUser) NxFg2 else NxOrange,
                                modifier = Modifier.padding(bottom = 2.dp,
                                                            start  = if (isUser) 0.dp else 2.dp,
                                                            end    = if (isUser) 2.dp else 0.dp),
                            )
                            Surface(
                                shape = if (isUser) RoundedCornerShape(8.dp, 2.dp, 8.dp, 8.dp)
                                        else         RoundedCornerShape(2.dp, 8.dp, 8.dp, 8.dp),
                                color = if (isUser) NxDim else NxBg3,
                            ) {
                                Text(
                                    entry.content,
                                    fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color    = NxFg,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

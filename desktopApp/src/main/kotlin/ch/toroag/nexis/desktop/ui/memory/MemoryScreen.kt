package ch.toroag.nexis.desktop.ui.memory

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
import androidx.compose.ui.unit.dp
import ch.toroag.nexis.desktop.ui.theme.*

@Composable
fun MemoryScreen(vm: MemoryViewModel) {
    val memories    by vm.memories.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val error       by vm.errorMessage.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor   = NxBg2,
            title = { Text("delete memory", color = NxFg) },
            text  = { Text("Remove this memory permanently?", color = NxFg2) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMemory(pendingDeleteId!!); pendingDeleteId = null }) {
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
            Text("memories", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NxFg,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadMemories() }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { vm.setSearchQuery(it) },
            placeholder   = { Text("search memories…", color = NxFg2) },
            modifier      = Modifier.fillMaxWidth(),
            singleLine    = true,
            shape         = RoundedCornerShape(4.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NxOrangeDim, unfocusedBorderColor = NxBorder,
                focusedTextColor = NxFg, unfocusedTextColor = NxFg, cursorColor = NxOrange,
                focusedContainerColor = NxBg2,
                unfocusedContainerColor = NxBg2,
            ),
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
        )
        Spacer(Modifier.height(12.dp))

        if (error != null) {
            Text(error!!, color = NxRed, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading && memories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange)
            }
        } else if (memories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("no memories found", color = NxFg2, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.id }) { mem ->
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
                                Text(mem.content,
                                     fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                     color = NxFg)
                                if (mem.createdAt.isNotEmpty()) {
                                    Text(mem.createdAt.take(16),
                                         fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                                         color = NxFg2)
                                }
                            }
                            IconButton(
                                onClick  = { pendingDeleteId = mem.id },
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

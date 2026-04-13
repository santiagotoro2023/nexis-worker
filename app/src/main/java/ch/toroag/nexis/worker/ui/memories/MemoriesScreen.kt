package ch.toroag.nexis.worker.ui.memories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun MemoriesScreen(
    onBack: () -> Unit,
    vm: MemoriesViewModel = viewModel(),
) {
    val memories    by vm.memories.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val error       by vm.errorMessage.collectAsState()

    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("delete memory", color = NxFg) },
            text  = { Text("remove this memory permanently?", color = NxFg2) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMemory(pendingDeleteId!!); pendingDeleteId = null }) {
                    Text("delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("cancel", color = NxFg2)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("memories", style = MaterialTheme.typography.titleMedium, color = NxFg) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadMemories() }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = NxFg2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder   = { Text("search memories...", color = NxFg2) },
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine    = true,
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
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            if (error != null) {
                Text(
                    error!!,
                    color    = MaterialTheme.colorScheme.error,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NxOrange)
                }
                memories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no memories yet", color = NxFg2, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(
                    contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryItem(
                            memory    = memory,
                            onLongPress = { pendingDeleteId = memory.id },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryItem(memory: Memory, onLongPress: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(4.dp))
            .combinedClickable(onLongClick = onLongPress, onClick = {})
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            memory.content,
            color    = NxFg,
            style    = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            memory.createdAt,
            color = NxFg2,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

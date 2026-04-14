package ch.toroag.nexis.worker.ui.history

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
fun HistoryScreen(
    onBack:          () -> Unit,
    onSessionLoaded: () -> Unit,
    vm: HistoryViewModel = viewModel(),
) {
    val sessions  by vm.sessions.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error     by vm.errorMessage.collectAsState()

    var confirmSession by remember { mutableStateOf<HistorySession?>(null) }
    var confirmDelete  by remember { mutableStateOf<HistorySession?>(null) }

    if (confirmSession != null) {
        val session = confirmSession!!
        AlertDialog(
            onDismissRequest = { confirmSession = null },
            title = { Text("load conversation?", color = NxFg) },
            text  = {
                Text(
                    "this replaces the current active conversation.",
                    color = NxFg2,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSession = null
                    vm.loadSession(session.sessionId) { onSessionLoaded() }
                }) {
                    Text("load", color = NxOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSession = null }) {
                    Text("cancel", color = NxFg2)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (confirmDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("delete session?", color = NxFg) },
            text  = { Text("permanently remove this conversation from history?", color = NxFg2,
                           style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(confirmDelete!!.sessionId)
                    confirmDelete = null
                }) { Text("delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
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
                title = { Text("history", style = MaterialTheme.typography.titleMedium, color = NxFg) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NxFg2)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadSessions() }) {
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
                sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no history yet", color = NxFg2, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sessions, key = { it.sessionId }) { session ->
                        SessionItem(
                            session     = session,
                            onClick     = { confirmSession = session },
                            onLongPress = { confirmDelete = session },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(session: HistorySession, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    val firstUserMsg = session.preview.firstOrNull { it.role == "user" }?.content ?: ""
    val preview = if (firstUserMsg.length > 90) firstUserMsg.take(90) + "…" else firstUserMsg
    val displayTitle = session.title.ifBlank { null }

    Row(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier            = Modifier.width(IntrinsicSize.Max),
        ) {
            Text(session.started, color = NxFg2, style = MaterialTheme.typography.labelSmall)
            Text(
                "(${session.source})",
                color = NxOrangeDim,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (displayTitle != null) {
                Text(
                    displayTitle,
                    color    = NxFg,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                preview.ifBlank { "(empty)" },
                color    = if (displayTitle != null) NxFg2 else NxFg,
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

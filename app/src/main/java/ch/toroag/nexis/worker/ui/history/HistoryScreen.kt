package ch.toroag.nexis.worker.ui.history

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
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
        Box(Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("LOAD CONVERSATION", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxOrange,
                     modifier = Modifier.padding(bottom = 12.dp))
                Text("This replaces the current active conversation.",
                     fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmSession = null },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    Button(
                        onClick = { confirmSession = null; vm.loadSession(session.sessionId) { onSessionLoaded() } },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NxOrange, contentColor = NxBg),
                    ) { Text("LOAD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }
        return
    }

    if (confirmDelete != null) {
        Box(Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("DELETE SESSION", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxRed,
                     modifier = Modifier.padding(bottom = 12.dp))
                Text("Permanently remove this conversation from history?",
                     fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2,
                     modifier = Modifier.padding(bottom = 20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirmDelete = null },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NxFg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NxBorder),
                    ) { Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    Button(
                        onClick = { vm.deleteSession(confirmDelete!!.sessionId); confirmDelete = null },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NxRed, contentColor = NxBg),
                    ) { Text("DELETE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }
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
            Text("CONVERSATION HISTORY", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadSessions() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(18.dp))
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
            sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO HISTORY YET", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2)
            }
            else -> LazyColumn(
                contentPadding      = PaddingValues(16.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(session: HistorySession, onClick: () -> Unit, onLongPress: () -> Unit) {
    val firstUserMsg = session.preview.firstOrNull { it.role == "user" }?.content ?: ""
    val preview = if (firstUserMsg.length > 90) firstUserMsg.take(90) + "…" else firstUserMsg
    val displayTitle = session.title.ifBlank { null }

    Row(
        Modifier
            .fillMaxWidth()
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.started, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2)
            Text("(${session.source})", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxOrangeDim)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (displayTitle != null) {
                Text(displayTitle, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                     color = NxFg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(preview.ifBlank { "(empty)" },
                 fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                 color = if (displayTitle != null) NxFg2 else NxFg,
                 maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

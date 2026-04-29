package ch.toroag.nexis.worker.ui.memories

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.toroag.nexis.worker.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
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
        Box(Modifier.fillMaxSize().background(NxBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.85f)
                    .background(NxBg3, RoundedCornerShape(16.dp))
                    .border(1.dp, NxBorder, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Text("DELETE MEMORY", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                     fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxRed,
                     modifier = Modifier.padding(bottom = 12.dp))
                Text("Remove this memory permanently?",
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
                        onClick = { vm.deleteMemory(pendingDeleteId!!); pendingDeleteId = null },
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
            Text("MEMORIES", fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                 fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp, color = NxFg2,
                 modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.loadMemories() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, null, tint = NxFg2, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = NxBorder, thickness = 1.dp)

        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { vm.setSearchQuery(it) },
            placeholder   = {
                Text("SEARCH MEMORIES…", fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                     color = NxFg2.copy(alpha = 0.5f))
            },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = NxFg2, modifier = Modifier.size(18.dp)) },
            modifier      = Modifier.fillMaxWidth().padding(16.dp),
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = NxBg2,
                unfocusedContainerColor = NxBg2,
                focusedBorderColor      = NxOrangeDim,
                unfocusedBorderColor    = NxBorder,
                focusedTextColor        = NxFg,
                unfocusedTextColor      = NxFg,
                cursorColor             = NxOrange,
            ),
            textStyle     = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )

        if (error != null) {
            Text(error!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NxRed,
                 modifier = Modifier.padding(horizontal = 16.dp))
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NxOrange, strokeWidth = 2.dp)
            }
            memories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO MEMORIES YET", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg2)
            }
            else -> LazyColumn(
                contentPadding      = PaddingValues(horizontal = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryItem(memory = memory, onLongPress = { pendingDeleteId = memory.id })
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
            .background(NxBg3, RoundedCornerShape(12.dp))
            .border(1.dp, NxBorder, RoundedCornerShape(12.dp))
            .combinedClickable(onLongClick = onLongPress, onClick = {})
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(memory.content, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = NxFg,
             maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(memory.createdAt, fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = NxFg2)
    }
}

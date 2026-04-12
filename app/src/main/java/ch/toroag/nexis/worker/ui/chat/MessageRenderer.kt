package ch.toroag.nexis.worker.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.worker.ui.theme.NxBg2
import ch.toroag.nexis.worker.ui.theme.NxBorder
import ch.toroag.nexis.worker.ui.theme.NxFg
import ch.toroag.nexis.worker.ui.theme.NxFg2
import ch.toroag.nexis.worker.ui.theme.NxOrange
import ch.toroag.nexis.worker.ui.theme.NxOrangeDim
import kotlinx.coroutines.delay

private sealed interface Segment {
    data class Text(val content: String) : Segment
    data class Code(val code: String, val lang: String) : Segment
}

private val CODE_BLOCK_RE = Regex("""```(\w*)\n?([\s\S]*?)```""")

private fun parseSegments(raw: String): List<Segment> {
    // Replace em dashes with spaced hyphens — AI responses commonly include them
    val content = raw.replace('\u2014', '-').replace('\u2013', '-')
    val result  = mutableListOf<Segment>()
    var last    = 0
    for (m in CODE_BLOCK_RE.findAll(content)) {
        if (m.range.first > last) {
            result += Segment.Text(content.substring(last, m.range.first))
        }
        result += Segment.Code(
            code = m.groupValues[2].trimEnd(),
            lang = m.groupValues[1].ifEmpty { "code" },
        )
        last = m.range.last + 1
    }
    if (last < content.length) result += Segment.Text(content.substring(last))
    return result
}

@Composable
fun RenderedMessage(content: String) {
    val context  = LocalContext.current
    val segments = remember(content) { parseSegments(content) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { seg ->
            when (seg) {
                is Segment.Text -> {
                    if (seg.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text  = seg.content.trim(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 13.sp,
                                    lineHeight = 20.sp,
                                ),
                                color = NxOrange,
                            )
                        }
                    }
                }
                is Segment.Code -> CodeBlock(seg.lang, seg.code, context)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String, context: Context) {
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(1800); copied = false }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg2, RoundedCornerShape(4.dp))
    ) {
        // Header row: language label + copy button
        Row(
            Modifier
                .fillMaxWidth()
                .background(NxBorder, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                lang.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 10.sp,
                ),
                color = NxFg2,
            )
            IconButton(
                onClick  = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint     = if (copied) NxOrange else NxFg2,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        // Code content — selectable
        SelectionContainer {
            Text(
                text     = code,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style    = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 12.sp,
                    lineHeight = 18.sp,
                ),
                color    = NxFg,
            )
        }
    }
}

@Composable
fun TypingIndicatorDots() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(380)
            step = (step + 1) % 4
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        repeat(3) { i ->
            Text(
                "▶",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (i < step) NxOrange else NxOrangeDim.copy(alpha = 0.25f),
            )
        }
    }
}

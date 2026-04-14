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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.toroag.nexis.worker.ui.theme.*
import kotlinx.coroutines.delay

// ── Block types ───────────────────────────────────────────────────────────────

private sealed interface Block {
    data class Para(val spans: List<Span>)       : Block
    data class Code(val lang: String, val code: String) : Block
    data class Header(val level: Int, val text: String) : Block
    data class Bullet(val spans: List<Span>)     : Block
    data class Numbered(val n: Int, val spans: List<Span>) : Block
    data class Quote(val spans: List<Span>)      : Block
}

// ── Inline span types ─────────────────────────────────────────────────────────

private sealed interface Span {
    data class Plain(val text: String)  : Span
    data class Bold(val text: String)   : Span
    data class Italic(val text: String) : Span
    data class InlineCode(val text: String) : Span
    data class BoldItalic(val text: String) : Span
}

// ── Inline parser ─────────────────────────────────────────────────────────────

private val INLINE_RE = Regex("""`([^`]+)`|\*\*\*(.+?)\*\*\*|\*\*(.+?)\*\*|__(.+?)__|\*(.+?)\*|_(.+?)_""")

private fun parseSpans(text: String): List<Span> {
    val result = mutableListOf<Span>()
    var last = 0
    for (m in INLINE_RE.findAll(text)) {
        if (m.range.first > last) result += Span.Plain(text.substring(last, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> result += Span.InlineCode(m.groupValues[1])
            m.groupValues[2].isNotEmpty() -> result += Span.BoldItalic(m.groupValues[2])
            m.groupValues[3].isNotEmpty() -> result += Span.Bold(m.groupValues[3])
            m.groupValues[4].isNotEmpty() -> result += Span.Bold(m.groupValues[4])
            m.groupValues[5].isNotEmpty() -> result += Span.Italic(m.groupValues[5])
            m.groupValues[6].isNotEmpty() -> result += Span.Italic(m.groupValues[6])
        }
        last = m.range.last + 1
    }
    if (last < text.length) result += Span.Plain(text.substring(last))
    return result
}

// ── Block parser ──────────────────────────────────────────────────────────────

private val CODE_BLOCK_RE = Regex("""```(\w*)\n?([\s\S]*?)```""")
private val HEADER_RE     = Regex("""^(#{1,4})\s+(.+)$""")
private val BULLET_RE     = Regex("""^[-*]\s+(.+)$""")
private val NUMBERED_RE   = Regex("""^(\d+)\.\s+(.+)$""")
private val QUOTE_RE      = Regex("""^>\s*(.+)$""")

private fun parseBlocks(raw: String): List<Block> {
    // Normalise em/en dashes that trip up TTS and aren't needed visually
    val content = raw.replace('\u2014', '-').replace('\u2013', '-')
    val blocks  = mutableListOf<Block>()

    // Split on code fences first to protect their content
    val codeRanges = CODE_BLOCK_RE.findAll(content).map { it.range }.toList()
    if (codeRanges.isEmpty()) {
        parseNonCodeBlocks(content, blocks)
    } else {
        var pos = 0
        for (m in CODE_BLOCK_RE.findAll(content)) {
            if (m.range.first > pos) parseNonCodeBlocks(content.substring(pos, m.range.first), blocks)
            blocks += Block.Code(
                lang = m.groupValues[1].ifEmpty { "code" },
                code = m.groupValues[2].trimEnd(),
            )
            pos = m.range.last + 1
        }
        if (pos < content.length) parseNonCodeBlocks(content.substring(pos), blocks)
    }
    return blocks
}

private fun parseNonCodeBlocks(text: String, out: MutableList<Block>) {
    val paraLines = mutableListOf<String>()

    fun flushPara() {
        if (paraLines.isEmpty()) return
        val joined = paraLines.joinToString(" ")
        out += Block.Para(parseSpans(joined))
        paraLines.clear()
    }

    for (raw in text.split('\n')) {
        val line = raw.trimEnd()
        when {
            line.isBlank() -> flushPara()
            HEADER_RE.matches(line) -> {
                flushPara()
                val m = HEADER_RE.find(line)!!
                out += Block.Header(m.groupValues[1].length, m.groupValues[2])
            }
            BULLET_RE.matches(line) -> {
                flushPara()
                out += Block.Bullet(parseSpans(BULLET_RE.find(line)!!.groupValues[1]))
            }
            NUMBERED_RE.matches(line) -> {
                flushPara()
                val m = NUMBERED_RE.find(line)!!
                out += Block.Numbered(m.groupValues[1].toInt(), parseSpans(m.groupValues[2]))
            }
            QUOTE_RE.matches(line) -> {
                flushPara()
                out += Block.Quote(parseSpans(QUOTE_RE.find(line)!!.groupValues[1]))
            }
            else -> paraLines += line
        }
    }
    flushPara()
}

// ── AnnotatedString builder ───────────────────────────────────────────────────

@Composable
private fun spansToAnnotated(spans: List<Span>, baseColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    return buildAnnotatedString {
        for (span in spans) {
            when (span) {
                is Span.Plain -> withStyle(SpanStyle(color = baseColor)) { append(span.text) }
                is Span.Bold  -> withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold)) { append(span.text) }
                is Span.Italic -> withStyle(SpanStyle(color = baseColor, fontStyle = FontStyle.Italic)) { append(span.text) }
                is Span.BoldItalic -> withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append(span.text) }
                is Span.InlineCode -> withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = NxBg2,
                    color      = NxOrangeLit,
                    fontSize   = 11.sp,
                )) { append(" ${span.text} ") }
            }
        }
    }
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun RenderedMessage(content: String) {
    val context = LocalContext.current
    val blocks  = remember(content) { parseBlocks(content) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is Block.Para -> {
                    if (block.spans.any { it !is Span.Plain || (it as Span.Plain).text.isNotBlank() }) {
                        SelectionContainer {
                            Text(
                                text      = spansToAnnotated(block.spans, NxOrange),
                                style     = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            )
                        }
                    }
                }
                is Block.Header -> {
                    val sz = when (block.level) { 1 -> 18.sp; 2 -> 16.sp; 3 -> 14.sp; else -> 13.sp }
                    Text(
                        block.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize   = sz,
                            fontWeight = FontWeight.Bold,
                            lineHeight = sz * 1.4f,
                        ),
                        color = NxOrange,
                    )
                }
                is Block.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", color = NxOrangeDim, style = MaterialTheme.typography.bodyMedium)
                        SelectionContainer {
                            Text(
                                text  = spansToAnnotated(block.spans, NxOrange),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            )
                        }
                    }
                }
                is Block.Numbered -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${block.n}.", color = NxOrangeDim,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.widthIn(min = 20.dp))
                        SelectionContainer {
                            Text(
                                text  = spansToAnnotated(block.spans, NxOrange),
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                            )
                        }
                    }
                }
                is Block.Quote -> {
                    Row(
                        Modifier.background(NxBg3, RoundedCornerShape(2.dp)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(NxOrangeDim)
                        )
                        Spacer(Modifier.width(4.dp))
                        SelectionContainer {
                            Text(
                                text     = spansToAnnotated(block.spans, NxFg2),
                                style    = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
                is Block.Code -> CodeBlock(block.lang, block.code, context)
            }
        }
    }
}

// ── Code block ────────────────────────────────────────────────────────────────

@Composable
private fun CodeBlock(lang: String, code: String, context: Context) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }

    Column(
        Modifier
            .fillMaxWidth()
            .background(NxBg2, RoundedCornerShape(4.dp))
    ) {
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
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
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
                    "Copy",
                    tint     = if (copied) NxOrange else NxFg2,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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

// ── Typing indicator ──────────────────────────────────────────────────────────

@Composable
fun TypingIndicatorDots() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(380); step = (step + 1) % 4 } }
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        repeat(3) { i ->
            Text(
                "▲",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (i < step) NxOrange else NxOrangeDim.copy(alpha = 0.25f),
            )
        }
    }
}

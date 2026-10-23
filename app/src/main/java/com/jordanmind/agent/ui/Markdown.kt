package com.jordanmind.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.ui.theme.Accent

/**
 * A deliberately small Markdown reader.
 *
 * A phone-sized model emits headings, lists, fenced code and the odd bold run, and rendering that
 * as one flat paragraph is what makes an agent UI feel like a form rather than a chat. Pulling in a
 * full Markdown library would drag in a parser, an AST and an HTML renderer to cover tables, HTML
 * blocks and reference links that this model never produces, so the subset it actually uses is
 * parsed here instead.
 *
 * Anything unrecognised falls through to a paragraph, so malformed output still renders as text
 * rather than disappearing.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Ordered(val items: List<String>) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Divider : MdBlock
}

private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
private val HEADING = Regex("""^\s*(#{1,6})\s+(.*)$""")
private val FENCE = Regex("""^\s*```\s*([A-Za-z0-9+#._-]*)\s*$""")
private val QUOTE = Regex("""^\s*>\s?(.*)$""")
private val DIVIDER = Regex("""^\s*([-*_])\1{2,}\s*$""")

/** Parses [source] into the block types above. Never throws; unknown lines become paragraphs. */
fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.replace("\r\n", "\n").split('\n')
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        when {
            line.isBlank() -> i++

            FENCE.matches(line) -> {
                val language = FENCE.matchEntire(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                val body = StringBuilder()
                i++
                // An unterminated fence runs to the end of the message: the model may still be
                // streaming, so showing the code is better than dropping it.
                while (i < lines.size && !FENCE.matches(lines[i])) {
                    body.appendLine(lines[i])
                    i++
                }
                if (i < lines.size) i++
                blocks += MdBlock.Code(language, body.toString().trimEnd())
            }

            HEADING.matches(line) -> {
                val m = HEADING.matchEntire(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                i++
            }

            DIVIDER.matches(line) -> {
                blocks += MdBlock.Divider
                i++
            }

            BULLET.matches(line) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = BULLET.matchEntire(lines[i]) ?: break
                    items += m.groupValues[1].trim()
                    i++
                }
                blocks += MdBlock.Bullet(items)
            }

            ORDERED.matches(line) -> {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = ORDERED.matchEntire(lines[i]) ?: break
                    items += m.groupValues[2].trim()
                    i++
                }
                blocks += MdBlock.Ordered(items)
            }

            QUOTE.matches(line) -> {
                val quoted = mutableListOf<String>()
                while (i < lines.size) {
                    val m = QUOTE.matchEntire(lines[i]) ?: break
                    quoted += m.groupValues[1].trim()
                    i++
                }
                blocks += MdBlock.Quote(quoted.joinToString(" "))
            }

            else -> {
                val paragraph = mutableListOf<String>()
                while (i < lines.size) {
                    val candidate = lines[i]
                    if (candidate.isBlank() || HEADING.matches(candidate) || BULLET.matches(candidate) ||
                        ORDERED.matches(candidate) || FENCE.matches(candidate) ||
                        QUOTE.matches(candidate) || DIVIDER.matches(candidate)
                    ) {
                        break
                    }
                    paragraph += candidate.trim()
                    i++
                }
                blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
    }
    return blocks
}

private val INLINE = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)""")

/** Renders `**bold**`, `*italic*` and `` `code` `` inside a single line of text. */
fun inlineMarkdown(text: String, codeColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }
            token.startsWith("`") ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                    append(token.removeSurrounding("`"))
                }
            else ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.removeSurrounding("*"))
                }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/** Renders a model reply as blocks. [text] is shown as-is when it parses to nothing. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val codeColor = Accent.Amber
    val blocks = parseMarkdown(text)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineMarkdown(block.text, codeColor),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MdBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, codeColor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = Accent.Gold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                inlineMarkdown(item, codeColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                is MdBlock.Ordered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${index + 1}.",
                                color = Accent.Gold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                inlineMarkdown(item, codeColor),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                is MdBlock.Code -> CodeBlock(block.language, block.code)

                is MdBlock.Quote -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .size(width = 3.dp, height = 18.dp)
                            .background(Accent.Gold, RoundedCornerShape(2.dp)),
                    )
                    Text(
                        inlineMarkdown(block.text, codeColor),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .size(width = 0.dp, height = 1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
    }
}

/**
 * Code is scrollable rather than wrapped: a wrapped command is easy to misread and hard to copy,
 * and the copy button is the whole point of rendering it separately.
 */
@Composable
private fun CodeBlock(language: String?, code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0803), RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.takeIf { it.isNotBlank() } ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = code,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE8E2D2),
        )
    }
}

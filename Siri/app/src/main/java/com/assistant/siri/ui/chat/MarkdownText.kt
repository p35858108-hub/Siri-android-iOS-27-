package com.assistant.siri.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.assistant.siri.ui.theme.CapsuleColors

// ---------- Modelo ----------

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String, val depth: Int) : MdBlock
    data class Numbered(val number: String, val text: String, val depth: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val HeadingRegex = Regex("^\\s{0,3}(#{1,6})\\s+(.+)$")
private val RuleRegex = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val BulletRegex = Regex("^(\\s*)[-*+•]\\s+(.+)$")
private val NumberedRegex = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.+)$")
private val QuoteRegex = Regex("^\\s*>\\s?(.*)$")

/**
 * Divide el texto en bloques. Tolera texto a medias: un bloque de código sin cerrar
 * se muestra como código hasta que llega el resto.
 */
internal fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val code = StringBuilder()
    var inCode = false

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    for (rawLine in source.lines()) {
        val line = rawLine.trimEnd()

        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.toString().trimEnd('\n'))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(rawLine).append('\n')
            continue
        }
        if (line.isBlank()) {
            flushParagraph()
            continue
        }
        if (RuleRegex.matches(line)) {
            flushParagraph()
            continue
        }

        val heading = HeadingRegex.matchEntire(line)
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            continue
        }

        val bullet = BulletRegex.matchEntire(line)
        if (bullet != null) {
            flushParagraph()
            blocks += MdBlock.Bullet(bullet.groupValues[2].trim(), bullet.groupValues[1].length / 2)
            continue
        }

        val numbered = NumberedRegex.matchEntire(line)
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.Numbered(
                number = numbered.groupValues[2],
                text = numbered.groupValues[3].trim(),
                depth = numbered.groupValues[1].length / 2
            )
            continue
        }

        val quote = QuoteRegex.matchEntire(line)
        if (quote != null) {
            flushParagraph()
            val last = blocks.lastOrNull()
            if (last is MdBlock.Quote) {
                blocks[blocks.lastIndex] = MdBlock.Quote(last.text + "\n" + quote.groupValues[1])
            } else {
                blocks += MdBlock.Quote(quote.groupValues[1])
            }
            continue
        }

        paragraph += line
    }

    if (inCode) blocks += MdBlock.Code(code.toString().trimEnd('\n'))
    flushParagraph()
    return blocks
}

// ---------- Texto en línea: **negrita**, *cursiva*, `código`, [enlace](url) ----------

internal fun buildInlineText(text: String, codeBackground: Color, linkColor: Color): AnnotatedString =
    buildAnnotatedString { appendInline(text, codeBackground, linkColor) }

private fun AnnotatedString.Builder.appendInline(text: String, codeBackground: Color, linkColor: Color) {
    val literal = StringBuilder()

    fun flush() {
        if (literal.isNotEmpty()) {
            append(literal.toString())
            literal.clear()
        }
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]

        if (c == '*' && text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    appendInline(text.substring(i + 2, end), codeBackground, linkColor)
                }
                i = end + 2
            } else {
                // Marcador sin cerrar (la respuesta aún está llegando): se muestra tal cual
                literal.append("**")
                i += 2
            }
            continue
        }

        if (c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace()) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1 && !text[end - 1].isWhitespace()) {
                flush()
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInline(text.substring(i + 1, end), codeBackground, linkColor)
                }
                i = end + 1
                continue
            }
        }

        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 0.92.em,
                        background = codeBackground
                    )
                ) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }

        if (c == '[') {
            val close = text.indexOf("](", i + 1)
            val paren = if (close > i) text.indexOf(')', close + 2) else -1
            if (close > i + 1 && paren > close) {
                flush()
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(text.substring(i + 1, close))
                }
                i = paren + 1
                continue
            }
        }

        literal.append(c)
        i++
    }
    flush()
}

// ---------- Dibujo ----------

/** Texto con formato Markdown sobre fondo oscuro, con jerarquía clara y buen interlineado. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = CapsuleColors.TextPrimary,
    mutedColor: Color = CapsuleColors.TextSecondary
) {
    val blocks = remember(text) { parseMarkdown(text) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Paragraph -> InlineText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )

                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.labelLarge
                    }
                    InlineText(
                        text = block.text,
                        style = style,
                        color = textColor,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp)
                    )
                }

                is MdBlock.Bullet -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = mutedColor,
                        modifier = Modifier.width(18.dp)
                    )
                    InlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Numbered -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
                    Text(
                        text = "${block.number}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = mutedColor,
                        modifier = Modifier.width(28.dp)
                    )
                    InlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(CapsuleColors.Accent.copy(alpha = 0.7f))
                    )
                    Spacer(Modifier.width(12.dp))
                    InlineText(
                        text = block.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = mutedColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF17171B))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = block.text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        color = textColor.copy(alpha = 0.92f),
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text) {
        buildInlineText(
            text = text,
            codeBackground = Color(0x24FFFFFF),
            linkColor = CapsuleColors.Accent
        )
    }
    Text(text = annotated, style = style, color = color, modifier = modifier)
}

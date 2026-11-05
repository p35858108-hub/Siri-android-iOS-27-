package com.assistant.siri.voice

/** Prepara el texto de una respuesta para ser leído en voz alta. */
internal object SpokenText {

    private val codeBlock = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`([^`]*)`")
    private val link = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val heading = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val bullet = Regex("(?m)^\\s*[-*+•]\\s+")
    private val quote = Regex("(?m)^\\s*>\\s?")
    private val emphasis = Regex("(\\*\\*|__|\\*|~~)")
    private val spaces = Regex("[ \\t]+")
    private val blankLines = Regex("\\n{3,}")
    private val sentenceBoundary = Regex("(?<=[.!?…])\\s+|\\n+")

    /** Quita los símbolos de Markdown y los bloques de código, que no se leen bien. */
    fun fromMarkdown(text: String): String = text
        .replace(codeBlock, " ")
        .replace(inlineCode, "\$1")
        .replace(link, "\$1")
        .replace(heading, "")
        .replace(bullet, "")
        .replace(quote, "")
        .replace(emphasis, "")
        .replace(spaces, " ")
        .replace(blankLines, "\n\n")
        .trim()

    /** Divide el texto en trozos de como máximo [maxLength] caracteres, cortando en frases. */
    fun split(text: String, maxLength: Int): List<String> {
        val sentences = text.split(sentenceBoundary).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            if (current.isNotBlank()) {
                chunks += current.toString().trim()
                current.clear()
            }
        }

        for (sentence in sentences) {
            if (sentence.length > maxLength) {
                flush()
                sentence.chunked(maxLength)
                    .filter { it.isNotBlank() }
                    .forEach { chunks += it.trim() }
                continue
            }
            if (current.length + sentence.length + 1 > maxLength) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        flush()

        return chunks.ifEmpty { listOf(text.take(maxLength)) }
    }
}

package com.adamfoerster.mdhabits.data.markdown

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A Markdown note split into YAML frontmatter fields and a free-text body. Field values are
 * written as JSON literals, which is valid YAML 1.2 flow syntax, so Obsidian renders the
 * frontmatter as properties while parsing stays unambiguous. Parsing is lenient (unquoted
 * strings from manual edits are accepted) and malformed lines are skipped, never fatal.
 */
data class MarkdownDoc(
    val fields: Map<String, JsonElement> = emptyMap(),
    val body: String = "",
)

/** Lenient so hand-edited frontmatter values like `name: Meditation` still parse as strings. */
internal val markdownJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object Frontmatter {
    private const val FENCE = "---"

    fun render(doc: MarkdownDoc): String = buildString {
        appendLine(FENCE)
        doc.fields.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine(FENCE)
        if (doc.body.isNotBlank()) {
            appendLine()
            appendLine(doc.body.trimEnd())
        }
    }

    fun parse(text: String): MarkdownDoc {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != FENCE) return MarkdownDoc(body = text.trim())
        val closing = lines.drop(1).indexOfFirst { it.trim() == FENCE }
        if (closing == -1) return MarkdownDoc(body = text.trim())

        val fields = lines.subList(1, 1 + closing).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val key = line.take(separator).trim()
            val raw = line.substring(separator + 1).trim()
            if (raw.isEmpty()) return@mapNotNull null
            runCatching { key to markdownJson.parseToJsonElement(raw) }.getOrNull()
        }.toMap()

        val body = lines.drop(closing + 2).joinToString("\n").trim()
        return MarkdownDoc(fields, body)
    }
}

// Typed readers used by the entity codecs. All return null on a missing or mistyped field so a
// manually broken note degrades to defaults instead of crashing the load.

internal fun MarkdownDoc.string(key: String): String? =
    (fields[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun MarkdownDoc.int(key: String): Int? = (fields[key] as? JsonPrimitive)?.intOrNull

internal fun MarkdownDoc.boolean(key: String): Boolean? =
    (fields[key] as? JsonPrimitive)?.booleanOrNull

internal fun MarkdownDoc.stringList(key: String): List<String> =
    runCatching { fields[key]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull().orEmpty()

package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.security.MessageDigest

/** Glossary categories, matching OpenNovel's so its CSV exports import as-is. */
enum class GlossaryType(val csvName: String, val label: String) {
    CHARACTER("character", "Character"),
    TITLE("title", "Title"),
    TERM("term", "Term"),
    ;

    companion object {
        fun fromCsv(value: String): GlossaryType =
            entries.firstOrNull { it.csvName.equals(value.trim(), ignoreCase = true) } ?: TERM
    }
}

/** Character gender, used to pick pronouns. Korean and Japanese often drop the subject entirely. */
enum class GlossaryGender(val csvName: String, val label: String) {
    MALE("male", "Male"),
    FEMALE("female", "Female"),
    UNDECIDED("undecided", "Not set"),
    ;

    companion object {
        fun fromCsv(value: String?): GlossaryGender =
            entries.firstOrNull { it.csvName.equals(value?.trim(), ignoreCase = true) } ?: UNDECIDED
    }
}

data class GlossaryEntry(
    val type: GlossaryType,
    /** The term as it appears in the source text. */
    val raw: String,
    /** How it must always be translated. */
    val translated: String,
    /** Only meaningful for [GlossaryType.CHARACTER]. */
    val gender: GlossaryGender = GlossaryGender.UNDECIDED,
)

/** Per-novel translation state: whether it's switched on, and its glossary. */
data class NovelTranslationConfig(
    val enabled: Boolean = false,
    val glossary: List<GlossaryEntry> = emptyList(),
)

/**
 * Files on disk for translation, under `filesDir/novel_translation/<novel key>/`:
 * `config.json` (on/off + glossary) and `chapters/<chapter key>.json` (saved translations).
 *
 * Plain files rather than database tables: no schema migration, and losing them only costs a
 * re-translation. The novel key is derived from source id + novel URL instead of the library id,
 * which changes when a backup is restored into a fresh install.
 *
 * Saved translations are **not** invalidated by glossary edits. A glossary change applies to
 * chapters translated afterwards; an already-translated chapter picks it up through "Retranslate
 * chapter". Invalidating automatically would re-bill every cached chapter after each small edit.
 * A saved translation *is* dropped if the original's paragraph list changed underneath it, since
 * the two must line up paragraph for paragraph.
 */
class NovelTranslationStore(context: Context) {

    private val root = context.filesDir.resolve("novel_translation")
    private val json = Json { prettyPrint = false }

    fun novelKey(sourceId: Long, novelUrl: String): String = sha256("$sourceId|$novelUrl").take(24)

    fun loadConfig(novelKey: String): NovelTranslationConfig {
        val file = configFile(novelKey)
        if (!file.exists()) return NovelTranslationConfig()
        return try {
            val obj = json.parseToJsonElement(file.readText()).jsonObject
            NovelTranslationConfig(
                enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                glossary = obj["glossary"]?.jsonArray.orEmpty().mapNotNull { element ->
                    val entry = element.jsonObject
                    val raw = entry["raw"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val translated = entry["translated"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (raw.isBlank()) return@mapNotNull null
                    GlossaryEntry(
                        type = GlossaryType.fromCsv(entry["type"]?.jsonPrimitive?.contentOrNull.orEmpty()),
                        raw = raw,
                        translated = translated,
                        gender = GlossaryGender.fromCsv(entry["gender"]?.jsonPrimitive?.contentOrNull),
                    )
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Unreadable translation config for $novelKey" }
            NovelTranslationConfig()
        }
    }

    fun saveConfig(novelKey: String, config: NovelTranslationConfig) {
        val obj = buildJsonObject {
            put("enabled", config.enabled)
            put(
                "glossary",
                buildJsonArray {
                    config.glossary.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("type", entry.type.csvName)
                                put("raw", entry.raw)
                                put("translated", entry.translated)
                                put("gender", entry.gender.csvName)
                            },
                        )
                    }
                },
            )
        }
        writeAtomically(configFile(novelKey), obj.toString())
    }

    /** The saved translation of [original], or null if there is none or it no longer lines up. */
    fun loadChapter(novelKey: String, chapterUrl: String, targetLanguage: String, original: List<String>): List<String>? {
        val file = chapterFile(novelKey, chapterUrl, targetLanguage)
        if (!file.exists()) return null
        return try {
            val obj = json.parseToJsonElement(file.readText()).jsonObject
            if (obj["source"]?.jsonPrimitive?.contentOrNull != fingerprint(original)) return null
            val paragraphs = obj["paragraphs"]!!.jsonArray.map { it.jsonPrimitive.content }
            paragraphs.takeIf { it.size == original.size }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Unreadable saved translation for $chapterUrl" }
            null
        }
    }

    fun saveChapter(
        novelKey: String,
        chapterUrl: String,
        targetLanguage: String,
        original: List<String>,
        translated: List<String>,
    ) {
        val obj = JsonObject(
            mapOf(
                "source" to JsonPrimitive(fingerprint(original)),
                "paragraphs" to JsonArray(translated.map(::JsonPrimitive)),
            ),
        )
        writeAtomically(chapterFile(novelKey, chapterUrl, targetLanguage), obj.toString())
    }

    fun deleteChapter(novelKey: String, chapterUrl: String, targetLanguage: String) {
        chapterFile(novelKey, chapterUrl, targetLanguage).delete()
    }

    private fun configFile(novelKey: String) = root.resolve(novelKey).resolve("config.json")

    private fun chapterFile(novelKey: String, chapterUrl: String, targetLanguage: String) =
        root.resolve(novelKey).resolve("chapters").resolve(sha256("$chapterUrl|$targetLanguage").take(32) + ".json")

    private fun fingerprint(original: List<String>) = sha256(original.joinToString("\u0000"))

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** OpenNovel's glossary CSV: `type,raw_term,translated_term,gender`, gender only on characters. */
object GlossaryCsv {

    private const val HEADER = "type,raw_term,translated_term,gender"

    fun export(entries: List<GlossaryEntry>): String = buildString {
        appendLine(HEADER)
        entries.forEach { entry ->
            val fields = mutableListOf(entry.type.csvName, entry.raw, entry.translated)
            if (entry.type == GlossaryType.CHARACTER) fields += entry.gender.csvName
            appendLine(fields.joinToString(",") { escape(it) })
        }
    }

    /** Parses a CSV, skipping a header row and rows without a raw term. */
    fun parse(text: String): List<GlossaryEntry> = parseRows(text.removePrefix("\uFEFF"))
        .filterNot { it.firstOrNull()?.trim().equals("type", ignoreCase = true) }
        .mapNotNull { row ->
            val raw = row.getOrNull(1)?.trim().orEmpty()
            if (raw.isEmpty()) return@mapNotNull null
            val type = GlossaryType.fromCsv(row.getOrNull(0).orEmpty())
            GlossaryEntry(
                type = type,
                raw = raw,
                translated = row.getOrNull(2)?.trim().orEmpty(),
                gender = if (type == GlossaryType.CHARACTER) GlossaryGender.fromCsv(row.getOrNull(3)) else GlossaryGender.UNDECIDED,
            )
        }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** RFC 4180-style: quoted fields may contain commas, quotes ("") and newlines. */
    private fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row += field.toString(); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}

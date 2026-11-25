package eu.kanade.tachiyomi.ui.reader.translation

import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import eu.kanade.tachiyomi.ui.reader.setting.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A translation failure worth showing the reader as-is. [httpCode] is set for HTTP errors. */
class TranslationException(
    message: String,
    val httpCode: Int? = null,
    /** Server's "try again in N seconds" hint on rate limits, when it gave one. */
    val retryAfterSeconds: Int? = null,
) : Exception(message)

data class TranslationSettings(
    val provider: TranslationProvider,
    /** One or more keys, separated by commas, spaces or new lines; see [NovelTranslator.request]. */
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val targetLanguage: String,
    /** Tried when [model] stays overloaded; only set when the user left the model on default. */
    val fallbackModel: String? = null,
)

/**
 * Translates a chapter with an LLM, **one output paragraph per input paragraph**.
 *
 * The 1:1 alignment is the whole design: reading progress is stored as a paragraph index, so the
 * translated chapter must have exactly as many paragraphs as the original for saved progress, the
 * progress percent and switching between original and translation to stay put. The model gets
 * the paragraphs as a JSON array and must answer with an array of the same length; a chunk that
 * comes back the wrong length is retried once, then split in half, and a single paragraph that
 * still can't be aligned keeps its original text rather than shifting everything after it.
 *
 * Long chapters are sent in chunks ([MAX_CHUNK_CHARS]), each with the previous chunk's last
 * translated paragraphs as context so names and tone carry across the seam.
 */
class NovelTranslator(baseClient: OkHttpClient) {

    private val client = baseClient.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Round-robin counter: each request starts on the next key, so parallel chunks spread over
     * all keys instead of piling onto one and hitting its per-minute limit.
     */
    private val keyCounter = AtomicInteger(0)

    /** Set once a Gemini model rejects `thinkingConfig`, so later requests stop sending it. */
    @Volatile private var thinkingUnsupported = false

    /**
     * Translates [paragraphs]. Chunks run up to [PARALLEL_REQUESTS] at a time, and [onPartial] gets
     * the result so far (null = not translated yet) every time a chunk lands, so the reader can
     * show the start of a long chapter while the rest is still on its way.
     */
    suspend fun translate(
        paragraphs: List<String>,
        glossary: List<GlossaryEntry>,
        settings: TranslationSettings,
        onPartial: (translated: List<String?>, doneChunks: Int, totalChunks: Int) -> Unit = { _, _, _ -> },
    ): List<String> = coroutineScope {
        if (settings.apiKey.isBlank()) {
            throw TranslationException("Add an API key in Settings → Reader → Novel → Translation.")
        }
        val model = settings.model.ifBlank { settings.provider.defaultModel }
        if (model.isBlank()) {
            throw TranslationException("Set a model in Settings → Reader → Novel → Translation.")
        }
        val resolved = settings.copy(
            model = model,
            fallbackModel = if (settings.model.isBlank()) settings.provider.fallbackModel else null,
        )

        val ranges = chunkRanges(paragraphs)
        val out = arrayOfNulls<String>(paragraphs.size)
        val lock = Mutex()
        val permits = Semaphore(PARALLEL_REQUESTS)
        var done = 0
        onPartial(out.toList(), 0, ranges.size)
        ranges.map { range ->
            async {
                permits.withPermit {
                    val chunk = paragraphs.subList(range.first, range.last + 1)
                    // Chunks run in parallel, so the context is the *original* text just before
                    // the chunk rather than its translation, which may not exist yet.
                    val context = paragraphs.subList(maxOf(0, range.first - CONTEXT_PARAGRAPHS), range.first)
                    val translated = translateAligned(chunk, glossary, resolved, context)
                    lock.withLock {
                        translated.forEachIndexed { i, text -> out[range.first + i] = text }
                        done++
                        onPartial(out.toList(), done, ranges.size)
                    }
                }
            }
        }.awaitAll()
        out.map { it.orEmpty() }
    }

    private suspend fun translateAligned(
        chunk: List<String>,
        glossary: List<GlossaryEntry>,
        settings: TranslationSettings,
        context: List<String>,
    ): List<String> {
        // Paragraphs with nothing to translate (scene breaks like "***", bare numbers) skip the API.
        if (chunk.all { !it.any(Char::isLetter) }) return chunk

        repeat(2) {
            val out = request(chunk, glossary, settings, context)
            if (out.size == chunk.size) return out
        }
        if (chunk.size == 1) return chunk // can't be aligned; keep the original line
        val half = chunk.size / 2
        val first = translateAligned(chunk.subList(0, half), glossary, settings, context)
        val second = translateAligned(chunk.subList(half, chunk.size), glossary, settings, chunk.subList(0, half).takeLast(CONTEXT_PARAGRAPHS))
        return first + second
    }

    private suspend fun request(
        chunk: List<String>,
        glossary: List<GlossaryEntry>,
        settings: TranslationSettings,
        context: List<String>,
    ): List<String> {
        val system = systemPrompt(settings.targetLanguage, glossary, chunk)
        val user = buildString {
            if (context.isNotEmpty()) {
                append("The paragraphs just before these in the original, for context only (do not translate or output them):\n")
                append(JsonArray(context.map(::JsonPrimitive)).toString())
                append("\n\n")
            }
            append("Translate these ${chunk.size} paragraphs. Reply with {\"paragraphs\": [...]} holding exactly ${chunk.size} strings.\n")
            append(buildJsonObject { put("paragraphs", JsonArray(chunk.map(::JsonPrimitive))) }.toString())
        }
        return parseParagraphs(callWithRetry(system, user, settings))
    }

    /**
     * What happens when the model can't take the request right now:
     * - Overload (HTTP 5xx, e.g. Gemini's "model is currently experiencing high demand") is
     *   temporary and hits every key alike, so it is retried with short waits.
     * - Quota (HTTP 429 on every key) gets one wait if the server says it clears within
     *   [MAX_QUOTA_WAIT_SECONDS] (a per-minute limit); otherwise (a daily limit) it moves on.
     * Either way, if the user is on the default model, the provider's lighter
     * [TranslationSettings.fallbackModel] gets the request next: on Gemini, Flash-Lite has its
     * own quota, so reading carries on after Flash's free allowance runs out.
     */
    private suspend fun callWithRetry(system: String, user: String, settings: TranslationSettings): String {
        val models = listOfNotNull(settings.model, settings.fallbackModel).distinct()
        var lastError: TranslationException? = null
        for (model in models) {
            var waitedForQuota = false
            for (wait in RETRY_WAITS_MS) {
                try {
                    return callWithKeyFailover(system, user, settings.copy(model = model))
                } catch (e: TranslationException) {
                    lastError = e
                    val code = e.httpCode ?: throw e
                    if (code in 500..599) {
                        delay(wait)
                        continue
                    }
                    if (code != 429) throw e
                    val hint = e.retryAfterSeconds
                    if (waitedForQuota || hint == null || hint > MAX_QUOTA_WAIT_SECONDS) break
                    waitedForQuota = true
                    delay(hint * 1_000L + 500L)
                }
            }
        }
        throw lastError!!
    }

    /**
     * Several keys may be given. A key that hits its quota or rate limit (HTTP 429) or is rejected
     * (401/403) hands the same request to the next one; only when every key failed does the error
     * reach the reader. Each request starts on the next key in turn ([keyCounter]), which spreads
     * parallel chunks over all keys.
     */
    private suspend fun callWithKeyFailover(system: String, user: String, settings: TranslationSettings): String {
        val keys = settings.apiKey.split(KEY_SEPARATOR).map(String::trim).filter(String::isNotEmpty).distinct()
        if (keys.isEmpty()) throw TranslationException("Add an API key in Settings → Reader → Novel → Translation.")
        var lastError: TranslationException? = null
        var shortestRetry: Int? = null
        val start = Math.floorMod(keyCounter.getAndIncrement(), keys.size)
        for (attempt in keys.indices) {
            val index = (start + attempt) % keys.size
            val keyed = settings.copy(apiKey = keys[index])
            try {
                val text = when (settings.provider) {
                    TranslationProvider.GEMINI -> callGemini(system, user, keyed)
                    TranslationProvider.OPENAI_COMPATIBLE -> callOpenAiCompatible(system, user, keyed)
                }
                return text
            } catch (e: TranslationException) {
                // Gemini reports a bad key as 400 "API key not valid" rather than 401/403.
                val badKey = e.httpCode == 400 && e.message.orEmpty().contains("API key", ignoreCase = true)
                if (e.httpCode !in FAILOVER_CODES && !badKey) throw e
                lastError = e
                e.retryAfterSeconds?.let { s -> shortestRetry = minOf(shortestRetry ?: s, s) }
            }
        }
        val error = lastError!!
        throw if (keys.size > 1) {
            TranslationException("All ${keys.size} API keys failed. ${error.message}", error.httpCode, shortestRetry)
        } else {
            error
        }
    }

    private suspend fun callGemini(system: String, user: String, settings: TranslationSettings): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { add(buildJsonObject { put("text", user) }) }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
                // Flash models "think" before answering by default, which only adds delay to a
                // translation. Models that don't know the setting reject it once; see below.
                if (!thinkingUnsupported) putJsonObject("thinkingConfig") { put("thinkingLevel", "low") }
            }
            // Novels routinely contain violence and romance; the default filters cut chapters off.
            putJsonArray("safetySettings") {
                listOf(
                    "HARM_CATEGORY_HARASSMENT",
                    "HARM_CATEGORY_HATE_SPEECH",
                    "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                    "HARM_CATEGORY_DANGEROUS_CONTENT",
                ).forEach { category ->
                    add(
                        buildJsonObject {
                            put("category", category)
                            put("threshold", "BLOCK_NONE")
                        },
                    )
                }
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${settings.model}:generateContent")
            .header("x-goog-api-key", settings.apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val root = try {
            execute(request, "Gemini")
        } catch (e: TranslationException) {
            if (e.httpCode == 400 && !thinkingUnsupported && e.message.orEmpty().contains("thinking", ignoreCase = true)) {
                thinkingUnsupported = true
                return callGemini(system, user, settings)
            }
            throw e
        }

        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val reason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw TranslationException("Gemini returned nothing" + (reason?.let { " (blocked: $it)" } ?: "") + ".")
        }
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
            .orEmpty()
        if (text.isBlank()) {
            val reason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
            throw TranslationException("Gemini returned an empty answer" + (reason?.let { " ($it)" } ?: "") + ".")
        }
        return text
    }

    private suspend fun callOpenAiCompatible(system: String, user: String, settings: TranslationSettings): String {
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.3)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", system)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", user)
                    },
                )
            }
        }
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val root = execute(request, "The translation service")
        return root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw TranslationException("The translation service returned an empty answer.")
    }

    private suspend fun execute(request: Request, serviceName: String): JsonObject {
        val response = try {
            client.newCall(request).await()
        } catch (e: java.io.IOException) {
            throw TranslationException("Couldn't reach $serviceName: ${e.message ?: "network error"}.")
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val detail = runCatching {
                    val error = json.parseToJsonElement(text).jsonObject["error"]
                    (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: (error as? JsonPrimitive)?.contentOrNull
                }.getOrNull()?.take(200)
                val hint = when (it.code) {
                    400 -> if (detail?.contains("API key", ignoreCase = true) == true) "The API key isn't valid." else "The request was rejected."
                    401, 403 -> "The API key was rejected."
                    404 -> "The model wasn't found. Check the model name in settings."
                    429 -> "Rate limit or free quota reached. Wait a minute and try again."
                    in 500..599 -> "$serviceName is having problems right now. Try again later."
                    else -> "$serviceName answered HTTP ${it.code}."
                }
                val retryAfter = it.header("Retry-After")?.trim()?.toIntOrNull()
                    ?: RETRY_DELAY_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
                throw TranslationException(hint + (detail?.let { d -> " ($d)" } ?: ""), it.code, retryAfter)
            }
            return try {
                json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw TranslationException("$serviceName sent an unreadable answer.")
            }
        }
    }

    /** Pulls the paragraph array out of a reply, tolerating code fences and a bare array. */
    private fun parseParagraphs(text: String): List<String> {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val element: JsonElement = runCatching { json.parseToJsonElement(cleaned) }.getOrElse {
            val start = cleaned.indexOfFirst { c -> c == '{' || c == '[' }
            val end = cleaned.indexOfLast { c -> c == '}' || c == ']' }
            if (start < 0 || end <= start) return emptyList()
            runCatching { json.parseToJsonElement(cleaned.substring(start, end + 1)) }.getOrElse { return emptyList() }
        }
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["paragraphs"] as? JsonArray ?: element.values.firstOrNull { it is JsonArray } as? JsonArray
            else -> null
        } ?: return emptyList()
        return array.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
    }

    private fun systemPrompt(targetLanguage: String, glossary: List<GlossaryEntry>, chunk: List<String>): String = buildString {
        append("You are a professional literary translator of web novels. Translate the user's paragraphs into ")
        append(targetLanguage)
        append(" so they read like a published, natural translation, keeping the author's tone, humor and style.\n\n")
        append("Rules:\n")
        append("- Answer with JSON only: {\"paragraphs\": [...]}, exactly one output string per input paragraph, in the same order. ")
        append("Never merge, split, skip, summarize or add paragraphs.\n")
        append("- Keep inline HTML tags (<b>, <i>, <em>, <strong>, <u>, <br>) around the words they belong to.\n")
        append("- Paragraphs that are only symbols, numbers or scene breaks (like ***) stay unchanged.\n")
        append("- Romanize personal names consistently; keep honorifics (-nim, -ssi, -sama, senpai, shixiong...) romanized.\n")
        append("- Use correct pronouns from context; the source language often omits subjects.\n")

        // Only send glossary entries that occur in this chunk: keeps prompts small on big glossaries.
        val joined = chunk.joinToString("\n")
        val relevant = glossary.filter { it.raw.isNotBlank() && it.translated.isNotBlank() && joined.contains(it.raw) }
        if (relevant.isNotEmpty()) {
            append("\nGlossary - always translate these exactly as given:\n")
            relevant.forEach { entry ->
                append("- ").append(entry.raw).append(" → ").append(entry.translated)
                append(" (").append(entry.type.label.lowercase())
                if (entry.type == GlossaryType.CHARACTER && entry.gender != GlossaryGender.UNDECIDED) {
                    append(", ").append(entry.gender.csvName)
                }
                append(")\n")
            }
        }
    }

    /** Index ranges of at most [MAX_CHUNK_CHARS] characters / [MAX_CHUNK_PARAGRAPHS] paragraphs each. */
    private fun chunkRanges(paragraphs: List<String>): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        var chars = 0
        paragraphs.forEachIndexed { i, p ->
            val count = i - start
            if (count > 0 && (chars + p.length > MAX_CHUNK_CHARS || count >= MAX_CHUNK_PARAGRAPHS)) {
                ranges += start until i
                start = i
                chars = 0
            }
            chars += p.length
        }
        if (start < paragraphs.size) ranges += start until paragraphs.size
        return ranges
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Free tiers limit **requests per day**, not their size, so a chunk is as big as is safe:
         * most chapters go in one request. (Small parallel chunks felt faster but burned a whole
         * day's free Gemini quota in a few chapters.) 8,000 CJK characters is ~3-4k output tokens.
         */
        const val MAX_CHUNK_CHARS = 8_000
        const val MAX_CHUNK_PARAGRAPHS = 200

        /**
         * Chunks translated at the same time. Requests rotate over the keys, so with several keys
         * this stays under each key's per-minute limit; with one key a 429 fails over or retries.
         */
        const val PARALLEL_REQUESTS = 2
        const val CONTEXT_PARAGRAPHS = 3

        val KEY_SEPARATOR = Regex("[,;\\s]+")
        val FAILOVER_CODES = setOf(401, 403, 429)

        /** Gemini puts the rate-limit wait in `error.details[].retryDelay`, e.g. "23s" or "23.5s". */
        val RETRY_DELAY_REGEX = Regex("""\"retryDelay\"\s*:\s*\"(\d+)(?:\.\d+)?s\"""")

        /** Longer waits than this mean a daily limit; don't sit on the "Translating" banner. */
        const val MAX_QUOTA_WAIT_SECONDS = 30

        /** Three tries per model on overload: waits of 2s and 6s between, then move on. */
        val RETRY_WAITS_MS = listOf(2_000L, 6_000L, 0L)
    }
}


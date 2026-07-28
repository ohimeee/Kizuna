package eu.kanade.tachiyomi.source.novel

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.NovelSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.security.MessageDigest

/**
 * A [NovelSource] backed by a small JS plugin file, in a Kizuna-native format inspired by
 * LNReader's source structure (not byte-compatible with it — see the class doc on the plugin
 * contract below).
 *
 * The plugin script must, at top level:
 * 1. Call `Register(json)` exactly once with its own metadata: `id`, `name`, `lang`, `baseUrl`,
 *    and optionally `version`/`supportsLatest`/`filters`/`iconUrl`. `filters` (optional, defaults
 *    to none) is a list of single-select genre-style filters shown in the search filter sheet:
 *    `[{ id, name, options: [{ label, value }] }]` — `id` is an internal key only `searchNovels`
 *    sees, `name`/`label` are what the user sees. Not every site has a usable one; it's fine to
 *    omit entirely. `iconUrl` (optional) is a remote image URL shown in Browse/Sources instead of
 *    the generic default-source icon — there's no APK to pull an icon from like extension sources
 *    get, so plugins that want a real icon must supply one themselves (e.g. the site's favicon).
 * 2. Assign `globalThis.source` to an object implementing:
 *    - `popularNovels(page)` -> JSON `{ novels: [{ title, url, cover? }], hasNextPage }`
 *    - `latestNovels(page)` -> same shape as `popularNovels`
 *    - `searchNovels(query, page, filtersJson)` -> same shape as `popularNovels`. `filtersJson` is
 *      a JSON object of `{ [filterId]: selectedValue }` for every declared filter the user picked
 *      a non-default option for (a filter left on its default/"Any" option is omitted entirely, so
 *      `filtersJson` is `"{}"` for a plain search with no filters touched).
 *    - `novelDetails(url)` -> JSON `{ title?, cover?, author?, description?, genres?, status? }`
 *    - `chapterList(novelUrl)` -> JSON array of `{ name, url, chapterNumber?, dateUpload? }`, in
 *      natural reading order (chapter 1 first) - this gets reversed internally to match Mihon's
 *      own newest-first sourceOrder convention, so plugins never need to worry about it
 *    - `chapterContent(chapterUrl)` -> plain string (not JSON), the chapter body
 *
 * Two globals are available to the script for scraping: `Http.get(url, headersJson)` /
 * `Http.post(url, body, headersJson)` (both return the response body as a string) /
 * `Http.getCookie(url, name)` (reads a cookie previously set on this device's cookie jar for that
 * URL's host — e.g. a CSRF token cookie set by an earlier `Http.get` response, needed by sites
 * whose JSON APIs require the token echoed back as a query param), and
 * `Html.selectText/selectOwnText/selectAttr/selectHtml(html, selector[, attr])` plus
 * `selectAllText/selectAllAttr` (which return a JSON array), all backed by Jsoup. A `fetchApi`
 * convenience wrapper around `Http` is also predefined — see [PRELUDE_SCRIPT].
 *
 * Every public call here spins up a fresh [QuickJs] instance, evaluates the prelude + plugin
 * script, and tears it down. This is simpler and safer than keeping a persistent engine around
 * (QuickJs instances are not meant to be shared across threads/coroutine dispatchers), at the
 * cost of re-parsing the plugin script on every call — acceptable for now given these scripts are
 * small.
 */
class JsNovelSource(private val scriptFile: File) : NovelSource {

    private val network: NetworkHelper by injectLazy()

    private val metadata: PluginMetadata by lazy {
        withEngine { engine, register -> register() }
    }

    override val id: Long get() = metadata.id
    override val name: String get() = metadata.name
    override val lang: String get() = metadata.lang
    override val supportsLatest: Boolean get() = metadata.supportsLatest
    override val iconUrl: String? get() = metadata.iconUrl

    val baseUrl: String get() = metadata.baseUrl
    val version: String get() = metadata.version

    override fun getHomeUrl(): String = baseUrl

    override fun getFilterList(): FilterList = FilterList(
        metadata.filters.map { def ->
            NovelSelectFilter(
                name = def.name,
                optionValues = listOf("") + def.options.map { it.value },
                labels = (listOf(ANY_OPTION_LABEL) + def.options.map { it.label }).toTypedArray(),
            )
        },
    )

    override suspend fun getPopularManga(page: Int): MangasPage = withIOContext {
        callListing("popularNovels", page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = withIOContext {
        callListing("latestNovels", page)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = withIOContext {
        withEngine { engine, _ ->
            val api = engine.get(SOURCE_GLOBAL, SourcePluginApi::class.java)
            val selections = metadata.filters.mapIndexedNotNull { index, def ->
                val filter = filters.getOrNull(index) as? NovelSelectFilter ?: return@mapIndexedNotNull null
                val value = filter.optionValues.getOrNull(filter.state)?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
                def.id to value
            }.toMap()
            parseListing(api.searchNovels(query, page, Json.encodeToString(selections)))
        }
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = withIOContext {
        withEngine { engine, _ ->
            val api = engine.get(SOURCE_GLOBAL, SourcePluginApi::class.java)

            val updatedManga = if (fetchDetails) {
                val details = Json.decodeFromString<NovelDetailsDto>(api.novelDetails(manga.url))
                manga.copy().apply {
                    details.title?.let { title = it }
                    details.cover?.let { thumbnail_url = it }
                    details.author?.let { author = it }
                    details.description?.let { description = it }
                    details.genres?.let { genre = it.joinToString(", ") }
                    details.status?.let { status = parseStatus(it) }
                    initialized = true
                }
            } else {
                manga
            }

            val updatedChapters = if (fetchChapters) {
                // Plugins return chapterList() in natural reading order (chapter 1 first - see the
                // class doc below and docs/novel-sources/README.md). Mihon's chapter-sort machinery
                // (tachiyomi.domain.chapter.service.ChapterSort, CHAPTER_SORTING_SOURCE branch)
                // assumes the opposite - the manga-extension convention of newest-first - and
                // assigns sourceOrder by raw list index accordingly (SyncChaptersWithSource). Left
                // as-is, a fresh novel entry (which defaults to source-order sorting) shows chapters
                // reversed everywhere that relies on sourceOrder, including reader prev/next
                // navigation. Reverse here, once, centrally, so every plugin can be written in the
                // natural order and this quirk doesn't need to be rediscovered per plugin.
                Json.decodeFromString<List<ChapterListItemDto>>(api.chapterList(manga.url)).asReversed().map { dto ->
                    SChapter.create().apply {
                        name = dto.name
                        url = dto.url
                        chapter_number = dto.chapterNumber
                        date_upload = dto.dateUpload
                    }
                }
            } else {
                chapters
            }

            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    override suspend fun getChapterText(chapter: SChapter): String = withIOContext {
        withEngine { engine, _ ->
            val api = engine.get(SOURCE_GLOBAL, SourcePluginApi::class.java)
            api.chapterContent(chapter.url)
        }
    }

    private suspend fun callListing(function: String, page: Int): MangasPage = withEngine { engine, _ ->
        val api = engine.get(SOURCE_GLOBAL, SourcePluginApi::class.java)
        val json = when (function) {
            "popularNovels" -> api.popularNovels(page)
            "latestNovels" -> api.latestNovels(page)
            else -> error("Unknown listing function: $function")
        }
        parseListing(json)
    }

    private fun parseListing(json: String): MangasPage {
        val result = Json.decodeFromString<NovelListResultDto>(json)
        val mangas = result.novels.map { item ->
            SManga.create().apply {
                title = item.title
                url = item.url
                item.cover?.let { thumbnail_url = it }
            }
        }
        return MangasPage(mangas, result.hasNextPage)
    }

    /**
     * Evaluates the prelude + plugin script in a fresh [QuickJs] instance, with the [Http]/[Html]
     * bridges and the metadata-capturing `Register` function wired in first. [block] receives the
     * live engine plus a `register` callback that re-runs metadata capture on demand (used by the
     * lazy [metadata] delegate so it doesn't need its own separate engine setup).
     */
    private fun <T> withEngine(block: (QuickJs, register: () -> PluginMetadata) -> T): T {
        var captured: PluginMetadata? = null

        QuickJs.create().use { engine ->
            engine.set(
                HTTP_GLOBAL,
                HttpBridge::class.java,
                object : HttpBridge {
                    override fun get(url: String, headersJson: String): String = httpGet(url, headersJson)
                    override fun post(url: String, body: String, headersJson: String): String =
                        httpPost(url, body, headersJson)
                    override fun getCookie(url: String, name: String): String =
                        network.cookieJar.get(url.toHttpUrl()).firstOrNull { it.name == name }?.value.orEmpty()
                },
            )
            engine.set(
                HTML_GLOBAL,
                HtmlBridge::class.java,
                object : HtmlBridge {
                    override fun selectText(html: String, selector: String): String =
                        Jsoup.parse(html).select(selector).firstOrNull()?.text().orEmpty()

                    override fun selectOwnText(html: String, selector: String): String =
                        Jsoup.parse(html).select(selector).firstOrNull()?.ownText().orEmpty()

                    override fun selectAttr(html: String, selector: String, attr: String): String =
                        Jsoup.parse(html).select(selector).firstOrNull()?.attr(attr).orEmpty()

                    override fun selectHtml(html: String, selector: String): String =
                        Jsoup.parse(html).select(selector).firstOrNull()?.outerHtml().orEmpty()

                    override fun selectAllText(html: String, selector: String): String =
                        Json.encodeToString(Jsoup.parse(html).select(selector).map { it.text() })

                    override fun selectAllAttr(html: String, selector: String, attr: String): String =
                        Json.encodeToString(Jsoup.parse(html).select(selector).map { it.attr(attr) })
                },
            )
            engine.set(
                REGISTER_GLOBAL,
                RegisterBridge::class.java,
                object : RegisterBridge {
                    override fun register(metadataJson: String) {
                        val dto = Json.decodeFromString<PluginMetadataDto>(metadataJson)
                        captured = PluginMetadata(
                            id = generateId(dto.id, dto.lang),
                            name = dto.name,
                            lang = dto.lang,
                            baseUrl = dto.baseUrl,
                            version = dto.version,
                            supportsLatest = dto.supportsLatest,
                            filters = dto.filters,
                            iconUrl = dto.iconUrl,
                        )
                    }
                },
            )

            engine.evaluate(PRELUDE_SCRIPT, "kizuna-prelude.js")
            engine.evaluate(scriptFile.readText(), scriptFile.name)

            return block(engine) {
                captured ?: error(
                    "Novel source script at ${scriptFile.path} never called Register(...)",
                )
            }
        }
    }

    private fun httpGet(url: String, headersJson: String): String {
        val request = Request.Builder().url(url).headers(parseHeaders(headersJson)).build()
        network.client.newCall(request).execute().use { response ->
            return response.body?.string().orEmpty()
        }
    }

    private fun httpPost(url: String, body: String, headersJson: String): String {
        val request = Request.Builder()
            .url(url)
            .headers(parseHeaders(headersJson))
            .post(body.toRequestBody(null))
            .build()
        network.client.newCall(request).execute().use { response ->
            return response.body?.string().orEmpty()
        }
    }

    private fun parseHeaders(headersJson: String): Headers {
        val map = if (headersJson.isBlank()) emptyMap() else Json.decodeFromString<Map<String, String>>(headersJson)
        return Headers.Builder().apply { map.forEach { (k, v) -> add(k, v) } }.build()
    }

    companion object {
        private const val HTTP_GLOBAL = "Http"
        private const val HTML_GLOBAL = "Html"
        private const val REGISTER_GLOBAL = "__register"
        private const val SOURCE_GLOBAL = "source"
        private const val ANY_OPTION_LABEL = "Any"

        /**
         * Small JS shim giving plugin scripts a `fetchApi`-like convenience wrapper and shorter
         * names for the Jsoup-backed selection bridge. Deliberately synchronous (no Promise), since
         * QuickJs calls are blocking on our side too — plugins should treat `fetchApi(...)` results
         * as already resolved.
         */
        private val PRELUDE_SCRIPT = """
            function Register(json) { __register.register(json); }

            function fetchApi(url, options) {
                options = options || {};
                var method = (options.method || 'GET').toUpperCase();
                var headers = JSON.stringify(options.headers || {});
                var bodyText = method === 'GET'
                    ? Http.get(url, headers)
                    : Http.post(url, options.body || '', headers);
                return {
                    text: function () { return bodyText; },
                    json: function () { return JSON.parse(bodyText); },
                };
            }

            function selectText(html, selector) { return Html.selectText(html, selector); }
            function selectOwnText(html, selector) { return Html.selectOwnText(html, selector); }
            function selectAttr(html, selector, attr) { return Html.selectAttr(html, selector, attr); }
            function selectHtml(html, selector) { return Html.selectHtml(html, selector); }
            function selectAllText(html, selector) { return JSON.parse(Html.selectAllText(html, selector)); }
            function selectAllAttr(html, selector, attr) { return JSON.parse(Html.selectAllAttr(html, selector, attr)); }
        """.trimIndent()

        /**
         * Mirrors [eu.kanade.tachiyomi.source.online.HttpSource.generateId]: a stable 63-bit id
         * derived from the plugin's declared string id + language, so re-installing the same
         * script keeps the same source id (and thus the same library entries).
         */
        private fun generateId(id: String, lang: String): Long {
            val key = "${id.lowercase()}/$lang"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
        }

        /** Maps a plugin-declared free-text status to [SManga]'s status constants. */
        private fun parseStatus(status: String): Int = when (status.trim().lowercase()) {
            "ongoing", "publishing", "releasing" -> SManga.ONGOING
            "completed", "complete", "finished" -> SManga.COMPLETED
            "licensed" -> SManga.LICENSED
            "publishing finished" -> SManga.PUBLISHING_FINISHED
            "cancelled", "canceled", "dropped" -> SManga.CANCELLED
            "hiatus", "on hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

private data class PluginMetadata(
    val id: Long,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val version: String,
    val supportsLatest: Boolean,
    val filters: List<FilterDefDto> = emptyList(),
    val iconUrl: String? = null,
)

@Serializable
private data class PluginMetadataDto(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val version: String = "1.0.0",
    val supportsLatest: Boolean = true,
    val filters: List<FilterDefDto> = emptyList(),
    val iconUrl: String? = null,
)

@Serializable
private data class FilterOptionDto(val label: String, val value: String)

@Serializable
private data class FilterDefDto(val id: String, val name: String, val options: List<FilterOptionDto>)

/**
 * The concrete [Filter.Select] every plugin-declared filter becomes. `values` (inherited, what the
 * filter-sheet UI displays) are the option labels prefixed with an "Any" no-op option at index 0;
 * [optionValues] is the parallel list of the actual values to send back to the plugin, index-
 * matched to `values` - `state` (inherited, the selected index) indexes into both.
 */
private class NovelSelectFilter(
    name: String,
    val optionValues: List<String>,
    labels: Array<String>,
) : Filter.Select<String>(name, labels)

@Serializable
private data class NovelListItemDto(val title: String, val url: String, val cover: String? = null)

@Serializable
private data class NovelListResultDto(val novels: List<NovelListItemDto>, val hasNextPage: Boolean = false)

@Serializable
private data class NovelDetailsDto(
    val title: String? = null,
    val cover: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: String? = null,
)

@Serializable
private data class ChapterListItemDto(
    val name: String,
    val url: String,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0L,
)

/** Exposed into JS as `Http`. Methods block synchronously on the calling thread. */
private interface HttpBridge {
    fun get(url: String, headersJson: String): String
    fun post(url: String, body: String, headersJson: String): String
    fun getCookie(url: String, name: String): String
}

/** Exposed into JS as `Html`. Backed by Jsoup; `html` is the raw document/fragment string to query. */
private interface HtmlBridge {
    fun selectText(html: String, selector: String): String
    fun selectOwnText(html: String, selector: String): String
    fun selectAttr(html: String, selector: String, attr: String): String
    fun selectHtml(html: String, selector: String): String
    fun selectAllText(html: String, selector: String): String
    fun selectAllAttr(html: String, selector: String, attr: String): String
}

/** JS -> Kotlin: the plugin calls this once at top level, via the `Register(json)` prelude helper. */
private interface RegisterBridge {
    fun register(metadataJson: String)
}

/** Kotlin -> JS: the operational entry points the plugin script must define on `globalThis.source`. */
private interface SourcePluginApi {
    fun popularNovels(page: Int): String
    fun latestNovels(page: Int): String
    fun searchNovels(query: String, page: Int, filtersJson: String): String
    fun novelDetails(url: String): String
    fun chapterList(novelUrl: String): String
    fun chapterContent(chapterUrl: String): String
}

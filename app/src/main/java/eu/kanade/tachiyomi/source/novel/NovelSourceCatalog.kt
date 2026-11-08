package eu.kanade.tachiyomi.source.novel

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fetches the list of novel sources available to install (see [NovelSourceCatalogEntry]) from a
 * JSON index. Defaults to the one published in the Kizuna repo itself, since there's no separate
 * source-repo infrastructure for `.js` novel sources (unlike the APK-based extension catalog).
 */
object NovelSourceCatalog {
    const val DEFAULT_INDEX_URL =
        "https://raw.githubusercontent.com/ohimeee/Kizuna/main/novel-sources/index.json"

    suspend fun fetch(
        indexUrl: String = DEFAULT_INDEX_URL,
        network: NetworkHelper = Injekt.get(),
        json: Json = Injekt.get(),
    ): List<NovelSourceCatalogEntry> {
        // Skip the HTTP cache so Refresh really refetches: GET() caches for 10 minutes by default,
        // on top of raw.githubusercontent's own 5-minute CDN cache.
        val response = network.client.newCall(GET(indexUrl, cache = CacheControl.FORCE_NETWORK)).awaitSuccess()
        return with(json) { response.parseAs<List<NovelSourceCatalogEntry>>() }
    }
}

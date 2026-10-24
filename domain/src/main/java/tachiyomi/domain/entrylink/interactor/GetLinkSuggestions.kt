package tachiyomi.domain.entrylink.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entrylink.repository.EntryLinkRepository
import tachiyomi.domain.entrylink.service.findTitleMatches
import tachiyomi.domain.entrylink.service.rankingScore
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * Library entries whose titles look like [Manga]'s own, for suggesting a link. Library only: no
 * source is searched, and an entry that is already linked has nothing to suggest.
 */
@Inject
class GetLinkSuggestions(
    private val getLibraryManga: GetLibraryManga,
    private val repository: EntryLinkRepository,
    private val sourceManager: SourceManager,
) {

    /** Every plausible match, best first, at most [limit]. Entries of the other content type rank first. */
    suspend fun await(manga: Manga, limit: Int = MAX_SUGGESTIONS): List<Manga> {
        return matches(manga).take(limit).map { it.manga }
    }

    /** The one entry worth prompting the user about, or null when nothing matches strongly. */
    suspend fun awaitBestMatch(manga: Manga): Manga? {
        return matches(manga).firstOrNull { it.isStrong }?.manga
    }

    private class Suggestion(val manga: Manga, val isStrong: Boolean)

    private suspend fun matches(manga: Manga): List<Suggestion> {
        return try {
            if (repository.getLinkedEntryId(manga.id) != null) return emptyList()

            val candidates = getLibraryManga.await()
                .map { it.manga }
                .filter { it.id != manga.id && it.favorite }
            // An entry already linked elsewhere would end up with two links, and only one is ever shown
            val matches = findTitleMatches(manga.title, candidates) { it.title }
                .filter { repository.getLinkedEntryId(it.item.id) == null }
            if (matches.isEmpty()) return emptyList()

            // Only matches need their type looked up, rather than the whole library
            val ownType = sourceManager.get(manga.source)?.contentType
            matches
                .map { match ->
                    val type = sourceManager.get(match.item.source)?.contentType
                    val opposite = ownType != null && type != null && type != ownType
                    match to rankingScore(match.score, opposite)
                }
                .sortedByDescending { (_, rank) -> rank }
                .map { (match, _) -> Suggestion(match.item, match.isStrong) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    companion object {
        const val MAX_SUGGESTIONS = 5
    }
}

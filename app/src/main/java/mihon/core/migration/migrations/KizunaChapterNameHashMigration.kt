package mihon.core.migration.migrations

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Kizuna's versionCodes don't line up with Mihon's, so [ChapterNameSuffixMigration] (keyed to
 * Mihon's 13..33 range) never fires for a Kizuna install. Before Mihon's `enableChapterNameHash`
 * preference was merged, Kizuna always appended `_<md5(url)[:6]>` to chapter folder names, so every
 * existing install keeps it on. Without it, two chapters sharing a name (common with novel sources)
 * would download into the same folder and the second would read as already downloaded.
 *
 * Version 38 is the versionCode of Kizuna 0.31.0, the first release carrying the preference. A fresh
 * install (previousVersion 0) never gets here: only [Migration.isAlways] migrations run for it.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class KizunaChapterNameHashMigration(
    private val preference: LibraryPreferences,
) : Migration {
    override val version: Float = 38f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        if (migrationContext.previousVersion in 1 until 38) {
            preference.enableChapterNameHash.set(true)
        }
        return true
    }
}

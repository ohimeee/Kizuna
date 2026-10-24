package mihon.core.migration.migrations

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationCompletedListener
import mihon.core.migration.MigrationContext
import mihon.core.migration.MigrationJobFactory
import mihon.core.migration.MigrationStrategyFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.service.LibraryPreferences

class KizunaChapterNameHashMigrationTest {

    private lateinit var hashPreference: Preference<Boolean>
    private lateinit var migration: KizunaChapterNameHashMigration

    @BeforeEach
    fun initialize() {
        hashPreference = mockk(relaxed = true)
        val libraryPreferences = mockk<LibraryPreferences> {
            every { enableChapterNameHash } returns hashPreference
        }
        migration = KizunaChapterNameHashMigration(libraryPreferences)
    }

    /** Runs the migration through the real strategy selection, the way `App` does on startup. */
    private fun upgrade(from: Int, to: Int) = runBlocking {
        val context = MigrationContext(false, from)
        val factory = MigrationJobFactory(context, CoroutineScope(Dispatchers.Default + Job()))
        val strategies = MigrationStrategyFactory(factory, mockk<MigrationCompletedListener>(relaxed = true))
        strategies.create(from, to)(listOf(migration)).await()
    }

    @Test
    fun `existing install upgrading to 38 turns the hash on`() {
        upgrade(from = 37, to = 38)

        verify(exactly = 1) { hashPreference.set(true) }
    }

    @Test
    fun `existing install from an older release turns the hash on`() {
        upgrade(from = 12, to = 40)

        verify(exactly = 1) { hashPreference.set(true) }
    }

    @Test
    fun `fresh install leaves the hash off`() {
        upgrade(from = 0, to = 38)

        verify(exactly = 0) { hashPreference.set(any()) }
    }

    @Test
    fun `install already on 38 or later is left alone`() {
        upgrade(from = 38, to = 39)

        verify(exactly = 0) { hashPreference.set(any()) }
    }
}

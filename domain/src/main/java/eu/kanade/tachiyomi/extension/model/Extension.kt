package eu.kanade.tachiyomi.extension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceContentType
import mihon.domain.extension.model.ExtensionStore
import tachiyomi.domain.source.model.StubSource

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val pkgFactory: String?,
        val sources: List<Source>,
        val icon: Drawable?,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isShared: Boolean,
        val store: ExtensionStore? = null,
    ) : Extension() {
        val contentType: SourceContentType
            get() = sources.firstOrNull()?.contentType ?: SourceContentType.IMAGE
    }

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String,
        override val isNsfw: Boolean,
        val sources: List<Source>,
        val apkUrl: String,
        val iconUrl: String,
        val store: ExtensionStore,
    ) : Extension() {
        val contentType: SourceContentType
            get() = sources.firstOrNull()?.contentType ?: SourceContentType.IMAGE

        data class Source(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
            val contentType: SourceContentType = SourceContentType.IMAGE,
        ) {
            fun toStubSource(): StubSource {
                return StubSource(
                    id = this.id,
                    lang = this.lang,
                    name = this.name,
                )
            }
        }
    }

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
    ) : Extension()
}

/**
 * The content type badge to show for this extension in Browse, or `null` if unknown
 * (e.g. an [Extension.Untrusted] APK whose sources haven't been loaded yet).
 */
val Extension.contentTypeOrNull: SourceContentType?
    get() = when (this) {
        is Extension.Installed -> contentType
        is Extension.Available -> contentType
        is Extension.Untrusted -> null
    }

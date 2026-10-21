package com.radustavila.nasaexplorer.core.offline

import android.content.Context
import android.net.Uri
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layout of the offline favourite images: `<root>/<source>/<item id>.<extension>`, with [root] at
 * `filesDir/favorites` in the app.
 *
 * `filesDir` rather than `cacheDir` on purpose: the system may clear the cache directory whenever it
 * runs low on space, and no `CacheEvictor` looks here. A copy goes away only with its favourite.
 *
 * Paths stored in the database are relative to [root], so they survive the app's data directory
 * moving (adoptable storage, restore onto another device).
 */
@Singleton
class FavoriteImageFiles(val root: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir.resolve(DIRECTORY))

    /** Where the copy of `(source, itemId)` with [extension] lives, relative to [root]. */
    fun relativePathFor(source: FavoriteSource, itemId: String, extension: String): String =
        "${source.key}/${fileNameOf(itemId)}.$extension"

    fun resolve(relativePath: String): File = root.resolve(relativePath)

    /** The file a download is written to before it is complete and moved into place. */
    fun partialFileFor(source: FavoriteSource, itemId: String): File =
        root.resolve("${source.key}/${fileNameOf(itemId)}.$PARTIAL_EXTENSION")

    /** A `file://` URI the image loader opens directly. */
    fun uriOf(relativePath: String): String = Uri.fromFile(resolve(relativePath)).toString()

    /** Deletes every copy of `(source, itemId)`, whatever its extension, and any partial download. */
    fun deleteAll(source: FavoriteSource, itemId: String) {
        val name = fileNameOf(itemId)
        root.resolve(source.key)
            .listFiles { file -> file.name.substringBeforeLast('.') == name }
            ?.forEach(File::delete)
    }

    /** Bytes taken by everything under [root]. */
    fun totalBytes(): Long = root.walkBottomUp().filter(File::isFile).sumOf(File::length)

    /** Present once existing favourites have been queued for download (see `backfillOnce`). */
    val backfillMarker: File get() = root.resolve(BACKFILL_MARKER)

    companion object {
        const val DIRECTORY = "favorites"
        private const val PARTIAL_EXTENSION = "part"
        private const val BACKFILL_MARKER = ".backfilled"
        private const val MAX_FILE_NAME_LENGTH = 120

        /**
         * A file name for an item id that is safe and unambiguous: ids carry slashes (EPIC), spaces
         * and dots (exoplanet names). Everything outside `[A-Za-z0-9_-]` is percent-encoded - the
         * dot included, so the extension is always what follows the last dot - and an id too long
         * for a file name is replaced by its hash.
         */
        internal fun fileNameOf(itemId: String): String {
            val encoded = buildString {
                itemId.toByteArray(Charsets.UTF_8).forEach { byte ->
                    val char = byte.toInt().toChar()
                    if (byte >= 0 && (char.isLetterOrDigit() || char == '_' || char == '-')) {
                        append(char)
                    } else {
                        append('%').append("%02X".format(byte.toInt() and BYTE_MASK))
                    }
                }
            }
            return when {
                encoded.isEmpty() -> "%"
                encoded.length > MAX_FILE_NAME_LENGTH -> sha256(itemId)
                else -> encoded
            }
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it.toInt() and BYTE_MASK) }

        private const val BYTE_MASK = 0xFF
    }
}

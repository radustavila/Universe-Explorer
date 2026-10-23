package com.radustavila.nasaexplorer.data.news.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloaded podcast episodes, in `filesDir/podcasts`: `filesDir` rather than `cacheDir` so the
 * system does not delete an episode the user saved for a flight. "Clear cache" removes them
 * (except favourites') through `PodcastDownloadStorage`.
 *
 * Paths stored in the database are relative to [root]; file names are a hash of the episode id,
 * which may be a URL.
 */
@Singleton
class PodcastFiles(val root: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir.resolve(DIRECTORY))

    fun relativePathFor(episodeId: String): String = "${fileNameOf(episodeId)}.$EXTENSION"

    fun resolve(relativePath: String): File = root.resolve(relativePath)

    /** The file a download is written to before it is complete and moved into place. */
    fun partialFileFor(episodeId: String): File = root.resolve("${fileNameOf(episodeId)}.$PARTIAL_EXTENSION")

    fun delete(episodeId: String) {
        resolve(relativePathFor(episodeId)).delete()
        partialFileFor(episodeId).delete()
    }

    fun sizeInBytes(): Long = root.listFiles()?.sumOf(File::length) ?: 0L

    private fun fileNameOf(episodeId: String): String =
        MessageDigest.getInstance("SHA-256").digest(episodeId.toByteArray())
            .take(HASH_BYTES)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIRECTORY = "podcasts"
        const val EXTENSION = "audio"
        const val PARTIAL_EXTENSION = "partial"
        const val HASH_BYTES = 16
    }
}

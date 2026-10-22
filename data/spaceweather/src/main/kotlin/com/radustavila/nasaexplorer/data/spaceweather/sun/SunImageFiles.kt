package com.radustavila.nasaexplorer.data.spaceweather.sun

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.model.SunChannel
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One downloaded SDO picture. */
data class StoredSunImage(val channel: SunChannel, val file: File, val updatedAt: Instant)

/**
 * The newest SDO picture of each channel, one file per channel named `{code}_{epochSecond}.jpg`
 * after SDO's `Last-Modified`. The name is the whole record, so there is no database; and a new
 * picture gets a new name, so no image cache can show an older one under it.
 *
 * Not Coil's disk cache: SDO reuses one URL per channel, which that cache cannot tell versions of
 * apart, and its LRU could push the last picture out while the device is offline.
 */
@Singleton
class SunImageFiles @Inject constructor(
    @Named(SUN_IMAGE_DIRECTORY) private val directory: File,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<SunChannel, StoredSunImage>?>(null)

    /** Every channel's picture; read from disk on first collection. */
    val images: Flow<Map<SunChannel, StoredSunImage>> = flow {
        if (state.value == null) mutex.withLock { if (state.value == null) state.value = scan() }
        emitAll(state.filterNotNull())
    }

    suspend fun current(channel: SunChannel): StoredSunImage? = mutex.withLock {
        loaded()[channel]
    }

    /** Writes [bytes] as [channel]'s picture published at [updatedAt], replacing the previous one. */
    suspend fun save(channel: SunChannel, updatedAt: Instant, bytes: InputStream) {
        mutex.withLock {
            val images = loaded()
            val stored = withContext(ioDispatcher) {
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create $directory")
                val partial = File(directory, "${channel.sdoCode}$PARTIAL_SUFFIX")
                partial.outputStream().use { bytes.copyTo(it) }
                val target = File(directory, "${channel.sdoCode}_${updatedAt.epochSecond}$EXTENSION")
                if (!partial.renameTo(target)) {
                    partial.delete()
                    throw IOException("Cannot move $partial to $target")
                }
                images[channel]?.file?.takeIf { it != target }?.delete()
                StoredSunImage(channel, target, updatedAt)
            }
            state.value = images + (channel to stored)
        }
    }

    /**
     * Deletes pictures published more than [retention] before [now], except the newest one, so a long
     * offline spell still shows the Sun as it was last seen. Also removes interrupted downloads.
     *
     * @return how many files were deleted.
     */
    suspend fun evict(now: Instant, retention: Duration): Int = mutex.withLock {
        val images = loaded()
        val newest = images.values.maxByOrNull { it.updatedAt }
        val expired = images.values.filter { it != newest && it.updatedAt.isBefore(now.minus(retention)) }
        withContext(ioDispatcher) {
            val partials = directory.listFiles { file -> file.name.endsWith(PARTIAL_SUFFIX) }.orEmpty()
            (expired.map { it.file } + partials).count { it.delete() }
        }.also { state.value = images - expired.map { it.channel }.toSet() }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(ioDispatcher) { directory.listFiles()?.forEach { it.delete() } }
            state.value = emptyMap()
        }
    }

    suspend fun sizeInBytes(): Long = withContext(ioDispatcher) { directory.listFiles().orEmpty().sumOf(File::length) }

    private suspend fun loaded(): Map<SunChannel, StoredSunImage> = state.value ?: scan().also { state.value = it }

    /** Reads the directory; when a channel somehow has several files, the newest wins. */
    private suspend fun scan(): Map<SunChannel, StoredSunImage> = withContext(ioDispatcher) {
        directory.listFiles().orEmpty()
            .mapNotNull(::parse)
            .groupBy { it.channel }
            .mapValues { (_, images) -> images.maxBy { it.updatedAt } }
    }

    private fun parse(file: File): StoredSunImage? {
        val name = file.name.takeIf { it.endsWith(EXTENSION) }?.removeSuffix(EXTENSION)
        val channel = SunChannel.entries.firstOrNull { it.sdoCode == name?.substringBefore('_') }
        val seconds = name?.substringAfter('_')?.toLongOrNull()
        if (channel == null || seconds == null) return null
        return StoredSunImage(channel, file, Instant.ofEpochSecond(seconds))
    }

    companion object {
        /** Names the directory the pictures live in (a folder of the app's cache directory). */
        const val SUN_IMAGE_DIRECTORY = "sun_image_directory"
        private const val EXTENSION = ".jpg"
        private const val PARTIAL_SUFFIX = ".part"
    }
}

package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.domain.sky.DeepSkyCatalogRepository
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The deep-sky catalogue bundled as `deepsky.bin` (F23, written by `tools/build_sky_catalog.py`
 * from OpenNGC, CC BY-SA 4.0): about 1,800 objects in 80 KB, read once per process. Callers keep it
 * off the main thread.
 */
@Singleton
class ResourceDeepSkyCatalog @Inject constructor() : DeepSkyCatalogRepository {

    private val mutex = Mutex()

    @Volatile
    private var cached: List<DeepSkyObject>? = null

    override suspend fun objects(): List<DeepSkyObject> =
        cached ?: mutex.withLock { cached ?: read().also { cached = it } }

    internal fun read(): List<DeepSkyObject> = open().use { input ->
        input.expectHeader()
        List(input.readInt()) { input.readObject() }
    }

    private fun open(): DataInputStream {
        val stream = ResourceDeepSkyCatalog::class.java.getResourceAsStream(FILE)
            ?: throw IOException("Missing bundled deep-sky catalogue $FILE")
        return DataInputStream(BufferedInputStream(stream))
    }

    private fun DataInputStream.expectHeader() {
        if (readInt() != MAGIC) throw IOException("Not a deep-sky catalogue file")
        val version = readInt()
        if (version != FORMAT_VERSION) throw IOException("Unsupported deep-sky catalogue version $version")
    }

    private fun DataInputStream.readObject(): DeepSkyObject {
        val id = readUTF()
        val designation = readUTF()
        val commonName = readUTF()
        val constellation = readUTF()
        val type = DeepSkyType.entries[readByte().toInt()]
        val position = EquatorialCoordinate(readFloat().toDouble(), readFloat().toDouble())
        val magnitude = readShort()
        val major = readShort()
        val minor = readShort()
        val angle = readShort()
        val messier = readByte().toInt()
        val caldwell = readByte().toInt()
        return DeepSkyObject(
            id = id,
            designation = designation,
            commonName = commonName.ifEmpty { null },
            type = type,
            position = position,
            constellation = constellation,
            magnitude = magnitude.takeIf { it != NO_MAGNITUDE }?.let { it / MAGNITUDE_SCALE },
            majorAxisArcmin = major.takeIf { it > 0 }?.let { it / AXIS_SCALE },
            minorAxisArcmin = minor.takeIf { it > 0 }?.let { it / AXIS_SCALE },
            positionAngleDegrees = angle.toInt().takeIf { it >= 0 },
            messier = messier.takeIf { it > 0 },
            caldwell = caldwell.takeIf { it > 0 },
        )
    }

    private companion object {
        // Absolute: R8 moves this class to another package, which breaks paths relative to it.
        const val FILE = "/com/radustavila/nasaexplorer/core/skymap/deepsky.bin"
        const val MAGIC = 0x534B5944
        const val FORMAT_VERSION = 1
        const val NO_MAGNITUDE: Short = Short.MIN_VALUE
        const val MAGNITUDE_SCALE = 100f
        const val AXIS_SCALE = 10f
    }
}

package com.radustavila.nasaexplorer.core.skymap

import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.model.ConstellationFigure
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.Star
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The catalogue bundled as this module's Java resources (`stars.bin`, `constellations.bin`, both
 * written by `tools/build_sky_catalog.py`), read once per process: about 130 KB, a few tens of
 * milliseconds. Callers keep it off the main thread.
 */
@Singleton
class ResourceSkyCatalog @Inject constructor() : SkyCatalogRepository {

    private val mutex = Mutex()

    @Volatile
    private var cached: SkyCatalog? = null

    override suspend fun catalog(): SkyCatalog =
        cached ?: mutex.withLock { cached ?: read().also { cached = it } }

    internal fun read(): SkyCatalog = SkyCatalog(
        stars = open(STARS_FILE).use(::readStars),
        constellations = open(CONSTELLATIONS_FILE).use(::readConstellations),
    )

    private fun open(name: String): DataInputStream {
        val stream = ResourceSkyCatalog::class.java.getResourceAsStream(name)
            ?: throw IOException("Missing bundled sky catalogue file $name")
        return DataInputStream(BufferedInputStream(stream))
    }

    private fun readStars(input: DataInputStream): List<Star> {
        input.expectHeader(STARS_MAGIC)
        val count = input.readInt()
        val positions = FloatArray(count * 2)
        val magnitudes = FloatArray(count)
        val colourIndices = ShortArray(count)
        val nameIndices = ShortArray(count)
        for (i in 0 until count) {
            positions[i * 2] = input.readFloat()
            positions[i * 2 + 1] = input.readFloat()
            magnitudes[i] = input.readShort() / MAGNITUDE_SCALE
            colourIndices[i] = input.readShort()
            nameIndices[i] = input.readShort()
        }
        val names = Array(input.readInt()) { input.readUTF() }
        return List(count) { i ->
            Star(
                position = EquatorialCoordinate(positions[i * 2].toDouble(), positions[i * 2 + 1].toDouble()),
                magnitude = magnitudes[i],
                colourIndex = colourIndices[i].takeIf { it != NO_COLOUR_INDEX }?.let { it / COLOUR_INDEX_SCALE },
                name = nameIndices[i].toInt().takeIf { it >= 0 }?.let(names::get),
            )
        }
    }

    private fun readConstellations(input: DataInputStream): List<ConstellationFigure> {
        input.expectHeader(CONSTELLATIONS_MAGIC)
        return List(input.readInt()) {
            val id = input.readUTF()
            val name = input.readUTF()
            val label = input.readCoordinate()
            val rank = input.readByte().toInt()
            val lines = List(input.readShort().toInt()) {
                List(input.readShort().toInt()) { input.readCoordinate() }
            }
            ConstellationFigure(id = id, name = name, labelPosition = label, rank = rank, lines = lines)
        }
    }

    private fun DataInputStream.expectHeader(magic: Int) {
        if (readInt() != magic) throw IOException("Not a sky catalogue file")
        val version = readInt()
        if (version != FORMAT_VERSION) throw IOException("Unsupported sky catalogue version $version")
    }

    private fun DataInputStream.readCoordinate() =
        EquatorialCoordinate(readFloat().toDouble(), readFloat().toDouble())

    private companion object {
        const val STARS_FILE = "stars.bin"
        const val CONSTELLATIONS_FILE = "constellations.bin"
        const val STARS_MAGIC = 0x534B5953
        const val CONSTELLATIONS_MAGIC = 0x534B5943
        const val FORMAT_VERSION = 1
        const val NO_COLOUR_INDEX: Short = Short.MIN_VALUE
        const val MAGNITUDE_SCALE = 100f
        const val COLOUR_INDEX_SCALE = 1000f
    }
}

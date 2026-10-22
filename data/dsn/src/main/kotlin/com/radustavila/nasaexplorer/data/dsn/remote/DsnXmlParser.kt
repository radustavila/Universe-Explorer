package com.radustavila.nasaexplorer.data.dsn.remote

import android.util.Xml
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigDishDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigSiteDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigSpacecraftDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnDishDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnLiveDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnSignalDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnStationDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnTargetDto
import java.io.InputStream
import kotlinx.serialization.SerializationException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Reads the DSN Now XML feeds with a pull parser. The feeds are public but undocumented, so the
 * parser is lenient: unknown elements are skipped, missing or empty attributes become null, and an
 * element without its identifying name is dropped. Only XML that cannot be read at all fails, as a
 * [SerializationException] (an `AppError.Serialization` above the data layer).
 */
internal object DsnXmlParser {

    fun parseLive(input: InputStream): DsnLiveDto = parse(input) { parser ->
        val stations = mutableListOf<StationBuilder>()
        var dish: DishBuilder? = null
        var timestamp: Long? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "station" -> parser.attr("name")?.let { name ->
                    val time = parser.attr("timeUTC")?.toLongOrNull()
                    stations += StationBuilder(name, parser.attr("friendlyName"), time)
                }
                // Dishes follow their station as siblings; one before any station has no site.
                "dish" -> dish = parser.attr("name")?.let { name ->
                    stations.lastOrNull()?.let { station ->
                        DishBuilder(name, parser).also(station.dishes::add)
                    }
                }
                "upSignal" -> dish?.upSignals?.add(parser.signal())
                "downSignal" -> dish?.downSignals?.add(parser.signal())
                "target" -> parser.target()?.let { dish?.targets?.add(it) }
                "timestamp" -> timestamp = parser.nextText().trim().toLongOrNull()
            }
        }
        DsnLiveDto(timestampMillis = timestamp, stations = stations.map(StationBuilder::build))
    }

    fun parseConfig(input: InputStream): DsnConfigDto = parse(input) { parser ->
        val sites = mutableListOf<ConfigSiteBuilder>()
        val spacecraft = mutableListOf<DsnConfigSpacecraftDto>()
        var site: ConfigSiteBuilder? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "site" -> site = parser.attr("name")?.let { ConfigSiteBuilder(it, parser.attr("friendlyName")) }
                        ?.also(sites::add)
                    "dish" -> parser.attr("name")?.let { site?.dishes?.add(DsnConfigDishDto(it, parser.attr("type"))) }
                    "spacecraft" -> parser.attr("name")?.let {
                        spacecraft += DsnConfigSpacecraftDto(it, parser.attr("friendlyName"))
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "site") site = null
            }
        }
        DsnConfigDto(
            sites = sites.map { DsnConfigSiteDto(it.name, it.friendlyName, it.dishes) },
            spacecraft = spacecraft,
        )
    }

    private inline fun <T> parse(input: InputStream, read: (XmlPullParser) -> T): T = try {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        read(parser)
    } catch (e: XmlPullParserException) {
        throw SerializationException("Unreadable DSN feed: ${e.message}", e)
    }

    /** Null for a missing or blank attribute. */
    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)?.trim()?.ifEmpty { null }

    private fun XmlPullParser.number(name: String): Double? = attr(name)?.toDoubleOrNull()?.takeIf(Double::isFinite)

    private fun XmlPullParser.signal() = DsnSignalDto(
        spacecraft = attr("spacecraft"),
        spacecraftId = attr("spacecraftID")?.toIntOrNull(),
        active = attr("active").toBoolean(),
        band = attr("band"),
        dataRate = number("dataRate"),
    )

    private fun XmlPullParser.target(): DsnTargetDto? = attr("name")?.let { name ->
        DsnTargetDto(
            name = name,
            id = attr("id")?.toIntOrNull(),
            uplegRange = number("uplegRange"),
            downlegRange = number("downlegRange"),
        )
    }

    private class StationBuilder(val name: String, val friendlyName: String?, val timeUtc: Long?) {
        val dishes = mutableListOf<DishBuilder>()
        fun build() = DsnStationDto(name, friendlyName, timeUtc, dishes.map(DishBuilder::build))
    }

    private class DishBuilder(val name: String, parser: XmlPullParser) {
        val azimuth = parser.number("azimuthAngle")
        val elevation = parser.number("elevationAngle")
        val activity = parser.attr("activity")
        val upSignals = mutableListOf<DsnSignalDto>()
        val downSignals = mutableListOf<DsnSignalDto>()
        val targets = mutableListOf<DsnTargetDto>()

        fun build() = DsnDishDto(name, azimuth, elevation, activity, upSignals, downSignals, targets)
    }

    private class ConfigSiteBuilder(val name: String, val friendlyName: String?) {
        val dishes = mutableListOf<DsnConfigDishDto>()
    }
}

package com.radustavila.nasaexplorer.data.smallbodies.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The table shape of JPL's CAD and fireball APIs: column names in [fields], then [data] as an array
 * of rows, each an array of values (strings or null) in the same order. Columns are looked up by
 * name, never by position: the APIs add columns depending on the query (`diameter=true`,
 * `fullname=true`), which shifts every position after them. An empty answer has neither field.
 */
@Serializable
data class JplTableDto(
    val fields: List<String> = emptyList(),
    val data: List<List<JsonElement>> = emptyList(),
) {
    fun rows(): List<JplRow> {
        val columns = fields.withIndex().associate { (index, name) -> name to index }
        return data.map { values -> JplRow(columns, values) }
    }
}

/** One row of a [JplTableDto], read by column name. */
class JplRow internal constructor(
    private val columns: Map<String, Int>,
    private val values: List<JsonElement>,
) {
    /** The value of [field] as text; null when the column is absent, the cell is null or blank. */
    operator fun get(field: String): String? {
        val element = columns[field]?.let(values::getOrNull) ?: return null
        if (element is JsonNull || element !is JsonPrimitive) return null
        return element.content.trim().takeIf { it.isNotEmpty() }
    }

    fun double(field: String): Double? = get(field)?.toDoubleOrNull()
}

/** JPL's Sentry list (`sentry.api` without parameters). Numbers come as strings. */
@Serializable
data class SentryListDto(
    val data: List<SentryObjectDto> = emptyList(),
)

@Serializable
data class SentryObjectDto(
    val des: String? = null,
    val ip: String? = null,
    @SerialName("ps_max") val palermoMax: String? = null,
    @SerialName("ts_max") val torinoMax: String? = null,
    @SerialName("n_imp") val potentialImpacts: Int? = null,
    val range: String? = null,
)

/**
 * The Small-Body Database's answer for one object (`sbdb.api?sstr=...&phys-par=true`). An unknown
 * object answers only `message`; an ambiguous name answers a `list` of candidates.
 */
@Serializable
data class SbdbDto(
    @SerialName("object") val body: SbdbObjectDto? = null,
    val orbit: SbdbOrbitDto? = null,
    @SerialName("phys_par") val physicalParameters: List<SbdbValueDto> = emptyList(),
    val message: String? = null,
)

@Serializable
data class SbdbObjectDto(
    val des: String? = null,
    val fullname: String? = null,
    val neo: Boolean = false,
    val pha: Boolean = false,
    @SerialName("orbit_class") val orbitClass: SbdbOrbitClassDto? = null,
)

@Serializable
data class SbdbOrbitClassDto(val code: String? = null, val name: String? = null)

@Serializable
data class SbdbOrbitDto(
    val moid: String? = null,
    @SerialName("first_obs") val firstObserved: String? = null,
    @SerialName("last_obs") val lastObserved: String? = null,
    @SerialName("n_obs_used") val observationsUsed: Int? = null,
    val elements: List<SbdbValueDto> = emptyList(),
)

/** A named value (orbital element or physical parameter); values come as strings. */
@Serializable
data class SbdbValueDto(
    val name: String? = null,
    val value: String? = null,
    val units: String? = null,
)

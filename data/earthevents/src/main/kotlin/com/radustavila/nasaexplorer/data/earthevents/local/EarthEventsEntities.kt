package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One open event. Its sources and reports are only ever read with the event and never queried on
 * their own, so they are stored as JSON columns rather than child tables.
 */
@Entity(
    tableName = "earth_event",
    indices = [Index("last_update")],
)
data class EarthEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    /** `EarthEventCategory.key`. */
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "closed_at")
    val closedAtMillis: Long?,
    /** Date of the newest report, for ordering and eviction. */
    @ColumnInfo(name = "last_update")
    val lastUpdateMillis: Long,
    /** JSON list of [SourceRecord]. */
    @ColumnInfo(name = "sources")
    val sourcesJson: String,
    /** JSON list of [GeometryRecord], oldest first. */
    @ColumnInfo(name = "geometry")
    val geometryJson: String,
    @ColumnInfo(name = "cached_at")
    val cachedAtMillis: Long,
)

@Serializable
internal data class SourceRecord(val id: String, val url: String)

/**
 * One report as stored. Coordinates are `[latitude, longitude]` pairs: [point] for a point,
 * [rings] (outline first) for an area.
 */
@Serializable
internal data class GeometryRecord(
    val date: Long,
    val point: List<Double>? = null,
    val rings: List<List<List<Double>>>? = null,
    val magnitude: Double? = null,
    val unit: String? = null,
)

/** The imagery layers EONET suggests for a category, in EONET's order, already Web Mercator. */
@Entity(
    tableName = "imagery_layer",
    primaryKeys = ["category", "layer_id"],
)
data class ImageryLayerEntity(
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "layer_id")
    val layerId: String,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "tile_matrix_set")
    val tileMatrixSet: String,
    @ColumnInfo(name = "file_extension")
    val fileExtension: String,
    @ColumnInfo(name = "cached_at")
    val cachedAtMillis: Long,
)

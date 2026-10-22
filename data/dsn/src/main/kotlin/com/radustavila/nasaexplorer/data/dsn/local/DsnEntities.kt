package com.radustavila.nasaexplorer.data.dsn.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One antenna of the last good live snapshot, with its site. Every live refresh replaces the whole
 * snapshot ([DsnDao.replaceLive]), so all rows share [observedAtMillis] and [fetchedAtMillis].
 */
@Entity(tableName = "dsn_dish")
data class DsnDishEntity(
    @PrimaryKey @ColumnInfo(name = "dish_name") val dishName: String,
    @ColumnInfo(name = "site_code") val siteCode: String,
    @ColumnInfo(name = "site_name") val siteName: String?,
    /** Position of the site, then of the dish, in the feed: the order the screen keeps. */
    @ColumnInfo(name = "site_order") val siteOrder: Int,
    @ColumnInfo(name = "dish_order") val dishOrder: Int,
    @ColumnInfo(name = "azimuth_deg") val azimuthDegrees: Double?,
    @ColumnInfo(name = "elevation_deg") val elevationDegrees: Double?,
    val activity: String?,
    @ColumnInfo(name = "observed_at") val observedAtMillis: Long,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** A dish's assignment to one spacecraft, with the feed's range when it gave one. */
@Entity(tableName = "dsn_link", primaryKeys = ["dish_name", "spacecraft_code"])
data class DsnLinkEntity(
    @ColumnInfo(name = "dish_name") val dishName: String,
    /** Upper case, e.g. `VGR2`. */
    @ColumnInfo(name = "spacecraft_code") val spacecraftCode: String,
    @ColumnInfo(name = "link_order") val linkOrder: Int,
    @ColumnInfo(name = "naif_id") val naifId: Int?,
    /** km; null when the feed said -1. */
    @ColumnInfo(name = "range_km") val rangeKm: Double?,
)

@Entity(tableName = "dsn_signal")
data class DsnSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "dish_name") val dishName: String,
    @ColumnInfo(name = "spacecraft_code") val spacecraftCode: String,
    @ColumnInfo(name = "is_uplink") val isUplink: Boolean,
    val band: String?,
    @ColumnInfo(name = "data_rate_bps") val dataRateBitsPerSecond: Double?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
)

/** A dish with the links and signals stored for it, read in one transaction. */
data class DsnDishWithLinks(
    @Embedded val dish: DsnDishEntity,
    @Relation(parentColumn = "dish_name", entityColumn = "dish_name")
    val links: List<DsnLinkEntity>,
    @Relation(parentColumn = "dish_name", entityColumn = "dish_name")
    val signals: List<DsnSignalEntity>,
)

/** A spacecraft code's name from `config.xml`. */
@Entity(tableName = "dsn_spacecraft")
data class DsnSpacecraftEntity(
    /** Upper case, as the live feed spells it. */
    @PrimaryKey val code: String,
    val name: String,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** A dish's size from `config.xml` (`70M`, `34M`, `34MHEF`, …). */
@Entity(tableName = "dsn_dish_info")
data class DsnDishInfoEntity(
    @PrimaryKey @ColumnInfo(name = "dish_name") val dishName: String,
    @ColumnInfo(name = "site_code") val siteCode: String,
    @ColumnInfo(name = "diameter_m") val diameterMeters: Int?,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** Horizons' range of one spacecraft at [epochMillis]. */
@Entity(tableName = "probe_distance")
data class ProbeDistanceEntity(
    @PrimaryKey @ColumnInfo(name = "horizons_id") val horizonsId: Int,
    val name: String,
    @ColumnInfo(name = "distance_au") val distanceAu: Double,
    @ColumnInfo(name = "range_rate_km_s") val rangeRateKmPerSecond: Double?,
    @ColumnInfo(name = "epoch") val epochMillis: Long,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

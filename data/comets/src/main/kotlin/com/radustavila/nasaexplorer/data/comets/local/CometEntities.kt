package com.radustavila.nasaexplorer.data.comets.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/** A candidate from the last SBDB query. */
@Entity(tableName = "comet")
data class CometEntity(
    @PrimaryKey val designation: String,
    val name: String,
    @ColumnInfo(name = "absolute_magnitude") val absoluteMagnitude: Double?,
    @ColumnInfo(name = "slope_parameter") val slopeParameter: Double?,
    @ColumnInfo(name = "perihelion_distance_au") val perihelionDistanceAu: Double,
    val eccentricity: Double,
    @ColumnInfo(name = "perihelion_time") val perihelionTimeMillis: Long?,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** One day of a comet's Horizons ephemeris. Each refresh replaces a comet's points together. */
@Entity(tableName = "comet_ephemeris", primaryKeys = ["designation", "time"])
data class CometEphemerisEntity(
    val designation: String,
    @ColumnInfo(name = "time") val timeMillis: Long,
    @ColumnInfo(name = "ra_deg") val rightAscensionDegrees: Double,
    @ColumnInfo(name = "dec_deg") val declinationDegrees: Double,
    val magnitude: Double?,
    @ColumnInfo(name = "sun_distance_au") val sunDistanceAu: Double,
    @ColumnInfo(name = "earth_distance_au") val earthDistanceAu: Double,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)

/** A comet with its stored ephemeris, read in one transaction. */
data class CometWithEphemeris(
    @Embedded val comet: CometEntity,
    @Relation(parentColumn = "designation", entityColumn = "designation")
    val ephemeris: List<CometEphemerisEntity>,
)

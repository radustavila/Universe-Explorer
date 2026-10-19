package com.radustavila.nasaexplorer.data.smallbodies.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** One close approach; an object may pass twice in the window, hence the composite key. Times are epoch ms. */
@Entity(tableName = "close_approaches", primaryKeys = ["designation", "time"], indices = [Index("time")])
data class CloseApproachEntity(
    @ColumnInfo(name = "designation") val designation: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "distance_au") val distanceAu: Double,
    @ColumnInfo(name = "velocity_km_s") val relativeVelocityKmPerSecond: Double,
    @ColumnInfo(name = "absolute_magnitude") val absoluteMagnitude: Double?,
    @ColumnInfo(name = "diameter_km") val diameterKm: Double?,
)

@Entity(tableName = "sentry_objects")
data class SentryObjectEntity(
    @PrimaryKey @ColumnInfo(name = "designation") val designation: String,
    @ColumnInfo(name = "impact_probability") val impactProbability: Double,
    @ColumnInfo(name = "palermo_max") val palermoMax: Double?,
    @ColumnInfo(name = "torino_max") val torinoMax: Int?,
    @ColumnInfo(name = "potential_impacts") val potentialImpacts: Int?,
    @ColumnInfo(name = "year_range") val yearRange: String?,
)

data class CloseApproachWithSentry(
    @Embedded val approach: CloseApproachEntity,
    @Relation(parentColumn = "designation", entityColumn = "designation")
    val sentry: SentryObjectEntity?,
)

@Entity(tableName = "fireballs")
data class FireballEntity(
    @PrimaryKey @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "radiated_energy_j") val radiatedEnergyJoules: Double,
    @ColumnInfo(name = "impact_energy_kt") val impactEnergyKilotons: Double,
    @ColumnInfo(name = "latitude") val latitude: Double?,
    @ColumnInfo(name = "longitude") val longitude: Double?,
    @ColumnInfo(name = "altitude_km") val altitudeKm: Double?,
    @ColumnInfo(name = "velocity_km_s") val velocityKmPerSecond: Double?,
)

@Entity(tableName = "small_body_details")
data class SmallBodyDetailsEntity(
    @PrimaryKey @ColumnInfo(name = "designation") val designation: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "orbit_class") val orbitClass: String?,
    @ColumnInfo(name = "neo") val isNearEarthObject: Boolean,
    @ColumnInfo(name = "pha") val isPotentiallyHazardous: Boolean,
    @ColumnInfo(name = "earth_moid_au") val earthMoidAu: Double?,
    @ColumnInfo(name = "semi_major_axis_au") val semiMajorAxisAu: Double?,
    @ColumnInfo(name = "eccentricity") val eccentricity: Double?,
    @ColumnInfo(name = "inclination_deg") val inclinationDegrees: Double?,
    @ColumnInfo(name = "perihelion_au") val perihelionAu: Double?,
    @ColumnInfo(name = "aphelion_au") val aphelionAu: Double?,
    @ColumnInfo(name = "period_days") val orbitalPeriodDays: Double?,
    @ColumnInfo(name = "absolute_magnitude") val absoluteMagnitude: Double?,
    @ColumnInfo(name = "diameter_km") val diameterKm: Double?,
    @ColumnInfo(name = "first_observed") val firstObserved: String?,
    @ColumnInfo(name = "last_observed") val lastObserved: String?,
    @ColumnInfo(name = "observations_used") val observationsUsed: Int?,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.OrbitalElements
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A position (km) and velocity (km/s) in the TEME frame SGP4 works in. */
internal data class StateVector(val position: Vector3, val velocity: Vector3)

internal data class Vector3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun times(factor: Double) = Vector3(x * factor, y * factor, z * factor)
    infix fun dot(other: Vector3) = x * other.x + y * other.y + z * other.z
    val length: Double get() = sqrt(this dot this)
}

/**
 * SGP4, the propagator two-line element sets are fitted for, ported from David Vallado's reference
 * implementation (Spacetrack Report #3 as revised in "Revisiting Spacetrack Report #3", AIAA
 * 2006-6753) with WGS-72 constants, as CelesTrak's elements expect.
 *
 * Only the near-Earth branch is implemented: deep-space objects (orbital period of 225 minutes or
 * more, which need SDP4's lunar and solar terms) are rejected. Every crewed station is far below
 * that.
 */
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod", "VariableNaming", "LocalVariableName")
internal class Sgp4(elements: OrbitalElements) {

    val epoch: Instant = elements.epoch

    private val bstar = elements.bstar
    private val ecco = elements.eccentricity
    private val inclo = Math.toRadians(elements.inclinationDegrees)
    private val nodeo = Math.toRadians(elements.raanDegrees)
    private val argpo = Math.toRadians(elements.argOfPericenterDegrees)
    private val mo = Math.toRadians(elements.meanAnomalyDegrees)

    private val isimp: Boolean
    private val noUnkozai: Double
    private val con41: Double
    private val x1mth2: Double
    private val x7thm1: Double
    private val cc1: Double
    private val cc4: Double
    private val cc5: Double
    private val d2: Double
    private val d3: Double
    private val d4: Double
    private val delmo: Double
    private val eta: Double
    private val argpdot: Double
    private val omgcof: Double
    private val sinmao: Double
    private val t2cof: Double
    private val t3cof: Double
    private val t4cof: Double
    private val t5cof: Double
    private val xlcof: Double
    private val aycof: Double
    private val xmcof: Double
    private val nodecf: Double
    private val nodedot: Double
    private val mdot: Double

    init {
        require(elements.meanMotion > 0) { "mean motion must be positive" }
        require(ecco in 0.0..<1.0) { "eccentricity must be in [0, 1)" }
        val noKozai = elements.meanMotion * 2.0 * PI / MINUTES_PER_DAY

        // initl: recover the original mean motion and semi-major axis from the Kozai mean motion.
        val eccsq = ecco * ecco
        val omeosq = 1.0 - eccsq
        val rteosq = sqrt(omeosq)
        val cosio = cos(inclo)
        val cosio2 = cosio * cosio
        val ak = (XKE / noKozai).pow(X2O3)
        val d1 = 0.75 * J2 * (3.0 * cosio2 - 1.0) / (rteosq * omeosq)
        var del = d1 / (ak * ak)
        val adel = ak * (1.0 - del * del - del * (1.0 / 3.0 + 134.0 * del * del / 81.0))
        del = d1 / (adel * adel)
        noUnkozai = noKozai / (1.0 + del)
        require(2.0 * PI / noUnkozai < DEEP_SPACE_PERIOD_MINUTES) {
            "deep-space orbits (period of 225 minutes or more) are not supported"
        }

        val ao = (XKE / noUnkozai).pow(X2O3)
        val sinio = sin(inclo)
        val po = ao * omeosq
        val con42 = 1.0 - 5.0 * cosio2
        con41 = -con42 - cosio2 - cosio2
        val posq = po * po
        val rp = ao * (1.0 - ecco)

        // sgp4init
        val ss = 78.0 / RADIUS_EARTH_KM + 1.0
        val qzms2t = ((120.0 - 78.0) / RADIUS_EARTH_KM).pow(4)
        isimp = rp < (220.0 / RADIUS_EARTH_KM + 1.0)

        var sfour = ss
        var qzms24 = qzms2t
        val perige = (rp - 1.0) * RADIUS_EARTH_KM
        if (perige < 156.0) {
            sfour = if (perige < 98.0) 20.0 else perige - 78.0
            qzms24 = ((120.0 - sfour) / RADIUS_EARTH_KM).pow(4)
            sfour = sfour / RADIUS_EARTH_KM + 1.0
        }
        val pinvsq = 1.0 / posq
        val tsi = 1.0 / (ao - sfour)
        eta = ao * ecco * tsi
        val etasq = eta * eta
        val eeta = ecco * eta
        val psisq = abs(1.0 - etasq)
        val coef = qzms24 * tsi.pow(4)
        val coef1 = coef / psisq.pow(3.5)
        val cc2 = coef1 * noUnkozai * (
            ao * (1.0 + 1.5 * etasq + eeta * (4.0 + etasq)) +
                0.375 * J2 * tsi / psisq * con41 * (8.0 + 3.0 * etasq * (8.0 + etasq))
            )
        cc1 = bstar * cc2
        val cc3 = if (ecco > 1.0e-4) -2.0 * coef * tsi * J3OJ2 * noUnkozai * sinio / ecco else 0.0
        x1mth2 = 1.0 - cosio2
        cc4 = 2.0 * noUnkozai * coef1 * ao * omeosq * (
            eta * (2.0 + 0.5 * etasq) + ecco * (0.5 + 2.0 * etasq) -
                J2 * tsi / (ao * psisq) * (
                    -3.0 * con41 * (1.0 - 2.0 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                        0.75 * x1mth2 * (2.0 * etasq - eeta * (1.0 + etasq)) * cos(2.0 * argpo)
                    )
            )
        cc5 = 2.0 * coef1 * ao * omeosq * (1.0 + 2.75 * (etasq + eeta) + eeta * etasq)
        val cosio4 = cosio2 * cosio2
        val temp1 = 1.5 * J2 * pinvsq * noUnkozai
        val temp2 = 0.5 * temp1 * J2 * pinvsq
        val temp3 = -0.46875 * J4 * pinvsq * pinvsq * noUnkozai
        mdot = noUnkozai + 0.5 * temp1 * rteosq * con41 +
            0.0625 * temp2 * rteosq * (13.0 - 78.0 * cosio2 + 137.0 * cosio4)
        argpdot = -0.5 * temp1 * con42 + 0.0625 * temp2 * (7.0 - 114.0 * cosio2 + 395.0 * cosio4) +
            temp3 * (3.0 - 36.0 * cosio2 + 49.0 * cosio4)
        val xhdot1 = -temp1 * cosio
        nodedot = xhdot1 + (0.5 * temp2 * (4.0 - 19.0 * cosio2) + 2.0 * temp3 * (3.0 - 7.0 * cosio2)) * cosio
        omgcof = bstar * cc3 * cos(argpo)
        xmcof = if (ecco > 1.0e-4) -X2O3 * coef * bstar / eeta else 0.0
        nodecf = 3.5 * omeosq * xhdot1 * cc1
        t2cof = 1.5 * cc1
        xlcof = if (abs(cosio + 1.0) > 1.5e-12) {
            -0.25 * J3OJ2 * sinio * (3.0 + 5.0 * cosio) / (1.0 + cosio)
        } else {
            -0.25 * J3OJ2 * sinio * (3.0 + 5.0 * cosio) / 1.5e-12
        }
        aycof = -0.5 * J3OJ2 * sinio
        delmo = (1.0 + eta * cos(mo)).pow(3)
        sinmao = sin(mo)
        x7thm1 = 7.0 * cosio2 - 1.0

        if (!isimp) {
            val cc1sq = cc1 * cc1
            d2 = 4.0 * ao * tsi * cc1sq
            val temp = d2 * tsi * cc1 / 3.0
            d3 = (17.0 * ao + sfour) * temp
            d4 = 0.5 * temp * ao * tsi * (221.0 * ao + 31.0 * sfour) * cc1
            t3cof = d2 + 2.0 * cc1sq
            t4cof = 0.25 * (3.0 * d3 + cc1 * (12.0 * d2 + 10.0 * cc1sq))
            t5cof = 0.2 * (3.0 * d4 + 12.0 * cc1 * d3 + 6.0 * d2 * d2 + 15.0 * cc1sq * (2.0 * d2 + cc1sq))
        } else {
            d2 = 0.0
            d3 = 0.0
            d4 = 0.0
            t3cof = 0.0
            t4cof = 0.0
            t5cof = 0.0
        }
    }

    fun propagate(at: Instant): StateVector =
        propagateMinutes(Duration.between(epoch, at).toNanos() / NANOS_PER_MINUTE)

    /** @param tsince minutes since [epoch]. */
    fun propagateMinutes(tsince: Double): StateVector {
        val t = tsince
        val xmdf = mo + mdot * t
        val argpdf = argpo + argpdot * t
        val nodedf = nodeo + nodedot * t
        var argpm = argpdf
        var mm = xmdf
        val t2 = t * t
        var nodem = nodedf + nodecf * t2
        var tempa = 1.0 - cc1 * t
        var tempe = bstar * cc4 * t
        var templ = t2cof * t2

        if (!isimp) {
            val delomg = omgcof * t
            val delm = xmcof * ((1.0 + eta * cos(xmdf)).pow(3) - delmo)
            val temp = delomg + delm
            mm = xmdf + temp
            argpm = argpdf - temp
            val t3 = t2 * t
            val t4 = t3 * t
            tempa = tempa - d2 * t2 - d3 * t3 - d4 * t4
            tempe += bstar * cc5 * (sin(mm) - sinmao)
            templ += t3cof * t3 + t4 * (t4cof + t * t5cof)
        }

        val am = (XKE / noUnkozai).pow(X2O3) * tempa * tempa
        val nm = XKE / am.pow(1.5)
        var em = ecco - tempe
        check(em < 1.0 && em >= -0.001) { "eccentricity out of range after $tsince minutes: $em" }
        if (em < 1.0e-6) em = 1.0e-6
        mm += noUnkozai * templ
        var xlm = mm + argpm + nodem
        nodem = nodem.rem(TWO_PI)
        argpm = argpm.rem(TWO_PI)
        xlm = xlm.rem(TWO_PI)
        mm = (xlm - argpm - nodem).rem(TWO_PI)

        val sinim = sin(inclo)
        val cosim = cos(inclo)

        // Long-period periodics.
        val axnl = em * cos(argpm)
        var temp = 1.0 / (am * (1.0 - em * em))
        val aynl = em * sin(argpm) + temp * aycof
        val xl = mm + argpm + nodem + temp * xlcof * axnl

        // Kepler's equation.
        val u = (xl - nodem).rem(TWO_PI)
        var eo1 = u
        var tem5 = 9999.9
        var iterations = 1
        var sineo1 = 0.0
        var coseo1 = 0.0
        while (abs(tem5) >= 1.0e-12 && iterations <= 10) {
            sineo1 = sin(eo1)
            coseo1 = cos(eo1)
            tem5 = 1.0 - coseo1 * axnl - sineo1 * aynl
            tem5 = (u - aynl * coseo1 + axnl * sineo1 - eo1) / tem5
            if (abs(tem5) >= 0.95) tem5 = if (tem5 > 0.0) 0.95 else -0.95
            eo1 += tem5
            iterations++
        }

        // Short-period preliminary quantities.
        val ecose = axnl * coseo1 + aynl * sineo1
        val esine = axnl * sineo1 - aynl * coseo1
        val el2 = axnl * axnl + aynl * aynl
        val pl = am * (1.0 - el2)
        check(pl >= 0.0) { "semi-latus rectum negative after $tsince minutes" }
        val rl = am * (1.0 - ecose)
        val rdotl = sqrt(am) * esine / rl
        val rvdotl = sqrt(pl) / rl
        val betal = sqrt(1.0 - el2)
        temp = esine / (1.0 + betal)
        val sinu = am / rl * (sineo1 - aynl - axnl * temp)
        val cosu = am / rl * (coseo1 - axnl + aynl * temp)
        var su = atan2(sinu, cosu)
        val sin2u = (cosu + cosu) * sinu
        val cos2u = 1.0 - 2.0 * sinu * sinu
        temp = 1.0 / pl
        val temp1 = 0.5 * J2 * temp
        val temp2 = temp1 * temp

        // Short-period periodics.
        val mrt = rl * (1.0 - 1.5 * temp2 * betal * con41) + 0.5 * temp1 * x1mth2 * cos2u
        su -= 0.25 * temp2 * x7thm1 * sin2u
        val xnode = nodem + 1.5 * temp2 * cosim * sin2u
        val xinc = inclo + 1.5 * temp2 * cosim * sinim * cos2u
        val mvt = rdotl - nm * temp1 * x1mth2 * sin2u / XKE
        val rvdot = rvdotl + nm * temp1 * (x1mth2 * cos2u + 1.5 * con41) / XKE
        check(mrt >= 1.0) { "satellite has decayed after $tsince minutes" }

        val sinsu = sin(su)
        val cossu = cos(su)
        val snod = sin(xnode)
        val cnod = cos(xnode)
        val sini = sin(xinc)
        val cosi = cos(xinc)
        val xmx = -snod * cosi
        val xmy = cnod * cosi
        val ux = xmx * sinsu + cnod * cossu
        val uy = xmy * sinsu + snod * cossu
        val uz = sini * sinsu
        val vx = xmx * cossu - cnod * sinsu
        val vy = xmy * cossu - snod * sinsu
        val vz = sini * cossu

        return StateVector(
            position = Vector3(ux, uy, uz) * (mrt * RADIUS_EARTH_KM),
            velocity = (Vector3(ux, uy, uz) * mvt + Vector3(vx, vy, vz) * rvdot) * VKM_PER_SEC,
        )
    }

    companion object {
        // WGS-72, the constants the elements were fitted with.
        const val RADIUS_EARTH_KM = 6378.135
        private const val MU = 398600.8
        private val XKE = 60.0 / sqrt(RADIUS_EARTH_KM * RADIUS_EARTH_KM * RADIUS_EARTH_KM / MU)
        private const val J2 = 0.001082616
        private const val J3 = -0.00000253881
        private const val J4 = -0.00000165597
        private const val J3OJ2 = J3 / J2
        private val VKM_PER_SEC = RADIUS_EARTH_KM * XKE / 60.0

        private const val X2O3 = 2.0 / 3.0
        private const val TWO_PI = 2.0 * PI
        private const val MINUTES_PER_DAY = 1440.0
        private const val NANOS_PER_MINUTE = 60.0e9
        private const val DEEP_SPACE_PERIOD_MINUTES = 225.0
    }
}

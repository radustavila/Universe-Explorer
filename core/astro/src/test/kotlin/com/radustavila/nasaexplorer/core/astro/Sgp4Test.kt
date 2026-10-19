package com.radustavila.nasaexplorer.core.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** State vectors against Vallado's reference implementation (python-sgp4 2.25, WGS-72). */
class Sgp4Test {

    @Test
    fun `Vanguard 1 matches the reference implementation`() {
        val sgp4 = Sgp4(ReferenceElements.VANGUARD_1)

        sgp4.assertState(0.0, Vector3(7022.465293, -1400.082968, 0.039952), Vector3(1.893841015, 6.405893759, 4.534807250))
        sgp4.assertState(360.0, Vector3(-7154.031202, -3783.176825, -3536.194123), Vector3(4.741887409, -4.151817765, -2.093935425))
        sgp4.assertState(1440.0, Vector3(-938.559239, -6268.187488, -4294.029248), Vector3(7.536105209, -0.427127707, 0.989878080))
        sgp4.assertState(4320.0, Vector3(-9060.473736, 4658.709525, 813.686732), Vector3(-2.232832783, -4.110453490, -3.157345433))
    }

    @Test
    fun `ISS matches the reference implementation`() {
        val sgp4 = Sgp4(ReferenceElements.ISS)

        sgp4.assertState(0.0, Vector3(4677.640706, 4932.470445, 0.007013), Vector3(-3.450049004, 3.269676344, 6.008425944))
        sgp4.assertState(360.0, Vector3(5502.266372, 1310.980922, -3770.770470), Vector3(1.427971959, 6.212651255, 4.243052289))
        sgp4.assertState(1440.0, Vector3(-5091.001805, -4507.836209, 6.939733), Vector3(3.144248181, -3.562712724, -6.006492999))
        sgp4.assertState(4320.0, Vector3(-5793.486666, -3560.977050, 14.054645), Vector3(2.475886668, -4.055372342, -6.006026128))
    }

    @Test
    fun `propagating by instant is the same as by minutes since epoch`() {
        val sgp4 = Sgp4(ReferenceElements.ISS)

        val byInstant = sgp4.propagate(ReferenceElements.ISS.epoch.plusSeconds(360L * 60))

        assertEquals(5502.266372, byInstant.position.x, POSITION_TOLERANCE_KM)
    }

    @Test
    fun `deep-space orbits are rejected`() {
        val geostationary = ReferenceElements.ISS.copy(meanMotion = 1.0027)

        assertThrows(IllegalArgumentException::class.java) { Sgp4(geostationary) }
    }

    private fun Sgp4.assertState(minutes: Double, position: Vector3, velocity: Vector3) {
        val state = propagateMinutes(minutes)
        assertEquals("x at $minutes", position.x, state.position.x, POSITION_TOLERANCE_KM)
        assertEquals("y at $minutes", position.y, state.position.y, POSITION_TOLERANCE_KM)
        assertEquals("z at $minutes", position.z, state.position.z, POSITION_TOLERANCE_KM)
        assertEquals("vx at $minutes", velocity.x, state.velocity.x, VELOCITY_TOLERANCE_KM_S)
        assertEquals("vy at $minutes", velocity.y, state.velocity.y, VELOCITY_TOLERANCE_KM_S)
        assertEquals("vz at $minutes", velocity.z, state.velocity.z, VELOCITY_TOLERANCE_KM_S)
    }

    private companion object {
        /** One metre; the reference prints millimetres. */
        const val POSITION_TOLERANCE_KM = 1e-3
        const val VELOCITY_TOLERANCE_KM_S = 1e-6
    }
}

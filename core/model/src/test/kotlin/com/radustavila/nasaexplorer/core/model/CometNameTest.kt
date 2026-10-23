package com.radustavila.nasaexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CometNameTest {

    @Test
    fun `a comet goes by its discoverers`() {
        assertEquals("Bok", cometShortName("C/2026 A2 (Bok)"))
        assertEquals("Tsuchinshan-ATLAS", cometShortName("C/2023 A3 (Tsuchinshan-ATLAS)"))
        assertEquals("Pons-Brooks", cometShortName("12P/Pons-Brooks"))
    }

    @Test
    fun `a comet without a name keeps its designation`() {
        assertEquals("P/2019 LD2", cometShortName("P/2019 LD2"))
        assertEquals("2026 X1", cometShortName("2026 X1"))
    }
}

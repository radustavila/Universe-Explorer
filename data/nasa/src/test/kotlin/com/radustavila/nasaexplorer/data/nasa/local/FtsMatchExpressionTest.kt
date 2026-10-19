package com.radustavila.nasaexplorer.data.nasa.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsMatchExpressionTest {

    @Test
    fun `every word becomes a lowercase prefix term`() {
        assertEquals("spiral* galax*", ftsMatchExpression("  Spiral GALAX "))
    }

    @Test
    fun `query syntax typed by the user never reaches the engine`() {
        assertEquals("m16* or* pillars*", ftsMatchExpression("\"M16\" OR -pillars*"))
        assertEquals("comète* 2026*", ftsMatchExpression("comète (2026)"))
    }

    @Test
    fun `nothing searchable yields no expression`() {
        assertNull(ftsMatchExpression(""))
        assertNull(ftsMatchExpression(" \"*()- "))
    }
}

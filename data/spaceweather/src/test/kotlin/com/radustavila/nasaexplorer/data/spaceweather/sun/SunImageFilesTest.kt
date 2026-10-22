package com.radustavila.nasaexplorer.data.spaceweather.sun

import com.radustavila.nasaexplorer.core.model.SunChannel
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SunImageFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory by lazy { File(folder.root, "sun_images") }
    private val now = Instant.parse("2026-10-22T16:00:00Z")

    private fun files() = SunImageFiles(directory, UnconfinedTestDispatcher())

    @Test
    fun `a saved picture replaces the channel's previous one and survives a restart`() = runTest {
        val files = files()
        files.save(SunChannel.CORONA, now.minusSeconds(900), "old".byteInputStream())
        files.save(SunChannel.CORONA, now, "new".byteInputStream())

        val stored = files.current(SunChannel.CORONA)!!
        assertEquals(now, stored.updatedAt)
        assertEquals("new", stored.file.readText())
        assertEquals(listOf("0171_${now.epochSecond}.jpg"), directory.list()!!.toList())

        val reopened = files().images.first()
        assertEquals(mapOf(SunChannel.CORONA to stored), reopened)
    }

    @Test
    fun `eviction drops old pictures and interrupted downloads but keeps the newest`() = runTest {
        val files = files()
        files.save(SunChannel.CORONA, now.minus(Duration.ofDays(5)), "a".byteInputStream())
        files.save(SunChannel.FLARES, now.minus(Duration.ofDays(4)), "b".byteInputStream())
        files.save(SunChannel.CHROMOSPHERE, now.minus(Duration.ofHours(1)), "c".byteInputStream())
        File(directory, "HMIIC.part").writeText("partial")

        assertEquals(3, files.evict(now, Duration.ofDays(2)))

        assertEquals(setOf(SunChannel.CHROMOSPHERE), files.images.first().keys)
        assertEquals(1, directory.list()!!.size)
    }

    @Test
    fun `the newest picture outlives a long offline spell`() = runTest {
        val files = files()
        files.save(SunChannel.CORONA, now.minus(Duration.ofDays(30)), "a".byteInputStream())

        assertEquals(0, files.evict(now, Duration.ofDays(2)))
        assertTrue(files.current(SunChannel.CORONA)!!.file.exists())
    }

    @Test
    fun `clearing deletes every picture and the size follows`() = runTest {
        val files = files()
        files.save(SunChannel.CORONA, now, "12345".byteInputStream())
        assertEquals(5L, files.sizeInBytes())

        files.clear()

        assertNull(files.current(SunChannel.CORONA))
        assertTrue(files.images.first().isEmpty())
        assertEquals(0L, files.sizeInBytes())
        assertFalse(directory.listFiles().orEmpty().any())
    }

    @Test
    fun `unknown files are ignored`() = runTest {
        directory.mkdirs()
        File(directory, "notes.txt").writeText("x")
        File(directory, "XYZ_123.jpg").writeText("x")

        assertTrue(files().images.first().isEmpty())
    }
}

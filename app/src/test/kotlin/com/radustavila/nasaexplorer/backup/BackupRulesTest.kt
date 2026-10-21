package com.radustavila.nasaexplorer.backup

import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.storage.DatabaseStorage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards what leaves the device in a backup: only the favourites database and the user's choices.
 * Lint's own FullBackupContent / DataExtractionRules checks validate the files' syntax and paths;
 * this checks their meaning, for both formats (Android 12+ and older).
 */
class BackupRulesTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun includes(file: String, section: String? = null): Set<Pair<String, String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = section?.let { document.getElementsByTagName(it).item(0) as Element } ?: document.documentElement
        assertEquals("only includes: anything not included is left out", 0, root.getElementsByTagName("exclude").length)
        val nodes = root.getElementsByTagName("include")
        return (0 until nodes.length).map { index ->
            val include = nodes.item(index) as Element
            include.getAttribute("domain") to include.getAttribute("path")
        }.toSet()
    }

    private val expected = setOf(
        "database" to NasaDatabase.NAME,
        "database" to "${NasaDatabase.NAME}-wal",
        "file" to "datastore/user_preferences.preferences_pb",
        "file" to "datastore/observer_location.preferences_pb",
        "file" to "datastore/sky_preferences.preferences_pb",
        "file" to "datastore/aurora_alerts.preferences_pb",
    )

    @Test
    fun `the manifest points to both rule files`() {
        assertTrue(manifest.contains("""android:allowBackup="true""""))
        assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""))
        assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules""""))
    }

    @Test
    fun `cloud backup, device transfer and legacy backup keep exactly the favourites and the user's choices`() {
        assertEquals(expected, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(expected, includes("data_extraction_rules.xml", "device-transfer"))
        assertEquals(expected, includes("backup_rules.xml"))
    }

    @Test
    fun `no cache, mirror database, offline image or sync bookkeeping is backed up`() {
        val paths = includes("data_extraction_rules.xml", "cloud-backup") + includes("backup_rules.xml")
        val domains = paths.map { it.first }.toSet()
        assertFalse(domains.any { it.contains("cache") })
        DatabaseStorage.MIRROR_DATABASES.forEach { mirror ->
            assertFalse(mirror, paths.any { (_, path) -> path.startsWith(mirror) })
        }
        assertFalse(paths.any { (_, path) -> path.startsWith("favorites") || path.contains("image") })
        assertFalse(paths.any { (_, path) -> path.contains("sync_metadata") })
        // SQLite rebuilds its shared-memory index; a restored one could be stale.
        assertFalse(paths.any { (_, path) -> path.endsWith("-shm") })
    }
}

package com.polylingy.client

import java.nio.file.Files
import java.time.LocalDate
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DbBackupTest {
    private val dir = Files.createTempDirectory("polylingy-backup")

    @AfterTest fun cleanUp() { dir.toFile().deleteRecursively() }

    private fun names() = Files.list(dir.resolve("backups")).use { s -> s.map { it.name }.sorted().toList() }

    private fun createDb() = DesktopStorage.open(dir).use { it.deviceId }

    @Test fun nothingHappensBeforeThereIsADatabase() {
        assertEquals(emptyList(), DbBackup.run(dir, LocalDate.of(2026, 11, 1)))
        assertTrue(!dir.resolve("backups").exists())
    }

    @Test fun theFirstStartOfADayMakesOneCopyThatIsAWorkingDatabase() {
        val device = createDb()
        val made = DbBackup.run(dir, LocalDate.of(2026, 11, 1))
        assertEquals(listOf("polylingy-2026-11-01.db"), made.map { it.name })
        assertEquals(emptyList(), DbBackup.run(dir, LocalDate.of(2026, 11, 1)), "once a day")
        val copyDir = Files.createTempDirectory("polylingy-restore")
        try {
            Files.copy(made.single(), copyDir.resolve("polylingy.db"))
            assertEquals(device, DesktopStorage.open(copyDir).use { it.deviceId }, "the copy opens and has the data")
        } finally { copyDir.toFile().deleteRecursively() }
        assertTrue(names().none { it.endsWith(".part") })
    }

    @Test fun onlyTheLastTwoWeeksAreKept() {
        createDb()
        for (day in 1..20) DbBackup.run(dir, LocalDate.of(2026, 11, 1).plusDays(day - 1L))
        val kept = names()
        assertEquals(DbBackup.KEEP_DAILY, kept.size)
        assertEquals("polylingy-2026-11-07.db", kept.first())
        assertEquals("polylingy-2026-11-20.db", kept.last())
    }

    @Test fun aDatabaseOfAnOlderSchemaIsCopiedBeforeItIsMigrated() {
        createDb()
        java.sql.DriverManager.getConnection("jdbc:sqlite:${dir.resolve("polylingy.db")}").use { it.createStatement().execute("PRAGMA user_version = 1") }
        val made = DbBackup.run(dir, LocalDate.of(2026, 11, 1), schemaVersion = 2)
        assertEquals(setOf("polylingy-2026-11-01.db", "polylingy-2026-11-01-before-schema-2.db"), made.map { it.name }.toSet())
        assertEquals(emptyList(), DbBackup.run(dir, LocalDate.of(2026, 11, 1), schemaVersion = 1), "a current database is not copied as a migration backup")
    }

    @Test fun theMirrorGetsTheDailyCopyAndKeepsTwoWeeks() {
        createDb()
        val mirror = dir.resolve("mirror")
        for (day in 1..16) DbBackup.run(dir, LocalDate.of(2026, 11, 1).plusDays(day - 1L), mirror = mirror)
        val copies = Files.list(mirror).use { s -> s.map { it.name }.sorted().toList() }
        assertEquals(DbBackup.KEEP_DAILY, copies.size)
        assertEquals("polylingy-2026-11-16.db", copies.last())
    }

    @Test fun aMirrorThatCantBeWrittenNeverStopsTheLocalBackup() {
        createDb()
        Files.writeString(dir.resolve("blocked"), "a file, not a folder")
        val made = DbBackup.run(dir, LocalDate.of(2026, 11, 1), mirror = dir.resolve("blocked").resolve("sub"))
        assertEquals(listOf("polylingy-2026-11-01.db"), made.map { it.name })
    }

    @Test fun aBackupThatCantBeMadeNeverThrows() {
        Files.writeString(dir.resolve("polylingy.db"), "this is not a database")
        DbBackup.run(dir, LocalDate.of(2026, 11, 1))
    }
}

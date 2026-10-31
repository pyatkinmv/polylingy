package com.polylingy.client

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.polylingy.client.db.PolylingyDb
import java.nio.file.Path
import java.util.Properties
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.createDirectories

object DesktopStorage {
    /** `<Documents>/polylingy`, next to (but separate from) the Flutter version's files. */
    fun defaultDataDir(): Path = FileSystemView.getFileSystemView().defaultDirectory.toPath().resolve("polylingy")

    /** Opens the database file, creating it or migrating its schema as needed. */
    fun open(dbFile: Path = defaultDataDir().resolve("polylingy.db")): LocalStorage {
        dbFile.parent?.createDirectories()
        return LocalStorage(JdbcSqliteDriver("jdbc:sqlite:$dbFile", Properties(), PolylingyDb.Schema))
    }

    fun inMemory(): LocalStorage = LocalStorage(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), PolylingyDb.Schema))

    /** The bundled sample course in the course file format. */
    fun sampleCourseJson(): String =
        requireNotNull(DesktopStorage::class.java.getResource("/sample_course.json")) { "sample_course.json is not bundled" }
            .readText()
}

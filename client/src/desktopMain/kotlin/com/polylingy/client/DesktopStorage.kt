package com.polylingy.client

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.polylingy.client.db.PolylingyDb
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.Properties
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.createDirectories

private val log = KotlinLogging.logger {}

object DesktopStorage {
    /** `<Documents>/polylingy`. */
    fun defaultDataDir(): Path = FileSystemView.getFileSystemView().defaultDirectory.toPath().resolve("polylingy")

    /** Opens `polylingy.db` in [dataDir], creating it as needed. */
    fun open(dataDir: Path = defaultDataDir()): LocalStorage {
        dataDir.createDirectories()
        val dbFile = dataDir.resolve("polylingy.db")
        log.info { "Opening database $dbFile" }
        return LocalStorage(JdbcSqliteDriver("jdbc:sqlite:$dbFile", Properties(), PolylingyDb.Schema))
    }

    fun inMemory(): LocalStorage = LocalStorage(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), PolylingyDb.Schema))
}

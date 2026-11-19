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
    /**
     * Folder of the installed app's data: `%LOCALAPPDATA%\Polylingy data`, not Documents, which OneDrive often syncs file by file
     * (a database being written to must not be). The folder of a run from the sources is [DEV_DIR], under Documents.
     */
    const val INSTALLED_DIR = "Polylingy data"

    /** Where copies of the installed app's database also go: static files, which is what a cloud folder is good for. */
    const val MIRROR_DIR = "Polylingy backups"

    /** Data of runs from the sources (Gradle, IDE, tests), kept apart from the installed app's. */
    const val DEV_DIR = "polylingy"

    /** True in the app packaged by `packageMsi` (jpackage sets this property for its launcher), false when run from the sources. */
    fun isInstalled(): Boolean = System.getProperty("jpackage.app-path") != null

    /**
     * Where the data lives: the folder named by the system property `polylingy.dataDir` when there is one; otherwise
     * `%LOCALAPPDATA%\Polylingy data` for the installed app and `<Documents>/polylingy` for a run from the sources,
     * so the two never share a database, courses, pictures, logs or backups.
     */
    fun defaultDataDir(): Path {
        System.getProperty("polylingy.dataDir")?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
        if (!isInstalled()) return documents().resolve(DEV_DIR)
        val local = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".local", "share")
        return local.resolve(INSTALLED_DIR)
    }

    /** The user's Documents folder. */
    fun documents(): Path = FileSystemView.getFileSystemView().defaultDirectory.toPath()

    /**
     * Where the installed app puts a second copy of each daily backup; null for a run from the sources and when the data
     * folder is chosen with `polylingy.dataDir` (a trial or a portable copy has no business in the user's Documents).
     */
    fun backupMirror(): Path? = if (isInstalled() && System.getProperty("polylingy.dataDir").isNullOrBlank()) documents().resolve(MIRROR_DIR) else null

    /** Opens `polylingy.db` in [dataDir], creating it as needed. */
    fun open(dataDir: Path = defaultDataDir()): LocalStorage {
        dataDir.createDirectories()
        val dbFile = dataDir.resolve("polylingy.db")
        log.info { "Opening database $dbFile" }
        return LocalStorage(JdbcSqliteDriver("jdbc:sqlite:$dbFile", Properties(), PolylingyDb.Schema))
    }

    fun inMemory(): LocalStorage = LocalStorage(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), PolylingyDb.Schema))
}

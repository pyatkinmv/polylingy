package com.polylingy.client

import com.polylingy.client.db.PolylingyDb
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.io.path.exists
import kotlin.io.path.name

private val log = KotlinLogging.logger {}

/**
 * Copies of `polylingy.db` in `<data dir>/backups/`, made before the app opens the database:
 *  - `polylingy-<date>.db`, the first start of each day, the last [KEEP_DAILY] kept;
 *  - `polylingy-<date>-before-schema-<n>.db` when the file is of an older schema than this build, so a migration can always
 *    be undone by putting the copy back; the last [KEEP_BEFORE_MIGRATION] kept.
 * The copy is made by SQLite itself (`VACUUM INTO`): consistent even while another program has the file open.
 * With a [mirror] folder (the installed app: a folder under Documents, which OneDrive and the like keep off the machine) each daily
 * copy is also put there, the last [KEEP_DAILY] kept. A failure is logged and never stops the app.
 */
object DbBackup {
    const val KEEP_DAILY = 14
    const val KEEP_BEFORE_MIGRATION = 5

    private const val DB = "polylingy.db"

    /** Makes the backups due [today]; returns the files it made. */
    fun run(dataDir: Path, today: LocalDate = LocalDate.now(), schemaVersion: Int = PolylingyDb.Schema.version.toInt(), mirror: Path? = null): List<Path> {
        val db = dataDir.resolve(DB)
        if (!db.exists()) return emptyList()
        val backups = dataDir.resolve("backups")
        val made = mutableListOf<Path>()
        try {
            Files.createDirectories(backups)
            val stored = userVersion(db)
            if (stored in 1 until schemaVersion) copy(db, backups.resolve("polylingy-$today-before-schema-$schemaVersion.db").takeUnless { it.exists() })?.let(made::add)
            copy(db, backups.resolve("polylingy-$today.db").takeUnless { it.exists() })?.let(made::add)
            if (mirror != null) {
                val daily = backups.resolve("polylingy-$today.db")
                try {
                    Files.createDirectories(mirror)
                    if (daily.exists() && !mirror.resolve(daily.name).exists()) Files.copy(daily, mirror.resolve(daily.name))
                    prune(mirror, Regex("""polylingy-\d{4}-\d{2}-\d{2}\.db"""), KEEP_DAILY)
                } catch (e: Exception) {
                    log.warn(e) { "Couldn't copy the backup to $mirror" }
                }
            }
            prune(backups, Regex("""polylingy-\d{4}-\d{2}-\d{2}\.db"""), KEEP_DAILY)
            prune(backups, Regex("""polylingy-\d{4}-\d{2}-\d{2}-before-schema-\d+\.db"""), KEEP_BEFORE_MIGRATION)
        } catch (e: Exception) {
            log.error(e) { "Backup of $db failed" }
        }
        return made
    }

    private fun userVersion(db: Path): Int = DriverManager.getConnection("jdbc:sqlite:$db").use { c ->
        c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.getInt(1) } }
    }

    private fun copy(db: Path, target: Path?): Path? {
        if (target == null) return null
        val part = target.resolveSibling(target.name + ".part")
        Files.deleteIfExists(part)
        DriverManager.getConnection("jdbc:sqlite:$db").use { c -> c.createStatement().use { it.execute("VACUUM INTO '${part.toString().replace("'", "''")}'") } }
        Files.move(part, target)
        log.info { "Backed up the database to $target (${Files.size(target)} bytes)" }
        return target
    }

    /** Names carry the date, so the oldest are the first in name order. */
    private fun prune(dir: Path, pattern: Regex, keep: Int) {
        val files = Files.list(dir).use { s -> s.filter { pattern.matches(it.name) }.sorted().toList() }
        for (old in files.dropLast(keep)) {
            Files.deleteIfExists(old)
            log.info { "Removed the old backup $old" }
        }
    }
}

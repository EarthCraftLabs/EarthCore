package de.mecrytv.earthcore.logging.internal

import de.mecrytv.earthcore.database.api.DatabaseService
import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

class DatabaseSink(
    private val database: DatabaseService,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val retentionDays: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) : LogSink {

    override val name: String = "database"

    override fun accept(entry: LogEntry) {
        val record = LogRecord.of(entry)
        scope.launch {
            runCatching { database.save(record) }
                .onFailure { fehler ->
                    logger.log(Level.WARNING, "Logeintrag konnte nicht gespeichert werden.", fehler)
                }
        }
    }

    override fun close() = Unit

    suspend fun prune(): Int {
        if (retentionDays <= 0) return 0
        val grenze = clock() - retentionDays * 86_400_000L
        return runCatching { database.execute(DELETE_OLDER, grenze) }
            .onFailure { logger.log(Level.WARNING, "Alte Logeintraege konnten nicht entfernt werden.", it) }
            .getOrDefault(0)
    }

    private companion object {

        const val DELETE_OLDER = "DELETE FROM `log_entries` WHERE `createdAt` < ?"
    }
}

package de.mecrytv.earthcore.logging.internal

import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogLevel
import de.mecrytv.earthcore.logging.api.LogSink
import de.mecrytv.earthcore.logging.api.Logbook
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class StandardLogbook(
    private val plugin: String,
    private val sinks: List<LogSink>,
    private val fallback: Logger,
) : Logbook {

    override fun debug(category: String, message: String) = emit(LogLevel.DEBUG, category, message)

    override fun info(category: String, message: String) = emit(LogLevel.INFO, category, message)

    override fun warn(category: String, message: String) = emit(LogLevel.WARN, category, message)

    override fun error(category: String, message: String, error: Throwable?) =
        log(LogEntry(LogLevel.ERROR, category, message, plugin, error = error))

    override fun record(category: String, actor: UUID?, message: String, details: Map<String, Any?>) =
        log(LogEntry(LogLevel.INFO, category, message, plugin, actor, details))

    override fun log(entry: LogEntry) {
        val vollstaendig = if (entry.plugin.isEmpty()) entry.copy(plugin = plugin) else entry
        for (sink in sinks) {
            try {
                sink.accept(vollstaendig)
            } catch (ex: Throwable) {
                fallback.log(Level.WARNING, "Logziel '" + sink.name + "' hat einen Eintrag abgelehnt.", ex)
            }
        }
    }

    private fun emit(level: LogLevel, category: String, message: String) =
        log(LogEntry(level, category, message, plugin))
}

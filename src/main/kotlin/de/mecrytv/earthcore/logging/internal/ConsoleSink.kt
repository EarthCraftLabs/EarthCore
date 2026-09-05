package de.mecrytv.earthcore.logging.internal

import de.mecrytv.earthcore.logging.api.LogEntry
import de.mecrytv.earthcore.logging.api.LogLevel
import de.mecrytv.earthcore.logging.api.LogSink
import java.util.logging.Logger

class ConsoleSink(
    private val loggers: (String) -> Logger,
    private val debugEnabled: () -> Boolean,
) : LogSink {

    override val name: String = "console"

    override fun accept(entry: LogEntry) {
        if (entry.level == LogLevel.DEBUG && !debugEnabled()) return
        loggers(entry.plugin).log(entry.level.julLevel, format(entry), entry.error)
    }

    override fun close() = Unit

    companion object {

        fun format(entry: LogEntry): String = buildString {
            append('[').append(entry.category).append("] ").append(entry.message)
            entry.actor?.let { append(" von ").append(it) }
            if (entry.details.isNotEmpty()) append(" (").append(entry.renderedDetails()).append(')')
        }
    }
}

package de.mecrytv.earthcore.logging.api

import java.time.Instant
import java.util.UUID

data class LogEntry(
    val level: LogLevel,
    val category: String,
    val message: String,
    val plugin: String = "",
    val actor: UUID? = null,
    val details: Map<String, Any?> = emptyMap(),
    val error: Throwable? = null,
    val timestamp: Instant = Instant.now(),
) {

    fun renderedDetails(): String =
        details.entries.joinToString(", ") { "${it.key}=${it.value}" }

    companion object {

        @JvmStatic
        fun of(level: LogLevel, category: String, message: String): LogEntry =
            LogEntry(level, category, message)
    }
}

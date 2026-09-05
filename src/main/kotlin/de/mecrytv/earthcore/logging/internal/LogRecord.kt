package de.mecrytv.earthcore.logging.internal

import de.mecrytv.earthcore.database.annotations.Column
import de.mecrytv.earthcore.database.annotations.JsonColumn
import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import de.mecrytv.earthcore.logging.api.LogEntry
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID

@Table("log_entries")
data class LogRecord(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val level: String,
    val plugin: String,
    val category: String,
    @Column("message", text = true) val message: String,
    val actor: UUID?,
    @JsonColumn val details: Map<String, String>,
    @Column("error", text = true) val error: String?,
) {

    companion object {

        fun of(entry: LogEntry): LogRecord = LogRecord(
            id = UUID.randomUUID().toString(),
            createdAt = entry.timestamp.toEpochMilli(),
            level = entry.level.name,
            plugin = entry.plugin,
            category = entry.category,
            message = entry.message,
            actor = entry.actor,
            details = entry.details.mapValues { it.value?.toString() ?: "null" },
            error = entry.error?.let(::stacktrace),
        )

        fun stacktrace(error: Throwable): String = StringWriter().also { writer ->
            PrintWriter(writer).use { error.printStackTrace(it) }
        }.toString()
    }
}

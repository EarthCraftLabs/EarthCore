package de.mecrytv.earthcore.logging.api

import java.util.UUID

interface Logbook {

    fun debug(category: LogCategory, message: String)

    fun info(category: LogCategory, message: String)

    fun warn(category: LogCategory, message: String)

    fun error(category: LogCategory, message: String, error: Throwable?)

    fun record(category: LogCategory, actor: UUID?, message: String, details: Map<String, Any?>)

    fun log(entry: LogEntry)
}

fun Logbook.record(category: LogCategory, actor: UUID?, message: String, vararg details: Pair<String, Any?>) =
    record(category, actor, message, details.toMap())

fun Logbook.error(category: LogCategory, message: String) = error(category, message, null)

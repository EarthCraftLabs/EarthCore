package de.mecrytv.earthcore.logging.api

import java.util.UUID

interface Logbook {

    fun debug(category: String, message: String)

    fun info(category: String, message: String)

    fun warn(category: String, message: String)

    fun error(category: String, message: String, error: Throwable?)

    fun record(category: String, actor: UUID?, message: String, details: Map<String, Any?>)

    fun log(entry: LogEntry)
}

fun Logbook.record(category: String, actor: UUID?, message: String, vararg details: Pair<String, Any?>) =
    record(category, actor, message, details.toMap())

fun Logbook.error(category: String, message: String) = error(category, message, null)

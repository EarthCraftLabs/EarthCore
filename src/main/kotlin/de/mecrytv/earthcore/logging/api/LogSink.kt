package de.mecrytv.earthcore.logging.api

interface LogSink {

    val name: String

    fun accept(entry: LogEntry)

    fun close()
}

package de.mecrytv.earthcore.logging.internal

import de.mecrytv.earthcore.logging.api.LogSink
import de.mecrytv.earthcore.logging.api.Logbook
import de.mecrytv.earthcore.logging.api.LogbookProvider
import org.bukkit.plugin.Plugin
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

class StandardLogbookProvider(
    private val sinks: List<LogSink>,
    private val fallback: Logger,
) : LogbookProvider {

    private val gebunden = ConcurrentHashMap<String, Logbook>()

    override fun of(plugin: Plugin): Logbook =
        gebunden.computeIfAbsent(plugin.name) { StandardLogbook(it, sinks, plugin.logger) }

    override fun sinks(): List<String> = sinks.map { it.name }

    fun close() = sinks.forEach { sink ->
        runCatching { sink.close() }.onFailure {
            fallback.log(Level.WARNING, "Logziel '" + sink.name + "' konnte nicht geschlossen werden.", it)
        }
    }
}

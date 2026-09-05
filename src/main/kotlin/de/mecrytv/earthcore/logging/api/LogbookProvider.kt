package de.mecrytv.earthcore.logging.api

import org.bukkit.plugin.Plugin

interface LogbookProvider {

    fun of(plugin: Plugin): Logbook

    fun sinks(): List<String>
}

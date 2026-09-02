package de.mecrytv.earthcore.registry.api

import de.mecrytv.earthcore.database.api.DatabaseService
import org.bukkit.plugin.java.JavaPlugin

interface AutoRegistrar {

    fun register(plugin: JavaPlugin, database: DatabaseService, vararg packages: String): RegistrationSummary

    fun register(plugin: JavaPlugin, vararg packages: String): RegistrationSummary
}

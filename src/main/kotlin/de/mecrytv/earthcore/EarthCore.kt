package de.mecrytv.earthcore

import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.config.JsonConfigService
import de.mecrytv.earthcore.config.PluginConfig
import de.mecrytv.earthcore.config.getOrDefault
import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.database.api.DatabaseProvider
import de.mecrytv.earthcore.database.api.DatabaseService
import de.mecrytv.earthcore.database.internal.HikariDatabaseProvider
import de.mecrytv.earthcore.registry.api.AutoRegistrar
import de.mecrytv.earthcore.registry.internal.ReflectionAutoRegistrar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

class EarthCore : JavaPlugin() {

    lateinit var scope: CoroutineScope
        private set

    lateinit var configService: ConfigService
        private set

    lateinit var databases: HikariDatabaseProvider
        private set

    lateinit var database: DatabaseService
        private set

    lateinit var autoRegistrar: AutoRegistrar
        private set

    override fun onEnable() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        configService = JsonConfigService(
            file = File(dataFolder, "config.json"),
            defaults = ConfigDefaults.model(PluginConfig()),
            logger = logger,
        )

        val credentials = configService.getOrDefault("database", DatabaseCredentials())
        databases = HikariDatabaseProvider(credentials, JsonConfigService.defaultGson(pretty = false))

        try {
            database = databases.of(credentials.database)
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Datenbank '${credentials.jdbcUrl}' nicht erreichbar.", ex)
            server.pluginManager.disablePlugin(this)
            return
        }

        autoRegistrar = ReflectionAutoRegistrar()

        server.servicesManager.register(DatabaseProvider::class.java, databases, this, ServicePriority.Normal)
        server.servicesManager.register(AutoRegistrar::class.java, autoRegistrar, this, ServicePriority.Normal)
        server.servicesManager.register(ConfigService::class.java, configService, this, ServicePriority.Normal)

        logger.info("EarthCore aktiv - verbunden mit ${credentials.jdbcUrl}")
    }

    override fun onDisable() {
        server.servicesManager.unregisterAll(this)
        if (::scope.isInitialized) scope.cancel()
        if (::databases.isInitialized) databases.close()
    }
}

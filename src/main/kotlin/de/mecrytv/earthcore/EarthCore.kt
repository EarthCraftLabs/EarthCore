package de.mecrytv.earthcore

import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.config.JsonConfigService
import de.mecrytv.earthcore.config.PluginConfig
import de.mecrytv.earthcore.config.getOrDefault
import de.mecrytv.earthcore.cooldown.api.CooldownRegistry
import de.mecrytv.earthcore.cooldown.internal.CooldownRecord
import de.mecrytv.earthcore.cooldown.internal.DatabaseCooldownRegistry
import de.mecrytv.earthcore.database.api.DatabaseCredentials
import de.mecrytv.earthcore.database.api.DatabaseProvider
import de.mecrytv.earthcore.database.api.DatabaseService
import de.mecrytv.earthcore.database.internal.HikariDatabaseProvider
import de.mecrytv.earthcore.logging.api.LogSink
import de.mecrytv.earthcore.logging.api.LogbookProvider
import de.mecrytv.earthcore.logging.api.LoggingSettings
import de.mecrytv.earthcore.logging.internal.ConsoleSink
import de.mecrytv.earthcore.logging.internal.DatabaseSink
import de.mecrytv.earthcore.logging.internal.DiscordSink
import de.mecrytv.earthcore.logging.internal.HttpWebhookSender
import de.mecrytv.earthcore.logging.internal.LogRecord
import de.mecrytv.earthcore.logging.internal.StandardLogbookProvider
import de.mecrytv.earthcore.registry.api.AutoRegistrar
import de.mecrytv.earthcore.registry.internal.ReflectionAutoRegistrar
import de.mecrytv.earthcore.version.api.CoreVersion
import de.mecrytv.earthcore.version.internal.PluginCoreVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

class EarthCore : JavaPlugin() {

    lateinit var scope: CoroutineScope
        private set

    lateinit var configService: ConfigService
        private set

    lateinit var messages: ConfigService
        private set

    lateinit var databases: HikariDatabaseProvider
        private set

    lateinit var database: DatabaseService
        private set

    lateinit var autoRegistrar: AutoRegistrar
        private set

    lateinit var cooldowns: DatabaseCooldownRegistry
        private set

    lateinit var logbooks: StandardLogbookProvider
        private set

    override fun onEnable() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val coreVersion = PluginCoreVersion(pluginMeta.version)

        configService = JsonConfigService(
            file = File(dataFolder, "config.json"),
            defaults = ConfigDefaults.model(PluginConfig()),
            logger = logger,
        )

        messages = JsonConfigService(
            file = File(dataFolder, "messages.json"),
            defaults = ConfigDefaults.resource("messages.json"),
            logger = logger,
        )

        val credentials = configService.getOrDefault("database", DatabaseCredentials())
        databases = HikariDatabaseProvider(credentials, JsonConfigService.defaultGson(pretty = false), logger)

        try {
            database = databases.of(credentials.database)
            database.registerModel(CooldownRecord::class.java)
            database.registerModel(LogRecord::class.java)
            cooldowns = DatabaseCooldownRegistry(database, scope, logger)
            runBlocking { cooldowns.load() }
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Datenbank '${credentials.jdbcUrl}' nicht erreichbar.", ex)
            server.pluginManager.disablePlugin(this)
            return
        }

        val loggingSettings = configService.getOrDefault("logging", LoggingSettings())
        val databaseSink = DatabaseSink(database, scope, logger, loggingSettings.retentionDays)
        val discordSink = DiscordSink(loggingSettings.discord, HttpWebhookSender(), logger)
        val sinks = buildList<LogSink> {
            add(ConsoleSink({ server.pluginManager.getPlugin(it)?.logger ?: logger }, loggingSettings::debug))
            add(databaseSink)
            if (loggingSettings.discord.any { it.url.isNotBlank() }) add(discordSink)
        }
        logbooks = StandardLogbookProvider(sinks, logger)

        autoRegistrar = ReflectionAutoRegistrar(cooldowns, messages)

        server.servicesManager.register(DatabaseProvider::class.java, databases, this, ServicePriority.Normal)
        server.servicesManager.register(AutoRegistrar::class.java, autoRegistrar, this, ServicePriority.Normal)
        server.servicesManager.register(ConfigService::class.java, configService, this, ServicePriority.Normal)
        server.servicesManager.register(CooldownRegistry::class.java, cooldowns, this, ServicePriority.Normal)
        server.servicesManager.register(CoreVersion::class.java, coreVersion, this, ServicePriority.Normal)
        server.servicesManager.register(LogbookProvider::class.java, logbooks, this, ServicePriority.Normal)

        server.scheduler.runTaskTimerAsynchronously(this, Runnable { cooldowns.prune() }, PRUNE_TICKS, PRUNE_TICKS)
        server.scheduler.runTaskTimerAsynchronously(this, Runnable { discordSink.flush() }, FLUSH_TICKS, FLUSH_TICKS)
        server.scheduler.runTaskTimerAsynchronously(
            this,
            Runnable { scope.launch { databaseSink.prune() } },
            PRUNE_TICKS,
            PRUNE_TICKS,
        )

        logger.info("EarthCore ${coreVersion.version} aktiv - verbunden mit ${credentials.jdbcUrl}")
    }

    override fun onDisable() {
        server.servicesManager.unregisterAll(this)
        if (::scope.isInitialized) scope.cancel()
        if (::logbooks.isInitialized) logbooks.close()
        if (::databases.isInitialized) databases.close()
    }

    private companion object {

        const val PRUNE_TICKS = 20L * 60 * 5

        const val FLUSH_TICKS = 40L
    }
}

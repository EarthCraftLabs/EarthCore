package de.mecrytv.earthcore.registry.internal

import de.mecrytv.earthcore.database.annotations.Table
import de.mecrytv.earthcore.database.api.DatabaseService
import de.mecrytv.earthcore.registry.annotations.AutoCommand
import de.mecrytv.earthcore.registry.annotations.AutoListener
import de.mecrytv.earthcore.registry.api.AutoRegistrar
import de.mecrytv.earthcore.registry.api.RegisteredEntry
import de.mecrytv.earthcore.registry.api.RegistrationSummary
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ReflectionAutoRegistrar : AutoRegistrar {

    override fun register(plugin: JavaPlugin, database: DatabaseService, vararg packages: String) =
        scanAndRegister(plugin, database, packages.toList())

    override fun register(plugin: JavaPlugin, vararg packages: String) =
        scanAndRegister(plugin, null, packages.toList())

    private fun scanAndRegister(
        plugin: JavaPlugin,
        database: DatabaseService?,
        packages: List<String>,
    ): RegistrationSummary {
        val entries = mutableListOf<RegisteredEntry>()
        val skipped = mutableListOf<String>()
        val commands = mutableListOf<Pair<AutoCommand, BasicCommand>>()

        for (type in scan(plugin, packages)) {
            val name = type.name
            try {
                type.getAnnotation(Table::class.java)?.let { table ->
                    if (database == null) {
                        skipped += "$name: @Table ohne DatabaseService - register(plugin, database, ...) nutzen"
                        return@let
                    }
                    database.registerModel(type)
                    entries += RegisteredEntry(RegisteredEntry.Kind.MODEL, table.value, "", name)
                }

                type.getAnnotation(AutoListener::class.java)?.let { meta ->
                    val missing = missingPlugins(plugin, meta.requires)
                    if (missing.isNotEmpty()) {
                        skipped += "$name: benoetigt $missing"
                        return@let
                    }
                    val listener = Instantiator.create(type, plugin) as? Listener
                        ?: error("@AutoListener verlangt org.bukkit.event.Listener")
                    plugin.server.pluginManager.registerEvents(listener, plugin)
                    entries += RegisteredEntry(
                        RegisteredEntry.Kind.LISTENER,
                        meta.name.ifEmpty { type.simpleName },
                        meta.description,
                        name,
                    )
                }

                type.getAnnotation(AutoCommand::class.java)?.let { meta ->
                    val missing = missingPlugins(plugin, meta.requires)
                    if (missing.isNotEmpty()) {
                        skipped += "$name: benoetigt $missing"
                        return@let
                    }
                    val command = Instantiator.create(type, plugin) as? BasicCommand
                        ?: error("@AutoCommand verlangt io.papermc.paper.command.brigadier.BasicCommand")
                    commands += meta to PermissionGate.wrap(command, meta.permission)
                    entries += RegisteredEntry(
                        RegisteredEntry.Kind.COMMAND,
                        meta.name,
                        meta.description,
                        name,
                    )
                }
            } catch (ex: Throwable) {
                skipped += "$name: ${ex.message}"
                plugin.logger.warning("$name uebersprungen: ${ex.message}")
            }
        }

        if (commands.isNotEmpty()) registerCommands(plugin, commands)

        val summary = RegistrationSummary(entries, skipped)
        plugin.logger.info("EarthCore AutoRegistrar: $summary")
        skipped.forEach { plugin.logger.warning("EarthCore AutoRegistrar uebersprungen - $it") }
        return summary
    }

    private fun missingPlugins(plugin: JavaPlugin, required: Array<String>): List<String> =
        required.filterNot { plugin.server.pluginManager.isPluginEnabled(it) }

    private fun scan(plugin: JavaPlugin, packages: List<String>): List<Class<*>> {
        val roots = packages.ifEmpty { listOf(plugin.javaClass.packageName) }
        val location = plugin.javaClass.protectionDomain?.codeSource?.location ?: return emptyList()
        val root = runCatching { File(location.toURI()) }.getOrNull() ?: return emptyList()
        return JarScanner.scan(root, plugin.javaClass.classLoader, roots) { name, ex ->
            plugin.logger.warning("Klasse '$name' konnte nicht geladen werden: ${ex.message}")
        }
    }

    private fun registerCommands(plugin: JavaPlugin, commands: List<Pair<AutoCommand, BasicCommand>>) {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar: Commands = event.registrar()
            for ((meta, command) in commands) {
                registrar.register(
                    meta.name,
                    meta.description.ifEmpty { null },
                    meta.aliases.toList(),
                    command,
                )
            }
        }
    }
}

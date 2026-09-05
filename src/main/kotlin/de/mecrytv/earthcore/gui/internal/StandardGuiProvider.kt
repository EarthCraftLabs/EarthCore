package de.mecrytv.earthcore.gui.internal

import de.mecrytv.earthcore.gui.api.Gui
import de.mecrytv.earthcore.gui.api.GuiProvider
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

internal class StandardGuiProvider(
    private val plugin: Plugin,
    override val logger: Logger,
    private val async: (Runnable) -> Unit,
) : GuiProvider, GuiRuntime {

    private val offen = ConcurrentHashMap<UUID, GuiSession>()
    private val geteilt = ConcurrentHashMap<Gui, GuiSession>()

    // ---------------------------------------------------------------- Oeffnen

    override fun open(viewer: Player, gui: Gui) = open(viewer, gui, current(viewer))

    override fun replace(viewer: Player, gui: Gui) = open(viewer, gui, null)

    override fun open(viewer: Player, gui: Gui, from: Gui?) {
        val vorher = offen[viewer.uniqueId]
        val verlauf = vorher?.history()?.toList().orEmpty()

        val session = sessionFor(gui)
        if (from != null) session.history().addLast(from)
        else verlauf.forEach { session.history().addLast(it) }

        detach(viewer, notifyClose = false)
        session.add(viewer)
        offen[viewer.uniqueId] = session

        session.render()
        viewer.openInventory(session.inventoryRef)
        runCatching { gui.onOpen(viewer) }
            .onFailure { logger.warning("onOpen von ${gui.javaClass.simpleName}: ${it.message}") }
    }

    override fun close(viewer: Player) {
        detach(viewer, notifyClose = true)
        viewer.closeInventory()
    }

    override fun current(viewer: Player): Gui? = offen[viewer.uniqueId]?.gui

    override fun back(viewer: Player): Boolean = offen[viewer.uniqueId]?.back() ?: false

    override fun openCount(): Int = offen.size

    internal fun tick() {
        offen.values.distinct().forEach { session ->
            val gewuenscht = runCatching { session.gui.onTick() }
                .onFailure { logger.warning("onTick von ${session.gui.javaClass.simpleName}: ${it.message}") }
                .getOrDefault(false)
            if (gewuenscht) session.render()
        }
    }

    // ---------------------------------------------------------------- Ereignisse

    internal fun sessionOf(viewer: Player): GuiSession? = offen[viewer.uniqueId]

    internal fun forget(viewer: Player) = detach(viewer, notifyClose = true)

    private fun detach(viewer: Player, notifyClose: Boolean) {
        val session = offen.remove(viewer.uniqueId) ?: return
        session.remove(viewer)
        if (notifyClose) {
            runCatching { session.gui.onClose(viewer) }
                .onFailure { logger.warning("onClose von ${session.gui.javaClass.simpleName}: ${it.message}") }
        }
        if (session.empty && !session.gui.shared) session.gui.handle = null
        if (session.empty) geteilt.remove(session.gui)
    }

    private fun sessionFor(gui: Gui): GuiSession {
        if (gui.shared) {
            return geteilt.computeIfAbsent(gui) { neueSession(it) }
        }
        return neueSession(gui)
    }

    private fun neueSession(gui: Gui): GuiSession {
        val session = GuiSession(gui, inventory(gui), this)
        gui.handle = session
        return session
    }

    private fun inventory(gui: Gui): Inventory {
        val typ = gui.type.inventoryType
        return if (typ == null) {
            Bukkit.createInventory(null, gui.type.size, gui.title)
        } else {
            Bukkit.createInventory(null, typ, gui.title)
        }
    }

    // ---------------------------------------------------------------- Runtime

    override fun runAsync(task: Runnable) = async(task)

    override fun runSync(task: () -> Unit) {
        if (Bukkit.isPrimaryThread()) task() else Bukkit.getScheduler().runTask(plugin, Runnable { task() })
    }

    override fun retitle(session: GuiSession, title: Component) = runSync {
        session.viewers().forEach { viewer ->
            runCatching { viewer.openInventory.setTitle(LegacyComponentSerializer.legacySection().serialize(title)) }
                .onFailure { logger.fine("Titel konnte nicht geaendert werden: ${it.message}") }
        }
    }
}

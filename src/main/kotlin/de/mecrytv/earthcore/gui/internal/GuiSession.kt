package de.mecrytv.earthcore.gui.internal

import de.mecrytv.earthcore.gui.api.Gui
import de.mecrytv.earthcore.gui.api.GuiClick
import de.mecrytv.earthcore.gui.api.GuiHandle
import de.mecrytv.earthcore.gui.api.GuiItem
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import java.util.logging.Level
import java.util.logging.Logger

internal class GuiSession(
    val gui: Gui,
    private val inventory: Inventory,
    private val umgebung: GuiRuntime,
) : GuiHandle, Navigation {

    private val betrachter = mutableListOf<Player>()
    private val verlauf = ArrayDeque<Gui>()

    private var buffer = GuiBuffer(gui.type.size)
    private var gezeichnet: GuiBuffer? = null
    private var seite = 0

    private val loads = LoadCache(
        runAsync = umgebung::runAsync,
        onDone = { umgebung.runSync { refresh() } },
        onError = { schluessel, fehler ->
            umgebung.logger.log(Level.WARNING, "Nachladen von '$schluessel' in ${gui.javaClass.simpleName} fehlgeschlagen.", fehler)
        },
    )

    // ---------------------------------------------------------------- Zeichnen

    fun render() {
        buffer.clear()
        val context = RenderContext(
            size = gui.type.size,
            width = gui.type.width,
            viewer = betrachter.firstOrNull().takeUnless { gui.shared },
            buffer = buffer,
            pageIndex = seite,
            loads = loads,
            navigation = this,
        )

        try {
            gui.render(context)
        } catch (ex: Throwable) {
            umgebung.logger.log(Level.SEVERE, "${gui.javaClass.simpleName} konnte nicht gezeichnet werden.", ex)
            return
        }

        buffer.diff(gezeichnet).forEach { slot -> inventory.setItem(slot, buffer[slot]?.stack) }
        gezeichnet = buffer.snapshot()
    }

    fun handle(event: InventoryClickEvent, viewer: Player) {
        val item = buffer[event.rawSlot] ?: return
        val aktion = item.onClick ?: return
        try {
            aktion.accept(GuiClick(viewer, gui, event.rawSlot, event.click, event.cursor, event))
        } catch (ex: Throwable) {
            umgebung.logger.log(Level.SEVERE, "Klick in ${gui.javaClass.simpleName} fehlgeschlagen.", ex)
        }
    }

    fun itemAt(slot: Int): GuiItem? = buffer[slot]

    // ---------------------------------------------------------------- Betrachter

    fun add(viewer: Player) {
        if (betrachter.none { it.uniqueId == viewer.uniqueId }) betrachter += viewer
    }

    fun remove(viewer: Player) {
        betrachter.removeAll { it.uniqueId == viewer.uniqueId }
    }

    val empty: Boolean get() = betrachter.isEmpty()

    val inventoryRef: Inventory get() = inventory

    fun history(): ArrayDeque<Gui> = verlauf

    // ---------------------------------------------------------------- Handle

    override fun refresh() = umgebung.runSync { render() }

    override fun retitle(title: Component) = umgebung.retitle(this, title)

    override fun close(viewer: Player) = umgebung.close(viewer)

    override fun closeAll() = betrachter.toList().forEach { umgebung.close(it) }

    override fun viewers(): List<Player> = betrachter.toList()

    // ---------------------------------------------------------------- Navigation

    override fun page(index: Int) {
        if (index == seite) return
        seite = index
        refresh()
    }

    override fun open(gui: Gui) {
        val viewer = betrachter.firstOrNull() ?: return
        umgebung.open(viewer, gui, this.gui)
    }

    override fun back(): Boolean {
        val viewer = betrachter.firstOrNull() ?: return false
        val vorheriges = verlauf.removeLastOrNull() ?: return false
        umgebung.open(viewer, vorheriges, null)
        return true
    }

    override val canGoBack: Boolean get() = verlauf.isNotEmpty()
}

internal interface GuiRuntime {

    val logger: Logger

    fun runAsync(task: Runnable)

    fun runSync(task: () -> Unit)

    fun open(viewer: Player, gui: Gui, from: Gui?)

    fun close(viewer: Player)

    fun retitle(session: GuiSession, title: Component)
}

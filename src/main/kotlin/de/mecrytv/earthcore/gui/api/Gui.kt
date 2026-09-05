package de.mecrytv.earthcore.gui.api

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

abstract class Gui @JvmOverloads constructor(
    val type: GuiType,
    title: Component,
    val shared: Boolean = false,
) {

    var title: Component = title
        set(value) {
            field = value
            handle?.retitle(value)
        }

    var interactablePlayerInventory: Boolean = false

    internal var handle: GuiHandle? = null

    val viewers: List<Player> get() = handle?.viewers().orEmpty()

    abstract fun render(view: GuiView)

    open fun onOpen(viewer: Player) = Unit

    open fun onClose(viewer: Player) = Unit

    open fun onTick(): Boolean = false

    fun refresh() {
        handle?.refresh()
    }

    fun close(viewer: Player) {
        handle?.close(viewer)
    }

    fun closeAll() {
        handle?.closeAll()
    }
}

internal interface GuiHandle {

    fun refresh()

    fun retitle(title: Component)

    fun close(viewer: Player)

    fun closeAll()

    fun viewers(): List<Player>
}

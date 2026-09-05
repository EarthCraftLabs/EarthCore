package de.mecrytv.earthcore.gui.api

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class GuiClick(
    val viewer: Player,
    val gui: Gui,
    val slot: Int,
    val type: ClickType,
    val cursor: ItemStack?,
    val event: InventoryClickEvent,
) {

    val leftClick: Boolean get() = type.isLeftClick

    val rightClick: Boolean get() = type.isRightClick

    val shiftClick: Boolean get() = type.isShiftClick

    fun refresh() = gui.refresh()

    fun close() = gui.close(viewer)
}

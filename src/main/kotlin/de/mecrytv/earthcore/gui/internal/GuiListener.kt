package de.mecrytv.earthcore.gui.internal

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerQuitEvent

internal class GuiListener(private val provider: StandardGuiProvider) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val viewer = event.whoClicked as? Player ?: return
        val session = provider.sessionOf(viewer) ?: return
        if (event.inventory != session.inventoryRef) return

        val eigenes = event.rawSlot in 0 until session.gui.type.size
        if (eigenes || !session.gui.interactablePlayerInventory) event.isCancelled = true
        if (!eigenes) return

        session.handle(event, viewer)
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val viewer = event.whoClicked as? Player ?: return
        val session = provider.sessionOf(viewer) ?: return
        if (event.inventory != session.inventoryRef) return

        val trifftMenue = event.rawSlots.any { it < session.gui.type.size }
        if (trifftMenue || !session.gui.interactablePlayerInventory) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val viewer = event.player as? Player ?: return
        val session = provider.sessionOf(viewer) ?: return
        if (event.inventory != session.inventoryRef) return
        provider.forget(viewer)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        provider.forget(event.player)
    }
}

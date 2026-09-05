package de.mecrytv.earthcore.gui.api

import org.bukkit.event.inventory.InventoryType

class GuiType private constructor(
    val size: Int,
    val width: Int,
    val inventoryType: InventoryType?,
) {

    override fun toString(): String = inventoryType?.name ?: "CHEST_${size / 9}"

    companion object {

        @JvmStatic
        fun chest(rows: Int): GuiType {
            require(rows in 1..6) { "Eine Truhe hat 1 bis 6 Reihen, angefragt waren $rows" }
            return GuiType(rows * 9, 9, null)
        }

        @JvmField val HOPPER = GuiType(5, 5, InventoryType.HOPPER)

        @JvmField val DISPENSER = GuiType(9, 3, InventoryType.DISPENSER)

        @JvmField val DROPPER = GuiType(9, 3, InventoryType.DROPPER)

        @JvmField val ANVIL = GuiType(3, 3, InventoryType.ANVIL)

        @JvmField val BREWING = GuiType(5, 5, InventoryType.BREWING)

        @JvmField val FURNACE = GuiType(3, 3, InventoryType.FURNACE)
    }
}

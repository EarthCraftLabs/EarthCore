package de.mecrytv.earthcore.gui.api

import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

class GuiItem(
    val stack: ItemStack,
    val onClick: Consumer<GuiClick>? = null,
) {

    fun onClick(action: Consumer<GuiClick>): GuiItem = GuiItem(stack, action)

    override fun equals(other: Any?): Boolean =
        this === other || (other is GuiItem && stack == other.stack)

    override fun hashCode(): Int = stack.hashCode()

    companion object {

        @JvmStatic
        fun of(stack: ItemStack): GuiItem = GuiItem(stack)

        @JvmStatic
        fun of(stack: ItemStack, action: Consumer<GuiClick>): GuiItem = GuiItem(stack, action)
    }
}

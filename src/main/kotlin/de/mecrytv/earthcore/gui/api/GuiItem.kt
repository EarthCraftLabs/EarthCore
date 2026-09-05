package de.mecrytv.earthcore.gui.api

import de.mecrytv.earthcore.item.api.ItemBuilder
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

class GuiItem @JvmOverloads constructor(
    val stack: ItemStack,
    val onClick: Consumer<GuiClick>? = null,
) {

    @JvmOverloads
    constructor(builder: ItemBuilder, onClick: Consumer<GuiClick>? = null) : this(builder.build(), onClick)

    fun onClick(action: Consumer<GuiClick>): GuiItem = GuiItem(stack, action)

    override fun equals(other: Any?): Boolean =
        this === other || (other is GuiItem && stack == other.stack)

    override fun hashCode(): Int = stack.hashCode()

    companion object {

        @JvmStatic
        fun of(stack: ItemStack): GuiItem = GuiItem(stack)

        @JvmStatic
        fun of(stack: ItemStack, action: Consumer<GuiClick>): GuiItem = GuiItem(stack, action)

        @JvmStatic
        fun of(builder: ItemBuilder): GuiItem = GuiItem(builder.build())

        @JvmStatic
        fun of(builder: ItemBuilder, action: Consumer<GuiClick>): GuiItem = GuiItem(builder.build(), action)
    }
}

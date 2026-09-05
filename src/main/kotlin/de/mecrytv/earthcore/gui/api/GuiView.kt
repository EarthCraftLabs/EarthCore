package de.mecrytv.earthcore.gui.api

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

interface GuiView {

    val size: Int

    val width: Int

    val viewer: Player?

    val page: Page

    // ---------------------------------------------------------------- Zeichnen

    fun item(slot: Int, stack: ItemStack): GuiView

    fun item(slot: Int, item: GuiItem): GuiView

    fun button(slot: Int, stack: ItemStack, action: Consumer<GuiClick>): GuiView

    fun fill(stack: ItemStack): GuiView

    fun border(stack: ItemStack): GuiView

    fun clear(slot: Int): GuiView

    // ---------------------------------------------------------------- Maske

    fun mask(vararg rows: String): GuiView

    fun bind(symbol: Char, stack: ItemStack): GuiView

    fun bind(symbol: Char, item: GuiItem): GuiView

    fun bind(symbol: Char, stack: ItemStack, action: Consumer<GuiClick>): GuiView

    fun slots(symbol: Char): List<Int>

    // ---------------------------------------------------------------- Seiten

    fun <T> paginate(symbol: Char, entries: List<T>, render: Function<T, GuiItem>): GuiView

    fun <T> paginate(slots: List<Int>, entries: List<T>, render: Function<T, GuiItem>): GuiView

    fun nextPage()

    fun previousPage()

    // ---------------------------------------------------------------- Nachladen

    fun <T> load(key: String, loader: Supplier<T>): T?

    // ---------------------------------------------------------------- Navigation

    fun open(gui: Gui)

    fun back(): Boolean

    val canGoBack: Boolean
}

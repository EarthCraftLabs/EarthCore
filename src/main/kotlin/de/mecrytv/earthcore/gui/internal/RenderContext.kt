package de.mecrytv.earthcore.gui.internal

import de.mecrytv.earthcore.gui.api.Gui
import de.mecrytv.earthcore.gui.api.GuiClick
import de.mecrytv.earthcore.gui.api.GuiItem
import de.mecrytv.earthcore.gui.api.GuiMask
import de.mecrytv.earthcore.gui.api.GuiView
import de.mecrytv.earthcore.gui.api.Page
import de.mecrytv.earthcore.item.api.ItemBuilder
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

internal class RenderContext(
    override val size: Int,
    override val width: Int,
    override val viewer: Player?,
    private val buffer: GuiBuffer,
    private val pageIndex: Int,
    private val loads: LoadCache,
    private val navigation: Navigation,
) : GuiView {

    private var mask: GuiMask? = null

    private var pageState: Page = Page.of(pageIndex, 1, 0)

    override val page: Page get() = pageState

    // ---------------------------------------------------------------- Zeichnen

    override fun item(slot: Int, stack: ItemStack): GuiView = item(slot, GuiItem(stack))

    override fun item(slot: Int, builder: ItemBuilder): GuiView = item(slot, GuiItem(builder))

    override fun item(slot: Int, item: GuiItem): GuiView = apply { buffer[slot] = item }

    override fun button(slot: Int, stack: ItemStack, action: Consumer<GuiClick>): GuiView =
        item(slot, GuiItem(stack, action))

    override fun button(slot: Int, builder: ItemBuilder, action: Consumer<GuiClick>): GuiView =
        item(slot, GuiItem(builder, action))

    override fun fill(stack: ItemStack): GuiView = apply {
        for (slot in 0 until size) if (buffer[slot] == null) buffer[slot] = GuiItem(stack)
    }

    override fun border(stack: ItemStack): GuiView = apply {
        val rows = size / width
        for (slot in 0 until size) {
            val reihe = slot / width
            val spalte = slot % width
            if (reihe == 0 || reihe == rows - 1 || spalte == 0 || spalte == width - 1) {
                buffer[slot] = GuiItem(stack)
            }
        }
    }

    override fun fill(builder: ItemBuilder): GuiView = fill(builder.build())

    override fun border(builder: ItemBuilder): GuiView = border(builder.build())

    override fun clear(slot: Int): GuiView = apply { buffer[slot] = null }

    // ---------------------------------------------------------------- Maske

    override fun mask(vararg rows: String): GuiView = apply {
        val neue = GuiMask(rows.toList())
        require(neue.size <= size) {
            "Die Maske belegt ${neue.size} Slots, das Menue hat aber nur $size"
        }
        mask = neue
    }

    override fun bind(symbol: Char, stack: ItemStack): GuiView = bind(symbol, GuiItem(stack))

    override fun bind(symbol: Char, item: GuiItem): GuiView =
        apply { slots(symbol).forEach { buffer[it] = item } }

    override fun bind(symbol: Char, stack: ItemStack, action: Consumer<GuiClick>): GuiView =
        bind(symbol, GuiItem(stack, action))

    override fun bind(symbol: Char, builder: ItemBuilder): GuiView = bind(symbol, GuiItem(builder))

    override fun bind(symbol: Char, builder: ItemBuilder, action: Consumer<GuiClick>): GuiView =
        bind(symbol, GuiItem(builder, action))

    override fun slots(symbol: Char): List<Int> {
        val aktuell = mask ?: error("Erst mask(...) aufrufen, dann bind/slots benutzen")
        return aktuell.slots(symbol)
    }

    // ---------------------------------------------------------------- Seiten

    override fun <T> paginate(symbol: Char, entries: List<T>, render: Function<T, GuiItem>): GuiView =
        paginate(slots(symbol), entries, render)

    override fun <T> paginate(slots: List<Int>, entries: List<T>, render: Function<T, GuiItem>): GuiView =
        apply {
            require(slots.isNotEmpty()) { "Fuer Seiten braucht es mindestens einen Slot" }
            pageState = Page.of(pageIndex, slots.size, entries.size)
            pageState.slice(entries).forEachIndexed { index, eintrag ->
                buffer[slots[index]] = render.apply(eintrag)
            }
        }

    override fun nextPage() = navigation.page(pageState.next().index)

    override fun previousPage() = navigation.page(pageState.previous().index)

    // ---------------------------------------------------------------- Nachladen

    override fun <T> load(key: String, loader: Supplier<T>): T? = loads.get(key, loader)

    // ---------------------------------------------------------------- Navigation

    override fun open(gui: Gui) = navigation.open(gui)

    override fun back(): Boolean = navigation.back()

    override val canGoBack: Boolean get() = navigation.canGoBack
}

internal interface Navigation {

    fun page(index: Int)

    fun open(gui: Gui)

    fun back(): Boolean

    val canGoBack: Boolean
}

package de.mecrytv.earthcore.gui.api

import de.mecrytv.earthcore.item.api.ItemBuilder
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.ItemMeta
import java.util.function.Consumer

open class AnvilPrompt(
    title: Component,
    private val start: String,
    private val onConfirm: Consumer<String>,
) : Gui(GuiType.ANVIL, title) {

    private var eingabe: String = start

    override fun render(view: GuiView) {
        view.item(0, ItemBuilder.of(Material.PAPER).name("<white>$start").build())
        view.button(2, ItemBuilder.of(Material.LIME_DYE).name("<green>Bestaetigen").build()) { klick ->
            val text = current(klick.viewer)
            if (text.isBlank()) return@button
            klick.close()
            onConfirm.accept(text)
        }
    }

    fun current(viewer: Player): String {
        val view = viewer.openInventory
        val ergebnis = view.getItem(2) ?: view.getItem(0)
        val meta: ItemMeta? = ergebnis?.itemMeta
        return meta?.displayName()?.let { plain(it) } ?: eingabe
    }

    private fun plain(component: Component): String =
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
}

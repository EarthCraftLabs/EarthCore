package de.mecrytv.earthcore.gui.api

import de.mecrytv.earthcore.item.api.ItemBuilder
import org.bukkit.Material

object Buttons {

    @JvmStatic
    @JvmOverloads
    fun filler(material: Material = Material.GRAY_STAINED_GLASS_PANE): GuiItem =
        GuiItem(ItemBuilder.of(material).name("<reset>").build())

    @JvmStatic
    fun previousPage(view: GuiView): GuiItem = GuiItem(
        ItemBuilder.of(if (view.page.first) Material.GRAY_DYE else Material.ARROW)
            .name(if (view.page.first) "<dark_gray>Erste Seite" else "<yellow>Zurueck")
            .lore("<gray>${view.page}")
            .build(),
    ) { if (!view.page.first) view.previousPage() }

    @JvmStatic
    fun nextPage(view: GuiView): GuiItem = GuiItem(
        ItemBuilder.of(if (view.page.last) Material.GRAY_DYE else Material.ARROW)
            .name(if (view.page.last) "<dark_gray>Letzte Seite" else "<yellow>Weiter")
            .lore("<gray>${view.page}")
            .build(),
    ) { if (!view.page.last) view.nextPage() }

    @JvmStatic
    fun back(view: GuiView): GuiItem = GuiItem(
        ItemBuilder.of(Material.BARRIER).name("<red>Zurueck").build(),
    ) { view.back() }

    @JvmStatic
    fun loading(): GuiItem = GuiItem(
        ItemBuilder.of(Material.CLOCK).name("<gray>Laedt...").build(),
    )
}

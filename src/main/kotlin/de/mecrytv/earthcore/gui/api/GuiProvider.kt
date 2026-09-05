package de.mecrytv.earthcore.gui.api

import org.bukkit.entity.Player

interface GuiProvider {

    fun open(viewer: Player, gui: Gui)

    fun replace(viewer: Player, gui: Gui)

    fun close(viewer: Player)

    fun current(viewer: Player): Gui?

    fun back(viewer: Player): Boolean

    fun openCount(): Int
}

package de.mecrytv.earthcore.gui.internal

import de.mecrytv.earthcore.gui.api.GuiItem

internal class GuiBuffer(val size: Int) {

    private val slots = arrayOfNulls<GuiItem>(size)

    operator fun get(slot: Int): GuiItem? = if (slot in 0 until size) slots[slot] else null

    operator fun set(slot: Int, item: GuiItem?) {
        if (slot in 0 until size) slots[slot] = item
    }

    fun clear() = slots.fill(null)

    fun diff(previous: GuiBuffer?): List<Int> {
        if (previous == null || previous.size != size) return (0 until size).toList()
        return (0 until size).filter { slots[it]?.stack != previous.slots[it]?.stack }
    }

    fun snapshot(): GuiBuffer = GuiBuffer(size).also { kopie ->
        slots.forEachIndexed { index, item -> kopie.slots[index] = item }
    }

    fun filled(): Int = slots.count { it != null }
}

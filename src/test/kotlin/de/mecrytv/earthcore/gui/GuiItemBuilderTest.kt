package de.mecrytv.earthcore.gui

import de.mecrytv.earthcore.gui.api.GuiItem
import de.mecrytv.earthcore.item.api.ItemBuilder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.mockbukkit.mockbukkit.MockBukkit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GuiItemBuilderTest {

    @BeforeTest
    fun start() {
        MockBukkit.mock()
    }

    @AfterTest
    fun stop() {
        MockBukkit.unmock()
    }

    private val builder = ItemBuilder.of(Material.DIAMOND).name("<gold>Diamant")

    @Test
    fun `ein builder ergibt dasselbe wie sein gebautes item`() {
        assertEquals(GuiItem(builder.build()), GuiItem(builder))
        assertEquals(GuiItem(builder.build()), GuiItem.of(builder))
    }

    @Test
    fun `der name kommt beim builder-weg genauso an`() {
        val item = GuiItem(builder)
        val name = item.stack.itemMeta.displayName()

        assertEquals("Diamant", PlainTextComponentSerializer.plainText().serialize(name!!))
    }

    @Test
    fun `ohne handler bleibt onClick leer`() {
        assertNull(GuiItem(builder).onClick)
    }

    @Test
    fun `mit handler wird er uebernommen`() {
        assertNotNull(GuiItem(builder) { }.onClick)
        assertNotNull(GuiItem.of(builder) { }.onClick)
    }

    @Test
    fun `jeder zugriff baut einen eigenen stack`() {
        val erster = GuiItem(builder).stack
        val zweiter = GuiItem(builder).stack

        erster.amount = 32

        assertEquals(1, zweiter.amount, "Der Builder darf keinen Stack zwischen Items teilen")
    }
}

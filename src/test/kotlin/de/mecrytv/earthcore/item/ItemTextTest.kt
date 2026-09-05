package de.mecrytv.earthcore.item

import de.mecrytv.earthcore.item.internal.ItemText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ItemTextTest {

    private fun textur(url: String): String = Base64.getEncoder()
        .encodeToString("""{"textures":{"SKIN":{"url":"$url"}}}""".toByteArray())

    @Test
    fun `minimessage wird geparst`() {
        val component = ItemText.parse("<gold>Scharfes Schwert")

        assertEquals("Scharfes Schwert", PlainTextComponentSerializer.plainText().serialize(component))
    }

    @Test
    fun `kursiv wird abgeschaltet, sonst schreibt minecraft alles schraeg`() {
        assertEquals(
            TextDecoration.State.FALSE,
            ItemText.parse("<gold>Schwert").decoration(TextDecoration.ITALIC),
        )
    }

    @Test
    fun `ein ausdruecklich gesetztes kursiv bleibt erhalten`() {
        assertEquals(
            TextDecoration.State.TRUE,
            ItemText.parse("<i>Absichtlich schraeg").decoration(TextDecoration.ITALIC),
        )
    }

    @Test
    fun `eine fertige komponente wird nur ergaenzt, nicht ueberschrieben`() {
        val eigen = Component.text("Fest").decoration(TextDecoration.ITALIC, true)

        assertEquals(TextDecoration.State.TRUE, ItemText.upright(eigen).decoration(TextDecoration.ITALIC))
        assertEquals(
            TextDecoration.State.FALSE,
            ItemText.upright(Component.text("Offen")).decoration(TextDecoration.ITALIC),
        )
    }

    // ---------------------------------------------------------------- Texturen

    @Test
    fun `eine gueltige textur liefert ihre url`() {
        val url = "https://textures.minecraft.net/texture/abc123"

        assertEquals(url, ItemText.verifyTexture(textur(url)))
    }

    @Test
    fun `kein base64 wird abgelehnt`() {
        val fehler = assertFailsWith<IllegalStateException> { ItemText.verifyTexture("das ist kein base64!!") }
        assertTrue(fehler.message!!.contains("Base64"), fehler.message!!)
    }

    @Test
    fun `base64 ohne skin-url wird abgelehnt`() {
        val ohneUrl = Base64.getEncoder().encodeToString("""{"textures":{}}""".toByteArray())

        val fehler = assertFailsWith<IllegalStateException> { ItemText.verifyTexture(ohneUrl) }
        assertTrue(fehler.message!!.contains("textures.SKIN.url"), fehler.message!!)
    }

    @Test
    fun `eine fremde url wird abgelehnt`() {
        val fehler = assertFailsWith<IllegalArgumentException> {
            ItemText.verifyTexture(textur("https://boese.example/texture/abc"))
        }
        assertTrue(fehler.message!!.contains("textures.minecraft.net"), fehler.message!!)
    }
}

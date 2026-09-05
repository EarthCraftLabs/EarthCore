package de.mecrytv.earthcore.item

import de.mecrytv.earthcore.item.api.ItemBuilder
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.persistence.PersistentDataType
import org.mockbukkit.mockbukkit.MockBukkit
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ItemBuilderTest {

    private val schluessel = NamespacedKey("earthshop", "artikel")

    @BeforeTest
    fun start() {
        MockBukkit.mock()
    }

    @AfterTest
    fun stop() {
        MockBukkit.unmock()
    }

    private fun klartext(component: net.kyori.adventure.text.Component?) =
        component?.let { PlainTextComponentSerializer.plainText().serialize(it) }

    // ---------------------------------------------------------------- Grundlagen

    @Test
    fun `material und menge landen im stack`() {
        val stack = ItemBuilder.of(Material.DIAMOND_SWORD).amount(3).build()

        assertEquals(Material.DIAMOND_SWORD, stack.type)
        assertEquals(3, stack.amount)
    }

    @Test
    fun `ohne angabe ist die menge eins`() {
        assertEquals(1, ItemBuilder.of(Material.STONE).build().amount)
    }

    @Test
    fun `eine menge unter eins wird sofort abgelehnt`() {
        assertFailsWith<IllegalArgumentException> { ItemBuilder.of(Material.STONE).amount(0) }
        assertFailsWith<IllegalArgumentException> { ItemBuilder.of(Material.STONE, -5) }
    }

    @Test
    fun `name und lore kommen an und sind nicht kursiv`() {
        val stack = ItemBuilder.of(Material.DIAMOND_SWORD)
            .name("<gold>Scharfes Schwert")
            .lore("<gray>Zeile eins", "<gray>Zeile zwei")
            .build()

        val meta = stack.itemMeta
        assertEquals("Scharfes Schwert", klartext(meta.displayName()))
        assertEquals(TextDecoration.State.FALSE, meta.displayName()!!.decoration(TextDecoration.ITALIC))
        assertContentEquals(listOf("Zeile eins", "Zeile zwei"), meta.lore()!!.map { klartext(it) })
        assertTrue(meta.lore()!!.all { it.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE })
    }

    @Test
    fun `addLore haengt an, statt zu ersetzen`() {
        val stack = ItemBuilder.of(Material.STONE).lore("eins").addLore("zwei").build()

        assertContentEquals(listOf("eins", "zwei"), stack.itemMeta.lore()!!.map { klartext(it) })
    }

    @Test
    fun `verzauberungen, flags und unzerstoerbar werden gesetzt`() {
        val stack = ItemBuilder.of(Material.DIAMOND_SWORD)
            .enchant(Enchantment.SHARPNESS, 5)
            .flags(ItemFlag.HIDE_ENCHANTS)
            .unbreakable(true)
            .build()

        assertEquals(5, stack.itemMeta.getEnchantLevel(Enchantment.SHARPNESS))
        assertTrue(stack.itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS))
        assertTrue(stack.itemMeta.isUnbreakable)
    }

    @Test
    fun `ein level unter eins wird abgelehnt`() {
        assertFailsWith<IllegalArgumentException> {
            ItemBuilder.of(Material.DIAMOND_SWORD).enchant(Enchantment.SHARPNESS, 0)
        }
    }

    // ---------------------------------------------------------------- Eigene Daten

    @Test
    fun `eigene daten ueberleben den bau und sind wieder auslesbar`() {
        val stack = ItemBuilder.of(Material.EMERALD).tag(schluessel, "schwert-01").build()

        assertEquals(
            "schwert-01",
            stack.itemMeta.persistentDataContainer.get(schluessel, PersistentDataType.STRING),
        )
    }

    @Test
    fun `auch zahlen lassen sich ablegen`() {
        val stack = ItemBuilder.of(Material.EMERALD)
            .data(schluessel, PersistentDataType.INTEGER, 250)
            .build()

        assertEquals(250, stack.itemMeta.persistentDataContainer.get(schluessel, PersistentDataType.INTEGER))
    }

    // ---------------------------------------------------------------- Unveraenderlichkeit

    @Test
    fun `jeder aufruf liefert einen neuen builder`() {
        val basis = ItemBuilder.of(Material.STONE)
        val abgeleitet = basis.amount(5)

        assertNotSame(basis, abgeleitet)
        assertEquals(0, basis.stepCount)
        assertEquals(1, abgeleitet.stepCount)
    }

    @Test
    fun `zwei ableitungen aus derselben vorlage beeinflussen sich nicht`() {
        val vorlage = ItemBuilder.of(Material.PAPER).name("<gold>Gutschein")

        val einer = vorlage.amount(1).build()
        val stapel = vorlage.amount(64).lore("<gray>Grosspackung").build()

        assertEquals(1, einer.amount)
        assertEquals(64, stapel.amount)
        assertEquals(null, einer.itemMeta.lore())
        assertEquals(1, stapel.itemMeta.lore()!!.size)
        assertEquals("Gutschein", klartext(einer.itemMeta.displayName()))
    }

    @Test
    fun `zweimal bauen liefert zwei unabhaengige stacks`() {
        val vorlage = ItemBuilder.of(Material.STONE).amount(1)
        val erster = vorlage.build()
        val zweiter = vorlage.build()

        erster.amount = 32

        assertNotSame(erster, zweiter)
        assertEquals(1, zweiter.amount)
    }

    @Test
    fun `from arbeitet auf einer kopie, nicht auf dem original`() {
        val original = ItemBuilder.of(Material.STONE).amount(1).build()
        val abgeleitet = ItemBuilder.from(original).amount(16).build()

        assertEquals(1, original.amount)
        assertEquals(16, abgeleitet.amount)
    }

    // ---------------------------------------------------------------- Falsches Material

    @Test
    fun `ein aufruf, der nicht zum material passt, meldet sich beim bauen`() {
        val fehler = assertFailsWith<IllegalStateException> {
            ItemBuilder.of(Material.STONE).armorColor(Color.RED).build()
        }

        assertTrue(fehler.message!!.contains("STONE"), fehler.message!!)
        assertTrue(fehler.message!!.contains("LeatherArmorMeta"), fehler.message!!)
    }

    @Test
    fun `beim passenden material geht derselbe aufruf durch`() {
        val stack = ItemBuilder.of(Material.LEATHER_CHESTPLATE).armorColor(Color.RED).build()

        assertEquals(Color.RED, (stack.itemMeta as LeatherArmorMeta).color)
    }

    // ---------------------------------------------------------------- Texturen

    @Test
    fun `eine kaputte textur faellt beim bauen des builders auf, nicht erst im spiel`() {
        assertFailsWith<IllegalStateException> {
            ItemBuilder.of(Material.PLAYER_HEAD).skullTexture("kein base64")
        }
    }

    @Test
    fun `eine gueltige textur wird angenommen`() {
        val textur = Base64.getEncoder().encodeToString(
            """{"textures":{"SKIN":{"url":"https://textures.minecraft.net/texture/abc"}}}""".toByteArray(),
        )

        assertEquals(1, ItemBuilder.of(Material.PLAYER_HEAD).skullTexture(textur).stepCount)
    }

    // ---------------------------------------------------------------- Ausweichweg

    @Test
    fun `edit reicht den stack unveraendert durch`() {
        val stack = ItemBuilder.of(Material.STONE).edit { it.amount = 7 }.build()

        assertEquals(7, stack.amount)
    }

    @Test
    fun `die schritte laufen in der reihenfolge, in der sie notiert wurden`() {
        val stack = ItemBuilder.of(Material.STONE).amount(2).edit { it.amount = 9 }.build()

        assertEquals(9, stack.amount)
    }
}

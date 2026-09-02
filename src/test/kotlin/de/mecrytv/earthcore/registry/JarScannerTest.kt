package de.mecrytv.earthcore.registry

import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import de.mecrytv.earthcore.registry.annotations.AutoCommand
import de.mecrytv.earthcore.registry.annotations.AutoListener
import de.mecrytv.earthcore.registry.internal.Instantiator
import de.mecrytv.earthcore.registry.internal.JarScanner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Table("scanned_models")
data class ScannedModel(@PrimaryKey val id: Int)

@AutoListener
class ScannedListener

@AutoCommand(name = "scanned", description = "Test", aliases = ["s", "sc"])
object ScannedCommand

class NeedsOwner(val owner: Any)

class NeedsNothing

class NeedsSomethingElse(val missing: Int)

class JarScannerTest {

    private val entries = sequenceOf(
        "de/mecrytv/earthshop/EarthShop.class",
        "de/mecrytv/earthshop/model/ShopProfile.class",
        "de/mecrytv/earthshop/command/ShopCommand.class",
        "de/mecrytv/earthshop/listener/JoinListener\$onJoin\$1.class",
        "de/mecrytv/earthother/Foreign.class",
        "de/mecrytv/earthshopextra/NotOurs.class",
        "de/mecrytv/earthshop/package-info.class",
        "module-info.class",
        "plugin.yml",
    )

    @Test
    fun `findet nur klassen unterhalb der angegebenen pakete`() {
        assertEquals(
            listOf(
                "de.mecrytv.earthshop.EarthShop",
                "de.mecrytv.earthshop.command.ShopCommand",
                "de.mecrytv.earthshop.model.ShopProfile",
            ),
            JarScanner.classNames(entries, listOf("de.mecrytv.earthshop")),
        )
    }

    @Test
    fun `ein aehnlich benanntes nachbarpaket wird nicht mitgenommen`() {
        assertEquals(emptyList(), JarScanner.classNames(entries, listOf("de.mecrytv.earthshopextr")))
    }

    @Test
    fun `ohne paket wird nichts gescannt`() {
        assertEquals(emptyList(), JarScanner.classNames(entries, listOf("", "  ")))
    }

    @Test
    fun `scan liest annotationen aus echten klassen auf der platte`() {
        val root = File(javaClass.protectionDomain.codeSource.location.toURI())
        val found = JarScanner.scan(root, javaClass.classLoader, listOf("de.mecrytv.earthcore.registry"))

        assertTrue(found.any { it.getAnnotation(Table::class.java)?.value == "scanned_models" })
        assertTrue(found.any { it.getAnnotation(AutoListener::class.java) != null })

        val command = found.single { it.getAnnotation(AutoCommand::class.java) != null }.getAnnotation(AutoCommand::class.java)
        assertEquals("scanned", command.name)
        assertEquals(listOf("s", "sc"), command.aliases.toList())
    }

    @Test
    fun `instantiator nimmt object, plugin-konstruktor und leeren konstruktor`() {
        val owner = "ich-bin-das-plugin"

        assertSame(ScannedCommand, Instantiator.create(ScannedCommand::class.java, owner))
        assertEquals(owner, (Instantiator.create(NeedsOwner::class.java, owner) as NeedsOwner).owner)
        assertTrue(Instantiator.create(NeedsNothing::class.java, owner) is NeedsNothing)

        assertFailsWith<IllegalStateException> { Instantiator.create(NeedsSomethingElse::class.java, owner) }
    }
}

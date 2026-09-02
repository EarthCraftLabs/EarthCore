package de.mecrytv.earthcore.registry

import de.mecrytv.earthcore.registry.internal.PermissionGate
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun sender(vararg granted: String): CommandSender = Proxy.newProxyInstance(
    CommandSender::class.java.classLoader,
    arrayOf(CommandSender::class.java),
) { _, method, args ->
    when (method.name) {
        "hasPermission" -> args?.firstOrNull() in granted
        "toString" -> "FakeSender"
        "hashCode" -> 0
        "equals" -> false
        else -> null
    }
} as CommandSender

private open class RecordingCommand(private val ownPermission: String? = null) : BasicCommand {

    var executed = 0

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        executed++
    }

    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> =
        listOf("vorschlag")

    override fun permission(): String? = ownPermission
}

class PermissionGateTest {

    @Test
    fun `ohne permission in der annotation bleibt das command unveraendert`() {
        val command = RecordingCommand()
        assertSame(command, PermissionGate.wrap(command, ""))
    }

    @Test
    fun `die annotation setzt die permission des commands`() {
        val gated = PermissionGate.wrap(RecordingCommand(), "earthshop.balance")

        assertEquals("earthshop.balance", gated.permission())
        assertTrue(gated.canUse(sender("earthshop.balance")))
        assertFalse(gated.canUse(sender("etwas.anderes")))
        assertFalse(gated.canUse(sender()))
    }

    @Test
    fun `eine eigene permission im command bleibt zusaetzlich wirksam`() {
        val gated = PermissionGate.wrap(RecordingCommand("earthshop.use"), "earthshop.balance")

        assertFalse(gated.canUse(sender("earthshop.balance")))
        assertTrue(gated.canUse(sender("earthshop.balance", "earthshop.use")))
    }

    @Test
    fun `execute und suggest gehen unveraendert durch`() {
        val command = RecordingCommand()
        val gated = PermissionGate.wrap(command, "earthshop.balance")

        gated.execute(Proxy.newProxyInstance(
            CommandSourceStack::class.java.classLoader,
            arrayOf(CommandSourceStack::class.java),
        ) { _, _, _ -> null } as CommandSourceStack, arrayOf("arg"))

        assertEquals(1, command.executed)
        assertEquals(listOf("vorschlag"), gated.suggest(Proxy.newProxyInstance(
            CommandSourceStack::class.java.classLoader,
            arrayOf(CommandSourceStack::class.java),
        ) { _, _, _ -> null } as CommandSourceStack, emptyArray()).toList())
    }

    @Test
    fun `ein command ohne eigene permission meldet null`() {
        assertNull(RecordingCommand().permission())
    }
}

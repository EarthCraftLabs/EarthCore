package de.mecrytv.earthcore.cooldown

import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.config.JsonConfigService
import de.mecrytv.earthcore.cooldown.api.CooldownRegistry
import de.mecrytv.earthcore.registry.annotations.Cooldown
import de.mecrytv.earthcore.registry.internal.CooldownGate
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.time.Duration
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class FakeCooldowns : CooldownRegistry {

    val gestartet = mutableListOf<Triple<UUID, String, Duration>>()
    var rest: Duration = Duration.ZERO

    override fun start(subject: UUID, key: String, duration: Duration) {
        gestartet += Triple(subject, key, duration)
    }

    override fun extend(subject: UUID, key: String, by: Duration) = Unit
    override fun clear(subject: UUID, key: String) = false
    override fun clearAll(subject: UUID) = 0
    override fun isActive(subject: UUID, key: String) = !rest.isZero
    override fun remaining(subject: UUID, key: String) = rest
    override fun keys(subject: UUID) = emptySet<String>()
}

private class RecordingCommand : BasicCommand {

    var aufrufe = 0

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        aufrufe++
    }
}

class CooldownGateTest {

    private val dir = Files.createTempDirectory("earthcore-messages").toFile()
    private val spieler = UUID.randomUUID()
    private val cooldowns = FakeCooldowns()
    private val command = RecordingCommand()

    private val gesendet = mutableListOf<String>()

    private val messages: ConfigService = JsonConfigService(
        file = java.io.File(dir, "messages.json"),
        defaults = ConfigDefaults.values(
            "prefix" to "[%plugin%] ",
            "cooldown.active" to "%prefix%Bitte warte noch %remaining%.",
            "kit.active" to "Kit erst in %remaining%.",
        ),
    )

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun gate(meta: Cooldown?, plugin: String = "EarthShop"): BasicCommand =
        CooldownGate.wrap(command, meta, plugin, "test", cooldowns, messages)

    private fun meta(
        seconds: Long = 30,
        minutes: Long = 0,
        hours: Long = 0,
        key: String = "",
        bypassPermission: String = "",
        messageKey: String = "cooldown.active",
    ) = Cooldown(seconds, minutes, hours, key, bypassPermission, messageKey)

    private fun quelle(sender: CommandSender): CommandSourceStack = Proxy.newProxyInstance(
        CommandSourceStack::class.java.classLoader,
        arrayOf(CommandSourceStack::class.java),
    ) { _, method, _ -> if (method.name == "getSender") sender else null } as CommandSourceStack

    private fun spielerMit(vararg rechte: String): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getUniqueId" -> spieler
            "hasPermission" -> args?.firstOrNull() in rechte
            "sendMessage" -> {
                gesendet += PlainTextComponentSerializer.plainText().serialize(args!![0] as Component)
                null
            }
            "hashCode" -> 0
            "equals" -> false
            "toString" -> "FakePlayer"
            else -> null
        }
    } as Player

    private fun konsole(): CommandSender = Proxy.newProxyInstance(
        CommandSender::class.java.classLoader,
        arrayOf(CommandSender::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "hashCode" -> 0
            "equals" -> false
            "toString" -> "FakeConsole"
            else -> null
        }
    } as CommandSender

    // ---------------------------------------------------------------- Verdrahtung

    @Test
    fun `ohne annotation bleibt das command unveraendert`() {
        assertSame(command, gate(null))
    }

    @Test
    fun `dauer wird aus stunden, minuten und sekunden addiert`() {
        gate(meta(seconds = 30, minutes = 2, hours = 1)).execute(quelle(spielerMit()), emptyArray())

        assertEquals(Duration.ofSeconds(3750), cooldowns.gestartet.single().third)
    }

    @Test
    fun `eine dauer von null wird abgelehnt`() {
        val fehler = runCatching { gate(meta(seconds = 0)) }.exceptionOrNull()
        assertTrue(fehler is IllegalArgumentException, "IllegalArgumentException erwartet, war $fehler")
    }

    // ---------------------------------------------------------------- Verhalten

    @Test
    fun `ohne laufenden cooldown laeuft das command und startet danach den cooldown`() {
        gate(meta()).execute(quelle(spielerMit()), emptyArray())

        assertEquals(1, command.aufrufe)
        assertEquals(Triple(spieler, "test", Duration.ofSeconds(30)), cooldowns.gestartet.single())
        assertTrue(gesendet.isEmpty())
    }

    @Test
    fun `bei laufendem cooldown wird das command nicht ausgefuehrt`() {
        cooldowns.rest = Duration.ofSeconds(90)

        gate(meta()).execute(quelle(spielerMit()), emptyArray())

        assertEquals(0, command.aufrufe)
        assertTrue(cooldowns.gestartet.isEmpty())
        assertEquals(listOf("[EarthShop] Bitte warte noch 1m 30s."), gesendet)
    }

    @Test
    fun `messageKey waehlt einen anderen text aus der messages-datei`() {
        cooldowns.rest = Duration.ofSeconds(45)

        gate(meta(messageKey = "kit.active")).execute(quelle(spielerMit()), emptyArray())

        assertEquals(listOf("Kit erst in 45s."), gesendet)
    }

    @Test
    fun `ein eigener key gewinnt gegen den commandnamen`() {
        gate(meta(key = "global.kit")).execute(quelle(spielerMit()), emptyArray())

        assertEquals("global.kit", cooldowns.gestartet.single().second)
    }

    @Test
    fun `wer die bypass-permission hat, wird nicht gebremst`() {
        cooldowns.rest = Duration.ofSeconds(90)

        gate(meta(bypassPermission = "earthcore.bypass"))
            .execute(quelle(spielerMit("earthcore.bypass")), emptyArray())

        assertEquals(1, command.aufrufe)
        assertTrue(gesendet.isEmpty())
        assertTrue(cooldowns.gestartet.isEmpty())
    }

    @Test
    fun `ohne die bypass-permission greift der cooldown weiterhin`() {
        cooldowns.rest = Duration.ofSeconds(90)

        gate(meta(bypassPermission = "earthcore.bypass"))
            .execute(quelle(spielerMit("etwas.anderes")), emptyArray())

        assertEquals(0, command.aufrufe)
    }

    @Test
    fun `die konsole hat nie einen cooldown`() {
        cooldowns.rest = Duration.ofSeconds(90)

        gate(meta()).execute(quelle(konsole()), emptyArray())

        assertEquals(1, command.aufrufe)
        assertTrue(cooldowns.gestartet.isEmpty())
    }

    @Test
    fun `wirft das command, wird kein cooldown gesetzt`() {
        val kaputt = object : BasicCommand {
            override fun execute(source: CommandSourceStack, args: Array<out String>) = error("kaputt")
        }
        val gated = CooldownGate.wrap(kaputt, meta(), "EarthShop", "test", cooldowns, messages)

        runCatching { gated.execute(quelle(spielerMit()), emptyArray()) }

        assertTrue(cooldowns.gestartet.isEmpty())
    }

    @Test
    fun `der praefix traegt den namen des aufrufenden plugins, nicht EarthCore`() {
        cooldowns.rest = Duration.ofSeconds(5)

        gate(meta(), plugin = "EarthShop").execute(quelle(spielerMit()), emptyArray())
        gate(meta(), plugin = "EarthQuests").execute(quelle(spielerMit()), emptyArray())

        assertEquals(
            listOf("[EarthShop] Bitte warte noch 5s.", "[EarthQuests] Bitte warte noch 5s."),
            gesendet,
        )
    }

    @Test
    fun `permission und suggest werden durchgereicht`() {
        val gated = gate(meta())

        assertNull(gated.permission())
        assertTrue(gated.suggest(quelle(spielerMit()), emptyArray()).isEmpty())
    }
}

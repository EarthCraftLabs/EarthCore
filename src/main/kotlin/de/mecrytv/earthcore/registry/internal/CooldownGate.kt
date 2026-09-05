package de.mecrytv.earthcore.registry.internal

import de.mecrytv.earthcore.config.ConfigService
import de.mecrytv.earthcore.cooldown.api.CooldownRegistry
import de.mecrytv.earthcore.cooldown.internal.DurationFormat
import de.mecrytv.earthcore.registry.annotations.Cooldown
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration

internal class CooldownGate(
    private val delegate: BasicCommand,
    private val cooldowns: CooldownRegistry,
    private val messages: ConfigService,
    private val key: String,
    private val duration: Duration,
    private val bypassPermission: String,
    private val messageKey: String,
) : BasicCommand {

    override fun execute(source: CommandSourceStack, args: Array<out String>) {
        val player = source.sender as? Player
        if (player == null || bypassed(player)) {
            delegate.execute(source, args)
            return
        }

        val remaining = cooldowns.remaining(player.uniqueId, key)
        if (!remaining.isZero) {
            player.sendMessage(message(remaining))
            return
        }

        delegate.execute(source, args)
        cooldowns.start(player.uniqueId, key, duration)
    }

    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> =
        delegate.suggest(source, args)

    override fun canUse(sender: CommandSender): Boolean = delegate.canUse(sender)

    override fun permission(): String? = delegate.permission()

    private fun bypassed(player: Player): Boolean =
        bypassPermission.isNotEmpty() && player.hasPermission(bypassPermission)

    private fun message(remaining: Duration): Component {
        val rest = DurationFormat.humanize(remaining)
        val raw = messages.getString(
            messageKey,
            "remaining" to rest,
            "prefix" to messages.getString("prefix").orEmpty(),
        ) ?: "<red>Bitte warte noch <yellow>$rest</yellow>."
        return MiniMessage.miniMessage().deserialize(raw)
    }

    companion object {

        fun wrap(
            command: BasicCommand,
            meta: Cooldown?,
            fallbackKey: String,
            cooldowns: CooldownRegistry,
            messages: ConfigService,
        ): BasicCommand {
            if (meta == null) return command
            val duration = meta.toDuration()
            require(!duration.isZero) {
                "@Cooldown braucht eine Dauer groesser als 0 (seconds, minutes oder hours)"
            }
            return CooldownGate(
                delegate = command,
                cooldowns = cooldowns,
                messages = messages,
                key = meta.key.ifEmpty { fallbackKey },
                duration = duration,
                bypassPermission = meta.bypassPermission,
                messageKey = meta.messageKey,
            )
        }

        fun Cooldown.toDuration(): Duration = Duration.ofSeconds(seconds)
            .plusMinutes(minutes)
            .plusHours(hours)
    }
}

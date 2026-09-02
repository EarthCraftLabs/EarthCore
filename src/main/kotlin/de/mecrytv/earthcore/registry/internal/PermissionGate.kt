package de.mecrytv.earthcore.registry.internal

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender

internal class PermissionGate(
    private val delegate: BasicCommand,
    private val permission: String,
) : BasicCommand {

    override fun execute(source: CommandSourceStack, args: Array<out String>) = delegate.execute(source, args)

    override fun suggest(source: CommandSourceStack, args: Array<out String>): Collection<String> =
        delegate.suggest(source, args)

    override fun canUse(sender: CommandSender): Boolean =
        sender.hasPermission(permission) && delegate.canUse(sender)

    override fun permission(): String = permission

    companion object {

        fun wrap(command: BasicCommand, permission: String): BasicCommand =
            if (permission.isEmpty()) command else PermissionGate(command, permission)
    }
}

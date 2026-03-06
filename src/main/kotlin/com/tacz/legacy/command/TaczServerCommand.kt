package com.tacz.legacy.command

import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.config.ServerToggleKey
import com.tacz.legacy.common.foundation.TaczDebugState
import com.tacz.legacy.common.item.GunTooltipPart
import com.tacz.legacy.common.resource.DefaultGunPackExporter
import net.minecraft.command.CommandBase
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.util.text.TextComponentTranslation
import java.util.Locale

internal object TaczServerCommand : CommandBase() {
    private val toggleNameMap: Map<String, ServerToggleKey> = mapOf(
        "default_table_limit" to ServerToggleKey.DEFAULT_TABLE_LIMIT,
        "server_shoot_network_check" to ServerToggleKey.SERVER_SHOOT_NETWORK_CHECK,
        "server_shoot_cooldown_check" to ServerToggleKey.SERVER_SHOOT_COOLDOWN_CHECK,
    )

    override fun getName(): String = "tacz"

    override fun getUsage(sender: ICommandSender): String =
        "/tacz reload | overwrite <true|false> | config <default_table_limit|server_shoot_network_check|server_shoot_cooldown_check> <true|false> | debug <true|false> | attachment_lock [player] <true|false> | dummy [player] <amount> | hide_tooltip_part [player] <mask>"

    override fun getRequiredPermissionLevel(): Int = 2

    override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }

        when (args[0].lowercase(Locale.ROOT)) {
            "reload" -> handleReload(sender)
            "overwrite" -> handleOverwrite(sender, args)
            "config" -> handleConfig(sender, args)
            "debug" -> handleDebug(sender, args)
            "attachment_lock" -> handleAttachmentLock(server, sender, args)
            "dummy" -> handleDummyAmmo(server, sender, args)
            "hide_tooltip_part" -> handleHideTooltipPart(server, sender, args)
            else -> sender.sendMessage(TextComponentString(getUsage(sender)))
        }
    }

    override fun getTabCompletions(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        targetPos: BlockPos?
    ): MutableList<String> {
        if (args.isEmpty()) {
            return mutableListOf()
        }
        if (args.size == 1) {
            return getListOfStringsMatchingLastWord(
                args,
                listOf("reload", "overwrite", "config", "debug", "attachment_lock", "dummy", "hide_tooltip_part")
            )
        }
        if (args.size == 2 && (args[0].equals("overwrite", true) || args[0].equals("debug", true))) {
            return getListOfStringsMatchingLastWord(args, listOf("true", "false"))
        }
        if (args.size == 2 && args[0].equals("config", true)) {
            return getListOfStringsMatchingLastWord(args, toggleNameMap.keys)
        }
        if (args.size == 3 && args[0].equals("config", true)) {
            return getListOfStringsMatchingLastWord(args, listOf("true", "false"))
        }
        if (args.size == 2 && (args[0].equals("attachment_lock", true) || args[0].equals("dummy", true) || args[0].equals("hide_tooltip_part", true))) {
            return getListOfStringsMatchingLastWord(args, server.onlinePlayerNames.toList())
        }
        if (args.size == 3 && args[0].equals("attachment_lock", true)) {
            return getListOfStringsMatchingLastWord(args, listOf("true", "false"))
        }
        return mutableListOf()
    }

    private fun handleReload(sender: ICommandSender): Unit {
        val startedAt = System.nanoTime()
        LegacyConfigManager.reloadAll()
        val exportResult = LegacyConfigManager.getGameDirectory()?.let(DefaultGunPackExporter::exportIfNeeded)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
        sender.sendMessage(TextComponentTranslation("commands.tacz.reload.success", String.format(Locale.ROOT, "%.3f", elapsedMs)))
        if (exportResult?.skipped == true) {
            sender.sendMessage(TextComponentTranslation("commands.tacz.reload.overwrite_off"))
        }
    }

    private fun handleOverwrite(sender: ICommandSender, args: Array<String>): Unit {
        if (args.size < 2) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val enabled = parseBoolean(args[1])
        LegacyConfigManager.setOverwriteEnabled(enabled)
        sender.sendMessage(
            TextComponentTranslation(
                if (enabled) "commands.tacz.reload.overwrite_on" else "commands.tacz.reload.overwrite_off"
            )
        )
    }

    private fun handleConfig(sender: ICommandSender, args: Array<String>): Unit {
        if (args.size < 3) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val key = toggleNameMap[args[1].lowercase(Locale.ROOT)]
        if (key == null) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val enabled = parseBoolean(args[2])
        LegacyConfigManager.applyServerToggle(key, enabled)
        sender.sendMessage(TextComponentTranslation("${key.translationKey}.${if (enabled) "enabled" else "disabled"}"))
    }

    private fun handleDebug(sender: ICommandSender, args: Array<String>): Unit {
        if (args.size < 2) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        TaczDebugState.enabled = parseBoolean(args[1])
        sender.sendMessage(TextComponentString("[TACZ] debug ${if (TaczDebugState.enabled) "enabled" else "disabled"}."))
    }

    private fun handleAttachmentLock(server: MinecraftServer, sender: ICommandSender, args: Array<String>): Unit {
        val (player, valueIndex) = resolvePlayer(server, sender, args, defaultValueIndex = 1)
        if (args.size <= valueIndex) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val locked = parseBoolean(args[valueIndex])
        withHeldGun(player, sender) { gunStack, gun ->
            gun.setAttachmentLock(gunStack, locked)
            sender.sendMessage(TextComponentString("[TACZ] set attachment lock=${locked} for ${player.name}."))
        }
    }

    private fun handleDummyAmmo(server: MinecraftServer, sender: ICommandSender, args: Array<String>): Unit {
        val (player, valueIndex) = resolvePlayer(server, sender, args, defaultValueIndex = 1)
        if (args.size <= valueIndex) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val amount = parseInt(args[valueIndex], 0)
        withHeldGun(player, sender) { gunStack, gun ->
            gun.setDummyAmmoAmount(gunStack, amount)
            sender.sendMessage(TextComponentString("[TACZ] set dummy ammo=${amount} for ${player.name}."))
        }
    }

    private fun handleHideTooltipPart(server: MinecraftServer, sender: ICommandSender, args: Array<String>): Unit {
        val (player, valueIndex) = resolvePlayer(server, sender, args, defaultValueIndex = 1)
        if (args.size <= valueIndex) {
            sender.sendMessage(TextComponentString(getUsage(sender)))
            return
        }
        val mask = parseInt(args[valueIndex], 0)
        withHeldGun(player, sender) { gunStack, _ ->
            GunTooltipPart.setHideFlags(gunStack, mask)
            sender.sendMessage(TextComponentString("[TACZ] set tooltip mask=${mask} for ${player.name}."))
        }
    }

    private fun resolvePlayer(
        server: MinecraftServer,
        sender: ICommandSender,
        args: Array<String>,
        defaultValueIndex: Int,
    ): Pair<EntityPlayerMP, Int> {
        if (args.size >= defaultValueIndex + 2) {
            return getPlayer(server, sender, args[1]) to (defaultValueIndex + 1)
        }
        val commandPlayer = sender.commandSenderEntity as? EntityPlayerMP
            ?: throw CommandException("commands.generic.player.unspecified")
        return commandPlayer to defaultValueIndex
    }

    private fun withHeldGun(
        player: EntityPlayerMP,
        sender: ICommandSender,
        operation: (ItemStack, com.tacz.legacy.api.item.IGun) -> Unit,
    ): Unit {
        val stack = player.heldItemMainhand
        val gun = com.tacz.legacy.api.item.IGun.getIGunOrNull(stack)
        if (stack.isEmpty || gun == null) {
            sender.sendMessage(TextComponentString("[TACZ] ${player.name} is not holding a TACZ gun."))
            return
        }
        operation(stack, gun)
    }
}

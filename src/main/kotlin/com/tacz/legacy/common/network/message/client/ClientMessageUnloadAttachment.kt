package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageRefreshRefitScreen
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.items.ItemHandlerHelper

public class ClientMessageUnloadAttachment() : IMessage, IMessageHandler<ClientMessageUnloadAttachment, IMessage?> {
    private var gunSlotIndex: Int = -1
    private var attachmentType: AttachmentType = AttachmentType.NONE

    public constructor(gunSlotIndex: Int, attachmentType: AttachmentType) : this() {
        this.gunSlotIndex = gunSlotIndex
        this.attachmentType = attachmentType
    }

    override fun fromBytes(buf: ByteBuf) {
        gunSlotIndex = buf.readInt()
        attachmentType = AttachmentType.values().getOrElse(buf.readInt()) { AttachmentType.NONE }
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(gunSlotIndex)
        buf.writeInt(attachmentType.ordinal)
    }

    override fun onMessage(message: ClientMessageUnloadAttachment, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        player.serverWorld.addScheduledTask {
            if (message.gunSlotIndex !in 0 until player.inventory.sizeInventory) {
                return@addScheduledTask
            }
            val gunStack = player.inventory.getStackInSlot(message.gunSlotIndex)
            val iGun = IGun.getIGunOrNull(gunStack) ?: return@addScheduledTask
            val currentAttachment = iGun.getAttachment(gunStack, message.attachmentType)
            if (currentAttachment.isEmpty) {
                return@addScheduledTask
            }
            if (!player.capabilities.isCreativeMode && !player.inventory.addItemStackToInventory(currentAttachment.copy())) {
                return@addScheduledTask
            }
            val unloadResult = LegacyGunRefitRuntime.unloadAttachment(gunStack, message.attachmentType) ?: return@addScheduledTask
            if (unloadResult.requiresAmmoRefund) {
                LegacyGunRefitRuntime.refundLoadedAmmo(gunStack, player.capabilities.isCreativeMode).refundStacks.forEach { stack ->
                    ItemHandlerHelper.giveItemToPlayer(player, stack)
                }
            }
            player.inventory.markDirty()
            player.inventoryContainer.detectAndSendChanges()
            TACZNetworkHandler.sendToPlayer(ServerMessageRefreshRefitScreen(), player)
        }
        return null
    }
}

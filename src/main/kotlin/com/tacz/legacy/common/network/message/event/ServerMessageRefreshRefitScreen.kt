package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.client.gui.GunRefitScreen
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class ServerMessageRefreshRefitScreen() : IMessage {
    override fun fromBytes(buf: ByteBuf) {}

    override fun toBytes(buf: ByteBuf) {}

    public class Handler : IMessageHandler<ServerMessageRefreshRefitScreen, IMessage?> {
        override fun onMessage(message: ServerMessageRefreshRefitScreen, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                (mc.currentScreen as? GunRefitScreen)?.refreshFromServer()
            }
            return null
        }
    }
}

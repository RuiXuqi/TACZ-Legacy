package com.tacz.legacy.common

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageSyncBaseTimestamp
import net.minecraft.item.ItemStack
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.function.Supplier

/**
 * 每 tick 驱动所有 EntityLivingBase 上的枪械子系统更新。
 * 注册到 Forge EVENT_BUS。
 */
internal object ShooterTickHandler {

    /**
     * 玩家加入世界时发起 baseTimestamp 同步握手。
     * 与上游 TACZ SyncBaseTimestamp.onPlayerJoinWorld 行为一致。
     */
    @SubscribeEvent
    fun onEntityJoinWorld(event: EntityJoinWorldEvent) {
        val entity = event.entity
        if (entity is EntityPlayerMP && !event.world.isRemote) {
            TACZNetworkHandler.sendToPlayer(ServerMessageSyncBaseTimestamp(), entity)
        }
    }

    @SubscribeEvent
    fun onLivingTick(event: LivingEvent.LivingUpdateEvent) {
        val entity: EntityLivingBase = event.entityLiving ?: return
        // 只在服务端 tick
        if (entity.world.isRemote) return

        val operator = IGunOperator.fromLivingEntity(entity)
        val holder = operator.getDataHolder()

        // 仅在手持枪械时 tick 子系统
        val mainHand = entity.heldItemMainhand
        if (mainHand.isEmpty || mainHand.item !is IGun) {
            // 非持枪：如果之前有持枪状态需要清理
            if (holder.currentGunItem != null) {
                operator.initialData()
                holder.currentGunItem = null
            }
            return
        }

        val syncedGun = holder.currentGunItem?.get()
        if (!isSameGunStack(mainHand, syncedGun)) {
            operator.draw(Supplier { entity.heldItemMainhand })
        }

        operator.tick()
    }

    private fun isSameGunStack(mainHand: ItemStack, syncedGun: ItemStack?): Boolean {
        if (syncedGun == null || syncedGun.isEmpty) {
            return false
        }
        val mainGun = mainHand.item as? IGun ?: return false
        val syncedIGun = syncedGun.item as? IGun ?: return false
        return mainGun.getGunId(mainHand) == syncedIGun.getGunId(syncedGun)
    }
}

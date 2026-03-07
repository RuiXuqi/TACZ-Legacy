package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import kotlin.math.abs

internal object LegacyClientGunAnimationDriver {
    internal fun resolveDisplayInstance(stack: ItemStack): GunDisplayInstance? {
        val iGun = stack.item as? IGun ?: return null
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, iGun.getGunId(stack)) ?: return null
        return TACZClientAssetManager.getGunDisplayInstance(displayId)
    }

    internal fun determineLoopInput(
        isSprinting: Boolean,
        isSneaking: Boolean,
        moveForward: Float,
        moveStrafe: Float,
        movementInputMissing: Boolean = false,
    ): String {
        if (movementInputMissing) {
            return GunAnimationConstant.INPUT_IDLE
        }
        if (!isSneaking && isSprinting) {
            return GunAnimationConstant.INPUT_RUN
        }
        val isMoving = abs(moveForward) > 0.01f || abs(moveStrafe) > 0.01f
        return if (!isSneaking && isMoving) {
            GunAnimationConstant.INPUT_WALK
        } else {
            GunAnimationConstant.INPUT_IDLE
        }
    }

    internal fun prepareContext(
        stateMachine: AnimationStateMachine<GunAnimationStateContext>,
        stack: ItemStack,
        display: GunDisplayInstance?,
        partialTicks: Float,
    ): GunAnimationStateContext {
        var context = stateMachine.context
        if (context == null) {
            context = GunAnimationStateContext()
            stateMachine.setContext(context)
        }
        context.setCurrentGunItem(stack)
        context.setDisplay(display)
        context.setPartialTicks(partialTicks)
        return context
    }

    internal fun trigger(
        stateMachine: AnimationStateMachine<GunAnimationStateContext>,
        input: String,
        stack: ItemStack,
        display: GunDisplayInstance?,
        partialTicks: Float = 0f,
    ): Boolean {
        if (!stateMachine.isInitialized) {
            return false
        }
        prepareContext(stateMachine, stack, display, partialTicks)
        stateMachine.trigger(input)
        return true
    }

    internal fun triggerIfInitialized(stack: ItemStack, input: String, partialTicks: Float = 0f): Boolean {
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        return trigger(stateMachine, input, stack, display, partialTicks)
    }

    internal fun tickLoopAnimation(player: EntityPlayerSP): Boolean {
        val stack = player.heldItemMainhand
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        val movement = player.movementInput
        val input = determineLoopInput(
            isSprinting = player.isSprinting,
            isSneaking = player.isSneaking,
            moveForward = movement?.moveForward ?: 0f,
            moveStrafe = movement?.moveStrafe ?: 0f,
            movementInputMissing = movement == null,
        )
        return trigger(stateMachine, input, stack, display)
    }
}

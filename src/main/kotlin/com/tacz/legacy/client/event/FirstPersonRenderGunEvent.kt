package com.tacz.legacy.client.event

import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.model.BedrockAnimatedModel
import com.tacz.legacy.client.model.bedrock.BedrockPart
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.util.math.MathUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.joml.Quaternionf

/**
 * 第一人称枪械渲染事件处理器。
 * 拦截 RenderSpecificHandEvent，如果主手持有枪械，则取消默认手部渲染并替换为
 * 基岩版模型 + 动画状态机驱动的第一人称渲染。
 *
 * Port of upstream TACZ FirstPersonRenderEvent + AnimateGeoItemRenderer.renderFirstPerson.
 */
@SideOnly(Side.CLIENT)
internal object FirstPersonRenderGunEvent {
    private var lastStateMachine: AnimationStateMachine<*>? = null
    private var lastRenderedModel: BedrockAnimatedModel? = null

    @SubscribeEvent
    @JvmStatic
    internal fun onRenderHand(event: RenderSpecificHandEvent) {
        val player = Minecraft.getMinecraft().player ?: return

        // Only handle main hand
        if (event.hand != EnumHand.MAIN_HAND) {
            // If main hand has gun, cancel off-hand rendering too
            val mainItem = player.heldItemMainhand
            if (mainItem.item is IGun) {
                event.isCanceled = true
            }
            return
        }

        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: return
        val gunId = iGun.getGunId(stack)

        // Resolve display instance
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val displayInstance = TACZClientAssetManager.getGunDisplayInstance(displayId) ?: return

        val model: BedrockAnimatedModel = displayInstance.gunModel ?: return

        // Resolve texture
        val textureLoc: ResourceLocation = displayInstance.modelTexture ?: return
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLoc) ?: return

        val partialTicks = event.partialTicks

        // --- State machine lifecycle ---
        val sm: LuaAnimationStateMachine<GunAnimationStateContext>? = displayInstance.animationStateMachine

        // Exit previous state machine if switching guns
        if (sm != lastStateMachine) {
            val prev = lastStateMachine
            if (prev != null && prev.isInitialized) {
                prev.exit()
            }
            lastStateMachine = sm
        }

        // Initialize state machine if needed
        if (sm != null && !sm.isInitialized && sm.exitingTime < System.currentTimeMillis()) {
            val context = GunAnimationStateContext()
            context.setCurrentGunItem(stack)
            context.setDisplay(displayInstance)
            context.setPartialTicks(partialTicks)
            sm.setContext(context)
            sm.initialize()
            sm.trigger(GunAnimationConstant.INPUT_DRAW)
        }

        // Update context and state machine
        if (sm != null && sm.isInitialized) {
            sm.processContextIfExist { context ->
                context.setCurrentGunItem(stack)
                context.setDisplay(displayInstance)
                context.setPartialTicks(partialTicks)
            }
            sm.update()
        }

        // --- Render ---
        lastRenderedModel = model
        GlStateManager.pushMatrix()

        // Apply root node offsets for view bob compensation
        // Upstream reverses view bob pitch/yaw based on xBob/yBob; in 1.12.2
        // the equivalent is cameraPitch/cameraYaw on the player entity.
        val cameraYaw = player.prevCameraYaw + (player.cameraYaw - player.prevCameraYaw) * partialTicks
        val cameraPitch = player.prevCameraPitch + (player.cameraPitch - player.prevCameraPitch) * partialTicks
        val xRot = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks
        val yRot = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks

        val rootNode: BedrockPart? = model.rootNode
        if (rootNode != null) {
            val clampedXRot = Math.tanh((xRot / 25).toDouble()).toFloat() * 25f
            val clampedYRot = Math.tanh((yRot / 25).toDouble()).toFloat() * 25f
            rootNode.offsetX += clampedYRot * 0.1f / 16f / 3f
            rootNode.offsetY += -clampedXRot * 0.1f / 16f / 3f
            rootNode.additionalQuaternion.mul(
                Quaternionf().rotateX(Math.toRadians((clampedXRot * 0.05f).toDouble()).toFloat())
            )
            rootNode.additionalQuaternion.mul(
                Quaternionf().rotateY(Math.toRadians((clampedYRot * 0.05f).toDouble()).toFloat())
            )
        }

        // Move from render origin (0, 24, 0) to model origin (0, 0, 0)
        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        // Bedrock models are upside-down, flip
        GlStateManager.rotate(180.0f, 0.0f, 0.0f, 1.0f)

        // Bind gun texture and render
        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)
        displayInstance.setActiveGunTexture(registeredTexture)
        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        model.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()

        // Clean animation transforms after render
        model.cleanAnimationTransform()
        model.cleanCameraAnimationTransform()

        GlStateManager.popMatrix()

        // Cancel vanilla rendering
        event.isCanceled = true
    }

    /**
     * Apply animation-driven camera rotation to the world camera.
     * Port of upstream TACZ CameraSetupEvent.applyLevelCameraAnimation.
     */
    @SubscribeEvent
    @JvmStatic
    internal fun onCameraSetup(event: EntityViewRenderEvent.CameraSetup) {
        val player = Minecraft.getMinecraft().player ?: return
        val stack = player.heldItemMainhand
        if (stack.item !is IGun) return
        val model = lastRenderedModel ?: return
        val (yaw, pitch, roll) = applyCameraAnimation(model, event.yaw, event.pitch, event.roll)
        event.yaw = yaw
        event.pitch = pitch
        event.roll = roll
    }

    /**
     * Apply camera animation from the state machine to the world camera.
     * Called from an EntityViewRenderEvent hook (e.g. CameraSetup).
     */
    internal fun applyCameraAnimation(model: BedrockAnimatedModel, yaw: Float, pitch: Float, roll: Float): Triple<Float, Float, Float> {
        val q: Quaternionf = MathUtil.multiplyQuaternion(model.cameraAnimationObject.rotationQuaternion, 1f)
        val yawDelta = Math.toDegrees(Math.asin((2 * (q.w() * q.y() - q.x() * q.z())).toDouble())).toFloat()
        val pitchDelta = Math.toDegrees(
            Math.atan2(
                (2 * (q.w() * q.x() + q.y() * q.z())).toDouble(),
                (1 - 2 * (q.x() * q.x() + q.y() * q.y())).toDouble()
            )
        ).toFloat()
        val rollDelta = Math.toDegrees(
            Math.atan2(
                (2 * (q.w() * q.z() + q.x() * q.y())).toDouble(),
                (1 - 2 * (q.y() * q.y() + q.z() * q.z())).toDouble()
            )
        ).toFloat()
        return Triple(yaw + yawDelta, pitch + pitchDelta, roll + rollDelta)
    }
}

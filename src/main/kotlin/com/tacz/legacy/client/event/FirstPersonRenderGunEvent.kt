package com.tacz.legacy.client.event

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.gameplay.LegacyClientGunAnimationDriver
import com.tacz.legacy.client.model.BedrockAnimatedModel
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.model.bedrock.BedrockPart
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.util.math.MathUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumHandSide
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.MathHelper
import net.minecraftforge.client.event.EntityViewRenderEvent
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer

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
    private val positioningMatrixBuffer: FloatBuffer = BufferUtils.createFloatBuffer(16)
    private var loggedFirstPersonRender = false

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

        val stack = event.itemStack
        val iGun = stack.item as? IGun ?: run {
            if (lastStateMachine?.isInitialized == true) {
                lastStateMachine?.exit()
            }
            lastStateMachine = null
            lastRenderedModel = null
            return
        }
        val gunId = iGun.getGunId(stack)
        val handSide = player.primaryHand

        // Resolve display instance
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val displayInstance = TACZClientAssetManager.getGunDisplayInstance(displayId) ?: return

        val model: BedrockGunModel = displayInstance.gunModel ?: return

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
            LegacyClientGunAnimationDriver.prepareContext(sm, stack, displayInstance, partialTicks)
            sm.initialize()
            sm.trigger(GunAnimationConstant.INPUT_DRAW)
        }

        // Update context and state machine
        if (sm != null && sm.isInitialized) {
            LegacyClientGunAnimationDriver.prepareContext(sm, stack, displayInstance, partialTicks)
            sm.update()
        }

        // --- Render ---
        lastRenderedModel = model
        GlStateManager.pushMatrix()
        applyVanillaFirstPersonTransform(handSide, event.equipProgress, event.swingProgress)

        // Apply root node offsets for view bob compensation
        val xRot = event.interpolatedPitch
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
        // Apply idle/iron/scope positioning so the gun and mounted optics sit in the
        // correct place relative to the camera instead of rendering at the raw model origin.
        val aimingProgress = IGunOperator.fromLivingEntity(player).getSynAimingProgress()
        applyFirstPersonPositioningTransform(model, stack, aimingProgress)

        // Bind gun texture and render
        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)
        displayInstance.setActiveGunTexture(registeredTexture)
        model.renderHand = true
        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        if (!loggedFirstPersonRender) {
            TACZLegacy.logger.info("[FirstPersonRenderGunEvent] First-person render hook active for {}", gunId)
            loggedFirstPersonRender = true
        }

    model.render(stack)
    model.renderHand = false

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()

        // Clean animation transforms after render
        model.cleanAnimationTransform()
        model.cleanCameraAnimationTransform()

        GlStateManager.popMatrix()

        // Cancel vanilla rendering
        event.isCanceled = true
    }

    private fun applyVanillaFirstPersonTransform(handSide: EnumHandSide, equipProgress: Float, swingProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        val swingRoot = MathHelper.sqrt(swingProgress)
        val swayX = -0.4f * MathHelper.sin(swingRoot * Math.PI.toFloat())
        val swayY = 0.2f * MathHelper.sin(swingRoot * ((Math.PI * 2.0).toFloat()))
        val swayZ = -0.2f * MathHelper.sin(swingProgress * Math.PI.toFloat())
        GlStateManager.translate(side * swayX, swayY, swayZ)
        transformSideFirstPerson(handSide, equipProgress)
        transformFirstPerson(handSide, swingProgress)
    }

    private fun transformSideFirstPerson(handSide: EnumHandSide, equipProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        GlStateManager.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f)
    }

    private fun transformFirstPerson(handSide: EnumHandSide, swingProgress: Float) {
        val side = if (handSide == EnumHandSide.RIGHT) 1 else -1
        val swingSin = MathHelper.sin(swingProgress * swingProgress * Math.PI.toFloat())
        GlStateManager.rotate(side * (45.0f + swingSin * -20.0f), 0.0f, 1.0f, 0.0f)
        val swingRootSin = MathHelper.sin(MathHelper.sqrt(swingProgress) * Math.PI.toFloat())
        GlStateManager.rotate(side * swingRootSin * -20.0f, 0.0f, 0.0f, 1.0f)
        GlStateManager.rotate(swingRootSin * -80.0f, 1.0f, 0.0f, 0.0f)
        GlStateManager.rotate(side * -45.0f, 0.0f, 1.0f, 0.0f)
    }

    private fun applyFirstPersonPositioningTransform(model: BedrockGunModel, stack: net.minecraft.item.ItemStack, aimingProgress: Float) {
        val transformMatrix = FirstPersonRenderMatrices.buildAimingPositioningTransform(
            idlePath = FirstPersonRenderMatrices.fromBedrockPath(model.idleSightPath),
            aimingPath = FirstPersonRenderMatrices.fromBedrockPath(model.resolveAimingViewPath(stack)),
            aimingProgress = aimingProgress,
        )
        GlStateManager.translate(0.0f, 1.5f, 0.0f)
        positioningMatrixBuffer.clear()
        transformMatrix.get(positioningMatrixBuffer)
        positioningMatrixBuffer.rewind()
        GL11.glMultMatrix(positioningMatrixBuffer)
        GlStateManager.translate(0.0f, -1.5f, 0.0f)
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

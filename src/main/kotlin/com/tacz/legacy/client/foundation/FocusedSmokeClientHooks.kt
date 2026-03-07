package com.tacz.legacy.client.foundation

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.ObjectAnimationRunner
import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.gameplay.LegacyClientGunAnimationDriver
import com.tacz.legacy.client.gameplay.LegacyClientShootCoordinator
import com.tacz.legacy.client.model.BedrockGunModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.network.message.event.ServerMessageSound
import com.tacz.legacy.common.foundation.FocusedSmokePlanner
import com.tacz.legacy.common.foundation.FocusedSmokeRuntime
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.network.INetHandler
import net.minecraft.network.play.client.CPacketHeldItemChange
import net.minecraft.util.EnumHand
import net.minecraft.world.GameType
import net.minecraft.world.WorldSettings
import net.minecraft.world.WorldType
import net.minecraftforge.client.event.RenderSpecificHandEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import org.lwjgl.opengl.Display
import kotlin.math.max

internal object FocusedSmokeClientHooks {
    private const val WORLD_LOAD_TIMEOUT_MS: Long = 45_000L
    private const val GEAR_TIMEOUT_MS: Long = 20_000L
    private const val BASELINE_DELAY_MS: Long = 2_500L
    private const val ANIMATION_WAIT_MS: Long = 8_000L
    private const val REGULAR_PROJECTILE_WAIT_MS: Long = 8_000L
    private const val EXPLOSION_WAIT_MS: Long = 15_000L
    private const val SHOOT_RETRY_INTERVAL_MS: Long = 750L
    private const val EXPLOSIVE_SHOT_PITCH: Float = 45.0f

    private enum class Step {
        WAIT_MENU,
        WAIT_WORLD,
        WAIT_GEAR,
        WAIT_BASELINE,
        WAIT_FIRST_RENDER,
        ATTEMPT_REGULAR_SHOT,
        WAIT_REGULAR_PROJECTILE,
        SWITCH_EXPLOSIVE,
        ATTEMPT_EXPLOSIVE_SHOT,
        WAIT_EXPLOSION,
        COMPLETE,
        FAILED,
    }

    private var step: Step = Step.WAIT_MENU
    private var stepEnteredAtMs: Long = System.currentTimeMillis()
    private var worldLaunchRequested: Boolean = false
    private var worldReadyAtMs: Long = 0L
    private var animationTimeoutLogged: Boolean = false
    private var lastShootAttemptAtMs: Long = 0L
    private var originalPauseOnLostFocus: Boolean? = null
    private var keepAliveGuiActive: Boolean = false
    private var syntheticServerSoundDispatched: Boolean = false
    private var syntheticServerSoundSkipped: Boolean = false

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END || !FocusedSmokeRuntime.enabled) {
            return
        }
        val mc = Minecraft.getMinecraft()
        ensurePauseOnLostFocusDisabled(mc)
        if (FocusedSmokeRuntime.hasFailed()) {
            step = Step.FAILED
            restorePauseOnLostFocus(mc)
            return
        }
        if (FocusedSmokeRuntime.hasPassed()) {
            step = Step.COMPLETE
            restorePauseOnLostFocus(mc)
            return
        }
        val world = mc.world
        val player = mc.player
        if (world == null || player == null) {
            handleOutOfWorld(mc)
            return
        }

        if (worldReadyAtMs == 0L) {
            worldReadyAtMs = System.currentTimeMillis()
            mc.gameSettings.thirdPersonView = 0
            transition(Step.WAIT_GEAR, "CLIENT_WORLD_READY dimension=${world.provider.dimension} player=${player.name}")
        }

        maintainClientLiveness(mc)

        when (step) {
            Step.WAIT_GEAR -> handleWaitGear(player)
            Step.WAIT_BASELINE -> handleWaitBaseline(player)
            Step.WAIT_FIRST_RENDER -> handleWaitFirstRender(player)
            Step.ATTEMPT_REGULAR_SHOT -> handleAttemptRegularShot(player)
            Step.WAIT_REGULAR_PROJECTILE -> handleWaitRegularProjectile()
            Step.SWITCH_EXPLOSIVE -> handleSwitchExplosive(player)
            Step.ATTEMPT_EXPLOSIVE_SHOT -> handleAttemptExplosiveShot(player)
            Step.WAIT_EXPLOSION -> handleWaitExplosion()
            Step.COMPLETE,
            Step.FAILED,
            Step.WAIT_MENU,
            Step.WAIT_WORLD -> Unit
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    fun onRenderHand(event: RenderSpecificHandEvent) {
        if (!FocusedSmokeRuntime.enabled || event.hand != EnumHand.MAIN_HAND) {
            return
        }
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val stack = event.itemStack
        val iGun = stack.item as? IGun ?: return
        val gunId = iGun.getGunId(stack)
        val plan = FocusedSmokeRuntime.currentPlan() ?: return
        if (gunId != plan.regularGunId && gunId != plan.explosiveGunId) {
            return
        }
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val displaySummary = FocusedSmokePlanner.describeGunDisplay(snapshot, gunId)
        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId)
        val displayInstance = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
        val gunModel = displayInstance?.gunModel
        val stateMachine = displayInstance?.animationStateMachine
        val stateNames = reflectStateNames(stateMachine)
        val runnerSummary = reflectRunnerSummary(stateMachine?.animationController)
        val attachmentSummary = collectAttachmentSummary(iGun, stack)
        val aimingPathSummary = collectAimingPathSummary(gunModel, stack)
        val soundSummary = displaySummary?.soundKeys?.joinToString(",")?.ifBlank { "none" } ?: "none"
        val details = buildString {
            append("gun=").append(gunId)
            append(" display=").append(displayId ?: "none")
            append(" runtime=").append(gunModel?.javaClass?.simpleName ?: "none")
            append(" stateMachine=").append(displaySummary?.stateMachineId ?: "none")
            append(" defaultType=").append(displaySummary?.defaultAnimationType ?: "none")
            append(" defaultAnimation=").append(displaySummary?.defaultAnimationId ?: "none")
            append(" sounds=").append(soundSummary)
            append(" smInitialized=").append(stateMachine?.isInitialized ?: false)
            append(" states=").append(stateNames)
            append(" runners=").append(runnerSummary)
            append(" attachments=").append(attachmentSummary)
            append(" aimingPath=").append(aimingPathSummary)
            append(" player=").append(player.name)
        }
        FocusedSmokeRuntime.markAnimationObserved(details)
        maybeDispatchSyntheticServerSound(player, gunId, displayId, displaySummary?.soundKeys.orEmpty())
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        if (!FocusedSmokeRuntime.enabled || !event.world.isRemote) {
            return
        }
        resetClientState()
        FocusedSmokeRuntime.resetScenarioState()
    }

    private fun handleOutOfWorld(mc: Minecraft) {
        worldReadyAtMs = 0L
        when (step) {
            Step.COMPLETE,
            Step.FAILED -> {
                restorePauseOnLostFocus(mc)
                return
            }
            else -> Unit
        }
        if (!FocusedSmokeRuntime.autoWorldLaunchEnabled) {
            if (step == Step.WAIT_MENU) {
                transition(Step.WAIT_WORLD, "AUTO_WORLD_LAUNCH_SKIPPED autoWorld=false")
            }
            return
        }
        if (!worldLaunchRequested && mc.currentScreen is GuiMainMenu) {
            val settings = WorldSettings(
                System.currentTimeMillis(),
                GameType.CREATIVE,
                true,
                false,
                WorldType.FLAT,
            ).enableCommands()
            worldLaunchRequested = true
            transition(
                Step.WAIT_WORLD,
                "AUTO_WORLD_LAUNCH folder=${FocusedSmokeRuntime.worldFolderName} world=${FocusedSmokeRuntime.worldDisplayName}"
            )
            mc.launchIntegratedServer(FocusedSmokeRuntime.worldFolderName, FocusedSmokeRuntime.worldDisplayName, settings)
            return
        }
        if (step == Step.WAIT_WORLD && elapsedMs() > WORLD_LOAD_TIMEOUT_MS) {
            FocusedSmokeRuntime.markFailure("world_load_timeout")
            step = Step.FAILED
        }
    }

    private fun handleWaitGear(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (FocusedSmokeRuntime.serverGearReady && holdsGun(player, plan.regularGunId)) {
            transition(
                Step.WAIT_BASELINE,
                "CLIENT_GEAR_READY regularGun=${plan.regularGunId} explosiveGun=${plan.explosiveGunId ?: "none"}"
            )
            return
        }
        if (elapsedMs() > GEAR_TIMEOUT_MS) {
            FocusedSmokeRuntime.markFailure("gear_not_prepared")
            step = Step.FAILED
        }
    }

    private fun handleWaitBaseline(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        if (elapsedMs() < BASELINE_DELAY_MS) {
            return
        }
        val operator = IGunOperator.fromLivingEntity(player)
        transition(
            Step.WAIT_FIRST_RENDER,
            "BASELINE_READY baseTimestamp=${operator.getDataHolder().baseTimestamp} drawCooldown=${operator.getSynDrawCoolDown()}"
        )
    }

    private fun handleWaitFirstRender(player: EntityPlayerSP) {
        if (FocusedSmokeRuntime.animationObserved) {
            transition(Step.ATTEMPT_REGULAR_SHOT, "ANIMATION_GATE_OPEN")
            return
        }
        if (!animationTimeoutLogged && elapsedMs() > ANIMATION_WAIT_MS) {
            animationTimeoutLogged = true
            FocusedSmokeRuntime.markRegularShootResult("ANIMATION_PENDING_TIMEOUT", currentGunId(player) ?: return)
            transition(Step.ATTEMPT_REGULAR_SHOT, "ANIMATION_WAIT_TIMEOUT continue_to_projectile_check=true")
        }
    }

    private fun handleAttemptRegularShot(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        ensureHoldingGun(player, plan.regularGunId, preferredSlot = 0)
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, plan.regularGunId) || !canAttemptShoot(operator)) {
            if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("regular_shot_not_ready")
                step = Step.FAILED
            }
            return
        }
        if (System.currentTimeMillis() - lastShootAttemptAtMs < SHOOT_RETRY_INTERVAL_MS) {
            return
        }
        player.isSprinting = false
        lastShootAttemptAtMs = System.currentTimeMillis()
        val result = attemptSmokeShoot(player, operator)
        FocusedSmokeRuntime.markRegularShootResult(result.name, plan.regularGunId)
        if (result == ShootResult.SUCCESS) {
            transition(Step.WAIT_REGULAR_PROJECTILE, "REGULAR_SHOT_SENT gun=${plan.regularGunId}")
        } else if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("regular_shot_failed_${result.name.lowercase()}")
            step = Step.FAILED
        }
    }

    private fun handleWaitRegularProjectile() {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        if (FocusedSmokeRuntime.regularProjectileObserved) {
            if (plan.explosiveGunId == null) {
                finalizeRun()
            } else {
                transition(Step.SWITCH_EXPLOSIVE, "REGULAR_PROJECTILE_GATE_OPEN gun=${plan.regularGunId}")
            }
            return
        }
        if (elapsedMs() > REGULAR_PROJECTILE_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("regular_projectile_missing")
            step = Step.FAILED
        }
    }

    private fun handleSwitchExplosive(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        val explosiveGunId = plan.explosiveGunId ?: run {
            finalizeRun()
            return
        }
        ensureHoldingGun(player, explosiveGunId, preferredSlot = 1)
        if (!holdsGun(player, explosiveGunId)) {
            if (elapsedMs() > GEAR_TIMEOUT_MS) {
                FocusedSmokeRuntime.markFailure("explosive_gun_not_prepared")
                step = Step.FAILED
            }
            return
        }
        if (elapsedMs() >= BASELINE_DELAY_MS) {
            transition(Step.ATTEMPT_EXPLOSIVE_SHOT, "EXPLOSIVE_GUN_READY gun=$explosiveGunId")
        }
    }

    private fun handleAttemptExplosiveShot(player: EntityPlayerSP) {
        val plan = FocusedSmokeRuntime.currentPlan() ?: run {
            FocusedSmokeRuntime.markFailure("no_regular_gun_plan")
            step = Step.FAILED
            return
        }
        val explosiveGunId = plan.explosiveGunId ?: run {
            finalizeRun()
            return
        }
        ensureHoldingGun(player, explosiveGunId, preferredSlot = 1)
        val operator = IGunOperator.fromLivingEntity(player)
        if (!holdsGun(player, explosiveGunId) || !canAttemptShoot(operator)) {
            if (elapsedMs() > EXPLOSION_WAIT_MS) {
                FocusedSmokeRuntime.markFailure("explosive_shot_not_ready")
                step = Step.FAILED
            }
            return
        }
        if (System.currentTimeMillis() - lastShootAttemptAtMs < SHOOT_RETRY_INTERVAL_MS) {
            return
        }
        player.isSprinting = false
        lastShootAttemptAtMs = System.currentTimeMillis()
        val result = attemptSmokeShoot(player, operator, pitch = EXPLOSIVE_SHOT_PITCH)
        FocusedSmokeRuntime.markExplosiveShootResult(result.name, explosiveGunId)
        if (result == ShootResult.SUCCESS) {
            transition(Step.WAIT_EXPLOSION, "EXPLOSIVE_SHOT_SENT gun=$explosiveGunId")
        } else if (elapsedMs() > EXPLOSION_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("explosive_shot_failed_${result.name.lowercase()}")
            step = Step.FAILED
        }
    }

    private fun handleWaitExplosion() {
        if (FocusedSmokeRuntime.explosionObserved) {
            finalizeRun()
            return
        }
        if (elapsedMs() > EXPLOSION_WAIT_MS) {
            FocusedSmokeRuntime.markFailure("explosion_missing")
            step = Step.FAILED
        }
    }

    private fun finalizeRun() {
        clearKeepAliveGuiIfNeeded(Minecraft.getMinecraft())
        if (FocusedSmokeRuntime.hasPassed()) {
            step = Step.COMPLETE
            return
        }
        val plan = FocusedSmokeRuntime.currentPlan()
        val animationReady = FocusedSmokeRuntime.animationObserved
        val projectileReady = FocusedSmokeRuntime.regularProjectileObserved
        val explosionReady = plan?.explosiveGunId == null || FocusedSmokeRuntime.explosionObserved
        if (animationReady && projectileReady && explosionReady) {
            step = Step.COMPLETE
            return
        }
        val reason = when {
            !animationReady -> "animation_missing"
            !projectileReady -> "regular_projectile_missing"
            !explosionReady -> "explosion_missing"
            else -> "focused_smoke_incomplete"
        }
        FocusedSmokeRuntime.markFailure(reason)
        step = Step.FAILED
    }

    private fun canAttemptShoot(operator: IGunOperator): Boolean {
        if (operator.getSynDrawCoolDown() != 0L) {
            return false
        }
        if (operator.getSynReloadState().stateType.isReloading()) {
            return false
        }
        if (operator.getSynIsBolting()) {
            return false
        }
        return true
    }

    private fun attemptSmokeShoot(
        player: EntityPlayerSP,
        operator: IGunOperator,
        pitch: Float = player.rotationPitch,
        yaw: Float = player.rotationYaw,
    ): ShootResult {
        return LegacyClientShootCoordinator.attemptShoot(player, operator, pitch, yaw, triggerAnimation = true)
    }

    private fun ensureHoldingGun(player: EntityPlayerSP, gunId: net.minecraft.util.ResourceLocation, preferredSlot: Int) {
        if (holdsGun(player, gunId)) {
            return
        }
        if (preferredSlot in 0..8 && stackGunId(player.inventory.getStackInSlot(preferredSlot)) == gunId) {
            switchHotbarSlot(player, preferredSlot)
            return
        }
        for (slot in 0..8) {
            if (stackGunId(player.inventory.getStackInSlot(slot)) == gunId) {
                switchHotbarSlot(player, slot)
                return
            }
        }
    }

    private fun switchHotbarSlot(player: EntityPlayerSP, slot: Int) {
        if (slot !in 0..8 || player.inventory.currentItem == slot) {
            return
        }
        player.inventory.currentItem = slot
        player.connection.sendPacket(CPacketHeldItemChange(slot))
    }

    private fun holdsGun(player: EntityPlayerSP, gunId: net.minecraft.util.ResourceLocation): Boolean =
        stackGunId(player.heldItemMainhand) == gunId

    private fun currentGunId(player: EntityPlayerSP): net.minecraft.util.ResourceLocation? = stackGunId(player.heldItemMainhand)

    private fun stackGunId(stack: net.minecraft.item.ItemStack): net.minecraft.util.ResourceLocation? =
        (stack.item as? IGun)?.getGunId(stack)

    private fun transition(next: Step, marker: String) {
        step = next
        stepEnteredAtMs = System.currentTimeMillis()
        lastShootAttemptAtMs = 0L
        com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] {}", marker)
    }

    private fun elapsedMs(): Long = max(0L, System.currentTimeMillis() - stepEnteredAtMs)

    private fun resetClientState() {
        step = Step.WAIT_MENU
        stepEnteredAtMs = System.currentTimeMillis()
        worldLaunchRequested = false
        worldReadyAtMs = 0L
        animationTimeoutLogged = false
        lastShootAttemptAtMs = 0L
        originalPauseOnLostFocus = null
        keepAliveGuiActive = false
        syntheticServerSoundDispatched = false
        syntheticServerSoundSkipped = false
    }

    private fun maintainClientLiveness(mc: Minecraft) {
        ensurePauseOnLostFocusDisabled(mc)
        updateKeepAliveGui(mc)
    }

    private fun ensurePauseOnLostFocusDisabled(mc: Minecraft) {
        if (originalPauseOnLostFocus == null) {
            originalPauseOnLostFocus = mc.gameSettings.pauseOnLostFocus
        }
        if (mc.gameSettings.pauseOnLostFocus) {
            mc.gameSettings.pauseOnLostFocus = false
            com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] pauseOnLostFocus=false")
        }
    }

    private fun restorePauseOnLostFocus(mc: Minecraft) {
        clearKeepAliveGuiIfNeeded(mc)
        val original = originalPauseOnLostFocus ?: return
        mc.gameSettings.pauseOnLostFocus = original
        originalPauseOnLostFocus = null
    }

    private fun updateKeepAliveGui(mc: Minecraft) {
        val wantsKeepAliveGui = when (step) {
            Step.WAIT_GEAR,
            Step.WAIT_BASELINE,
            Step.WAIT_FIRST_RENDER,
            Step.ATTEMPT_REGULAR_SHOT,
            Step.WAIT_REGULAR_PROJECTILE,
            Step.SWITCH_EXPLOSIVE,
            Step.ATTEMPT_EXPLOSIVE_SHOT,
            Step.WAIT_EXPLOSION -> true
            else -> false
        }
        val isFocusAtRisk = !Display.isActive() || !mc.inGameHasFocus
        val shouldOpenChat = wantsKeepAliveGui && isFocusAtRisk
        if (shouldOpenChat) {
            if (mc.currentScreen !is GuiChat) {
                mc.displayGuiScreen(GuiChat(""))
                keepAliveGuiActive = true
                com.tacz.legacy.TACZLegacy.logger.info(
                    "[FocusedSmoke] KEEPALIVE_GUI_OPEN screen=GuiChat displayActive={} inGameHasFocus={}",
                    Display.isActive(),
                    mc.inGameHasFocus,
                )
            }
            return
        }
        if (keepAliveGuiActive && (mc.currentScreen is GuiChat) && !wantsKeepAliveGui) {
            clearKeepAliveGuiIfNeeded(mc)
        }
    }

    private fun clearKeepAliveGuiIfNeeded(mc: Minecraft) {
        if (!keepAliveGuiActive) {
            return
        }
        if (mc.currentScreen is GuiChat) {
            mc.displayGuiScreen(null)
            com.tacz.legacy.TACZLegacy.logger.info("[FocusedSmoke] KEEPALIVE_GUI_CLOSE")
        }
        keepAliveGuiActive = false
    }

    private fun maybeDispatchSyntheticServerSound(
        player: EntityPlayerSP,
        gunId: net.minecraft.util.ResourceLocation,
        displayId: net.minecraft.util.ResourceLocation?,
        soundKeys: List<String>,
    ) {
        if (syntheticServerSoundDispatched || syntheticServerSoundSkipped) {
            return
        }
        val actualDisplayId = displayId ?: return
        val soundKey = pickSyntheticServerSoundKey(soundKeys) ?: return
        val messageContext = createSyntheticClientMessageContext()
        if (messageContext == null) {
            syntheticServerSoundSkipped = true
            TACZLegacy.logger.info("[FocusedSmoke][Audio] SERVER_MESSAGE_SYNTHETIC_SKIPPED reason=context_unavailable")
            return
        }
        syntheticServerSoundDispatched = true
        ServerMessageSound.Handler().onMessage(
            ServerMessageSound(
                player.entityId,
                gunId,
                actualDisplayId,
                soundKey,
                0.15f,
                1.0f,
                8,
            ),
            messageContext,
        )
        TACZLegacy.logger.info(
            "[FocusedSmoke][Audio] SERVER_MESSAGE_SYNTHETIC_SENT gun={} display={} soundKey={}",
            gunId,
            actualDisplayId,
            soundKey,
        )
    }

    private fun pickSyntheticServerSoundKey(soundKeys: List<String>): String? {
        if (soundKeys.isEmpty()) {
            return null
        }
        return soundKeys.firstOrNull { it == "shoot" }
            ?: soundKeys.firstOrNull { it == "draw" }
            ?: soundKeys.firstOrNull { it == "reload_tactical" }
            ?: soundKeys.firstOrNull()
    }

    private fun createSyntheticClientMessageContext(): MessageContext? = runCatching {
        val constructor = MessageContext::class.java.getDeclaredConstructor(INetHandler::class.java, Side::class.java)
        constructor.isAccessible = true
        constructor.newInstance(null, Side.CLIENT)
    }.getOrNull()

    private fun reflectStateNames(stateMachine: AnimationStateMachine<*>?): String {
        if (stateMachine == null) {
            return "none"
        }
        return runCatching {
            val field = AnimationStateMachine::class.java.getDeclaredField("currentStates")
            field.isAccessible = true
            val states = field.get(stateMachine) as? Iterable<*>
            (states?.toList() ?: emptyList())
                .mapNotNull { state -> state?.javaClass?.simpleName?.takeIf(String::isNotBlank) ?: state?.javaClass?.name }
                .joinToString("|")
                .ifBlank { "empty" }
        }.getOrElse { error -> "error:${error.javaClass.simpleName}" }
    }

    private fun reflectRunnerSummary(controller: AnimationController?): String {
        if (controller == null) {
            return "none"
        }
        return runCatching {
            val field = AnimationController::class.java.getDeclaredField("currentRunners")
            field.isAccessible = true
            val runners = field.get(controller) as? List<*>
            (runners ?: emptyList<Any?>()).mapIndexedNotNull { index, value ->
                val runner = value as? ObjectAnimationRunner ?: return@mapIndexedNotNull null
                val status = when {
                    runner.isTransitioning -> "transition"
                    runner.isRunning -> "running"
                    runner.isHolding -> "holding"
                    runner.isStopped -> "stopped"
                    runner.isPausing -> "paused"
                    else -> "idle"
                }
                "#$index:${runner.animation.name}:$status"
            }.joinToString("|").ifBlank { "empty" }
        }.getOrElse { error -> "error:${error.javaClass.simpleName}" }
    }

    private fun collectAttachmentSummary(iGun: IGun, stack: net.minecraft.item.ItemStack): String {
        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val installed = AttachmentType.values()
            .asSequence()
            .filter { it != AttachmentType.NONE }
            .flatMap { type ->
                sequence {
                    val attachmentId = iGun.getAttachmentId(stack, type)
                    if (attachmentId != DefaultAssets.EMPTY_ATTACHMENT_ID) {
                        yield(formatAttachmentEntry(snapshot, type.toString(), attachmentId))
                    }
                    val builtIn = iGun.getBuiltInAttachmentId(stack, type)
                    if (builtIn != DefaultAssets.EMPTY_ATTACHMENT_ID) {
                        yield(formatAttachmentEntry(snapshot, "builtin-$type", builtIn))
                    }
                }
            }
            .toList()
        return installed.joinToString(",").ifBlank { "none" }
    }

    private fun formatAttachmentEntry(
        snapshot: com.tacz.legacy.common.resource.TACZRuntimeSnapshot,
        label: String,
        attachmentId: net.minecraft.util.ResourceLocation,
    ): String {
        val displayId = TACZGunPackPresentation.resolveAttachmentDisplayId(snapshot, attachmentId)
        val index = TACZClientAssetManager.getAttachmentIndex(attachmentId)
        val adapter = index?.adapterNodeName ?: "none"
        val showMuzzle = index?.isShowMuzzle ?: false
        val views = index?.views?.joinToString("|") ?: "none"
        return "$label=$attachmentId(display=${displayId ?: "none"};adapter=$adapter;showMuzzle=$showMuzzle;scope=${index?.isScope ?: false};sight=${index?.isSight ?: false};views=$views)"
    }

    private fun collectAimingPathSummary(model: Any?, stack: net.minecraft.item.ItemStack): String {
        val gunModel = model as? BedrockGunModel ?: return "none"
        return gunModel.resolveAimingViewPath(stack)
            ?.joinToString(">") { part -> part.name ?: "<anon>" }
            ?.ifBlank { "empty" }
            ?: "none"
    }
}

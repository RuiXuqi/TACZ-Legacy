package com.tacz.legacy.client.animation

import com.tacz.legacy.api.client.animation.AnimationController
import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.client.animation.model.GunAnimatedModel
import com.tacz.legacy.client.render.item.LegacyAnimationPose

/**
 * 单把枪（一次持有 session）的动画控制器会话。
 *
 * 职责：
 * 1. 持有 [AnimationController] + [GunAnimatedModel]
 * 2. 每帧接收 Lua 状态机的轨道快照，并将意图同步到控制器
 *    （runAnimation / setBlending）
 * 3. 调用 controller.update() → listener 写入 → model 收集姿态
 * 4. 可选的音效回调注入
 */

internal enum class SessionTrackPlayMode {
    LOOP,
    PLAY_ONCE_HOLD,
    PLAY_ONCE_STOP
}

internal data class SessionTrack(
    val trackKey: String,
    val animationName: String,
    val playMode: SessionTrackPlayMode,
    val progress: Float
)

internal class GunAnimationControllerSession(
    prototypes: List<ObjectAnimation>,
    boneNames: Set<String>
) {
    private val model: GunAnimatedModel = GunAnimatedModel(boneNames)
    private val controller: AnimationController = AnimationController(prototypes, model)

    // 当前各控制器轨道上正在播放的动画名称（用于变更检测）
    private val activeAnimationByTrack = HashMap<Int, String>()

    // Lua trackKey → controller 整数轨道号
    private val trackKeyToControllerTrack = HashMap<String, Int>()
    private var nextControllerTrack = 0

    /**
     * 根据 Lua 状态机的轨道快照，将动画意图同步到 [AnimationController]。
     * 只在动画名称变更时触发 runAnimation（含过渡）。
     */
    fun syncFromSnapshots(
        snapshots: List<SessionTrack>,
        transitionTimeS: Float = DEFAULT_TRANSITION_TIME_S,
        forceRestartNames: Set<String> = emptySet()
    ) {
        for (snapshot in snapshots) {
            val trackKey = snapshot.trackKey
            if (snapshot.animationName.isBlank()) continue

            val controllerTrack = resolveControllerTrack(trackKey)
            val currentAnim = activeAnimationByTrack[controllerTrack]

            if (currentAnim != snapshot.animationName || forceRestartNames.contains(snapshot.animationName)) {
                if (!controller.containPrototype(snapshot.animationName)) continue

                val playType = when (snapshot.playMode) {
                    SessionTrackPlayMode.LOOP -> ObjectAnimation.PlayType.LOOP
                    SessionTrackPlayMode.PLAY_ONCE_HOLD -> ObjectAnimation.PlayType.PLAY_ONCE_HOLD
                    else -> ObjectAnimation.PlayType.PLAY_ONCE_STOP
                }

                val effectiveTransition = if (currentAnim == null) 0f else transitionTimeS
                controller.runAnimation(controllerTrack, snapshot.animationName, playType, effectiveTransition)

                // 轨道行号 ≥1 的为混合轨道
                val line = trackKey.substringBefore(':').toIntOrNull() ?: 0
                controller.setBlending(controllerTrack, line > 0)

                activeAnimationByTrack[controllerTrack] = snapshot.animationName
            }
        }
    }

    /**
     * 帧更新：清空模型 → controller.update() → 收集变换。
     */
    fun updateAndCollectPose(): LegacyAnimationPose {
        model.cleanAllTransforms()
        controller.update()
        return model.collectPose()
    }

    private fun resolveControllerTrack(trackKey: String): Int {
        return trackKeyToControllerTrack.getOrPut(trackKey) {
            val track = nextControllerTrack++
            track
        }
    }

    companion object {
        internal const val DEFAULT_TRANSITION_TIME_S = 0.15f
    }
}

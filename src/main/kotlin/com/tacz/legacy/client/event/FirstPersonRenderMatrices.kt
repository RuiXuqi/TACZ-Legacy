package com.tacz.legacy.client.event

import com.tacz.legacy.client.model.bedrock.BedrockPart
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

internal object FirstPersonRenderMatrices {
    internal data class PositioningNode(
        val x: Float,
        val y: Float,
        val z: Float,
        val xRot: Float,
        val yRot: Float,
        val zRot: Float,
        val hasParent: Boolean,
    )

    internal fun fromBedrockPath(nodePath: List<BedrockPart>?): List<PositioningNode>? {
        return nodePath?.map { part ->
            PositioningNode(
                x = part.x,
                y = part.y,
                z = part.z,
                xRot = part.xRot,
                yRot = part.yRot,
                zRot = part.zRot,
                hasParent = part.parent != null,
            )
        }
    }

    internal fun buildPositioningNodeInverse(nodePath: List<PositioningNode>?): Matrix4f {
        val matrix = Matrix4f().identity()
        if (nodePath == null) {
            return matrix
        }
        for (i in nodePath.indices.reversed()) {
            val part = nodePath[i]
            matrix.rotateX(-part.xRot)
            matrix.rotateY(-part.yRot)
            matrix.rotateZ(-part.zRot)
            if (part.hasParent) {
                matrix.translate(-part.x / 16.0f, -part.y / 16.0f, -part.z / 16.0f)
            } else {
                matrix.translate(-part.x / 16.0f, 1.5f - part.y / 16.0f, -part.z / 16.0f)
            }
        }
        return matrix
    }

    internal fun appendPath(
        primary: List<PositioningNode>?,
        secondary: List<PositioningNode>?,
    ): List<PositioningNode>? {
        if (primary.isNullOrEmpty()) {
            return secondary
        }
        if (secondary.isNullOrEmpty()) {
            return primary
        }
        val combined = ArrayList<PositioningNode>(primary.size + secondary.size)
        combined.addAll(primary)
        combined.addAll(secondary)
        return combined
    }

    internal fun resolveScopeViewSwitchIndex(views: IntArray?, zoomNumber: Int): Int {
        if (views == null || views.isEmpty()) {
            return 0
        }
        val rawIndex = views[Math.floorMod(zoomNumber, views.size)]
        return (rawIndex - 1).coerceAtLeast(0)
    }

    internal fun buildAimingPositioningTransform(
        idlePath: List<PositioningNode>?,
        aimingPath: List<PositioningNode>?,
        aimingProgress: Float,
    ): Matrix4f {
        val idleMatrix = buildPositioningNodeInverse(idlePath)
        if (aimingPath == null) {
            return idleMatrix
        }
        val clampedProgress = aimingProgress.coerceIn(0.0f, 1.0f)
        if (clampedProgress <= 0.0f) {
            return idleMatrix
        }
        val aimingMatrix = buildPositioningNodeInverse(aimingPath)
        if (clampedProgress >= 1.0f) {
            return aimingMatrix
        }
        return interpolateMatrix(idleMatrix, aimingMatrix, clampedProgress)
    }

    private fun interpolateMatrix(from: Matrix4f, to: Matrix4f, alpha: Float): Matrix4f {
        val fromTranslation = from.getTranslation(Vector3f())
        val toTranslation = to.getTranslation(Vector3f())
        val blendedTranslation = fromTranslation.lerp(toTranslation, alpha, Vector3f())

        val blendedRotation = from.getNormalizedRotation(Quaternionf())
            .slerp(to.getNormalizedRotation(Quaternionf()), alpha)

        return Matrix4f()
            .translation(blendedTranslation)
            .rotate(blendedRotation)
    }
}
package com.tacz.legacy.client.render.item

import com.tacz.legacy.api.client.animation.Animations
import com.tacz.legacy.client.animation.GunAnimationControllerSession
import com.tacz.legacy.client.animation.SessionTrack
import com.tacz.legacy.client.animation.SessionTrackPlayMode
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class GunAnimationPlaybackTest {
    @Test
    fun testDefaultGunpackAk47Animation() {
        val animPath = "/assets/tacz/animations/ak47.animation.json"
        val resourceStream = java.io.File("src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/animations/ak47.animation.json").inputStream()
            
        
        val gson = com.google.gson.GsonBuilder().registerTypeAdapter(com.tacz.legacy.client.resource.pojo.animation.bedrock.AnimationKeyframes::class.java, com.tacz.legacy.client.resource.serialize.AnimationKeyframesSerializer()).registerTypeAdapter(com.tacz.legacy.client.resource.pojo.animation.bedrock.SoundEffectKeyframes::class.java, com.tacz.legacy.client.resource.serialize.SoundEffectKeyframesSerializer()).create()
        val bedrockFile = gson.fromJson(java.io.InputStreamReader(resourceStream), BedrockAnimationFile::class.java)
        val objectAnimations = Animations.createAnimationFromBedrock(bedrockFile)
        
        assertTrue(objectAnimations.isNotEmpty())
        
        val boneNames = objectAnimations.flatMap { it.channels.keys }.toSet()
        val session = GunAnimationControllerSession(objectAnimations, boneNames)
        
        val tracks = mutableListOf(
            SessionTrack("0:0", "static_idle", SessionTrackPlayMode.LOOP, 0f),
            SessionTrack("1:0", "shoot", SessionTrackPlayMode.PLAY_ONCE_HOLD, 0f)
        )
        
        // Sync first frame
        session.syncFromSnapshots(tracks, 0f)
        var pose = session.updateAndCollectPose()
        
        // Should have transformations since we started
        assertNotNull(pose)
        
        // Let's tick the time artificially? We can't directly. AnimationController uses System.nanoTime().
        // Wait, for tests we could sleep a bit.
        Thread.sleep(50)
        pose = session.updateAndCollectPose()
        
        val bodyTransform = pose.boneTransformsByName["root"] ?: LegacyBoneTransform(LegacyVec3.ZERO, LegacyVec3.ZERO, LegacyVec3.ONE)
        val isAnimating = bodyTransform.positionOffset.x != 0f || bodyTransform.positionOffset.y != 0f || bodyTransform.positionOffset.z != 0f ||
                          bodyTransform.rotationOffset.x != 0f || bodyTransform.rotationOffset.y != 0f || bodyTransform.rotationOffset.z != 0f
        
        assertTrue("Root should be animating in shoot", isAnimating)
    }
}

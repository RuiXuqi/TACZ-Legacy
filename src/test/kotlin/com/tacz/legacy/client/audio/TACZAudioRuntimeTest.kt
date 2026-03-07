package com.tacz.legacy.client.audio

import com.tacz.legacy.client.sound.GunSoundPlayManager
import net.minecraft.util.ResourceLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TACZAudioRuntimeTest {
    @After
    fun tearDown() {
        System.clearProperty("tacz.audio.backend")
        System.clearProperty("tacz.audio.preflight")
        System.clearProperty("tacz.audio.preflight.strict")
        TACZAudioRuntime.clear()
        GunSoundPlayManager.resetPlaybackBackendForTesting()
    }

    @Test
    fun `manifest probes ogg vorbis asset from directory pack`() {
        val root = Files.createTempDirectory("tacz-audio-runtime-manifest")
        val assetPath = root.resolve("assets/example/tacz_sounds/weapons/test.ogg")
        Files.createDirectories(assetPath.parent)
        Files.write(assetPath, createMinimalVorbisOgg(sampleRate = 22050, channels = 1))

        val soundId = ResourceLocation("example", "weapons/test")
        TACZAudioRuntime.reload(
            listOf(root.toFile()),
            mapOf(
                soundId to setOf(
                    TACZAudioReference(
                        sourceType = "gun-display",
                        ownerId = ResourceLocation("example", "display/test"),
                        key = "shoot",
                    )
                )
            ),
        )

        val descriptor = TACZAudioRuntime.getManifest().entries[soundId]
        assertNotNull(descriptor)
        descriptor ?: return
        assertEquals(ResourceLocation("example", "tacz_sounds/weapons/test.ogg"), descriptor.assetLocation)
        assertTrue(descriptor.exists)
        assertEquals(TACZAudioProbeStatus.SUPPORTED_OGG_VORBIS, descriptor.probeStatus)
        assertEquals(1, descriptor.channels)
        assertEquals(22050, descriptor.sampleRate)
    }

    @Test
    fun `diagnostic backend records animation and server requests`() {
        System.setProperty("tacz.audio.backend", "diagnostic")
        val root = Files.createTempDirectory("tacz-audio-runtime-diagnostic")
        val assetPath = root.resolve("assets/example/tacz_sounds/weapons/test.ogg")
        Files.createDirectories(assetPath.parent)
        Files.write(assetPath, createMinimalVorbisOgg(sampleRate = 44100, channels = 2))

        val soundId = ResourceLocation("example", "weapons/test")
        TACZAudioRuntime.reload(
            listOf(root.toFile()),
            mapOf(
                soundId to setOf(
                    TACZAudioReference(
                        sourceType = "animation-keyframe",
                        ownerId = ResourceLocation("example", "animations/test"),
                        key = "reload@0.25",
                    )
                )
            ),
        )

        GunSoundPlayManager.playAnimationSound(null, soundId, 1f, 1f, 16)
        GunSoundPlayManager.playNetworkSound(null, soundId, 1f, 1f, 16)

        val submissions = TACZAudioRuntime.getRecentSubmissions()
        assertEquals(2, submissions.size)
        assertEquals(
            listOf(TACZAudioRequestOrigin.ANIMATION, TACZAudioRequestOrigin.SERVER_MESSAGE),
            submissions.map(TACZAudioSubmissionRecord::origin),
        )
        assertTrue(submissions.all { it.backendMode == TACZAudioBackendMode.DIAGNOSTIC })
        assertTrue(submissions.all { it.probeStatus == TACZAudioProbeStatus.SUPPORTED_OGG_VORBIS })
        assertTrue(submissions.all { it.disposition == TACZAudioSubmissionDisposition.RECORDED_ONLY })
        assertFalse(TACZAudioRuntime.shouldUseLegacyMinecraftBridge())
    }

    @Test
    fun `null backend records request without legacy bridge`() {
        System.setProperty("tacz.audio.backend", "null")
        val root = Files.createTempDirectory("tacz-audio-runtime-null")
        val assetPath = root.resolve("assets/example/tacz_sounds/weapons/test.ogg")
        Files.createDirectories(assetPath.parent)
        Files.write(assetPath, createMinimalVorbisOgg(sampleRate = 32000, channels = 1))

        val soundId = ResourceLocation("example", "weapons/test")
        TACZAudioRuntime.reload(
            listOf(root.toFile()),
            mapOf(soundId to setOf(TACZAudioReference(sourceType = "server-sound", key = "shoot"))),
        )

        GunSoundPlayManager.playNetworkSound(null, soundId, 1f, 1f, 16)

        val submissions = TACZAudioRuntime.getRecentSubmissions()
        assertEquals(1, submissions.size)
        assertEquals(TACZAudioBackendMode.NULL, submissions.single().backendMode)
        assertEquals(TACZAudioSubmissionDisposition.RECORDED_ONLY, submissions.single().disposition)
        assertFalse(TACZAudioRuntime.shouldUseLegacyMinecraftBridge())
    }

    private fun createMinimalVorbisOgg(sampleRate: Int, channels: Int): ByteArray {
        val identificationPacket = ByteArray(30)
        identificationPacket[0] = 1
        "vorbis".toByteArray(StandardCharsets.US_ASCII).copyInto(identificationPacket, destinationOffset = 1)
        writeLittleEndianInt(identificationPacket, offset = 7, value = 0)
        identificationPacket[11] = channels.toByte()
        writeLittleEndianInt(identificationPacket, offset = 12, value = sampleRate)
        writeLittleEndianInt(identificationPacket, offset = 16, value = 0)
        writeLittleEndianInt(identificationPacket, offset = 20, value = 0)
        writeLittleEndianInt(identificationPacket, offset = 24, value = 0)
        identificationPacket[28] = ((4 shl 4) or 4).toByte()
        identificationPacket[29] = 1

        return ByteArrayOutputStream().use { out ->
            out.write("OggS".toByteArray(StandardCharsets.US_ASCII))
            out.write(0)
            out.write(2)
            repeat(8) { out.write(0) }
            writeLittleEndianInt(out, 1)
            writeLittleEndianInt(out, 0)
            writeLittleEndianInt(out, 0)
            out.write(1)
            out.write(identificationPacket.size)
            out.write(identificationPacket)
            out.toByteArray()
        }
    }

    private fun writeLittleEndianInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun writeLittleEndianInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }
}

package com.tacz.legacy.client.sound;

import com.tacz.legacy.TACZLegacy;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;

/**
 * Client-side sound playback for gun animation sounds.
 * Port of upstream TACZ SoundPlayManager, adapted for 1.12.2.
 * <p>
 * Currently logs sound triggers; actual audio playback requires
 * a custom sound loading pipeline (loading .ogg from gun packs).
 */
@SideOnly(Side.CLIENT)
public class GunSoundPlayManager {
    private static final Marker MARKER = MarkerManager.getMarker("GunSound");
    private static boolean loggedOnce = false;

    public static void playClientSound(Entity entity, @Nullable ResourceLocation soundName, float volume, float pitch, int distance) {
        if (soundName == null) return;
        if (!loggedOnce) {
            TACZLegacy.logger.debug(MARKER, "Animation sound triggered: {} (vol={}, pitch={}, dist={})", soundName, volume, pitch, distance);
            loggedOnce = true;
        }
        // TODO: Implement custom audio playback from gun pack .ogg files.
        // The sound names reference gun pack sounds (e.g., "tacz:ak47/fire"),
        // which are .ogg files loaded from pack zips. A proper implementation
        // would decode the audio and play through OpenAL/SoundHandler.
    }

    /**
     * Reset the one-shot log flag (e.g., on resource reload).
     */
    public static void resetLogFlag() {
        loggedOnce = false;
    }
}

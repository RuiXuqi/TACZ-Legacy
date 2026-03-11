package com.tacz.legacy.client.sound;

import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.client.resource.TACZClientAssetManager;
import com.tacz.legacy.mixin.minecraft.client.MinecraftInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resource-pack bridge exposing gun pack sound assets to Minecraft's sound system.
 */
@SideOnly(Side.CLIENT)
public final class GunPackSoundResourcePack implements IResourcePack {
    private static final GunPackSoundResourcePack INSTANCE = new GunPackSoundResourcePack();

    private volatile Set<String> resourceDomains = Collections.emptySet();

    private GunPackSoundResourcePack() {
    }

    @SuppressWarnings("deprecation")
    public static synchronized void installOrUpdate(Set<ResourceLocation> soundResources) {
        synchronize(soundResources, true);
    }

    @SuppressWarnings("deprecation")
    public static synchronized void synchronize(Set<ResourceLocation> soundResources, boolean enabled) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        if (!enabled) {
            INSTANCE.resourceDomains = Collections.emptySet();
            boolean removed = removeInstalled(minecraft);
            if (removed) {
                TACZLegacy.logger.info("Refreshing resources after disabling gun pack sound bridge");
                minecraft.refreshResources();
            }
            return;
        }

        LinkedHashSet<String> newDomains = new LinkedHashSet<>();
        for (ResourceLocation soundId : soundResources) {
            newDomains.add(soundId.getNamespace());
        }
        boolean domainsChanged = !INSTANCE.resourceDomains.equals(newDomains);
        INSTANCE.resourceDomains = Collections.unmodifiableSet(newDomains);

        boolean installedNow = ensureInstalled(minecraft);
        if ((installedNow || domainsChanged) && !newDomains.isEmpty()) {
            TACZLegacy.logger.info("Refreshing resources after gun pack sound bridge update (domains={})", newDomains);
            minecraft.refreshResources();
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean ensureInstalled(Minecraft minecraft) {
        List<IResourcePack> defaultResourcePacks = getDefaultResourcePacks(minecraft);
        if (defaultResourcePacks.contains(INSTANCE)) {
            return false;
        }
        defaultResourcePacks.add(INSTANCE);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean removeInstalled(Minecraft minecraft) {
        return getDefaultResourcePacks(minecraft).remove(INSTANCE);
    }

    @SuppressWarnings("unchecked")
    private static List<IResourcePack> getDefaultResourcePacks(Minecraft minecraft) {
        if (!(minecraft instanceof MinecraftInvoker)) {
            throw new IllegalStateException("MinecraftInvoker mixin not applied; unable to access default resource packs safely");
        }
        return ((MinecraftInvoker) minecraft).tacz$getDefaultResourcePacks();
    }

    private ResourceLocation redirectSoundPath(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith("sounds/") && path.endsWith(".ogg")) {
            return new ResourceLocation(location.getNamespace(), "tacz_sounds/" + path.substring("sounds/".length()));
        }
        return location;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        location = redirectSoundPath(location);
        InputStream stream = TACZClientAssetManager.INSTANCE.openPackAsset(location);
        if (stream == null) {
            throw new FileNotFoundException(location.toString());
        }
        return stream;
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        location = redirectSoundPath(location);
        String path = location.getPath();
        if (!path.endsWith(".ogg")) {
            return false;
        }
        return TACZClientAssetManager.INSTANCE.hasPackAsset(location);
    }

    @Override
    public Set<String> getResourceDomains() {
        return resourceDomains;
    }

    @Override
    @Nullable
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    @Override
    public String getPackName() {
        return "TACZ Gun Pack Sounds";
    }
}

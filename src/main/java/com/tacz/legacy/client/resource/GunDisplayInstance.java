package com.tacz.legacy.client.resource;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tacz.legacy.TACZLegacy;
import com.tacz.legacy.api.client.animation.AnimationController;
import com.tacz.legacy.api.client.animation.Animations;
import com.tacz.legacy.api.client.animation.ObjectAnimation;
import com.tacz.legacy.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.legacy.api.client.animation.statemachine.LuaStateMachineFactory;
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.legacy.client.model.BedrockAnimatedModel;
import com.tacz.legacy.client.model.GunModelConstant;
import com.tacz.legacy.client.model.functional.LeftHandRender;
import com.tacz.legacy.client.model.functional.RightHandRender;
import com.tacz.legacy.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.legacy.client.resource.pojo.display.gun.GunTransform;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import net.minecraft.util.ResourceLocation;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 经过处理和校验的枪械显示运行时数据。
 * Port of upstream TACZ GunDisplayInstance, simplified for 1.12.2.
 */
public class GunDisplayInstance {
    private BedrockAnimatedModel gunModel;
    private @Nullable LuaAnimationStateMachine<GunAnimationStateContext> animationStateMachine;
    private @Nullable LuaTable stateMachineParam;
    private Map<String, ResourceLocation> sounds = Maps.newHashMap();
    private @Nullable GunTransform transform;
    private ResourceLocation modelTexture;
    private float ironZoom = 1.2f;
    private float zoomModelFov = 70f;
    private boolean showCrosshair = false;
    private @Nullable LeftHandRender leftHandRender;
    private @Nullable RightHandRender rightHandRender;

    private GunDisplayInstance() {}

    @Nullable
    public static GunDisplayInstance create(GunDisplay display, TACZClientAssetManager assets) {
        GunDisplayInstance instance = new GunDisplayInstance();
        try {
            instance.checkTextureAndModel(display, assets);
            instance.checkAnimation(display, assets);
            instance.checkSounds(display);
            instance.checkTransform(display);
            instance.ironZoom = Math.max(display.getIronZoom(), 1.0f);
            instance.zoomModelFov = Math.min(display.getZoomModelFov(), 70f);
            instance.showCrosshair = display.isShowCrosshair();
            return instance;
        } catch (Exception e) {
            TACZLegacy.logger.warn("Failed to create GunDisplayInstance: {}", e.getMessage());
            return null;
        }
    }

    private void checkTextureAndModel(GunDisplay display, TACZClientAssetManager assets) {
        ResourceLocation modelLocation = display.getModelLocation();
        if (modelLocation == null) throw new IllegalArgumentException("display missing model");
        TACZClientAssetManager.ModelData modelData = assets.getModel(modelLocation);
        if (modelData == null) throw new IllegalArgumentException("model not found: " + modelLocation);

        ResourceLocation textureLocation = display.getModelTexture();
        if (textureLocation == null) throw new IllegalArgumentException("display missing texture");
        modelTexture = textureLocation;

        gunModel = new BedrockAnimatedModel(modelData.getPojo(), modelData.getVersion());
        // Register hand functional renderers
        leftHandRender = new LeftHandRender(gunModel);
        rightHandRender = new RightHandRender(gunModel);
        gunModel.setFunctionalRenderer(GunModelConstant.LEFTHAND_POS_NODE, bedrockPart -> leftHandRender);
        gunModel.setFunctionalRenderer(GunModelConstant.RIGHTHAND_POS_NODE, bedrockPart -> rightHandRender);
    }

    private void checkAnimation(GunDisplay display, TACZClientAssetManager assets) {
        ResourceLocation location = display.getAnimationLocation();
        AnimationController controller;
        if (location == null) {
            controller = new AnimationController(Lists.newArrayList(), gunModel);
        } else {
            BedrockAnimationFile animFile = assets.getAnimationFile(location);
            if (animFile == null) throw new IllegalArgumentException("animation not found: " + location);
            controller = Animations.createControllerFromBedrock(animFile, gunModel);
        }
        // Initialize state machine
        ResourceLocation smLocation = display.getStateMachineLocation();
        if (smLocation == null) {
            smLocation = new ResourceLocation("tacz", "default_state_machine");
        }
        LuaTable script = assets.getScript(smLocation);
        if (script != null) {
            animationStateMachine = new LuaStateMachineFactory<GunAnimationStateContext>()
                    .setController(controller)
                    .setLuaScripts(script)
                    .build();
        } else {
            TACZLegacy.logger.warn("State machine script not found: {}, gun will have no animation", smLocation);
        }
        // Load state machine params
        Map<String, Object> params = display.getStateMachineParam();
        if (params != null) {
            stateMachineParam = new LuaTable();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                stateMachineParam.set(entry.getKey(), CoerceJavaToLua.coerce(entry.getValue()));
            }
        }
    }

    private void checkSounds(GunDisplay display) {
        Map<String, ResourceLocation> soundMaps = display.getSounds();
        if (soundMaps != null && !soundMaps.isEmpty()) {
            sounds.putAll(soundMaps);
        }
    }

    private void checkTransform(GunDisplay display) {
        GunTransform t = display.getTransform();
        if (t == null || t.getScale() == null) {
            transform = GunTransform.getDefault();
        } else {
            transform = t;
        }
    }

    // --- Getters ---

    public BedrockAnimatedModel getGunModel() {
        return gunModel;
    }

    /**
     * Set the active gun texture for hand renderers so they can restore it
     * after binding the player skin texture.
     */
    public void setActiveGunTexture(@Nullable ResourceLocation texture) {
        if (leftHandRender != null) leftHandRender.setGunTexture(texture);
        if (rightHandRender != null) rightHandRender.setGunTexture(texture);
    }

    @Nullable
    public LuaAnimationStateMachine<GunAnimationStateContext> getAnimationStateMachine() {
        return animationStateMachine;
    }

    @Nullable
    public LuaTable getStateMachineParam() {
        return stateMachineParam;
    }

    @Nullable
    public ResourceLocation getSound(String name) {
        return sounds.get(name);
    }

    public Map<String, ResourceLocation> getSounds() {
        return sounds;
    }

    @Nullable
    public GunTransform getTransform() {
        return transform;
    }

    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    public float getIronZoom() {
        return ironZoom;
    }

    public float getZoomModelFov() {
        return zoomModelFov;
    }

    public boolean isShowCrosshair() {
        return showCrosshair;
    }
}

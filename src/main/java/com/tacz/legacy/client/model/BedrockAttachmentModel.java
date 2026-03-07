package com.tacz.legacy.client.model;

import com.tacz.legacy.client.model.bedrock.BedrockPart;
import com.tacz.legacy.client.model.bedrock.ModelRendererWrapper;
import com.tacz.legacy.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.legacy.client.resource.pojo.model.BedrockVersion;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gun-mounted attachment model runtime.
 *
 * This is a minimal Legacy port of upstream BedrockAttachmentModel focused on
 * the runtime data needed for mounted attachment rendering and scope view
 * positioning. Full stencil/optic masking can be layered on top later.
 */
public class BedrockAttachmentModel extends BedrockAnimatedModel {
    private static final String SCOPE_VIEW_NODE = "scope_view";
    private static final String DIVISION_NODE = "division";
    private static final String OCULAR_NODE = "ocular";
    private static final String OCULAR_SCOPE_NODE = "ocular_scope";
    private static final String OCULAR_SIGHT_NODE = "ocular_sight";

    protected final List<List<BedrockPart>> scopeViewPaths = new ArrayList<>();
    @Nullable
    private ItemStack currentGunItem;
    @Nullable
    private ItemStack attachmentItem;
    private boolean isScope = false;
    private boolean isSight = false;

    public BedrockAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
        cacheScopeViewPaths();
        hideIndexedNodes(SCOPE_VIEW_NODE);
        hideIndexedNodes(DIVISION_NODE);
        hideIndexedNodes(OCULAR_NODE);
        hideIndexedNodes(OCULAR_SCOPE_NODE);
        hideIndexedNodes(OCULAR_SIGHT_NODE);
    }

    private void cacheScopeViewPaths() {
        List<BedrockPart> path = getPath(modelMap.get(SCOPE_VIEW_NODE));
        int index = 2;
        while (path != null) {
            scopeViewPaths.add(path);
            path = getPath(modelMap.get(SCOPE_VIEW_NODE + '_' + index++));
        }
    }

    private void hideIndexedNodes(String baseNodeName) {
        ModelRendererWrapper wrapper = modelMap.get(baseNodeName);
        int index = 2;
        while (wrapper != null) {
            wrapper.setHidden(true);
            wrapper = modelMap.get(baseNodeName + '_' + index++);
        }
    }

    @Nullable
    public List<BedrockPart> getScopeViewPath(int viewSwitchCount) {
        if (scopeViewPaths.isEmpty()) {
            return null;
        }
        if (viewSwitchCount < 0 || viewSwitchCount >= scopeViewPaths.size()) {
            return scopeViewPaths.get(0);
        }
        return scopeViewPaths.get(viewSwitchCount);
    }

    public void setIsScope(boolean scope) {
        isScope = scope;
    }

    public void setIsSight(boolean sight) {
        isSight = sight;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public void render(@Nullable ItemStack attachmentItem, @Nullable ItemStack currentGunItem) {
        this.attachmentItem = attachmentItem;
        this.currentGunItem = currentGunItem;
        super.render();
    }

    @Nullable
    public ItemStack getCurrentGunItem() {
        return currentGunItem;
    }

    @Nullable
    public ItemStack getAttachmentItem() {
        return attachmentItem;
    }
}

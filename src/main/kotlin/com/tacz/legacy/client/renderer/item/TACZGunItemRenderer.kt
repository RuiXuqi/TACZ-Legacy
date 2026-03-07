package com.tacz.legacy.client.renderer.item

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.client.model.SlotModel
import com.tacz.legacy.client.model.TACZPerspectiveAwareBakedModel
import com.tacz.legacy.client.model.bedrock.BedrockModel
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.resource.pojo.display.gun.GunDisplay
import com.tacz.legacy.client.resource.pojo.display.gun.GunTransform
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * TEISR (TileEntityItemStackRenderer) for gun items.
 *
 * Context-aware rendering via [TACZPerspectiveAwareBakedModel]:
 * - **Item presentation contexts** (GUI, dropped, fixed, head) → flat slot texture
 * - **First person** → skipped (handled by FirstPersonRenderGunEvent)
 * - **Third person right hand** → 3D bedrock model with gun pack texture
 * - **Third person left hand** → skipped (upstream convention)
 */
@SideOnly(Side.CLIENT)
internal object TACZGunItemRenderer : TileEntityItemStackRenderer() {
    private val SLOT_MODEL = SlotModel()

    override fun renderByItem(stack: ItemStack) {
        renderByItem(stack, 1.0f)
    }

    override fun renderByItem(stack: ItemStack, partialTicks: Float) {
        val item = stack.item
        if (item !is IGun) return

        val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
        val gunId = item.getGunId(stack)

        val displayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: return
        val display: GunDisplay = TACZClientAssetManager.getGunDisplay(displayId) ?: return

        val transformType = TACZPerspectiveAwareBakedModel.getCurrentTransformType()

        // First person is handled by FirstPersonRenderGunEvent
        if (transformType == ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND ||
            transformType == ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND) {
            return
        }

        // Third person left hand — skip per upstream convention
        if (transformType == ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND) {
            return
        }

        // Item presentation contexts → flat slot texture
        if (TACZPerspectiveAwareBakedModel.isItemPresentationContext(transformType)) {
            renderSlotTexture(display)
            return
        }

        // Third person right hand → 3D model
        renderGunModel(display)
    }

    private fun renderSlotTexture(display: GunDisplay) {
        val slotTexLoc = display.slotTextureLocation ?: return
        val registeredSlot = TACZClientAssetManager.getTextureLocation(slotTexLoc) ?: return
        Minecraft.getMinecraft().textureManager.bindTexture(registeredSlot)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5f, 1.5f, 0.5f)
        GlStateManager.rotate(180f, 0f, 0f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        SLOT_MODEL.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }

    private fun renderGunModel(display: GunDisplay) {
        val modelLocation: ResourceLocation = display.modelLocation ?: return
        val modelData = TACZClientAssetManager.getModel(modelLocation) ?: return
        val model = BedrockModel(modelData.pojo, modelData.version)

        val textureLocation: ResourceLocation = display.modelTexture ?: return
        val registeredTexture: ResourceLocation = TACZClientAssetManager.getTextureLocation(textureLocation) ?: return

        Minecraft.getMinecraft().textureManager.bindTexture(registeredTexture)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.5f, 2.0f, 0.5f)
        GlStateManager.scale(-1f, -1f, 1f)

        GlStateManager.enableLighting()
        GlStateManager.enableRescaleNormal()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO,
        )

        model.render()

        GlStateManager.disableBlend()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
    }
}

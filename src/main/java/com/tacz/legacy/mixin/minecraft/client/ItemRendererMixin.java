package com.tacz.legacy.mixin.minecraft.client;

import com.tacz.legacy.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the equip animation (hand bobbing down and up) when switching between guns.
 * Port of upstream TACZ ItemInHandRendererMixin, adapted for 1.12.2 ItemRenderer.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Shadow
    private float equippedProgressMainHand;
    @Shadow
    private float prevEquippedProgressMainHand;
    @Shadow
    private ItemStack itemStackMainHand;

    @Inject(method = "updateEquippedItem", at = @At("HEAD"))
    private void cancelGunEquipAnimation(CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }
        ItemStack itemStack = player.getHeldItemMainhand();
        if (itemStack.getItem() instanceof IGun) {
            equippedProgressMainHand = 1.0f;
            prevEquippedProgressMainHand = 1.0f;
            itemStackMainHand = itemStack;
        }
    }
}

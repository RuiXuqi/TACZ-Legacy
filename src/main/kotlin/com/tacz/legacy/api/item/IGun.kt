package com.tacz.legacy.api.item

import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

public interface IGun {
    public fun getGunId(stack: ItemStack): ResourceLocation

    public fun setAttachmentLock(stack: ItemStack, locked: Boolean): Unit

    public fun setDummyAmmoAmount(stack: ItemStack, amount: Int): Unit

    public companion object {
        public const val ATTACHMENT_LOCK_TAG: String = "AttachmentLock"
        public const val DUMMY_AMMO_TAG: String = "DummyAmmo"

        @JvmStatic
        public fun getIGunOrNull(stack: ItemStack): IGun? = stack.item as? IGun
    }
}

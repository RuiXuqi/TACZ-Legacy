package com.tacz.legacy.common.item

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

public object GunTooltipPart {
    public const val HIDE_FLAGS_TAG: String = "HideTooltipPartMask"

    @JvmStatic
    public fun setHideFlags(stack: ItemStack, mask: Int): Unit {
        ensureTag(stack).setInteger(HIDE_FLAGS_TAG, mask)
    }

    @JvmStatic
    public fun getHideFlags(stack: ItemStack): Int = stack.tagCompound?.getInteger(HIDE_FLAGS_TAG) ?: 0

    private fun ensureTag(stack: ItemStack): NBTTagCompound {
        val existing: NBTTagCompound? = stack.tagCompound
        if (existing != null) {
            return existing
        }
        val created = NBTTagCompound()
        stack.tagCompound = created
        return created
    }
}

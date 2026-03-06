package com.tacz.legacy.common.registry

import com.tacz.legacy.common.item.LegacyItems
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.ItemStack

internal object LegacyCreativeTabs {
    internal val GUNS: CreativeTabs = object : CreativeTabs("tacz.guns") {
        override fun createIcon(): ItemStack = ItemStack(LegacyItems.MODERN_KINETIC_GUN)
    }

    internal val AMMO: CreativeTabs = object : CreativeTabs("tacz.ammo") {
        override fun createIcon(): ItemStack = ItemStack(LegacyItems.AMMO)
    }

    internal val PARTS: CreativeTabs = object : CreativeTabs("tacz.parts") {
        override fun createIcon(): ItemStack = ItemStack(LegacyItems.ATTACHMENT)
    }

    internal val DECORATION: CreativeTabs = object : CreativeTabs("tacz.decoration") {
        override fun createIcon(): ItemStack = ItemStack(LegacyItems.GUN_SMITH_TABLE)
    }
}

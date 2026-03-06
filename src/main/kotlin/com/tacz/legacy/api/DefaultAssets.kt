package com.tacz.legacy.api

import net.minecraft.util.ResourceLocation

public object DefaultAssets {
    @JvmField
    public val DEFAULT_BLOCK_ID: ResourceLocation = ResourceLocation("tacz", "gun_smith_table")

    @JvmField
    public val DEFAULT_GUN_ID: ResourceLocation = ResourceLocation("tacz", "modern_kinetic")

    @JvmField
    public val DEFAULT_ATTACHMENT_ID: ResourceLocation = ResourceLocation("tacz", "attachment")

    @JvmField
    public val DEFAULT_AMMO_ID: ResourceLocation = ResourceLocation("tacz", "ammo")
}

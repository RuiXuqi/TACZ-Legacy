package com.tacz.legacy.common.entity

import com.tacz.legacy.TACZLegacy
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityMinecartEmpty
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.EntityEntry
import net.minecraftforge.fml.common.registry.EntityEntryBuilder
import net.minecraftforge.registries.IForgeRegistry

internal object LegacyEntities {
    internal val BULLET: EntityEntry = EntityEntryBuilder.create<EntityKineticBullet>()
        .entity(EntityKineticBullet::class.java)
        .id(ResourceLocation(TACZLegacy.MOD_ID, "bullet"), 0)
        .name("bullet")
        .tracker(64, 1, true)
        .build()

    internal val TARGET_MINECART: EntityEntry = EntityEntryBuilder.create<TargetMinecartEntity>()
        .entity(TargetMinecartEntity::class.java)
        .id(ResourceLocation(TACZLegacy.MOD_ID, "target_minecart"), 1)
        .name("target_minecart")
        .tracker(64, 1, true)
        .build()

    internal fun registerAll(registry: IForgeRegistry<EntityEntry>): Unit {
        registry.register(BULLET)
        registry.register(TARGET_MINECART)
    }
}

internal class EntityKineticBullet(worldIn: World) : Entity(worldIn) {
    init {
        setSize(0.25f, 0.25f)
    }

    override fun entityInit(): Unit = Unit

    override fun readEntityFromNBT(compound: NBTTagCompound): Unit = Unit

    override fun writeEntityToNBT(compound: NBTTagCompound): Unit = Unit

    override fun onUpdate(): Unit {
        super.onUpdate()
        if (!world.isRemote && ticksExisted > 40) {
            setDead()
        }
    }
}

internal class TargetMinecartEntity : EntityMinecartEmpty {
    constructor(worldIn: World) : super(worldIn)

    constructor(worldIn: World, x: Double, y: Double, z: Double) : super(worldIn, x, y, z)
}

package com.tacz.legacy.common.block.entity

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.GameRegistry

internal object LegacyBlockEntities {
    private var registered: Boolean = false

    internal fun registerAll(): Unit {
        if (registered) {
            return
        }
        GameRegistry.registerTileEntity(GunSmithTableTileEntity::class.java, ResourceLocation(TACZLegacy.MOD_ID, "gun_smith_table"))
        GameRegistry.registerTileEntity(TargetTileEntity::class.java, ResourceLocation(TACZLegacy.MOD_ID, "target"))
        GameRegistry.registerTileEntity(StatueTileEntity::class.java, ResourceLocation(TACZLegacy.MOD_ID, "statue"))
        registered = true
    }
}

internal class GunSmithTableTileEntity : TileEntity() {
    internal var blockId: ResourceLocation = DefaultAssets.DEFAULT_BLOCK_ID

    override fun readFromNBT(compound: NBTTagCompound): Unit {
        super.readFromNBT(compound)
        if (compound.hasKey("BlockId")) {
            blockId = ResourceLocation(compound.getString("BlockId"))
        }
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val tag = super.writeToNBT(compound)
        tag.setString("BlockId", blockId.toString())
        return tag
    }
}

internal class TargetTileEntity : TileEntity() {
    internal var triggered: Boolean = false

    internal fun trigger(): Unit {
        triggered = true
        markDirty()
    }

    internal fun reset(): Unit {
        triggered = false
        markDirty()
    }

    override fun readFromNBT(compound: NBTTagCompound): Unit {
        super.readFromNBT(compound)
        triggered = compound.getBoolean("Triggered")
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val tag = super.writeToNBT(compound)
        tag.setBoolean("Triggered", triggered)
        return tag
    }
}

internal class StatueTileEntity : TileEntity() {
    internal var storedItem: ItemStack = ItemStack.EMPTY

    internal fun store(stack: ItemStack): Unit {
        storedItem = stack.copy()
        markDirty()
    }

    internal fun clear(): Unit {
        storedItem = ItemStack.EMPTY
        markDirty()
    }

    override fun readFromNBT(compound: NBTTagCompound): Unit {
        super.readFromNBT(compound)
        storedItem = if (compound.hasKey("StoredItem")) {
            ItemStack(compound.getCompoundTag("StoredItem"))
        } else {
            ItemStack.EMPTY
        }
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        val tag = super.writeToNBT(compound)
        if (!storedItem.isEmpty) {
            tag.setTag("StoredItem", storedItem.writeToNBT(NBTTagCompound()))
        }
        return tag
    }
}

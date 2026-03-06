package com.tacz.legacy.common.item

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.GunItemManager
import com.tacz.legacy.common.block.LegacyBlocks
import com.tacz.legacy.common.registry.LegacyCreativeTabs
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.registries.IForgeRegistry

internal object LegacyItems {
    internal val MODERN_KINETIC_GUN: ModernKineticGunItem = ModernKineticGunItem().named("modern_kinetic_gun", LegacyCreativeTabs.GUNS)
    internal val AMMO: LegacySimpleItem = LegacySimpleItem().named("ammo", LegacyCreativeTabs.AMMO)
    internal val ATTACHMENT: LegacySimpleItem = LegacySimpleItem().named("attachment", LegacyCreativeTabs.PARTS)
    internal val AMMO_BOX: LegacySimpleItem = LegacySimpleItem(maxStackSize = 1).named("ammo_box", LegacyCreativeTabs.AMMO)
    internal val TARGET_MINECART: LegacySimpleItem = LegacySimpleItem(maxStackSize = 1).named("target_minecart", LegacyCreativeTabs.DECORATION)

    internal val GUN_SMITH_TABLE: ItemBlock = createBlockItem(LegacyBlocks.GUN_SMITH_TABLE)
    internal val WORKBENCH_A: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_A)
    internal val WORKBENCH_B: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_B)
    internal val WORKBENCH_C: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_C)
    internal val TARGET: ItemBlock = createBlockItem(LegacyBlocks.TARGET)
    internal val STATUE: ItemBlock = createBlockItem(LegacyBlocks.STATUE)

    internal val allItems: List<Item> = listOf(
        MODERN_KINETIC_GUN,
        AMMO,
        ATTACHMENT,
        AMMO_BOX,
        TARGET_MINECART,
        GUN_SMITH_TABLE,
        WORKBENCH_A,
        WORKBENCH_B,
        WORKBENCH_C,
        TARGET,
        STATUE,
    )

    internal fun registerAll(registry: IForgeRegistry<Item>): Unit {
        allItems.forEach(registry::register)
        GunItemManager.registerGunItem(ModernKineticGunItem.TYPE_NAME, MODERN_KINETIC_GUN)
    }

    private fun createBlockItem(block: net.minecraft.block.Block): ItemBlock = ItemBlock(block).apply {
        registryName = requireNotNull(block.registryName)
        setTranslationKey("${TACZLegacy.MOD_ID}.${requireNotNull(block.registryName).path}")
        setCreativeTab(LegacyCreativeTabs.DECORATION)
    }

    private fun <T : Item> T.named(path: String, tab: CreativeTabs): T {
        registryName = ResourceLocation(TACZLegacy.MOD_ID, path)
        setTranslationKey("${TACZLegacy.MOD_ID}.$path")
        setCreativeTab(tab)
        return this
    }
}

internal open class LegacySimpleItem(maxStackSize: Int = 64) : Item() {
    init {
        this.maxStackSize = maxStackSize
    }
}

internal class ModernKineticGunItem : Item(), IGun {
    init {
        maxStackSize = 1
    }

    override fun getGunId(stack: ItemStack): ResourceLocation = DefaultAssets.DEFAULT_GUN_ID

    override fun setAttachmentLock(stack: ItemStack, locked: Boolean): Unit {
        ensureTag(stack).setBoolean(IGun.ATTACHMENT_LOCK_TAG, locked)
    }

    override fun setDummyAmmoAmount(stack: ItemStack, amount: Int): Unit {
        ensureTag(stack).setInteger(IGun.DUMMY_AMMO_TAG, amount.coerceAtLeast(0))
    }

    internal companion object {
        internal const val TYPE_NAME: String = "modern_kinetic"
    }
}

private fun ensureTag(stack: ItemStack): NBTTagCompound {
    val existing = stack.tagCompound
    if (existing != null) {
        return existing
    }
    val created = NBTTagCompound()
    stack.tagCompound = created
    return created
}

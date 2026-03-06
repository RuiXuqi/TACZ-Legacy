package com.tacz.legacy.client.registry

import com.tacz.legacy.common.item.LegacyItems
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.item.Item
import net.minecraftforge.client.event.ModelRegistryEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
internal object ModelRegisterer {
    @SubscribeEvent
    internal fun onModelRegister(event: ModelRegistryEvent): Unit {
        LegacyItems.allItems.forEach(::registerInventoryModel)
    }

    private fun registerInventoryModel(item: Item): Unit {
        val id = requireNotNull(item.registryName)
        ModelLoader.setCustomModelResourceLocation(item, 0, ModelResourceLocation(id, "inventory"))
    }
}

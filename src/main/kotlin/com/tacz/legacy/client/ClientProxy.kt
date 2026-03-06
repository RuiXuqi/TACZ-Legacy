package com.tacz.legacy.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.CommonProxy
import com.tacz.legacy.common.foundation.BootstrapDiagnostics
import com.tacz.legacy.common.foundation.BootstrapStep
import com.tacz.legacy.client.particle.LegacyParticleFactoryRegistry
import com.tacz.legacy.client.registry.ModelRegisterer
import net.minecraftforge.common.MinecraftForge

internal class ClientProxy : CommonProxy() {
    init {
        MinecraftForge.EVENT_BUS.register(ModelRegisterer)
        MinecraftForge.EVENT_BUS.register(ClientConfigEventHandler)
    }

    override fun init(): Unit {
        super.init()
        LegacyParticleFactoryRegistry.register()
        BootstrapDiagnostics.record(BootstrapStep.CLIENT_RUNTIME_READY)
        TACZLegacy.logger.info("[FoundationSmoke] CLIENT runtime hooks ready.")
    }
}

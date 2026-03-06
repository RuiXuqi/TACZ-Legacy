package com.tacz.legacy.client.particle

import com.tacz.legacy.common.config.LegacyConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.IParticleFactory
import net.minecraft.client.particle.Particle
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
internal object LegacyParticleFactoryRegistry {
    private const val BULLET_HOLE_ID: Int = 2001

    internal fun register(): Unit {
        Minecraft.getMinecraft().effectRenderer.registerParticle(BULLET_HOLE_ID, BulletHoleParticleFactory())
    }
}

@SideOnly(Side.CLIENT)
internal class BulletHoleParticleFactory : IParticleFactory {
    override fun createParticle(
        particleID: Int,
        worldIn: World,
        xCoordIn: Double,
        yCoordIn: Double,
        zCoordIn: Double,
        xSpeedIn: Double,
        ySpeedIn: Double,
        zSpeedIn: Double,
        vararg parameters: Int,
    ): Particle = BulletHoleParticle(worldIn, xCoordIn, yCoordIn, zCoordIn)
}

@SideOnly(Side.CLIENT)
internal class BulletHoleParticle(
    worldIn: World,
    xCoordIn: Double,
    yCoordIn: Double,
    zCoordIn: Double,
) : Particle(worldIn, xCoordIn, yCoordIn, zCoordIn) {
    init {
        canCollide = false
        particleMaxAge = LegacyConfigManager.client.bulletHoleParticleLife.coerceAtLeast(1)
        particleAlpha = 1.0f
        particleScale = 0.1f
        setParticleTextureIndex(0)
    }

    override fun onUpdate(): Unit {
        super.onUpdate()
        val threshold = LegacyConfigManager.client.bulletHoleParticleFadeThreshold.coerceIn(0.0, 1.0)
        val progress = particleAge.toDouble() / particleMaxAge.toDouble()
        if (progress >= threshold && threshold < 1.0) {
            val remaining = ((1.0 - progress) / (1.0 - threshold)).coerceIn(0.0, 1.0)
            particleAlpha = remaining.toFloat()
        }
    }
}

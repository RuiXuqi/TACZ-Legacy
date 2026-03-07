package com.tacz.legacy.common.entity

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.resource.BulletCombatData
import com.tacz.legacy.common.resource.DistanceDamagePoint
import com.tacz.legacy.common.config.HeadShotAabbConfigRead
import com.tacz.legacy.common.config.LegacyConfigManager
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityList
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityMinecartEmpty
import net.minecraft.entity.projectile.EntityThrowable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.init.Blocks
import net.minecraft.util.DamageSource
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.fml.common.registry.EntityEntry
import net.minecraftforge.fml.common.registry.EntityEntryBuilder
import net.minecraftforge.registries.IForgeRegistry
import kotlin.math.max
import kotlin.math.sqrt

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

internal class EntityKineticBullet : EntityThrowable {
    private var damage: Float = 5.0f
    private var bulletSpeed: Float = 5.0f
    private var bulletGravity: Float = 0.0f
    private var friction: Float = 0.01f
    private var pierce: Int = 1
    private var lifespan: Int = 200
    private var knockback: Float = 0.0f
    private var igniteEntity: Boolean = false
    private var igniteEntityTime: Int = 2
    private var igniteBlock: Boolean = false
    private var isTracerAmmo: Boolean = false
    private var damageModifier: Float = 1.0f
    private var armorIgnore: Float = 0.0f
    private var headShotMultiplier: Float = 1.0f
    private val damageAdjust: MutableList<DistanceDamagePoint> = mutableListOf()
    private var startPosX: Double = 0.0
    private var startPosY: Double = 0.0
    private var startPosZ: Double = 0.0

    // Explosion properties
    private var hasExplosion: Boolean = false
    private var explosionRadius: Float = 0f
    private var explosionDamage: Float = 0f
    private var explosionKnockback: Boolean = false
    private var explosionDestroyBlock: Boolean = false
    private var explosionDelay: Float = 0f

    /** 发射的枪械 ID，供下游渲染/音效使用 */
    internal var gunId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "unknown")
        private set
    /** 枪械 display ID，供下游渲染使用 */
    internal var gunDisplayId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "default")
        private set
    /** 弹药 ID，供下游渲染/特效使用  */
    internal var ammoId: ResourceLocation = ResourceLocation(TACZLegacy.MOD_ID, "empty")
        private set

    /** NBT / network deserialization constructor */
    constructor(worldIn: World) : super(worldIn) {
        setSize(0.1f, 0.1f)
    }

    /** 数据驱动构造器：从 BulletCombatData 获取所有弹道参数 */
    constructor(
        worldIn: World,
        shooter: EntityLivingBase,
        bulletData: BulletCombatData,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        ammoId: ResourceLocation,
        isTracer: Boolean,
    ) : super(worldIn, shooter) {
        setSize(0.1f, 0.1f)
        this.damage = bulletData.damage
        this.bulletSpeed = bulletData.speed
        this.bulletGravity = bulletData.gravity
        this.friction = bulletData.friction
        this.pierce = bulletData.pierce
        this.lifespan = bulletData.getLifeTicks()
        this.knockback = bulletData.knockback
        this.igniteEntity = bulletData.igniteEntity
        this.igniteEntityTime = bulletData.igniteEntityTime
        this.igniteBlock = bulletData.igniteBlock
        this.isTracerAmmo = isTracer
        this.gunId = gunId
        this.gunDisplayId = gunDisplayId
        this.ammoId = ammoId
        this.startPosX = posX
        this.startPosY = posY
        this.startPosZ = posZ

        val extraDamage = bulletData.extraDamageData
        this.armorIgnore = ((extraDamage?.armorIgnore ?: 0.0f) * LegacyConfigManager.server.armorIgnoreBaseMultiplier.toFloat())
            .coerceIn(0.0f, 1.0f)
        this.headShotMultiplier = max((extraDamage?.headShotMultiplier ?: 1.0f) * LegacyConfigManager.server.headShotBaseMultiplier.toFloat(), 0.0f)
        this.damageAdjust.clear()
        this.damageAdjust.addAll(extraDamage?.damageAdjust.orEmpty())
        
        val exp = bulletData.explosionData
        if (exp != null && exp.explode) {
            this.hasExplosion = true
            this.explosionRadius = exp.radius
            this.explosionDamage = exp.damage
            this.explosionKnockback = exp.knockback
            this.explosionDestroyBlock = exp.destroyBlock
            this.explosionDelay = if (exp.delay < 0.0f) Float.POSITIVE_INFINITY else exp.delay
        }
    }

    /** 霰弹伤害分摊。与上游 applyShotgunDamageSpread 一致。 */
    internal fun applyShotgunDamageSpread(bulletCount: Int) {
        if (bulletCount > 1) {
            damageModifier = 1.0f / bulletCount
        }
    }

    /** 获取实际伤害（含霰弹分摊） */
    internal fun getEffectiveDamage(): Float = resolveEffectiveDamageAt(Vec3d(posX, posY, posZ))

    internal fun isTracerAmmo(): Boolean = isTracerAmmo

    override fun getGravityVelocity(): Float = bulletGravity

    override fun onImpact(result: RayTraceResult) {
        if (world.isRemote) return

        when (result.typeOfHit) {
            RayTraceResult.Type.ENTITY -> {
                val target = result.entityHit ?: return
                val hitPos = result.hitVec ?: Vec3d(target.posX, target.posY + target.height * 0.5, target.posZ)
                if (target != thrower) {
                    applyDirectHitDamage(target, hitPos)
                }
                if (hasExplosion) {
                    triggerExplosion(hitPos)
                    setDead()
                    return
                }
                pierce--
                if (pierce <= 0) setDead()
            }
            RayTraceResult.Type.BLOCK -> {
                val hitPos = result.hitVec ?: Vec3d(posX, posY, posZ)
                if (hasExplosion) {
                    triggerExplosion(hitPos)
                    setDead()
                    return
                }
                if (igniteBlock && thrower != null) {
                    val pos = result.blockPos.offset(result.sideHit)
                    if (world.isAirBlock(pos) && LegacyConfigManager.common.igniteBlock) {
                        world.setBlockState(pos, Blocks.FIRE.defaultState)
                    }
                }
                setDead()
            }
            else -> {}
        }
    }

    override fun writeEntityToNBT(compound: NBTTagCompound) {
        super.writeEntityToNBT(compound)
        compound.setFloat("Damage", damage)
        compound.setFloat("Speed", bulletSpeed)
        compound.setFloat("Gravity", bulletGravity)
        compound.setFloat("Friction", friction)
        compound.setInteger("Pierce", pierce)
        compound.setFloat("Knockback", knockback)
        compound.setBoolean("IgniteEntity", igniteEntity)
        compound.setInteger("IgniteEntityTime", igniteEntityTime)
        compound.setBoolean("IgniteBlock", igniteBlock)
        compound.setBoolean("IsTracer", isTracerAmmo)
        compound.setFloat("ArmorIgnore", armorIgnore)
        compound.setFloat("HeadShotMultiplier", headShotMultiplier)
        compound.setDouble("StartPosX", startPosX)
        compound.setDouble("StartPosY", startPosY)
        compound.setDouble("StartPosZ", startPosZ)
        val damageAdjustList = NBTTagList()
        damageAdjust.forEach { pair ->
            val entry = NBTTagCompound()
            entry.setFloat("Distance", pair.distance)
            entry.setFloat("Damage", pair.damage)
            damageAdjustList.appendTag(entry)
        }
        compound.setTag("DamageAdjust", damageAdjustList)
        
        compound.setBoolean("HasExplosion", hasExplosion)
        if (hasExplosion) {
            compound.setFloat("ExplosionRadius", explosionRadius)
            compound.setFloat("ExplosionDamage", explosionDamage)
            compound.setBoolean("ExplosionKnockback", explosionKnockback)
            compound.setBoolean("ExplosionDestroyBlock", explosionDestroyBlock)
            compound.setBoolean("ExplosionDelayInfinite", explosionDelay.isInfinite())
            if (!explosionDelay.isInfinite()) {
                compound.setFloat("ExplosionDelay", explosionDelay)
            }
        }
        compound.setString("GunId", gunId.toString())
        compound.setString("GunDisplayId", gunDisplayId.toString())
        compound.setString("AmmoId", ammoId.toString())
        compound.setFloat("DamageModifier", damageModifier)
    }

    private fun triggerExplosion(position: Vec3d = Vec3d(posX, posY, posZ)) {
        LegacyProjectileExplosionHelper.createExplosion(
            owner = thrower,
            exploder = this,
            x = position.x,
            y = position.y,
            z = position.z,
            damage = explosionDamage,
            radius = explosionRadius,
            knockback = explosionKnockback,
            destroyBlock = explosionDestroyBlock,
        )
    }

    override fun readEntityFromNBT(compound: NBTTagCompound) {
        super.readEntityFromNBT(compound)
        damage = compound.getFloat("Damage")
        bulletSpeed = compound.getFloat("Speed")
        bulletGravity = compound.getFloat("Gravity")
        friction = if (compound.hasKey("Friction")) compound.getFloat("Friction") else 0.01f
        pierce = compound.getInteger("Pierce")
        knockback = if (compound.hasKey("Knockback")) compound.getFloat("Knockback") else 0.0f
        igniteEntity = compound.getBoolean("IgniteEntity")
        igniteEntityTime = if (compound.hasKey("IgniteEntityTime")) compound.getInteger("IgniteEntityTime") else 2
        igniteBlock = compound.getBoolean("IgniteBlock")
        isTracerAmmo = compound.getBoolean("IsTracer")
        armorIgnore = if (compound.hasKey("ArmorIgnore")) compound.getFloat("ArmorIgnore") else 0.0f
        headShotMultiplier = if (compound.hasKey("HeadShotMultiplier")) compound.getFloat("HeadShotMultiplier") else 1.0f
        startPosX = if (compound.hasKey("StartPosX")) compound.getDouble("StartPosX") else posX
        startPosY = if (compound.hasKey("StartPosY")) compound.getDouble("StartPosY") else posY
        startPosZ = if (compound.hasKey("StartPosZ")) compound.getDouble("StartPosZ") else posZ
        damageAdjust.clear()
        if (compound.hasKey("DamageAdjust")) {
            val list = compound.getTagList("DamageAdjust", 10)
            for (index in 0 until list.tagCount()) {
                val entry = list.getCompoundTagAt(index)
                damageAdjust += DistanceDamagePoint(
                    distance = entry.getFloat("Distance"),
                    damage = entry.getFloat("Damage"),
                )
            }
        }
        
        hasExplosion = compound.getBoolean("HasExplosion")
        if (hasExplosion) {
            explosionRadius = compound.getFloat("ExplosionRadius")
            explosionDamage = compound.getFloat("ExplosionDamage")
            explosionKnockback = compound.getBoolean("ExplosionKnockback")
            explosionDestroyBlock = compound.getBoolean("ExplosionDestroyBlock")
            explosionDelay = if (compound.getBoolean("ExplosionDelayInfinite")) {
                Float.POSITIVE_INFINITY
            } else {
                compound.getFloat("ExplosionDelay")
            }
        }
        if (compound.hasKey("GunId")) gunId = ResourceLocation(compound.getString("GunId"))
        if (compound.hasKey("GunDisplayId")) gunDisplayId = ResourceLocation(compound.getString("GunDisplayId"))
        if (compound.hasKey("AmmoId")) ammoId = ResourceLocation(compound.getString("AmmoId"))
        damageModifier = if (compound.hasKey("DamageModifier")) compound.getFloat("DamageModifier") else 1.0f
    }

    override fun onUpdate() {
        super.onUpdate()
        // 空气阻力
        if (friction > 0.0f) {
            val mov = motionX * motionX + motionY * motionY + motionZ * motionZ
            if (mov > 0.0001) {
                val factor = (1.0 - friction).coerceIn(0.0, 1.0)
                motionX *= factor
                motionY *= factor
                motionZ *= factor
            }
        }
        
        if (hasExplosion && explosionDelay.isFinite()) {
            explosionDelay -= 0.05f // 1 tick = 0.05s
            if (explosionDelay <= 0 && !isDead) {
                if (!world.isRemote) {
                    triggerExplosion()
                }
                setDead()
            }
        }
        
        lifespan--
        if (lifespan <= 0) setDead()
    }

    private fun applyDirectHitDamage(target: Entity, hitPosition: Vec3d) {
        val totalDamage = resolveEffectiveDamageAt(hitPosition)
        if (totalDamage <= 0.0f) {
            return
        }
        val isHeadShot = target is EntityLivingBase && isHeadShot(target, hitPosition)
        val headShotDamage = if (isHeadShot) totalDamage * headShotMultiplier else totalDamage
        val damageSplit = splitDamage(headShotDamage)
        if (target is EntityLivingBase) {
            target.hurtResistantTime = 0
        }
        if (damageSplit.first > 0.0f) {
            target.attackEntityFrom(createBulletDamageSource(ignoreArmor = false), damageSplit.first)
        }
        if (damageSplit.second > 0.0f) {
            if (target is EntityLivingBase) {
                target.hurtResistantTime = 0
            }
            target.attackEntityFrom(createBulletDamageSource(ignoreArmor = true), damageSplit.second)
        }
        applyImpactKnockback(target)
        if (igniteEntity && !target.world.isRemote && LegacyConfigManager.common.igniteEntity) {
            target.setFire(igniteEntityTime)
        }
    }

    private fun createBulletDamageSource(ignoreArmor: Boolean): DamageSource {
        val damageSource = DamageSource.causeThrownDamage(this, thrower).setProjectile()
        return if (ignoreArmor) damageSource.setDamageBypassesArmor() else damageSource
    }

    private fun applyImpactKnockback(target: Entity) {
        if (knockback <= 0.0f) {
            return
        }
        val horizontalSpeed = sqrt(motionX * motionX + motionZ * motionZ)
        if (horizontalSpeed <= 1.0E-6) {
            return
        }
        val normX = motionX / horizontalSpeed
        val normZ = motionZ / horizontalSpeed
        target.addVelocity(normX * knockback * 0.6, 0.1, normZ * knockback * 0.6)
        target.velocityChanged = true
    }

    private fun resolveEffectiveDamageAt(hitPosition: Vec3d): Float {
        val origin = Vec3d(startPosX, startPosY, startPosZ)
        val travelDistance = origin.distanceTo(hitPosition)
        val adjustedDamage = if (damageAdjust.isEmpty()) {
            damage
        } else {
            damageAdjust.firstOrNull { travelDistance < it.distance || it.distance.isInfinite() }?.damage
                ?: damageAdjust.last().damage
        }
        return (adjustedDamage * LegacyConfigManager.server.damageBaseMultiplier.toFloat() * damageModifier)
            .coerceAtLeast(0.0f)
    }

    private fun splitDamage(totalDamage: Float): Pair<Float, Float> {
        val armorPiercingDamage = (totalDamage * armorIgnore.coerceIn(0.0f, 1.0f)).coerceAtLeast(0.0f)
        val normalDamage = (totalDamage - armorPiercingDamage).coerceAtLeast(0.0f)
        return normalDamage to armorPiercingDamage
    }

    private fun isHeadShot(target: EntityLivingBase, hitPosition: Vec3d): Boolean {
        return resolveHeadShotBox(target).contains(hitPosition)
    }

    private fun resolveHeadShotBox(target: EntityLivingBase): AxisAlignedBB {
        val configured = EntityList.getKey(target)?.let(HeadShotAabbConfigRead::getAabb)
        if (configured != null) {
            return configured.offset(target.posX, target.posY, target.posZ).grow(0.01)
        }
        val halfWidth = target.width / 2.0
        return AxisAlignedBB(
            target.posX - halfWidth,
            target.posY + target.eyeHeight - 0.25,
            target.posZ - halfWidth,
            target.posX + halfWidth,
            target.posY + target.eyeHeight + 0.25,
            target.posZ + halfWidth,
        ).grow(0.01)
    }
}

internal class TargetMinecartEntity : EntityMinecartEmpty {
    constructor(worldIn: World) : super(worldIn)

    constructor(worldIn: World, x: Double, y: Double, z: Double) : super(worldIn, x, y, z)
}

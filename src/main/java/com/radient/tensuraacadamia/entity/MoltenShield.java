package com.radient.tensuraacadamia.entity;

import com.github.hvnbael.trnightmare.main.uniques.BreakerSkill;
import com.github.hvnbael.trnightmare.util.BreakerHelper;
import com.radient.tensuraacadamia.ability.unique.quirks.AlchemyQuirk;
import com.radient.tensuraacadamia.mixin.AlchemyProjectileImpactInvoker;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.entity.magic.barrier.RangedBarrierEntity;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class MoltenShield extends Mob {
    public static final float MAX_HEALTH = 5_000;
    public static final int MAX_REPAIRS = 5;
    private static final EntityDataAccessor<Integer> SPAN = SynchedEntityData.defineId(MoltenShield.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NORMAL = SynchedEntityData.defineId(MoltenShield.class, EntityDataSerializers.INT);
    private UUID creator;
    private double savedAura, threshold;
    private long expiresAt;
    private int repairs;
    public MoltenShield(EntityType<? extends MoltenShield> type, Level level) {
        super(type, level); setNoAi(true); setNoGravity(true); setPersistenceRequired(); noPhysics = true; xpReward = 0;
        TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
        setHealth(getMaxHealth());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(SPAN, 5); builder.define(NORMAL, Direction.NORTH.get3DDataValue());
    }
    public Direction normal() { return Direction.from3DDataValue(entityData.get(NORMAL)); }
    public int span() { return entityData.get(SPAN); }
    public void configure(LivingEntity owner, Vec3 center, Direction normal, int span, boolean mastered) {
        creator = owner.getUUID(); savedAura = EnergyHelper.getMaxAura(owner); threshold = mastered ? 2 : 1.5;
        expiresAt = level().getGameTime() + AlchemyQuirk.WALL_LIFETIME;
        entityData.set(SPAN, span); entityData.set(NORMAL, normal.get3DDataValue());
        setPos(center.x, center.y - (normal.getAxis() == Direction.Axis.Y ? 0.25 : span / 2.0), center.z);
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return entityData == null ? super.getDefaultDimensions(pose) : EntityDimensions.scalable(span(), normal().getAxis() == Direction.Axis.Y ? 0.5F : span());
    }
    @Override protected AABB makeBoundingBox() {
        if (entityData == null) return super.makeBoundingBox();
        double x = normal().getAxis() == Direction.Axis.X ? 0.5 : span(), y = normal().getAxis() == Direction.Axis.Y ? 0.5 : span(), z = normal().getAxis() == Direction.Axis.Z ? 0.5 : span();
        return new AABB(getX() - x / 2, getY(), getZ() - z / 2, getX() + x / 2, getY() + y, getZ() + z / 2);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) { super.onSyncedDataUpdated(key); if (key == SPAN || key == NORMAL) refreshDimensions(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override protected void dropAllDeathLoot(ServerLevel server, DamageSource source) { }
    @Override public void tick() { super.tick(); setDeltaMovement(Vec3.ZERO); if (!level().isClientSide && level().getGameTime() >= expiresAt) discard(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAlive()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
            return true;
        }
        if (breaksFor(source.getEntity(), source)) {
            shatter();
            return true;
        }
        if (!Float.isFinite(amount) || amount <= 0) return false;
        setHealth(Math.max(0, getHealth() - amount));
        if (getHealth() <= 0) shatter();
        return true;
    }
    public static boolean overpowers(double attackerAura, double ownerAura, double threshold) { return attackerAura > ownerAura * threshold; }
    private boolean breaksFor(Entity attacker, DamageSource source) {
        if (source instanceof TensuraDamageSource typed && typed.tensura$getBarrierBypassLevel() > 0) return true;
        if (!(attacker instanceof LivingEntity living)) return false;
        Entity owner = level() instanceof ServerLevel server && creator != null ? server.getEntity(creator) : null;
        double aura = owner instanceof LivingEntity caster ? EnergyHelper.getMaxAura(caster) : savedAura;
        var breaker = SkillAPI.getSkillsFrom(living).getSkill(BreakerSkill.BREAKER).orElse(null);
        return overpowers(EnergyHelper.getMaxAura(living), aura, threshold)
                || RangedBarrierEntity.shouldInstaBreak(living, owner)
                || breaker != null && BreakerHelper.isBoundaryBreakerInSlot(living, breaker);
    }
    private boolean shatter() {
        if (isRemoved()) return true;
        if (repairs < MAX_REPAIRS && level() instanceof ServerLevel server && creator != null
                && server.getEntity(creator) instanceof LivingEntity owner && owner.isAlive()) {
            var instance = AlchemyQuirk.instance(owner);
            if (instance != null && AlchemyQuirk.spend(instance, owner, span() * span())) {
                repairs++;
                setHealth(getMaxHealth());
                level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1, 1.2F);
                Vec3 center = getBoundingBox().getCenter();
                server.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                return false;
            }
        }
        level().playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1, 0.6F);
        discard();
        return true;
    }
    private boolean canRepair() {
        if (repairs >= MAX_REPAIRS || !(level() instanceof ServerLevel server) || creator == null
                || !(server.getEntity(creator) instanceof LivingEntity owner) || !owner.isAlive()) return false;
        var instance = AlchemyQuirk.instance(owner);
        return instance != null && AlchemyQuirk.points(instance) >= span() * span();
    }
    private static List<MoltenShield> crossings(ServerLevel level, Vec3 from, Vec3 to, double radius) {
        return level.getEntitiesOfClass(MoltenShield.class, new AABB(from, to).inflate(radius + 0.1), wall -> wall.isAlive()
                && (wall.getBoundingBox().inflate(radius).contains(from) || wall.getBoundingBox().inflate(radius).clip(from, to).isPresent()))
                .stream().sorted(Comparator.comparingDouble(wall -> {
                    AABB bounds = wall.getBoundingBox().inflate(radius);
                    return bounds.contains(from) ? 0 : bounds.clip(from, to).orElse(to).distanceToSqr(from);
                })).toList();
    }
    public static boolean blocksProjectile(ServerLevel level, Projectile projectile) {
        Vec3 from = projectile.position(), to = from.add(projectile.getDeltaMovement());
        for (MoltenShield wall : crossings(level, from, to, projectile.getBbWidth() / 2)) {
            if (wall.breaksFor(projectile.getOwner(), null)) {
                if (wall.shatter()) continue;
                return true;
            }
            if (projectile instanceof TensuraProjectile magic) {
                wall.hurt(magic.getDamageSource(), magic.getDamage());
                if (wall.isAlive() && magic.getSecondaryDamage() > 0)
                    wall.hurt(magic.getDamageSource(magic.getSecondaryDamageType(), 1), magic.getSecondaryDamage());
            } else {
                Vec3 point = wall.getBoundingBox().inflate(projectile.getBbWidth() / 2).clip(from, to).orElse(from);
                ((AlchemyProjectileImpactInvoker) projectile).tracadamia$hit(new EntityHitResult(wall, point));
            }
            return true;
        }
        return false;
    }
    public static boolean blocksDamage(ServerLevel level, Vec3 from, Vec3 to, Entity attacker, DamageSource source) {
        return blocksDamage(level, from, to, attacker, source, 0);
    }
    public static boolean blocksDamage(ServerLevel level, Vec3 from, Vec3 to, Entity attacker, DamageSource source, float amount) {
        for (MoltenShield wall : crossings(level, from, to, 0)) {
            if (wall.breaksFor(attacker, source)) {
                if (wall.shatter()) continue;
                return true;
            }
            if (source != null && amount > 0) wall.hurt(source, amount);
            return true;
        }
        return false;
    }
    public static Vec3 clipBeam(ServerLevel level, Vec3 from, Vec3 to, double width, LivingEntity attacker) {
        return clipBeam(level, from, to, width, attacker, null, 0);
    }
    public static Vec3 clipBeam(ServerLevel level, Vec3 from, Vec3 to, double width, LivingEntity attacker, DamageSource source, float amount) {
        for (MoltenShield wall : crossings(level, from, to, width / 2)) {
            boolean instantBreak = wall.breaksFor(attacker, source);
            if (instantBreak) {
                if (source == null) {
                    if (!wall.canRepair()) continue;
                } else if (wall.shatter()) continue;
            }
            AABB bounds = wall.getBoundingBox().inflate(width / 2);
            if (!instantBreak && source != null && amount > 0) wall.hurt(source, amount);
            return bounds.contains(from) ? from : bounds.clip(from, to).orElse(to);
        }
        return to;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); if (creator != null) tag.putUUID("AlchemyCreator", creator);
        tag.putInt("AlchemySpan", span()); tag.putInt("AlchemyNormal", normal().get3DDataValue());
        tag.putDouble("AlchemyOwnerAura", savedAura); tag.putDouble("AlchemyThreshold", threshold); tag.putLong("AlchemyExpires", expiresAt);
        tag.putInt("AlchemyRepairs", repairs);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); creator = tag.hasUUID("AlchemyCreator") ? tag.getUUID("AlchemyCreator") : null;
        entityData.set(SPAN, Math.clamp(tag.getInt("AlchemySpan"), AlchemyQuirk.MIN_WALL_SIZE, AlchemyQuirk.MAX_WALL_SIZE));
        entityData.set(NORMAL, tag.getInt("AlchemyNormal")); savedAura = tag.getDouble("AlchemyOwnerAura");
        threshold = tag.getDouble("AlchemyThreshold"); expiresAt = tag.getLong("AlchemyExpires");
        repairs = Math.clamp(tag.getInt("AlchemyRepairs"), 0, MAX_REPAIRS);
        setBoundingBox(makeBoundingBox()); TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
    }
}

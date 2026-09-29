package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.UUID;

public final class DarkShadow extends Mob {
    public static final int BERSERK_CHECK_TICKS = 600, BERSERK_TICKS = 300;
    public static final double BERSERK_CHANCE = 0.25;
    public static final float DAMAGE_PER_PERCENT = 2.5F;
    public static final int PUNCH_INTERVAL = 40;
    public static final double ATTACK_GAP = 1.25;
    private static final String BINDING = "DarkShadowBinding";
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMMAND = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FUSED = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BERSERK = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RAGNAROK = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RELEASED = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HEALTH_MAX = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> POWER = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAPTIVE = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> END = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> ARM_PROGRESS = SynchedEntityData.defineId(DarkShadow.class, EntityDataSerializers.FLOAT);
    private UUID creatorId, captiveId;
    private CompoundTag loadedSession;
    private int mastery, activeTicks, captiveKind;
    private long berserkUntil, releaseUntil, flightUntil, punchAt, actionUntil, holdUntil, acquireAt, nextDamage, nextAttack;
    private Vec3 punchPoint, holdAnchor, slamPoint;
    private double holdDistance;
    private boolean movingCaptive;
    private int queuedMode = -1, queuedAt, queuedTicks;
    private UUID queuedTarget;
    private Vec3 queuedDirection;
    private boolean counterPunch;

    public DarkShadow(EntityType<? extends DarkShadow> type, Level level) {
        super(type, level);
        setNoAi(true); setNoGravity(true); setPersistenceRequired(); noPhysics = true; xpReward = 0;
        TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER, -1); builder.define(COMMAND, -1); builder.define(FUSED, false);
        builder.define(BERSERK, false); builder.define(RAGNAROK, false); builder.define(RELEASED, false); builder.define(HEALTH_MAX, 20F);
        builder.define(POWER, DAMAGE_PER_PERCENT); builder.define(ACTION, -1); builder.define(CAPTIVE, -1); builder.define(END, new Vector3f());
        builder.define(ARM_PROGRESS, 1F);
    }
    public UUID creatorId() { return creatorId; }
    public LivingEntity creator() {
        if (creatorId == null && !level().isClientSide) return null;
        Entity entity = level() instanceof ServerLevel server ? server.getEntity(creatorId) : level().getEntity(entityData.get(OWNER));
        return entity instanceof LivingEntity living ? living : null;
    }
    public int command() { return entityData.get(COMMAND); }
    public void cycleCommand(boolean counterUnlocked) {
        cancelNormalPunch();
        entityData.set(COMMAND, Math.floorMod(command() + 1, counterUnlocked ? 3 : 2));
    }
    public boolean fused() { return entityData.get(FUSED); }
    public void setFused(boolean fused) {
        if (fused != fused()) cancelAttack();
        entityData.set(FUSED, fused);
    }
    public boolean berserk() { return entityData.get(BERSERK); }
    public boolean ragnarok() { return entityData.get(RAGNAROK); }
    public boolean mastered() { return mastery >= 10000; }
    public int action() { return entityData.get(ACTION); }
    public Entity visualCaptive() { return level().getEntity(entityData.get(CAPTIVE)); }
    public Vec3 armEnd() { return position().add(new Vec3(entityData.get(END))); }
    public float armProgress() { return entityData.get(ARM_PROGRESS); }
    public boolean flying() { return level().getGameTime() < flightUntil; }
    public float visualScale() { return ragnarok() ? 5 : (entityData.get(RELEASED) ? 3 : 1) * (berserk() ? 2 : 1); }
    public double tetherRange() { return (mastered() ? 10 : 3 + Math.max(0, mastery / 1500)) * (ragnarok() ? 3 : 1); }
    @Override public double getAttributeValue(Holder<Attribute> attribute) {
        if (attribute.equals(Attributes.MAX_HEALTH) && entityData != null) return entityData.get(HEALTH_MAX);
        return super.getAttributeValue(attribute);
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return entityData == null ? super.getDefaultDimensions(pose) : EntityDimensions.scalable(0.6F * visualScale(), 1.8F * visualScale());
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data == RAGNAROK || data == RELEASED || data == BERSERK) refreshDimensions();
    }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return (!fused() || berserk()) && super.isPickable(); }
    @Override public boolean isAttackable() { return (!fused() || berserk()) && super.isAttackable(); }
    @Override public void travel(Vec3 input) { }
    @Override public void push(Entity entity) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override protected void dropAllDeathLoot(ServerLevel server, DamageSource source) { }
    @Override public void die(DamageSource source) {
        LivingEntity owner = creator();
        cancelAttack(); releaseCaptive(); stopFlight();
        if (owner != null) DarkShadowQuirk.shadowDied(this, owner);
        super.die(source);
    }
    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) { releaseCaptive(); stopFlight(); }
        super.remove(reason);
    }
    public void configure(LivingEntity owner, ManasSkillInstance instance) {
        creatorId = owner.getUUID(); entityData.set(OWNER, owner.getId());
        setFused(DarkShadowQuirk.fused(instance)); setPos(owner.position());
        updateStats(owner, instance); setHealth(getMaxHealth());
    }
    @Override public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && loadedSession != null) {
            restoreSession(loadedSession); loadedSession = null;
            TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
        }
    }
    public void updateStats(LivingEntity owner, ManasSkillInstance instance) {
        float ratio = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1;
        mastery = (int) instance.getMastery();
        float release = level().getGameTime() < releaseUntil ? 2 : 1;
        entityData.set(RELEASED, release > 1);
        float percentage = DarkShadowQuirk.percentage(instance);
        entityData.set(HEALTH_MAX, percentage * 20 * release);
        entityData.set(POWER, percentage * DAMAGE_PER_PERCENT * release * (ragnarok() ? 2 : 1));
        copyAttributes(owner, release);
        var existence = TensuraStorages.getExistenceFrom(this);
        existence.setMagicule(Math.max(1, EnergyHelper.getMaxMagicule(this)));
        existence.setAura(Math.max(1, EnergyHelper.getMaxAura(this)));
        // Filling EP can award native combat stats; the copied stats are authoritative.
        copyAttributes(owner, release);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(getMaxHealth());
        setEffectiveValue(getAttribute(Attributes.ATTACK_DAMAGE), entityData.get(POWER));
        setHealth(Math.clamp(ratio, 0, 1) * getMaxHealth());
    }
    private void copyAttributes(LivingEntity owner, float release) {
        for (var holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            if (holder.equals(Attributes.MAX_HEALTH) || holder.equals(Attributes.ATTACK_DAMAGE)
                    || holder.equals(Attributes.SCALE) || holder.equals(Attributes.GRAVITY)
                    || holder.equals(TensuraAttributes.WIDTH_MULTIPLIER) || holder.equals(TensuraAttributes.HEIGHT_MULTIPLIER)) continue;
            var attribute = getAttribute(holder);
            if (attribute != null && owner.getAttribute(holder) != null) setEffectiveValue(attribute, owner.getAttributeValue(holder) * 2 * release);
        }
        // Body size is controlled by visualScale, not the doubled combat attributes.
        setEffectiveValue(getAttribute(TensuraAttributes.WIDTH_MULTIPLIER), 1);
        setEffectiveValue(getAttribute(TensuraAttributes.HEIGHT_MULTIPLIER), 1);
        refreshDimensions();
    }
    private static void setEffectiveValue(AttributeInstance attribute, double desired) {
        double added = 0, baseMultiplier = 1, totalMultiplier = 1;
        for (var modifier : attribute.getModifiers()) {
            switch (modifier.operation()) {
                case ADD_VALUE -> added += modifier.amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier *= 1 + modifier.amount();
            }
        }
        double multiplier = baseMultiplier * totalMultiplier;
        if (Double.isFinite(multiplier) && Math.abs(multiplier) > 1E-9 && Double.isFinite(desired))
            attribute.setBaseValue(desired / multiplier - added);
    }
    public float attackDamage() { return entityData.get(POWER) * (creator() != null && DarkShadowQuirk.lowLight(creator()) ? 2 : 1); }
    public boolean enemy(LivingEntity target) {
        LivingEntity owner = creator();
        return target != this && !(target instanceof DarkShadow) && owner != null
                && (berserk() && target == owner || HomingQuirk.isEnemy(owner, target));
    }
    public void hit(LivingEntity target, float damage, int mode) {
        if (!enemy(target)) return;
        target.invulnerableTime = 0;
        target.hurt(DarkShadowQuirk.damageSource(this, mode), damage);
    }
    public void absorb(float amount, DamageSource source) {
        setHealth(Math.max(0, getHealth() - amount)); hurtTime = 10;
        if (getHealth() <= 0) die(source);
    }
    public void counter(LivingEntity target) {
        if (enemy(target) && !attacking() && level().getGameTime() >= nextAttack
                && creator().distanceToSqr(target) <= Math.pow(tetherRange() + 3, 2)) {
            queueAttack(DarkShadowQuirk.COMMAND, target);
            counterPunch = true;
        }
    }
    public boolean attacking() { return queuedMode >= 0 || punchAt > 0; }
    public void cancelAttack() {
        if (queuedMode == DarkShadowQuirk.WOMB && captiveId == null) entityData.set(CAPTIVE, -1);
        queuedMode = -1; queuedTarget = null; queuedDirection = null;
        entityData.set(ACTION, -1);
    }
    public void cancelNormalPunch() { if (queuedMode == DarkShadowQuirk.COMMAND) cancelAttack(); }
    public void queueAttack(int mode, LivingEntity target) {
        queuedMode = mode; queuedTarget = target == null ? null : target.getUUID();
        queuedDirection = creator().getLookAngle(); queuedAt = activeTicks; counterPunch = false;
        queuedTicks = switch (mode) {
            case DarkShadowQuirk.COMMAND -> 8;
            case DarkShadowQuirk.CLAWS, DarkShadowQuirk.ANGEL -> 10;
            case DarkShadowQuirk.WOMB -> 12;
            case DarkShadowQuirk.SABBATH -> 6;
            case DarkShadowQuirk.FLEETING -> 16;
            case DarkShadowQuirk.BALDUR -> 24;
            default -> throw new IllegalArgumentException("Not a windup attack: " + mode);
        };
        if (mode == DarkShadowQuirk.COMMAND) nextAttack = level().getGameTime() + PUNCH_INTERVAL;
        if (mode == DarkShadowQuirk.WOMB) entityData.set(CAPTIVE, target.getId());
        showArms(target == null ? creator().getEyePosition().add(queuedDirection.scale(mode == DarkShadowQuirk.CLAWS && mastered() ? 15 : 10))
                : target.getBoundingBox().getCenter(), mode, queuedTicks + 8);
        entityData.set(ARM_PROGRESS, 0F);
        level().playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.7F,
                mode == DarkShadowQuirk.BALDUR ? 0.6F : 0.9F);
    }
    private void tickAttack(LivingEntity owner) {
        if (queuedMode < 0) return;
        LivingEntity target = queuedTarget == null ? null : ((ServerLevel) level()).getEntity(queuedTarget) instanceof LivingEntity living ? living : null;
        if (queuedTarget != null && (target == null || !target.isAlive() || !enemy(target))) {
            if (queuedMode == DarkShadowQuirk.WOMB) entityData.set(CAPTIVE, -1);
            cancelAttack(); return;
        }
        float progress = Math.clamp((activeTicks - queuedAt) / (float) queuedTicks, 0, 1);
        entityData.set(ARM_PROGRESS, progress);
        Vec3 point = target == null ? owner.getEyePosition().add(queuedDirection.scale(queuedMode == DarkShadowQuirk.CLAWS && mastered() ? 15 : 10))
                : target.getBoundingBox().getCenter();
        entityData.set(END, point.subtract(position()).toVector3f());
        if (activeTicks % 3 == 0 && queuedMode != DarkShadowQuirk.COMMAND) {
            ((ServerLevel) level()).sendParticles(new net.minecraft.core.particles.DustParticleOptions(new Vector3f(0.16F, 0.025F, 0.22F), 1),
                    owner.getX(), owner.getY() + 1, owner.getZ(), 5, 0.45, 0.45, 0.45, 0);
        }
        if (progress < 1) return;
        int mode = queuedMode; queuedMode = -1; queuedTarget = null;
        if (mode == DarkShadowQuirk.COMMAND) {
            if (target != null && (counterPunch ? owner.distanceToSqr(target) <= Math.pow(tetherRange() + 3, 2)
                    : inPunchReach(target)) && hasLineOfSight(target)) {
                hit(target, attackDamage(), mode);
                target.setDeltaMovement(target.getDeltaMovement().add(target.position().subtract(position()).normalize().scale(0.3)));
                target.hurtMarked = true;
                level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9F, 0.65F);
            }
        } else DarkShadowQuirk.finishAttack(this, owner, mode, queuedDirection, target);
        queuedDirection = null;
    }
    public void clampTether() {
        LivingEntity owner = creator(); if (owner == null) return;
        Vec3 offset = position().subtract(owner.position());
        if (offset.lengthSqr() > tetherRange() * tetherRange()) setPos(owner.position().add(offset.normalize().scale(tetherRange())));
    }
    private boolean inPunchReach(LivingEntity target) {
        return getBoundingBox().inflate(ATTACK_GAP + 0.5).intersects(target.getBoundingBox());
    }
    private Vec3 attackPosition(LivingEntity target, LivingEntity owner) {
        double distance = (getBbWidth() + target.getBbWidth()) / 2 + ATTACK_GAP;
        Vec3 away = position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = owner.position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = owner.getLookAngle().multiply(-1, 0, -1);
        if (away.lengthSqr() < 0.01) away = new Vec3(0, 0, -1);
        Vec3 goal = target.position().add(away.normalize().scale(distance));
        // Prefer the creator's side when the other side would exceed the tether.
        Vec3 towardOwner = owner.position().subtract(target.position()).multiply(1, 0, 1);
        if (goal.distanceToSqr(owner.position()) > tetherRange() * tetherRange() && towardOwner.lengthSqr() > 0.01)
            goal = target.position().add(towardOwner.normalize().scale(distance));
        return goal;
    }
    public void showArms(Vec3 point, int mode, int ticks) {
        entityData.set(ACTION, mode); entityData.set(END, point.subtract(position()).toVector3f());
        entityData.set(ARM_PROGRESS, 1F);
        actionUntil = level().getGameTime() + ticks;
    }
    public void totalRelease() {
        releaseUntil = level().getGameTime() + (mastered() ? 1200 : 600);
        entityData.set(RELEASED, true);
        LivingEntity owner = creator(); if (owner != null && DarkShadowQuirk.instance(owner) != null) updateStats(owner, DarkShadowQuirk.instance(owner));
        ((ServerLevel) level()).sendParticles(new net.minecraft.core.particles.DustParticleOptions(new Vector3f(0.2F, 0.035F, 0.26F), 1.5F),
                getX(), getY() + 1, getZ(), 40, 0.8, 1, 0.8, 0);
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.7F, 0.65F);
    }
    public void startFlight() {
        LivingEntity owner = creator();
        flightUntil = level().getGameTime() + 200;
        owner.addEffect(new MobEffectInstance(MHAEffects.DARK_SHADOW_FLIGHT, 200, 0, false, false, true));
        owner.setDeltaMovement(owner.getLookAngle().scale(0.8).add(0, 0.7, 0)); owner.hurtMarked = true;
    }
    public void stopFlight() {
        flightUntil = 0; LivingEntity owner = creator(); if (owner != null) owner.removeEffect(MHAEffects.DARK_SHADOW_FLIGHT);
    }
    public void startBerserk(int ticks) {
        cancelAttack();
        berserkUntil = level().getGameTime() + ticks; entityData.set(BERSERK, true);
        releaseCaptive(); stopFlight();
        if (creator() != null) DarkShadowQuirk.message(creator(), "berserk");
    }
    public void startRagnarok() {
        LivingEntity owner = creator();
        entityData.set(RAGNAROK, true); startBerserk(40);
        punchAt = level().getGameTime() + 40;
        LivingEntity target = DarkShadowQuirk.lookedTarget(owner, tetherRange());
        punchPoint = target == null ? owner.level().clip(new ClipContext(owner.getEyePosition(),
                owner.getEyePosition().add(owner.getLookAngle().scale(tetherRange())), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation()
                : target.getBoundingBox().getCenter();
        updateStats(owner, DarkShadowQuirk.instance(owner)); showArms(punchPoint, DarkShadowQuirk.RAGNAROK, 40);
        entityData.set(ARM_PROGRESS, 0F);
    }
    private void finishRagnarok() {
        Vec3 impact = punchPoint;
        float damage = attackDamage() * 3;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(punchPoint, 10, 10, 10), this::enemy))
            hit(target, damage, DarkShadowQuirk.RAGNAROK);
        DarkShadowQuirk.breakRagnarokBlocks(this, punchPoint);
        ((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, punchPoint.x, punchPoint.y, punchPoint.z, 8, 2, 2, 2, 0);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2, 0.5F);
        punchAt = 0; punchPoint = null; berserkUntil = 0; entityData.set(RAGNAROK, false); entityData.set(BERSERK, false);
        showArms(impact, DarkShadowQuirk.RAGNAROK, 12);
        if (creator() != null && DarkShadowQuirk.instance(creator()) != null) updateStats(creator(), DarkShadowQuirk.instance(creator()));
        clampTether();
    }
    public static DarkShadow binding(LivingEntity target) {
        var tag = target.getPersistentData();
        if (!(target.level() instanceof ServerLevel server) || !tag.hasUUID(BINDING)) return null;
        Entity entity = server.getEntity(tag.getUUID(BINDING));
        if (!(entity instanceof DarkShadow shadow) || !shadow.isAlive() || !target.getUUID().equals(shadow.captiveId)
                || server.getGameTime() < shadow.acquireAt || server.getGameTime() >= shadow.holdUntil) return null;
        return shadow;
    }
    public static boolean blocksMovement(Entity entity) {
        return entity instanceof LivingEntity target && binding(target) instanceof DarkShadow shadow && !shadow.movingCaptive;
    }
    public boolean isGrabbing() { return captiveKind == DarkShadowQuirk.ARMS && captiveId != null && level().getGameTime() >= acquireAt; }
    public void grab(LivingEntity target) {
        releaseCaptive(); captiveId = target.getUUID(); captiveKind = DarkShadowQuirk.ARMS;
        holdDistance = Math.clamp(creator().distanceTo(target), 3, 20);
        acquireAt = level().getGameTime() + Math.max(8, (long) Math.ceil(holdDistance / 2));
        holdUntil = acquireAt + (mastered() ? 300 : 200); nextDamage = acquireAt + 20;
        entityData.set(CAPTIVE, target.getId()); showArms(target.getBoundingBox().getCenter(), captiveKind, (int) (holdUntil - level().getGameTime()));
        entityData.set(ARM_PROGRESS, 0F);
    }
    public void womb(LivingEntity target) {
        releaseCaptive(); captiveId = target.getUUID(); captiveKind = DarkShadowQuirk.WOMB;
        holdAnchor = target.position(); acquireAt = level().getGameTime(); holdUntil = acquireAt + (mastered() ? 400 : 300);
        entityData.set(CAPTIVE, target.getId()); target.getPersistentData().putUUID(BINDING, getUUID());
        showArms(target.getBoundingBox().getCenter(), captiveKind, (int) (holdUntil - acquireAt));
    }
    public void releaseCaptive() {
        if (captiveId != null && level() instanceof ServerLevel server && server.getEntity(captiveId) instanceof LivingEntity target
                && target.getPersistentData().hasUUID(BINDING) && target.getPersistentData().getUUID(BINDING).equals(getUUID()))
            target.getPersistentData().remove(BINDING);
        captiveId = null; holdAnchor = null; slamPoint = null; entityData.set(CAPTIVE, -1);
    }
    public void slam() {
        if (!(level() instanceof ServerLevel server) || !isGrabbing() || !(server.getEntity(captiveId) instanceof LivingEntity target)) return;
        Vec3 end = new Vec3(target.getX(), server.getMinBuildHeight(), target.getZ());
        var hit = server.clip(new ClipContext(target.position(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        if (hit.getType() == HitResult.Type.BLOCK) slamPoint = hit.getLocation().add(0, 0.05, 0);
    }
    private void tickCaptive(LivingEntity owner) {
        if (captiveId == null) return;
        Entity entity = ((ServerLevel) level()).getEntity(captiveId);
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || level().getGameTime() >= holdUntil || berserk()) { releaseCaptive(); return; }
        if (queuedMode < 0) showArms(target.getBoundingBox().getCenter(), captiveKind, 2);
        if (level().getGameTime() < acquireAt) {
            entityData.set(ARM_PROGRESS, (float) Math.clamp(1 - (acquireAt - level().getGameTime()) / Math.max(8, Math.ceil(holdDistance / 2)), 0, 1));
            if (!owner.hasLineOfSight(target)) releaseCaptive();
            return;
        }
        target.getPersistentData().putUUID(BINDING, getUUID());
        Vec3 goal = holdAnchor;
        if (captiveKind == DarkShadowQuirk.ARMS) {
            Vec3 intended = owner.getEyePosition().add(owner.getLookAngle().scale(holdDistance));
            var hit = level().clip(new ClipContext(owner.getEyePosition(), intended, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            goal = slamPoint != null ? slamPoint : hit.getLocation().subtract(owner.getLookAngle().scale(target.getBbWidth() / 2 + 0.2))
                    .subtract(0, target.getBbHeight() / 2, 0);
            if (level().getGameTime() >= nextDamage) { nextDamage = level().getGameTime() + 20; hit(target, attackDamage() * 0.2F, captiveKind); }
        }
        Vec3 movement = goal.subtract(target.position());
        if (movement.lengthSqr() > 9) movement = movement.normalize().scale(3);
        movingCaptive = true;
        try { target.move(MoverType.SELF, movement); }
        finally { movingCaptive = false; }
        if (target instanceof net.minecraft.server.level.ServerPlayer player) player.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
        target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true; target.fallDistance = 0;
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) calculateEntityAnimation(false);
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        LivingEntity owner = creator(); if (owner == null) return;
        ManasSkillInstance instance = DarkShadowQuirk.instance(owner);
        if (instance != null && instance.getOrCreateTag().hasUUID(DarkShadowQuirk.SHADOW)
                && !instance.getOrCreateTag().getUUID(DarkShadowQuirk.SHADOW).equals(getUUID())) { discard(); return; }
        if (!owner.isAlive()) { releaseCaptive(); discard(); return; }
        if (instance == null || !instance.isToggled()) {
            if (!berserk()) { releaseCaptive(); discard(); return; }
        }
        entityData.set(OWNER, owner.getId());
        if (instance != null) {
            setFused(DarkShadowQuirk.fused(instance));
            if (instance.getOrCreateTag().getInt("DarkShadowRuntimeId") != getId()) {
                instance.getOrCreateTag().putInt("DarkShadowRuntimeId", getId()); instance.markDirty();
            }
        }
        activeTicks++;
        if (releaseUntil > 0 && server.getGameTime() >= releaseUntil) {
            releaseUntil = 0;
            entityData.set(RELEASED, false);
            if (instance != null) updateStats(owner, instance);
        }
        if (punchAt > 0) entityData.set(ARM_PROGRESS, (float) Math.pow(Math.clamp(1 - (punchAt - server.getGameTime()) / 40.0, 0, 1), 2));
        if (punchAt > 0 && server.getGameTime() >= punchAt) finishRagnarok();
        if (berserk() && server.getGameTime() >= berserkUntil) entityData.set(BERSERK, false);
        if (instance != null && activeTicks % 20 == 0) {
            if (activeTicks % 1200 == 0) instance.addMasteryPoint(owner);
            updateStats(owner, instance);
            var existence = TensuraStorages.getExistenceFrom(owner);
            double cost = EnergyHelper.getMaxAura(owner) * 0.0005;
            if (existence.getAura() < cost && !berserk()) { DarkShadowQuirk.dismiss(instance, owner, false); return; }
            existence.setAura(Math.max(0, existence.getAura() - cost)); existence.markDirty();
            if (activeTicks % BERSERK_CHECK_TICKS == 0 && !mastered() && DarkShadowQuirk.lowLight(owner) && random.nextDouble() < BERSERK_CHANCE)
                startBerserk(BERSERK_TICKS);
            instance.getOrCreateTag().put(DarkShadowQuirk.SNAPSHOT, session()); instance.markDirty();
        }
        if (flying() && !owner.hasEffect(MHAEffects.DARK_SHADOW_FLIGHT)) stopFlight();
        tickCaptive(owner);
        tickAttack(owner);
        if (actionUntil <= server.getGameTime()) entityData.set(ACTION, -1);
        LivingEntity target = null;
        if (berserk()) target = owner;
        else if (!fused() && !ragnarok() && captiveId == null) {
            if (command() == 0 && activeTicks % 10 == 0) {
                setTarget(server.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(tetherRange() + 3), this::enemy)
                        .stream().filter(owner::hasLineOfSight).min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null));
            } else if (command() == 1 || command() == 2) {
                LivingEntity attacker = owner.getLastHurtByMob();
                setTarget(attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100 && enemy(attacker) ? attacker : null);
            }
            target = getTarget();
        }
        Vec3 goal = fused() && !berserk() ? owner.position() : owner.position().add(owner.getLookAngle().multiply(-1.3, 0, -1.3));
        if (ragnarok() && punchPoint != null) goal = punchPoint.subtract(0, getBbHeight() * 0.4, 0);
        else if (target != null && target.isAlive()) goal = attackPosition(target, owner);
        Vec3 offset = goal.subtract(owner.position());
        if (offset.lengthSqr() > tetherRange() * tetherRange()) goal = owner.position().add(offset.normalize().scale(tetherRange()));
        Vec3 movement = goal.subtract(position());
        Vec3 visualPoint = armEnd();
        double speed = Math.max(0.1, getAttributeValue(Attributes.MOVEMENT_SPEED) * 2);
        if (movement.length() > speed) movement = movement.normalize().scale(speed);
        if (fused() && !berserk()) setPos(owner.position()); else move(MoverType.SELF, movement);
        setDeltaMovement(Vec3.ZERO); clampTether();
        calculateEntityAnimation(false);
        if (action() >= 0) entityData.set(END, visualPoint.subtract(position()).toVector3f());
        if (punchAt > 0 && punchPoint != null) entityData.set(END, punchPoint.subtract(position()).toVector3f());
        setYRot(owner.getYRot()); yBodyRot = owner.yBodyRot; yHeadRot = owner.yHeadRot;
        if (target != null && !attacking() && captiveId == null && !ragnarok() && server.getGameTime() >= nextAttack
                && inPunchReach(target) && hasLineOfSight(target)) {
            queueAttack(DarkShadowQuirk.COMMAND, target);
        }
    }
    public CompoundTag session() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("HealthRatio", getHealth() / getMaxHealth()); tag.putInt("ActiveTicks", activeTicks); tag.putInt("Command", command());
        tag.putLong("BerserkUntil", berserkUntil); tag.putLong("ReleaseUntil", releaseUntil); return tag;
    }
    public void restoreSession(CompoundTag tag) {
        activeTicks = tag.getInt("ActiveTicks"); entityData.set(COMMAND, tag.getInt("Command"));
        berserkUntil = tag.getLong("BerserkUntil"); releaseUntil = tag.getLong("ReleaseUntil");
        entityData.set(RELEASED, level().getGameTime() < releaseUntil);
        entityData.set(BERSERK, level().getGameTime() < berserkUntil);
        LivingEntity owner = creator(); if (owner != null && DarkShadowQuirk.instance(owner) != null) updateStats(owner, DarkShadowQuirk.instance(owner));
        setHealth(Math.clamp(tag.getFloat("HealthRatio"), 0, 1) * getMaxHealth());
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (creatorId != null) tag.putUUID("ShadowCreator", creatorId);
        tag.put("ShadowSession", session()); tag.putInt("ShadowMastery", mastery); tag.putBoolean("ShadowFused", fused());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        creatorId = tag.hasUUID("ShadowCreator") ? tag.getUUID("ShadowCreator") : null; mastery = tag.getInt("ShadowMastery");
        setFused(tag.getBoolean("ShadowFused"));
        float release = tag.getCompound("ShadowSession").getLong("ReleaseUntil") > level().getGameTime() ? 2 : 1;
        entityData.set(HEALTH_MAX, Math.max(1, mastery / 100) * 20F * release);
        entityData.set(POWER, Math.max(1, mastery / 100) * DAMAGE_PER_PERCENT * release);
        restoreSession(tag.getCompound("ShadowSession"));
        loadedSession = tag.getCompound("ShadowSession").copy();
        TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
    }
}

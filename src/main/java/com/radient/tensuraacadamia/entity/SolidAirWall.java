package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SolidAirQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class SolidAirWall extends Mob {
    private static final EntityDataAccessor<Float> SPAN = SynchedEntityData.defineId(SolidAirWall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS = SynchedEntityData.defineId(SolidAirWall.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> NORMAL = SynchedEntityData.defineId(SolidAirWall.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SPIKED = SynchedEntityData.defineId(SolidAirWall.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEPLETED = SynchedEntityData.defineId(SolidAirWall.class, EntityDataSerializers.BOOLEAN);
    public static final int BREAK_GRACE_TICKS = 10;
    public static final int CLOSING_TICKS = 100;
    public static final float MIN_PRISON_SIZE = 4;
    private UUID owner;
    private Vec3 prisonCenter;
    private int layer, layers = 1;
    private long createdAt, expiresAt, breaksAt;

    public SolidAirWall(EntityType<? extends SolidAirWall> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        noPhysics = true;
        blocksBuilding = true;
        xpReward = 0;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPAN, 5F);
        builder.define(THICKNESS, 1F);
        builder.define(NORMAL, Direction.NORTH.get3DDataValue());
        builder.define(SPIKED, false);
        builder.define(DEPLETED, false);
    }
    public Direction normal() { return Direction.from3DDataValue(entityData.get(NORMAL)); }
    public boolean spiked() { return entityData.get(SPIKED); }
    public UUID ownerId() { return owner; }
    public void configure(LivingEntity creator, Vec3 center, Direction normal, float span, float thickness,
                          float health, boolean spiked, Vec3 prisonCenter, int layer, int layers) {
        owner = creator.getUUID();
        this.prisonCenter = prisonCenter;
        this.layer = layer;
        this.layers = layers;
        createdAt = level().getGameTime();
        expiresAt = createdAt + SolidAirQuirk.LIFETIME;
        breaksAt = 0;
        entityData.set(DEPLETED, false);
        entityData.set(NORMAL, normal.get3DDataValue());
        entityData.set(THICKNESS, thickness);
        entityData.set(SPAN, span);
        entityData.set(SPIKED, spiked);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth(health);
        setCenter(center);
    }
    private Vec3 extents() {
        float span = entityData.get(SPAN), thickness = entityData.get(THICKNESS);
        return switch (normal().getAxis()) {
            case X -> new Vec3(thickness, span, span);
            case Y -> new Vec3(span, thickness, span);
            case Z -> new Vec3(span, span, thickness);
        };
    }
    private void setCenter(Vec3 center) { setPos(center.x, center.y - extents().y / 2, center.z); }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (entityData == null) return super.getDefaultDimensions(pose);
        Vec3 size = extents();
        return EntityDimensions.scalable((float) Math.max(size.x, size.z), (float) size.y);
    }
    @Override protected AABB makeBoundingBox() {
        if (entityData == null) return super.makeBoundingBox();
        Vec3 size = extents();
        return new AABB(getX() - size.x / 2, getY(), getZ() - size.z / 2,
                getX() + size.x / 2, getY() + size.y, getZ() + size.z / 2);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data == SPAN || data == NORMAL || data == THICKNESS) refreshDimensions();
    }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isAlive() { return !isRemoved() && (entityData.get(DEPLETED) || super.isAlive()); }
    @Override public boolean isDeadOrDying() { return !entityData.get(DEPLETED) && super.isDeadOrDying(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity entity) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public void die(DamageSource source) {
        if (!level().isClientSide && !entityData.get(DEPLETED)) {
            entityData.set(DEPLETED, true);
            breaksAt = level().getGameTime() + BREAK_GRACE_TICKS;
        }
    }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        long now = server.getGameTime();
        if (now >= (entityData.get(DEPLETED) ? breaksAt : expiresAt)) { discard(); return; }
        if (!spiked() || prisonCenter == null) return;
        float span = 15 - (15 - MIN_PRISON_SIZE) * Math.clamp((float) (now - createdAt) / CLOSING_TICKS, 0, 1);
        entityData.set(SPAN, span);
        Vec3 inward = Vec3.atLowerCornerOf(normal().getNormal()).scale(-1);
        setCenter(prisonCenter.subtract(inward.scale(span / 2 - (layer + 0.5) / layers)));
        LivingEntity creator = owner == null ? null : server.getEntity(owner) instanceof LivingEntity living ? living : null;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.45),
                target -> !(target instanceof SolidAirWall) && target.isAlive() && !target.isSpectator()
                        && !target.getUUID().equals(owner) && (creator == null || HomingQuirk.isEnemy(creator, target)))) {
            if (span > MIN_PRISON_SIZE) {
                target.setDeltaMovement(target.getDeltaMovement().add(inward.scale(0.12)));
                target.hurtMarked = true;
            }
            if (now < target.getPersistentData().getLong("TracadamiaAirMaidenHitAt")) continue;
            DamageSource source = server.damageSources().source(TensuraDamageTypes.WIND_ELEMENTAL, this, creator);
            TensuraDamageSource typed = (TensuraDamageSource) source;
            typed.tensura$setSkillType(Skill.SkillType.UNIQUE);
            typed.tensura$setElement(Element.WIND);
            typed.tensura$setAbilityMode(2);
            if (creator != null) SkillAPI.getSkillsFrom(creator).getSkill(QuirkSkills.SOLID_AIR.get()).ifPresent(typed::tensura$setAbilityInstance);
            target.getPersistentData().putLong("TracadamiaAirMaidenHitAt", now + 10);
            target.hurt(source, 50);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("AirOwner", owner);
        tag.putFloat("AirSpan", entityData.get(SPAN));
        tag.putFloat("AirThickness", entityData.get(THICKNESS));
        tag.putInt("AirNormal", normal().get3DDataValue());
        tag.putBoolean("AirSpiked", spiked());
        tag.putLong("AirCreated", createdAt);
        tag.putLong("AirExpires", expiresAt);
        tag.putLong("AirBreaks", breaksAt);
        tag.putInt("AirLayer", layer);
        tag.putInt("AirLayers", layers);
        if (prisonCenter != null) {
            tag.putDouble("AirCenterX", prisonCenter.x);
            tag.putDouble("AirCenterY", prisonCenter.y);
            tag.putDouble("AirCenterZ", prisonCenter.z);
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("AirOwner") ? tag.getUUID("AirOwner") : null;
        entityData.set(SPAN, tag.getFloat("AirSpan"));
        entityData.set(THICKNESS, tag.getFloat("AirThickness"));
        entityData.set(NORMAL, tag.getInt("AirNormal"));
        entityData.set(SPIKED, tag.getBoolean("AirSpiked"));
        createdAt = tag.getLong("AirCreated");
        expiresAt = tag.getLong("AirExpires");
        breaksAt = tag.getLong("AirBreaks");
        entityData.set(DEPLETED, breaksAt > 0);
        layer = tag.getInt("AirLayer");
        layers = Math.max(1, tag.getInt("AirLayers"));
        prisonCenter = tag.contains("AirCenterX") ? new Vec3(tag.getDouble("AirCenterX"), tag.getDouble("AirCenterY"), tag.getDouble("AirCenterZ")) : null;
        setBoundingBox(makeBoundingBox());
    }
}

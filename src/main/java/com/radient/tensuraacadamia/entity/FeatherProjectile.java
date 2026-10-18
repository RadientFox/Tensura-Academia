package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.FierceWingsQuirk;
import com.radient.tensuraacadamia.regestry.FierceWingsEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class FeatherProjectile extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(FeatherProjectile.class, EntityDataSerializers.INT);
    private static final int LIFETIME = 60;
    private static final double SPEED = 1.8D;
    private static final double HOMING = 0.25D;

    private float damage;
    private int targetId = -1;

    public FeatherProjectile(EntityType<? extends FeatherProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public FeatherProjectile(Level level, LivingEntity owner, float damage, @Nullable LivingEntity target, int color) {
        this(FierceWingsEntities.FEATHER.get(), level);
        setOwner(owner);
        this.damage = damage;
        this.targetId = target == null ? -1 : target.getId();
        entityData.set(COLOR, color);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, FierceWingsQuirk.DEFAULT_COLOR);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    private DustParticleOptions dust(float scale) {
        int rgb = getColor();
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F), scale);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && !ownedBy(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            if (tickCount > 3 && targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity target && target.isAlive()) {
                Vec3 toward = target.getBoundingBox().getCenter().subtract(position()).normalize();
                Vec3 heading = velocity.lengthSqr() < 1.0E-6D ? toward : velocity.normalize();
                velocity = heading.lerp(toward, HOMING).normalize().scale(SPEED);
                setDeltaMovement(velocity);
                hasImpulse = true;
            }

            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                onHit(hit);
                return;
            }
        } else if (tickCount % 2 == 0) {
            level().addParticle(dust(0.8F),getX(), getY() + getBbHeight() * 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
        }

        setPos(position().add(velocity));
        updateRotation();
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && getOwner() instanceof LivingEntity owner) {
            FierceWingsQuirk.featherHit(owner, target, damage);
        }

        if (level() instanceof ServerLevel server) {
            Vec3 at = hit.getLocation();
            server.sendParticles(dust(0.8F),at.x, at.y, at.z, 6, 0.1D, 0.1D, 0.1D, 0.05D);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.5F, 1.6F);
        }

        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("FeatherDamage", damage);
        tag.putInt("FeatherColor", getColor());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("FeatherDamage");
        if (tag.contains("FeatherColor")) {
            entityData.set(COLOR, tag.getInt("FeatherColor"));
        }
    }
}

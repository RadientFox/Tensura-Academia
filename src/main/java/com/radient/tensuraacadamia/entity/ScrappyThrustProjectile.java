package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.SlideAndGlideQuirk;
import com.radient.tensuraacadamia.regestry.SlideAndGlideEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

public final class ScrappyThrustProjectile extends Projectile {
    private float damage;
    private int lifetime = 30;

    public ScrappyThrustProjectile(EntityType<? extends ScrappyThrustProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public ScrappyThrustProjectile(Level level, LivingEntity owner, float damage, int lifetime) {
        this(SlideAndGlideEntities.SCRAPPY_THRUST.get(), level);
        setOwner(owner);
        this.damage = damage;
        this.lifetime = lifetime;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && !ownedBy(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > lifetime) {
            discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                onHit(hit);
                return;
            }
        }

        setPos(position().add(velocity));
        updateRotation();
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                && getOwner() instanceof LivingEntity owner) {
            SlideAndGlideQuirk.thrustHit(owner, target, damage);
        }

        if (level() instanceof ServerLevel server) {
            Vec3 at = hit.getLocation();
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.1D, 0.1D, 0.1D, 0.12D);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 1.6F);
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
        tag.putFloat("ThrustDamage", damage);
        tag.putInt("ThrustLifetime", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("ThrustDamage");
        lifetime = tag.getInt("ThrustLifetime");
    }
}

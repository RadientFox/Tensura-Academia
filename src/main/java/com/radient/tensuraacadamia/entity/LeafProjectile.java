package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.regestry.LeafEntities;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class LeafProjectile extends Projectile {
    public static final int NEEDLE = 1;
    public static final int HEMLOCK = 2;
    private static final int HEMLOCK_HOVER_TICKS = 20;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(
            LeafProjectile.class, EntityDataSerializers.INT);
    private boolean poison;
    private boolean mastered;
    private int hoverIndex;
    private UUID targetId;
    private Vec3 targetPosition = Vec3.ZERO;
    private Vec3 launchDirection = Vec3.ZERO;

    public LeafProjectile(EntityType<? extends LeafProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public LeafProjectile(Level level, LivingEntity owner, int kind) {
        this(LeafEntities.LEAF.get(), level);
        setOwner(owner);
        entityData.set(KIND, kind);
        setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.6)));
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, NEEDLE);
    }

    public int getKind() { return entityData.get(KIND); }

    public void setNeedleEffects(boolean poison, boolean mastered) {
        this.poison = poison;
        this.mastered = mastered;
    }

    public void setHemlockTarget(int index, LivingEntity target) {
        this.hoverIndex = index;
        this.targetId = target.getUUID();
        this.targetPosition = target.getBoundingBox().getCenter();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity target && getOwner() instanceof LivingEntity owner
                && HomingQuirk.isEnemy(owner, target);
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > 80 || !(getOwner() instanceof LivingEntity owner) || !owner.isAlive())) {
            discard();
            return;
        }
        if (getKind() == HEMLOCK && tickCount <= HEMLOCK_HOVER_TICKS) {
            if (!level().isClientSide && getOwner() instanceof LivingEntity owner) {
                double angle = hoverIndex * Math.PI / 2;
                double rise = Math.min(1.0, tickCount / 8.0);
                setPos(owner.getX() + Math.cos(angle) * 0.48,
                        owner.getEyeY() + 0.15 + rise * 0.9,
                        owner.getZ() + Math.sin(angle) * 0.48);
                setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        if (getKind() == HEMLOCK && !level().isClientSide) {
            if (launchDirection.lengthSqr() < 0.01) {
                if (level() instanceof ServerLevel server && targetId != null
                        && server.getEntity(targetId) instanceof LivingEntity target && target.isAlive())
                    targetPosition = target.getBoundingBox().getCenter();
                launchDirection = targetPosition.subtract(position()).normalize();
                if (launchDirection.lengthSqr() < 0.01) { discard(); return; }
            }
            double speed = Math.min(2.3, 0.35 + (tickCount - HEMLOCK_HOVER_TICKS - 1) * 0.12);
            setDeltaMovement(launchDirection.scale(speed));
            hasImpulse = true;
        }
        Vec3 movement = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                if (!net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) onHit(hit);
                return;
            }
        }
        setPos(position().add(movement));
        updateRotation();
    }

    @Override protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                && getOwner() instanceof LivingEntity owner && HomingQuirk.isEnemy(owner, target)) {
            if (getKind() == NEEDLE) {
                target.invulnerableTime = 0;
                if (target.hurt(owner.damageSources().thrown(this, owner), mastered ? 2.5F : 2F) && poison)
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            } else {
                target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 300, 1));
                target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.CORROSION), 300, 1));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FATAL_POISON), 300, 1));
            }
        }
        discard();
    }
}

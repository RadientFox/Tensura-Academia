package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.PopOffQuirk;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class PopOffProjectile extends Projectile {
    public static final double GRAVITY = 0.05D;
    private static final int FLYING = 0, GROUND = 1, ATTACHED = 2;
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
            PopOffProjectile.class, EntityDataSerializers.INT);
    private long expiry;
    private UUID attachedTarget;
    private Vec3 attachmentOffset = Vec3.ZERO;
    private Vec3 surfaceNormal = new Vec3(0, 1, 0);
    private boolean creatorTouching;

    public PopOffProjectile(EntityType<? extends PopOffProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
        expiry = level.getGameTime() + 200;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(STATE, FLYING); }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator() && !ownedBy(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level().getGameTime() >= expiry) { discard(); return; }
        int state = entityData.get(STATE);
        if (state != FLYING) {
            if (level().isClientSide) return;
            if (state == GROUND) touchGroundBall();
            else if (attachedTarget != null && level() instanceof net.minecraft.server.level.ServerLevel server) {
                Entity target = server.getEntity(attachedTarget);
                if (target != null) {
                    if (!target.isAlive()) discard();
                    else setPos(target.position().add(attachmentOffset));
                }
            }
            return;
        }
        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS
                    && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                onHit(hit);
                return;
            }
        }
        setPos(position().add(velocity));
        setDeltaMovement(velocity.add(0, -GRAVITY, 0));
        updateRotation();
    }

    @Override
    protected void onHit(HitResult hit) {
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
            MobEffectInstance old = target.getEffect(MHAEffects.POP);
            int amplifier = old == null ? 0 : Math.min(MobEffectInstance.MAX_AMPLIFIER, old.getAmplifier() + 1);
            target.addEffect(new MobEffectInstance(MHAEffects.POP, PopOffQuirk.STICK_TICKS, amplifier));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PopOffQuirk.STICK_TICKS, amplifier));
            attachedTarget = target.getUUID();
            attachmentOffset = position().subtract(target.position());
            entityData.set(STATE, ATTACHED);
        } else if (hit instanceof BlockHitResult blockHit) {
            surfaceNormal = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            setPos(hit.getLocation().add(surfaceNormal.scale(0.221D)).add(0, -0.22D, 0));
            entityData.set(STATE, GROUND);
        } else { discard(); return; }
        expiry = level().getGameTime() + PopOffQuirk.STICK_TICKS;
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
    }

    private void touchGroundBall() {
        boolean touching = false;
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.12D),
                entity -> entity.isAlive() && !entity.isSpectator())) {
            if (!ownedBy(target)) {
                TensuraMobEffect.addEffect(target, new MobEffectInstance(
                                TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 100, 4),
                        getOwner(), QuirkSkills.POP_OFF.get(), 0);
                discard();
                return;
            }
            touching = true;
            if (!creatorTouching) {
                Vec3 velocity = target.getDeltaMovement();
                Vec3 bounce = surfaceNormal.scale(1.0D).add(velocity.x * 0.5D, 0, velocity.z * 0.5D);
                target.setDeltaMovement(bounce.add(0, surfaceNormal.y == 0 ? 0.35D : 0, 0).scale(2.0D));
                target.fallDistance = 0;
                target.hurtMarked = true;
            }
        }
        creatorTouching = touching;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("PopState", entityData.get(STATE));
        tag.putLong("PopExpiry", expiry);
        if (attachedTarget != null) tag.putUUID("PopTarget", attachedTarget);
        tag.putDouble("PopOffsetX", attachmentOffset.x);
        tag.putDouble("PopOffsetY", attachmentOffset.y);
        tag.putDouble("PopOffsetZ", attachmentOffset.z);
        tag.putDouble("PopNormalX", surfaceNormal.x);
        tag.putDouble("PopNormalY", surfaceNormal.y);
        tag.putDouble("PopNormalZ", surfaceNormal.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(STATE, tag.getInt("PopState"));
        expiry = tag.getLong("PopExpiry");
        attachedTarget = tag.hasUUID("PopTarget") ? tag.getUUID("PopTarget") : null;
        attachmentOffset = new Vec3(tag.getDouble("PopOffsetX"), tag.getDouble("PopOffsetY"), tag.getDouble("PopOffsetZ"));
        surfaceNormal = new Vec3(tag.getDouble("PopNormalX"), tag.getDouble("PopNormalY"), tag.getDouble("PopNormalZ"));
    }
}

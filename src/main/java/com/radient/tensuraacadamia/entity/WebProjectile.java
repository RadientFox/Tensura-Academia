package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.regestry.SpiderRegistry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

public final class WebProjectile extends Projectile {
    public static final int SHOT = 0;
    public static final int IMPACT = 1;
    public static final int ELECTRIC = 2;
    public static final int BLOSSOM = 3;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(WebProjectile.class, EntityDataSerializers.INT);
    private static final BlockParticleOption COBWEB = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
    private static final int LIFETIME = 60;
    private static final double SPEED = 2.2D;
    private static final double HOMING = 0.3D;

    private float damage;
    private float extraDamage;
    private int targetId = -1;

    public WebProjectile(EntityType<? extends WebProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public WebProjectile(Level level, LivingEntity owner, int kind, float damage, float extraDamage, @Nullable LivingEntity target) {
        this(SpiderRegistry.WEB.get(), level);
        setOwner(owner);
        entityData.set(KIND, kind);
        this.damage = damage;
        this.extraDamage = extraDamage;
        this.targetId = target == null ? -1 : target.getId();
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, SHOT);
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
            if (targetId >= 0 && level().getEntity(targetId) instanceof LivingEntity target && target.isAlive()) {
                Vec3 toward = target.getBoundingBox().getCenter().subtract(position()).normalize();
                Vec3 heading = velocity.lengthSqr() < 1.0E-6D ? toward : velocity.normalize();
                velocity = heading.lerp(toward, HOMING).normalize().scale(Math.max(SPEED, velocity.length()));
                setDeltaMovement(velocity);
                hasImpulse = true;
            }

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
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && getOwner() instanceof LivingEntity owner) {
            SpiderQuirk.webHit(owner, target, getKind(), damage, extraDamage);
        }

        if (level() instanceof ServerLevel server) {
            Vec3 at = hit.getLocation();
            server.sendParticles(COBWEB, at.x, at.y, at.z, 8, 0.15D, 0.15D, 0.15D, 0.05D);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 1.5F);
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
        tag.putInt("WebKind", getKind());
        tag.putFloat("WebDamage", damage);
        tag.putFloat("WebExtraDamage", extraDamage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("WebKind"));
        damage = tag.getFloat("WebDamage");
        extraDamage = tag.getFloat("WebExtraDamage");
    }
}

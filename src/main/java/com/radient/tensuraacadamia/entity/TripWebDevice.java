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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

public final class TripWebDevice extends Entity {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(TripWebDevice.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> LINE_END = SynchedEntityData.defineId(TripWebDevice.class, EntityDataSerializers.VECTOR3);
    private static final BlockParticleOption COBWEB = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());

    private @Nullable UUID owner;
    private int lifetime = 6000;

    public TripWebDevice(EntityType<? extends TripWebDevice> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public TripWebDevice(Level level, LivingEntity owner, Vec3 at, Vec3 lineEnd, int lifetime) {
        this(SpiderRegistry.TRIP_DEVICE.get(), level);
        this.owner = owner.getUUID();
        this.lifetime = lifetime;
        entityData.set(OWNER_ID, owner.getId());
        entityData.set(LINE_END, lineEnd.toVector3f());
        setPos(at.x, at.y - getBbHeight() * 0.5D, at.z);
    }

    public int getOwnerId() {
        return entityData.get(OWNER_ID);
    }

    public @Nullable UUID getOwnerUUID() {
        return owner;
    }

    public Vec3 getLineEnd() {
        return new Vec3(entityData.get(LINE_END));
    }

    public Vec3 getLineStart() {
        return position().add(0.0D, getBbHeight() * 0.5D, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_ID, -1);
        builder.define(LINE_END, new Vector3f());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity ownerEntity = owner == null ? null : level.getEntity(owner) instanceof LivingEntity living ? living : null;
        if (owner == null || tickCount > lifetime) {
            breakApart(level);
            return;
        }

        if (ownerEntity != null && ownerEntity.getId() != getOwnerId()) {
            entityData.set(OWNER_ID, ownerEntity.getId());
        }

        Vec3 start = getLineStart();
        Vec3 end = getLineEnd();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(0.3D),
                living -> living.isAlive() && !living.isSpectator() && !living.getUUID().equals(owner))) {
            if (target.getBoundingBox().inflate(0.1D).clip(start, end).isPresent()) {
                spring(level, target, ownerEntity);
                return;
            }
        }
    }

    private void spring(ServerLevel level, LivingEntity target, @Nullable LivingEntity ownerEntity) {
        Vec3 at = position();
        target.stopRiding();
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        } else {
            target.teleportTo(at.x, at.y, at.z);
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        SpiderQuirk.webTarget(target, ownerEntity);
        level.sendParticles(COBWEB, at.x, at.y + 0.5D, at.z, 20, 0.4D, 0.5D, 0.4D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CROSSBOW_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        discard();
    }

    private void breakApart(ServerLevel level) {
        level.sendParticles(COBWEB, getX(), getY() + 0.2D, getZ(), 6, 0.1D, 0.1D, 0.1D, 0.02D);
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!isRemoved() && level() instanceof ServerLevel level) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
            breakApart(level);
        }
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("TripOwner") ? tag.getUUID("TripOwner") : null;
        lifetime = tag.getInt("TripLifetime");
        tickCount = tag.getInt("TripAge");
        entityData.set(LINE_END, new Vector3f(tag.getFloat("TripEndX"), tag.getFloat("TripEndY"), tag.getFloat("TripEndZ")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("TripOwner", owner);
        }
        tag.putInt("TripLifetime", lifetime);
        tag.putInt("TripAge", tickCount);
        Vector3f end = entityData.get(LINE_END);
        tag.putFloat("TripEndX", end.x);
        tag.putFloat("TripEndY", end.y);
        tag.putFloat("TripEndZ", end.z);
    }
}

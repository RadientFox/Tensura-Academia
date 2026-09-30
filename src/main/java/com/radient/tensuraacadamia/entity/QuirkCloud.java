package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.CloudQuirk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuirkCloud extends Entity {
    public static final int RIDE = 0, BLIND = 1, STORM = 2;
    public static final int STORM_TICKS = 100, BLIND_TICKS = 300;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(QuirkCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FOLLOWING = SynchedEntityData.defineId(QuirkCloud.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SPEED_TIER = SynchedEntityData.defineId(QuirkCloud.class, EntityDataSerializers.INT);
    private UUID creator, victim;
    private boolean mastered, blindArrived;
    private int followerIndex;
    private long born, blindEnds;
    private final Set<UUID> stormHits = new HashSet<>();
    private final Map<UUID, Vec3> stormPinned = new HashMap<>();

    public QuirkCloud(EntityType<? extends QuirkCloud> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, RIDE);
        builder.define(FOLLOWING, false);
        builder.define(SPEED_TIER, 0);
    }
    public int kind() { return entityData.get(KIND); }
    public UUID creator() { return creator; }
    public void makeRide(LivingEntity owner, int index, boolean mastered) {
        entityData.set(KIND, RIDE);
        entityData.set(SPEED_TIER, CloudQuirk.speedTier(owner));
        creator = owner.getUUID(); followerIndex = index; this.mastered = mastered;
        born = level().getGameTime();
    }
    public void makeBlind(LivingEntity owner, LivingEntity target, boolean mastered) {
        entityData.set(KIND, BLIND);
        creator = owner.getUUID(); victim = target.getUUID(); this.mastered = mastered;
        born = level().getGameTime();
        setPos(owner.getX(), owner.getEyeY() - 0.3, owner.getZ());
    }
    public void makeStorm(LivingEntity owner, double groundY) {
        entityData.set(KIND, STORM);
        creator = owner.getUUID(); born = level().getGameTime();
        setPos(owner.getX(), groundY + 50, owner.getZ());
    }

    @Override public void tick() {
        super.tick();
        setNoGravity(true);
        if (kind() == RIDE) {
            if (level() instanceof ServerLevel server) ride(server);
            else if (!entityData.get(FOLLOWING) && isControlledByLocalInstance()
                    && getControllingPassenger() instanceof Player driver) {
                steer(driver);
                move(MoverType.SELF, getDeltaMovement());
                driver.fallDistance = 0;
            }
            return;
        }
        if (!(level() instanceof ServerLevel server)) return;
        switch (kind()) {
            case BLIND -> blind(server);
            case STORM -> storm(server);
            default -> discard();
        }
    }

    private void ride(ServerLevel server) {
        Player owner = creator == null ? null : server.getPlayerByUUID(creator);
        if (owner == null || !owner.isAlive() || owner.level() != level()) { discard(); return; }
        entityData.set(SPEED_TIER, CloudQuirk.speedTier(owner));
        double range = CloudQuirk.mastered(owner) ? 200 : 100;
        if (owner.distanceToSqr(this) > range * range) { discard(); return; }
        boolean following = owner.getVehicle() instanceof QuirkCloud leader && leader != this
                && leader.kind() == RIDE && creator.equals(leader.creator());
        entityData.set(FOLLOWING, following);
        if (following && owner.getVehicle() instanceof QuirkCloud leader) {
            double angle = Math.toRadians(leader.getYRot());
            Vec3 forward = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            Vec3 side = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 goal = leader.position().subtract(forward.scale(3.2 + followerIndex * 1.2))
                    .add(side.scale(followerIndex % 2 == 0 ? 2.4 : -2.4));
            double followerSpeed = 1.0 + 0.15 * entityData.get(SPEED_TIER);
            Vec3 desired = goal.subtract(position()).scale(0.22 * followerSpeed);
            if (desired.lengthSqr() > 0.75 * 0.75 * followerSpeed * followerSpeed)
                desired = desired.normalize().scale(0.75 * followerSpeed);
            setDeltaMovement(desired);
            setYRot(leader.getYRot());
            move(MoverType.SELF, getDeltaMovement());
        } else if (getControllingPassenger() == null) {
            setDeltaMovement(getDeltaMovement().scale(0.65));
            move(MoverType.SELF, getDeltaMovement());
        }
        for (Entity passenger : getPassengers()) passenger.fallDistance = 0;
    }

    private void steer(Player driver) {
        Vec3 forward = driver.getLookAngle();
        Vec3 left = new Vec3(forward.z, 0, -forward.x).normalize();
        double speed = 1.0 + 0.15 * entityData.get(SPEED_TIER);
        Vec3 desired = forward.scale(driver.zza * 0.52 * speed).add(left.scale(driver.xxa * 0.42 * speed));
        if (desired.lengthSqr() > 0.58 * 0.58 * speed * speed)
            desired = desired.normalize().scale(0.58 * speed);
        setDeltaMovement(getDeltaMovement().scale(0.68).add(desired.scale(0.32)));
        setYRot(driver.getYRot());
    }

    private void blind(ServerLevel server) {
        Entity entity = victim == null ? null : server.getEntity(victim);
        if (!(entity instanceof LivingEntity target) || !target.isAlive()) { discard(); return; }
        Vec3 face = target.getEyePosition().add(target.getLookAngle().scale(0.3));
        if (!blindArrived) {
            Vec3 direction = face.subtract(position());
            Vec3 step = direction.lengthSqr() > 1.6 * 1.6 ? direction.normalize().scale(1.6) : direction;
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            if (position().distanceToSqr(face) < 0.09) {
                blindArrived = true;
                blindEnds = server.getGameTime() + BLIND_TICKS;
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));
            }
            if (server.getGameTime() - born > 80) discard();
            return;
        }
        setPos(face.x, face.y, face.z);
        if (target instanceof Mob mob) mob.setTarget(null);
        if (server.getGameTime() >= blindEnds) discard();
    }

    private void storm(ServerLevel server) {
        long age = server.getGameTime() - born;
        if (age >= STORM_TICKS) { discard(); return; }
        setPos(getX(), getY() - 0.5, getZ());
        Entity caster = creator == null ? null : server.getEntity(creator);
        AABB volume = new AABB(getX() - 50, getY(), getZ() - 50,
                getX() + 50, getY() + 50, getZ() + 50);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, volume,
                candidate -> candidate.isAlive() && !candidate.getUUID().equals(creator))) {
            if (stormHits.add(target.getUUID())) {
                stormPinned.put(target.getUUID(), target.position());
                target.hurt(caster == null ? damageSources().magic() : damageSources().indirectMagic(this, caster), 100);
            }
        }
        stormPinned.forEach((id, spot) -> {
            Entity target = server.getEntity(id);
            if (target instanceof LivingEntity living && living.isAlive()) {
                living.teleportTo(spot.x, spot.y, spot.z);
                living.setDeltaMovement(Vec3.ZERO);
                living.hurtMarked = true;
            }
        });
    }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (kind() != RIDE || getPassengers().size() >= 2 || player.isPassenger()) return InteractionResult.PASS;
        if (!level().isClientSide) player.startRiding(this, true);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override protected boolean canAddPassenger(Entity passenger) {
        return kind() == RIDE && passenger instanceof Player && getPassengers().size() < 2;
    }
    @Override public LivingEntity getControllingPassenger() {
        if (kind() != RIDE) return null;
        return getFirstPassenger() instanceof LivingEntity living ? living : null;
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        int seat = getPassengers().indexOf(passenger);
        double side = seat == 0 ? -0.62 : 0.62;
        double angle = Math.toRadians(getYRot());
        move.accept(passenger, getX() + Math.cos(angle) * side,
                getY() + 0.55, getZ() + Math.sin(angle) * side);
    }
    @Override public boolean isPickable() { return kind() == RIDE; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return kind() == RIDE; }
    @Override public AABB getBoundingBoxForCulling() {
        return kind() == STORM ? new AABB(getX() - 50, getY(), getZ() - 50,
                getX() + 50, getY() + 50, getZ() + 50) : super.getBoundingBoxForCulling();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("Kind"));
        if (tag.hasUUID("Creator")) creator = tag.getUUID("Creator");
        if (tag.hasUUID("Victim")) victim = tag.getUUID("Victim");
        mastered = tag.getBoolean("Mastered");
        entityData.set(SPEED_TIER, tag.getInt("SpeedTier"));
        blindArrived = tag.getBoolean("BlindArrived");
        followerIndex = tag.getInt("FollowerIndex");
        born = tag.getLong("Born");
        blindEnds = tag.getLong("BlindEnds");
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", kind());
        if (creator != null) tag.putUUID("Creator", creator);
        if (victim != null) tag.putUUID("Victim", victim);
        tag.putBoolean("Mastered", mastered);
        tag.putInt("SpeedTier", entityData.get(SPEED_TIER));
        tag.putBoolean("BlindArrived", blindArrived);
        tag.putInt("FollowerIndex", followerIndex);
        tag.putLong("Born", born);
        tag.putLong("BlindEnds", blindEnds);
    }
}

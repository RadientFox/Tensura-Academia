package com.radient.tensuraacadamia.quirks.barrier;

import com.radient.tensuraacadamia.quirks.alchemy.MoltenShield;
import com.radient.tensuraacadamia.quirks.homing.HomingBarrageProjectile;
import com.radient.tensuraacadamia.quirks.queen_beam.QueenBeamProjectile;

import com.radient.tensuraacadamia.quirks.homing.HomingSteering;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DomeBarrier extends Entity {
    public static final String DEFLECTED_UNTIL = "BarrierDeflectedUntil";
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(DomeBarrier.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MASTERED = SynchedEntityData.defineId(DomeBarrier.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> HIT = SynchedEntityData.defineId(DomeBarrier.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Long> HIT_AT = SynchedEntityData.defineId(DomeBarrier.class, EntityDataSerializers.LONG);
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
    private final Map<UUID, Vec3> occupants = new HashMap<>();
    public record Impact(Vec3 point, long at) { }
    private final java.util.ArrayList<Impact> impacts = new java.util.ArrayList<>();
    private UUID creator;
    private int generation;
    public DomeBarrier(EntityType<? extends DomeBarrier> type, Level level) { super(type, level); noPhysics = true; setNoGravity(true); }
    public DomeBarrier(Level level, LivingEntity owner, int size, boolean mastered, int generation) {
        this(BarrierEntities.DOME.get(), level); creator = owner.getUUID(); this.generation = generation;
        entityData.set(SIZE, size); entityData.set(MASTERED, mastered); setPos(owner.getX(), owner.getY() - .05, owner.getZ());
        occupants.put(owner.getUUID(), owner.position());
        level.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, .5F, 1.7F);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 3); builder.define(MASTERED, false); builder.define(HIT, new Vector3f()); builder.define(HIT_AT, -100L);
    }
    public UUID creatorId() { return creator; }
    public int size() { return entityData.get(SIZE); }
    public boolean mastered() { return entityData.get(MASTERED); }
    public Vec3 hitPoint() { return new Vec3(entityData.get(HIT)); }
    public long hitAt() { return entityData.get(HIT_AT); }
    public List<Impact> impacts() { return impacts; }
    public double radius() { return size() * .5; }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(size(), size()); }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key); if (key == SIZE) refreshDimensions();
        if (key == HIT_AT && level().isClientSide) { impacts.add(new Impact(hitPoint(), hitAt())); if (impacts.size() > 4) impacts.removeFirst(); }
    }
    @Override public boolean isPickable() { return false; }
    private Vec3 unit(Vec3 point, double padding) {
        Vec3 offset = point.subtract(position()); return new Vec3(offset.x / (radius() + padding), offset.y / (size() + padding), offset.z / (radius() + padding));
    }
    public boolean contains(Vec3 point) { return point.y >= getY() && unit(point, 0).lengthSqr() < 1; }
    // check the dome shell and floor, not its bounding box
    public Vec3 crossing(Vec3 from, Vec3 to, double padding) {
        if (padding > 0 && from.y >= getY() - padding && unit(from, padding).lengthSqr() <= 1) return from;
        Vec3 a = unit(from, padding), d = unit(to, padding).subtract(a);
        double best = Double.POSITIVE_INFINITY, A = d.lengthSqr(), B = 2 * a.dot(d), C = a.lengthSqr() - 1;
        double discriminant = B * B - 4 * A * C;
        if (A > 1e-12 && discriminant >= 0) {
            double root = Math.sqrt(discriminant);
            for (double t : new double[]{(-B - root) / (2 * A), (-B + root) / (2 * A)})
                if (t >= 0 && t <= 1 && from.lerp(to, t).y >= getY() - padding) best = Math.min(best, t);
        }
        if (Math.abs(to.y - from.y) > 1e-10) {
            double t = (getY() - padding - from.y) / (to.y - from.y);
            if (t >= 0 && t <= 1) {
                Vec3 point = unit(from.lerp(to, t), padding);
                if (point.x * point.x + point.z * point.z <= 1) best = Math.min(best, t);
            }
        }
        return Double.isFinite(best) ? from.lerp(to, best) : null;
    }
    public Vec3 normal(Vec3 point) {
        if (Math.abs(point.y - getY()) < .01) return new Vec3(0, -1, 0);
        Vec3 offset = point.subtract(position());
        return new Vec3(offset.x / (radius() * radius()), offset.y / (size() * size()), offset.z / (radius() * radius())).normalize();
    }
    private LivingEntity owner() {
        return level() instanceof ServerLevel server && creator != null && server.getEntity(creator) instanceof LivingEntity living ? living : null;
    }
    private boolean active() {
        var owner = owner(); return !isRemoved() && owner != null && owner.isAlive() && TensuraStorages.getExistenceFrom(owner).getAura() > 0;
    }
    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel server)) return;
        var owner = owner(); var instance = owner == null ? null : SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.BARRIER.get()).orElse(null);
        if (!active() || instance == null || instance.getOrCreateTag().getInt(BarrierQuirk.GENERATION) != generation) { discard(); return; }
        // catch teleports that skip movement collision
        for (var target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox(), living -> !living.isSpectator() && living.isAlive()))
            if (contains(target.getBoundingBox().getCenter())) occupants.put(target.getUUID(), target.position());
        occupants.entrySet().removeIf(entry -> {
            if (!(server.getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) return true;
            if (!contains(target.getBoundingBox().getCenter())) {
                Vec3 point = entry.getValue();
                if (target instanceof ServerPlayer player) player.connection.teleport(point.x, point.y, point.z, player.getYRot(), player.getXRot());
                else target.teleportTo(point.x, point.y, point.z);
                target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true;
            }
            return false;
        });
    }
    private void ripple(Vec3 point) {
        entityData.set(HIT, point.subtract(position()).toVector3f()); entityData.set(HIT_AT, level().getGameTime());
        if (tickCount % 3 == 0) level().playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, .3F, 1.4F);
    }
    public enum Outcome { ABSORB, REFLECT, REDIRECT }
    public static Outcome outcome(boolean mastered, double roll) { return !mastered ? Outcome.ABSORB : roll < .1 ? Outcome.REFLECT : roll < .3 ? Outcome.REDIRECT : Outcome.ABSORB; }
    public Outcome absorb(Vec3 point, DamageSource source, float damage) {
        ripple(point);
        var owner = owner(); if (owner == null) return Outcome.ABSORB;
        Outcome outcome = outcome(mastered(), random.nextDouble());
        if (outcome == Outcome.ABSORB && Float.isFinite(damage)) {
            var energy = TensuraStorages.getExistenceFrom(owner);
            energy.setAura(Math.max(0, energy.getAura() - Math.max(0, damage) * BarrierQuirk.AURA_PER_DAMAGE)); energy.markDirty();
        } else if (outcome == Outcome.REFLECT && source != null && !(source.getDirectEntity() instanceof Projectile)
                && source.getEntity() instanceof LivingEntity attacker && attacker != owner && Float.isFinite(damage) && !REFLECTING.get()) {
            REFLECTING.set(true);
            try { attacker.invulnerableTime = 0; attacker.hurt(owner.damageSources().source(DamageTypes.MAGIC, owner), damage); }
            finally { REFLECTING.set(false); }
        }
        return outcome;
    }
    private static List<DomeBarrier> crossings(Level level, Vec3 from, Vec3 to, double padding, boolean incomingOnly) {
        return level.getEntitiesOfClass(DomeBarrier.class, new AABB(from, to).inflate(padding + .1),
                dome -> !dome.isRemoved() && (!incomingOnly || !dome.contains(from)) && dome.crossing(from, to, padding) != null)
                .stream().sorted(java.util.Comparator.comparingDouble(dome -> dome.crossing(from, to, padding).distanceToSqr(from))).toList();
    }
    public static boolean blockDamage(ServerLevel level, Vec3 from, Vec3 to, DamageSource source, float damage) {
        if (REFLECTING.get()) return false;
        for (var dome : crossings(level, from, to, 0, true)) if (dome.active()) {
            dome.absorb(dome.crossing(from, to, 0), source, damage); return true;
        }
        return false;
    }
    public static Vec3 clipBeam(ServerLevel level, Vec3 from, Vec3 to, double width, DamageSource source, float damage) {
        for (var dome : crossings(level, from, to, width * .5, true)) if (dome.active()) {
            Vec3 point = dome.crossing(from, to, width * .5);
            if (source != null && damage > 0) dome.absorb(point, source, damage);
            return point;
        }
        return to;
    }
    public static boolean shields(ServerLevel level, Vec3 from, Vec3 to) {
        return crossings(level, from, to, 0, true).stream().anyMatch(DomeBarrier::active);
    }
    public static Vec3 clipBeamAndWalls(ServerLevel level, Vec3 from, Vec3 to, double width, LivingEntity attacker) {
        return clipBeamAndWalls(level, from, to, width, attacker, null, 0);
    }
    public static Vec3 clipBeamAndWalls(ServerLevel level, Vec3 from, Vec3 to, double width, LivingEntity attacker, DamageSource source, float damage) {
        Vec3 wall = MoltenShield.clipBeam(level, from, to, width, attacker), dome = clipBeam(level, from, to, width, null, 0);
        // only damage the first barrier hit
        return dome.distanceToSqr(from) < wall.distanceToSqr(from)
                ? clipBeam(level, from, to, width, source, damage) : MoltenShield.clipBeam(level, from, to, width, attacker, source, damage);
    }
    public static boolean blockProjectile(ServerLevel level, Projectile projectile) {
        Vec3 from = projectile.position(), to = from.add(projectile.getDeltaMovement());
        for (var dome : crossings(level, from, to, projectile.getBbWidth() * .5, true)) if (dome.active()) {
            float damage = projectile instanceof TensuraProjectile magic ? Math.max(0, magic.getDamage()) + Math.max(0, magic.getSecondaryDamage())
                    : projectile instanceof ThrownTrident ? 8 : projectile instanceof AbstractArrow arrow ? (float) Math.ceil(arrow.getBaseDamage() * projectile.getDeltaMovement().length())
                    : projectile instanceof SmallFireball ? 5 : projectile instanceof LargeFireball ? 6
                    : projectile instanceof WitherSkull ? projectile.getOwner() == null ? 5 : 8
                    : projectile instanceof QueenBeamProjectile queen ? queen.isPrincess() ? 10 : 20 : 0;
            if (projectile instanceof AbstractArrow && projectile.getPersistentData().getBoolean("TracadamiaSnipe")) damage *= 2;
            Vec3 point = dome.crossing(from, to, projectile.getBbWidth() * .5);
            var outcome = dome.absorb(point, null, damage);
            if (outcome == Outcome.ABSORB) projectile.discard();
            else {
                Vec3 normal = dome.normal(point), velocity = projectile.getDeltaMovement();
                Vec3 direction = outcome == Outcome.REFLECT && projectile.getOwner() != null
                        ? projectile.getOwner().getEyePosition().subtract(point).normalize()
                        : velocity.subtract(normal.scale(velocity.dot(normal))).normalize().add(normal.scale(.35)).normalize();
                if (direction.lengthSqr() < .01) direction = normal;
                projectile.setPos(point.add(normal.scale(.15))); projectile.setDeltaMovement(direction.scale(Math.max(.1, velocity.length())));
                var attacker = projectile.getOwner();
                if (outcome == Outcome.REFLECT) projectile.setOwner(dome.owner());
                HomingSteering.clear(projectile); projectile.getPersistentData().remove("TracadamiaHomingTarget");
                projectile.getPersistentData().putLong(DEFLECTED_UNTIL, level.getGameTime() + 40);
                if (projectile instanceof TensuraFlyingProjectile magic) magic.setHomingTarget(outcome == Outcome.REFLECT ? attacker : null);
                if (projectile instanceof HomingBarrageProjectile barrage) barrage.setTarget(outcome == Outcome.REFLECT && attacker instanceof LivingEntity living ? living : null);
                projectile.hurtMarked = true;
            }
            return true;
        }
        return false;
    }
    public static Vec3 limitMovement(Entity entity, Vec3 movement) {
        if (entity instanceof DomeBarrier || entity instanceof Projectile || entity.isSpectator() || movement.lengthSqr() < 1e-10) return movement;
        Vec3 from = entity.getBoundingBox().getCenter(), to = from.add(movement);
        for (var dome : crossings(entity.level(), from, to, 0, false)) {
            Vec3 point = dome.crossing(from, to, 0), normal = dome.normal(point);
            Vec3 before = point.subtract(movement.normalize().scale(.025));
            Vec3 rest = to.subtract(point), slide = rest.subtract(normal.scale(rest.dot(normal)));
            Vec3 end = before.add(slide);
            if (dome.crossing(before, end, 0) != null) end = before;
            movement = end.subtract(from); to = end;
        }
        return movement;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (creator != null) tag.putUUID("Creator", creator); tag.putInt("Size", size()); tag.putBoolean("Mastered", mastered()); tag.putInt("Generation", generation);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        creator = tag.hasUUID("Creator") ? tag.getUUID("Creator") : null;
        entityData.set(SIZE, java.util.Arrays.stream(BarrierQuirk.SIZES).anyMatch(size -> size == tag.getInt("Size")) ? tag.getInt("Size") : 3);
        entityData.set(MASTERED, tag.getBoolean("Mastered")); generation = tag.getInt("Generation");
    }
}

package com.radient.tensuraacadamia.quirks.acid;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class AcidProjectile extends Projectile {
    public static final int SHOT = 0, ALMA = 1, VEIL = 2, MARK = 3;
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(AcidProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STACKS = SynchedEntityData.defineId(AcidProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> FACING = SynchedEntityData.defineId(AcidProjectile.class, EntityDataSerializers.VECTOR3);
    private static final DustParticleOptions ACID_DUST = new DustParticleOptions(new Vector3f(.63F, .63F, .7F), .65F);

    public AcidProjectile(EntityType<? extends AcidProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public AcidProjectile(Level level, LivingEntity owner, int form, int stacks) {
        this(AcidEntities.ACID.get(), level);
        setOwner(owner);
        entityData.set(FORM, form);
        entityData.set(STACKS, stacks);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, SHOT);
        builder.define(STACKS, 1);
        builder.define(FACING, new Vector3f(0, 0, 1));
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(form() == ALMA ? 2 : form() == VEIL ? 3 : .4F, form() == VEIL ? 3 : form() == ALMA ? 2 : .4F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (FORM.equals(accessor)) refreshDimensions();
    }

    public int form() {
        return entityData.get(FORM);
    }

    public int stacks() {
        return entityData.get(STACKS);
    }

    public Vec3 facing() {
        if (form() < VEIL && getDeltaMovement().lengthSqr() > 1e-8) return getDeltaMovement().normalize();
        return new Vec3(entityData.get(FACING)).normalize();
    }

    public void setFacing(Vec3 direction) {
        if (direction.lengthSqr() < 1e-8) return;
        direction = direction.normalize();
        entityData.set(FACING, direction.toVector3f());
        setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        setXRot((float) -Math.toDegrees(Math.asin(Math.clamp(direction.y, -1, 1))));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void updateRotation() {

        if (form() < VEIL && getDeltaMovement().lengthSqr() > 1e-8) setFacing(getDeltaMovement());
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
        updateRotation();
    }

    public static Vec3 right(Vec3 forward) {
        Vec3 side = forward.cross(new Vec3(0, 1, 0));
        return side.lengthSqr() < 1e-8 ? new Vec3(1, 0, 0) : side.normalize();
    }

    public static Vec3 up(Vec3 forward) {
        return right(forward).cross(forward).normalize();
    }

    public static Vec3 handPosition(LivingEntity owner, float partialTick, double reach) {
        Vec3 forward = owner.level().isClientSide ? owner.getViewVector(partialTick) : owner.getLookAngle();
        int hand = owner instanceof Player player && player.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 eye = owner.level().isClientSide ? owner.getEyePosition(partialTick) : owner.getEyePosition();
        return eye.add(forward.scale(reach)).add(right(forward).scale(.4 * hand)).add(0, -.4, 0);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity target && getOwner() instanceof LivingEntity owner && HomingQuirk.isEnemy(owner, target);
    }

    @Override
    public void tick() {
        super.tick();
        int lifetime = form() == MARK ? 60 : form() == VEIL ? 60 : 100;
        if (!level().isClientSide && (tickCount >= lifetime || !(getOwner() instanceof LivingEntity owner) || !owner.isAlive())) {
            discard();
            return;
        }
        if (form() == MARK) return;
        if (form() == VEIL) return;
        if (!level().isClientSide) {
            HitResult hit = level().clip(new ClipContext(position(), position().add(getDeltaMovement()),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 end = hit.getType() == HitResult.Type.MISS ? position().add(getDeltaMovement()) : hit.getLocation();
            var entityHit = ProjectileUtil.getEntityHitResult(level(), this, position(), end,
                    getBoundingBox().expandTowards(getDeltaMovement()).inflate(1), this::canHitEntity, form() == ALMA ? 1 : .3F);
            if (entityHit != null) hit = entityHit;
            if (hit.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) {
                setPos(hit.getLocation());
                onHit(hit);
                return;
            }
        }
        setPos(position().add(getDeltaMovement()));
        updateRotation();
        if (level().isClientSide) for (int i = 0; i < 2; i++)
            level().addParticle(ACID_DUST,
                    getX() + (random.nextDouble() - .5) * .25, getY() + (random.nextDouble() - .5) * .25,
                    getZ() + (random.nextDouble() - .5) * .25, 0, -.025, 0);
    }

    public boolean crossesVeil(Vec3 from, Vec3 to) {
        Vec3 normal = facing(), delta = to.subtract(from), relative = from.subtract(position());
        double denominator = delta.dot(normal);
        if (Math.abs(denominator) < 1e-8 && Math.abs(relative.dot(normal)) > .2) return false;
        double fraction = Math.abs(denominator) < 1e-8 ? 0 : -relative.dot(normal) / denominator;
        if (fraction < 0 || fraction > 1) return false;
        Vec3 offset = relative.add(delta.scale(fraction)), right = right(normal), up = up(normal);
        return Math.abs(offset.dot(right)) <= 1.5 && Math.abs(offset.dot(up)) <= 1.5;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof LivingEntity owner)) return;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && HomingQuirk.isEnemy(owner, target)) {
            AcidQuirk.applyAcid(target, owner, stacks());
            if (form() == ALMA) {
                Vec3 forward = facing(), right = right(forward), up = up(forward);
                Vec3 impact = hit.getLocation();
                for (var behind : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact.add(forward.scale(10))).inflate(8),
                        enemy -> enemy != target && HomingQuirk.isEnemy(owner, enemy))) {
                    Vec3 offset = behind.getBoundingBox().getCenter().subtract(impact);
                    double depth = offset.dot(forward);
                    if (depth >= 0 && depth <= 10 && Math.abs(offset.dot(right)) <= 5 && Math.abs(offset.dot(up)) <= 5)
                        AcidQuirk.applyAcid(behind, owner, (int) Math.ceil(stacks() / 2.0));
                }
            }
        } else if (hit instanceof BlockHitResult block)
            mark(level, owner, hit.getLocation(), Vec3.atLowerCornerOf(block.getDirection().getNormal()));
        splash(level, hit.getLocation(), form() == ALMA ? 2 : .5);
        level.playSound(null, blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, .6F, .8F);
        discard();
    }

    public static void mark(ServerLevel level, LivingEntity owner, Vec3 point, Vec3 normal) {
        var mark = new AcidProjectile(level, owner, MARK, 0);
        mark.setPos(point.add(normal.scale(.025)));
        mark.setFacing(normal);
        level.addFreshEntity(mark);
    }

    public static void splash(ServerLevel level, Vec3 point, double radius) {
        level.sendParticles(MHAParticles.ACID_SPLASH.get(), point.x, point.y, point.z, 0, radius, 0, 0, 1);
    }
}

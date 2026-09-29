package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.VinesQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.UUID;

public final class VineConstruct extends Mob {
    public static final int SNARE = 0, SHIELD = 1, CAGE = 2, DRAG = 3;
    private static final String BINDING = "TracadamiaVineBinding";
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NORMAL = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(VineConstruct.class, EntityDataSerializers.FLOAT);
    private UUID creator, captive, shieldRoot, seeking;
    private BlockPos grappleBlock;
    private Vec3 anchor;
    private static final double GRAPPLE_SPEED = 1.5, GRAPPLE_STOP_DISTANCE = 2;
    private float bonus = 1;
    private boolean mastered, movingTarget;
    private long expiresAt, nextDamage;
    private int missingTicks;
    private Entity lastBeamOwner;
    private ManasSkillInstance lastBeamSkill;
    private long lastBeamTick = Long.MIN_VALUE;

    public VineConstruct(EntityType<? extends VineConstruct> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        noPhysics = true;
        xpReward = 0;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FORM, SNARE);
        builder.define(OWNER, -1);
        builder.define(TARGET, -1);
        builder.define(NORMAL, Direction.NORTH.get3DDataValue());
        builder.define(WIDTH, 0.4F);
        builder.define(HEIGHT, 0.4F);
    }
    public int form() { return entityData.get(FORM); }
    public Entity visualOwner() { return level().getEntity(entityData.get(OWNER)); }
    public Entity visualTarget() { return level().getEntity(entityData.get(TARGET)); }
    public UUID creatorId() { return creator; }
    public long expiresAt() { return expiresAt; }
    public boolean isGrappling() { return grappleBlock != null; }
    public Direction normal() { return Direction.from3DDataValue(entityData.get(NORMAL)); }
    public void configure(LivingEntity owner, int form, boolean mastered, float bonus, float health) {
        this.creator = owner.getUUID();
        this.mastered = mastered;
        this.bonus = bonus;
        entityData.set(FORM, form);
        entityData.set(OWNER, owner.getId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        setHealth(health);
        setPos(owner.getEyePosition());
        expiresAt = level().getGameTime() + Math.round((form == SHIELD ? 100 : form == DRAG ? 80 : 40) * bonus);
    }
    public void configurePanel(Vec3 feet, Vec3 normal, UUID root) {
        shieldRoot = root;
        entityData.set(NORMAL, Direction.getNearest(normal.x, 0, normal.z).get3DDataValue());
        setSize(2.3F * bonus, 4 * bonus);
        setPos(feet);
        blocksBuilding = true;
    }
    private void setSize(float width, float height) {
        entityData.set(WIDTH, width);
        entityData.set(HEIGHT, height);
    }
    @Override protected EntityDimensions getDefaultDimensions(Pose pose) {
        return entityData == null ? super.getDefaultDimensions(pose)
                : EntityDimensions.scalable(entityData.get(WIDTH), entityData.get(HEIGHT));
    }
    @Override protected AABB makeBoundingBox() {
        if (entityData == null || form() != SHIELD) return super.makeBoundingBox();
        double x = normal().getAxis() == Direction.Axis.X ? 0.5 : entityData.get(WIDTH);
        double z = normal().getAxis() == Direction.Axis.Z ? 0.5 : entityData.get(WIDTH);
        return new AABB(getX() - x / 2, getY(), getZ() - z / 2, getX() + x / 2, getY() + entityData.get(HEIGHT), getZ() + z / 2);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (data == WIDTH || data == HEIGHT || data == FORM || data == NORMAL) refreshDimensions();
    }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean isPushable() { return false; }
    @Override public void travel(Vec3 input) { }
    @Override public void push(Entity entity) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (form() == CAGE && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (level() instanceof ServerLevel server && form() == SHIELD && shieldRoot != null && !shieldRoot.equals(getUUID())) {
            Entity root = server.getEntity(shieldRoot);
            return root instanceof VineConstruct vine && vine.hurt(source, amount);
        }
        // A wide beam can hit several panels; all belong to the same shield, not five shields.
        if (form() == SHIELD && level() instanceof ServerLevel) {
            ManasSkillInstance ability = ((TensuraDamageSource) source).tensura$getAbilityInstance();
            if (ability != null && (ability.getSkill() == QuirkSkills.BEAMS_FROM_HIS_EYES.get()
                    || ability.getSkill() == QuirkSkills.IMPURE_BEAM.get())) {
                if (lastBeamTick == level().getGameTime() && lastBeamOwner == source.getEntity() && lastBeamSkill == ability) return false;
                lastBeamTick = level().getGameTime();
                lastBeamOwner = source.getEntity();
                lastBeamSkill = ability;
            }
        }
        invulnerableTime = 0;
        boolean hit = super.hurt(source, amount);
        if (hit && isAlive() && form() == SHIELD && level() instanceof ServerLevel server)
            server.getEntitiesOfClass(VineConstruct.class, getBoundingBox().inflate(12), vine -> getUUID().equals(vine.shieldRoot))
                    .forEach(vine -> vine.setHealth(getHealth()));
        return hit;
    }
    @Override public void die(DamageSource source) {
        if (level() instanceof ServerLevel server && form() == SHIELD && getUUID().equals(shieldRoot))
            server.getEntitiesOfClass(VineConstruct.class, getBoundingBox().inflate(12), vine -> getUUID().equals(vine.shieldRoot))
                    .forEach(VineConstruct::discard);
        discard();
    }
    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && captive != null && level() instanceof ServerLevel server
                && server.getEntity(captive) instanceof LivingEntity target
                && target.getPersistentData().hasUUID(BINDING) && target.getPersistentData().getUUID(BINDING).equals(getUUID()))
            target.getPersistentData().remove(BINDING);
        super.remove(reason);
    }

    public static VineConstruct binding(LivingEntity target) {
        var tag = target.getPersistentData();
        if (!(target.level() instanceof ServerLevel server) || !tag.hasUUID(BINDING)) return null;
        Entity entity = server.getEntity(tag.getUUID(BINDING));
        return entity instanceof VineConstruct vine && vine.isAlive() && target.getUUID().equals(vine.captive)
                && (vine.form() == DRAG || server.getGameTime() < vine.expiresAt) ? vine : null;
    }
    public static boolean blocksMovement(Entity target) {
        return target instanceof LivingEntity living && binding(living) instanceof VineConstruct vine && !vine.movingTarget;
    }
    public static boolean crucified(LivingEntity target) {
        VineConstruct vine = binding(target);
        return vine != null && vine.form() == CAGE;
    }
    public static boolean intercept(LivingEntity victim, DamageSource source, float amount) {
        if (victim instanceof VineConstruct || !(victim.level() instanceof ServerLevel server)
                || source.getDirectEntity() == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Vec3 start = source.getDirectEntity() instanceof LivingEntity living ? living.getEyePosition() : source.getDirectEntity().position();
        Vec3 end = victim.getBoundingBox().getCenter();
        VineConstruct shield = server.getEntitiesOfClass(VineConstruct.class, new AABB(start, end).inflate(0.5),
                        vine -> vine.form() == SHIELD && vine.isAlive() && vine.getBoundingBox().clip(start, end).isPresent())
                .stream().min(Comparator.comparingDouble(vine -> vine.getBoundingBox().clip(start, end).orElse(end).distanceToSqr(start)))
                .orElse(null);
        if (shield == null) return false;
        shield.hurt(source, amount);
        return true;
    }
    public void bind(LivingEntity target) {
        seeking = null;
        captive = target.getUUID();
        entityData.set(TARGET, target.getId());
        target.getPersistentData().putUUID(BINDING, getUUID());
        anchor = target.position();
        setDeltaMovement(Vec3.ZERO);
        setSize(target.getBbWidth() + (form() == CAGE ? 1 : 0.6F), target.getBbHeight() + 0.5F);
        setPos(target.position().subtract(0, 0.2, 0));
        expiresAt = level().getGameTime() + Math.round((form() == CAGE ? 400 : mastered ? 200 : 100) * bonus);
        nextDamage = level().getGameTime() + 20;
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        LivingEntity owner = creator == null ? null : server.getEntity(creator) instanceof LivingEntity living ? living : null;
        if (form() != DRAG || captive == null) if (server.getGameTime() >= expiresAt) { discard(); return; }
        if (owner == null || captive != null && server.getEntity(captive) == null) {
            if (++missingTicks > 40) discard();
            return;
        }
        missingTicks = 0;
        if (!owner.isAlive()) { discard(); return; }
        entityData.set(OWNER, owner.getId());
        if (form() == SHIELD) {
            setDeltaMovement(Vec3.ZERO);
            if (shieldRoot != null && !shieldRoot.equals(getUUID()) && !(server.getEntity(shieldRoot) instanceof VineConstruct)) discard();
        } else if (grappleBlock != null) pullToBlock(server, owner);
        else if (captive == null) fly(server, owner);
        else if (server.getEntity(captive) instanceof LivingEntity target) restrain(server, owner, target);
        else discard();
    }
    private void fly(ServerLevel server, LivingEntity owner) {
        double range = form() == DRAG ? VinesQuirk.VOLLEY_RANGE + 2 : VinesQuirk.VINE_RANGE * bonus;
        if (distanceToSqr(owner) > range * range) { discard(); return; }
        if (form() == DRAG) {
            LivingEntity nearby = seeking != null && server.getEntity(seeking) instanceof LivingEntity living ? living : null;
            if (nearby == null || !VinesQuirk.validTarget(owner, nearby)
                    || owner.distanceToSqr(nearby) > VinesQuirk.VOLLEY_RANGE * VinesQuirk.VOLLEY_RANGE || !hasLineOfSight(nearby))
                nearby = VinesQuirk.nearbyTargets(owner).stream().filter(this::hasLineOfSight).findFirst().orElse(null);
            if (nearby == null) { discard(); return; }
            seek(nearby);
        }
        Vec3 start = position(), end = start.add(getDeltaMovement());
        var block = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(server, this, start, end, new AABB(start, end).inflate(0.5),
                entity -> entity instanceof LivingEntity target && VinesQuirk.validTarget(owner, target), getBbWidth() / 2);
        if (hit != null) { bind((LivingEntity) hit.getEntity()); return; }
        if (!server.hasChunkAt(BlockPos.containing(end))) { discard(); return; }
        if (block.getType() != HitResult.Type.MISS) {
            if (form() == SNARE) {
                grappleBlock = block.getBlockPos().immutable();
                anchor = block.getLocation();
                setPos(anchor);
                setDeltaMovement(Vec3.ZERO);
                expiresAt = server.getGameTime() + Math.round((mastered ? 200 : 100) * bonus);
                pullToBlock(server, owner);
            } else discard();
            return;
        }
        setPos(end);
    }
    private void pullToBlock(ServerLevel server, LivingEntity owner) {
        if (!server.hasChunkAt(grappleBlock) || server.getBlockState(grappleBlock).getCollisionShape(server, grappleBlock).isEmpty()
                || owner.isSpectator()) { discard(); return; }
        Vec3 delta = anchor.subtract(owner.getBoundingBox().getCenter());
        double distance = delta.length();
        owner.fallDistance = 0;
        if (distance <= GRAPPLE_STOP_DISTANCE) {
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            discard();
            return;
        }
        owner.setDeltaMovement(delta.scale(Math.min(GRAPPLE_SPEED * bonus, distance - (GRAPPLE_STOP_DISTANCE - 0.1)) / distance));
        owner.hurtMarked = true;
    }
    public void seek(LivingEntity target) {
        seeking = target.getUUID();
        setDeltaMovement(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(1.2));
    }
    private void restrain(ServerLevel server, LivingEntity owner, LivingEntity target) {
        if (!HomingQuirk.isEnemy(owner, target) || !target.getPersistentData().hasUUID(BINDING)
                || !target.getPersistentData().getUUID(BINDING).equals(getUUID())) { discard(); return; }
        entityData.set(TARGET, target.getId());
        Vec3 desired = anchor;
        if (form() == CAGE) {
            // Lift slowly, using real collision so ceilings are not crossed.
            long elapsed = Math.round(400 * bonus) - (expiresAt - server.getGameTime());
            if (elapsed <= 10) desired = anchor.add(0, 0.3 * bonus, 0);
        } else if (form() == DRAG) {
            double distance = owner.position().distanceTo(target.position());
            if (distance <= 5) { discard(); return; }
            Vec3 step = owner.position().subtract(target.position()).normalize().scale(Math.min(0.12 * bonus, distance - 5));
            desired = target.position().add(step);
        }
        movingTarget = true;
        try { target.move(MoverType.SELF, desired.subtract(target.position())); }
        finally { movingTarget = false; }
        anchor = target.position();
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0;
        if (target instanceof ServerPlayer player)
            player.connection.teleport(anchor.x, anchor.y, anchor.z, player.getYRot(), player.getXRot());
        setPos(anchor.subtract(0, 0.2, 0));
        if (form() != CAGE && server.getGameTime() >= nextDamage) {
            nextDamage = server.getGameTime() + 20;
            DamageSource source = server.damageSources().source(DamageTypes.MOB_ATTACK, this, owner);
            var typed = (TensuraDamageSource) source;
            typed.tensura$setSkillType(Skill.SkillType.UNIQUE);
            typed.tensura$setAbilityMode(form());
            SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.VINES.get()).ifPresent(typed::tensura$setAbilityInstance);
            target.invulnerableTime = 0;
            target.hurt(source, (form() == DRAG ? 20 : 5) * bonus);
        }
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (creator != null) tag.putUUID("VineCreator", creator);
        if (captive != null) tag.putUUID("VineCaptive", captive);
        if (shieldRoot != null) tag.putUUID("VineShield", shieldRoot);
        if (seeking != null) tag.putUUID("VineSeeking", seeking);
        if (grappleBlock != null) tag.putLong("VineGrappleBlock", grappleBlock.asLong());
        tag.putInt("VineForm", form());
        tag.putInt("VineNormal", normal().get3DDataValue());
        tag.putFloat("VineWidth", entityData.get(WIDTH));
        tag.putFloat("VineHeight", entityData.get(HEIGHT));
        tag.putFloat("VineBonus", bonus);
        tag.putBoolean("VineMastered", mastered);
        tag.putLong("VineExpires", expiresAt);
        tag.putLong("VineDamageAt", nextDamage);
        if (anchor != null) { tag.putDouble("VineAnchorX", anchor.x); tag.putDouble("VineAnchorY", anchor.y); tag.putDouble("VineAnchorZ", anchor.z); }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        creator = tag.hasUUID("VineCreator") ? tag.getUUID("VineCreator") : null;
        captive = tag.hasUUID("VineCaptive") ? tag.getUUID("VineCaptive") : null;
        shieldRoot = tag.hasUUID("VineShield") ? tag.getUUID("VineShield") : null;
        seeking = tag.hasUUID("VineSeeking") ? tag.getUUID("VineSeeking") : null;
        grappleBlock = tag.contains("VineGrappleBlock") ? BlockPos.of(tag.getLong("VineGrappleBlock")) : null;
        entityData.set(FORM, tag.getInt("VineForm"));
        entityData.set(NORMAL, tag.getInt("VineNormal"));
        setSize(tag.getFloat("VineWidth"), tag.getFloat("VineHeight"));
        bonus = tag.getFloat("VineBonus");
        mastered = tag.getBoolean("VineMastered");
        expiresAt = tag.getLong("VineExpires");
        nextDamage = tag.getLong("VineDamageAt");
        anchor = tag.contains("VineAnchorX") ? new Vec3(tag.getDouble("VineAnchorX"), tag.getDouble("VineAnchorY"), tag.getDouble("VineAnchorZ")) : null;
        blocksBuilding = form() == SHIELD;
    }
}

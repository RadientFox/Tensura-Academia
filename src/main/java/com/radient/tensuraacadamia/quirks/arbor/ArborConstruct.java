package com.radient.tensuraacadamia.quirks.arbor;

import com.radient.tensuraacadamia.quirks.solid_air.SolidAirWall;
import com.radient.tensuraacadamia.quirks.vines.VineConstruct;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArborConstruct extends Mob {
    public static final int BRANCH = 0, TREE_BRANCH = 1, TREE = 2;
    private static final String BINDING = "ArborBinding";
    private static final EntityDataAccessor<Integer> FORM = SynchedEntityData.defineId(ArborConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ArborConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROOT = SynchedEntityData.defineId(ArborConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ArborConstruct.class, EntityDataSerializers.LONG);
    private UUID creator, captive, seeking, treeId;
    private Vec3 anchor;
    private BlockPos grappleBlock;
    private long deadline, nextPulse;
    private double reach = 8;
    private boolean released, movingTarget;
    private final Map<UUID, UUID> treeBranches = new HashMap<>();

    public ArborConstruct(EntityType<? extends ArborConstruct> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setPersistenceRequired();
        noPhysics = true;
        xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FORM, BRANCH);
        builder.define(OWNER, -1);
        builder.define(ROOT, -1);
        builder.define(START, 0L);
    }

    public int form() {
        return entityData.get(FORM);
    }

    public Entity visualOwner() {
        return level().getEntity(entityData.get(OWNER));
    }

    public Entity visualRoot() {
        return level().getEntity(entityData.get(ROOT));
    }

    public long age() {
        return level().getGameTime() - entityData.get(START);
    }

    public boolean isAttachedOrFlying() {
        return !released && isAlive();
    }

    public UUID creatorId() {
        return creator;
    }

    private void configure(LivingEntity owner, int form, Vec3 point) {
        creator = owner.getUUID();
        entityData.set(OWNER, owner.getId());
        entityData.set(FORM, form);
        entityData.set(START, level().getGameTime());
        refreshDimensions();
        setPos(point);
        setHealth(getMaxHealth());
        TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
    }

    public void configureBranch(LivingEntity owner, LivingEntity target) {
        configure(owner, BRANCH, owner.getEyePosition().add(owner.getLookAngle().scale(.8)));
        seeking = target == null ? null : target.getUUID();
        setDeltaMovement(owner.getLookAngle().scale(1.6));
        deadline = level().getGameTime() + 40;
    }

    public void configureTree(LivingEntity owner, Vec3 point) {
        configure(owner, TREE, point);
        deadline = level().getGameTime() + ArborQuirk.TREE_TICKS;
    }

    private void configureTreeBranch(LivingEntity owner, ArborConstruct tree, LivingEntity target) {
        configure(owner, TREE_BRANCH, tree.position().add(0, 12, 0));
        treeId = tree.getUUID();
        entityData.set(ROOT, tree.getId());
        seeking = target.getUUID();
        deadline = tree.deadline;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return entityData != null && form() == TREE ? EntityDimensions.scalable(30, 30) : EntityDimensions.scalable(1.2F, 2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == FORM) refreshDimensions();
    }

    @Override
    public boolean canBeCollidedWith() {
        return form() != TREE && isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void travel(Vec3 input) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (form() == TREE && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        invulnerableTime = 0;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel server) server.sendParticles(
                new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState()),
                getX(), getY() + 1, getZ(), 16, .5, .6, .5, .1);
        discard();
    }

    private LivingEntity owner(ServerLevel level) {
        return creator != null && level.getEntity(creator) instanceof LivingEntity living ? living : null;
    }

    public static boolean canBind(LivingEntity owner, LivingEntity target) {
        return target != owner && target.isAlive() && !target.isSpectator() && !(target instanceof ArborConstruct)
                && !(target instanceof VineConstruct) && !(target instanceof SolidAirWall)
                && !(target instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().invulnerable)
                && binding(target) == null;
    }

    public static ArborConstruct binding(LivingEntity target) {
        var tag = target.getPersistentData();
        if (!(target.level() instanceof ServerLevel level) || !tag.hasUUID(BINDING)) return null;
        if (level.getEntity(tag.getUUID(BINDING)) instanceof ArborConstruct branch && branch.isAlive() && !branch.released
                && target.getUUID().equals(branch.captive) && level.getGameTime() < branch.deadline) return branch;
        return null;
    }

    public static boolean blocksMovement(Entity entity) {
        if (!(entity instanceof LivingEntity target)) return false;
        var branch = binding(target);
        if (branch == null || branch.movingTarget) return false;
        if (branch.form() == BRANCH) return true;
        return target.level() instanceof ServerLevel level && branch.owner(level) instanceof LivingEntity owner && HomingQuirk.isEnemy(owner, target);
    }

    public void scroll(double delta) {
        reach = Math.clamp(reach + delta, 2, ArborQuirk.BRANCH_RANGE);
    }

    public void release() {
        if (released) return;
        clearBinding();
        captive = null;
        seeking = null;
        grappleBlock = null;
        released = true;
        setDeltaMovement(Vec3.ZERO);
        deadline = level().getGameTime() + ArborQuirk.LINGER_TICKS;
    }

    private void clearBinding() {
        if (captive != null && level() instanceof ServerLevel level && level.getEntity(captive) instanceof LivingEntity target
                && target.getPersistentData().hasUUID(BINDING) && target.getPersistentData().getUUID(BINDING).equals(getUUID()))
            target.getPersistentData().remove(BINDING);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) clearBinding();
        super.remove(reason);
    }

    private void bind(LivingEntity target) {
        captive = target.getUUID();
        seeking = null;
        target.getPersistentData().putUUID(BINDING, getUUID());
        nextPulse = level().getGameTime() + 20;
        anchor = target.position();
        setPos(target.getBoundingBox().getCenter().subtract(0, 1, 0));
        setDeltaMovement(Vec3.ZERO);
        if (form() == BRANCH) {
            var owner = owner((ServerLevel) level());
            reach = owner == null ? 8 : Math.clamp(owner.getEyePosition().distanceTo(target.getBoundingBox().getCenter()), 2, ArborQuirk.BRANCH_RANGE);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) return;
        var owner = owner(level);
        if (owner == null || !owner.isAlive() || !ArborQuirk.hasArbor(owner)) {
            discard();
            return;
        }
        entityData.set(OWNER, owner.getId());
        if (form() == TREE) {
            if (level.getGameTime() >= deadline) discard();
            else if (age() % 20 == 0) attachNearby(level, owner);
            return;
        }
        if (released) {
            if (level.getGameTime() >= deadline) discard();
            return;
        }
        if (form() == BRANCH) {
            var skill = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ARBOR.get()).orElse(null);
            if (skill == null || !ArborQuirk.holding(skill, owner)) {
                release();
                return;
            }
            deadline = level.getGameTime() + 6;
        } else {
            if (!(level.getEntity(treeId) instanceof ArborConstruct tree) || !tree.isAlive() || level.getGameTime() >= deadline) {
                discard();
                return;
            }
            entityData.set(ROOT, tree.getId());
        }
        if (grappleBlock != null) {
            if (!level.hasChunkAt(grappleBlock) || level.getBlockState(grappleBlock).getCollisionShape(level, grappleBlock).isEmpty()) {
                release();
                return;
            }
            Vec3 delta = anchor.subtract(owner.getBoundingBox().getCenter());
            double distance = delta.length();
            if (distance > reach && distance > 2) {
                owner.setDeltaMovement(delta.scale(Math.min(1.2, distance - reach) / distance));
                owner.hurtMarked = true;
                owner.fallDistance = 0;
            }
        } else if (captive == null) fly(level, owner);
        else if (level.getEntity(captive) instanceof LivingEntity target && target.isAlive())
            restrain(level, owner, target);
        else discard();
    }

    private void fly(ServerLevel level, LivingEntity owner) {
        if (seeking != null) {
            if (!(level.getEntity(seeking) instanceof LivingEntity target) || !canBind(owner, target)) {
                discard();
                return;
            }
            setDeltaMovement(target.getBoundingBox().getCenter().subtract(position()).normalize().scale(form() == TREE_BRANCH ? 3 : 1.6));
        }
        Vec3 start = position(), end = start.add(getDeltaMovement());
        if (!level.hasChunkAt(BlockPos.containing(end))) {
            discard();
            return;
        }
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = form() == TREE_BRANCH || block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, this, start, stop, new AABB(start, stop).inflate(.8),
                entity -> entity instanceof LivingEntity target && canBind(owner, target)
                        && (seeking == null || target.getUUID().equals(seeking)), .7F);
        if (hit != null) {
            bind((LivingEntity) hit.getEntity());
            return;
        }
        if (form() == BRANCH && block.getType() != HitResult.Type.MISS) {
            if (seeking != null) {
                release();
                return;
            }
            grappleBlock = block.getBlockPos();
            anchor = block.getLocation();
            reach = owner.getBoundingBox().getCenter().distanceTo(anchor);
            setPos(anchor);
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setPos(end);
        if (form() == BRANCH && owner.distanceToSqr(this) > ArborQuirk.BRANCH_RANGE * ArborQuirk.BRANCH_RANGE)
            release();
    }

    private void restrain(ServerLevel level, LivingEntity owner, LivingEntity target) {
        if (!target.getPersistentData().hasUUID(BINDING) || !getUUID().equals(target.getPersistentData().getUUID(BINDING))) {
            discard();
            return;
        }
        if (form() == BRANCH) {
            Vec3 desired = owner.getEyePosition().add(owner.getLookAngle().scale(reach)).subtract(0, target.getBbHeight() / 2, 0);
            moveCaptive(target, desired.subtract(target.position()), .7);
        } else {
            var tree = (ArborConstruct) level.getEntity(treeId);
            if (!tree.area().intersects(target.getBoundingBox())) {
                discard();
                return;
            }
            if (HomingQuirk.isEnemy(owner, target)) {
                Vec3 desired = tree.position().add(0, 1, 0);
                moveCaptive(target, desired.subtract(target.position()), .18);
                if (tree.getBoundingBox().contains(target.getBoundingBox().getCenter())) hit(level, owner, target);
                if (level.getGameTime() >= nextPulse && target.isAlive()) {
                    nextPulse = level.getGameTime() + 20;
                    owner.heal(10);
                    var from = TensuraStorages.getExistenceFrom(target);
                    var to = TensuraStorages.getExistenceFrom(owner);
                    double aura = Math.max(0, from.getAura()) * .01, mp = Math.max(0, from.getMagicule()) * .01;
                    from.setAura(from.getAura() - aura);
                    from.setMagicule(from.getMagicule() - mp);
                    to.setAura(Math.min(EnergyHelper.getMaxAura(owner), to.getAura() + aura));
                    to.setMagicule(Math.min(EnergyHelper.getMaxMagicule(owner), to.getMagicule() + mp));
                    from.markDirty();
                    to.markDirty();
                    var effect = switch (random.nextInt(3)) {
                        case 0 -> MobEffects.WEAKNESS;
                        case 1 -> MobEffects.MOVEMENT_SLOWDOWN;
                        default ->
                                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.wrapAsHolder(TensuraMobEffects.PARALYSIS.get());
                    };
                    var old = target.getEffect(effect);
                    target.addEffect(new MobEffectInstance(effect, 60, old == null ? 0 : Math.min(255, old.getAmplifier() + 1)));
                }
            } else if (level.getGameTime() >= nextPulse) {
                nextPulse = level.getGameTime() + 20;
                var energy = TensuraStorages.getExistenceFrom(target);
                energy.setAura(Math.min(EnergyHelper.getMaxAura(target), energy.getAura() + EnergyHelper.getMaxAura(target) * ArborQuirk.ALLY_REGEN));
                energy.setMagicule(Math.min(EnergyHelper.getMaxMagicule(target), energy.getMagicule() + EnergyHelper.getMaxMagicule(target) * ArborQuirk.ALLY_REGEN));
                energy.markDirty();
            }
        }
        setPos(target.getBoundingBox().getCenter().subtract(0, 1, 0));
    }

    private void moveCaptive(LivingEntity target, Vec3 delta, double speed) {
        movingTarget = true;
        try {
            target.move(MoverType.SELF, delta.length() > speed ? delta.normalize().scale(speed) : delta);
        } finally {
            movingTarget = false;
        }
        anchor = target.position();
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0;
        if (target instanceof ServerPlayer player)
            player.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
    }

    private void hit(ServerLevel level, LivingEntity owner, LivingEntity target) {
        var source = level.damageSources().source(DamageTypes.MOB_ATTACK, this, owner);
        var typed = (TensuraDamageSource) source;
        SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ARBOR.get()).ifPresent(typed::tensura$setAbilityInstance);
        typed.tensura$setAbilityMode(2);
        typed.tensura$setSkillType(io.github.manasmods.tensura.ability.skill.Skill.SkillType.UNIQUE);
        target.invulnerableTime = 0;
        target.hurt(source, 20);
    }

    private void attachNearby(ServerLevel level, LivingEntity owner) {
        treeBranches.entrySet().removeIf(entry -> !(level.getEntity(entry.getValue()) instanceof ArborConstruct branch) || !branch.isAlive());
        for (var target : level.getEntitiesOfClass(LivingEntity.class, area(), target -> canBind(owner, target))) {
            if (treeBranches.containsKey(target.getUUID())) continue;
            var branch = ArborEntities.CONSTRUCT.get().create(level);
            if (branch == null) continue;
            branch.configureTreeBranch(owner, this, target);
            if (level.addFreshEntity(branch)) treeBranches.put(target.getUUID(), branch.getUUID());
        }
    }

    private AABB area() {
        return new AABB(position().add(-ArborQuirk.TREE_RADIUS, -ArborQuirk.TREE_RADIUS, -ArborQuirk.TREE_RADIUS),
                position().add(ArborQuirk.TREE_RADIUS, ArborQuirk.TREE_RADIUS, ArborQuirk.TREE_RADIUS));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (creator != null) tag.putUUID("Creator", creator);
        if (captive != null) tag.putUUID("Captive", captive);
        if (seeking != null) tag.putUUID("Seeking", seeking);
        if (treeId != null) tag.putUUID("Tree", treeId);
        if (grappleBlock != null) tag.putLong("Grapple", grappleBlock.asLong());
        if (anchor != null) {
            tag.putDouble("AnchorX", anchor.x);
            tag.putDouble("AnchorY", anchor.y);
            tag.putDouble("AnchorZ", anchor.z);
        }
        tag.putInt("Form", form());
        tag.putLong("Start", entityData.get(START));
        tag.putLong("Deadline", deadline);
        tag.putDouble("Reach", reach);
        tag.putBoolean("Released", released);
        tag.putLong("NextPulse", nextPulse);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        creator = tag.hasUUID("Creator") ? tag.getUUID("Creator") : null;
        captive = tag.hasUUID("Captive") ? tag.getUUID("Captive") : null;
        seeking = tag.hasUUID("Seeking") ? tag.getUUID("Seeking") : null;
        treeId = tag.hasUUID("Tree") ? tag.getUUID("Tree") : null;
        grappleBlock = tag.contains("Grapple") ? BlockPos.of(tag.getLong("Grapple")) : null;
        anchor = tag.contains("AnchorX") ? new Vec3(tag.getDouble("AnchorX"), tag.getDouble("AnchorY"), tag.getDouble("AnchorZ")) : null;
        entityData.set(FORM, tag.getInt("Form"));
        entityData.set(START, tag.getLong("Start"));
        refreshDimensions();
        deadline = tag.getLong("Deadline");
        reach = Math.clamp(tag.getDouble("Reach"), 2, ArborQuirk.BRANCH_RANGE);
        released = tag.getBoolean("Released");
        nextPulse = tag.getLong("NextPulse");
        TensuraStorages.getExistenceFrom(this).setSkippingEPDrop(true);
    }
}

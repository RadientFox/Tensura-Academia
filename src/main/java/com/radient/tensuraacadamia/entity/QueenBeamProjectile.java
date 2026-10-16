package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.QueenBeamQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.intrinsic.CharmSkill;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.entity.template.subclass.ISubordinate;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.SubordinateHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class QueenBeamProjectile extends Projectile {
    public static final float SPEED = 1.5F;
    private static final int MAX_FLIGHT_TICKS = 100;
    private static final EntityDataAccessor<Boolean> PRINCESS = SynchedEntityData.defineId(
            QueenBeamProjectile.class, EntityDataSerializers.BOOLEAN);
    private final Set<UUID> hitTargets = new HashSet<>();
    private int ricochets;

    public QueenBeamProjectile(EntityType<? extends QueenBeamProjectile> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(PRINCESS, false); }

    public boolean isPrincess() { return entityData.get(PRINCESS); }

    public void setPrincess(boolean princess) { entityData.set(PRINCESS, princess); }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PRINCESS.equals(key)) refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float size = isPrincess() ? 0.3F : 0.6F;
        return EntityDimensions.scalable(size, size);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 movement = getDeltaMovement();
        if (!level().isClientSide) {
            if (tickCount > MAX_FLIGHT_TICKS || !(getOwner() instanceof LivingEntity owner) || !owner.isAlive()) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                if (!net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hit)) onHit(hit);
                return;
            }
        } else {
            level().addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.12F, 0.5F),
                            isPrincess() ? 0.4F : 0.8F), getX(), getY(), getZ(), 0, 0, 0);
        }
        setPos(position().add(movement));
        updateRotation();
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity target && getOwner() instanceof LivingEntity owner
                && isEnemy(owner, target) && !hitTargets.contains(target.getUUID());
    }

    private static boolean isEnemy(LivingEntity owner, LivingEntity target) {
        return target != owner && target.isAlive() && !target.isSpectator()
                && !(target instanceof Player player && player.getAbilities().invulnerable)
                && !owner.isAlliedTo(target) && !SubordinateHelper.isAlly(owner, target);
    }

    @Override
    protected void onHit(HitResult hit) {
        if (!(getOwner() instanceof LivingEntity owner)) { discard(); return; }
        if (!isPrincess()) {
            boolean charmed = false;
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(hit.getLocation(), 3, 3, 3), victim -> isEnemy(owner, victim))) {
                charmed |= strike(owner, target, 20.0F);
            }
            if (charmed) playCharmSound();
            discard();
            return;
        }
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity target)) {
            discard();
            return;
        }
        hitTargets.add(target.getUUID());
        if (strike(owner, target, 10.0F)) playCharmSound();
        if (ricochets >= QueenBeamQuirk.MAX_RICOCHETS) { discard(); return; }
        LivingEntity next = nearestEnemy(owner);
        if (next == null) { discard(); return; }
        ricochets++;
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 2.0F, 1.1F + ricochets * 0.2F);
        Vec3 direction = next.getBoundingBox().getCenter().subtract(position()).normalize();
        setDeltaMovement(direction.scale(SPEED));
        hasImpulse = true;
    }

    private LivingEntity nearestEnemy(LivingEntity owner) {
        double range = QueenBeamQuirk.RICOCHET_RANGE;
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(range),
                target -> isEnemy(owner, target) && !hitTargets.contains(target.getUUID())
                        && target.getBoundingBox().getCenter().distanceToSqr(position()) <= range * range
                        && level().clip(new ClipContext(position(), target.getBoundingBox().getCenter(),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS)
                .stream().min(Comparator.comparingDouble(target ->
                        target.getBoundingBox().getCenter().distanceToSqr(position()))).orElse(null);
    }

    private void playCharmSound() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.3F, 1.3F);
    }

    private boolean strike(LivingEntity owner, LivingEntity target, float damage) {
        DamageSource source = level().damageSources().source(DamageTypes.MAGIC, this, owner);
        TensuraDamageSource tensura = (TensuraDamageSource) source;
        tensura.tensura$setSkillType(Skill.SkillType.UNIQUE);
        tensura.tensura$setAbilityMode(isPrincess() ? QueenBeamQuirk.PRINCESS : QueenBeamQuirk.QUEEN);
        SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.QUEEN_BEAM.get()).ifPresent(tensura::tensura$setAbilityInstance);
        target.hurt(source, damage);
        if (!target.isAlive() || CharmSkill.isMindControlFailed(owner, target, level(), true)) return false;
        if (TensuraEntityEvents.FORCE_TAME_EVENT.invoker().tame(target, owner, true).isFalse()) return false;
        if (!TensuraMobEffect.addEffect(target, new MobEffectInstance(
                        TensuraMobEffects.getReference(TensuraMobEffects.MIND_CONTROL), QueenBeamQuirk.CHARM_TICKS,
                        0, false, true, true), owner, QuirkSkills.QUEEN_BEAM.get(),
                        isPrincess() ? QueenBeamQuirk.PRINCESS : QueenBeamQuirk.QUEEN)) return false;
        var existence = TensuraStorages.getExistenceFrom(target);
        existence.setTemporaryOwner(owner.getUUID());
        existence.markDirty();
        SubordinateHelper.removeTarget(target);
        if (target instanceof ISubordinate subordinate && owner instanceof Player player) subordinate.tame(player);
        return true;
    }
}

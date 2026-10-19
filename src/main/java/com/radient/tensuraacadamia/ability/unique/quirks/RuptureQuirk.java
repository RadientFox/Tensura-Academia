package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.RuptureArmEntity;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Limbs;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

public class RuptureQuirk extends Skill {

    private static final QuirkSkillsConfig.Rupture CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Rupture;

    private static final int RUPTURE = 0;
    private static final int ARM_RUPTURE = 1;
    private static final int MODES = 2;
    private static final int DIRECTIONS = 4;

    public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F);

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/rupture.png");
    }

    public RuptureQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == ARM_RUPTURE ? CONFIG.armAuraCost : CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == RUPTURE ? ARM_RUPTURE : RUPTURE;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case RUPTURE -> "rupture.rupture";
            case ARM_RUPTURE -> "rupture.arm_rupture";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode == ARM_RUPTURE && entity.level() instanceof ServerLevel level) {
            armRupture(level, instance, entity);
        }
    }

    // Rupture
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != RUPTURE || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        int chargeTicks = CONFIG.chargeSeconds * 20;
        if (heldTicks % 5 == 0) {
            level.sendParticles(BLOOD, entity.getX(), entity.getY(0.5D), entity.getZ(), 2 + Math.min(heldTicks, chargeTicks) / 10, 0.4D, 0.6D, 0.4D, 0.0D);
        }

        // Activation time
        if (heldTicks % 20 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tensura.skill.time_held.max", Math.min(heldTicks / 20, CONFIG.chargeSeconds), CONFIG.chargeSeconds).withStyle(ChatFormatting.GOLD), true);
            }
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != RUPTURE || heldTicks < CONFIG.chargeSeconds * 20 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.radiusMastered : CONFIG.radius;

        // Split between the four directions
        float damage = (float) (entity.getHealth() * (mastered ? CONFIG.healthMultiplierMastered : CONFIG.healthMultiplier) / DIRECTIONS);

        Holder<DamageType> type = level.damageSources().generic().typeHolder();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                target -> target != entity && target.isAlive() && HitRules.canHit(entity, target) && target.distanceTo(entity) <= radius);

        // Not a direct hit
        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            target.hurt(new DamageSource(type, null, entity), damage);
        }

        spawnRupture(level, entity, radius);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.activationCooldown, mode);
        if (entity.getRandom().nextDouble() >= (mastered ? CONFIG.surviveChanceMastered : CONFIG.surviveChance)) {
            killUser(entity, level.damageSources().generic());
        }
    }


    private static void armRupture(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int offArm = Limbs.getArm(entity, false);
        int mainArm = Limbs.getArm(entity, true);
        int arm = Limbs.hasLimb(entity, offArm) ? offArm : Limbs.hasLimb(entity, mainArm) ? mainArm : -1;
        if (arm < 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.rupture.no_arms").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ARM_RUPTURE)) {
            return;
        }

        BurstQuirk.payHealth(level, instance, entity, ARM_RUPTURE, CONFIG.armHealthCost);
        if (!entity.isAlive()) {
            return;
        }

        Limbs.loseLimb(entity, arm, Limbs.RUPTURE);
        boolean right = arm == Limbs.RIGHT_ARM;
        Vec3 look = entity.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize().scale((right ? 0.35D : -0.35D) * entity.getScale());
        Vec3 start = entity.getEyePosition().add(look.scale(0.5D)).add(side).subtract(0.0D, 0.3D * entity.getScale(), 0.0D);
        float damage = (float) (entity.getMaxHealth() * (instance.isMastered(entity) ? CONFIG.armHealthMultiplierMastered : CONFIG.armHealthMultiplier));
        RuptureArmEntity flying = RuptureArmEntity.create(level, entity, instance, right, damage, (float) CONFIG.armRadius, (int) Math.round(CONFIG.armSeconds * 20.0D));
        flying.moveTo(start.x, start.y, start.z, entity.getYRot(), entity.getXRot());
        flying.shoot(look.x, look.y, look.z, (float) (CONFIG.armSpeed / 20.0D), 0.0F);
        level.addFreshEntity(flying);

        level.sendParticles(BLOOD, start.x, start.y, start.z, 20, 0.2D, 0.2D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, start.x, start.y, start.z, 6, 0.2D, 0.2D, 0.2D, 0.1D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.6F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.8F);
        instance.setCoolDown(CONFIG.armCooldown, ARM_RUPTURE);
        instance.addMasteryPoint(entity);
    }

    public static void ruptureArm(ServerLevel level, @Nullable ManasSkillInstance instance, @Nullable LivingEntity owner, Vec3 pos, float damage, float radius) {
        if (owner != null && instance != null) {
            DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, ARM_RUPTURE);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius),
                    target -> target != owner && target.isAlive() && !target.isSpectator() && HitRules.canHit(owner, target) && target.getBoundingBox().getCenter().distanceTo(pos) <= radius + target.getBbWidth() * 0.5D)) {
                target.invulnerableTime = 0;
                target.hurt(source, damage);
            }
        }

        level.sendParticles(BLOOD, pos.x, pos.y, pos.z, 120, radius * 0.25D, radius * 0.25D, radius * 0.25D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, pos.x, pos.y, pos.z, 0, 0.0D, 1.0D, 0.0D, radius / 3.0D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.8F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
    }

    // Four way burst
    private static void spawnRupture(ServerLevel level, LivingEntity entity, double radius) {
        double y = entity.getY(0.5D);
        for (int side = 0; side < DIRECTIONS; side++) {
            Vec3 direction = Vec3.directionFromRotation(0.0F, entity.getYRot() + side * 90.0F);
            level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX() + direction.x, y, entity.getZ() + direction.z, 0, direction.x, direction.y, direction.z, 1.0D);

            for (double distance = 1.0D; distance <= radius; distance += 1.0D) {
                level.sendParticles(BLOOD, entity.getX() + direction.x * distance, y, entity.getZ() + direction.z * distance, 4, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), y, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    // Lethal unless something stops the death
    private static void killUser(LivingEntity entity, DamageSource source) {
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setResistanceBypassLevel(2.0F);
        tensuraSource.tensura$setDodgeBypass();

        entity.invulnerableTime = 0;
        entity.hurt(source, Math.max(entity.getMaxHealth(), entity.getHealth()) * 100.0F + entity.getAbsorptionAmount());
    }

}

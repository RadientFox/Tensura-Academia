package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class RuptureQuirk extends Skill {

    private static final QuirkSkillsConfig.Rupture CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Rupture;

    private static final int RUPTURE = 0;
    private static final int DIRECTIONS = 4;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F);

    public RuptureQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == RUPTURE ? "rupture.rupture" : super.getModeId(instance, mode);
    }

    // Rupture, Active
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != RUPTURE || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        int chargeTicks = CONFIG.chargeSeconds * 20;
        if (heldTicks % 5 == 0) {
            level.sendParticles(BLOOD, entity.getX(), entity.getY(0.5D), entity.getZ(), 2 + Math.min(heldTicks, chargeTicks) / 10, 0.4D, 0.6D, 0.4D, 0.0D);
        }

        // Activation time, same as Greed
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

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.radiusMastered : CONFIG.radius;

        // Split between the four directions
        float damage = (float) (entity.getHealth() * (mastered ? CONFIG.healthMultiplierMastered : CONFIG.healthMultiplier) / DIRECTIONS);

        Holder<DamageType> type = level.damageSources().generic().typeHolder();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                target -> target != entity && target.isAlive() && target.distanceTo(entity) <= radius);

        // Not a direct hit, so melee passives stay out of it
        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            target.hurt(new DamageSource(type, null, entity), damage);
        }

        spawnRupture(level, entity, radius);
        instance.addMasteryPoint(entity);
        killUser(entity, level.damageSources().generic());
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

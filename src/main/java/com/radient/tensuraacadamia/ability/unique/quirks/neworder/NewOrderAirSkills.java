package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class NewOrderAirSkills {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private NewOrderAirSkills() {
    }

    static void use(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int mode) {
        NewOrderRule rule = NewOrderQuirk.getModeRule(mode);
        if (rule == null || !entity.isAlive() || !NewOrderRules.hasRule(entity, rule)) {
            return;
        }

        boolean used = switch (mode) {
            case NewOrderQuirk.VACUUM -> NewOrderVacuum.placeVacuum(level, entity, instance, mode);
            case NewOrderQuirk.RESTRAIN -> NewOrderCage.restrain(level, entity, instance, mode);
            case NewOrderQuirk.EXPLODE -> explode(level, entity, instance, mode);
            case NewOrderQuirk.PROTECT -> NewOrderBarrier.raise(level, entity, instance, mode);
            case NewOrderQuirk.WEAPON -> NewOrderBarrage.start(level, entity, instance, mode);
            default -> NewOrderGround.spikes(level, entity, instance, mode);
        };
        if (used) {
            instance.setCoolDown(getCooldown(mode), mode);
        }
    }

    private static int getCooldown(int mode) {
        return switch (mode) {
            case NewOrderQuirk.VACUUM -> CONFIG.vacuumCooldown;
            case NewOrderQuirk.RESTRAIN -> CONFIG.restrainCooldown;
            case NewOrderQuirk.EXPLODE -> CONFIG.explodeCooldown;
            case NewOrderQuirk.PROTECT -> CONFIG.protectCooldown;
            case NewOrderQuirk.WEAPON -> CONFIG.weaponCooldown;
            default -> CONFIG.spikyCooldown;
        };
    }

    // The Air Will Explode
    private static boolean explode(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return false;
        }

        NewOrderUtil.Aim aim = NewOrderUtil.aim(entity, CONFIG.explodeRange);
        Vec3 center = aim.block() != null ? aim.point().add(Vec3.atLowerCornerOf(aim.block().getDirection().getNormal()).scale(0.5D)) : aim.point();
        double half = CONFIG.explodeSize * 0.5D;
        DamageSource source = NewOrderUtil.windSource(instance, entity, mode);
        float damage = NewOrderUtil.ruleDamage(entity, CONFIG.explodeDamage);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(half), target -> target.isAlive() && !target.isSpectator() && HitRules.canHit(entity, target))) {
            NewOrderUtil.hit(target, source, damage);
            Vec3 push = target.getBoundingBox().getCenter().subtract(center);
            push = push.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : push.normalize();
            NewOrderUtil.fling(target, entity, instance, push.scale(0.9D).add(0.0D, 0.35D, 0.0D));
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        NewOrderUtil.impact(level, center, NewOrderPayloads.ImpactPayload.EXPLODE, (float) half);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, half * 0.4D, half * 0.4D, half * 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        NewOrderUtil.playSound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 1.1F);
        NewOrderUtil.playSound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 1.5F, 0.7F);
        NewOrderUtil.playSound(level, entity.getEyePosition(), TensuraSoundEvents.CAST_WIND.get(), 0.6F, 1.3F);
        NewOrderUtil.callout(level, new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() + 0.8D, entity.getZ()), entity, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.AIR]);
        if (level.random.nextInt(3) == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    static void forget(LivingEntity entity, ManasSkillInstance instance) {
        NewOrderCage.forget(entity);
        NewOrderBarrier.forget(entity, instance);
        NewOrderBarrage.forget(entity);
    }

}

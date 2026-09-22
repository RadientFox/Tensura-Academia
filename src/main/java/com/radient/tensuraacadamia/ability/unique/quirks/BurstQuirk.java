package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.entity.magic.field.MagicExplosion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class BurstQuirk extends Skill {

    private static final QuirkSkillsConfig.Burst CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Burst;

    private static final int BURST = 0;

    // Tensura explosion
    private static class BurstExplosion extends MagicExplosion {
        private BurstExplosion(Level level, Entity owner) {
            super(level, owner);
        }

        @Override
        public void applyEffect(LivingEntity target, boolean instant) {
            if (instant) {
                super.applyEffect(target, true);
            }
        }
    }

    public BurstQuirk() {
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
        return mode == BURST ? "burst.burst" : super.getModeId(instance, mode);
    }

    // Burst
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != BURST || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        int chargeTicks = CONFIG.chargeSeconds * 20;
        if (heldTicks == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (heldTicks % 5 == 0) {
            int count = 2 + Math.min(heldTicks, chargeTicks) / 10;
            level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY(0.5D), entity.getZ(), count, 0.4D, 0.6D, 0.4D, 0.02D);
            level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY(0.5D), entity.getZ(), count / 2, 0.4D, 0.6D, 0.4D, 0.02D);
        }

        if (heldTicks % 20 == 0 && entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tensura.skill.time_held.max", Math.min(heldTicks / 20, CONFIG.chargeSeconds), CONFIG.chargeSeconds).withStyle(ChatFormatting.GOLD), true);
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != BURST || heldTicks < CONFIG.chargeSeconds * 20 || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        MagicExplosion explosion = new BurstExplosion(level, entity);
        explosion.setSkill(entity, instance, this, BURST);
        explosion.setElementalAttack(false);
        explosion.setDamage((float) (entity.getMaxHealth() * (mastered ? CONFIG.healthMultiplierMastered : CONFIG.healthMultiplier)));
        explosion.setSize((float) (mastered ? CONFIG.radiusMastered : CONFIG.radius));
        explosion.setVisualOnly(!CONFIG.breakBlocks);
        explosion.setPos(entity.position().add(0.0D, 0.2D, 0.0D));
        level.addFreshEntity(explosion);

        instance.addMasteryPoint(entity);
        killUser(entity, entity.damageSources().explosion(null, null));
    }

    // Lethal unless something prevents death
    private static void killUser(LivingEntity entity, DamageSource source) {
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setResistanceBypassLevel(2.0F);
        tensuraSource.tensura$setDodgeBypass();

        entity.invulnerableTime = 0;
        entity.hurt(source, Math.max(entity.getMaxHealth(), entity.getHealth()) * 100.0F + entity.getAbsorptionAmount());
    }

}

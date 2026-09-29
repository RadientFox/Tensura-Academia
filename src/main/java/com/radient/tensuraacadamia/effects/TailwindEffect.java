package com.radient.tensuraacadamia.effects;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class TailwindEffect extends MobEffect {
    public static final String BOOST_AT = "TracadamiaTailwindBoostAt";
    public static final double BOOST_STRENGTH = 2.7;
    private final boolean flight;

    public TailwindEffect(boolean flight) { super(MobEffectCategory.BENEFICIAL, 0xEAF4FF); this.flight = flight; }

    public static boolean isFlying(LivingEntity entity) {
        return entity.isAlive() && entity.hasEffect(MHAEffects.WIND_FLIGHT) && entity.hasEffect(MHAEffects.TAILWIND)
                && !entity.onGround() && !entity.isInWaterOrBubble() && !entity.isPassenger();
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return flight; }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.hasEffect(MHAEffects.TAILWIND)) {
            entity.getPersistentData().remove(BOOST_AT);
            return false;
        }
        if (entity.level() instanceof ServerLevel level && isFlying(entity)) {
            entity.fallDistance = 0;
            if (level.getGameTime() >= entity.getPersistentData().getLong(BOOST_AT)) {
                entity.getPersistentData().putLong(BOOST_AT, level.getGameTime() + 40);
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.4).add(entity.getLookAngle().scale(BOOST_STRENGTH)));
                entity.hurtMarked = true;
                level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        36, 0.4, 0.3, 0.4, 0.05);
            }
        }
        return true;
    }
}

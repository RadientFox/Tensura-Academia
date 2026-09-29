package com.radient.tensuraacadamia.effects;

import com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public final class DarkShadowFlightEffect extends MobEffect {
    public DarkShadowFlightEffect() { super(MobEffectCategory.BENEFICIAL, 0x17121E); }
    public static boolean isFlying(LivingEntity owner) {
        return owner.isAlive() && owner.hasEffect(MHAEffects.DARK_SHADOW_FLIGHT)
                && !owner.onGround() && !owner.isInWaterOrBubble() && !owner.isPassenger();
    }
    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
    @Override public boolean applyEffectTick(LivingEntity owner, int amplifier) {
        if (!(owner.level() instanceof ServerLevel)) return true;
        var shadow = DarkShadowQuirk.shadow(owner);
        if (shadow == null || !shadow.fused() || shadow.berserk() || !shadow.flying()) return false;
        owner.fallDistance = 0;
        if (owner.tickCount % 5 == 0) {
            owner.setDeltaMovement(owner.getDeltaMovement().scale(0.8).add(owner.getLookAngle().scale(0.35)));
            owner.hurtMarked = true;
        }
        return true;
    }
}

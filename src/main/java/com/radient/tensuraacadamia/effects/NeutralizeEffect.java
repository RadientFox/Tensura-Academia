package com.radient.tensuraacadamia.effects;

import com.radient.tensuraacadamia.ability.unique.quirks.NeutralizationQuirk;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

public final class NeutralizeEffect extends MobEffect {
    public NeutralizeEffect() {
        super(MobEffectCategory.HARMFUL, 0x8C90A6);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 10 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            NeutralizationQuirk.shutOffQuirks(entity);
        }

        return true;
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}

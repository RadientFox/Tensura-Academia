package com.radient.tensuraacadamia.quirks.acid;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class AcidEffect extends MobEffect {
    public AcidEffect() {
        super(MobEffectCategory.HARMFUL, 0xB0B1BE);
        addAttributeModifier(Attributes.ARMOR, ResourceLocation.fromNamespaceAndPath("tracadamia", "acid_defense"),
                -10, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity target, int amplifier) {
        // half second clock kept same during new stacks of acid
        if (!target.level().isClientSide && target.tickCount % 10 == 0 && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(AcidQuirk.acidDamage(target), AcidQuirk.DAMAGE_PER_STACK * Math.clamp(amplifier + 1, 1, AcidQuirk.MAX_STACKS));
        }
        return true;
    }
}

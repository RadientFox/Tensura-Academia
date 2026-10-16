package com.radient.tensuraacadamia.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SplinterEffect extends MobEffect {
    public SplinterEffect() {
        super(MobEffectCategory.HARMFUL, 0x80552E);
        addAttributeModifier(Attributes.MAX_HEALTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "splinter_max_health"),
                -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}

package com.radient.tensuraacadamia.quirks.bat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class BatlikeEffect extends MobEffect {
    public BatlikeEffect(boolean resting) {
        super(MobEffectCategory.BENEFICIAL, 0x68557B);
        if (!resting) addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "batlike_speed"), .4,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}

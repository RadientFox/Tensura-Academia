package com.radient.tensuraacadamia.effects;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class AspersionEffect extends MobEffect {
    public AspersionEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x665C8A);
        addAttributeModifier(TensuraAttributes.PRESENCE_CONCEALMENT,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "aspersion"),
                4.0, AttributeModifier.Operation.ADD_VALUE);
    }
}

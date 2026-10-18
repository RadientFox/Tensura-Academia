package com.radient.tensuraacadamia.quirks.assault_dust;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class DustyEffect extends MobEffect {
    public DustyEffect() {
        super(MobEffectCategory.HARMFUL, 0xA68A60);
        addAttributeModifier(TensuraAttributes.PRESENCE_CONCEALMENT,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "dusty_presence"),
                -1, AttributeModifier.Operation.ADD_VALUE);
    }
}

package com.radient.tensuraacadamia.effects;

import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class SmokescreenObscuredEffect extends MobEffect {
    public SmokescreenObscuredEffect() {
        super(MobEffectCategory.HARMFUL, 0x534F70);
        addAttributeModifier(TensuraAttributes.PRESENCE_SENSE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "smoke_presence_sense"),
                -1024.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(TensuraAttributes.PRESENCE_SENSE_RADIUS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "smoke_presence_radius"),
                -1024.0, AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(TensuraAttributes.HEAT_SENSE_RADIUS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "smoke_heat_radius"),
                -1024.0, AttributeModifier.Operation.ADD_VALUE);
    }
}

package com.radient.tensuraacadamia.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class AdditiveBoost {

    private AdditiveBoost() {
    }

    public static void apply(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double added) {
        AttributeInstance boost = entity.getAttribute(attribute);
        if (boost == null) {
            return;
        }

        AttributeModifier current = boost.getModifier(id);
        double factor = current == null ? 1.0D : 1.0D + current.amount();
        double others = factor > 0.0D ? boost.getValue() / factor : 0.0D;
        if (added <= 0.0D || others <= 1.0E-4D) {
            if (current != null) {
                boost.removeModifier(id);
            }
            return;
        }

        double amount = added / others;
        if (current == null || Math.abs(current.amount() - amount) > 1.0E-6D) {
            boost.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

}

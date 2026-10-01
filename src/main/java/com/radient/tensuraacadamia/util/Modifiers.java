package com.radient.tensuraacadamia.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Modifiers {

    private static final double SAME = 1.0E-6D;

    private Modifiers() {
    }

    public static void set(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null || entity.level().isClientSide) {
            return;
        }

        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) < SAME) {
            if (current != null) {
                keepHealthShare(entity, attribute, () -> instance.removeModifier(id));
            }
            return;
        }

        if (current == null || current.operation() != operation || Math.abs(current.amount() - amount) >= SAME) {
            keepHealthShare(entity, attribute, () -> instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation)));
        }
    }

    private static void keepHealthShare(LivingEntity entity, Holder<Attribute> attribute, Runnable change) {
        if (!attribute.is(Attributes.MAX_HEALTH) || !entity.isAlive()) {
            change.run();
            return;
        }

        float share = entity.getHealth() / entity.getMaxHealth();
        change.run();
        entity.setHealth(share * entity.getMaxHealth());
    }

}

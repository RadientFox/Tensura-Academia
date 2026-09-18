package com.radient.tensuraacadamia.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public class BloodParalysisEffect extends MobEffect {
    public BloodParalysisEffect() {
        super(MobEffectCategory.HARMFUL, 0x5D1628);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "blood_paralysis_speed"),
                -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "blood_paralysis_jump"),
                -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) {
            Vec3 motion = entity.getDeltaMovement();
            if (motion.x != 0.0D || motion.z != 0.0D || motion.y > 0.0D) {
                entity.setDeltaMovement(0.0D, Math.min(motion.y, 0.0D), 0.0D);
                entity.hurtMarked = true;
            }
            if (entity.isSprinting()) entity.setSprinting(false);
        }
        return true;
    }
}

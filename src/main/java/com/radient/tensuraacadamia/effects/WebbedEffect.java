package com.radient.tensuraacadamia.effects;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

public final class WebbedEffect extends MobEffect {
    private static final BlockParticleOption COBWEB = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());

    public WebbedEffect() {
        super(MobEffectCategory.HARMFUL, 0xEDEDED);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("webbed_movement"), -0.85D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.FLYING_SPEED, id("webbed_flying"), -0.85D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, id("webbed_jump"), -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.GRAVITY, id("webbed_gravity"), -0.75D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", path);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.fallDistance = 0.0F;
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(COBWEB, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 3,
                    entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.35D, entity.getBbWidth() * 0.4D, 0.0D);
        }
        return true;
    }
}

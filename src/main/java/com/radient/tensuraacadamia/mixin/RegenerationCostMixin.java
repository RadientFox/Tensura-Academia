package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderWill;
import io.github.manasmods.tensura.effect.ability.InstantRegenerationEffect;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Infinite Regeneration costs less magicules
@Mixin(value = InstantRegenerationEffect.class, remap = false)
public abstract class RegenerationCostMixin {

    @Redirect(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lio/github/manasmods/tensura/util/EnergyHelper;isOutOfMagiculeConsuming(Lnet/minecraft/world/entity/LivingEntity;DD)D"), remap = false)
    private double tracadamia$healCost(LivingEntity entity, double cost, double minimum) {
        double multiplier = NewOrderWill.getRegenerationCostMultiplier(entity);
        if (multiplier >= 1.0D) {
            return EnergyHelper.isOutOfMagiculeConsuming(entity, cost, minimum);
        }
        if (multiplier <= 0.0D) {
            return 0.0D;
        }
        return EnergyHelper.isOutOfMagiculeConsuming(entity, cost * multiplier, minimum) / multiplier;
    }

}

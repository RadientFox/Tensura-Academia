package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.ViralCosmosQuirk;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Skills cost more MP and aura while a poison flower grows
@Mixin(value = EnergyHelper.class, remap = false)
public abstract class EnergyCostMixin {

    @Unique
    private static boolean tracadamia$multiplying;

    @Inject(method = "isOutOfEnergy(Lnet/minecraft/world/entity/LivingEntity;DD)Z", at = @At("HEAD"), cancellable = true)
    private static void tracadamia$flowerCost(LivingEntity entity, double aura, double magicule, CallbackInfoReturnable<Boolean> callback) {
        double multiplier = ViralCosmosQuirk.getCostMultiplier(entity);
        if (multiplier == 1.0D || tracadamia$multiplying) {
            return;
        }

        tracadamia$multiplying = true;
        try {
            callback.setReturnValue(EnergyHelper.isOutOfEnergy(entity, aura * multiplier, magicule * multiplier));
        } finally {
            tracadamia$multiplying = false;
        }
    }

}

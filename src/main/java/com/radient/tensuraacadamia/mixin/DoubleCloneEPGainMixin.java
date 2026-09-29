package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.DoubleCloneManager;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnergyHelper.class)
public abstract class DoubleCloneEPGainMixin {
    @Inject(method = "getEPGain(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void tracadamia$noDoubleEP(LivingEntity victim, LivingEntity killer, boolean reduce,
                                            CallbackInfoReturnable<Double> cir) {
        if (DoubleCloneManager.isDoubleDuplicate(victim)) cir.setReturnValue(0.0D);
    }
}

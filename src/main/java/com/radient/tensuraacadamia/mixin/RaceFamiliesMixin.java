package com.radient.tensuraacadamia.mixin;

import com.github.hvnbael.trnightmare.util.racecosmetic.RaceFamilies;
import com.radient.tensuraacadamia.util.QuirkCosmetics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(value = RaceFamilies.class, remap = false)
public abstract class RaceFamiliesMixin {

    @Inject(method = "families(Lnet/minecraft/world/entity/LivingEntity;IZ)Ljava/util/Set;", at = @At("RETURN"))
    private static void tracadamia$quirkFamilies(LivingEntity entity, int clan, boolean borrowed, CallbackInfoReturnable<Set<String>> cir) {
        QuirkCosmetics.addFamilies(entity, borrowed, cir.getReturnValue());
    }
}

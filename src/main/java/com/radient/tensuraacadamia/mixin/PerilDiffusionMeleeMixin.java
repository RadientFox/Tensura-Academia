package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.PerilDiffusionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.PermeationQuirk;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PerilDiffusionMeleeMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void tracadamia$detectMelee(Entity target, CallbackInfoReturnable<Boolean> callback) {
        PerilDiffusionQuirk.trigger(target, (Mob) (Object) this);
        if (target instanceof LivingEntity living && PermeationQuirk.isPhasing(living)) callback.setReturnValue(false);
    }
}

package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.ExtraArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.TreeFrogQuirk;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void tracadamia$wallClimb(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (ExtraArmsQuirk.canWallClimb((LivingEntity) (Object) this) || TreeFrogQuirk.canClimb((LivingEntity) (Object) this))) {
            cir.setReturnValue(true);
        }
    }

}

package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.whirlwind.TailwindEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class TailwindFlightMixin {
    @Inject(method = "isFallFlying", at = @At("RETURN"), cancellable = true)
    private void tracadamia$tailwindFlight(CallbackInfoReturnable<Boolean> callback) {
        if (TailwindEffect.isFlying((LivingEntity) (Object) this)
                || com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowFlightEffect.isFlying((LivingEntity) (Object) this)
                || com.radient.tensuraacadamia.quirks.bat.BatEvents.flying((LivingEntity) (Object) this)) callback.setReturnValue(true);
    }
}

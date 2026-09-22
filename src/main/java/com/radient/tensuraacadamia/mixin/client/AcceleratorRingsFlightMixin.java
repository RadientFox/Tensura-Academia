package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.AcceleratorRingsFlightClient;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class AcceleratorRingsFlightMixin {
    @Inject(method = "isFallFlying", at = @At("RETURN"), cancellable = true)
    private void tracadamia$showAcceleratorFlight(CallbackInfoReturnable<Boolean> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (AcceleratorRingsFlightClient.isActive(entity.getId())) callback.setReturnValue(true);
    }
}

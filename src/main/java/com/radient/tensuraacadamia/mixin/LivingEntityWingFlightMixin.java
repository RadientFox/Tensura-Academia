package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.FierceWingsQuirk;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityWingFlightMixin {

    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void tracadamia$wingFlight(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.isFallFlying() && !entity.onGround() && !entity.isPassenger() && !entity.hasEffect(MobEffects.LEVITATION)
                && FierceWingsQuirk.canWingFly(entity)) {
            ci.cancel();
        }
    }
}

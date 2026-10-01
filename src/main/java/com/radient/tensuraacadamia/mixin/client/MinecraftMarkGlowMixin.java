package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.ability.unique.quirks.VibrationDetectionQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMarkGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void tracadamia$markedGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (VibrationDetectionQuirk.isMarkedOnClient(entity)) {
            cir.setReturnValue(true);
        }
    }

}

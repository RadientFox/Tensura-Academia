package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.PermeationPhaseClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class PermeationPhaseMovementMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tracadamia$phaseBeforeMovement(CallbackInfo callback) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (PermeationPhaseClient.isActive(player.getId())) player.noPhysics = true;
    }
}

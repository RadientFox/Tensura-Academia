package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.bat.BatEvents;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class BatWakeMixin {
    @Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
    private void tracadamia$wakeHangingBats(CallbackInfo callback) {
        for (var player : ((ServerLevel) (Object) this).players()) {
            if (BatEvents.hanging(player)) BatEvents.wake(player);
        }
    }
}

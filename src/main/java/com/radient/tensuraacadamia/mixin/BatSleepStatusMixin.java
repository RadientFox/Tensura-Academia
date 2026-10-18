package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.bat.BatEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(SleepStatus.class)
public abstract class BatSleepStatusMixin {
    @Shadow public abstract int sleepersNeeded(int percentage);

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z"))
    private boolean tracadamia$countHangingSleepers(ServerPlayer player, Operation<Boolean> original) {
        return original.call(player) || BatEvents.sleepingForNight(player);
    }

    @Inject(method = "areEnoughDeepSleeping", at = @At("RETURN"), cancellable = true)
    private void tracadamia$countRestedBats(int percentage, List<ServerPlayer> players, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() || players.stream().noneMatch(BatEvents::restedLongEnough)) return;
        long rested = players.stream().filter(player -> player.isSleepingLongEnough() || BatEvents.restedLongEnough(player)).count();
        callback.setReturnValue(rested >= sleepersNeeded(percentage));
    }
}

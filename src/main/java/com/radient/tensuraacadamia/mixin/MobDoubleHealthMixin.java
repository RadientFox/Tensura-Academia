package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.DoubleMobHealthData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobDoubleHealthMixin {
    @Unique
    private static final EntityDataAccessor<Float> tracadamia$REDUCED_MAX_HEALTH =
            DoubleMobHealthData.createAccessor();

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void tracadamia$defineReducedMaxHealth(SynchedEntityData.Builder builder, CallbackInfo callback) {
        builder.define(tracadamia$REDUCED_MAX_HEALTH, 0.0F);
    }
}

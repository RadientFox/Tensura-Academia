package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.entity.DarkShadow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class DarkShadowMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void tracadamia$shadowCaptive(MoverType type, Vec3 movement, CallbackInfo callback) {
        if (DarkShadow.blocksMovement((Entity) (Object) this)) callback.cancel();
    }
}

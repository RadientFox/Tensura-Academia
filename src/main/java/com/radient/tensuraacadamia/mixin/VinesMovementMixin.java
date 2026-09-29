package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.entity.VineConstruct;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class VinesMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void tracadamia$holdVineCaptive(MoverType type, Vec3 movement, CallbackInfo callback) {
        if (VineConstruct.blocksMovement((Entity) (Object) this)) callback.cancel();
    }
}

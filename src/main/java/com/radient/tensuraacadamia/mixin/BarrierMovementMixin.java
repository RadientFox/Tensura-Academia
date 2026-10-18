package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.barrier.DomeBarrier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class BarrierMovementMixin {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 tracadamia$barrierMovement(Vec3 movement) {
        return DomeBarrier.limitMovement((Entity) (Object) this, movement);
    }
}

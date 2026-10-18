package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.bat.BatEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class BatMovementMixin {
    @Inject(method = "getDefaultGravity", at = @At("HEAD"), cancellable = true)
    private void tracadamia$batGravity(CallbackInfoReturnable<Double> callback) {
        LivingEntity owner = (LivingEntity) (Object) this;
        if (BatEvents.flying(owner) || BatEvents.hanging(owner)) callback.setReturnValue(0.0);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void tracadamia$batMovement(Vec3 input, CallbackInfo callback) {
        LivingEntity owner = (LivingEntity) (Object) this;
        if (BatEvents.hanging(owner)) {
            owner.setDeltaMovement(Vec3.ZERO);
            callback.cancel();
            return;
        }
        Vec3 dash = BatEvents.dash(owner);
        if (dash.lengthSqr() > 0) {
            if (owner.isControlledByLocalInstance()) {
                owner.setDeltaMovement(dash);
                owner.move(MoverType.SELF, dash);
                owner.fallDistance = 0;
            }
            callback.cancel();
        } else if (BatEvents.landing(owner) && owner.isControlledByLocalInstance()) {
            Vec3 velocity = owner.getDeltaMovement();
            owner.setDeltaMovement(velocity.x * .85, Math.min(velocity.y, -.22), velocity.z * .85);
        } else if (BatEvents.flying(owner) && owner.isControlledByLocalInstance() && input.z > 0) {
            Vec3 velocity = owner.getDeltaMovement().add(owner.getLookAngle().scale(input.z * .035));
            double maximum = owner.isSprinting() ? 1.2 : .8;
            if (velocity.lengthSqr() > maximum * maximum) velocity = velocity.normalize().scale(maximum);
            owner.setDeltaMovement(velocity);
        }
    }
}

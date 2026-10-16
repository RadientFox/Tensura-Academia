package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.MultiArms;
import com.radient.tensuraacadamia.ability.unique.quirks.WeldQuirk;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// This was made to fix the carrying/holding of grab/throw type skills
@Mixin(Entity.class)
public abstract class HeldPassengerMixin {

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("HEAD"), cancellable = true)
    private void tracadamia$heldSpot(Entity passenger, Entity.MoveFunction move, CallbackInfo callback) {
        Entity vehicle = (Entity) (Object) this;
        Vec3 spot = MultiArms.getRidingSpot(vehicle, passenger);
        if (spot == null) {
            spot = WeldQuirk.getTransportSpot(vehicle, passenger);
        }

        if (spot != null) {
            move.accept(passenger, spot.x, spot.y, spot.z);
            callback.cancel();
        }
    }

    @Inject(method = "getDismountLocationForPassenger", at = @At("HEAD"), cancellable = true)
    private void tracadamia$heldDismount(LivingEntity passenger, CallbackInfoReturnable<Vec3> callback) {
        Vec3 spot = MultiArms.getDismountSpot((Entity) (Object) this, passenger);
        if (spot != null) {
            callback.setReturnValue(spot);
        }
    }

    public boolean shouldRiderSit() {
        Entity vehicle = (Entity) (Object) this;
        return !MultiArms.isHolding(vehicle) && !WeldQuirk.isTransporting(vehicle);
    }

}

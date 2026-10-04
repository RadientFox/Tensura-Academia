package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.DoubleMobHealthData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDoubleHealthMixin {
    @Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true)
    private void tracadamia$useReducedMaxHealth(CallbackInfoReturnable<Float> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof Mob mob) {
            float health = DoubleMobHealthData.get(mob);
            if (Float.isFinite(health) && health > 0.0F) callback.setReturnValue(
                    com.radient.tensuraacadamia.ability.unique.quirks.OverhaulQuirk.duplicateHealth(entity, health));
        }
    }
}

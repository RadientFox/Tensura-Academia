package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.VibrationDetectionQuirk;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

// Vibration detection lets earth magic lock onto targets behind walls
@Mixin(value = ObjectSelectionHelper.class, remap = false)
public abstract class VibrationTargetingMixin {

    @Inject(method = "getTargetPredicate", at = @At("RETURN"), cancellable = true)
    private static <T extends Entity> void tracadamia$feltThroughWalls(LivingEntity user, boolean nonSubordinate, boolean lineOfSight, boolean requirePhysics, CallbackInfoReturnable<Predicate<T>> cir) {
        if (!lineOfSight || !VibrationDetectionQuirk.canTargetThroughWalls(user)) {
            return;
        }

        Predicate<T> inSight = cir.getReturnValue();
        Predicate<T> anywhere = ObjectSelectionHelper.getTargetPredicate(user, nonSubordinate, false, requirePhysics);
        cir.setReturnValue(entity -> inSight.test(entity) || VibrationDetectionQuirk.isFelt(user, entity) && anywhere.test(entity));
    }

}

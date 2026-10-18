package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.quirks.arbor.ArborQuirk;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class ArborBurnMixin {
    @ModifyVariable(method = "setRemainingFireTicks", at = @At("HEAD"), argsOnly = true)
    private int tracadamia$arborBurnDuration(int ticks) {
        return (Entity) (Object) this instanceof LivingEntity living && !living.level().isClientSide ? ArborQuirk.burnTicks(living, ticks) : ticks;
    }
}

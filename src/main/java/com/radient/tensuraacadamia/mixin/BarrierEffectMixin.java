package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.TongueTankQuirk;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.tensura.effect.ability.MagicBarrierEffect;
import io.github.manasmods.tensura.effect.ability.PhysicalBarrierEffect;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Bunker Breaker unaffected by barriers
@Mixin(value = {PhysicalBarrierEffect.class, MagicBarrierEffect.class}, remap = false)
public abstract class BarrierEffectMixin {

    @Inject(method = "onBeingDamaged", at = @At("HEAD"), cancellable = true, remap = false)
    private void tracadamia$bunkerBreaker(LivingEntity entity, DamageSource source, Changeable<Float> amount, CallbackInfoReturnable<Boolean> cir) {
        if (TongueTankQuirk.isBunkerBreaker(source)) {
            cir.setReturnValue(true);
        }
    }

}

package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.entity.VineConstruct;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ManasSkillInstance.class)
public abstract class VinesSkillGateMixin {
    @Inject(method = "canInteractSkill", at = @At("HEAD"), cancellable = true, remap = false)
    private void tracadamia$blockCrucifiedSkill(LivingEntity owner, CallbackInfoReturnable<Boolean> callback) {
        if (VineConstruct.crucified(owner)) callback.setReturnValue(false);
    }
    @Inject(method = "onHeld", at = @At("HEAD"), cancellable = true, remap = false)
    private void tracadamia$stopCrucifiedHold(LivingEntity owner, int ticks, int mode, CallbackInfoReturnable<Boolean> callback) {
        if (VineConstruct.crucified(owner)) callback.setReturnValue(false);
    }
}

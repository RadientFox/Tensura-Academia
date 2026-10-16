package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.PermeationQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ManasSkillInstance.class)
public abstract class PermeationSkillGateMixin {
    @Inject(method = "canInteractSkill", at = @At("HEAD"), cancellable = true, remap = false)
    private void tracadamia$blockSkillStart(LivingEntity owner, CallbackInfoReturnable<Boolean> callback) {
        if (PermeationQuirk.blocksSkill(owner, (ManasSkillInstance) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "onHeld", at = @At("HEAD"), cancellable = true, remap = false)
    private void tracadamia$blockOtherHeldSkills(LivingEntity owner, int ticks, int mode, CallbackInfoReturnable<Boolean> callback) {
        if (PermeationQuirk.blocksSkill(owner, (ManasSkillInstance) (Object) this)) callback.setReturnValue(false);
    }
}

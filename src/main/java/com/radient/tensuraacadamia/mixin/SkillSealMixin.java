package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSeals;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// I cant use the anti-skill features because were counting quirks as something different than uniques
// If there's another way to stop uniques/ults with quirks being separate then this can be removed, changed, or substituted
@Mixin(value = Skill.class, remap = false)
public abstract class SkillSealMixin {

    @Inject(method = "isAffectedByStatus", at = @At("RETURN"), cancellable = true, remap = false)
    private void tracadamia$newOrderSeal(ManasSkillInstance instance, LivingEntity entity, int mode, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && NewOrderSeals.isSealed((Skill) (Object) this, entity)) {
            cir.setReturnValue(true);
        }
    }

}

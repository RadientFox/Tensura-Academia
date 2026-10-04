package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.OverhaulLegs;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidArmorLayer.class)
public abstract class OverhaulArmorKneesMixin {
    @Inject(method = "getArmorModelHook", at = @At("RETURN"))
    private void tracadamia$armorKnees(LivingEntity entity, ItemStack stack, EquipmentSlot slot,
                                     HumanoidModel<?> model, CallbackInfoReturnable<Model> ci) {
        if (ci.getReturnValue() == model) OverhaulLegs.armor(
                ((HumanoidArmorLayer<?, ?, ?>) (Object) this).getParentModel(), model, slot == EquipmentSlot.LEGS ? 0.5F : 1);
    }
}

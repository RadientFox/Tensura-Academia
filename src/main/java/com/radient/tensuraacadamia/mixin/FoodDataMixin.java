package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSeals;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void tracadamia$canNotRecover(Player player, float amount) {
        if (!NewOrderSeals.cannotRecover(player)) {
            player.heal(amount);
        }
    }

}

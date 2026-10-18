package com.radient.tensuraacadamia.mixin;

import com.github.hvnbael.trnightmare.racialprogression.RacialProgressionService;
import com.github.hvnbael.trnightmare.racialprogression.RacialTree;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SpideySmartsSkill;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RacialProgressionService.class, remap = false)
public abstract class RacialProgressionMixin {

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private static void tracadamia$quirkTrees(RacialTree tree, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (SpiderQuirk.TREE.equals(tree.id())) {
            cir.setReturnValue(SpiderQuirk.getSpider(player).isPresent());
        }
    }

    @Inject(method = "countItems", at = @At("HEAD"), cancellable = true)
    private static void tracadamia$countTokens(Player player, RacialTree.ItemCost cost, CallbackInfoReturnable<Integer> cir) {
        int balance = SpideySmartsSkill.tokenBalance(player, cost.id());
        if (balance >= 0) {
            cir.setReturnValue(balance);
        }
    }

    @Inject(method = "takeItems", at = @At("HEAD"), cancellable = true)
    private static void tracadamia$spendTokens(Player player, RacialTree.ItemCost cost, CallbackInfo ci) {
        if (SpideySmartsSkill.spendTokens(player, cost.id(), cost.count())) {
            ci.cancel();
        }
    }
}

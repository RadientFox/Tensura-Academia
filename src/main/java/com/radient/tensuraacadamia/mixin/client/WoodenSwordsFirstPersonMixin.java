package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.ability.unique.quirks.WoodenSwordsQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemInHandRenderer.class)
public abstract class WoodenSwordsFirstPersonMixin {
    @ModifyArg(
            method = "renderHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
            ),
            index = 5
    )
    private ItemStack tracadamia$renderVirtualWoodenSwords(ItemStack heldStack) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible() || !WoodenSwordsQuirk.isSlottedForRender(player)) {
            return heldStack;
        }
        return WoodenSwordsQuirk.getSwordStackForRender(player);
    }
}

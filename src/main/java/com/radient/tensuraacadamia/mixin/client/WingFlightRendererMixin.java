package com.radient.tensuraacadamia.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.client.WingClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class WingFlightRendererMixin {

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void tracadamia$wingFlight(AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyYaw, float partialTick, float scale, CallbackInfo ci) {
        WingClient.rotateBody(player, poseStack, bodyYaw, partialTick);
    }

}

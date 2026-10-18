package com.radient.tensuraacadamia.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.quirks.bat.BatEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class BatHeldItemMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void tracadamia$hideWingTools(PoseStack poses, MultiBufferSource buffers, int light, LivingEntity owner,
                                        float swing, float amount, float partial, float age, float yaw, float pitch, CallbackInfo callback) {
        if (owner instanceof Player && BatEvents.active(owner)) callback.cancel();
    }
}

package com.radient.tensuraacadamia.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.client.OverhaulLegs;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class OverhaulLegRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    private void tracadamia$jointedLeg(PoseStack poses, VertexConsumer vertices, int light, int overlay, int color, CallbackInfo ci) {
        if (OverhaulLegs.render((ModelPart) (Object) this, poses, vertices, light, overlay, color)) ci.cancel();
    }
}

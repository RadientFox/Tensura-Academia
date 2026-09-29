package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.PopOffProjectile;
import com.radient.tensuraacadamia.regestry.PopOffEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PopOffRenderer extends EntityRenderer<PopOffProjectile> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    public PopOffRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.18F; }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PopOffEntities.POP_OFF.get(), PopOffRenderer::new);
    }

    @Override
    public void render(PopOffProjectile ball, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        PoseStack.Pose pose = poses.last();
        for (int row = 0; row < 4; row++) {
            double a = Math.PI * row / 4, b = Math.PI * (row + 1) / 4;
            for (int col = 0; col < 8; col++) {
                double c = Math.PI * 2 * col / 8, d = Math.PI * 2 * (col + 1) / 8;
                vertex(buffer, pose, a, c, light);
                vertex(buffer, pose, a, d, light);
                vertex(buffer, pose, b, d, light);
                vertex(buffer, pose, b, c, light);
            }
        }
        super.render(ball, yaw, partialTick, poses, buffers, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, double latitude, double longitude, int light) {
        float x = (float) (Math.sin(latitude) * Math.cos(longitude));
        float y = (float) Math.cos(latitude);
        float z = (float) (Math.sin(latitude) * Math.sin(longitude));
        buffer.addVertex(pose.pose(), x * 0.22F, y * 0.22F + 0.22F, z * 0.22F)
                .setColor(150, 45, 200, 255).setUv((float) (longitude / (Math.PI * 2)), (float) (latitude / Math.PI))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, x, y, z);
    }

    @Override public ResourceLocation getTextureLocation(PopOffProjectile ball) { return TEXTURE; }
}

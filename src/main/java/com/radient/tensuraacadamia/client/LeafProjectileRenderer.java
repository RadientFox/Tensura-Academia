package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.LeafProjectile;
import com.radient.tensuraacadamia.regestry.LeafEntities;
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
import com.mojang.math.Axis;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LeafProjectileRenderer extends EntityRenderer<LeafProjectile> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/particle/cherry_0.png");

    public LeafProjectileRenderer(EntityRendererProvider.Context context) { super(context); }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(LeafEntities.LEAF.get(), LeafProjectileRenderer::new);
    }

    @Override public void render(LeafProjectile leaf, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        poses.pushPose();
        poses.mulPose(entityRenderDispatcher.cameraOrientation());
        poses.mulPose(Axis.ZP.rotationDegrees((leaf.tickCount + partialTick) * 18.0F));
        float radius = leaf.getKind() == LeafProjectile.HEMLOCK ? 0.34F : 0.18F;
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poses.last();
        vertex(buffer, pose, -radius, -radius, 0, 1, light);
        vertex(buffer, pose, radius, -radius, 1, 1, light);
        vertex(buffer, pose, radius, radius, 1, 0, light);
        vertex(buffer, pose, -radius, radius, 0, 0, light);
        poses.popPose();
        super.render(leaf, yaw, partialTick, poses, buffers, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
                               float u, float v, int light) {
        buffer.addVertex(pose.pose(), x, y, 0)
                .setColor(45, 220, 55, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    @Override public ResourceLocation getTextureLocation(LeafProjectile leaf) { return TEXTURE; }
}

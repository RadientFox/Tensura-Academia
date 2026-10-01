package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.GravityClusterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class GravityClusterRenderer extends EntityRenderer<GravityClusterEntity> {

    private final BlockRenderDispatcher dispatcher;

    public GravityClusterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GravityClusterEntity.TYPE.get(), GravityClusterRenderer::new);
    }

    @Override
    public void render(GravityClusterEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (entity.getBlocks().isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getSpin(partialTick)));

        boolean formed = entity.isFormed();
        Vec3 center = entity.getPosition(partialTick);
        double travelled = (entity.level().getGameTime() - entity.getGatherStart() + partialTick) * entity.getGatherSpeed();
        for (GravityClusterEntity.Part part : entity.getBlocks()) {
            if (part.state().getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            Vec3 spot = part.offset();
            if (!formed) {
                Vec3 from = part.origin().subtract(center);
                double distance = spot.distanceTo(from);
                spot = distance <= 1.0E-3D || travelled >= distance ? spot : from.add(spot.subtract(from).scale(Math.max(0.0D, travelled) / distance));
            }

            poseStack.pushPose();
            poseStack.translate(spot.x - 0.5D, spot.y - 0.5D, spot.z - 0.5D);
            this.dispatcher.renderSingleBlock(part.state(), poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(GravityClusterEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

}

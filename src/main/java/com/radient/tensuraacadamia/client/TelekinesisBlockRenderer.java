package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.LightBallProjectile;
import com.radient.tensuraacadamia.entity.StormBoltEntity;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import io.github.manasmods.tensura.client.entity.projectile.magic.MagicSphereRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
// TELEKINESIS WHY
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class TelekinesisBlockRenderer extends EntityRenderer<TelekinesisBlockEntity> {

    private final BlockRenderDispatcher dispatcher;

    public TelekinesisBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TelekinesisBlockEntity.TYPE.get(), TelekinesisBlockRenderer::new);
        event.registerEntityRenderer(StormBoltEntity.TYPE.get(), context -> (EntityRenderer<StormBoltEntity>) (EntityRenderer<?>) new LightningBoltRenderer(context));
        event.registerEntityRenderer(LightBallProjectile.TYPE.get(), MagicSphereRenderer::new);
    }

    @Override
    public void render(TelekinesisBlockEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        BlockState state = entity.getBlockState();
        if (state.getRenderShape() == RenderShape.MODEL) {
            float scale = entity.getBlockScale();
            Vec3 offset = entity.getHoldRenderOffset(partialTick);
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5D, 0.0D, -0.5D);
            this.dispatcher.renderSingleBlock(state, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TelekinesisBlockEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

}

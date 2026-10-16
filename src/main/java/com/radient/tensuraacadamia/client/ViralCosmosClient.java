package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ViralCosmosQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class ViralCosmosClient {

    private static final float FLOWER_SIZE = 0.6F;
    private static final float SMALL_FLOWER_SIZE = 0.35F;

    private ViralCosmosClient() {
    }

    // Flower on the host's back
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        int kind = ViralCosmosQuirk.getClientFlower(entity.getId());
        if (kind == ViralCosmosQuirk.NO_FLOWER || entity.isInvisible()) {
            return;
        }

        BlockState flower = kind == ViralCosmosQuirk.HEALING_FLOWER ? Blocks.PINK_TULIP.defaultBlockState() : Blocks.ALLIUM.defaultBlockState();
        float size = (kind == ViralCosmosQuirk.SMALL_FLOWER ? SMALL_FLOWER_SIZE : FLOWER_SIZE) * Math.max(0.5F, entity.getScale());
        float bodyYaw = Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(0.0F, entity.getBbHeight() * 0.62F, -entity.getBbWidth() * 0.5F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-25.0F));
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(flower, poseStack, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ViralCosmosQuirk.clearClient();
    }

}

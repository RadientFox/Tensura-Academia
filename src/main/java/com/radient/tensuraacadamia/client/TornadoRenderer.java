package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.TornadoEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class TornadoRenderer extends GeoEntityRenderer<TornadoEntity> {

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        this.shadowRadius = 0.0F;
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TornadoEntity.TYPE.get(), TornadoRenderer::new);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, TornadoEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float size = animatable.getVisualSize();
        super.scaleModelForRender(widthScale * size, heightScale * size, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

    private static final class Model extends GeoModel<TornadoEntity> {

        private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath("tensura", "geo/entity/misc/death_tornado.geo.json");
        private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("tensura", "animations/entity/misc/death_tornado.animation.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/misc/death_tornado.png");

        @Override
        public ResourceLocation getModelResource(TornadoEntity animatable) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(TornadoEntity animatable) {
            return WhiteTextures.get(TEXTURE);
        }

        @Override
        public ResourceLocation getAnimationResource(TornadoEntity animatable) {
            return ANIMATION;
        }

        @Override
        public RenderType getRenderType(TornadoEntity animatable, ResourceLocation texture) {
            return RenderType.entityTranslucent(texture);
        }

    }

}

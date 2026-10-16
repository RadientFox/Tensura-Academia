package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AirRestrainRenderer {

    private static final int CLOSE = 6;
    private static final int LET_GO = 5;
    private static final float OPEN = 0.7F;
    private static final float TIGHT = 0.07F;
    private static final float FEET = 1.501F;
    private static final int WIND_COLOR = FastColor.ARGB32.color(235, 230, 248, 255);
    private static final int TINT_COLOR = FastColor.ARGB32.color(95, 200, 235, 255);

    private record Hold(long start, int ticks) {}

    private static final Map<Integer, Hold> HELD = new HashMap<>();
    private static final Map<Integer, Long> LETTING_GO = new HashMap<>();

    private AirRestrainRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long time = level.getGameTime();
        for (NewOrderPayloads.CagePayload cage : NewOrderPayloads.getClientCages()) {
            if (cage.ticks() > 0) {
                HELD.put(cage.entityId(), new Hold(time, cage.ticks()));
                LETTING_GO.remove(cage.entityId());
            } else if (HELD.remove(cage.entityId()) != null) {
                LETTING_GO.put(cage.entityId(), time);
            }
        }
        NewOrderPayloads.getClientCages().clear();
        HELD.entrySet().removeIf(entry -> {
            if (time - entry.getValue().start() > entry.getValue().ticks()) {
                LETTING_GO.put(entry.getKey(), time);
                return true;
            }
            return level.getEntity(entry.getKey()) == null;
        });
        LETTING_GO.values().removeIf(start -> time - start > LET_GO || start > time);
    }

    private static float @Nullable [] getHold(Entity entity, float partialTick) {
        if (HELD.isEmpty() && LETTING_GO.isEmpty() || entity.level() == null) {
            return null;
        }

        float now = entity.level().getGameTime() + partialTick;
        Hold hold = HELD.get(entity.getId());
        if (hold != null) {
            float age = now - hold.start();
            float close = Mth.clamp(age / CLOSE, 0.0F, 1.0F);
            float squeeze = 1.0F + TIGHT + OPEN * (1.0F - close) * (1.0F - close) + 0.035F * Mth.sin(age * 0.9F);
            return new float[]{squeeze, close};
        }

        Long gone = LETTING_GO.get(entity.getId());
        if (gone != null) {
            float out = Mth.clamp((now - gone) / LET_GO, 0.0F, 1.0F);
            return new float[]{1.0F + TIGHT + 0.4F * out, 1.0F - out};
        }
        return null;
    }

    private static RenderType getWind(Entity entity, float partialTick, boolean second) {
        float now = entity.level().getGameTime() + partialTick;
        return second ? RenderType.breezeWind(AirShapes.WIND, 0.5F, now * 0.04F % 1.0F) : RenderType.breezeWind(AirShapes.WIND, now * 0.05F % 1.0F, 0.0F);
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer living) {
                living.addLayer(new HoldLayer(living));
            } else if (renderer instanceof GeoEntityRenderer geo) {
                geo.addRenderLayer(new GeoHoldLayer(geo));
            }
        }
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof LivingEntityRenderer living) {
                living.addLayer(new HoldLayer(living));
            }
        }
    }

    // Vanilla models
    private static final class HoldLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

        private HoldLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float[] hold = getHold(entity, partialTick);
            if (hold == null || hold[1] <= 0.01F || entity.isInvisible()) {
                return;
            }

            float middle = FEET - entity.getBbHeight() * 0.5F / Math.max(0.01F, entity.getScale());
            M model = getParentModel();
            for (int pass = 0; pass < 3; pass++) {
                float squeeze = hold[0] * (pass == 2 ? 1.05F : 1.0F);
                poseStack.pushPose();
                poseStack.translate(0.0F, middle, 0.0F);
                poseStack.scale(squeeze, squeeze, squeeze);
                poseStack.translate(0.0F, -middle, 0.0F);
                VertexConsumer consumer = buffers.getBuffer(pass == 0 ? RenderType.entityTranslucent(getTextureLocation(entity)) : getWind(entity, partialTick, pass == 2));
                model.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, AirShapes.fade(pass == 0 ? TINT_COLOR : WIND_COLOR, hold[1] * (pass == 2 ? 0.7F : 1.0F)));
                poseStack.popPose();
            }
        }
    }

    // Draws over models and adds the wind effect
    private static final class GeoHoldLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

        private GeoHoldLayer(GeoRenderer<T> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource buffers, @Nullable VertexConsumer buffer,
                           float partialTick, int light, int overlay) {
            if (!(animatable instanceof LivingEntity entity) || entity.isInvisible()) {
                return;
            }

            float[] hold = getHold(entity, partialTick);
            if (hold == null || hold[1] <= 0.01F) {
                return;
            }

            float middle = entity.getBbHeight() * 0.5F;
            for (int pass = 0; pass < 3; pass++) {
                float squeeze = hold[0] * (pass == 2 ? 1.05F : 1.0F);
                RenderType type = pass == 0 ? RenderType.entityTranslucent(getRenderer().getTextureLocation(animatable)) : getWind(entity, partialTick, pass == 2);
                poseStack.pushPose();
                poseStack.translate(0.0F, middle, 0.0F);
                poseStack.scale(squeeze, squeeze, squeeze);
                poseStack.translate(0.0F, -middle, 0.0F);
                getRenderer().reRender(bakedModel, poseStack, buffers, animatable, type, buffers.getBuffer(type), partialTick, light, OverlayTexture.NO_OVERLAY,
                        AirShapes.fade(pass == 0 ? TINT_COLOR : WIND_COLOR, hold[1] * (pass == 2 ? 0.7F : 1.0F)));
                poseStack.popPose();
            }
        }
    }

    static void clear() {
        HELD.clear();
        LETTING_GO.clear();
    }

}

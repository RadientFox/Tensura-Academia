package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.WingQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

// Just and arch daemon wing render on the player
// This should cause absolutely no problems in the future?
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class WingsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("tensura", "geo/entity/arch_daemon.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/daemon/arch/wings/archdemon_wings_large.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath("tensura", "animations/entity/arch_daemon.animation.json");
    private static final String WINGS_BONE = "wings";

    private static final List<String> BODY_BONES = List.of("body", "miscBody", "torso", "miscTorso", "upper_torso", "miscUpper");
    private static final float NECK_HEIGHT = 1.5F;
    private static final float ROOT_HEIGHT = 21.0F / 16.0F;
    private static final float ROOT_DEPTH = 2.0F / 16.0F;

    private static final Wings WINGS = new Wings();
    private static final WingsRenderer RENDERER = new WingsRenderer();

    public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new WingsLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float growth = WingClient.getWingGrowth(player, partialTick);
        if (player.isInvisible() || growth <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -NECK_HEIGHT, 0.0F);
        poseStack.translate(0.0F, ROOT_HEIGHT, ROOT_DEPTH);
        poseStack.scale(growth, growth, growth);
        poseStack.translate(0.0F, -ROOT_HEIGHT, -ROOT_DEPTH);
        WINGS.player = player;
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        RENDERER.render(poseStack, WINGS, buffer, type, buffer.getBuffer(type), packedLight, partialTick);
        WINGS.player = null;
        poseStack.popPose();
    }

    private static final class Wings implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private final RawAnimation fly = RawAnimation.begin().thenLoop("animation.arch_daemon.fly");
        private final RawAnimation hover = RawAnimation.begin().thenLoop("animation.arch_daemon.idle_fly");
        private final RawAnimation idle = RawAnimation.begin().thenLoop("animation.arch_daemon.idle");
        private @Nullable AbstractClientPlayer player;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "wings", 5, state -> {
                AbstractClientPlayer flyer = this.player;
                if (flyer == null) {
                    return PlayState.STOP;
                }

                boolean moving = flyer.getX() != flyer.xo || flyer.getY() != flyer.yo || flyer.getZ() != flyer.zo;
                if (isFlying(flyer)) {
                    return state.setAndContinue(moving ? fly : hover);
                }
                return state.setAndContinue(idle);
            }));
        }

        private static boolean isFlying(AbstractClientPlayer player) {
            if (player.isLocalPlayer() && player.getAbilities().flying) {
                return true;
            }

            ManasSkillInstance wing = WingQuirk.getWing(player).orElse(null);
            return wing != null && wing.getTag() != null && (wing.getTag().getBoolean(WingQuirk.FLYING_TAG) || wing.getTag().getBoolean(WingQuirk.DIVING_TAG));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return player == null ? 0.0D : player.tickCount;
        }
    }

    private static final class WingsModel extends GeoModel<Wings> {
        @Override
        public ResourceLocation getModelResource(Wings animatable) {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(Wings animatable) {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(Wings animatable) {
            return ANIMATIONS;
        }

        @Override
        public void setCustomAnimations(Wings animatable, long instanceId, AnimationState<Wings> animationState) {
            for (String name : BODY_BONES) {
                getBone(name).ifPresent(bone -> {
                    BoneSnapshot rest = bone.getInitialSnapshot();
                    bone.setRotX(rest.getRotX());
                    bone.setRotY(rest.getRotY());
                    bone.setRotZ(rest.getRotZ());
                    bone.setPosX(rest.getOffsetX());
                    bone.setPosY(rest.getOffsetY());
                    bone.setPosZ(rest.getOffsetZ());
                    bone.setScaleX(rest.getScaleX());
                    bone.setScaleY(rest.getScaleY());
                    bone.setScaleZ(rest.getScaleZ());
                });
            }
        }
    }

    private static final class WingsRenderer extends GeoObjectRenderer<Wings> {
        private WingsRenderer() {
            super(new WingsModel());
        }

        @Override
        public long getInstanceId(Wings animatable) {
            return animatable.player == null ? 0L : animatable.player.getId();
        }

        @Override
        public RenderType getRenderType(Wings animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
            return RenderType.entityCutoutNoCull(texture);
        }

        // No block centering, the layer has already put it on the player's back
        @Override
        public void preRender(PoseStack poseStack, Wings animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            this.objectRenderTranslations = new org.joml.Matrix4f(poseStack.last().pose());
            for (GeoBone bone : model.topLevelBones()) {
                showWingsOnly(bone, false);
            }
        }

        private static void showWingsOnly(GeoBone bone, boolean wing) {
            boolean shown = wing || bone.getName().equals(WINGS_BONE);
            bone.setHidden(!shown);
            bone.setChildrenHidden(false);
            for (GeoBone child : bone.getChildBones()) {
                showWingsOnly(child, shown);
            }
        }

        @Override
        public int getPackedOverlay(Wings animatable, float u, float partialTick) {
            return OverlayTexture.NO_OVERLAY;
        }
    }

}

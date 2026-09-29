package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk;
import com.radient.tensuraacadamia.entity.DarkShadow;
import com.radient.tensuraacadamia.regestry.DarkShadowEntities;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DarkShadowRenderer extends EntityRenderer<DarkShadow> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private final DarkShadowModel model;
    private static final java.util.Map<PlayerSkin.Model, BlackAbyssModel> ABYSS_MODELS = new java.util.EnumMap<>(PlayerSkin.Model.class);
    public DarkShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new DarkShadowModel(context.bakeLayer(DarkShadowModel.LAYER));
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DarkShadowModel.LAYER, DarkShadowModel::createLayer);
        event.registerLayerDefinition(BlackAbyssModel.NORMAL, () -> BlackAbyssModel.createLayer(false));
        event.registerLayerDefinition(BlackAbyssModel.SLIM, () -> BlackAbyssModel.createLayer(true));
    }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DarkShadowEntities.SHADOW.get(), DarkShadowRenderer::new);
    }
    @SubscribeEvent public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ABYSS_MODELS.clear();
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                BlackAbyssModel armor = new BlackAbyssModel(event.getContext().bakeLayer(
                        skin == PlayerSkin.Model.SLIM ? BlackAbyssModel.SLIM : BlackAbyssModel.NORMAL));
                ABYSS_MODELS.put(skin, armor);
                renderer.addLayer(new BlackAbyssLayer(renderer, armor));
            }
        }
    }
    @Override public ResourceLocation getTextureLocation(DarkShadow shadow) { return TEXTURE; }
    @Override public boolean shouldRender(DarkShadow shadow, Frustum frustum, double x, double y, double z) {
        return shadow.shouldRender(x, y, z);
    }
    @Override public void render(DarkShadow shadow, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        Entity owner = shadow.creator();
        var client = Minecraft.getInstance();
        boolean firstPerson = owner == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
        boolean translucentArms = firstPerson && owner == client.player;
        int armColor = translucentArms ? 0x990C0C0C : 0xFF0C0C0C;
        boolean hideBody = shadow.fused() && !shadow.berserk();
        float age = shadow.tickCount + partialTick;
        if (!hideBody) {
            poses.pushPose();
            poses.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180 - yaw)));
            if (shadow.fused() && owner instanceof net.minecraft.world.entity.LivingEntity living && living.isFallFlying())
                poses.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-90 - owner.getXRot())));
            float size = shadow.visualScale() * (owner instanceof net.minecraft.world.entity.LivingEntity livingOwner ? livingOwner.getScale() : 1)
                    * (shadow.fused() ? 1.04F : 1);
            poses.scale(-size, -size, size); poses.translate(0, -1.501, 0);
            model.animate(shadow, partialTick, yaw);
            model.renderBody(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);
            model.renderEyes(poses, buffers.getBuffer(RenderType.eyes(TEXTURE)), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
        Vec3 base = shadow.getPosition(partialTick);
        if (!hideBody && owner != null) tail(shadow, owner, base, partialTick, poses, buffers, light);
        VertexConsumer armVertices = buffers.getBuffer(translucentArms
                ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE));
        int action = shadow.action();
        if (owner != null && action == DarkShadowQuirk.BARRAGE) {
            barrage(shadow, owner, age, partialTick, poses, buffers, light, translucentArms);
        }
        if (owner != null && action >= 0 && action != DarkShadowQuirk.WOMB && action != DarkShadowQuirk.ANGEL && action != DarkShadowQuirk.BARRAGE) {
            var ownerSkill = owner instanceof net.minecraft.world.entity.LivingEntity living ? DarkShadowQuirk.instance(living) : null;
            boolean mastered = ownerSkill != null && ownerSkill.getMastery() >= 10000;
            Vec3 origin = (hideBody ? owner.getPosition(partialTick).add(0, owner.getBbHeight() * 0.72, 0)
                    : base.add(0, shadow.getBbHeight() * 0.56, 0)).subtract(base);
            Vec3 right = owner.getViewVector(partialTick).cross(new Vec3(0, 1, 0)).normalize();
            if (hideBody && !firstPerson && owner instanceof net.minecraft.world.entity.LivingEntity living) {
                float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) * net.minecraft.util.Mth.DEG_TO_RAD;
                right = new Vec3(-Math.cos(bodyYaw), 0, -Math.sin(bodyYaw));
            }
            if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
            Vec3 end = shadow.armEnd().subtract(base);
            if (firstPerson && shadow.fused()) origin = client.gameRenderer.getMainCamera().getPosition().subtract(base)
                    .add(owner.getViewVector(partialTick).scale(0.8)).add(0, -0.8, 0);
            float progress = shadow.armProgress();
            float extension = (float) Math.pow(Math.clamp((progress - 0.35F) / 0.65F, 0, 1), 2);
            Vec3 forward = end.subtract(origin).normalize();
            if (action == DarkShadowQuirk.SABBATH) forward = owner.getViewVector(partialTick);
            if (forward.lengthSqr() < 0.01) forward = owner.getViewVector(partialTick);
            float width = action == DarkShadowQuirk.ARMS ? 0.85F : action == DarkShadowQuirk.RAGNAROK ? 2.5F
                    : action == DarkShadowQuirk.FLEETING ? 1.5F : action == DarkShadowQuirk.BALDUR ? 2
                    : action == DarkShadowQuirk.CLAWS && mastered ? 1 : 0.65F;
            boolean single = action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.FLEETING
                    || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR;
            for (int side = single ? 1 : -1; side <= 1; side += 2) {
                Vec3 from = origin.add(right.scale(side * (firstPerson ? 0.9 : hideBody ? owner.getBbWidth() * 0.65 : 0.4 * shadow.visualScale())));
                Vec3 pulledBack = from.subtract(forward.scale(0.5 + progress * 0.6)).add(right.scale(side * 0.25));
                Vec3 tip = end.add(right.scale(single ? 0 : side * (action == DarkShadowQuirk.CLAWS ? width * 1.65 : 0.5)));
                Vec3 to = pulledBack.lerp(tip, extension);
                float thickness = action == DarkShadowQuirk.FLEETING || action == DarkShadowQuirk.BALDUR
                        ? width * (0.45F + progress * 0.55F) : width;
                if (action == DarkShadowQuirk.ARMS || action == DarkShadowQuirk.CLAWS) {
                    Vec3 elbow = from.lerp(to, 0.5).add(right.scale(side * 1.2 * (action == DarkShadowQuirk.CLAWS ? 1 - extension : 1)));
                    arm(model, armVertices, poses, from, elbow, thickness, light, armColor);
                    arm(model, armVertices, poses, elbow, to, thickness, light, armColor);
                } else {
                    if (action == DarkShadowQuirk.SABBATH && progress >= 1) from = from.subtract(forward.scale(10));
                    arm(model, armVertices, poses, from, to, thickness, light, armColor);
                }
                if (action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR)
                    arm(model, armVertices, poses, to, to.add(forward.scale(thickness * 0.6)), thickness * 1.4F, light, armColor);
                else talons(model, armVertices, poses, to, forward, right, thickness, light, armColor);
                if (action != DarkShadowQuirk.COMMAND) {
                    int purple = (armColor & 0xFF000000) | 0x291038;
                    Vec3 stripe = from.lerp(to, 0.25).add(right.scale(thickness * 0.4));
                    for (int streak = 1; streak <= 3; streak++) {
                        Vec3 next = from.lerp(to, 0.25 + streak * 0.25)
                                .add(right.scale(thickness * (streak % 2 == 0 ? 0.4 : 0.28)));
                        arm(model, armVertices, poses, stripe, next, thickness * 0.1F, light, purple);
                        stripe = next;
                    }
                }
            }
        }
        Entity captive = shadow.visualCaptive();
        if (captive != null && shadow.action() == DarkShadowQuirk.WOMB) {
            Vec3 center = captive.getPosition(partialTick).subtract(base);
            double radius = (captive.getBbWidth() / 2 + 0.5) * (1.8 - shadow.armProgress() * 0.8);
            for (int ring = 0; ring < 3; ring++) for (int segment = 0; segment < 8; segment++) {
                double a = segment * Math.PI / 4, b = (segment + 1) * Math.PI / 4;
                double y = captive.getBbHeight() * (ring + 0.5) / 3;
                arm(model, armVertices, poses, center.add(Math.cos(a) * radius, y, Math.sin(a) * radius),
                        center.add(Math.cos(b) * radius, y, Math.sin(b) * radius), 0.35F, light, armColor);
            }
            for (int hoop = 0; hoop < 2; hoop++) for (int segment = 0; segment < 8; segment++) {
                double a = segment * Math.PI / 4, b = (segment + 1) * Math.PI / 4;
                Vec3 axis = hoop == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
                Vec3 from = center.add(axis.scale(Math.cos(a) * radius)).add(0, captive.getBbHeight() * (0.5 + Math.sin(a) * 0.65), 0);
                Vec3 to = center.add(axis.scale(Math.cos(b) * radius)).add(0, captive.getBbHeight() * (0.5 + Math.sin(b) * 0.65), 0);
                arm(model, armVertices, poses, from, to, 0.3F, light, armColor);
            }
        }
        if (owner instanceof net.minecraft.world.entity.LivingEntity living && (living.hasEffect(MHAEffects.DARK_SHADOW_FLIGHT) || action == DarkShadowQuirk.ANGEL)) {
            Vec3 forward = owner.getViewVector(partialTick).normalize();
            Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(forward).normalize();
            float unfold = action == DarkShadowQuirk.ANGEL ? Math.max(0.1F, shadow.armProgress()) : 1;
            Vec3 center = owner.getPosition(partialTick).subtract(base).add(0, living.isFallFlying() ? 0.55 : owner.getBbHeight() * 0.65, 0)
                    .subtract(forward.scale(0.25));
            for (int side = -1; side <= 1; side += 2) {
                Vec3 elbow = center.add(right.scale(side * 1.25 * unfold)).add(up.scale(Math.sin(age * 0.5) * 0.35)).subtract(forward.scale(0.2));
                arm(model, armVertices, poses, center, elbow, 0.35F, light, armColor);
                for (int feather = 0; feather < 5; feather++) {
                    Vec3 root = center.lerp(elbow, 0.35 + feather * 0.15);
                    Vec3 tip = elbow.add(right.scale(side * (0.8 - feather * 0.12) * unfold))
                            .subtract(up.scale((0.3 + feather * 0.17) * unfold))
                            .subtract(forward.scale((0.2 + feather * 0.18) * unfold));
                    arm(model, armVertices, poses, root, tip, 0.22F, light, armColor);
                    arm(model, armVertices, poses, root.subtract(forward.scale(0.09)), tip.subtract(forward.scale(0.09)),
                            0.08F, light, (armColor & 0xFF000000) | 0x30203E);
                }
            }
        }
        super.render(shadow, yaw, partialTick, poses, buffers, light);
    }
    private void barrage(DarkShadow shadow, Entity owner, float age, float partialTick,
                         PoseStack poses, MultiBufferSource buffers, int light, boolean firstPerson) {
        Vec3 origin = new Vec3(0, shadow.getBbHeight() * 0.56, 0);
        Vec3 forward = owner.getViewVector(partialTick).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        Vec3 up = forward.cross(right).normalize();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (int trail = 2; trail >= 0; trail--) for (int side = -1; side <= 1; side += 2) {
            double time = age - trail * 0.65, phase = time / (DarkShadow.BARRAGE_INTERVAL * 2.0) + (side == 1 ? 0.5 : 0);
            double extension = 0.5 - 0.5 * Math.cos(phase * Math.PI * 2);
            Vec3 shoulder = origin.add(right.scale(side * Math.min(0.7, shadow.visualScale() * 0.35)));
            Vec3 tip = origin.add(forward.scale(0.25 + extension * (DarkShadow.BARRAGE_REACH - 0.25)))
                    .add(right.scale(side * (0.3 + Math.sin(time * 0.73) * 0.18)))
                    .add(up.scale(Math.cos(time * 0.91 + side) * 0.55));
            Vec3 elbow = shoulder.lerp(tip, 0.5).add(right.scale(side * (1 - extension) * 0.45));
            int alpha = trail == 0 ? (firstPerson ? 153 : 255) : (firstPerson ? 45 : 65) / trail;
            int black = alpha << 24 | 0x0C0C0C, purple = alpha << 24 | 0x291038;
            arm(model, vertices, poses, shoulder, elbow, 0.27F, light, black);
            arm(model, vertices, poses, elbow, tip, 0.31F, light, black);
            arm(model, vertices, poses, tip, tip.add(forward.scale(0.35)), 0.46F, light, black);
            Vec3 stripe = right.scale(side * 0.13);
            arm(model, vertices, poses, elbow.add(stripe), tip.add(stripe), 0.06F, light, purple);
        }
    }
    private void tail(DarkShadow shadow, Entity owner, Vec3 base, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 from = new Vec3(0, 0.2 * shadow.visualScale(), 0);
        Vec3 to = owner.getPosition(partialTick).subtract(base).add(0, owner.getBbHeight() * 0.55, 0);
        Vec3 direction = to.subtract(from), sideways = direction.cross(new Vec3(0, 1, 0)).normalize();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        float age = shadow.tickCount + partialTick;
        Vec3 previous = from;
        for (int step = 1; step <= 10; step++) {
            double t = step / 10.0, curve = Math.sin(Math.PI * t);
            Vec3 point = from.lerp(to, t).add(0, -curve * Math.min(0.5, direction.length() * 0.12), 0)
                    .add(sideways.scale(curve * Math.sin(age * 0.08 + t * 5) * 0.12));
            float width = (float) ((0.28 * (1 - t) + 0.1 * t) * Math.sqrt(shadow.visualScale()));
            arm(model, vertices, poses, previous, point, width, light, 0xFF0A0811);
            Vec3 stripe = sideways.scale(width * 0.42);
            arm(model, vertices, poses, previous.add(stripe), point.add(stripe), width * 0.12F, light, 0xFF30203E);
            previous = point;
        }
    }
    private static void talons(DarkShadowModel model, VertexConsumer vertices, PoseStack poses, Vec3 point,
                               Vec3 forward, Vec3 right, float width, int light, int color) {
        for (int claw = -1; claw <= 1; claw++) {
            Vec3 start = point.add(right.scale(claw * width * 0.45));
            Vec3 bend = start.add(forward.scale(width)).add(0, width * 0.15, 0);
            arm(model, vertices, poses, start, bend, width * 0.16F, light, color);
            arm(model, vertices, poses, bend, bend.add(forward.scale(width * 0.35)).add(0, -width * 0.45, 0),
                    width * 0.1F, light, color);
        }
    }
    private static final class BlackAbyssLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final BlackAbyssModel armor;
        private BlackAbyssLayer(PlayerRenderer renderer, BlackAbyssModel armor) { super(renderer); this.armor = armor; }
        @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                     float swing, float amount, float partialTick, float age, float yaw, float pitch) {
            DarkShadow shadow = DarkShadowQuirk.shadow(player);
            if (player.isInvisible() || shadow == null || !shadow.fused() || shadow.berserk()) return;
            armor.render(poses, buffers, light, getParentModel(), shadow, partialTick);
        }
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
    public static final class FirstPerson {
        @SubscribeEvent public static void renderArm(RenderArmEvent event) {
            var player = event.getPlayer();
            DarkShadow shadow = DarkShadowQuirk.shadow(player);
            BlackAbyssModel armor = ABYSS_MODELS.get(player.getSkin().model());
            if (armor == null || player.isInvisible() || shadow == null || !shadow.fused() || shadow.berserk()) return;
            if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
            armor.renderFirstPersonArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                    event.getArm() == HumanoidArm.RIGHT ? renderer.getModel().rightArm : renderer.getModel().leftArm, event.getArm());
            event.setCanceled(true);
        }
    }
    private static void arm(DarkShadowModel model, VertexConsumer vertices, PoseStack poses, Vec3 start, Vec3 end,
                            float width, int light, int color) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 0.00001) return;
        var part = model.segment;
        poses.pushPose(); poses.translate(start.x, start.y, start.z);
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), delta.normalize().toVector3f()));
        poses.scale(width / 0.25F, (float) delta.length() / 0.75F, width / 0.25F);
        part.visible = true;
        part.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, color);
        part.visible = false;
        poses.popPose();
    }
}

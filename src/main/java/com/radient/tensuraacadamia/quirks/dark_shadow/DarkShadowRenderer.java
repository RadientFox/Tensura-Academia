package com.radient.tensuraacadamia.quirks.dark_shadow;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
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
        int action = shadow.action();
        DarkShadowModel.AttackPose attack = DarkShadowModel.attackPose(shadow, partialTick);
        if (action >= 0 && action != DarkShadowQuirk.ANGEL) {
            Vec3 facing = shadow.armEnd().subtract(shadow.position());
            if (facing.horizontalDistanceSqr() > .01)
                yaw = (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
        }
        float size = shadow.visualScale() * (owner instanceof net.minecraft.world.entity.LivingEntity livingOwner ? livingOwner.getScale() : 1)
                * (shadow.fused() ? 1.04F : 1);
        if (!hideBody) {
            poses.pushPose();
            poses.mulPose(new Quaternionf().rotationY((float) Math.toRadians(180 - yaw)));
            if (shadow.fused() && owner instanceof net.minecraft.world.entity.LivingEntity living && living.isFallFlying())
                poses.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-90 - owner.getXRot())));
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
            float progress = attack.windup();
            float extension = attack.extension();
            Vec3 forward = end.subtract(origin).normalize();
            if (action == DarkShadowQuirk.SABBATH) forward = owner.getViewVector(partialTick);
            if (forward.lengthSqr() < 0.01) forward = owner.getViewVector(partialTick);
            if (!hideBody) {
                right = forward.cross(new Vec3(0, 1, 0)).normalize();
                if (right.lengthSqr() < .01) right = new Vec3(1, 0, 0);
            }
            Vec3 up = right.cross(forward).normalize();
            float width = action == DarkShadowQuirk.ARMS ? 0.85F : action == DarkShadowQuirk.RAGNAROK ? 2.5F
                    : action == DarkShadowQuirk.FLEETING ? 1.5F : action == DarkShadowQuirk.BALDUR ? 2
                    : action == DarkShadowQuirk.CLAWS && mastered ? 1 : action == DarkShadowQuirk.COMMAND ? .34F * size : .65F;
            boolean single = action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.FLEETING
                    || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR;
            for (int armIndex = 0; armIndex < (single ? 1 : 2); armIndex++) {
                armVertices = buffers.getBuffer(translucentArms ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE));
                int side = single ? (action == DarkShadowQuirk.COMMAND ? attack.side() : 1) : armIndex * 2 - 1;
                Vec3 from = hideBody ? origin.add(right.scale(side * (firstPerson ? .9 : owner.getBbWidth() * .65)))
                        : model.shoulder(side, yaw, size);
                float gestureScale = action == DarkShadowQuirk.COMMAND ? size : 1;
                Vec3 pulledBack = from.subtract(forward.scale((.25 + progress * .35) * gestureScale))
                        .add(right.scale(side * (.18 + progress * .18) * gestureScale)).add(up.scale((.15 + progress * .35) * gestureScale));
                if (action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR)
                    pulledBack = from.add(0, width * (.6 + progress * .65), 0).subtract(forward.scale(.3 + progress * .6));
                else if (action == DarkShadowQuirk.FLEETING || action == DarkShadowQuirk.CLAWS)
                    pulledBack = from.add(right.scale(side * width * (1 + progress))).add(up.scale(.25));
                if (!hideBody && attack.recovery() > .5F)
                    pulledBack = pulledBack.lerp(model.hand(side, yaw, size), (attack.recovery() - .5F) * 2);
                Vec3 tip = end.add(right.scale(single ? 0 : side * (action == DarkShadowQuirk.CLAWS ? width * 1.65 : 0.5)));
                Vec3 to = pulledBack.lerp(tip, extension);
                float thickness = action == DarkShadowQuirk.FLEETING || action == DarkShadowQuirk.BALDUR
                        ? width * (0.45F + progress * 0.55F) : width;
                if (action == DarkShadowQuirk.SABBATH && extension >= .95F) from = from.subtract(forward.scale(10));
                Vec3 elbow = from.lerp(to, .48).add(right.scale(side * thickness * (1.55 - extension * 1.3)))
                        .subtract(up.scale(thickness * (.65 - extension * .5)));
                if (action == DarkShadowQuirk.ARMS) elbow = elbow.add(right.scale(side * .7));
                arm(model, armVertices, poses, from, elbow, thickness * .8F, light, armColor);
                arm(model, armVertices, poses, elbow, to, thickness, light, armColor);
                if (action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR)
                    arm(model, armVertices, poses, to, to.add(forward.scale(thickness * 0.6)), thickness * 1.4F, light, armColor);
                else talons(model, armVertices, poses, to, action == DarkShadowQuirk.ARMS ? right.scale(-side) : forward,
                        action == DarkShadowQuirk.ARMS ? up : right, thickness, light, armColor);
                int purple = (armColor & 0xFF000000) | 0x291038;
                Vec3 stripe = right.scale(side * thickness * .48);
                arm(model, armVertices, poses, elbow.add(stripe), elbow.lerp(to, .5).add(stripe.scale(.7)), thickness * .08F, light, purple);
                arm(model, armVertices, poses, elbow.lerp(to, .5).add(stripe.scale(.7)), to.add(stripe), thickness * .08F, light, purple);
                punchFlourish(action, attack, pulledBack, to, forward, up, right, thickness, base, firstPerson, poses, buffers);
            }
        }
        Entity captive = shadow.visualCaptive();
        if (captive != null && shadow.action() == DarkShadowQuirk.WOMB) {
            Vec3 center = captive.getPosition(partialTick).subtract(base);
            double radius = (captive.getBbWidth() / 2 + 0.5) * (1.8 - shadow.armProgress() * 0.8);
            radius *= 1 + Math.sin(Math.floor(age / 2) * .12) * .025;
            for (int ring = 0; ring < 3; ring++) for (int segment = 0; segment < 8; segment++) {
                double a = segment * Math.PI / 4, b = (segment + 1) * Math.PI / 4;
                double y = captive.getBbHeight() * (ring + 0.5) / 3;
                arm(model, armVertices, poses, center.add(Math.cos(a) * radius, y, Math.sin(a) * radius),
                        center.add(Math.cos(b) * radius, y, Math.sin(b) * radius), 0.35F, light, armColor);
                if (segment % 2 == 0) arm(model, armVertices, poses,
                        center.add(Math.cos(a) * (radius + .17), y, Math.sin(a) * (radius + .17)),
                        center.add(Math.cos(b) * (radius + .17), y, Math.sin(b) * (radius + .17)),
                        .045F, light, (armColor & 0xFF000000) | 0x38204E);
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
            double flapPhase = (Math.floor(age / 2) * 2 % 14) / 14;
            double flap = flapPhase < .3 ? 1 - flapPhase / .15 : -1 + (flapPhase - .3) / .35;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 elbow = center.add(right.scale(side * 1.25 * unfold)).add(up.scale(flap * .35 * unfold)).subtract(forward.scale(0.2));
                arm(model, armVertices, poses, center, elbow, 0.35F, light, armColor);
                for (int feather = 0; feather < 5; feather++) {
                    Vec3 root = center.lerp(elbow, 0.35 + feather * 0.15);
                    Vec3 tip = elbow.add(right.scale(side * (0.8 - feather * 0.12) * unfold))
                            .add(up.scale((flap * .3 - .3 - feather * .17) * unfold))
                            .subtract(forward.scale((0.2 + feather * 0.18) * unfold));
                    arm(model, armVertices, poses, root, tip, 0.22F, light, armColor);
                    arm(model, armVertices, poses, root.subtract(forward.scale(0.09)), tip.subtract(forward.scale(0.09)),
                            0.08F, light, (armColor & 0xFF000000) | 0x30203E);
                }
            }
        }
        flourish(shadow, owner, base, age, firstPerson, poses, buffers);
        super.render(shadow, yaw, partialTick, poses, buffers, light);
    }
    private static void flourish(DarkShadow shadow, Entity owner, Vec3 base, float age, boolean firstPerson,
                                 PoseStack poses, MultiBufferSource buffers) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        if (SkillVfx.detail(cam, Vec3.ZERO) == 0 || owner == null) return;
        Vec3 center = new Vec3(0, shadow.getBbHeight() * .55, 0);
        if (!shadow.fused() || shadow.berserk()) {
            for (int i = 0; i < 3; i++) {
                double a = age * .045 + i * Math.PI * 2 / 3;
                Vec3 p = center.add(Math.cos(a) * shadow.visualScale() * .45, Math.sin(age * .06 + i) * .25, Math.sin(a) * shadow.visualScale() * .45);
                SkillVfx.disc(poses, buffers, cam, p, shadow.visualScale() * .6, 0x14051d, .18f, false);
            }
        }
        int action = shadow.action();
        if (action < 0 || action == DarkShadowQuirk.WOMB || action == DarkShadowQuirk.ANGEL) return;
        Vec3 end = shadow.armEnd().subtract(base), from = owner.position().add(0, owner.getBbHeight() * .65, 0).subtract(base);
        Vec3 direction = end.subtract(from); if (direction.lengthSqr() < .01) direction = owner.getLookAngle();
        DarkShadowModel.AttackPose attack = DarkShadowModel.attackPose(shadow, age - shadow.tickCount);
        float progress = shadow.armProgress(), alpha = (firstPerson ? .25f : .65f) * (1 - attack.recovery());
        if (progress < .5f && action != DarkShadowQuirk.BARRAGE) {
            SkillVfx.ring(poses, buffers, cam, from, direction, .25 + progress * .6, .035, 0x9b3ddd, alpha, age * .2);
            SkillVfx.disc(poses, buffers, cam, from, .4, 0x7d29ae, progress * alpha, true);
        } else {
            Vec3 tip = from.lerp(end, attack.extension());
            if (action == DarkShadowQuirk.BARRAGE) tip = from.add(owner.getLookAngle().scale(2));
            SkillVfx.ribbon(poses, buffers, cam, from, tip, .12, 0x67208c, alpha * .4f, true);
            if (action == DarkShadowQuirk.CLAWS || action == DarkShadowQuirk.FLEETING || action == DarkShadowQuirk.SABBATH) {
                Vec3 right = SkillVfx.perpendicular(direction);
                for (int i = -1; i <= 1; i++) SkillVfx.ribbon(poses, buffers, cam, tip.add(right.scale(i * .3)),
                        tip.subtract(direction.normalize().scale(1.2)).add(right.scale(i * .2)), .045, 0xbf65e8, alpha, true);
            }
            if (action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR)
                SkillVfx.ring(poses, buffers, cam, tip, direction, .7 + Math.sin(age * .5) * .12, .065, 0xa441dd, alpha, age * .1);
        }
    }
    private static void punchFlourish(int action, DarkShadowModel.AttackPose attack, Vec3 cocked,
                                      Vec3 fist, Vec3 forward, Vec3 up, Vec3 right, float width, Vec3 base,
                                      boolean firstPerson, PoseStack poses, MultiBufferSource buffers) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(base);
        if (SkillVfx.detail(camera, fist) == 0) return;
        float alpha = firstPerson ? .2F : .5F;
        if (action == DarkShadowQuirk.CLAWS || action == DarkShadowQuirk.FLEETING || action == DarkShadowQuirk.SABBATH) {
            SkillVfx.crescent(poses, buffers, camera, fist.subtract(up.scale(width * .5)), right, up,
                    width * 1.6, width * .3, 0xB277DE, alpha * attack.impact(), attack.side() * .3);
        }
        if (attack.extension() > .3 && attack.recovery() < .5) {
            for (int streak = -1; streak <= 1; streak++) {
                Vec3 offset = up.scale(streak * width * .45).add(right.scale(streak * width * .18));
                SkillVfx.ribbon(poses, buffers, camera, cocked.lerp(fist, .35).add(offset), fist.add(offset),
                        width * .075, 0x8c4abd, alpha * attack.extension(), true);
            }
        }
        if (attack.impact() <= 0 || action == DarkShadowQuirk.ARMS) return;
        double radius = width * (1 + (1 - attack.impact()) * 1.6);
        SkillVfx.ring(poses, buffers, camera, fist.add(forward.scale(width * .25)), forward,
                radius, width * .09, 0xb374db, alpha * attack.impact(), 0);
        if (action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR) {
            SkillVfx.burst(poses, buffers, camera, fist, (1 - attack.impact()) * 12, radius * (firstPerson ? .4 : .8), 0xb374db, 0);
        }
    }
    private void barrage(DarkShadow shadow, Entity owner, float age, float partialTick,
                         PoseStack poses, MultiBufferSource buffers, int light, boolean firstPerson) {
        Vec3 origin = new Vec3(0, shadow.getBbHeight() * 0.56, 0);
        Vec3 forward = owner.getViewVector(partialTick).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 0.01) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(forward).normalize();
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for (int trail = 2; trail >= 0; trail--) for (int side = -1; side <= 1; side += 2) {
            vertices = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
            double time = age - trail * .45;
            double phase = time / (DarkShadow.BARRAGE_INTERVAL * 2.0) + (side == 1 ? .5 : 0);
            phase -= Math.floor(phase);
            double extension = phase < .25 ? phase * 4 : 1 - (phase - .25) / .75;
            Vec3 shoulder = origin.add(right.scale(side * Math.min(.7, shadow.visualScale() * .4)));
            Vec3 tip = origin.add(forward.scale(0.25 + extension * (DarkShadow.BARRAGE_REACH - 0.25)))
                    .add(right.scale(side * (.25 + Math.sin(Math.floor(time / 4) * 2.3) * .15)))
                    .add(up.scale(Math.cos(Math.floor(time / 4) * 1.7 + side) * .4));
            Vec3 elbow = shoulder.lerp(tip, .45).add(right.scale(side * (1 - extension) * .6)).subtract(up.scale(.2));
            int alpha = trail == 0 ? (firstPerson ? 153 : 255) : (firstPerson ? 45 : 65) / trail;
            int black = alpha << 24 | 0x0C0C0C, purple = alpha << 24 | 0x291038;
            arm(model, vertices, poses, shoulder, elbow, 0.27F, light, black);
            arm(model, vertices, poses, elbow, tip, 0.31F, light, black);
            arm(model, vertices, poses, tip, tip.add(forward.scale(0.35)), 0.46F, light, black);
            Vec3 stripe = right.scale(side * 0.13);
            arm(model, vertices, poses, elbow.add(stripe), tip.add(stripe), 0.06F, light, purple);
            if (trail == 0 && extension > .65) {
                Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(shadow.getPosition(partialTick));
                SkillVfx.ribbon(poses, buffers, camera, shoulder.lerp(tip, .4), tip, .045, 0x9a56cb,
                        firstPerson ? .18F : .4F, true);
            }
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

package com.radient.tensuraacadamia.quirks.bat;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class BatClient {
    private static final RenderType MEMBRANE = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/block/brown_terracotta.png"));
    private static final RenderType FANGS = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"));
    private static final Vec3[] FINGERS = {
            new Vec3(1.05, -.06, -.02), new Vec3(.76, .5, .03), new Vec3(.35, .82, .08)
    };

    private BatClient() { }

    public static void pose(PlayerModel<?> model, LivingEntity owner) {
        if (!BatEvents.active(owner)) return;
        model.rightArm.visible = model.leftArm.visible = false;
        model.rightSleeve.visible = model.leftSleeve.visible = false;
        if (BatEvents.hanging(owner)) {
            model.rightLeg.xRot = model.leftLeg.xRot = -.35F;
            model.rightPants.copyFrom(model.rightLeg);
            model.leftPants.copyFrom(model.leftLeg);
        }
    }

    @SubscribeEvent
    public static void invert(RenderPlayerEvent.Pre event) {
        if (!BatEvents.hanging(event.getEntity())) return;
        event.getPoseStack().translate(0, event.getEntity().getBbHeight(), 0);
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(180));
    }

    @SubscribeEvent
    public static void restore(RenderPlayerEvent.Post event) {
        if (!BatEvents.hanging(event.getEntity())) return;
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(-180));
        event.getPoseStack().translate(0, -event.getEntity().getBbHeight(), 0);
    }

    @SubscribeEvent
    public static void wakeOnJump(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && BatEvents.hanging(minecraft.player)
                && minecraft.player.input.jumping && minecraft.player.tickCount % 4 == 0)
            PacketDistributor.sendToServer(BatWakePayload.INSTANCE);
    }

    @SubscribeEvent
    public static void firstPerson(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null || !BatEvents.active(player)) return;
        event.setCanceled(true);
        int side = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        if (event.getHand() == InteractionHand.OFF_HAND) side = -side;
        var poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-side * .5, -.45, -.75);
        poses.scale(.45F, -.45F, .45F);
        wing(poses, event.getMultiBufferSource(), -side, BatEvents.flying(player) ? .7F : .12F,
                player.tickCount + event.getPartialTick(), event.getPackedLight());
        poses.popPose();
    }

    private static void wing(PoseStack poses, MultiBufferSource buffers, int side, float spread, float age, int light) {
        poses.pushPose();
        poses.mulPose(Axis.YP.rotation(side * (1 - spread) * .45F));
        poses.mulPose(Axis.XP.rotation(.12F));
        poses.mulPose(Axis.ZP.rotation(side * (float) Math.sin(Math.floor(age / 2) * .9) * (.02F + spread * .28F)));
        var vertices = buffers.getBuffer(MEMBRANE);
        Vec3 shoulder = Vec3.ZERO;
        Vec3 elbow = new Vec3(side * (.07 + spread * .24), .34 - spread * .27, .02);
        Vec3 wrist = new Vec3(side * (.13 + spread * .47), .61 - spread * .46, -.025);
        Vec3[] tips = new Vec3[4];
        for (int finger = 0; finger < FINGERS.length; finger++) {
            Vec3 offset = FINGERS[finger];
            tips[finger] = wrist.add(side * offset.x * (.24 + spread * .76),
                    offset.y * (.72 + spread * .28), offset.z + (1 - spread) * .14);
        }
        tips[3] = new Vec3(side * .025, .83, .1);
        Vec3 center = wrist.add(0, .18, -.045);
        web(poses, vertices, center, shoulder, elbow, light);
        web(poses, vertices, center, elbow, wrist, light);
        web(poses, vertices, center, wrist, tips[0], light);
        for (int finger = 0; finger < tips.length - 1; finger++) {
            Vec3 start = tips[finger], end = tips[finger + 1];
            Vec3 control = start.lerp(end, .5).lerp(wrist, .45);
            Vec3 previous = start;
            for (int segment = 1; segment <= 4; segment++) {
                double t = segment / 4.0;
                Vec3 edge = start.scale((1 - t) * (1 - t)).add(control.scale(2 * t * (1 - t)))
                        .add(end.scale(t * t)).add(0, 0, Math.sin(t * Math.PI) * .035);
                web(poses, vertices, center, previous, edge, light);
                previous = edge;
            }
        }
        web(poses, vertices, center, tips[3], shoulder, light);
        bone(poses, vertices, shoulder, elbow, .045, .035, 0xBCA29B, light);
        bone(poses, vertices, elbow, wrist, .035, .025, 0xBCA29B, light);
        bone(poses, vertices, wrist, wrist.add(side * .08, -.12, -.02), .018, .004, 0xBCA29B, light);
        for (Vec3 tip : tips) bone(poses, vertices, wrist, tip, .018, .006, 0xAA9291, light);
        poses.popPose();
    }

    private static void web(PoseStack poses, VertexConsumer vertices, Vec3 center, Vec3 a, Vec3 b, int light) {
        triangle(poses, vertices, center, a, b, 0x88717F, light);
        Vec3 thickness = new Vec3(0, 0, .018);
        triangle(poses, vertices, center.add(thickness), b.add(thickness), a.add(thickness), 0x75616D, light);
    }

    private static void triangle(PoseStack poses, VertexConsumer vertices, Vec3 a, Vec3 b, Vec3 c, int color, int light) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        for (Vec3 point : new Vec3[]{a, b, c, a}) vertex(poses, vertices, point, normal, color, light);
    }

    private static void vertex(PoseStack poses, VertexConsumer vertices, Vec3 point, Vec3 normal, int color, int light) {
        vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                .setUv((float) (point.x * 2), (float) (point.y * 2)).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(poses.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void bone(PoseStack poses, VertexConsumer vertices, Vec3 start, Vec3 end,
                             double startWidth, double endWidth, int color, int light) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 right = SkillVfx.perpendicular(axis), up = axis.cross(right);
        for (int face = 0; face < 4; face++) {
            double a = face * Math.PI / 2, b = (face + 1) * Math.PI / 2;
            Vec3 p = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 q = right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            Vec3 normal = p.add(q).normalize();
            for (Vec3 point : new Vec3[]{start.add(p.scale(startWidth)), end.add(p.scale(endWidth)),
                    end.add(q.scale(endWidth)), start.add(q.scale(startWidth))})
                vertex(poses, vertices, point, normal, color, light);
        }
    }

    private static final class Wings extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private Wings(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float swing, float amount, float partial, float age, float yaw, float pitch) {
            if (!BatEvents.active(player) || player.isInvisible()) return;
            var model = getParentModel();
            boolean resting = BatEvents.hanging(player);
            float spread = resting ? 0 : BatEvents.flying(player) ? 1 : .08F;
            for (int side : new int[]{-1, 1}) {
                poses.pushPose();
                model.body.translateAndRotate(poses);
                poses.translate(side * .25, .12, 0);
                wing(poses, buffers, side, spread, age, light);
                poses.popPose();
            }
            poses.pushPose();
            model.head.translateAndRotate(poses);
            var vertices = buffers.getBuffer(MEMBRANE);
            for (int side : new int[]{-1, 1}) {
                triangle(poses, vertices, new Vec3(side * .18, -.42, 0),
                        new Vec3(side * .3, -.61, -.03), new Vec3(side * .28, -.3, .06), 0x88717F, light);
            }
            vertices = buffers.getBuffer(FANGS);
            for (int side : new int[]{-1, 1}) bone(poses, vertices, new Vec3(side * .11, -.08, -.26),
                    new Vec3(side * .11, .025, -.27), .025, .002, 0xE7DFCA, light);
            poses.popPose();
        }
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        @SubscribeEvent
        public static void register(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Wings(renderer));
            }
        }
    }
}

package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.network.QuirkAnimPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WaldoLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final String GOLD = "minecraft:block/gold_block";
    private static final String IRON = "minecraft:block/iron_block";
    private static final int GOLD_TINT = 0xFFFFFF;
    private static final int CLAW_TINT = 0xFFF2C8;
    private static final int IRON_TINT = 0xC8C4BE;
    private static final int HUB_TINT = 0x6E6A70;

    private static final double UPPER_BONE = 1.0D;
    private static final double LOWER_BONE = 1.15D;
    private static final double CLAW = 0.45D;
    private static final double NECK_TO_FEET = 1.5D;
    private static final Vec3 UP = new Vec3(0.0D, -1.0D, 0.0D);
    private static final Vec3 DOWN = new Vec3(0.0D, 1.0D, 0.0D);

    public WaldoLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new WaldoLayer(renderer));
            }
        }
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partial, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!QuirkAnimClient.hasState(player.getId(), QuirkAnimPayload.SPIDER_WALDOS) || player.isInvisible()) {
            return;
        }

        poses.pushPose();
        getParentModel().body.translateAndRotate(poses);
        PoseStack.Pose pose = poses.last();
        Matrix4f toLocal = new Matrix4f(pose.pose()).invert();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 feet = local(toLocal, player.getPosition(partial).subtract(camera));
        boolean inWorld = Math.abs(feet.x) < 0.5D && Math.abs(feet.y - NECK_TO_FEET) < 0.75D && Math.abs(feet.z) < 0.75D;

        VertexConsumer buffer = BlockyBox.atlas(buffers);
        TextureAtlasSprite gold = BlockyBox.sprite(GOLD);
        TextureAtlasSprite iron = BlockyBox.sprite(IRON);
        float t = ageInTicks;
        float walk = limbSwing * 0.6F;
        float stride = Math.min(1.0F, limbSwingAmount * 1.5F);
        long now = player.level().getGameTime();

        BlockyBox.textured(buffer, pose, new Vec3(0.0D, 0.26D, 0.17D), new Vec3(0.2D, 0.0D, 0.0D), new Vec3(0.0D, 0.2D, 0.0D),
                new Vec3(0.0D, 0.0D, 0.05D), iron, HUB_TINT, light);

        for (int leg = 0; leg < 4; leg++) {
            double side = leg % 2 == 0 ? 1.0D : -1.0D;
            boolean upper = leg < 2;
            Vec3 mount = new Vec3(-side * 0.17D, upper ? 0.12D : 0.38D, 0.2D);

            Vec3 target;
            Vec3 pole;
            if (upper) {
                double bob = Mth.sin(t * 0.08F + leg) * 0.06D;
                target = new Vec3(-side * 1.25D, -1.05D + bob, 0.6D);
                pole = new Vec3(-side * 0.5D, -1.0D, 0.4D);
            } else if (player.onGround()) {
                double phase = walk + (side > 0 ? 0.0D : Math.PI);
                double lift = Math.max(0.0D, Math.cos(phase)) * 0.35D * stride;
                target = new Vec3(-side * 1.25D, NECK_TO_FEET - 0.02D - lift, 0.15D - Math.sin(phase) * 0.55D * stride);
                pole = new Vec3(-side * 0.6D, -1.2D, 0.0D);
            } else {
                double bob = Mth.sin(t * 0.1F + leg) * 0.06D;
                target = new Vec3(-side * 1.45D, 1.05D + bob, 0.45D);
                pole = new Vec3(-side * 0.7D, -1.0D, 0.0D);
            }

            WaldoClient.Strike strike = inWorld ? WaldoClient.getStrike(player.getId(), leg) : null;
            if (strike != null) {
                float k = (now - strike.start() + partial) / WaldoClient.STRIKE_TICKS;
                Entity victim = player.level().getEntity(strike.targetId());
                if (k >= 0.0F && k <= 1.0F && victim != null) {
                    Vec3 hit = local(toLocal, victim.getPosition(partial).add(0.0D, victim.getBbHeight() * 0.5D, 0.0D).subtract(camera));
                    float reach = k < 0.35F ? ease(k / 0.35F) : 1.0F - ease((k - 0.35F) / 0.65F);
                    target = target.lerp(hit, reach);
                }
            }

            drawLeg(buffer, pose, mount, target, pole, gold, iron, light);
        }
        poses.popPose();
    }

    private static Vec3 local(Matrix4f toLocal, Vec3 render) {
        Vector3f v = toLocal.transformPosition(new Vector3f((float) render.x, (float) render.y, (float) render.z));
        return new Vec3(v.x, v.y, v.z);
    }

    private static void drawLeg(VertexConsumer buffer, PoseStack.Pose pose, Vec3 mount, Vec3 target, Vec3 pole,
                                TextureAtlasSprite gold, TextureAtlasSprite iron, int light) {
        Vec3 span = target.subtract(mount);
        double distance = Math.min(span.length(), UPPER_BONE + LOWER_BONE - 0.02D);
        if (distance < 1.0E-3D) {
            return;
        }

        Vec3 dir = span.normalize();
        Vec3 end = mount.add(dir.scale(distance));
        double along = (UPPER_BONE * UPPER_BONE - LOWER_BONE * LOWER_BONE + distance * distance) / (2.0D * distance);
        double rise = Math.sqrt(Math.max(0.0D, UPPER_BONE * UPPER_BONE - along * along));
        Vec3 bend = pole.subtract(dir.scale(pole.dot(dir)));
        bend = bend.lengthSqr() < 1.0E-4D ? UP.subtract(dir.scale(dir.dot(UP))) : bend;
        bend = bend.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : bend.normalize();
        Vec3 knee = mount.add(dir.scale(along)).add(bend.scale(rise));

        Vec3 clawDir = end.subtract(knee).normalize().lerp(DOWN, 0.35D).normalize();
        Vec3 tip = end.add(clawDir.scale(CLAW));

        segment(buffer, pose, mount, knee, 0.055D, gold, GOLD_TINT, light);
        segment(buffer, pose, knee, end, 0.045D, gold, GOLD_TINT, light);
        segment(buffer, pose, end, end.add(clawDir.scale(CLAW * 0.55D)), 0.04D, gold, CLAW_TINT, light);
        segment(buffer, pose, end.add(clawDir.scale(CLAW * 0.5D)), tip, 0.022D, gold, CLAW_TINT, light);
        joint(buffer, pose, mount, 0.08D, iron, HUB_TINT, light);
        joint(buffer, pose, knee, 0.08D, iron, IRON_TINT, light);
        joint(buffer, pose, end, 0.065D, iron, IRON_TINT, light);
    }

    private static void segment(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, double thickness, TextureAtlasSprite sprite, int tint, int light) {
        Vec3 half = to.subtract(from).scale(0.5D);
        if (half.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 axis = half.normalize();
        Vec3 ref = Math.abs(axis.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : DOWN;
        Vec3 a = axis.cross(ref).normalize().scale(thickness);
        Vec3 b = axis.cross(a).normalize().scale(thickness);
        BlockyBox.textured(buffer, pose, from.add(half), half, a, b, sprite, tint, light);
    }

    private static void joint(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, double size, TextureAtlasSprite sprite, int tint, int light) {
        BlockyBox.textured(buffer, pose, center, new Vec3(size, 0.0D, 0.0D), new Vec3(0.0D, size, 0.0D), new Vec3(0.0D, 0.0D, size), sprite, tint, light);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}

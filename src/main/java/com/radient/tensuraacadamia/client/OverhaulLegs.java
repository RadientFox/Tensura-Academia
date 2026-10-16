package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.Animation;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class OverhaulLegs {
    private static final Map<ModelPart, Joint> JOINTS = new WeakHashMap<>();
    private OverhaulLegs() { }

    public static void reset(PlayerModel<?> model) {
        for (ModelPart part : List.of(model.rightLeg, model.leftLeg, model.rightPants, model.leftPants)) {
            Joint joint = JOINTS.get(part);
            if (joint != null) joint.active = false;
        }
    }

    public static void pose(PlayerModel<?> model, Animation clip, float time) {
        float right = OverhaulAnimations.values(clip, "rightKnee", time, false)[0];
        float left = OverhaulAnimations.values(clip, "leftKnee", time, false)[0];
        enable(model.rightLeg, right, 0, 16, 64, 0, false);
        enable(model.leftLeg, left, 16, 48, 64, 0, false);
        enable(model.rightPants, right, 0, 32, 64, 0.25F, false);
        enable(model.leftPants, left, 0, 48, 64, 0.25F, false);
    }

    public static void armor(HumanoidModel<?> parent, HumanoidModel<?> armor, float inflation) {
        copy(parent.rightLeg, armor.rightLeg, inflation, false);
        copy(parent.leftLeg, armor.leftLeg, inflation, true);
    }

    private static void copy(ModelPart source, ModelPart target, float inflation, boolean mirror) {
        Joint original = JOINTS.get(source);
        Joint prior = JOINTS.get(target);
        if (original != null && original.active) {
            enable(target, original.shin.xRot, 0, 16, 32, inflation, mirror);
            JOINTS.get(target).missing = !source.visible;
        } else if (prior != null) prior.active = false;
    }

    private static void enable(ModelPart part, float knee, int u, int v, int height, float inflation, boolean mirror) {
        Joint joint = JOINTS.computeIfAbsent(part, ignored -> new Joint(u, v, height, inflation, mirror));
        joint.active = true; joint.missing = false; joint.shin.xRot = knee;
    }

    public static boolean render(ModelPart original, PoseStack poses, VertexConsumer vertices, int light, int overlay, int color) {
        Joint joint = JOINTS.get(original);
        if (joint == null || !joint.active) return false;
        if (!original.visible || joint.missing) return true;
        if (original.skipDraw || Math.abs(joint.shin.xRot) < 0.002F) return false;
        poses.pushPose(); original.translateAndRotate(poses);
        joint.thigh.render(poses, vertices, light, overlay, color);
        joint.shin.render(poses, vertices, light, overlay, color);
        joint.seam(poses.last(), vertices, light, overlay, color);
        poses.popPose();
        return true;
    }

    public static void clear() { JOINTS.clear(); }

    private static final class Joint {
        final ModelPart thigh, shin;
        final int u, v, textureHeight;
        final float inflation;
        final boolean mirror;
        boolean active, missing;
        Joint(int u, int v, int textureHeight, float inflation, boolean mirror) {
            this.u = u; this.v = v; this.textureHeight = textureHeight;
            this.inflation = inflation; this.mirror = mirror;
            thigh = new ModelPart(List.of(cube(u, v, 0, 6, textureHeight, inflation, mirror,
                    EnumSet.complementOf(EnumSet.of(Direction.UP)))), Map.of());
            shin = new ModelPart(List.of(
                    cube(u, v + 6, 0, 6, textureHeight, inflation, mirror, EnumSet.complementOf(EnumSet.of(Direction.UP, Direction.DOWN))),
                    cube(u, v, -6, 12, textureHeight, inflation, mirror, EnumSet.of(Direction.UP))), Map.of());
            shin.setPos(0, 6, 0);
        }

        void seam(PoseStack.Pose pose, VertexConsumer vertices, int light, int overlay, int color) {
            float sin = Mth.sin(shin.xRot), cos = Mth.cos(shin.xRot), radius = 2 + inflation;
            float top = 6 + inflation, bottom = 6 - inflation * cos + radius * sin;
            if (sin <= 0 || bottom <= top) return;
            float z = -inflation * sin - radius * cos;
            float intersection = -inflation * (1 + cos) / sin;
            float row = v + 10, normalY = Mth.sin(shin.xRot / 2), normalZ = -Mth.cos(shin.xRot / 2);
            float rightU = u + (mirror ? 4 : 8), leftU = u + (mirror ? 8 : 4);
            vertex(vertices, pose, radius, top, -radius, rightU, row - 0.5F, normalY, normalZ, light, overlay, color, 0);
            vertex(vertices, pose, -radius, top, -radius, leftU, row - 0.5F, normalY, normalZ, light, overlay, color, 0);
            vertex(vertices, pose, -radius, bottom, z, leftU, row + 0.5F, normalY, normalZ, light, overlay, color, 0);
            vertex(vertices, pose, radius, bottom, z, rightU, row + 0.5F, normalY, normalZ, light, overlay, color, 0);
            for (int side = -1; side <= 1; side += 2) {
                float frontU = u + ((side < 0) != mirror ? 4 : 8);
                float innerU = frontU + ((side < 0) != mirror ? -1 : 1) * 4 * (intersection + radius) / (2 * radius);
                vertex(vertices, pose, side * radius, top, -radius, frontU, row - 0.5F, 0, 0, light, overlay, color, side);
                if (side < 0) vertex(vertices, pose, side * radius, top, intersection, innerU, row - 0.5F, 0, 0, light, overlay, color, side);
                vertex(vertices, pose, side * radius, bottom, z, frontU, row + 0.5F, 0, 0, light, overlay, color, side);
                if (side > 0) vertex(vertices, pose, side * radius, top, intersection, innerU, row - 0.5F, 0, 0, light, overlay, color, side);
                vertex(vertices, pose, side * radius, side < 0 ? bottom : top, side < 0 ? z : intersection,
                        side < 0 ? frontU : innerU, side < 0 ? row + 0.5F : row - 0.5F, 0, 0, light, overlay, color, side);
            }
        }

        void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z, float U, float V,
                    float normalY, float normalZ, int light, int overlay, int color, float normalX) {
            vertices.addVertex(pose, x / 16, y / 16, z / 16).setColor(color).setUv(U / 64, V / textureHeight)
                    .setOverlay(overlay).setLight(light).setNormal(pose, normalX, normalY, normalZ);
        }
    }

    private static ModelPart.Cube cube(int u, int v, float y, float height, int textureHeight,
                                       float inflation, boolean mirror, java.util.Set<Direction> faces) {
        return new ModelPart.Cube(u, v, -2, y, -2, 4, height, 4, inflation, inflation, inflation,
                mirror, 64, textureHeight, faces);
    }
}

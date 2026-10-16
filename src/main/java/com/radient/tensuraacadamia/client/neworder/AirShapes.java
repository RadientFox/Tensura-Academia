package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.WindChargeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

// effects for air moves :(
final class AirShapes {

    static final ResourceLocation WIND = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
    static final ResourceLocation CHARGE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static @Nullable WindChargeModel chargeModel;
    private static final float UV_PER_BLOCK = 0.5F;

    // wind charge's grey core
    private static final float CORE_U = 0.125F;
    private static final float CORE_V = 0.25F;

    // air's streaks
    static final int WIND_COLOR = FastColor.ARGB32.color(150, 225, 245, 255);
    static final int BODY_COLOR = FastColor.ARGB32.color(60, 215, 240, 255);
    static final int CORE_COLOR = FastColor.ARGB32.color(90, 205, 235, 255);

    private AirShapes() {
    }

    static RenderType wind(float time) {
        return RenderType.breezeWind(WIND, time * 0.02F % 1.0F, 0.0F);
    }

    static RenderType wind(float time, float speed, float shift) {
        return RenderType.breezeWind(WIND, time * speed % 1.0F, shift);
    }

    static RenderType charge(float time) {
        return RenderType.breezeWind(CHARGE, time * 0.03F % 1.0F, 0.0F);
    }

    // uses the wind charge
    // I like the vanilla feel
    static void charge(PoseStack poseStack, VertexConsumer consumer, Vec3 at, float size, float time, float alpha, int light) {
        if (chargeModel == null) {
            chargeModel = new WindChargeModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.WIND_CHARGE));
        }
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.scale(size, size, size);
        chargeModel.setupAnim(null, 0.0F, 0.0F, time, 0.0F, 0.0F);
        chargeModel.renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha))), 255, 255, 255));
        poseStack.popPose();
    }

    static RenderType core() {
        return RenderType.entityTranslucent(CHARGE);
    }

    static RenderType body() {
        return RenderType.debugQuads();
    }

    static int fade(int color, float alpha) {
        return FastColor.ARGB32.color(Math.round(FastColor.ARGB32.alpha(color) * Math.max(0.0F, Math.min(1.0F, alpha))), color);
    }

    static Vec3[] frame(Vec3 forward) {
        Vec3 side = forward.cross(UP);
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        return new Vec3[]{side, side.cross(forward).normalize()};
    }

    static void box(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 x, Vec3 y, Vec3 z, double halfX, double halfY, double halfZ, int color, int light) {
        Vec3 ax = x.scale(halfX);
        Vec3 ay = y.scale(halfY);
        Vec3 az = z.scale(halfZ);
        face(consumer, pose, center.add(ax), ay, az, x, color, light);
        face(consumer, pose, center.subtract(ax), az, ay, x.reverse(), color, light);
        face(consumer, pose, center.add(ay), az, ax, y, color, light);
        face(consumer, pose, center.subtract(ay), ax, az, y.reverse(), color, light);
        face(consumer, pose, center.add(az), ax, ay, z, color, light);
        face(consumer, pose, center.subtract(az), ay, ax, z.reverse(), color, light);
    }

    static void coreBox(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, Vec3 x, Vec3 y, Vec3 z, double halfX, double halfY, double halfZ, float u, float v, int color, int light) {
        Vec3 ax = x.scale(halfX);
        Vec3 ay = y.scale(halfY);
        Vec3 az = z.scale(halfZ);
        Vec3[][] faces = {{ax, ay, az, x}, {ax.reverse(), az, ay, x.reverse()}, {ay, az, ax, y}, {ay.reverse(), ax, az, y.reverse()}, {az, ax, ay, z}, {az.reverse(), ay, ax, z.reverse()}};
        for (int i = 0; i < faces.length; i++) {
            float shift = i * 0.37F;
            quad(consumer, pose, center.add(faces[i][0]), faces[i][1], faces[i][2], faces[i][3], (u + shift) % 0.75F, v, (u + shift) % 0.75F + CORE_U, v + CORE_V, color, light);
        }
    }

    static void tip(VertexConsumer consumer, PoseStack.Pose pose, Vec3 base, Vec3 tip, Vec3 side, Vec3 up, double half, int color, int light) {
        Vec3[] corners = {
                base.add(side.scale(half)).add(up.scale(half)), base.subtract(side.scale(half)).add(up.scale(half)),
                base.subtract(side.scale(half)).subtract(up.scale(half)), base.add(side.scale(half)).subtract(up.scale(half))
        };
        float length = (float) (base.distanceTo(tip) * UV_PER_BLOCK);
        float width = (float) (half * 2.0D * UV_PER_BLOCK);
        for (int i = 0; i < 4; i++) {
            Vec3 a = corners[i];
            Vec3 b = corners[(i + 1) % 4];
            Vec3 normal = b.subtract(a).cross(tip.subtract(a)).normalize();
            vertex(consumer, pose, a, 0.0F, 0.0F, normal, color, light);
            vertex(consumer, pose, b, width, 0.0F, normal, color, light);
            vertex(consumer, pose, tip, width * 0.5F, length, normal, color, light);
            vertex(consumer, pose, tip, width * 0.5F, length, normal, color, light);
        }
    }


    static void shell(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, double half, double halfY, Vec3 eye, float uPerBlock, float vPerBlock, float vShift, boolean core, int color, int light) {
        double side = half * 2.0D;
        double height = halfY * 2.0D;
        float us = (float) (side * uPerBlock);
        float vs = (float) (height * vPerBlock);
        Vec3[] normals = {new Vec3(0.0D, 0.0D, -1.0D), new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), new Vec3(-1.0D, 0.0D, 0.0D), UP, UP.reverse()};
        Integer[] order = {0, 1, 2, 3, 4, 5};
        Arrays.sort(order, Comparator.comparingDouble(i -> -center.add(normals[i].multiply(half, halfY, half)).distanceToSqr(eye)));
        for (int i : order) {
            Vec3 normal = normals[i];
            Vec3 middle = center.add(normal.multiply(half, halfY, half));
            if (i < 4) {
                Vec3 across = new Vec3(-normal.z, 0.0D, normal.x).scale(half);
                Vec3 up = UP.scale(halfY);
                float u0 = core ? i * 0.19F % 0.75F : i * us;
                float u1 = core ? u0 + CORE_U : u0 + us;
                float v1 = core ? CORE_V : vs;
                quad(consumer, pose, middle, across, up, normal, u0, vShift, u1, vShift + v1, color, light);
            } else {
                Vec3 across = new Vec3(half, 0.0D, 0.0D);
                Vec3 deep = new Vec3(0.0D, 0.0D, half);
                float u1 = core ? 0.5F + CORE_U : us;
                float v1 = core ? CORE_V : us;
                quad(consumer, pose, middle, across, deep, normal, core ? 0.5F : 0.0F, vShift, u1, vShift + v1, color, light);
            }
        }
    }

    static void edges(VertexConsumer consumer, PoseStack.Pose pose, Vec3 center, double half, double halfY, double thick, boolean core, int color, int light) {
        Vec3 x = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 y = UP;
        Vec3 z = new Vec3(0.0D, 0.0D, 1.0D);
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                Vec3[] middles = {center.add(0.0D, a * halfY, b * half), center.add(a * half, 0.0D, b * half), center.add(a * half, b * halfY, 0.0D)};
                double[][] sizes = {{half + thick, thick, thick}, {thick, halfY + thick, thick}, {thick, thick, half + thick}};
                for (int i = 0; i < 3; i++) {
                    if (core) {
                        coreBox(consumer, pose, middles[i], x, y, z, sizes[i][0], sizes[i][1], sizes[i][2], (a + b + i) * 0.21F + 0.3F, 0.0F, color, light);
                    } else {
                        box(consumer, pose, middles[i], x, y, z, sizes[i][0], sizes[i][1], sizes[i][2], color, light);
                    }
                }
            }
        }
    }

    static void quad(VertexConsumer consumer, PoseStack.Pose pose, Vec3 middle, Vec3 u, Vec3 v, Vec3 normal, float u0, float v0, float u1, float v1, int color, int light) {
        vertex(consumer, pose, middle.subtract(u).subtract(v), u0, v1, normal, color, light);
        vertex(consumer, pose, middle.add(u).subtract(v), u1, v1, normal, color, light);
        vertex(consumer, pose, middle.add(u).add(v), u1, v0, normal, color, light);
        vertex(consumer, pose, middle.subtract(u).add(v), u0, v0, normal, color, light);
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, Vec3 middle, Vec3 u, Vec3 v, Vec3 normal, int color, int light) {
        float us = (float) (u.length() * 2.0D * UV_PER_BLOCK);
        float vs = (float) (v.length() * 2.0D * UV_PER_BLOCK);
        vertex(consumer, pose, middle.subtract(u).subtract(v), 0.0F, 0.0F, normal, color, light);
        vertex(consumer, pose, middle.add(u).subtract(v), us, 0.0F, normal, color, light);
        vertex(consumer, pose, middle.add(u).add(v), us, vs, normal, color, light);
        vertex(consumer, pose, middle.subtract(u).add(v), 0.0F, vs, normal, color, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 at, float u, float v, Vec3 normal, int color, int light) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

}

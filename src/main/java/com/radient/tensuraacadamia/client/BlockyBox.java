package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class BlockyBox {
    public static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private static final Vec3 KEY = new Vec3(0.3D, 0.8D, 0.5D).normalize();

    private BlockyBox() {
    }

    public static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
    }

    public static VertexConsumer atlas(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
    }

    public static TextureAtlasSprite sprite(String id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.parse(id));
    }

    public static void textured(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, TextureAtlasSprite sprite, int rgb, int light) {
        Vec3[] axes = {ax, ay, az};
        float uSpan = sprite.getU1() - sprite.getU0();
        float vSpan = sprite.getV1() - sprite.getV0();
        for (int axis = 0; axis < 3; axis++) {
            Vec3 normal = axes[axis].normalize();
            Vec3 a = axes[(axis + 1) % 3];
            Vec3 b = axes[(axis + 2) % 3];
            float u1 = sprite.getU0() + uSpan * (float) Math.min(1.0D, a.length() * 2.0D);
            float v1 = sprite.getV0() + vSpan * (float) Math.min(1.0D, b.length() * 2.0D);
            float[][] uvs = {{sprite.getU0(), sprite.getV0()}, {u1, sprite.getV0()}, {u1, v1}, {sprite.getU0(), v1}};
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 n = normal.scale(sign);
                Vec3 center = c.add(axes[axis].scale(sign));
                float shade = (float) (0.65D + 0.35D * Math.max(0.0D, n.dot(KEY)));
                int r = (int) (((rgb >> 16) & 0xFF) * shade);
                int g = (int) (((rgb >> 8) & 0xFF) * shade);
                int bl = (int) ((rgb & 0xFF) * shade);
                Vec3[] corners = {center.subtract(a).subtract(b), center.add(a).subtract(b), center.add(a).add(b), center.subtract(a).add(b)};
                for (int i = 0; i < 4; i++) {
                    Vec3 corner = corners[sign > 0 ? i : 3 - i];
                    float[] uv = uvs[sign > 0 ? i : 3 - i];
                    buffer.addVertex(pose.pose(), (float) corner.x, (float) corner.y, (float) corner.z)
                            .setColor(r, g, bl, 255).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
                }
            }
        }
    }

    public static void box(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, int rgb, int light) {
        Vec3[] axes = {ax, ay, az};
        for (int axis = 0; axis < 3; axis++) {
            Vec3 normal = axes[axis].normalize();
            Vec3 a = axes[(axis + 1) % 3];
            Vec3 b = axes[(axis + 2) % 3];
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 n = normal.scale(sign);
                Vec3 center = c.add(axes[axis].scale(sign));
                float shade = (float) (0.55D + 0.45D * Math.max(0.0D, n.dot(KEY)));
                int r = (int) (((rgb >> 16) & 0xFF) * shade);
                int g = (int) (((rgb >> 8) & 0xFF) * shade);
                int bl = (int) ((rgb & 0xFF) * shade);
                Vec3[] corners = sign > 0
                        ? new Vec3[]{center.subtract(a).subtract(b), center.add(a).subtract(b), center.add(a).add(b), center.subtract(a).add(b)}
                        : new Vec3[]{center.subtract(a).subtract(b), center.subtract(a).add(b), center.add(a).add(b), center.add(a).subtract(b)};
                for (Vec3 corner : corners) {
                    buffer.addVertex(pose.pose(), (float) corner.x, (float) corner.y, (float) corner.z)
                            .setColor(r, g, bl, 255).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                            .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
                }
            }
        }
    }
}

package com.radient.tensuraacadamia.quirks.elasticity;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class ElasticityRenderer {
    private static final Map<UUID, Surface> SURFACES = new HashMap<>();
    private static ClientLevel currentLevel;

    private record Dent(Vec3 point, float strength, long time) { }

    private static final class Surface {
        ElasticitySurfacePayload state;
        final long appearedAt;
        final List<Dent> dents = new ArrayList<>();

        Surface(ElasticitySurfacePayload state, long now) {
            this.state = state;
            appearedAt = now;
        }
    }

    public static void receive(ElasticitySurfacePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        changeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimension())) return;
        long now = level.getGameTime();
        if (payload.expiresAt() <= now) return;
        Surface surface = SURFACES.get(payload.id());
        if (surface == null || surface.state.expiresAt() != payload.expiresAt()) {
            surface = new Surface(payload, now);
            SURFACES.put(payload.id(), surface);
        }
        surface.state = payload;
        if (payload.strength() != 0) {
            if (surface.dents.size() >= 8) surface.dents.removeFirst();
            Vec3 right = payload.right().normalize();
            Vec3 up = payload.normal().cross(right).normalize();
            Vec3 contact = payload.impact().subtract(payload.center());
            Vec3 planar = right.scale(contact.dot(right)).add(up.scale(contact.dot(up)));
            double reach = payload.radius() * .8;
            if (planar.lengthSqr() > reach * reach) planar = planar.normalize().scale(reach);
            surface.dents.add(new Dent(planar, payload.strength(), now));
        }
        // limit cached surfaces during crowded fights
        if (SURFACES.size() > 256) {
            UUID oldest = SURFACES.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().appearedAt))
                    .orElseThrow().getKey();
            SURFACES.remove(oldest);
        }
    }

    private static void changeLevel(ClientLevel level) {
        if (currentLevel != level) {
            SURFACES.clear();
            currentLevel = level;
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        changeLevel(level);
        if (level == null) return;
        long now = level.getGameTime();
        SURFACES.values().removeIf(surface -> now >= surface.state.expiresAt());
        SURFACES.values().forEach(surface -> surface.dents.removeIf(dent -> now - dent.time() >= 24));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        changeLevel(minecraft.level);
        if (minecraft.level == null || SURFACES.isEmpty()) return;
        double now = minecraft.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        MultiBufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Surface surface : SURFACES.values()) {
            ElasticitySurfacePayload state = surface.state;
            int detail = SkillVfx.detail(camera, state.center());
            if (detail == 0 || now >= state.expiresAt()) continue;
            float fade = (float) Math.min(1, Math.max(0, (state.expiresAt() - now) / 8));
            float appear = (float) Math.min(1, Math.max(0, (now - surface.appearedAt) / 4));
            double radius = state.radius() * (.8 + .2 * Math.sin(appear * Math.PI / 2));
            Vec3 right = state.right().normalize();
            Vec3 normal = state.normal().normalize();
            Vec3 up = normal.cross(right).normalize();
            poses.pushPose();
            Vec3 origin = state.center().subtract(camera);
            poses.translate(origin.x, origin.y, origin.z);
            for (int layer = 0; layer < state.layers(); layer++) {
                Vec3 offset = normal.scale(layer * .16);
                if (state.sandwich()) {
                    double progress = Math.clamp(1 - (state.expiresAt() - 12 - now) / 15, 0, 1);
                    double fall = progress < .45 ? .15 * Math.pow(progress / .45, 2)
                            : .15 + .85 * Math.pow((progress - .45) / .55, .55);
                    offset = new Vec3(0, (7 - layer * 2) * (1 - fall) + .12 * layer, 0);
                }
                membrane(poses, buffers, camera.subtract(state.center()), surface, right, up, normal,
                        offset, radius, now, fade * appear, detail);
            }
            poses.popPose();
        }
    }

    private static void membrane(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Surface surface,
                                 Vec3 right, Vec3 up, Vec3 normal, Vec3 offset, double radius,
                                 double now, float opacity, int detail) {
        int segments = detail >= 20 ? 48 : 24;
        int rings = detail >= 20 ? 12 : 6;
        Vec3[][] front = new Vec3[rings + 1][segments + 1];
        Vec3[][] back = new Vec3[rings + 1][segments + 1];
        for (int ring = 0; ring <= rings; ring++) {
            double fraction = ring / (double) rings;
            for (int segment = 0; segment <= segments; segment++) {
                double angle = segment * Math.PI * 2 / segments;
                double x = Math.cos(angle) * fraction * radius;
                double y = Math.sin(angle) * fraction * radius;
                double depth = displacement(surface, right, up, x, y, fraction, now);
                double thickness = .045 + .13 * Math.sqrt(Math.max(0, 1 - fraction * fraction));
                Vec3 point = offset.add(right.scale(x)).add(up.scale(y)).add(normal.scale(depth));
                front[ring][segment] = point.add(normal.scale(thickness));
                back[ring][segment] = point.subtract(normal.scale(thickness));
            }
        }

        // draw both faces and the rim so the surface has thickness
        VertexConsumer vertices = buffers.getBuffer(SkillVfx.HAZE);
        for (int ring = 0; ring < rings; ring++) {
            float shade = (float) ring / rings;
            float alpha = opacity * (.12F + .12F * shade * shade);
            int color = ring % 3 == 0 ? 0xcce7ec : 0xb3d3df;
            for (int segment = 0; segment < segments; segment++) {
                quad(vertices, poses, front[ring][segment], front[ring + 1][segment],
                        front[ring + 1][segment + 1], front[ring][segment + 1], color, alpha);
                quad(vertices, poses, back[ring][segment + 1], back[ring + 1][segment + 1],
                        back[ring + 1][segment], back[ring][segment], color, alpha * .65F);
            }
        }
        for (int segment = 0; segment < segments; segment++) {
            quad(vertices, poses, front[rings][segment], back[rings][segment],
                    back[rings][segment + 1], front[rings][segment + 1], 0xd5edf0, opacity * .45F);
        }

        // highlights stretch with the surface
        for (int segment = 0; segment < segments; segment++) {
            SkillVfx.ribbon(poses, buffers, camera, front[rings][segment], front[rings][segment + 1],
                    .055, 0xe3f8ff, opacity * .4F, true);
        }
        for (int band : new int[]{rings / 3, rings * 2 / 3}) {
            for (int segment = segments / 12; segment < segments / 3; segment++) {
                SkillVfx.ribbon(poses, buffers, camera, front[band][segment], front[band][segment + 1],
                        .07, 0xf1fbff, opacity * .22F, true);
                int opposite = segment + segments / 2;
                SkillVfx.ribbon(poses, buffers, camera, back[band][opposite], back[band][opposite + 1],
                        .04, 0xd4efff, opacity * .15F, true);
            }
        }
        if (!surface.dents.isEmpty()) {
            for (int spoke = 0; spoke < 6; spoke++) {
                int segment = spoke * segments / 6;
                for (int ring = 1; ring < rings - 1; ring++) {
                    double bend = front[ring][segment].subtract(offset).dot(normal);
                    float alpha = (float) Math.min(.35, Math.abs(bend) * .45) * opacity;
                    SkillVfx.ribbon(poses, buffers, camera, front[ring][segment], front[ring + 1][segment],
                            .035, 0xe5faff, alpha, true);
                }
            }
        }
    }

    private static double displacement(Surface surface, Vec3 right, Vec3 up,
                                       double x, double y, double fraction, double now) {
        double displacement = .025 * Math.sin(now * .08 + x * .7 + y * .6);
        for (Dent dent : surface.dents) {
            double age = now - dent.time();
            double hitX = dent.point().dot(right), hitY = dent.point().dot(up);
            double distance = Math.hypot(x - hitX, y - hitY);
            displacement += ElasticityDeformation.dent(distance, age, dent.strength());
        }
        return ElasticityDeformation.anchored(displacement, fraction);
    }

    private static void quad(VertexConsumer vertices, PoseStack poses, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int color, float alpha) {
        int opacity = (int) (255 * Math.clamp(alpha, 0, 1));
        vertex(vertices, poses, a, color, opacity);
        vertex(vertices, poses, b, color, opacity);
        vertex(vertices, poses, c, color, opacity);
        vertex(vertices, poses, d, color, opacity);
    }

    private static void vertex(VertexConsumer vertices, PoseStack poses, Vec3 point, int color, int opacity) {
        vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, opacity);
    }

    private ElasticityRenderer() { }
}

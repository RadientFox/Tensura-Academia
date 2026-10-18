package com.radient.tensuraacadamia.quirks.barrier;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DomeBarrierRenderer extends EntityRenderer<DomeBarrier> {
    public DomeBarrierRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(BarrierEntities.DOME.get(), DomeBarrierRenderer::new); }
    @Override public ResourceLocation getTextureLocation(DomeBarrier dome) { return ResourceLocation.withDefaultNamespace("textures/misc/white.png"); }
    @Override public void render(DomeBarrier dome, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(dome.position());
        double radius = dome.radius(), height = dome.size(), spin = dome.mastered() ? SkillVfx.clock(dome.level().getGameTime(), partial) * .006 : 0;
        int detail = SkillVfx.detail(camera, new net.minecraft.world.phys.AABB(-radius,0,-radius,radius,height,radius));
        if (detail == 0) return;
        var vertices = buffers.getBuffer(SkillVfx.HAZE);
        int slices = detail >= 12 ? 32 : 20, rings = 10;
        float appear = Math.min(1, (dome.tickCount + partial) / 6);
        for (int row = 0; row < rings; row++) for (int column = 0; column < slices; column++) {
            double a = column * Math.PI * 2 / slices + spin, b = (column + 1) * Math.PI * 2 / slices + spin;
            double p = row * Math.PI / (rings * 2), q = (row + 1) * Math.PI / (rings * 2);
            quad(poses, vertices, surface(radius, height, p, a), surface(radius, height, p, b),
                    surface(radius, height, q, b), surface(radius, height, q, a), .035F * appear);
        }
        for (int column = 0; column < slices; column++)
            quad(poses, vertices, Vec3.ZERO, surface(radius, height, 0, column * Math.PI * 2 / slices),
                    surface(radius, height, 0, (column + 1) * Math.PI * 2 / slices), Vec3.ZERO, .035F * appear);
        SkillVfx.ring(poses, buffers, camera, Vec3.ZERO, new Vec3(0, 1, 0), radius, .035, 0x99DCFF, .25F * appear, spin);
        // keep the dome visible without blocking the players view
        for (int line = 0; line < 8; line++) for (int step = 0; step < 12; step++) {
            double a = line * Math.PI / 4 + spin, p = step * Math.PI / 24, q = (step + 1) * Math.PI / 24;
            SkillVfx.ribbon(poses, buffers, camera, surface(radius, height, p, a), surface(radius, height, q, a), .012, 0x94D5FF, .07F * appear, true);
        }
        for (var impact : dome.impacts()) {
            float time = dome.level().getGameTime() - impact.at() + partial;
            if (time < 0 || time > 22) continue;
            Vec3 normal = new Vec3(impact.point().x / radius, Math.max(0, impact.point().y) / height, impact.point().z / radius).normalize();
            if (normal.lengthSqr() < .01) normal = new Vec3(0, 1, 0);
            Vec3 right = SkillVfx.perpendicular(normal), up = normal.cross(right);
            for (int wave = 0; wave < 2; wave++) {
                float waveTime = time - wave * 3; if (waveTime < 0 || waveTime > 16) continue;
                double angle = Math.min(1.2, (.1 + waveTime * .075) / Math.sqrt(radius));
                float alpha = (1 - waveTime / 16) * .7F;
                Vec3 previous = ripple(normal, right, up, radius, height, angle, 0);
                for (int step = 1; step <= 32; step++) {
                    Vec3 next = ripple(normal, right, up, radius, height, angle, step * Math.PI / 16);
                    if (previous.y >= 0 && next.y >= 0) SkillVfx.ribbon(poses, buffers, camera, previous, next,
                            wave == 0 ? .045 : .025, wave == 0 ? 0xF0FCFF : 0x7ACBEE, alpha, true);
                    previous = next;
                }
            }
        }
    }
    private static Vec3 surface(double radius, double height, double latitude, double longitude) {
        return new Vec3(Math.cos(latitude) * Math.cos(longitude) * radius, Math.sin(latitude) * height, Math.cos(latitude) * Math.sin(longitude) * radius);
    }
    private static Vec3 ripple(Vec3 normal, Vec3 right, Vec3 up, double radius, double height, double angle, double phase) {
        Vec3 unit = normal.scale(Math.cos(angle)).add(right.scale(Math.sin(angle) * Math.cos(phase))).add(up.scale(Math.sin(angle) * Math.sin(phase)));
        return unit.multiply(radius + .025, height + .025, radius + .025);
    }
    private static void quad(PoseStack poses, VertexConsumer vertices, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        for (Vec3 point : new Vec3[]{a, b, c, d}) vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z).setColor(.38F, .7F, 1, alpha);
    }
}

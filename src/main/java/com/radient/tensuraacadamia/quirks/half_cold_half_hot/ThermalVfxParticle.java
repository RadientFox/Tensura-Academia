package com.radient.tensuraacadamia.quirks.half_cold_half_hot;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ThermalVfxParticle extends Particle {
    public enum Kind { FIRE_STREAM, FIRE_SHELL, FROST_BURST, PALE_BLADE, FROST_FRONT }
    private final Kind kind;
    private final Vec3 vector;
    private final double radius;

    private ThermalVfxParticle(ClientLevel level, double x, double y, double z,
                               double first, double second, double third, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        vector = new Vec3(first, second, third);
        radius = Math.clamp(first, .1, 32);
        lifetime = kind == Kind.FROST_BURST ? 12 : kind == Kind.PALE_BLADE ? 5 : 3;
        hasPhysics = false;
        xd = yd = zd = 0;
    }

    @Override public AABB getRenderBoundingBox(float partialTick) {
        Vec3 position = new Vec3(x, y, z);
        return kind == Kind.FIRE_STREAM ? new AABB(position, position.add(vector)).inflate(2)
                : AABB.ofSize(position, Math.max(12, radius * 3), Math.max(12, radius * 3), Math.max(12, radius * 3));
    }

    @Override public void render(VertexConsumer ignored, Camera camera, float partialTick) {
        Vec3 position = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z));
        Vec3 view = camera.getPosition().subtract(position);
        PoseStack poses = new PoseStack();
        Vec3 relative = position.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float time = age + partialTick;
        float fade = Math.max(0, 1 - time / lifetime);
        double clock = SkillVfx.clock(level.getGameTime(), partialTick);
        switch (kind) {
            case FIRE_STREAM -> {
                if (vector.lengthSqr() < .01) return;
                Vec3 forward = vector.normalize(), right = SkillVfx.perpendicular(forward), up = right.cross(forward);
                SkillVfx.tube(poses, buffers, view, Vec3.ZERO, vector, .09, 0xFFE295, fade * .3F);
                for (int strand = 0; strand < 2; strand++) {
                    Vec3 previous = Vec3.ZERO;
                    for (int segment = 1; segment <= 12; segment++) {
                        double progress = segment / 12.0;
                        double lift = Math.sin(progress * Math.PI) * (.25 + strand * .2);
                        Vec3 point = vector.scale(progress).add(right.scale(Math.sin(progress * 12 - clock * .5 + strand * 2) * lift))
                                .add(up.scale(lift));
                        SkillVfx.ribbon(poses, buffers, view, previous, point, .4 * (1 - progress * .85),
                                strand == 0 ? 0xFFCF53 : 0xFF6415, fade * .65F, true);
                        previous = point;
                    }
                }
                for (int tongue = 0; tongue < 5; tongue++) {
                    double progress = (clock * .06 + tongue / 5.0) % 1;
                    Vec3 center = vector.scale(progress).add(up.scale(Math.sin(progress * Math.PI) * .3));
                    SkillVfx.billow(poses, buffers, view, center, .4 * (1 - progress * .5), .25,
                            0xF96916, fade * .3F, clock * .015 + tongue);
                }
            }
            case FIRE_SHELL -> {
                if (SkillVfx.detail(view, Vec3.ZERO) == 0) return;
                var vertices = buffers.getBuffer(SkillVfx.SHINE);
                float visible = view.length() < radius ? .2F : 1;
                for (int ring = 0; ring < 8; ring++) for (int segment = 0; segment < 20; segment++) {
                    double a = ring * Math.PI / 8, b = (ring + 1) * Math.PI / 8;
                    double p = segment * Math.PI / 10, q = (segment + 1) * Math.PI / 10;
                    for (Vec3 point : new Vec3[]{fireSurface(a, p, clock), fireSurface(a, q, clock), fireSurface(b, q, clock), fireSurface(b, p, clock)})
                        vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                                .setColor(1, ring % 2 == 0 ? .35F : .58F, .055F, fade * .055F * visible);
                }
                for (int flame = 0; flame < 16; flame++) {
                    double angle = flame * Math.PI / 8, progress = (clock * .05 + flame * .37) % 1;
                    Vec3 start = new Vec3(Math.cos(angle) * radius * .8, -radius * .4, Math.sin(angle) * radius * .8);
                    Vec3 end = start.scale(1 - progress * .25).add(0, radius * (.25 + progress * .9), 0);
                    SkillVfx.ribbon(poses, buffers, view, start.lerp(end, .3), end, radius * .055 * (1 - progress),
                            flame % 3 == 0 ? 0xFFE68B : 0xFF6A19, fade * .6F * visible, true);
                }
            }
            case PALE_BLADE -> {
                Vec3 forward = vector.lengthSqr() < .01 ? new Vec3(0, 0, 1) : vector.normalize();
                Vec3 right = SkillVfx.perpendicular(forward), up = right.cross(forward);
                SkillVfx.crescent(poses, buffers, view, Vec3.ZERO, right.scale(2.2), up,
                        2.5, .45, 0x85D8F5, fade * .85F, 0);
            }
            case FROST_BURST, FROST_FRONT -> {
                double size = kind == Kind.FROST_FRONT ? radius : radius * (.25 + Math.min(1, time / 5) * .75);
                SkillVfx.ring(poses, buffers, view, new Vec3(0, .15, 0), new Vec3(0, 1, 0), size, .12, 0x9CE8FF, fade * .65F, 0);
                for (int shard = 0; shard < 12; shard++) {
                    double angle = shard * Math.PI / 6;
                    Vec3 base = new Vec3(Math.cos(angle) * size, 0, Math.sin(angle) * size);
                    Vec3 tip = base.add(Math.cos(angle) * .25, .4 + shard % 3 * .25, Math.sin(angle) * .25);
                    iceShard(poses, buffers, base, tip, .18, fade);
                    SkillVfx.ribbon(poses, buffers, view, base, tip, .045, 0xEDFDFF, fade * .8F, true);
                }
            }
        }
    }

    private static void iceShard(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
                                 Vec3 base, Vec3 tip, double width, float alpha) {
        Vec3 axis = tip.subtract(base).normalize(), right = SkillVfx.perpendicular(axis), up = axis.cross(right);
        var vertices = buffers.getBuffer(SkillVfx.HAZE);
        for (int side = 0; side < 4; side++) {
            double a = side * Math.PI / 2, b = (side + 1) * Math.PI / 2;
            Vec3 p = base.add(right.scale(Math.cos(a) * width)).add(up.scale(Math.sin(a) * width));
            Vec3 q = base.add(right.scale(Math.cos(b) * width)).add(up.scale(Math.sin(b) * width));
            for (Vec3 point : new Vec3[]{p, q, tip, tip}) vertices.addVertex(poses.last().pose(),
                    (float) point.x, (float) point.y, (float) point.z).setColor(.38F + side * .1F, .72F + side * .06F, 1, alpha * .7F);
        }
    }

    private Vec3 fireSurface(double latitude, double longitude, double clock) {
        double billow = 1 + Math.sin(longitude * 6 + clock * .4 + latitude * 4) * .045;
        return new Vec3(Math.sin(latitude) * Math.cos(longitude) * radius * billow,
                Math.cos(latitude) * radius * .67, Math.sin(latitude) * Math.sin(longitude) * radius * billow);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }

    public record Provider(Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double first, double second, double third) {
            return new ThermalVfxParticle(level, x, y, z, first, second, third, kind);
        }
    }
}

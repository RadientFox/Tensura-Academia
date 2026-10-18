package com.radient.tensuraacadamia.quirks.shared;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class EyeBeamParticle extends Particle {
    private static final int LIFETIME_TICKS = 2;

    private final Vec3 direction;
    private final double visibleLength;
    private final double beamWidth;
    private final int beamColor;

    private EyeBeamParticle(ClientLevel level, double x, double y, double z,
                            double directionX, double directionY, double directionZ,
                            double beamLength, double beamWidth, int beamColor) {
        super(level, x, y, z);
        Vec3 suppliedDirection = new Vec3(directionX, directionY, directionZ);
        double lengthFraction = suppliedDirection.length();
        this.direction = suppliedDirection.lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, 0.0D, 1.0D) : suppliedDirection.normalize();

        this.beamWidth = beamWidth;
        this.beamColor = beamColor;
        this.visibleLength = beamLength * lengthFraction;

        this.lifetime = LIFETIME_TICKS;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
    }

    @Override public AABB getRenderBoundingBox(float partialTick) {
        Vec3 origin = new Vec3(x, y, z);
        return new AABB(origin, origin.add(direction.scale(visibleLength))).inflate(beamWidth);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (this.visibleLength <= 0.05D) {
            return;
        }

        Vec3 origin = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z));
        Vec3 cam = camera.getPosition().subtract(origin);
        PoseStack poses = new PoseStack(); Vec3 relative = origin.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        double time = SkillVfx.clock(level.getGameTime(), partialTick);
        renderBeam(poses, buffers, cam, direction, visibleLength, beamWidth, beamColor & 0xFFFFFF, time);
    }

    static void renderBeam(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                           Vec3 direction, double visibleLength, double beamWidth, int rgb, double time) {
        Vec3 end = direction.scale(visibleLength), right = SkillVfx.perpendicular(direction), up = direction.cross(right);
        float visibility = cam.distanceTo(SkillVfx.closest(cam, Vec3.ZERO, end)) < beamWidth * .5 ? .3F : 1;
        SkillVfx.tube(poses, buffers, cam, Vec3.ZERO, end, beamWidth * .65, rgb, .045f * visibility);
        SkillVfx.tube(poses, buffers, cam, Vec3.ZERO, end, beamWidth * .5, rgb, .2f * visibility);
        SkillVfx.tube(poses, buffers, cam, Vec3.ZERO, end, beamWidth * .115, rgb, .9f * visibility);
        int segments = Math.clamp((int) (visibleLength / 5), 8, cam.lengthSqr() > 4096 ? 16 : 40);
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double k = (double) i / segments, angle = time * .7 + k * Math.PI * 8;
            double radius = beamWidth * .43 * Math.sin(Math.PI * k);
            Vec3 point = end.scale(k).add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
            if (previous != null) SkillVfx.ribbon(poses, buffers, cam, previous, point, Math.max(.025, beamWidth * .045), rgb, .6f * visibility, true);
            previous = point;
        }
        for (int i = 0; i < 3; i++) {
            double k = (time * .035 + i / 3.0) % 1;
            SkillVfx.ring(poses, buffers, cam, end.scale(k), direction, beamWidth * .45,
                    Math.max(.018, beamWidth * .025), rgb, .45f * visibility, time * .2);
        }
        SkillVfx.ring(poses, buffers, cam, end, direction, Math.max(.12, beamWidth * .5),
                Math.max(.025, beamWidth * .06), rgb, .7F * visibility, time * .1);
        SkillVfx.disc(poses, buffers, cam, end, Math.max(.18, beamWidth * .6), rgb, .65f * visibility, true);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    public record Provider(SpriteSet sprites, double beamLength, double beamWidth, int beamColor)
            implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double directionX, double directionY, double directionZ) {
            return new EyeBeamParticle(level, x, y, z, directionX, directionY, directionZ,
                    this.beamLength, this.beamWidth, this.beamColor);
        }
    }
}

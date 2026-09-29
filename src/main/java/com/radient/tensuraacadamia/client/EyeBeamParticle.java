package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EyeBeamParticle extends Particle {
    private static final int LIFETIME_TICKS = 2;
    private static final float ROTATION_PER_TICK = 2.5F;

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
        this.setSize((float) (visibleLength * 2.0D + this.beamWidth),
                (float) (visibleLength * 2.0D + this.beamWidth));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (this.visibleLength <= 0.05D) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        Vec3 origin = new Vec3(Mth.lerp(partialTick, this.xo, this.x),
                Mth.lerp(partialTick, this.yo, this.y), Mth.lerp(partialTick, this.zo, this.z));
        PoseStack poseStack = new PoseStack();
        Vec3 relativeOrigin = origin.subtract(cameraPosition);
        poseStack.translate(relativeOrigin.x, relativeOrigin.y, relativeOrigin.z);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f((float) this.direction.x, (float) this.direction.y, (float) this.direction.z)));
        float rotation = (float) ((this.level.getGameTime() * (double) ROTATION_PER_TICK) % (Math.PI * 2.0D));
        poseStack.mulPose(Axis.YP.rotation(rotation + partialTick * ROTATION_PER_TICK));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        int height = Math.max(1, Mth.ceil((float) this.visibleLength));
        poseStack.scale(1.0F, (float) (this.visibleLength / height), 1.0F);
        BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION,
                partialTick, 1.0F, this.level.getGameTime(), 0, height, beamColor,
                (float) (this.beamWidth * 0.5D), (float) (this.beamWidth * 0.55D));
        buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, false));
        buffers.endBatch(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
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

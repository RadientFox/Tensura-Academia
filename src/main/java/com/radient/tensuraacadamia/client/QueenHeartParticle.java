package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class QueenHeartParticle extends Particle {
    private static final String[] HEART = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};

    private QueenHeartParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        lifetime = 10;
        hasPhysics = false;
        xd = yd = zd = 0;
    }

    @Override
    public void render(VertexConsumer unused, Camera camera, float partialTick) {
        Vec3 origin = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y),
                Mth.lerp(partialTick, zo, z)).subtract(camera.getPosition());
        PoseStack poses = new PoseStack();
        poses.translate(origin.x, origin.y, origin.z);
        poses.mulPose(camera.rotation());
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        var matrix = poses.last().pose();
        int alpha = (int) (230 * (1.0F - (age + partialTick) / lifetime));
        float pixel = 0.055F;
        for (int row = 0; row < HEART.length; row++) {
            for (int col = 0; col < HEART[row].length(); col++) {
                if (HEART[row].charAt(col) != '1') continue;
                float left = (col - 3.5F) * pixel, top = (3.0F - row) * pixel;
                buffer.addVertex(matrix, left, top - pixel, 0).setColor(255, 75, 165, alpha);
                buffer.addVertex(matrix, left + pixel, top - pixel, 0).setColor(255, 75, 165, alpha);
                buffer.addVertex(matrix, left + pixel, top, 0).setColor(255, 75, 165, alpha);
                buffer.addVertex(matrix, left, top, 0).setColor(255, 75, 165, alpha);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new QueenHeartParticle(level, x, y, z);
        }
    }
}

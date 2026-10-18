package com.radient.tensuraacadamia.quirks.assault_dust;

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

public final class DustWaveParticle extends Particle {
    private final double width;
    private final double height;
    private final Vec3 right;
    private final Vec3 forward;

    private DustWaveParticle(ClientLevel level, double x, double y, double z, double width, double height, double angle) {
        super(level, x, y, z);
        this.width = Math.clamp(width, 1, 100);
        this.height = Math.clamp(height, 1, 50);
        right = new Vec3(Math.cos(angle), 0, -Math.sin(angle));
        forward = new Vec3(Math.sin(angle), 0, Math.cos(angle));
        lifetime = 4;
        hasPhysics = false;
        xd = yd = zd = 0;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        return new AABB(x - width, y - 2, z - width, x + width, y + height + 5, z + width);
    }

    @Override
    public void render(VertexConsumer ignored, Camera camera, float partialTick) {
        Vec3 position = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z));
        Vec3 view = camera.getPosition().subtract(position);
        int detail = SkillVfx.detail(view, new AABB(-width / 2, 0, -width / 2, width / 2, height, width / 2));
        if (detail == 0) return;
        PoseStack poses = new PoseStack();
        Vec3 relative = position.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float fade = Math.max(0, 1 - (age + partialTick) / lifetime);
        double clock = SkillVfx.clock(level.getGameTime(), partialTick);
        int columns = detail > 8 ? 12 : 8;
        for (int column = 0; column < columns; column++) {
            double side = width * ((column + .5) / columns - .5);
            double crest = height * (.92 + Math.sin(column * 1.7 + clock * .07) * .08);
            for (int row = 0; row < 5; row++) {
                double fraction = (row + .5) / 5;
                Vec3 point = right.scale(side).add(0, fraction * crest, 0)
                        .add(forward.scale(Math.sin(fraction * Math.PI) * 3 + Math.sin(clock * .08 + column) * .25));
                double size = Math.max(width / columns * .75, height / 10);
                SkillVfx.billow(poses, buffers, view, point, size, size * 1.2,
                        row < 2 ? 0x715944 : 0xC5AE89, fade * .55F, Math.sin(column + row + clock * .02) * .3);
            }
            Vec3 grit = right.scale(side).add(forward.scale(-1 - (clock * .09 + column * .3) % 2)).add(0, .2, 0);
            SkillVfx.ribbon(poses, buffers, view, grit, grit.add(0, .1, 0).subtract(forward.scale(.45)),
                    .045, 0x9C805B, fade * .8F, false);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double width, double height, double angle) {
            return new DustWaveParticle(level, x, y, z, width, height, angle);
        }
    }
}

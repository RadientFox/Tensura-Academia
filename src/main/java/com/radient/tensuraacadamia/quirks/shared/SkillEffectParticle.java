package com.radient.tensuraacadamia.quirks.shared;

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
import net.minecraft.world.phys.Vec3;

public final class SkillEffectParticle extends Particle {
    public static final int PORTAL = 1, CLASH = 2, BLAST = 3, CHARGE = 4;
    private final int form;
    private final float opacity, size;
    private final double phase;
    private SkillEffectParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int form, float opacity) {
        super(level, x, y, z); this.form = form; this.opacity = opacity; phase = random.nextDouble() * Math.PI * 2;
        lifetime = form == PORTAL ? 12 : form == CHARGE ? 7 : 10;
        size = form == PORTAL ? .45f
                : form == BLAST || form == CLASH ? (float) Math.clamp(vx, .2, 5) : .65f;
        hasPhysics = false; gravity = 0; xd = form < CLASH ? vx : 0; yd = form < CLASH ? vy : 0; zd = form < CLASH ? vz : 0;
        setSize(size * 3, size * 3);
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        Vec3 origin = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z));
        Vec3 cam = camera.getPosition().subtract(origin); if (cam.lengthSqr() > 9216) return;
        float time = age + partial, fade = Math.max(0, 1 - time / lifetime);
        var poses = new PoseStack(); Vec3 relative = origin.subtract(camera.getPosition()); poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (form == PORTAL) {
            float alpha = opacity * Math.min(1, time / 2) * Math.min(1, fade * 4);
            SkillVfx.disc(poses, buffers, cam, Vec3.ZERO, size * (1 + time * .008), 0x09040f, alpha * .8f, false);
            int lobes = cam.lengthSqr() > 1024 ? 1 : 2;
            for (int i = 0; i < lobes; i++) {
                double a = phase + i * 2.4 + time * .04;
                Vec3 p = new Vec3(Math.cos(a) * size * .35, Math.sin(a * 1.7) * size * .25, Math.sin(a) * size * .2);
                SkillVfx.disc(poses, buffers, cam, p, size * .7, 0x210d30, alpha * .35f, false);
            }
        } else if (form == CLASH) {
            SkillVfx.burst(poses, buffers, cam, Vec3.ZERO, time, size * 1.7, 0xffdb80, 41);
            SkillVfx.ring(poses, buffers, cam, Vec3.ZERO, new Vec3(0, 1, .4), .5 + time * .09, .06, 0xff3535, fade, time * .2);
        } else if (form == BLAST) SkillVfx.burst(poses, buffers, cam, Vec3.ZERO, time, size, 0xffd72e, 17);
        else {
            SkillVfx.disc(poses, buffers, cam, Vec3.ZERO, .45, 0xffd72e, fade * .6f, true);
            for (int i = 0; i < 6; i++) {
                double a = phase + i * Math.PI / 3 + time * .2;
                Vec3 edge = new Vec3(Math.cos(a), Math.sin(a), Math.sin(a * 2) * .4).scale(fade);
                SkillVfx.ribbon(poses, buffers, cam, edge, edge.scale(.25), .035, 0xffe781, fade, true);
            }
        }
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    public record Provider(int form, float opacity) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SkillEffectParticle(level, x, y, z, vx, vy, vz, form, opacity);
        }
    }
}

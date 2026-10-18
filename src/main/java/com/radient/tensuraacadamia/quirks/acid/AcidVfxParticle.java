package com.radient.tensuraacadamia.quirks.acid;

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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.world.phys.AABB;

public final class AcidVfxParticle extends Particle {
    private final boolean charge;
    private final double size, phase;
    private final int ownerId;

    private AcidVfxParticle(ClientLevel level, double x, double y, double z, double size, int ownerId, boolean charge) {
        super(level, x, y, z);
        this.charge = charge;
        this.size = Math.clamp(size, .1, 3);
        this.ownerId = ownerId;
        phase = random.nextDouble() * Math.PI * 2;
        lifetime = charge ? 7 : 16;
        hasPhysics = false;
        xd = yd = zd = 0;
        setSize((float) this.size * 5, (float) this.size * 5);
    }

    @Override
    public AABB getRenderBoundingBox(float partial) {
        Vec3 center = charge && level.getEntity(ownerId) instanceof LivingEntity owner
                ? AcidProjectile.handPosition(owner, partial, 1.2) : new Vec3(x, y, z);
        return AABB.ofSize(center, size * 6, size * 6, size * 6);
    }

    @Override
    public void render(VertexConsumer ignored, Camera camera, float partial) {
        Vec3 origin = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z));
        Vec3 forward = new Vec3(0, 1, 0);
        if (charge && level.getEntity(ownerId) instanceof LivingEntity owner) {
            if (!owner.isAlive()) return;
            origin = AcidProjectile.handPosition(owner, partial, 1.2);
            forward = owner.getViewVector(partial);
        }
        Vec3 view = camera.getPosition().subtract(origin);
        if (view.lengthSqr() > 9216) return;
        float time = age + partial, fade = Math.max(0, 1 - time / lifetime);
        var poses = new PoseStack();
        Vec3 relative = origin.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        if (charge) poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), forward.toVector3f()));
        if (charge)
            AcidRenderer.blob(poses, buffers, Vec3.ZERO, size * (.9 + Math.sin(time * .4) * .06), 1, time, fade * .9F, 8);
        else if (time < 7) {
            Vec3 right = SkillVfx.perpendicular(forward), up = forward.cross(right);
            var vertices = buffers.getBuffer(SkillVfx.HAZE);
            for (int lobe = 0; lobe < 8; lobe++) {
                double a = phase + lobe * Math.PI / 4, b = a + Math.PI / 4;
                double spread = size * (.2 + time * .11);
                Vec3 p = right.scale(Math.cos(a) * spread).add(up.scale(Math.sin(a) * spread));
                Vec3 q = right.scale(Math.cos(b) * spread).add(up.scale(Math.sin(b) * spread));
                Vec3 tip = p.scale(1.35).add(forward.scale(size * Math.sin(time / 7 * Math.PI) * (lobe % 2 == 0 ? .6 : .35)));
                for (Vec3 point : new Vec3[]{p.scale(.3), p, tip, q})
                    vertices.addVertex(poses.last().pose(),
                            (float) point.x, (float) point.y, (float) point.z).setColor(.7F, .71F, .77F, fade * .8F);
            }
        }
        int drops = view.lengthSqr() > 1024 ? 4 : charge ? 6 : 12;
        for (int i = 0; i < drops; i++) {
            double angle = phase + i * 2.399963, y = 1 - 2.0 * (i + .5) / drops, ring = Math.sqrt(1 - y * y);
            Vec3 direction = new Vec3(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
            Vec3 point = charge ? direction.scale(size * 1.3).yRot(time * .15F)
                    : direction.scale(size * (.1 + time * .07)).add(0, -time * time * .0025, 0);
            AcidRenderer.blob(poses, buffers, point, size * (charge ? .12 : .09) * Math.max(.2, fade),
                    charge ? 1.2 : 1.8 + fade * .8, time + i, fade, 6);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    public record Provider(boolean charge) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new AcidVfxParticle(level, x, y, z, vx, (int) vy, charge);
        }
    }
}

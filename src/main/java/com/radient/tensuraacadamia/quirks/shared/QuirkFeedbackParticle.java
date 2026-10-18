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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class QuirkFeedbackParticle extends Particle {
    public enum Kind { STOCKPILE, TELEPORT, PHASE, BLOOD, WOOD_SLASH }

    private final Kind kind;
    private final double radius;
    private final double height;
    private final Vec3 direction;
    private final int targetId;

    private QuirkFeedbackParticle(ClientLevel level, double x, double y, double z,
                                 double first, double second, double third, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        radius = kind == Kind.WOOD_SLASH ? .9 : Math.clamp(first, .15, 32);
        height = Math.clamp(second, .5, 6);
        direction = new Vec3(first, second, third).normalize();
        targetId = kind == Kind.PHASE || kind == Kind.BLOOD ? (int) third : -1;
        lifetime = kind == Kind.STOCKPILE ? 16 : kind == Kind.WOOD_SLASH ? 6 : 10;
        hasPhysics = false;
        xd = yd = zd = 0;
        setSize((float) radius * 4, (float) Math.max(height, radius * 4));
    }

    @Override public AABB getRenderBoundingBox(float partialTick) {
        var target = targetId < 0 ? null : level.getEntity(targetId);
        Vec3 center = target == null ? new Vec3(x, y, z) : target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
        return AABB.ofSize(center, radius * 6, Math.max(height * 3, radius * 6), radius * 6);
    }

    @Override public void render(VertexConsumer ignored, Camera camera, float partialTick) {
        Vec3 position = new Vec3(Mth.lerp(partialTick, xo, x), Mth.lerp(partialTick, yo, y), Mth.lerp(partialTick, zo, z));
        var target = targetId < 0 ? null : level.getEntity(targetId);
        if (target != null) position = target.getPosition(partialTick).add(0, target.getBbHeight() / 2, 0);
        Vec3 view = camera.getPosition().subtract(position);
        if (SkillVfx.detail(view, Vec3.ZERO) == 0) return;
        PoseStack poses = new PoseStack();
        Vec3 relative = position.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float time = age + partialTick;
        float fade = Math.max(0, 1 - time / lifetime);

        switch (kind) {
            case STOCKPILE -> {
                float visible = view.length() < radius ? .25F : 1;
                double size = radius * (.15 + Math.min(1, time / 4) * .85);
                SkillVfx.flare(poses, buffers, view, size, 0x321329, fade * visible, 11);
                SkillVfx.disc(poses, buffers, view, Vec3.ZERO, size * .65, 0xA23569, fade * .55F * visible, true);
                SkillVfx.disc(poses, buffers, view, Vec3.ZERO, size * .3, 0xFFF0DA, Math.max(0, 1 - time / 6) * visible, true);
                SkillVfx.ring(poses, buffers, view, Vec3.ZERO, view, size * 1.1, radius * .025, 0xF493BC, fade * .7F * visible, 0);
            }
            case TELEPORT -> {
                double spread = radius * (.3 + time * .06);
                Vec3 bottom = new Vec3(0, -height / 2, 0);
                SkillVfx.ring(poses, buffers, view, bottom.add(0, height * time / lifetime, 0),
                        new Vec3(0, 1, 0), spread, .04, 0xB6A2FF, fade * .8F, time * .1);
                SkillVfx.ribbon(poses, buffers, view, bottom, bottom.add(0, height, 0),
                        radius * fade, 0xDECFFF, fade * .4F, true);
            }
            case PHASE -> {
                Vec3 center = new Vec3(0, height * (time / lifetime - .5), 0);
                SkillVfx.ring(poses, buffers, view, center, new Vec3(0, 1, 0), radius, .025, 0xBDDDF1, fade * .45F, 0);
                SkillVfx.ribbon(poses, buffers, view, center.add(-radius, 0, 0), center.add(radius, 0, 0),
                        .025, 0xE7F5FF, fade * .3F, true);
            }
            case BLOOD -> {
                SkillVfx.disc(poses, buffers, view, Vec3.ZERO, radius * (1 + time * .025), 0x631027, fade * .35F, false);
                for (int streak = 0; streak < 5; streak++) {
                    double angle = streak * Math.PI * 2 / 5;
                    Vec3 start = new Vec3(Math.cos(angle) * radius, height * .4, Math.sin(angle) * radius);
                    SkillVfx.ribbon(poses, buffers, view, start, start.add(0, -height * .7 * (1 - fade), 0),
                            .035, 0xAE2440, fade * .65F, false);
                }
            }
            case WOOD_SLASH -> {
                Vec3 forward = direction.lengthSqr() < .01 ? new Vec3(0, 0, 1) : direction;
                Vec3 right = SkillVfx.perpendicular(forward), up = right.cross(forward);
                Vec3 previous = null;
                for (int segment = 0; segment <= 12; segment++) {
                    double angle = -.9 + segment * 1.8 / 12 + time * .06;
                    Vec3 next = right.scale(Math.sin(angle) * radius).add(up.scale(Math.cos(angle) * radius * .45));
                    if (previous != null) {
                        SkillVfx.ribbon(poses, buffers, view, previous, next, .12 * fade, 0xBA965D, fade * .45F, false);
                        SkillVfx.ribbon(poses, buffers, view, previous, next, .025, 0xFFE1A1, fade * .5F, true);
                    }
                    previous = next;
                }
            }
        }
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }

    public record Provider(Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double first, double second, double third) {
            return new QuirkFeedbackParticle(level, x, y, z, first, second, third, kind);
        }
    }
}

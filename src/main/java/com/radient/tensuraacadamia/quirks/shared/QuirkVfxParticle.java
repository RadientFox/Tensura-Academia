package com.radient.tensuraacadamia.quirks.shared;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

public final class QuirkVfxParticle extends Particle {
    public static final int EXPLOSION = 0, IMPACT = 1, ARC = 2, FIELD = 3, TRAIL = 4,
            SOUND = 5, WIND = 7, WIND_RELEASE = 8, SHARDS = 9, LINK = 10, SHELL = 11;
    private final int form, color;
    private final double size, height;
    private final Vec3 direction;
    private final long seed;

    private QuirkVfxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, int form, double size) {
        super(level, x, y, z);
        this.form = form; seed = random.nextLong();
        this.size = form == IMPACT || form == SHELL ? Math.clamp(vx, .25, 18)
                : form == WIND || form == WIND_RELEASE ? Math.clamp(vx, .6, 15)
                : form == SHARDS ? Math.clamp(vx * .5, .5, 15) : size;
        height = form == WIND || form == WIND_RELEASE ? Math.clamp(vy, 1, 50)
                : form == LINK ? new Vec3(vx, vy, vz).length() : 0;
        Vec3 supplied = form == SHARDS ? Vec3.atLowerCornerOf(Direction.from3DDataValue((int) Math.round(vy)).getNormal()) : new Vec3(vx, vy, vz);
        direction = supplied.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : supplied.normalize();
        color = form == WIND || form == WIND_RELEASE ? windColor((int) Math.round(vz))
                : form == SHARDS ? (vz > .5 ? 0xffcc4d : 0xa4e9ff) : 0;
        lifetime = switch (form) { case WIND, LINK -> 3; case SHELL -> 6; case IMPACT -> 26; case SHARDS, WIND_RELEASE -> 16;
            case TRAIL -> 18; case EXPLOSION -> 12; case ARC -> 7; case FIELD -> 9; default -> 16; };
        hasPhysics = false; gravity = 0; xd = yd = zd = 0;
        if (form == ARC) { xd = vx; yd = vy; zd = vz; }
        double extent = form == LINK ? height * 2 : form == WIND || form == WIND_RELEASE ? this.size * 2.5 : this.size * 6;
        setSize((float) Math.max(.5, extent), (float) Math.max(extent, height));
    }
    @Override public void render(VertexConsumer ignored, Camera camera, float partial) {
        Vec3 origin = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z));
        Vec3 cam = camera.getPosition().subtract(origin);
        var poses = new PoseStack(); Vec3 relative = origin.subtract(camera.getPosition());
        poses.translate(relative.x, relative.y, relative.z);
        renderEffect(poses, Minecraft.getInstance().renderBuffers().bufferSource(), cam, form, size, height,
                direction, color, age + partial, SkillVfx.clock(level.getGameTime(), partial), seed);
    }
    @Override public AABB getRenderBoundingBox(float partial) {
        Vec3 origin = new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z));
        if (form == LINK) return new AABB(origin,origin.add(direction.scale(height))).inflate(1);
        if (form == WIND || form == WIND_RELEASE) {
            double radius = size * 1.7 + 1;
            return new AABB(origin.x-radius,origin.y-1,origin.z-radius,origin.x+radius,origin.y+height+1,origin.z+radius);
        }
        return AABB.ofSize(origin,Math.max(1,size*6),Math.max(1,size*6),Math.max(1,size*6));
    }
    static int windColor(int infusion) {
        return switch (infusion) { case 1 -> 0xff680d; case 2 -> 0x164dc7; case 3 -> 0x9cdfff; default -> 0xd9efff; };
    }
    static void renderEffect(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                           int form, double size, double height, Vec3 direction, int color, float time, double clock, long seed) {
        int detail = SkillVfx.detail(cam, new Vec3(0, height * .5, 0)); if (detail == 0) return;
        switch (form) {
            case EXPLOSION, IMPACT -> explosion(poses, buffers, cam, size, time, seed, form == IMPACT, detail);
            case ARC, FIELD, TRAIL -> electricity(poses, buffers, cam, form, size, time, seed, detail);
            case SOUND -> sound(poses, buffers, cam, size, direction, time);
            case WIND, WIND_RELEASE -> wind(poses, buffers, cam, size, height, color, clock, time, form == WIND_RELEASE, detail);
            case SHARDS -> shards(poses, buffers, cam, size, direction, color, time, seed, detail);
            case LINK -> {
                float fade = Math.max(0, 1 - time / 3);
                SkillVfx.lightning(poses, buffers, cam, Vec3.ZERO, direction.scale(height), .065, 0xffd52a, fade, seed, true);
                SkillVfx.lightning(poses, buffers, cam, Vec3.ZERO, direction.scale(height), .018, 0xfff8c4, fade, seed, false);
            }
            case SHELL -> discharge(poses,buffers,cam,size,time,seed);
            default -> { }
        }
    }
    private static void discharge(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                                   double radius, float time, long seed) {
        float fade=Math.max(0,1-time/6);
        double growth=.65+time*.06;
        for(int i=1;i<=3;i++) {
            double fraction=i/3.0, r=radius*fraction*growth, y=radius*.7*Math.sqrt(1-fraction*fraction);
            SkillVfx.ring(poses,buffers,cam,new Vec3(0,y,0),new Vec3(0,1,0),r,.05,0xffd52a,fade*.35F,0);
        }
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2+seed%23*.2;
            Vec3 tip=new Vec3(Math.cos(a)*radius,.15,Math.sin(a)*radius);
            SkillVfx.lightning(poses,buffers,cam,new Vec3(0,radius*.55,0),tip,.028,0xffef8a,fade*.6F,seed+i,true);
        }
    }
    private static void explosion(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                                  double size, float time, long seed, boolean large, int detail) {
        float fade = Math.max(0, 1 - time / (large ? 18 : 10));
        if (fade == 0) return;
        float flash = Math.max(0, 1 - time / 2);
        float visibility = cam.length() < size ? .3F : 1;
        double growth = 1 - Math.exp(-time * .4);
        double radius = size * (.18 + growth * .65);
        int segments = detail > 8 ? 12 : 8;
        int lobes = detail > 8 ? (large ? 6 : 4) : 3;
        float body = Math.min(1, time / 2) * fade * visibility;
        explosionLobe(poses, buffers, Vec3.ZERO, radius * .65, radius * .65,
                0xFFC552, fade * fade * .85F * visibility, time, seed, segments);
        for (int lobe = 0; lobe < lobes; lobe++) {
            double angle = lobe * 2.399963 + (seed & 255) * .1;
            double height = 1 - 2.0 * (lobe + .5) / lobes;
            double spread = Math.sqrt(1 - height * height);
            Vec3 center = new Vec3(Math.cos(angle) * spread, height, Math.sin(angle) * spread)
                    .scale(radius * .45).add(0, size * time * .012, 0);
            double billow = radius * (.65 + .08 * Math.sin(lobe * 1.7 + time * .3));
            explosionLobe(poses, buffers, center, billow, billow * (1 + time * .012),
                    lobe % 2 == 0 ? 0xF58218 : 0xDC510C, body * .9F, time, seed + lobe * 37, segments);
        }
        if (flash > 0) explosionLobe(poses, buffers, Vec3.ZERO, radius * 1.05, radius * 1.05,
                0xFFF4CF, flash * visibility, time, seed, segments);
        if (time < 6) SkillVfx.ring(poses, buffers, cam, Vec3.ZERO, new Vec3(0, 1, 0), size * (.2 + growth),
                size * .025, 0xFFBE45, (1 - time / 6) * .45F * visibility, 0);
        if (time > 2) {
            int sparks = Math.min(large ? 8 : 3, detail);
            for (int spark = 0; spark < sparks; spark++) {
                double angle = spark * 2.399963 + seed % 31;
                double height = 1 - 2.0 * (spark + .5) / sparks;
                double spread = Math.sqrt(1 - height * height);
                Vec3 axis = new Vec3(Math.cos(angle) * spread, height, Math.sin(angle) * spread);
                Vec3 tip = axis.scale(size * (.5 + time * .08)).add(0, -time * time * size * .0015, 0);
                SkillVfx.tube(poses, buffers, cam, tip.subtract(axis.scale(size * .12)), tip,
                        size * .009, 0xFFAE3B, fade * .7F * visibility);
            }
        }
    }
    private static void explosionLobe(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers,
                                      Vec3 center, double radius, double height, int color, float alpha,
                                      float time, long seed, int segments) {
        if (alpha <= 0) return;
        int rows = segments / 2;
        Vec3[][] points = new Vec3[rows + 1][segments];
        double phase = (seed & 255) * .1;
        for (int row = 0; row <= rows; row++) {
            double latitude = -Math.PI / 2 + Math.PI * row / rows;
            for (int column = 0; column < segments; column++) {
                double longitude = Math.PI * 2 * column / segments + phase;
                Vec3 normal = new Vec3(Math.cos(latitude) * Math.cos(longitude), Math.sin(latitude),
                        Math.cos(latitude) * Math.sin(longitude));
                double ripple = 1 + .12 * Math.sin(normal.x * 5 + normal.y * 3 + phase + time * .25)
                        * Math.cos(normal.z * 4 - time * .18);
                points[row][column] = center.add(normal.multiply(radius * ripple, height * ripple, radius * ripple));
            }
        }
        var vertices = buffers.getBuffer(SkillVfx.FLAME);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < segments; column++) {
                int next = (column + 1) % segments;
                Vec3[] corners = {points[row][column], points[row + 1][column],
                        points[row + 1][next], points[row][next]};
                Vec3 normal = corners[0].add(corners[1]).add(corners[2]).add(corners[3])
                        .scale(.25).subtract(center).normalize();
                float shade = (float) (.7 + .3 * Math.max(0, normal.dot(new Vec3(.36, .8, .48))));
                for (Vec3 corner : corners) vertices.addVertex(poses.last().pose(), (float) corner.x, (float) corner.y, (float) corner.z)
                        .setColor((int) (((color >> 16) & 255) * shade), (int) (((color >> 8) & 255) * shade),
                                (int) ((color & 255) * shade), (int) (255 * alpha));
            }
        }
    }
    private static void electricity(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                                    int form, double size, float time, long seed, int detail) {
        float fade = Math.max(0, 1 - time / (form == TRAIL ? 18 : form == FIELD ? 9 : 7));
        int arcs = form == FIELD ? 3 : form == TRAIL ? 1 : 2;
        if (detail <= 8) arcs = 1;
        for (int i = 0; i < arcs; i++) {
            double angle = (seed % 1009) * .05 + i * 2.1;
            Vec3 axis = new Vec3(Math.cos(angle), Math.sin(angle * 1.3) * .6, Math.sin(angle)).normalize();
            SkillVfx.lightning(poses, buffers, cam, axis.scale(-size), axis.scale(size), form == FIELD ? .018 : .012,
                    0xffef8a, fade, seed + i + (long) (time / 2) * 31, form != TRAIL && detail > 8);
        }
        SkillVfx.disc(poses, buffers, cam, Vec3.ZERO, size * .3, 0xffc928, fade * .35F, true);
        if (form == FIELD) SkillVfx.ring(poses, buffers, cam, Vec3.ZERO, cam, size * (.3 + time * .07), .02,
                0xffdc4d, fade * .5F, seed);
    }
    private static void sound(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                              double radius, Vec3 direction, float time) {
        float fade = Math.max(0, 1 - time / 16);
        Vec3 center = direction.scale(time * .14);
        SkillVfx.ring(poses, buffers, cam, center, direction, radius * (.65 + time * .025), Math.max(.06, radius * .06),
                0xffdf75, fade * .9F, 0);
        SkillVfx.ring(poses, buffers, cam, center.subtract(direction.scale(.35)), direction, radius * (.5 + time * .025),
                Math.max(.035, radius * .025), 0xfff4c6, fade * .65F, .08);
        Vec3 right = SkillVfx.perpendicular(direction), up = direction.cross(right);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3;
            Vec3 rim = right.scale(Math.cos(angle) * radius * .8).add(up.scale(Math.sin(angle) * radius * .8));
            SkillVfx.ribbon(poses, buffers, cam, center.add(rim), center.add(rim.scale(.95)).subtract(direction.scale(.7)),
                    .025, 0xffd85e, fade * .45F, true);
        }
    }
    private static void wind(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                             double radius, double height, int color, double clock, float time, boolean released, int detail) {
        float alpha = released ? Math.max(0, 1 - time / 16) * .35F : .22F;
        double growth = released ? 1 + time * .035 : 1;
        int strands = detail <= 8 ? 3 : 5, segments = detail <= 8 ? 18 : height > 10 ? 48 : 24;
        for (int strand = 0; strand < strands; strand++) {
            Vec3 previous = null;
            for (int i = 0; i <= segments; i++) {
                double t = i / (double) segments, a = clock * .16 + t * Math.PI * 6 + strand * Math.PI * 2 / strands;
                double r = radius * (height > 10 ? .92 + .06 * Math.sin(t * 8) : .25 + .7 * t) * growth;
                Vec3 point = new Vec3(Math.cos(a) * r, t * height, Math.sin(a) * r);
                if (previous != null) {
                    SkillVfx.ribbon(poses, buffers, cam, previous, point, radius * (height > 10 ? .085 : .13), color, alpha, false);
                    if (strand % 2 == 0) SkillVfx.ribbon(poses, buffers, cam, previous, point, .035,
                            color, alpha * 1.4F, true);
                }
                previous = point;
            }
        }
        for (int i = 0; i < 3; i++) SkillVfx.ring(poses, buffers, cam, new Vec3(0, height * (i + .3) / 3, 0), new Vec3(0, 1, 0),
                radius * (height > 10 ? 1 : .35 + i * .3) * growth, .035, color, alpha * .65F, clock * .16);
    }
    private static void shards(PoseStack poses, net.minecraft.client.renderer.MultiBufferSource buffers, Vec3 cam,
                               double radius, Vec3 normal, int color, float time, long seed, int detail) {
        float fade = Math.max(0, 1 - time / 16);
        Vec3 right = SkillVfx.perpendicular(normal), up = normal.cross(right);
        for (int i = 0; i < Math.min(12, detail); i++) {
            double a = i * 2.399963 + seed % 17;
            Vec3 p = right.scale(Math.cos(a) * radius * (.3 + time * .035))
                    .add(up.scale(Math.sin(a) * radius * (.3 + time * .035)))
                    .add(normal.scale(time * .05)).add(0, -.005 * time * time, 0);
            SkillVfx.ribbon(poses, buffers, cam, p, p.add(right.scale(radius * .12)).add(up.scale(radius * .06)),
                    radius * .08, color, fade * .6F, true);
        }
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.CUSTOM; }
    public record Provider(int form, double size) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new QuirkVfxParticle(level, x, y, z, vx, vy, vz, form, size);
        }
    }
}

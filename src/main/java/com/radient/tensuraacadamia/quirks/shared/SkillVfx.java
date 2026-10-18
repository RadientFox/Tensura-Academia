package com.radient.tensuraacadamia.quirks.shared;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;

public final class SkillVfx extends RenderType {
    public static final RenderType HAZE = create("tracadamia_skill_haze", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    public static final RenderType SHINE = create("tracadamia_skill_shine", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    public static final RenderType FLAME = create("tracadamia_explosion_flame", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE).setCullState(CULL).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    public static final RenderType BILLOW = create("tracadamia_billow", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(ResourceLocation.withDefaultNamespace("textures/particle/generic_5.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    private static final VFXHelper.GeometryList GEOMETRY = new VFXHelper.GeometryList();
    private SkillVfx(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumble,
                     boolean sorted, Runnable setup, Runnable clear) { super(name, format, mode, size, crumble, sorted, setup, clear); }
    public static int detail(Vec3 camera, Vec3 center) {
        double distance = camera.distanceToSqr(center);
        return distance > 16384 ? 0 : distance > 4096 ? 8 : distance > 1024 ? 12 : 20;
    }
    public static double clock(long ticks, float partial) {
        return (double) ticks + partial;
    }
    public static Vec3 closest(Vec3 camera, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double fraction = delta.lengthSqr() < 1e-8 ? 0 : Math.clamp(camera.subtract(start).dot(delta) / delta.lengthSqr(), 0, 1);
        return start.add(delta.scale(fraction));
    }
    public static int detail(Vec3 camera, AABB bounds) {
        return detail(camera, new Vec3(Math.clamp(camera.x, bounds.minX, bounds.maxX),
                Math.clamp(camera.y, bounds.minY, bounds.maxY), Math.clamp(camera.z, bounds.minZ, bounds.maxZ)));
    }
    public static Vec3 perpendicular(Vec3 direction) {
        Vec3 axis = direction.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : direction.normalize();
        return axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
    }
    public static void tube(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 start, Vec3 end,
                            double radius, int rgb, float alpha) {
        Vec3 length = end.subtract(start);
        if (length.lengthSqr() < 1e-8 || radius <= 0 || alpha <= 0) return;
        double nearest = Math.clamp(camera.subtract(start).dot(length) / length.lengthSqr(), 0, 1);
        int steps = detail(camera, start.add(length.scale(nearest)));
        if (steps == 0) return;
        steps = Math.max(12, steps);
        Vec3 axis = length.normalize(), right = perpendicular(axis), up = axis.cross(right);
        Vec3 view = camera.subtract(start.add(length.scale(nearest)));
        view = view.subtract(axis.scale(view.dot(axis)));
        if (view.lengthSqr() < 1e-8) view = right;
        view = view.normalize();
        GEOMETRY.clear();
        int capColor = VFXHelper.Colors.pack(alpha, rgb);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps, b = (i + 1) * Math.PI * 2 / steps;
            Vec3 normalA = right.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
            Vec3 normalB = right.scale(Math.cos(b)).add(up.scale(Math.sin(b)));
            Vec3 p = start.add(normalA.scale(radius)), q = start.add(normalB.scale(radius));
            Vec3 P = end.add(normalA.scale(radius)), Q = end.add(normalB.scale(radius));
            int colorA = VFXHelper.Colors.pack(alpha * (.3F + .7F * (float) Math.abs(normalA.dot(view))), rgb);
            int colorB = VFXHelper.Colors.pack(alpha * (.3F + .7F * (float) Math.abs(normalB.dot(view))), rgb);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), p, q, Q, P, colorA, colorB, colorB, colorA);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), start, q, p, start, capColor, capColor, capColor, capColor);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), end, P, Q, end, capColor, capColor, capColor, capColor);
        }
        GEOMETRY.emit(buffers.getBuffer(SHINE), poses.last().pose());
    }
    public static void ribbon(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 start, Vec3 end,
                              double width, int rgb, float alpha, boolean glow) {
        if (start.distanceToSqr(end) < 1e-8 || width <= 0 || alpha <= 0) return;
        if (end.subtract(start).cross(camera.subtract(start)).lengthSqr() < 1e-8) camera = start.add(perpendicular(end.subtract(start)));
        GEOMETRY.clear();
        if (glow) {
            Vec3 side = end.subtract(start).cross(camera.subtract(start)).normalize().scale(width * .5);
            int edge = VFXHelper.Colors.pack(0, rgb), core = VFXHelper.Colors.pack(alpha, rgb);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), start.subtract(side), start, end, end.subtract(side), edge, core, core, edge);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), start, start.add(side), end.add(side), end, core, edge, edge, core);
        } else {
            Vec3 side = end.subtract(start).cross(camera.subtract(start)).normalize().scale(width * .5);
            int color = VFXHelper.Colors.pack(alpha, rgb);
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), start.subtract(side), start.add(side),
                    end.add(side), end.subtract(side), color, color, color, color);
        }
        GEOMETRY.emit(buffers.getBuffer(glow ? SHINE : HAZE), poses.last().pose());
    }
    public static void disc(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 center,
                            double radius, int rgb, float alpha, boolean glow) {
        int steps = detail(camera, center); if (!glow) steps = Math.min(8, steps);
        if (steps == 0 || radius <= 0 || alpha <= 0) return;
        if (camera.distanceToSqr(center) < 1e-8) camera = center.add(0, 0, 1);
        Vec3 normal = camera.subtract(center).normalize(), right = perpendicular(normal), up = normal.cross(right);
        GEOMETRY.clear();
        int inner = VFXHelper.Colors.pack(alpha, rgb), outer = VFXHelper.Colors.pack(0, rgb);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps, b = (i + 1) * Math.PI * 2 / steps;
            Vec3 p = center.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            Vec3 q = center.add(right.scale(Math.cos(b) * radius)).add(up.scale(Math.sin(b) * radius));
            VFXHelper.Shapes.drawQuad(GEOMETRY, poses.last().pose(), center, p, q, center, inner, outer, outer, inner);
        }
        GEOMETRY.emit(buffers.getBuffer(glow ? SHINE : HAZE), poses.last().pose());
    }
    public static void billow(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 center,
                              double width, double height, int rgb, float alpha, double rotation) {
        if (detail(camera, center) == 0 || alpha <= 0) return;
        Vec3 normal = camera.subtract(center);
        if (normal.lengthSqr() < 1e-8) normal = new Vec3(0, 0, 1);
        normal = normal.normalize();
        Vec3 right = perpendicular(normal), up = normal.cross(right);
        Vec3 horizontal = right.scale(Math.cos(rotation)).add(up.scale(Math.sin(rotation))).scale(width);
        Vec3 vertical = up.scale(Math.cos(rotation)).subtract(right.scale(Math.sin(rotation))).scale(height);
        var vertices = buffers.getBuffer(BILLOW);
        Vec3[] corners = {center.subtract(horizontal).subtract(vertical), center.subtract(horizontal).add(vertical),
                center.add(horizontal).add(vertical), center.add(horizontal).subtract(vertical)};
        for (int i = 0; i < 4; i++) vertices.addVertex(poses.last().pose(), (float) corners[i].x, (float) corners[i].y, (float) corners[i].z)
                .setUv(i >= 2 ? 1 : 0, i == 0 || i == 3 ? 1 : 0)
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Math.clamp(alpha, 0, 1) * 255));
    }
    public static void crescent(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 center,
                                Vec3 right, Vec3 up, double radius, double width, int rgb, float alpha, double rotation) {
        int steps = detail(camera, center);
        if (steps == 0 || alpha <= 0) return;
        var vertices = buffers.getBuffer(SHINE);
        for (int i = 0; i < steps; i++) {
            double a = rotation - 1.1 + 2.2 * i / steps, b = rotation - 1.1 + 2.2 * (i + 1) / steps;
            Vec3 p = right.scale(Math.sin(a)).add(up.scale(Math.cos(a)));
            Vec3 q = right.scale(Math.sin(b)).add(up.scale(Math.cos(b)));
            double innerA = radius - width * Math.sin(Math.PI * i / steps);
            double innerB = radius - width * Math.sin(Math.PI * (i + 1) / steps);
            Vec3[] points = {center.add(p.scale(innerA)), center.add(p.scale(radius)), center.add(q.scale(radius)), center.add(q.scale(innerB))};
            for (int v = 0; v < 4; v++) vertices.addVertex(poses.last().pose(), (float) points[v].x, (float) points[v].y, (float) points[v].z)
                    .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (255 * alpha * (v == 0 || v == 3 ? .12 : 1)));
        }
    }
    public static void ring(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 center, Vec3 normal,
                            double radius, double width, int rgb, float alpha, double spin) {
        int steps = detail(camera, center); if (steps == 0) return;
        if (normal.lengthSqr() < 1e-8) normal = new Vec3(0, 1, 0);
        normal = normal.normalize(); Vec3 right = perpendicular(normal), up = normal.cross(right);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps + spin, b = (i + 1) * Math.PI * 2 / steps + spin;
            Vec3 p = center.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            Vec3 q = center.add(right.scale(Math.cos(b) * radius)).add(up.scale(Math.sin(b) * radius));
            ribbon(poses, buffers, camera, p, q, width, rgb, alpha * (i % 5 == 0 ? .35f : 1), true);
        }
    }
    public static void burst(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 center,
                             float time, double size, int rgb, long seed) {
        float fade = Math.max(0, 1 - time / 12); if (fade == 0 || detail(camera, center) == 0) return;
        disc(poses, buffers, camera, center, size * .7, rgb, fade * .6f, true);
        ring(poses, buffers, camera, center, camera.subtract(center), size * (.2 + time * .12), size * .07, rgb, fade, time * .1);
        int count = detail(camera, center);
        for (int i = 0; i < count; i++) {
            double a = i * 2.399963 + seed, y = 1 - 2.0 * (i + .5) / count, r = Math.sqrt(1 - y * y);
            Vec3 direction = new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
            Vec3 tip = center.add(direction.scale(size * (.3 + time * .15)));
            ribbon(poses, buffers, camera, tip.subtract(direction.scale(size * .35)), tip, size * .045, rgb, fade, true);
        }
    }
    public static void lightning(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 from, Vec3 to,
                                 double width, int rgb, float alpha, long seed, boolean branches) {
        if (detail(camera, from.lerp(to, .5)) == 0 || from.distanceToSqr(to) < 1e-8 || alpha <= 0) return;
        GEOMETRY.clear();
        int color = VFXHelper.Colors.pack(alpha, rgb);
        float displacement = (float) Math.min(.75, from.distanceTo(to) * .3);
        int subdivisions = detail(camera, from.lerp(to,.5)) > 8 ? 3 : 2;
        if (branches) VFXHelper.Shapes.drawBranchingLightning(GEOMETRY, poses.last().pose(), from, to,
                displacement, subdivisions, (float) width, color, seed, .3F);
        else VFXHelper.Shapes.drawLightningArc(GEOMETRY, poses.last().pose(), from, to,
                displacement, subdivisions, (float) width, color, seed);
        GEOMETRY.emit(buffers.getBuffer(SHINE), poses.last().pose());
    }
    public static void flare(PoseStack poses, MultiBufferSource buffers, Vec3 camera, double radius, int rgb, float alpha, long seed) {
        if (alpha <= 0 || detail(camera, Vec3.ZERO) == 0) return;
        Vec3 normal = camera.lengthSqr() < 1e-8 ? new Vec3(0,0,1) : camera.normalize();
        Vec3 right = perpendicular(normal), up = normal.cross(right);
        GEOMETRY.clear();
        int core = VFXHelper.Colors.pack(alpha,rgb), edge = VFXHelper.Colors.pack(alpha*.7F,rgb);
        for(int i=0;i<16;i++) {
            double a=i*Math.PI/8+seed%37*.1,b=(i+1)*Math.PI/8+seed%37*.1;
            double r=radius*(i%2==0?1:.5)*(1+Math.sin(i*4.1+seed%17)*.12);
            double R=radius*(i%2==0?.5:1)*(1+Math.sin((i+1)*4.1+seed%17)*.12);
            Vec3 p=right.scale(Math.cos(a)*r).add(up.scale(Math.sin(a)*r));
            Vec3 q=right.scale(Math.cos(b)*R).add(up.scale(Math.sin(b)*R));
            VFXHelper.Shapes.drawQuad(GEOMETRY,poses.last().pose(),Vec3.ZERO,p,q,Vec3.ZERO,core,edge,edge,core);
        }
        GEOMETRY.emit(buffers.getBuffer(HAZE),poses.last().pose());
    }
    public static void flush() {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch(BILLOW); buffers.endBatch(HAZE); buffers.endBatch(SHINE); buffers.endBatch(FLAME);
    }
}


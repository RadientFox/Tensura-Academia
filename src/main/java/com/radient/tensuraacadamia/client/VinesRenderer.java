package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.VineConstruct;
import com.radient.tensuraacadamia.regestry.VinesEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VinesRenderer extends EntityRenderer<VineConstruct> {
    private static final RenderType OPAQUE_VINES = RenderType.create("tracadamia_vines",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    public VinesRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VinesEntities.VINE.get(), VinesRenderer::new);
    }
    @Override public boolean shouldRender(VineConstruct vine, Frustum frustum, double x, double y, double z) {
        return vine.shouldRender(x, y, z);
    }
    @Override public void render(VineConstruct vine, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        VertexConsumer vertices = buffers.getBuffer(OPAQUE_VINES);
        if (vine.form() == VineConstruct.SHIELD) {
            poses.pushPose();
            if (vine.normal().getAxis() == net.minecraft.core.Direction.Axis.X)
                poses.mulPose(new Quaternionf().rotationY((float) Math.PI / 2));
            float half = vine.getBbWidth() / 2, height = vine.getBbHeight();
            for (float x = -half + 0.08F; x <= half; x += 0.3F)
                strand(vertices, poses, new Vec3(x, 0, 0), new Vec3(x, height, 0), 0.16F, true);
            for (float y = 0.15F; y <= height; y += 0.4F)
                strand(vertices, poses, new Vec3(-half, y, 0.12), new Vec3(half, y, 0.12), 0.14F, false);
            poses.popPose();
        } else {
            Entity owner = vine.visualOwner();
            if (vine.form() != VineConstruct.CAGE && owner != null) {
                var client = Minecraft.getInstance();
                boolean firstPerson = owner == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
                Vec3 camera = client.gameRenderer.getMainCamera().getPosition().subtract(vine.getPosition(partialTick));
                Vec3 back = owner.getViewVector(partialTick).multiply(-0.18, 0, -0.18);
                Vec3 origin = (firstPerson ? camera.add(0, 0.5, 0)
                        : owner.getPosition(partialTick).add(0, owner.getEyeHeight() + 0.4, 0).subtract(vine.getPosition(partialTick))).add(back);
                Vec3 end = new Vec3(0, vine.getBbHeight() * 0.5, 0);
                int pieces = Math.clamp((int) origin.distanceTo(end) * 2, 4, 80);
                Vec3 previous = origin;
                for (int i = 1; i <= pieces; i++) {
                    double t = (double) i / pieces;
                    Vec3 next = origin.lerp(end, t).add(0, -Math.sin(t * Math.PI) * 0.25, 0);
                    if (!firstPerson || outsideCamera(previous, next, camera))
                        strand(vertices, poses, previous, next, 0.08F, i % 3 == 0);
                    previous = next;
                }
            }
            if (vine.visualTarget() != null || vine.form() == VineConstruct.CAGE) {
                double radius = vine.getBbWidth() / 2;
                double height = vine.getBbHeight();
                for (int ring = 0; ring < (vine.form() == VineConstruct.CAGE ? 7 : 4); ring++) {
                    double y = height * (ring + 0.5) / (vine.form() == VineConstruct.CAGE ? 7 : 4);
                    double r = vine.form() == VineConstruct.CAGE ? radius * Math.sin(Math.PI * y / height) : radius;
                    for (int segment = 0; segment < 12; segment++) {
                        double a = segment * Math.PI / 6, b = (segment + 1) * Math.PI / 6;
                        strand(vertices, poses, new Vec3(Math.cos(a) * r, y, Math.sin(a) * r),
                                new Vec3(Math.cos(b) * r, y, Math.sin(b) * r), 0.09F, segment % 3 == 0);
                    }
                }
                if (vine.form() == VineConstruct.CAGE) for (int meridian = 0; meridian < 6; meridian++) {
                    double angle = meridian * Math.PI / 3;
                    for (int segment = 0; segment < 10; segment++) {
                        double a = segment * Math.PI / 10, b = (segment + 1) * Math.PI / 10;
                        strand(vertices, poses, new Vec3(Math.cos(angle) * radius * Math.sin(a), height * segment / 10, Math.sin(angle) * radius * Math.sin(a)),
                                new Vec3(Math.cos(angle) * radius * Math.sin(b), height * (segment + 1) / 10, Math.sin(angle) * radius * Math.sin(b)), 0.08F, false);
                    }
                    Vec3 previous = Vec3.ZERO;
                    for (int segment = 1; segment <= 8; segment++) {
                        double t = segment / 8.0;
                        double spread = radius * 0.8 * Math.sin(t * Math.PI / 2);
                        Vec3 next = new Vec3(Math.cos(angle) * spread, -t * 2, Math.sin(angle) * spread);
                        strand(vertices, poses, previous, next, 0.09F, segment % 3 == 0);
                        previous = next;
                    }
                }
            } else strand(vertices, poses, Vec3.ZERO, new Vec3(0, 0.35, 0), 0.12F, true);
        }
        super.render(vine, yaw, partialTick, poses, buffers, light);
    }
    private static boolean outsideCamera(Vec3 start, Vec3 end, Vec3 camera) {
        Vec3 delta = end.subtract(start);
        double t = delta.lengthSqr() == 0 ? 0 : Math.clamp(camera.subtract(start).dot(delta) / delta.lengthSqr(), 0, 1);
        return start.add(delta.scale(t)).distanceToSqr(camera) > 0.85 * 0.85;
    }
    private static void strand(VertexConsumer vertices, PoseStack poses, Vec3 start, Vec3 end, float width, boolean thorn) {
        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 0.00001) return;
        poses.pushPose();
        poses.translate(start.x, start.y, start.z);
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
        Matrix4f matrix = poses.last().pose();
        float half = width / 2, length = (float) delta.length();
        box(vertices, matrix, -half, -half, 0, half, half, length, 28, 68, 20);
        if (thorn) {
            float z = length / 2, point = half + 0.17F;
            quad(vertices, matrix, -half,-half,z-0.06F, half,-half,z-0.06F, 0,-point,z, 0,-point,z, 94,80,42);
            quad(vertices, matrix, half,-half,z-0.06F, half,-half,z+0.06F, 0,-point,z, 0,-point,z, 94,80,42);
            quad(vertices, matrix, half,-half,z+0.06F, -half,-half,z+0.06F, 0,-point,z, 0,-point,z, 94,80,42);
            quad(vertices, matrix, -half,-half,z+0.06F, -half,-half,z-0.06F, 0,-point,z, 0,-point,z, 94,80,42);
        }
        poses.popPose();
    }
    private static void box(VertexConsumer v, Matrix4f m, float x, float y, float z, float X, float Y, float Z, int r, int g, int b) {
        quad(v,m,x,y,z,X,y,z,X,Y,z,x,Y,z,r,g,b);
        quad(v,m,x,Y,Z,X,Y,Z,X,y,Z,x,y,Z,r,g,b);
        quad(v,m,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,r,g,b);
        quad(v,m,x,y,Z,X,y,Z,X,y,z,x,y,z,r,g,b);
        quad(v,m,x,y,z,x,Y,z,x,Y,Z,x,y,Z,r,g,b);
        quad(v,m,X,y,Z,X,Y,Z,X,Y,z,X,y,z,r,g,b);
    }
    private static void quad(VertexConsumer v, Matrix4f m, float x1,float y1,float z1,float x2,float y2,float z2,
                             float x3,float y3,float z3,float x4,float y4,float z4,int r,int g,int b) {
        v.addVertex(m,x1,y1,z1).setColor(r,g,b,255);
        v.addVertex(m,x2,y2,z2).setColor(r,g,b,255);
        v.addVertex(m,x3,y3,z3).setColor(r,g,b,255);
        v.addVertex(m,x4,y4,z4).setColor(r,g,b,255);
    }
    @Override public ResourceLocation getTextureLocation(VineConstruct vine) {
        return ResourceLocation.withDefaultNamespace("textures/block/vine.png");
    }
}

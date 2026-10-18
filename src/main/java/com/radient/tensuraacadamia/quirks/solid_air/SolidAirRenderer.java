package com.radient.tensuraacadamia.quirks.solid_air;

import com.radient.tensuraacadamia.quirks.shared.BarrierVfx;
import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SolidAirRenderer extends EntityRenderer<SolidAirWall> {
    public SolidAirRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SolidAirEntities.WALL.get(), SolidAirRenderer::new);
    }
    @Override public void render(SolidAirWall wall, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        AABB bounds = wall.getBoundingBox().move(-wall.getX(), -wall.getY(), -wall.getZ());
        VertexConsumer vertices = buffers.getBuffer(SkillVfx.HAZE);
        poses.pushPose();
        poses.translate(0, bounds.getYsize() / 2, 0);
        var normal = wall.normal().getNormal();
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f(-normal.getX(), -normal.getY(), -normal.getZ()));
        poses.mulPose(rotation);
        float span = (float) Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        float thickness = (float) Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize()));
        Matrix4f matrix = poses.last().pose();
        float half = span / 2;
        box(vertices, matrix, -half, -half, -thickness / 2, half, half, thickness / 2, 22);
        for (float offset = -half; offset <= half + 0.001F; offset += 1) {
            box(vertices, matrix, offset - 0.015F, -half, thickness / 2, offset + 0.015F, half, thickness / 2 + 0.025F, 65);
            box(vertices, matrix, -half, offset - 0.015F, thickness / 2, half, offset + 0.015F, thickness / 2 + 0.025F, 65);
        }
        if (wall.spiked()) for (float x = -half + 0.75F; x < half; x += 2) for (float y = -half + 0.75F; y < half; y += 2) {
            float z = thickness / 2, w = 0.25F;
            quad(vertices, matrix, x-w,y-w,z, x+w,y-w,z, x,y,z+0.45F, x,y,z+0.45F, 220);
            quad(vertices, matrix, x+w,y-w,z, x+w,y+w,z, x,y,z+0.45F, x,y,z+0.45F, 220);
            quad(vertices, matrix, x+w,y+w,z, x-w,y+w,z, x,y,z+0.45F, x,y,z+0.45F, 220);
            quad(vertices, matrix, x-w,y+w,z, x-w,y-w,z, x,y,z+0.45F, x,y,z+0.45F, 220);
        }
        Vec3 view = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .subtract(wall.getPosition(partialTick)).subtract(0,bounds.getYsize()/2,0);
        Vec3 localCamera = new Vec3(view.toVector3f().rotate(new Quaternionf(rotation).conjugate()));
        BarrierVfx.render(poses,buffers,localCamera,span,thickness,wall.getHealth()/Math.max(1,wall.getMaxHealth()),
                wall.tickCount+partialTick,wall.visualHitAge(partialTick),
                new Vec3(wall.visualHitPoint().toVector3f().rotate(new Quaternionf(rotation).conjugate())),
                false,false,wall.getUUID().getLeastSignificantBits());
        poses.popPose();
        super.render(wall, yaw, partialTick, poses, buffers, light);
    }
    private static void box(VertexConsumer vertices, Matrix4f matrix, float x, float y, float z,
                            float X, float Y, float Z, int alpha) {
        quad(vertices,matrix,x,y,z,X,y,z,X,Y,z,x,Y,z,alpha);
        quad(vertices,matrix,x,Y,Z,X,Y,Z,X,y,Z,x,y,Z,alpha);
        quad(vertices,matrix,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,alpha);
        quad(vertices,matrix,x,y,Z,X,y,Z,X,y,z,x,y,z,alpha);
        quad(vertices,matrix,x,y,z,x,Y,z,x,Y,Z,x,y,Z,alpha);
        quad(vertices,matrix,X,y,Z,X,Y,Z,X,Y,z,X,y,z,alpha);
    }
    private static void quad(VertexConsumer vertices, Matrix4f matrix, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float x4, float y4, float z4, int alpha) {
        vertices.addVertex(matrix,x1,y1,z1).setColor(210,240,255,alpha);
        vertices.addVertex(matrix,x2,y2,z2).setColor(210,240,255,alpha);
        vertices.addVertex(matrix,x3,y3,z3).setColor(210,240,255,alpha);
        vertices.addVertex(matrix,x4,y4,z4).setColor(210,240,255,alpha);
    }
    @Override public ResourceLocation getTextureLocation(SolidAirWall wall) {
        return ResourceLocation.withDefaultNamespace("textures/block/white_stained_glass.png");
    }
}

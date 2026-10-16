package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.QuirkCloud;
import com.radient.tensuraacadamia.regestry.CloudEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CloudRenderer extends EntityRenderer<QuirkCloud> {
    public CloudRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CloudEntities.CLOUD.get(), CloudRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(QuirkCloud cloud) {
        return ResourceLocation.withDefaultNamespace("textures/block/white_wool.png");
    }
    @Override public void render(QuirkCloud cloud, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        if (cloud.kind() == QuirkCloud.RIDE) {
            box(vertices, matrix, -1.5, -0.08, -1.15, 1.5, 0.45, 1.15, 236, 240, 246, 195);
            box(vertices, matrix, -1.85, 0.02, -0.6, -0.55, 0.58, 0.55, 249, 250, 255, 180);
            box(vertices, matrix, 0.5, 0.01, -0.85, 1.75, 0.62, 0.52, 249, 250, 255, 178);
            box(vertices, matrix, -0.7, 0.1, 0.38, 0.9, 0.7, 1.4, 255, 255, 255, 180);
            box(vertices, matrix, -0.65, 0.27, -0.72, 0.65, 0.84, 0.54, 255, 255, 255, 160);
        } else if (cloud.kind() == QuirkCloud.BLIND) {
            box(vertices, matrix, -0.6, -0.35, -0.5, 0.6, 0.35, 0.5, 245, 247, 252, 205);
            box(vertices, matrix, -0.8, -0.1, -0.25, 0.2, 0.45, 0.45, 255, 255, 255, 165);
            box(vertices, matrix, -0.15, -0.45, -0.55, 0.8, 0.2, 0.35, 255, 255, 255, 165);
        } else {
            box(vertices, matrix, -50, 0, -50, 50, 50, 50, 139, 151, 165, 95);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                double cx = x * 32.0, cz = z * 32.0;
                double height = 37 + ((x + z + 6) % 3) * 4;
                box(vertices, matrix, cx - 19, height - 6, cz - 19,
                        cx + 19, Math.min(54, height + 11), cz + 19, 205, 213, 221, 88);
            }
        }
        super.render(cloud, yaw, partialTick, poses, buffers, light);
    }

    private static void box(VertexConsumer vertices, Matrix4f m, double x, double y, double z,
                            double X, double Y, double Z, int r, int g, int b, int a) {
        quad(vertices,m,x,y,z,X,y,z,X,Y,z,x,Y,z,r,g,b,a);
        quad(vertices,m,x,Y,Z,X,Y,Z,X,y,Z,x,y,Z,r,g,b,a);
        quad(vertices,m,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,r,g,b,a);
        quad(vertices,m,x,y,Z,X,y,Z,X,y,z,x,y,z,r,g,b,a);
        quad(vertices,m,x,y,z,x,Y,z,x,Y,Z,x,y,Z,r,g,b,a);
        quad(vertices,m,X,y,Z,X,Y,Z,X,Y,z,X,y,z,r,g,b,a);
    }
    private static void quad(VertexConsumer v, Matrix4f m, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3,
                             double x4, double y4, double z4, int r, int g, int b, int a) {
        v.addVertex(m,(float)x1,(float)y1,(float)z1).setColor(r,g,b,a);
        v.addVertex(m,(float)x2,(float)y2,(float)z2).setColor(r,g,b,a);
        v.addVertex(m,(float)x3,(float)y3,(float)z3).setColor(r,g,b,a);
        v.addVertex(m,(float)x4,(float)y4,(float)z4).setColor(r,g,b,a);
    }
}

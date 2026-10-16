package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.QueenBeamProjectile;
import com.radient.tensuraacadamia.regestry.QueenBeamEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QueenBeamRenderer extends EntityRenderer<QueenBeamProjectile> {
    public QueenBeamRenderer(EntityRendererProvider.Context context) { super(context); }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(QueenBeamEntities.QUEEN_BEAM.get(), QueenBeamRenderer::new);
    }

    @Override
    public void render(QueenBeamProjectile beam, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        Vec3 direction = beam.getDeltaMovement().normalize();
        poses.pushPose();
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)));
        float scale = beam.isPrincess() ? 0.5F : 1.0F;
        poses.scale(scale, scale, scale);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        var matrix = poses.last().pose();
        float w = 0.22F, front = 0.15F, back = -1.0F;
        quad(buffer, matrix, -w, -w, front, w, -w, front, w, w, front, -w, w, front);
        quad(buffer, matrix, -w, w, back, w, w, back, w, -w, back, -w, -w, back);
        quad(buffer, matrix, -w, w, front, w, w, front, w, w, back, -w, w, back);
        quad(buffer, matrix, -w, -w, back, w, -w, back, w, -w, front, -w, -w, front);
        quad(buffer, matrix, w, -w, front, w, -w, back, w, w, back, w, w, front);
        quad(buffer, matrix, -w, w, front, -w, w, back, -w, -w, back, -w, -w, front);
        poses.popPose();
        super.render(beam, yaw, partialTick, poses, buffers, light);
    }

    private static void quad(VertexConsumer buffer, org.joml.Matrix4f matrix,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        buffer.addVertex(matrix, x1, y1, z1).setColor(255, 75, 165, 230);
        buffer.addVertex(matrix, x2, y2, z2).setColor(255, 75, 165, 230);
        buffer.addVertex(matrix, x3, y3, z3).setColor(255, 75, 165, 230);
        buffer.addVertex(matrix, x4, y4, z4).setColor(255, 75, 165, 230);
    }

    @Override
    public ResourceLocation getTextureLocation(QueenBeamProjectile beam) {
        return ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    }
}

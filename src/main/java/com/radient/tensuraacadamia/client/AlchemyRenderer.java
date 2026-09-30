package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.AlchemyQuirk;
import com.radient.tensuraacadamia.entity.MoltenShield;
import com.radient.tensuraacadamia.regestry.AlchemyEntities;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AlchemyRenderer extends EntityRenderer<MoltenShield> {
    public AlchemyRenderer(EntityRendererProvider.Context context) { super(context); }
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AlchemyEntities.SHIELD.get(), AlchemyRenderer::new);
        event.registerEntityRenderer(AlchemyEntities.COIN.get(), ThrownItemRenderer::new);
    }
    @SubscribeEvent public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "iron_lock"), (graphics, delta) -> {
            var mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) return;
            var lock = mc.player.getEffect(MHAEffects.IRON_LOCK);
            if (lock == null) return;
            int x = graphics.guiWidth() / 2 - 50, y = graphics.guiHeight() / 2 + 25;
            graphics.fill(x - 3, y - 2, x + 103, y + 18, 0xB0000000);
            graphics.renderItem(Items.IRON_BARS.getDefaultInstance(), x, y);
            graphics.drawString(mc.font, Component.translatable("tracadamia.skill.alchemy.locked", (lock.getDuration() + 19) / 20), x + 20, y + 4, 0xFFE0E4E8);
        });
    }
    @Override public ResourceLocation getTextureLocation(MoltenShield wall) { return ResourceLocation.withDefaultNamespace("textures/block/gold_block.png"); }
    @Override public void render(MoltenShield wall, float yaw, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        var bounds = wall.getBoundingBox().move(-wall.getX(), -wall.getY(), -wall.getZ());
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poses.last().pose();
        int glow = 165 + (int) (Math.sin((wall.tickCount + partialTick) * 0.09) * 20);
        box(vertices, matrix, bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ, 255, glow, 20, 220);
        double w = 0.12;
        if (wall.normal().getAxis() == net.minecraft.core.Direction.Axis.Y) {
            for (double x = bounds.minX; x < bounds.maxX; x += 1)
                box(vertices, matrix, x, bounds.maxY, bounds.minZ, x + 0.035, bounds.maxY + 0.025, bounds.maxZ, 255, 235, 105, 255);
        } else {
            for (double y = bounds.minY; y < bounds.maxY; y += 1)
                box(vertices, matrix, bounds.minX - 0.015, y, bounds.minZ - 0.015, bounds.maxX + 0.015, y + 0.025, bounds.maxZ + 0.015, 255, 225, 80, 255);
            box(vertices, matrix, bounds.minX - w, bounds.minY, bounds.minZ - w, bounds.maxX + w, bounds.minY + w, bounds.maxZ + w, 255, 220, 65, 255);
            box(vertices, matrix, bounds.minX - w, bounds.maxY - w, bounds.minZ - w, bounds.maxX + w, bounds.maxY, bounds.maxZ + w, 255, 220, 65, 255);
        }
        super.render(wall, yaw, partialTick, poses, buffers, light);
    }
    private static void box(VertexConsumer vertices, Matrix4f m, double x, double y, double z, double X, double Y, double Z, int r, int g, int b, int a) {
        quad(vertices,m,x,y,z,X,y,z,X,Y,z,x,Y,z,r,g,b,a);
        quad(vertices,m,x,Y,Z,X,Y,Z,X,y,Z,x,y,Z,r,g,b,a);
        quad(vertices,m,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,r,g,b,a);
        quad(vertices,m,x,y,Z,X,y,Z,X,y,z,x,y,z,r,g,b,a);
        quad(vertices,m,x,y,z,x,Y,z,x,Y,Z,x,y,Z,r,g,b,a);
        quad(vertices,m,X,y,Z,X,Y,Z,X,Y,z,X,y,z,r,g,b,a);
    }
    private static void quad(VertexConsumer v, Matrix4f m, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, double x4, double y4, double z4, int r, int g, int b, int a) {
        v.addVertex(m,(float)x1,(float)y1,(float)z1).setColor(r,g,b,a);
        v.addVertex(m,(float)x2,(float)y2,(float)z2).setColor(r,g,b,a);
        v.addVertex(m,(float)x3,(float)y3,(float)z3).setColor(r,g,b,a);
        v.addVertex(m,(float)x4,(float)y4,(float)z4).setColor(r,g,b,a);
    }
    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
    public static final class Restraints {
        @SubscribeEvent public static void render(RenderLivingEvent.Post<?, ?> event) {
            var target = event.getEntity(); if (!target.isAlive() || !AlchemyQuirk.ironLocked(target)) return;
            var vertices = event.getMultiBufferSource().getBuffer(RenderType.lightning());
            var matrix = event.getPoseStack().last().pose();
            double half = target.getBbWidth() / 2 + 0.25, height = target.getBbHeight() + 0.25;
            for (int side = -1; side <= 1; side += 2) for (int column = -1; column <= 1; column++) {
                double p = column * half;
                box(vertices,matrix,p-0.045,0,side*half-0.045,p+0.045,height,side*half+0.045,160,165,172,255);
                box(vertices,matrix,side*half-0.045,0,p-0.045,side*half+0.045,height,p+0.045,160,165,172,255);
            }
            for (double y : new double[]{0.15, height * 0.55, height - 0.1}) {
                for (int side = -1; side <= 1; side += 2) {
                    box(vertices,matrix,-half,y,side*half-0.035,half,y+0.07,side*half+0.035,195,200,205,255);
                    box(vertices,matrix,side*half-0.035,y,-half,side*half+0.035,y+0.07,half,195,200,205,255);
                }
            }
        }
    }
}

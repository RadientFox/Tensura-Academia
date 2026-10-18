package com.radient.tensuraacadamia.client;

import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.TripWebDevice;
import com.radient.tensuraacadamia.entity.WebProjectile;
import com.radient.tensuraacadamia.regestry.SpiderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WebRenderer extends EntityRenderer<WebProjectile> {
    private static final int WEB = 0xF2F2F2;
    private static final int WEB_SHADE = 0xC8C8CC;
    private static final int SPARK = 0x7FE6FF;

    public WebRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SpiderRegistry.WEB.get(), WebRenderer::new);
        event.registerEntityRenderer(SpiderRegistry.TRIP_DEVICE.get(), TripRenderer::new);
    }

    @Override
    public boolean shouldRender(WebProjectile web, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(WebProjectile web) {
        return BlockyBox.TEXTURE;
    }

    @Override
    public void render(WebProjectile web, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        float t = web.tickCount + partial;
        float size = web.getKind() == WebProjectile.IMPACT ? 0.2F : 0.13F;
        Vec3 c = new Vec3(0.0D, web.getBbHeight() * 0.5D, 0.0D);
        Vec3 x = new Vec3(Mth.cos(t * 0.4F), 0.0D, Mth.sin(t * 0.4F));
        Vec3 z = new Vec3(-x.z, 0.0D, x.x);
        Vec3 y = new Vec3(0.0D, 1.0D, 0.0D);
        VertexConsumer buffer = BlockyBox.buffer(buffers);
        PoseStack.Pose pose = poses.last();
        BlockyBox.box(buffer, pose, c, x.scale(size), y.scale(size), z.scale(size), WEB, light);
        BlockyBox.box(buffer, pose, c, x.scale(size * 2.2D), y.scale(size * 0.2D), z.scale(size * 0.2D), WEB_SHADE, light);
        BlockyBox.box(buffer, pose, c, x.scale(size * 0.2D), y.scale(size * 2.2D), z.scale(size * 0.2D), WEB_SHADE, light);
        BlockyBox.box(buffer, pose, c, x.scale(size * 0.2D), y.scale(size * 0.2D), z.scale(size * 2.2D), WEB_SHADE, light);

        if (web.getKind() == WebProjectile.ELECTRIC) {
            Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(web.getPosition(partial));
            VFXHelper.Layers layers = new VFXHelper.Layers();
            VFXHelper.Billboards.glowDisc(layers.glow, null, c, cam, 0.35F, SPARK, 0.35F, 12);
            long flicker = (long) (t / 2.0F);
            for (int i = 0; i < 3; i++) {
                double angle = (flicker * 1.7D + i * 2.1D) % Mth.TWO_PI;
                Vec3 out = c.add(Math.cos(angle) * 0.4D, Math.sin(angle * 1.3D) * 0.3D, Math.sin(angle) * 0.4D);
                VFXHelper.Shapes.drawLightningArc(layers.glow, null, c, out, 0.08F, 2, 0.02F, VFXHelper.Colors.pack(0.9F, SPARK), web.getId() + flicker * 7L + i);
            }
            layers.emit(buffers, pose.pose());
        }
        super.render(web, yaw, partial, poses, buffers, light);
    }

    public static final class TripRenderer extends EntityRenderer<TripWebDevice> {
        private static final int BODY = 0x2E2C33;
        private static final int TRIM = 0x55525C;
        private static final int LENS = 0xE0202A;
        private static final int LINE = 0xFF3A40;

        public TripRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(TripWebDevice device, Frustum frustum, double x, double y, double z) {
            return true;
        }

        @Override
        public ResourceLocation getTextureLocation(TripWebDevice device) {
            return BlockyBox.TEXTURE;
        }

        @Override
        public void render(TripWebDevice device, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
            Vec3 origin = device.getPosition(partial);
            Vec3 c = new Vec3(0.0D, device.getBbHeight() * 0.5D, 0.0D);
            Vec3 lineEnd = device.getLineEnd().subtract(origin);
            Vec3 fwd = lineEnd.subtract(c).lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : lineEnd.subtract(c).normalize();
            Vec3 ref = Math.abs(fwd.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 side = fwd.cross(ref).normalize();
            Vec3 up = side.cross(fwd).normalize();

            VertexConsumer buffer = BlockyBox.buffer(buffers);
            PoseStack.Pose pose = poses.last();
            BlockyBox.box(buffer, pose, c, fwd.scale(0.08D), side.scale(0.14D), up.scale(0.14D), BODY, light);
            BlockyBox.box(buffer, pose, c.subtract(fwd.scale(0.06D)), fwd.scale(0.03D), side.scale(0.17D), up.scale(0.17D), TRIM, light);
            BlockyBox.box(buffer, pose, c.add(fwd.scale(0.09D)), fwd.scale(0.02D), side.scale(0.05D), up.scale(0.05D), LENS, LightTexture.FULL_BRIGHT);
            for (int i = 0; i < 4; i++) {
                double angle = i * Mth.HALF_PI + Mth.PI / 4.0D;
                Vec3 out = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                BlockyBox.box(buffer, pose, c.add(out.scale(0.19D)).subtract(fwd.scale(0.05D)), fwd.scale(0.02D), out.scale(0.07D), out.cross(fwd).normalize().scale(0.02D), TRIM, light);
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getId() == device.getOwnerId()) {
                Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(origin);
                VFXHelper.Layers layers = new VFXHelper.Layers();
                float pulse = 0.55F + 0.2F * Mth.sin((device.tickCount + partial) * 0.2F);
                VFXHelper.Billboards.streak(layers.glow, null, c, lineEnd, 0.03F, 0.03F, VFXHelper.Colors.pack(pulse, LINE), VFXHelper.Colors.pack(pulse * 0.6F, LINE), cam);
                VFXHelper.Billboards.glowDisc(layers.glow, null, c.add(fwd.scale(0.1D)), cam, 0.12F, LINE, 0.6F, 10);
                layers.emit(buffers, pose.pose());
            }
            super.render(device, yaw, partial, poses, buffers, light);
        }
    }
}

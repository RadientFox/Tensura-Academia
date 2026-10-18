package com.radient.tensuraacadamia.client;

import com.github.hvnbael.trnightmare.client.vfx.VFXHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.ScrappyThrustProjectile;
import com.radient.tensuraacadamia.regestry.SlideAndGlideEntities;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ScrappyThrustRenderer extends EntityRenderer<ScrappyThrustProjectile> {
    private static final float RADIUS = 0.22F;
    private static final int CORE = 0xE6FFFF;
    private static final int GLOW = 0x3FE6FF;

    public ScrappyThrustRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SlideAndGlideEntities.SCRAPPY_THRUST.get(), ScrappyThrustRenderer::new);
    }

    @Override
    public boolean shouldRender(ScrappyThrustProjectile ball, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ScrappyThrustProjectile ball) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(ScrappyThrustProjectile ball, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 center = new Vec3(0.0D, ball.getBbHeight() * 0.5D, 0.0D);
        Vec3 cam = this.entityRenderDispatcher.camera.getPosition().subtract(ball.getPosition(partial));
        float t = ball.tickCount + partial;
        float grow = VFXHelper.MathHelper.smoothstep(0.0F, 2.0F, t);
        VFXHelper.Layers layers = new VFXHelper.Layers();
        VFXHelper.Firing.energyBall(layers, null, cam, center, ball.getDeltaMovement(), t, RADIUS * (0.6F + 0.4F * grow), CORE, GLOW, 1.0F, ball.getId());
        layers.emit(buffers, pose.last().pose());
        super.render(ball, yaw, partial, pose, buffers, light);
    }
}

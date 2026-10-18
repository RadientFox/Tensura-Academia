package com.radient.tensuraacadamia.quirks.cloud;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

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
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(cloud.getPosition(partialTick));
        float age = cloud.tickCount + partialTick;
        if (cloud.kind() == QuirkCloud.RIDE) {
            ride(poses, buffers, camera, age, 1);
        } else if (cloud.kind() == QuirkCloud.BLIND) {
            ride(poses, buffers, camera, age, .4);
        } else {
            int detail = SkillVfx.detail(camera, new AABB(-50, 0, -50, 50, 50, 50));
            if (detail == 0) return;
            int columns = detail > 8 ? 4 : 3;
            double spacing = columns == 4 ? 24 : 31, radius = columns == 4 ? 14 : 19;
            for (int layer = 0; layer < 3; layer++) for (int x = 0; x < columns; x++) for (int z = 0; z < columns; z++) {
                Vec3 center = new Vec3((x - (columns - 1) * .5) * spacing,
                        10 + layer * 15 + Math.sin(age * .035 + x + z) * .4, (z - (columns - 1) * .5) * spacing);
                SkillVfx.billow(poses, buffers, camera, center, radius, 10, layer == 0 ? 0x8191A9 : 0xDCE5F1,
                        .42F, Math.sin(x * 2.3 + z) * .2);
            }
        }
        super.render(cloud, yaw, partialTick, poses, buffers, light);
    }

    private static void ride(PoseStack poses, MultiBufferSource buffers, Vec3 camera, float age, double size) {
        if (SkillVfx.detail(camera, Vec3.ZERO) == 0) return;
        SkillVfx.billow(poses, buffers, camera, new Vec3(0, .15 * size, 0), 1.6 * size, .55 * size, 0xB2C0D5, .85F, 0);
        int lobes = SkillVfx.detail(camera, Vec3.ZERO) > 8 ? 8 : 5;
        for (int lobe = 0; lobe < lobes; lobe++) {
            double angle = lobe * Math.PI * 2 / lobes;
            Vec3 center = new Vec3(Math.cos(angle) * 1.05 * size,
                    (.4 + Math.sin(age * .025 + lobe) * .035) * size, Math.sin(angle) * .75 * size);
            SkillVfx.billow(poses, buffers, camera, center, .75 * size, (.45 + lobe % 2 * .12) * size,
                    0xF4F7FF, .78F, Math.sin(lobe * 1.7) * .15);
        }
    }
}

package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.FierceWingsQuirk;
import com.radient.tensuraacadamia.entity.FeatherProjectile;
import com.radient.tensuraacadamia.regestry.FierceWingsEntities;
import com.radient.tensuraacadamia.regestry.FierceWingsItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FeatherRenderer extends EntityRenderer<FeatherProjectile> {
    private static final int QUILL = 0xF0D8C8;

    public FeatherRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FierceWingsEntities.FEATHER.get(), FeatherRenderer::new);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, FierceWingsQuirk.DEFAULT_COLOR)) : -1,
                FierceWingsItems.FEATHER_BLADE.get(), FierceWingsItems.ADAMANTITE_FEATHER_BLADE.get(), FierceWingsItems.HIHIIROKANE_FEATHER_BLADE.get());
    }

    @Override
    public boolean shouldRender(FeatherProjectile feather, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(FeatherProjectile feather) {
        return BlockyBox.TEXTURE;
    }

    @Override
    public void render(FeatherProjectile feather, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 moved = new Vec3(feather.getX() - feather.xo, feather.getY() - feather.yo, feather.getZ() - feather.zo);
        Vec3 fwd = moved.lengthSqr() < 1.0E-6D ? feather.getLookAngle() : moved.normalize();
        Vec3 ref = Math.abs(fwd.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = fwd.cross(ref).normalize();
        Vec3 up = side.cross(fwd).normalize();
        float roll = (feather.tickCount + partial) * 0.6F;
        Vec3 s = side.scale(Mth.cos(roll)).add(up.scale(Mth.sin(roll)));
        Vec3 u = fwd.cross(s).normalize();

        Vec3 c = new Vec3(0.0D, feather.getBbHeight() * 0.5D, 0.0D);
        VertexConsumer buffer = BlockyBox.buffer(buffers);
        PoseStack.Pose pose = poses.last();
        int vane = feather.getColor();
        BlockyBox.box(buffer, pose, c, fwd.scale(0.32D), s.scale(0.02D), u.scale(0.02D), shade(vane, 0.0F, 0.52F), light);
        BlockyBox.box(buffer, pose, c.subtract(fwd.scale(0.05D)), fwd.scale(0.22D), s.scale(0.09D), u.scale(0.012D), vane, light);
        BlockyBox.box(buffer, pose, c.add(fwd.scale(0.05D)), fwd.scale(0.12D), s.scale(0.12D), u.scale(0.014D), shade(vane, 0.1F, 1.0F), light);
        BlockyBox.box(buffer, pose, c.add(fwd.scale(0.26D)), fwd.scale(0.06D), s.scale(0.05D), u.scale(0.012D), shade(vane, 0.22F, 1.0F), light);
        BlockyBox.box(buffer, pose, c.subtract(fwd.scale(0.32D)), fwd.scale(0.06D), s.scale(0.015D), u.scale(0.015D), QUILL, light);
        super.render(feather, yaw, partial, poses, buffers, light);
    }

    private static int shade(int rgb, float lighten, float scale) {
        int r = (int) Mth.lerp(lighten, ((rgb >> 16) & 0xFF) * scale, 255.0F);
        int g = (int) Mth.lerp(lighten, ((rgb >> 8) & 0xFF) * scale, 255.0F);
        int b = (int) Mth.lerp(lighten, (rgb & 0xFF) * scale, 255.0F);
        return (r << 16) | (g << 8) | b;
    }
}

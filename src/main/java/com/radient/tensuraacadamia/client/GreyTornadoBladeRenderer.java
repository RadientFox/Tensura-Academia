package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.GreyTornadoBlade;
import com.radient.tensuraacadamia.regestry.GreyTornadoEntities;
import io.github.manasmods.tensura.client.entity.projectile.magic.MagicTornadoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import java.io.InputStream;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GreyTornadoBladeRenderer {
    private static final ResourceLocation ORIGINAL = ResourceLocation.fromNamespaceAndPath(
            "tensura", "textures/entity/misc/wind_tornado.png");

    private GreyTornadoBladeRenderer() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GreyTornadoEntities.TORNADO_BLADE.get(), context -> {
            registerGreyTexture(Minecraft.getInstance().getResourceManager());
            return new MagicTornadoRenderer<GreyTornadoBlade>(context);
        });
    }

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GreyTornadoBladeRenderer::registerGreyTexture);
    }

    private static void registerGreyTexture(ResourceManager resources) {
        try (InputStream stream = resources.open(ORIGINAL)) {
            NativeImage image = NativeImage.read(stream);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = pixel & 255;
                    int green = (pixel >>> 8) & 255;
                    int blue = (pixel >>> 16) & 255;
                    int grey = (red * 30 + green * 59 + blue * 11) / 100;
                    image.setPixelRGBA(x, y, (pixel & 0xff000000) | (grey << 16) | (grey << 8) | grey);
                }
            }
            Minecraft.getInstance().getTextureManager().register(
                    GreyTornadoBlade.GREY_TEXTURE, new DynamicTexture(image));
        } catch (IOException exception) {
            TensuraAcadamia.LOGGER.error("Could not load Tensura's Tornado Blade texture", exception);
        }
    }
}

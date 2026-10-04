package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class WhiteTextures {

    private static final int MAX_SIZE = 1024;
    private static final int BASE = 222;
    private static final int RANGE = 33;
    private static final int COOL_TINT = 6;

    private static final Map<ResourceLocation, ResourceLocation> WHITENED = new HashMap<>();

    private WhiteTextures() {
    }

    public static ResourceLocation get(ResourceLocation source) {
        ResourceLocation white = WHITENED.get(source);
        if (white == null) {
            white = create(source);
            WHITENED.put(source, white);
        }

        return white;
    }

    private static ResourceLocation create(ResourceLocation source) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "whitened/" + source.getNamespace() + "/" + source.getPath());
        Minecraft minecraft = Minecraft.getInstance();
        try (InputStream stream = minecraft.getResourceManager().open(source); NativeImage original = NativeImage.read(stream)) {
            minecraft.getTextureManager().register(id, new DynamicTexture(whiten(original)));
            return id;
        } catch (IOException exception) {
            TensuraAcadamia.LOGGER.warn("Couldn't whiten {}", source, exception);
            return source;
        }
    }

    private static NativeImage whiten(NativeImage original) {
        int step = 1;
        while (original.getWidth() / step > MAX_SIZE || original.getHeight() / step > MAX_SIZE) {
            step *= 2;
        }

        int width = Math.max(1, original.getWidth() / step);
        int height = Math.max(1, original.getHeight() / step);
        NativeImage white = new NativeImage(width, height, true);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                long alpha = 0L;
                long light = 0L;
                for (int dx = 0; dx < step; dx++) {
                    for (int dy = 0; dy < step; dy++) {
                        int color = original.getPixelRGBA(x * step + dx, y * step + dy);
                        int a = color >>> 24 & 255;
                        int b = color >> 16 & 255;
                        int g = color >> 8 & 255;
                        int r = color & 255;
                        alpha += a;
                        light += (long) (0.299D * r + 0.587D * g + 0.114D * b) * a;
                    }
                }

                int a = (int) (alpha / (step * step));
                int grey = alpha == 0L ? BASE : BASE + (int) (light / alpha) * RANGE / 255;
                int blue = Math.min(255, grey + COOL_TINT);
                white.setPixelRGBA(x, y, a << 24 | blue << 16 | grey << 8 | grey);
            }
        }

        return white;
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            WHITENED.values().stream().filter(id -> id.getNamespace().equals(TensuraAcadamia.MODID)).forEach(textures::release);
            WHITENED.clear();
        });
    }

}

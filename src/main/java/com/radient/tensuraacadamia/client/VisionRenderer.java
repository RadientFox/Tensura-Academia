package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

// Shared screen effects for vision quirks
public final class VisionRenderer {

    private static final Map<ResourceLocation, Chain> CHAINS = new HashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    private static final Map<RenderType, RenderType> MASK_TYPES = new HashMap<>();
    private static final Map<RenderType, RenderType> XRAY_TYPES = new HashMap<>();

    private static final TintBufferSource MASK_BUFFERS = new TintBufferSource(VisionRenderer::getMaskType);
    private static final TintBufferSource XRAY_BUFFERS = new TintBufferSource(VisionRenderer::getXrayType);
    private static final MultiBufferSource.BufferSource BOX_BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(262144));

    private static @Nullable RenderTarget maskTarget;

    private static final RenderType BOX_TYPE = new RenderType("tracadamia_vision_boxes", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            () -> {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                if (maskTarget != null) {
                    maskTarget.bindWrite(false);
                }
            },
            () -> {
                RenderSystem.enableCull();
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }) {
    };

    private static final class Chain {
        private final PostChain chain;
        private int width = -1;
        private int height = -1;

        private Chain(PostChain chain) {
            this.chain = chain;
        }
    }

    private VisionRenderer() {
    }

    public static @Nullable PostChain getChain(ResourceLocation id) {
        if (FAILED.contains(id)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        Chain chain = CHAINS.get(id);
        if (chain == null) {
            try {
                chain = new Chain(new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), main, id));
            } catch (IOException | RuntimeException exception) {
                TensuraAcadamia.LOGGER.error("Failed to load the {} shader", id, exception);
                FAILED.add(id);
                return null;
            }

            CHAINS.put(id, chain);
        }

        if (chain.width != main.width || chain.height != main.height) {
            chain.chain.resize(main.width, main.height);
            chain.width = main.width;
            chain.height = main.height;
        }

        return chain.chain;
    }

    public static void process(RenderLevelStageEvent event, PostChain chain) {
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    public static boolean renderMask(RenderLevelStageEvent event, PostChain chain, boolean throughWalls, Predicate<LivingEntity> filter, ToIntFunction<LivingEntity> color) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.levelRenderer.entityTarget() == null) {
            return false;
        }

        RenderTarget main = minecraft.getMainRenderTarget();
        RenderTarget mask = chain.getTempTarget("mask");
        mask.clear(Minecraft.ON_OSX);
        if (!throughWalls) {
            mask.copyDepthFrom(main);
        }

        mask.bindWrite(false);
        maskTarget = mask;

        TintBufferSource buffers = throughWalls ? XRAY_BUFFERS : MASK_BUFFERS;
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        PoseStack poseStack = new PoseStack();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || !filter.test(living) || !dispatcher.shouldRender(entity, event.getFrustum(), cameraPos.x, cameraPos.y, cameraPos.z)) {
                continue;
            }

            if (entity == camera.getEntity() && !camera.isDetached()) {
                continue;
            }

            buffers.color = color.applyAsInt(living);
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity));
            double x = Mth.lerp(partialTick, entity.xOld, entity.getX()) - cameraPos.x;
            double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) - cameraPos.y;
            double z = Mth.lerp(partialTick, entity.zOld, entity.getZ()) - cameraPos.z;
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            dispatcher.render(entity, x, y, z, yaw, partialTick, poseStack, buffers, dispatcher.getPackedLightCoords(entity, partialTick));
        }

        buffers.endBatch();
        main.bindWrite(false);
        return true;
    }

    public static void renderMaskBoxes(PostChain chain, Consumer<VertexConsumer> boxes) {
        maskTarget = chain.getTempTarget("mask");
        boxes.accept(BOX_BUFFERS.getBuffer(BOX_TYPE));
        BOX_BUFFERS.endBatch();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }


    private static RenderType getMaskType(RenderType outline) {
        return MASK_TYPES.computeIfAbsent(outline, type -> new RenderType("tracadamia_vision_mask", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                () -> {
                    type.setupRenderState();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.depthMask(false);
                    RenderSystem.enablePolygonOffset();
                    RenderSystem.polygonOffset(-1.0F, -10.0F);
                    if (maskTarget != null) {
                        maskTarget.bindWrite(false);
                    }
                },
                () -> {
                    RenderSystem.polygonOffset(0.0F, 0.0F);
                    RenderSystem.disablePolygonOffset();
                    type.clearRenderState();
                    RenderSystem.depthMask(true);
                }) {
        });
    }

    private static RenderType getXrayType(RenderType outline) {
        return XRAY_TYPES.computeIfAbsent(outline, type -> new RenderType("tracadamia_vision_xray", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                () -> {
                    type.setupRenderState();
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                    if (maskTarget != null) {
                        maskTarget.bindWrite(false);
                    }
                },
                () -> {
                    type.clearRenderState();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(true);
                }) {
        });
    }

    private static final class TintBufferSource extends MultiBufferSource.BufferSource {
        private final Function<RenderType, RenderType> wrap;
        private int color;

        private TintBufferSource(Function<RenderType, RenderType> wrap) {
            super(new ByteBufferBuilder(262144), new LinkedHashMap<>());
            this.wrap = wrap;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            Optional<RenderType> outline = type.isOutline() ? Optional.of(type) : type.outline();
            if (outline.isEmpty()) {
                return DiscardVertexConsumer.INSTANCE;
            }

            return new TintVertexConsumer(super.getBuffer(this.wrap.apply(outline.get())), this.color);
        }
    }

    // Keeps the position and texture for the cutout, the color is fixed
    private record TintVertexConsumer(VertexConsumer delegate, int color) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z).setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private enum DiscardVertexConsumer implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

}

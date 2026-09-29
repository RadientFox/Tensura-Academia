package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.MoleQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class MoleClient {

    private static final int SCAN_INTERVAL = 20;
    private static final int MAX_HIGHLIGHTS = 512;
    private static final float FILL_ALPHA = 0.2F;
    private static final float EDGE_ALPHA = 0.9F;

    private record Highlight(BlockPos pos, int color) {}

    private static List<Highlight> highlights = List.of();

    // Ore Affinity
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !MoleQuirk.hasOreAffinity(player)) {
            highlights = List.of();
            return;
        }

        if (player.tickCount % SCAN_INTERVAL == 0) {
            highlights = scan(minecraft.level, player.blockPosition(), MoleQuirk.getOreRange());
        }
    }

    // Skips chunk sections whose palette has none of the ores
    private static List<Highlight> scan(ClientLevel level, BlockPos center, int range) {
        List<Highlight> found = new ArrayList<>();
        long rangeSqr = (long) range * range;

        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - range); chunkX <= SectionPos.blockToSectionCoord(center.getX() + range); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - range); chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + range); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(index));
                    if (section == null || section.hasOnlyAir() || baseY + 15 < center.getY() - range || baseY > center.getY() + range || !section.maybeHas(MoleQuirk::isAffinityOre)) {
                        continue;
                    }

                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                int color = MoleQuirk.getAffinityColor(state);
                                if (color == -1) {
                                    continue;
                                }

                                BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(chunkX) + x, baseY + y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                                if (pos.distSqr(center) <= rangeSqr) {
                                    found.add(new Highlight(pos, color));
                                }
                            }
                        }
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(highlight -> highlight.pos().distSqr(center)));
        return found.size() > MAX_HIGHLIGHTS ? List.copyOf(found.subList(0, MAX_HIGHLIGHTS)) : found;
    }

    // Drawing box
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || highlights.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Highlight highlight : highlights) {
            addFaces(fill, matrix, highlight, camera);
        }
        draw(fill);

        BufferBuilder edges = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (Highlight highlight : highlights) {
            addEdges(edges, matrix, highlight, camera);
        }
        draw(edges);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void draw(BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    private static void addFaces(BufferBuilder builder, Matrix4f matrix, Highlight highlight, Vec3 camera) {
        float x0 = (float) (highlight.pos().getX() - camera.x);
        float y0 = (float) (highlight.pos().getY() - camera.y);
        float z0 = (float) (highlight.pos().getZ() - camera.z);
        float x1 = x0 + 1.0F;
        float y1 = y0 + 1.0F;
        float z1 = z0 + 1.0F;
        float r = (highlight.color() >> 16 & 255) / 255.0F;
        float g = (highlight.color() >> 8 & 255) / 255.0F;
        float b = (highlight.color() & 255) / 255.0F;

        quad(builder, matrix, r, g, b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(builder, matrix, r, g, b, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(builder, matrix, r, g, b, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(builder, matrix, r, g, b, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(builder, matrix, r, g, b, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(builder, matrix, r, g, b, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, float r, float g, float b, float... corners) {
        for (int i = 0; i < corners.length; i += 3) {
            builder.addVertex(matrix, corners[i], corners[i + 1], corners[i + 2]).setColor(r, g, b, FILL_ALPHA);
        }
    }

    private static void addEdges(BufferBuilder builder, Matrix4f matrix, Highlight highlight, Vec3 camera) {
        float x0 = (float) (highlight.pos().getX() - camera.x);
        float y0 = (float) (highlight.pos().getY() - camera.y);
        float z0 = (float) (highlight.pos().getZ() - camera.z);
        float x1 = x0 + 1.0F;
        float y1 = y0 + 1.0F;
        float z1 = z0 + 1.0F;
        float r = (highlight.color() >> 16 & 255) / 255.0F;
        float g = (highlight.color() >> 8 & 255) / 255.0F;
        float b = (highlight.color() & 255) / 255.0F;

        float[][] lines = {
                {x0, y0, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z0, x1, y1, z0}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}
        };

        for (float[] line : lines) {
            builder.addVertex(matrix, line[0], line[1], line[2]).setColor(r, g, b, EDGE_ALPHA);
            builder.addVertex(matrix, line[3], line[4], line[5]).setColor(r, g, b, EDGE_ALPHA);
        }
    }

}

package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.VoyanceQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class VoyanceClient {

    private static final ResourceLocation VOYANCE_VISION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "shaders/post/voyance_vision.json");

    private static final int LOOT = 0xFFFFC83A;
    private static final int TRAP = 0xFFFF3B3B;
    private static final int LAVA = 0xFFFF8A1E;
    private static final int REDSTONE = 0xFFB0001E;
    private static final int SCAN_TICKS = 20;
    private static final int MAX_HIGHLIGHTS = 2048;
    private static final float INSET = 0.02F;

    private record Highlight(BlockPos pos, int color) {}

    private static List<Highlight> highlights = List.of();
    private static boolean maskReady;

    private VoyanceClient() {
    }

    private static boolean isActive(LocalPlayer player) {
        return VoyanceQuirk.getVoyance(player).filter(VoyanceQuirk::isActive).isPresent();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !isActive(minecraft.player)) {
            maskReady = false;
            return;
        }

        PostChain chain = VisionRenderer.getChain(VOYANCE_VISION);
        if (chain == null) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            maskReady = VisionRenderer.renderMask(event, chain, true, entity -> entity.getType().is(TensuraEntityTags.TRAP_ENTITY) || entity.getType().is(TensuraEntityTags.TREASURE_ENTITY),
                    entity -> entity.getType().is(TensuraEntityTags.TRAP_ENTITY) ? TRAP : LOOT);
            if (maskReady) {
                Vec3 camera = event.getCamera().getPosition();
                VisionRenderer.renderMaskBoxes(chain, consumer -> highlights.forEach(highlight -> renderBox(consumer, highlight, camera)));
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (maskReady) {
                VisionRenderer.process(event, chain);
            }

            maskReady = false;
        }
    }

    private static void renderBox(VertexConsumer consumer, Highlight highlight, Vec3 camera) {
        float x0 = (float) (highlight.pos().getX() - camera.x) + INSET;
        float y0 = (float) (highlight.pos().getY() - camera.y) + INSET;
        float z0 = (float) (highlight.pos().getZ() - camera.z) + INSET;
        float x1 = x0 + 1.0F - INSET * 2.0F;
        float y1 = y0 + 1.0F - INSET * 2.0F;
        float z1 = z0 + 1.0F - INSET * 2.0F;
        int color = highlight.color();
        quad(consumer, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(consumer, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(consumer, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(consumer, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer consumer, int color, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        consumer.addVertex(ax, ay, az).setColor(color);
        consumer.addVertex(bx, by, bz).setColor(color);
        consumer.addVertex(cx, cy, cz).setColor(color);
        consumer.addVertex(dx, dy, dz).setColor(color);
    }

    // Loot containers, traps and lava
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !isActive(player)) {
            highlights = List.of();
            return;
        }

        if (player.tickCount % SCAN_TICKS != 0 && !highlights.isEmpty()) {
            return;
        }

        ManasSkillInstance voyance = VoyanceQuirk.getVoyance(player).orElse(null);
        int range = voyance == null || voyance.getTag() == null ? 24 : Math.max(1, voyance.getTag().getInt(VoyanceQuirk.RANGE_TAG));
        BlockPos center = player.blockPosition();
        List<Highlight> found = new ArrayList<>();
        int minSection = SectionPos.blockToSectionCoord(center.getY() - range);
        int maxSection = SectionPos.blockToSectionCoord(center.getY() + range);
        for (int chunkX = SectionPos.blockToSectionCoord(center.getX() - range); chunkX <= SectionPos.blockToSectionCoord(center.getX() + range); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(center.getZ() - range); chunkZ <= SectionPos.blockToSectionCoord(center.getZ() + range); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof RandomizableContainer && !isTrap(blockEntity.getBlockState()) && blockEntity.getBlockPos().distSqr(center) <= range * range) {
                        found.add(new Highlight(blockEntity.getBlockPos(), LOOT));
                    }
                }

                for (int sectionY = Math.max(minSection, chunk.getMinSection()); sectionY <= Math.min(maxSection, chunk.getMaxSection() - 1); sectionY++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    if (section.hasOnlyAir() || !section.maybeHas(VoyanceClient::isSeen)) {
                        continue;
                    }

                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!isSeen(state)) {
                                    continue;
                                }

                                BlockPos pos = new BlockPos(SectionPos.sectionToBlockCoord(chunkX) + x, SectionPos.sectionToBlockCoord(sectionY) + y, SectionPos.sectionToBlockCoord(chunkZ) + z);
                                if (pos.distSqr(center) <= range * range) {
                                    found.add(new Highlight(pos, isTrap(state) ? TRAP : isRedstone(state) ? REDSTONE : LAVA));
                                }
                            }
                        }
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(highlight -> highlight.pos().distSqr(center)));
        highlights = found.size() > MAX_HIGHLIGHTS ? List.copyOf(found.subList(0, MAX_HIGHLIGHTS)) : List.copyOf(found);
    }

    private static boolean isTrap(BlockState state) {
        return state.is(BlockTags.PRESSURE_PLATES) || state.is(Blocks.TRIPWIRE) || state.is(Blocks.TRIPWIRE_HOOK) || state.is(Blocks.TNT) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.DISPENSER);
    }

    private static boolean isLava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }

    private static boolean isRedstone(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
    }

    private static boolean isSeen(BlockState state) {
        return isTrap(state) || isRedstone(state) || isLava(state);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        highlights = List.of();
    }

}

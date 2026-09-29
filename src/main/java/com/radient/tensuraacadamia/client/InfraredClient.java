package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.InfraredQuirk;
import io.github.manasmods.tensura.data.TensuraBlockTags;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class InfraredClient {

    private static final ResourceLocation INFRARED_VISION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "shaders/post/infrared_vision.json");

    private static final Predicate<BlockState> HEAT_SOURCE = state -> state.is(TensuraBlockTags.HEAT_SOURCE_BLOCKS) && state.getLightEmission() >= 3;
    private static final int HEAT_COLOR = 0xFFFF0000;
    private static final int SCAN_BUDGET = 512;
    private static final int FULL_SCAN_COST = 64;
    private static final int MAX_HEAT_BLOCKS = 8192;

    private static final LongArrayFIFOQueue SCAN_QUEUE = new LongArrayFIFOQueue();
    private static final Long2ObjectOpenHashMap<long[]> SECTION_HEAT = new Long2ObjectOpenHashMap<>();

    private static float[] heatVertices = new float[0];
    private static BlockPos meshOrigin = BlockPos.ZERO;
    private static @Nullable ClientLevel scannedLevel;
    private static boolean maskReady;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || !InfraredQuirk.isActive(minecraft.player)) {
            maskReady = false;
            return;
        }

        PostChain chain = VisionRenderer.getChain(INFRARED_VISION);
        if (chain == null) {
            return;
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            Set<Integer> signatures = InfraredQuirk.getClientSignatures();
            maskReady = VisionRenderer.renderMask(event, chain, true, entity -> signatures.contains(entity.getId()), entity -> HEAT_COLOR);
            if (maskReady) {
                renderHeatSources(event, chain, minecraft.level, signatures);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (maskReady) {
                VisionRenderer.process(event, chain);
            }

            maskReady = false;
        }
    }

    private static void renderHeatSources(RenderLevelStageEvent event, PostChain chain, ClientLevel level, Set<Integer> signatures) {
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float x = (float) (meshOrigin.getX() - camera.x);
        float y = (float) (meshOrigin.getY() - camera.y);
        float z = (float) (meshOrigin.getZ() - camera.z);
        float[] vertices = heatVertices;

        VisionRenderer.renderMaskBoxes(chain, consumer -> {
            for (int i = 0; i < vertices.length; i += 3) {
                consumer.addVertex(vertices[i] + x, vertices[i + 1] + y, vertices[i + 2] + z).setColor(HEAT_COLOR);
            }

            for (int id : signatures) {
                Entity entity = level.getEntity(id);
                if (entity != null && !(entity instanceof LivingEntity)) {
                    Vec3 offset = entity.getPosition(partialTick).subtract(camera).subtract(entity.position());
                    addBox(consumer, entity.getBoundingBox().move(offset));
                }
            }
        });
    }

    private static void addBox(VertexConsumer consumer, AABB box) {
        FloatArrayList vertices = new FloatArrayList();
        for (Direction direction : Direction.values()) {
            addFace(vertices, box, direction);
        }

        for (int i = 0; i < vertices.size(); i += 3) {
            consumer.addVertex(vertices.getFloat(i), vertices.getFloat(i + 1), vertices.getFloat(i + 2)).setColor(HEAT_COLOR);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !InfraredQuirk.isActive(player) || level.dimensionType().ultraWarm() || InfraredQuirk.getClientRange() <= 0.0D) {
            clearHeat();
            return;
        }

        if (level != scannedLevel) {
            clearHeat();
            scannedLevel = level;
        }

        if (SCAN_QUEUE.isEmpty()) {
            rebuildMesh(level, player);
            queueSections(level, player);
        }

        int budget = SCAN_BUDGET;
        while (budget > 0 && !SCAN_QUEUE.isEmpty()) {
            budget -= scanSection(level, SCAN_QUEUE.dequeueLong());
        }
    }

    private static void clearHeat() {
        SCAN_QUEUE.clear();
        SECTION_HEAT.clear();
        heatVertices = new float[0];
        scannedLevel = null;
    }

    private static void queueSections(ClientLevel level, LocalPlayer player) {
        double range = InfraredQuirk.getClientRange();
        int radius = Mth.ceil(range / 16.0D);
        SectionPos center = SectionPos.of(player);
        LongOpenHashSet queued = new LongOpenHashSet();
        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
            for (int z = center.z() - radius; z <= center.z() + radius; z++) {
                for (int y = Math.max(level.getMinSection(), center.y() - radius); y <= Math.min(level.getMaxSection() - 1, center.y() + radius); y++) {
                    AABB section = new AABB(x << 4, y << 4, z << 4, (x << 4) + 16, (y << 4) + 16, (z << 4) + 16);
                    if (section.distanceToSqr(player.position()) <= range * range) {
                        long key = SectionPos.asLong(x, y, z);
                        queued.add(key);
                        SCAN_QUEUE.enqueue(key);
                    }
                }
            }
        }

        SECTION_HEAT.keySet().removeIf((long key) -> !queued.contains(key));
    }

    private static int scanSection(ClientLevel level, long key) {
        int sectionX = SectionPos.x(key);
        int sectionY = SectionPos.y(key);
        int sectionZ = SectionPos.z(key);
        LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, false);
        int index = level.getSectionIndexFromSectionY(sectionY);
        if (chunk == null || index < 0 || index >= chunk.getSections().length) {
            SECTION_HEAT.remove(key);
            return 1;
        }

        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir() || !section.maybeHas(HEAT_SOURCE)) {
            SECTION_HEAT.remove(key);
            return 1;
        }

        LongArrayList found = new LongArrayList();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (HEAT_SOURCE.test(section.getBlockState(x, y, z))) {
                        found.add(BlockPos.asLong((sectionX << 4) + x, (sectionY << 4) + y, (sectionZ << 4) + z));
                    }
                }
            }
        }

        if (found.isEmpty()) {
            SECTION_HEAT.remove(key);
        } else {
            SECTION_HEAT.put(key, found.toLongArray());
        }

        return FULL_SCAN_COST;
    }

    private static void rebuildMesh(ClientLevel level, LocalPlayer player) {
        double range = InfraredQuirk.getClientRange();
        Vec3 eye = player.getEyePosition();
        List<BlockPos> blocks = new ArrayList<>();
        for (long[] positions : SECTION_HEAT.values()) {
            for (long pos : positions) {
                BlockPos blockPos = BlockPos.of(pos);
                if (Vec3.atCenterOf(blockPos).distanceToSqr(eye) <= range * range) {
                    blocks.add(blockPos);
                }
            }
        }

        if (blocks.size() > MAX_HEAT_BLOCKS) {
            blocks.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
            blocks = blocks.subList(0, MAX_HEAT_BLOCKS);
        }

        List<List<AABB>> shapes = new ArrayList<>(blocks.size());
        LongOpenHashSet full = new LongOpenHashSet();
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            List<AABB> boxes = HEAT_SOURCE.test(state) ? getHeatBoxes(level, pos, state) : List.of();
            shapes.add(boxes);
            if (boxes.size() == 1 && isFullCube(boxes.get(0))) {
                full.add(pos.asLong());
            }
        }

        meshOrigin = player.blockPosition();
        FloatArrayList vertices = new FloatArrayList();
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            boolean isFull = full.contains(pos.asLong());
            for (AABB box : shapes.get(i)) {
                AABB placed = box.move(pos.getX() - meshOrigin.getX(), pos.getY() - meshOrigin.getY(), pos.getZ() - meshOrigin.getZ());
                for (Direction direction : Direction.values()) {
                    if (!isFull || !full.contains(pos.relative(direction).asLong())) {
                        addFace(vertices, placed, direction);
                    }
                }
            }
        }

        heatVertices = vertices.toFloatArray();
    }

    private static List<AABB> getHeatBoxes(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.LAVA) && state.getShape(level, pos).isEmpty()) {
            float height = Math.max(0.15F, state.getFluidState().getHeight(level, pos));
            return List.of(new AABB(0.0D, 0.0D, 0.0D, 1.0D, height >= 0.85F ? 1.0D : height, 1.0D));
        }

        if (state.is(BlockTags.FIRE)) {
            return List.of(new AABB(0.05D, 0.0D, 0.05D, 0.95D, 0.9D, 0.95D));
        }

        VoxelShape shape = state.getShape(level, pos);
        return shape.isEmpty() ? List.of(Block.box(2.0D, 0.0D, 2.0D, 14.0D, 14.0D, 14.0D).bounds()) : shape.toAabbs();
    }

    private static boolean isFullCube(AABB box) {
        return box.minX <= 0.0D && box.minY <= 0.0D && box.minZ <= 0.0D && box.maxX >= 1.0D && box.maxY >= 1.0D && box.maxZ >= 1.0D;
    }

    private static void addFace(FloatArrayList vertices, AABB box, Direction direction) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        float[] face = switch (direction) {
            case DOWN -> new float[]{minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ};
            case UP -> new float[]{minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ};
            case NORTH -> new float[]{minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ};
            case SOUTH -> new float[]{minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ};
            case WEST -> new float[]{minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ};
            case EAST -> new float[]{maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ};
        };
        vertices.addElements(vertices.size(), face);
    }

}

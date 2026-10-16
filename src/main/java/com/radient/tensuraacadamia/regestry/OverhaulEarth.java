package com.radient.tensuraacadamia.regestry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OverhaulEarth extends Block {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "tracadamia");
    public static final DeferredHolder<Block, OverhaulEarth> EARTH = BLOCKS.register("overhaul_earth", () -> new OverhaulEarth(false));
    public static final DeferredHolder<Block, OverhaulEarth> SPIKE = BLOCKS.register("overhaul_spike", () -> new OverhaulEarth(true));
    private final boolean spike;

    private OverhaulEarth(boolean spike) { super(Properties.ofFullCopy(Blocks.STONE).noLootTable().noOcclusion()); this.spike = spike; }
    public static void register(IEventBus bus) { BLOCKS.register(bus); }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    public static boolean place(ServerLevel level, BlockPos pos, int lifetime) {
        return place(level, pos, lifetime, false);
    }
    public static boolean place(ServerLevel level, BlockPos pos, int lifetime, boolean spike) {
        if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir()) return false;
        OverhaulEarth block = spike ? SPIKE.get() : EARTH.get();
        if (!level.setBlock(pos, block.defaultBlockState(), 3)) return false;
        level.scheduleTick(pos, block, lifetime);
        return true;
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return spike ? Block.box(2, 0, 2, 14, 16, 14) : super.getShape(state, level, pos, context);
    }
}

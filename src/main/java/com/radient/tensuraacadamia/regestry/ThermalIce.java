package com.radient.tensuraacadamia.regestry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Scheduled block ticks persist across saves and chunk unloads. No terrain is replaced. */
public final class ThermalIce extends Block {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "tracadamia");
    public static final DeferredHolder<Block, ThermalIce> ICE = BLOCKS.register("thermal_ice", ThermalIce::new);

    private ThermalIce() {
        super(Properties.ofFullCopy(Blocks.PACKED_ICE).noLootTable());
    }

    public static void register(IEventBus bus) { BLOCKS.register(bus); }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    public static boolean place(ServerLevel level, BlockPos pos, int duration) {
        if (!level.hasChunkAt(pos) || !level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, ICE.get().defaultBlockState(), 3);
        level.scheduleTick(pos, ICE.get(), duration);
        return true;
    }
}

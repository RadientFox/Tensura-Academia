package com.radient.tensuraacadamia.commands;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class TapeCourseCommand {
    private static final int HEIGHT = 30;
    private static final int FINISH_DISTANCE = 44;

    private TapeCourseCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tape_course")
                .requires(source -> source.hasPermission(2))
                .executes(context -> build(context.getSource().getPlayerOrException())));
    }

    private static int build(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos start = player.blockPosition().above(HEIGHT);
        if (start.getY() + 9 >= level.getMaxBuildHeight()) {
            player.sendSystemMessage(Component.literal("Not enough build height for a Tape course here."));
            return 0;
        }

        Direction forward = Direction.fromYRot(player.getYRot());
        Direction side = forward.getClockWise();
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        platform(blocks, start, forward, side, 0, 0, 2, Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
        platform(blocks, start, forward, side, FINISH_DISTANCE, 0, 2, Blocks.LIME_CONCRETE.defaultBlockState());
        for (int distance = 9; distance <= 36; distance += 9) {
            platform(blocks, start, forward, side, distance, 8, 1, Blocks.YELLOW_CONCRETE.defaultBlockState());
            blocks.put(point(start, forward, side, distance, 0, 8), Blocks.HONEY_BLOCK.defaultBlockState());
            platform(blocks, start, forward, side, distance, -9, 1, Blocks.WHITE_CONCRETE.defaultBlockState());
        }

        for (BlockPos pos : blocks.keySet()) {
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir() || !level.mayInteract(player, pos)) {
                player.sendSystemMessage(Component.literal("Tape course not built: the space is occupied, unloaded, or protected."));
                return 0;
            }
        }
        if (!level.getBlockState(start.above()).isAir() || !level.getBlockState(start.above(2)).isAir()) {
            player.sendSystemMessage(Component.literal("Tape course not built: the starting position is obstructed."));
            return 0;
        }

        blocks.forEach((pos, state) -> level.setBlock(pos, state, 3));
        player.stopRiding();
        player.teleportTo(start.getX() + 0.5, start.getY() + 1, start.getZ() + 0.5);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.sendSystemMessage(Component.literal("Tape course ready: swing between the four yellow anchors to reach the green platform."));
        return 1;
    }

    private static void platform(Map<BlockPos, BlockState> blocks, BlockPos start, Direction forward,
                                 Direction side, int distance, int height, int radius, BlockState state) {
        for (int length = -radius; length <= radius; length++) {
            for (int width = -radius; width <= radius; width++) {
                blocks.put(point(start, forward, side, distance + length, width, height), state);
            }
        }
    }

    private static BlockPos point(BlockPos start, Direction forward, Direction side,
                                  int distance, int lateral, int height) {
        return start.relative(forward, distance).relative(side, lateral).above(height);
    }
}

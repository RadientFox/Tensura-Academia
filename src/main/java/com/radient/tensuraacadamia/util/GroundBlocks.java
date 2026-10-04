package com.radient.tensuraacadamia.util;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.mixin.FallingBlockEntityInvoker;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class GroundBlocks {

    private static final int SURFACE_SEARCH = 6;
    private static final int MAX_MOVING = 400;
    private static final int LIFT_TIMEOUT = 60;
    private static final int THROW_TIMEOUT = 200;

    private record Moving(FallingBlockEntity block, ServerLevel level, @Nullable BlockPos origin, BlockState state, long deadline, boolean place) {}

    private static final List<Moving> MOVING = new ArrayList<>();

    private GroundBlocks() {
    }

    public static @Nullable BlockPos findSurface(ServerLevel level, BlockPos column) {
        for (int y = SURFACE_SEARCH; y >= -SURFACE_SEARCH; y--) {
            BlockPos pos = column.above(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos;
            }
        }

        return null;
    }

    public static boolean canGrief(Level level, @Nullable LivingEntity entity) {
        return entity != null && !(entity instanceof Player) ? level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) : TensuraGameRules.canSkillGrief(level);
    }

    public static boolean canBreak(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, BlockPos pos) {
        if (!canGrief(level, entity)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }

        if (entity instanceof Player player && !level.mayInteract(player, pos)) {
            return false;
        }

        return !TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(instance, level, entity, pos.getX(), pos.getY(), pos.getZ()).isFalse();
    }

    public static void griefed(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, BlockPos pos) {
        TensuraSkillEvents.SKILL_GRIEF_POS.invoker().grief(instance, level, entity, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean lift(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos, double speed) {
        BlockState state = level.getBlockState(pos);
        if (MOVING.size() >= MAX_MOVING || !state.isCollisionShapeFullBlock(level, pos) || !state.getFluidState().isEmpty() || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }

        if (!canBreak(level, instance, owner, pos) || !level.getEntities((Entity) null, new AABB(pos.above())).isEmpty()) {
            return false;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        griefed(level, instance, owner, pos);
        FallingBlockEntity block = FallingBlockEntityInvoker.tracadamia$create(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, state);
        block.dropItem = false;
        block.time = 1;
        block.setDeltaMovement(0.0D, speed, 0.0D);
        level.addFreshEntity(block);
        MOVING.add(new Moving(block, level, pos.immutable(), state, level.getGameTime() + LIFT_TIMEOUT, false));
        return true;
    }

    public static void throwBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 motion) {
        throwBlock(level, pos, state, motion, false);
    }

    public static void dropBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 motion) {
        throwBlock(level, pos, state, motion, true);
    }

    private static void throwBlock(ServerLevel level, BlockPos pos, BlockState state, Vec3 motion, boolean place) {
        if (MOVING.size() >= MAX_MOVING || state.getRenderShape() != RenderShape.MODEL) {
            if (place && level.getBlockState(pos).canBeReplaced()) {
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
            return;
        }

        FallingBlockEntity block = FallingBlockEntityInvoker.tracadamia$create(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, state);
        block.dropItem = false;
        block.disableDrop();
        block.time = 1;
        block.setDeltaMovement(motion);
        level.addFreshEntity(block);
        MOVING.add(new Moving(block, level, null, state, level.getGameTime() + THROW_TIMEOUT, place));
    }

    public static void dust(ServerLevel level, BlockPos pos, BlockState state, int count) {
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, count, 0.3D, 0.0D, 0.3D, 0.0D);
        }
    }

    private static boolean tickMoving(Moving moving) {
        FallingBlockEntity block = moving.block();
        ServerLevel level = moving.level();
        boolean landed = block.isRemoved();
        if (!landed) {
            if (level.getGameTime() < moving.deadline()) {
                return false;
            }

            block.discard();
        }

        BlockPos spot = moving.origin() != null ? moving.origin() : block.blockPosition();
        if (!level.isLoaded(spot)) {
            return true;
        }

        if (moving.origin() != null && !level.getBlockState(spot).is(moving.state().getBlock()) && level.getBlockState(spot).canBeReplaced()) {
            level.setBlock(spot, moving.state(), Block.UPDATE_ALL);
        } else if (moving.place() && landed && level.getBlockState(spot).canBeReplaced()) {
            level.setBlock(spot, moving.state(), Block.UPDATE_ALL);
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, moving.state()), block.getX(), block.getY() + 0.1D, block.getZ(), 6, 0.3D, 0.05D, 0.3D, 0.05D);
        if (level.random.nextInt(6) == 0) {
            level.playSound(null, block.getX(), block.getY(), block.getZ(), moving.state().getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6F, 0.7F);
        }

        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MOVING.isEmpty()) {
            MOVING.removeIf(GroundBlocks::tickMoving);
        }
    }

    // lifted blocks go back
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Moving moving : MOVING) {
            moving.block().discard();
            if (moving.origin() != null && moving.level().isLoaded(moving.origin()) && moving.level().getBlockState(moving.origin()).canBeReplaced()) {
                moving.level().setBlock(moving.origin(), moving.state(), Block.UPDATE_ALL);
            }
        }

        MOVING.clear();
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TelekinesisQuirk extends Skill {

    private static final QuirkSkillsConfig.Telekinesis CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Telekinesis;

    private static final int THROW = 0;
    private static final int MULTI_THROW = 1;
    private static final int BOULDER = 2;
    private static final int WALL = 3;

    private static final double TARGET_RAY_OFFSET = 1.0D;
    private static final float SMALL_SCALE = 0.5F;
    private static final double MAX_GATHER_SPEED = 1.5D;
    private static final int THROW_LIFETIME = 60;
    private static final int ROCK_LIFETIME = 160;
    private static final int RETURN_LIFETIME = 40;
    private static final double WALL_ARC = Math.cos(Math.toRadians(50.0D));
    private static final double STEERING = 0.5D;

    private static final Map<UUID, HeldBlocks> HELD = new HashMap<>();
    private static final List<ThrownBlocks> THROWN = new ArrayList<>();
    private static final List<Returning> RETURNING = new ArrayList<>();

    private record Lifted(TelekinesisBlockEntity block, BlockPos origin, float hardness, int slot) {}

    private record Returning(Lifted lifted, long until) {}

    private static final class HeldBlocks {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final List<Lifted> blocks;
        private final int slots;
        private final long until;
        private final double phase;
        private final double rockDamage;
        private final double rockHardness;
        private int rockHold = TelekinesisBlockEntity.HOLD_ARMS;
        private boolean firing;
        private @Nullable LivingEntity target;
        private Vec3 aim = Vec3.ZERO;
        private long nextLaunch;

        private HeldBlocks(ManasSkillInstance instance, LivingEntity owner, int mode, List<Lifted> blocks, long until, double phase, double rockDamage, double rockHardness) {
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
            this.blocks = blocks;
            this.slots = blocks.size();
            this.until = until;
            this.phase = phase;
            this.rockDamage = rockDamage;
            this.rockHardness = rockHardness;
        }

        private boolean isRock() {
            return !Double.isNaN(this.rockDamage);
        }
    }

    private static final class ThrownBlocks {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final List<Lifted> blocks;
        private final List<Vec3> offsets;
        private final List<Vec3> lastPositions = new ArrayList<>();
        private final @Nullable LivingEntity target;
        private final int mode;
        private final float damage;
        private final double speed;
        private final boolean rock;
        private @Nullable Vec3 velocity;
        private Vec3 center;
        private Vec3 aim;
        private Vec3 direction;
        private int age;

        private ThrownBlocks(ManasSkillInstance instance, LivingEntity owner, List<Lifted> blocks, List<Vec3> offsets, @Nullable LivingEntity target, Vec3 center, Vec3 aim, int mode, double speed, double rockDamage) {
            this.instance = instance;
            this.owner = owner;
            this.blocks = blocks;
            this.offsets = offsets;
            this.target = target;
            this.center = center;
            this.aim = aim;
            Vec3 direction = aim.subtract(center);
            this.direction = direction.lengthSqr() > 1.0E-4D ? direction.normalize() : owner.getLookAngle();
            this.mode = mode;
            this.speed = speed;
            this.rock = !Double.isNaN(rockDamage);
            boolean mastered = instance.isMastered(owner);
            this.damage = (float) (Double.isNaN(rockDamage)
                    ? blocks.stream().mapToDouble(lifted -> getDamage(lifted, mastered)).sum()
                    : rockDamage + blocks.stream().mapToDouble(lifted -> Math.max(0.0F, lifted.hardness())).sum());
            for (Lifted lifted : blocks) {
                this.lastPositions.add(lifted.block().position());
            }
        }
    }

    public TelekinesisQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 4;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == THROW ? WALL : mode - 1;
        }

        return mode == WALL ? THROW : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case THROW -> "telekinesis.throw";
            case MULTI_THROW -> "telekinesis.multi_throw";
            case BOULDER -> "telekinesis.boulder";
            case WALL -> "telekinesis.wall";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        HeldBlocks held = HELD.get(entity.getUUID());
        if (held == null) {
            pickUp(level, instance, entity, mode);
        } else if (entity.isShiftKeyDown()) {
            returnHeld(HELD.remove(entity.getUUID()), level.getGameTime());
            entity.swing(InteractionHand.MAIN_HAND, true);
        } else if (!held.firing) {
            throwHeld(level, held);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        HeldBlocks held = HELD.remove(entity.getUUID());
        if (held != null) {
            returnHeld(held, entity.level().getGameTime());
        }
    }

    // Pick up the hardest blocks around

    private static int getHoldType(int mode) {
        return switch (mode) {
            case MULTI_THROW -> TelekinesisBlockEntity.HOLD_ORBIT;
            case BOULDER -> TelekinesisBlockEntity.HOLD_BOULDER;
            case WALL -> TelekinesisBlockEntity.HOLD_WALL;
            default -> TelekinesisBlockEntity.HOLD_SHOULDER;
        };
    }

    private static void pickUp(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int mode) {
        BlockPos looked = getLookedAtBlock(level, instance, entity);
        List<BlockPos> nearby = findNearbyBlocks(level, instance, entity);
        if (looked != null && !nearby.contains(looked)) {
            nearby.add(looked);
        }

        if (nearby.isEmpty()) {
            fail(entity, "tracadamia.skill.telekinesis.no_blocks");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        Vec3 feet = entity.position();
        nearby.sort(Comparator.comparingDouble((BlockPos pos) -> -level.getBlockState(pos).getDestroySpeed(level, pos))
                .thenComparing(pos -> !pos.equals(looked))
                .thenComparingDouble(pos -> pos.distToCenterSqr(feet)));
        List<BlockPos> chosen = new ArrayList<>(nearby.subList(0, Math.min(nearby.size(), getLiftCount(instance, entity, mode))));

        float scale = mode == BOULDER || mode == WALL ? 1.0F : SMALL_SCALE;
        List<Float> hardness = new ArrayList<>();
        List<TelekinesisBlockEntity> lifted = liftBlocks(level, instance, entity, chosen, scale, mode == WALL, hardness);

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lifted.size(); i++) {
            order.add(i);
        }

        if (mode == MULTI_THROW) {
            order.sort(Comparator.comparingDouble(i -> getAngle(entity, lifted.get(i))));
        }

        List<Lifted> blocks = new ArrayList<>();
        for (int slot = 0; slot < order.size(); slot++) {
            int i = order.get(slot);
            blocks.add(new Lifted(lifted.get(i), chosen.get(i), hardness.get(i), slot));
        }

        long time = level.getGameTime();
        double phase = blocks.isEmpty() ? 0.0D : getAngle(entity, blocks.get(0).block()) - time * TelekinesisBlockEntity.ORBIT_SPEED;
        HELD.put(entity.getUUID(), new HeldBlocks(instance, entity, mode, blocks, time + CONFIG.heldSeconds * 20L, phase, Double.NaN, 1.0D));
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.addMasteryPoint(entity);
    }

    private static List<TelekinesisBlockEntity> liftBlocks(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, List<BlockPos> chosen, float scale, boolean shield, List<Float> hardness) {
        List<TelekinesisBlockEntity> lifted = new ArrayList<>();
        for (BlockPos pos : chosen) {
            BlockState state = level.getBlockState(pos);
            hardness.add(state.getDestroySpeed(level, pos));
            level.removeBlock(pos, false);
            GroundBlocks.griefed(level, instance, entity, pos);
            level.levelEvent(2001, pos, Block.getId(state));

            TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, scale, Vec3.atCenterOf(pos));
            block.setShield(shield);
            level.addFreshEntity(block);
            lifted.add(block);
        }

        return lifted;
    }

    public static boolean liftRock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int count, double damage) {
        return liftRock(level, instance, entity, count, damage, 1.0D);
    }

    public static boolean liftRock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int count, double damage, double hardnessDamage) {
        return liftRock(level, instance, entity, count, damage, hardnessDamage, TelekinesisBlockEntity.HOLD_ARMS);
    }

    public static boolean liftRock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int count, double damage, double hardnessDamage, int hold) {
        int width = count > 8 ? 3 : 2;
        int layers = Math.max(1, Mth.ceil(count / (double) (width * width)));
        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        AABB footprint = entity.getBoundingBox().inflate(0.2D, 1.0D, 0.2D);
        int startX = 0;
        int startZ = 0;
        for (double gap = 0.5D; gap <= 3.0D; gap += 0.5D) {
            Vec3 spot = entity.position().add(forward.scale(entity.getBbWidth() * 0.5D + width * 0.5D + gap));
            startX = Mth.floor(spot.x - width * 0.5D + 0.5D);
            startZ = Mth.floor(spot.z - width * 0.5D + 0.5D);
            if (!footprint.intersects(new AABB(startX, footprint.minY, startZ, startX + width, footprint.maxY, startZ + width))) {
                break;
            }
        }

        List<BlockPos> chosen = new ArrayList<>();
        List<Vec3> shape = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                BlockPos surface = GroundBlocks.findSurface(level, new BlockPos(startX + dx, entity.getBlockY(), startZ + dz));
                for (int layer = 0; surface != null && layer < layers && chosen.size() < count; layer++) {
                    BlockPos pos = surface.below(layer);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || footprint.intersects(new AABB(pos)) || !canLift(level, instance, entity, pos, state)) {
                        break;
                    }

                    chosen.add(pos);
                    shape.add(new Vec3(dx - (width - 1) * 0.5D, (layers - 1) * 0.5D - layer, dz - (width - 1) * 0.5D));
                }
            }
        }

        if (chosen.isEmpty()) {
            return false;
        }

        List<Float> hardness = new ArrayList<>();
        List<TelekinesisBlockEntity> lifted = liftBlocks(level, instance, entity, chosen, 1.0F, false, hardness);
        List<Integer> used = new ArrayList<>();
        List<Lifted> blocks = new ArrayList<>();
        for (int i = 0; i < lifted.size(); i++) {
            int slot = TelekinesisBlockEntity.BOULDER_SHAPE.indexOf(shape.get(i));
            for (int next = 0; slot < 0 || used.contains(slot); next++) {
                slot = next;
            }

            used.add(slot);
            blocks.add(new Lifted(lifted.get(i), chosen.get(i), hardness.get(i), slot));
        }

        HeldBlocks held = new HeldBlocks(instance, entity, BOULDER, blocks, Long.MAX_VALUE, 0.0D, damage, hardnessDamage);
        held.rockHold = hold;
        HELD.put(entity.getUUID(), held);
        return true;
    }

    public static boolean isHoldingRock(LivingEntity entity) {
        HeldBlocks held = HELD.get(entity.getUUID());
        return held != null && held.isRock();
    }

    public static void throwRock(ServerLevel level, LivingEntity entity, double range, double speed) {
        HeldBlocks held = HELD.get(entity.getUUID());
        if (held == null || !held.isRock()) {
            return;
        }

        HELD.remove(entity.getUUID());
        List<Lifted> blocks = held.blocks.stream().filter(lifted -> !lifted.block().isRemoved()).toList();
        if (blocks.isEmpty()) {
            return;
        }

        for (Lifted lifted : blocks) {
            lifted.block().release();
            lifted.block().noPhysics = false;
        }

        Vec3 center = blocks.stream().map(lifted -> getBlockCenter(lifted.block())).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / blocks.size());
        List<Vec3> offsets = blocks.stream().map(lifted -> getBlockCenter(lifted.block()).subtract(center)).toList();
        double extraHardness = (held.rockHardness - 1.0D) * blocks.stream().mapToDouble(lifted -> Math.max(0.0F, lifted.hardness())).sum();
        ThrownBlocks thrown = new ThrownBlocks(held.instance, entity, blocks, offsets, null, center, center, BOULDER, 0.0D, held.rockDamage + extraHardness);
        thrown.velocity = MultiArms.getThrowVelocity(entity, null, center, range, speed);
        THROWN.add(thrown);

        entity.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 0.4F);
    }

    public static void dropRock(LivingEntity entity) {
        HeldBlocks held = HELD.get(entity.getUUID());
        if (held != null && held.isRock()) {
            HELD.remove(entity.getUUID());
            returnHeld(held, entity.level().getGameTime());
        }
    }

    private static double getAngle(LivingEntity owner, Entity block) {
        return Mth.atan2(block.getZ() - owner.getZ(), block.getX() - owner.getX());
    }

    private static @Nullable BlockPos getLookedAtBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        BlockHitResult hit = ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, CONFIG.targetRange);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        return canLift(level, instance, entity, pos, level.getBlockState(pos)) ? pos : null;
    }

    private static int getLiftCount(ManasSkillInstance instance, LivingEntity entity, int mode) {
        boolean mastered = instance.isMastered(entity);
        int count = switch (mode) {
            case MULTI_THROW -> randomBetween(entity, CONFIG.multiThrowMin, CONFIG.multiThrowMax);
            case BOULDER -> mastered ? randomBetween(entity, CONFIG.boulderMinMastered, CONFIG.boulderMaxMastered) : randomBetween(entity, CONFIG.boulderMin, CONFIG.boulderMax);
            case WALL -> CONFIG.wallBlocks;
            default -> 1;
        };

        return Mth.clamp(count, 1, TelekinesisBlockEntity.BOULDER_SHAPE.size());
    }

    private static int randomBetween(LivingEntity entity, int min, int max) {
        return Mth.randomBetweenInclusive(entity.getRandom(), min, Math.max(min, max));
    }

    private static List<BlockPos> findNearbyBlocks(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos feet = entity.blockPosition();
        AABB footprint = entity.getBoundingBox().inflate(0.5D, 1.0D, 0.5D);
        int radius = CONFIG.pickupRadius;
        int height = Mth.ceil(entity.getBbHeight()) + 1;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }

                for (int dy = -3; dy <= height; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && !footprint.intersects(new AABB(pos)) && isExposed(level, pos) && canLift(level, instance, entity, pos, state)) {
                        found.add(pos);
                    }
                }
            }
        }

        return found;
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static boolean canLift(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty() && GroundBlocks.canBreak(level, instance, entity, pos);
    }


    private static boolean isShielded(LivingEntity owner, Vec3 from) {
        Vec3 offset = from.subtract(owner.position());
        Vec3 flat = new Vec3(offset.x, 0.0D, offset.z);
        if (offset.y < -1.0D || offset.y > owner.getBbHeight() + 2.0D || flat.lengthSqr() < 1.0E-4D) {
            return false;
        }

        return flat.normalize().dot(Vec3.directionFromRotation(0.0F, owner.getYRot())) >= WALL_ARC;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        HeldBlocks held = HELD.get(entity.getUUID());
        if (held == null || held.mode != WALL || held.owner != entity || held.blocks.isEmpty() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        DamageSource source = event.getSource();
        Vec3 from = source.getDirectEntity() != null ? getBlockCenter(source.getDirectEntity()) : source.getSourcePosition();
        if (from == null || !isShielded(entity, from)) {
            return;
        }

        event.setCanceled(true);
        TelekinesisBlockEntity block = held.blocks.stream().map(Lifted::block).min(Comparator.comparingDouble(wall -> wall.distanceToSqr(from))).orElseThrow();
        Vec3 hit = getBlockCenter(block);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.getBlockState()), hit.x, hit.y, hit.z, 10, 0.3D, 0.3D, 0.3D, 0.1D);
        level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F + entity.getRandom().nextFloat() * 0.4F);
    }

    private static Vec3 getBlockCenter(Entity block) {
        return block.position().add(0.0D, block.getBbHeight() * 0.5D, 0.0D);
    }

    private static void returnHeld(HeldBlocks held, long time) {
        for (Lifted lifted : held.blocks) {
            if (!lifted.block().isRemoved()) {
                lifted.block().release();
                lifted.block().setShield(false);
                RETURNING.add(new Returning(lifted, time + RETURN_LIFETIME));
            }
        }

        if (held.mode == WALL) {
            held.instance.setCoolDown(CONFIG.wallCooldown, WALL);
        }
    }

    private static void placeAllBack(HeldBlocks held) {
        for (Lifted lifted : held.blocks) {
            if (lifted.block().level() instanceof ServerLevel level) {
                placeBack(level, lifted);
            }
        }
    }

    private static void placeBack(ServerLevel level, Lifted lifted) {
        TelekinesisBlockEntity block = lifted.block();
        BlockState state = block.getBlockState();
        BlockPos origin = lifted.origin();
        block.discard();

        if (!level.getBlockState(origin).canBeReplaced()) {
            Block.popResource(level, origin, new ItemStack(state.getBlock()));
            return;
        }

        level.setBlockAndUpdate(origin, state);
        SoundType sound = state.getSoundType(level, origin, null);
        level.playSound(null, origin, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private static boolean isGone(Lifted lifted) {
        TelekinesisBlockEntity block = lifted.block();
        if (!block.isRemoved()) {
            return false;
        }

        Entity.RemovalReason reason = block.getRemovalReason();
        if (reason != null && !reason.shouldDestroy() && block.level() instanceof ServerLevel level) {
            placeBack(level, lifted);
        }

        return true;
    }

    private static void throwHeld(ServerLevel level, HeldBlocks held) {
        LivingEntity entity = held.owner;
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.throwRange + TARGET_RAY_OFFSET, false, true);
        Vec3 aim = target != null ? getCenter(target) : getLookedAtSpot(level, entity);
        if (!held.isRock()) {
            held.instance.setCoolDown(getCooldown(held.mode), held.mode);
        }

        if (held.mode == MULTI_THROW) {
            held.firing = true;
            held.target = target;
            held.aim = aim;
            held.nextLaunch = level.getGameTime();
            return;
        }

        HELD.remove(entity.getUUID());
        List<Lifted> blocks = held.blocks.stream().filter(lifted -> !lifted.block().isRemoved()).toList();
        if (blocks.isEmpty()) {
            return;
        }

        for (Lifted lifted : blocks) {
            lifted.block().release();
            lifted.block().noPhysics = false;
            lifted.block().setShield(false);
        }

        if (held.mode == BOULDER || held.mode == WALL) {
            Vec3 center = blocks.stream().map(lifted -> getBlockCenter(lifted.block())).reduce(Vec3.ZERO, Vec3::add).scale(1.0D / blocks.size());
            List<Vec3> offsets = blocks.stream().map(lifted -> getBlockCenter(lifted.block()).subtract(center)).toList();
            THROWN.add(new ThrownBlocks(held.instance, entity, blocks, offsets, target, center, aim, held.mode, CONFIG.boulderSpeed, held.rockDamage));
        } else {
            for (Lifted lifted : blocks) {
                THROWN.add(new ThrownBlocks(held.instance, entity, List.of(lifted), List.of(Vec3.ZERO), target, getBlockCenter(lifted.block()), aim, held.mode, CONFIG.throwSpeed, Double.NaN));
            }
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, held.mode == THROW ? 0.6F : 0.4F);
    }

    private static Vec3 getLookedAtSpot(ServerLevel level, LivingEntity entity) {
        return ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, CONFIG.throwRange).getLocation();
    }

    private static Vec3 getFollowedSpot(ServerLevel level, LivingEntity entity, Vec3 from) {
        BlockHitResult hit = ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, CONFIG.throwRange);
        return hit.getType() == HitResult.Type.MISS ? from.add(entity.getLookAngle().scale(CONFIG.throwRange * 2.0D)) : hit.getLocation();
    }

    // The block closest to the aim goes first
    private static void launchNext(ServerLevel level, HeldBlocks held) {
        LivingEntity owner = held.owner;
        LivingEntity target = held.target != null && held.target.isAlive() ? held.target : null;
        Vec3 aim = target != null ? getCenter(target) : getLookedAtSpot(level, owner);
        Vec3 from = getCenter(owner);
        Vec3 direction = aim.subtract(from).normalize();

        Lifted next = held.blocks.stream().max(Comparator.comparingDouble(lifted -> getBlockCenter(lifted.block()).subtract(from).normalize().dot(direction))).orElseThrow();
        held.blocks.remove(next);
        next.block().release();
        next.block().noPhysics = false;
        THROWN.add(new ThrownBlocks(held.instance, owner, List.of(next), List.of(Vec3.ZERO), target, getBlockCenter(next.block()), aim, MULTI_THROW, CONFIG.throwSpeed, Double.NaN));

        owner.swing(held.blocks.size() % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.0F + owner.getRandom().nextFloat() * 0.3F);
    }

    private static int getCooldown(int mode) {
        return switch (mode) {
            case MULTI_THROW -> CONFIG.multiThrowCooldown;
            case BOULDER -> CONFIG.boulderCooldown;
            case WALL -> CONFIG.wallCooldown;
            default -> CONFIG.throwCooldown;
        };
    }

    // Base damage + the block's hardness
    private static double getDamage(Lifted lifted, boolean mastered) {
        return (mastered ? CONFIG.baseDamageMastered : CONFIG.baseDamage) + Math.max(0.0F, lifted.hardness());
    }

    private static Vec3 getCenter(Entity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static @Nullable LivingEntity findHit(ServerLevel level, ThrownBlocks thrown, TelekinesisBlockEntity block, Vec3 from, Vec3 to) {
        AABB sweep = block.getBoundingBox().move(from.subtract(to)).expandTowards(to.subtract(from)).inflate(0.3D);
        return level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != thrown.owner && target.isAlive() && !target.isSpectator())
                .stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(from)))
                .orElse(null);
    }

    private static void hitTarget(ThrownBlocks thrown, LivingEntity target, TelekinesisBlockEntity block) {
        DamageSource source = new DamageSource(thrown.owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.THROWN), block, thrown.owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setAbilityInstance(thrown.instance);
        tensuraSource.tensura$setAbilityMode(thrown.mode);

        target.invulnerableTime = 0;
        if (target.hurt(source, thrown.damage) && thrown.owner.getRandom().nextBoolean()) {
            thrown.instance.addMasteryPoint(thrown.owner);
        }
    }

    private static void shatter(ServerLevel level, ThrownBlocks thrown) {
        Vec3 carry = thrown.velocity == null ? Vec3.ZERO : thrown.velocity.scale(0.3D);
        for (Lifted lifted : thrown.blocks) {
            TelekinesisBlockEntity block = lifted.block();
            if (block.isRemoved()) {
                continue;
            }

            if (thrown.rock) {
                Vec3 out = getBlockCenter(block).subtract(thrown.center).normalize().scale(0.25D);
                GroundBlocks.throwBlock(level, block.blockPosition(), block.getBlockState(), out.add(carry).add(0.0D, 0.3D, 0.0D));
            } else {
                level.levelEvent(2001, block.blockPosition(), Block.getId(block.getBlockState()));
            }

            block.discard();
        }

        if (thrown.mode == BOULDER || thrown.mode == WALL) {
            level.sendParticles(ParticleTypes.EXPLOSION, thrown.center.x, thrown.center.y, thrown.center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.playSound(null, thrown.center.x, thrown.center.y, thrown.center.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.5F, 0.5F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!HELD.isEmpty()) {
            HELD.values().removeIf(TelekinesisQuirk::tickHeld);
        }

        if (!THROWN.isEmpty()) {
            THROWN.removeIf(TelekinesisQuirk::tickThrown);
        }

        if (!RETURNING.isEmpty()) {
            RETURNING.removeIf(TelekinesisQuirk::tickReturning);
        }
    }

    private static boolean tickHeld(HeldBlocks held) {
        LivingEntity owner = held.owner;
        held.blocks.removeIf(TelekinesisQuirk::isGone);
        if (held.blocks.isEmpty()) {
            return true;
        }

        if (!owner.isAlive() || owner.isRemoved() || held.blocks.get(0).block().level() != owner.level() || !(owner.level() instanceof ServerLevel level)) {
            placeAllBack(held);
            return true;
        }

        long time = level.getGameTime();
        if (!held.firing && time > held.until) {
            returnHeld(held, time);
            return true;
        }

        if (held.firing && time >= held.nextLaunch) {
            launchNext(level, held);
            held.nextLaunch = time + Math.max(1, CONFIG.multiThrowInterval);
            if (held.blocks.isEmpty()) {
                return true;
            }
        }

        int hold = held.isRock() ? held.rockHold : getHoldType(held.mode);
        for (Lifted lifted : held.blocks) {
            TelekinesisBlockEntity block = lifted.block();
            block.keepAlive();
            block.hold(owner, hold, lifted.slot(), held.slots, held.phase);
            Vec3 offset = block.getHoldSpot(owner, time).subtract(block.position());
            Vec3 motion = held.isRock() && offset.length() < MAX_GATHER_SPEED ? offset : offset.scale(0.5D);
            if (motion.length() > MAX_GATHER_SPEED) {
                motion = motion.normalize().scale(MAX_GATHER_SPEED);
            }

            block.noPhysics = true;
            block.setDeltaMovement(motion);
        }

        return false;
    }

    private static boolean tickThrown(ThrownBlocks thrown) {
        TelekinesisBlockEntity lead = thrown.blocks.stream().map(Lifted::block).filter(block -> !block.isRemoved()).findFirst().orElse(null);
        if (lead == null || !(lead.level() instanceof ServerLevel level)) {
            return true;
        }

        for (int i = 0; i < thrown.blocks.size(); i++) {
            TelekinesisBlockEntity block = thrown.blocks.get(i).block();
            if (block.isRemoved()) {
                continue;
            }

            LivingEntity hit = findHit(level, thrown, block, thrown.lastPositions.get(i), block.position());
            if (hit != null) {
                hitTarget(thrown, hit, block);
                shatter(level, thrown);
                return true;
            }
        }

        boolean collided = thrown.blocks.stream().map(Lifted::block).anyMatch(block -> !block.isRemoved() && (block.horizontalCollision || block.verticalCollision
                || !level.isPositionEntityTicking(block.blockPosition()) && !level.noCollision(block, block.getBoundingBox())));
        if (thrown.age++ > (thrown.velocity != null ? ROCK_LIFETIME : THROW_LIFETIME) || collided) {
            shatter(level, thrown);
            return true;
        }

        if (thrown.velocity != null) {
            Vec3 velocity = thrown.velocity;
            thrown.center = thrown.center.add(velocity.x, velocity.y - MultiArms.THROW_GRAVITY * 0.5D, velocity.z);
            thrown.velocity = velocity.subtract(0.0D, MultiArms.THROW_GRAVITY, 0.0D);
            moveBlocks(thrown);
            return false;
        }

        if (thrown.target != null && thrown.target.isAlive()) {
            thrown.aim = getCenter(thrown.target);
        }

        boolean following = thrown.target == null && thrown.mode == MULTI_THROW && thrown.owner.isAlive();
        if (following) {
            thrown.aim = getFollowedSpot(level, thrown.owner, thrown.center);
        }

        double speed = thrown.speed;
        Vec3 toAim = thrown.aim.subtract(thrown.center);
        if (toAim.length() > speed && (following || thrown.target != null || toAim.dot(thrown.direction) > 0.0D)) {
            Vec3 wanted = toAim.normalize();
            thrown.direction = following ? thrown.direction.lerp(wanted, STEERING).normalize() : wanted;
        }

        thrown.center = thrown.center.add(thrown.direction.scale(speed));
        moveBlocks(thrown);
        return false;
    }

    private static void moveBlocks(ThrownBlocks thrown) {
        for (int i = 0; i < thrown.blocks.size(); i++) {
            TelekinesisBlockEntity block = thrown.blocks.get(i).block();
            thrown.lastPositions.set(i, block.position());
            if (block.isRemoved()) {
                continue;
            }

            block.keepAlive();

            // Blocks outside the area the server ticks don't move themselves, so they'd freeze and then jump ahead
            Vec3 spot = thrown.center.add(thrown.offsets.get(i)).subtract(0.0D, block.getBbHeight() * 0.5D, 0.0D);
            if (block.level() instanceof ServerLevel level && !level.isPositionEntityTicking(block.blockPosition())) {
                block.setPos(spot);
                block.setDeltaMovement(Vec3.ZERO);
            } else {
                block.setDeltaMovement(spot.subtract(block.position()));
            }
        }
    }

    private static boolean tickReturning(Returning returning) {
        TelekinesisBlockEntity block = returning.lifted().block();
        if (isGone(returning.lifted()) || !(block.level() instanceof ServerLevel level)) {
            return true;
        }

        block.keepAlive();
        Vec3 home = Vec3.atCenterOf(returning.lifted().origin()).subtract(0.0D, block.getBbHeight() * 0.5D, 0.0D);
        Vec3 offset = home.subtract(block.position());
        if (offset.lengthSqr() < 0.09D || level.getGameTime() > returning.until()) {
            placeBack(level, returning.lifted());
            return true;
        }

        Vec3 motion = offset.scale(0.35D);
        if (motion.length() > MAX_GATHER_SPEED) {
            motion = motion.normalize().scale(MAX_GATHER_SPEED);
        }

        block.noPhysics = true;
        block.setDeltaMovement(motion);
        return false;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (HeldBlocks held : HELD.values()) {
            placeAllBack(held);
        }

        for (Returning returning : RETURNING) {
            if (returning.lifted().block().level() instanceof ServerLevel level) {
                placeBack(level, returning.lifted());
            }
        }

        for (ThrownBlocks thrown : THROWN) {
            thrown.blocks.forEach(lifted -> lifted.block().discard());
        }

        HELD.clear();
        THROWN.clear();
        RETURNING.clear();
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

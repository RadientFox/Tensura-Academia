package com.radient.tensuraacadamia.ability.unique.quirks.attraction;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.GravityClusterEntity;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AttractionCrash {

    private static final QuirkSkillsConfig.Attraction CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Attraction;

    private static final int BATCH_TICKS = 5;
    private static final float GATHER_SPEED = 0.9F;
    private static final float SPIN = 6.0F;
    private static final float COLLAPSE_SPIN = 30.0F;
    private static final int PICK_TRIES = 10;
    private static final double RING_GAP = 0.9D;
    private static final double SHELL_SQUASH = 0.55D;
    private static final int RING_FIRST = 10;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final float SHOT_SCALE = 0.8F;
    private static final int SHOT_TICKS = 40;
    private static final double ITEM_SPEED = 0.6D;

    private record Held(BlockState state, float hardness, Vec3 slot) {}

    private static final class Crash {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final List<Held> held = new ArrayList<>();
        private final List<GravityClusterEntity> clusters = new ArrayList<>();
        private final List<GravityClusterEntity.Part> batch = new ArrayList<>();
        private double size = 1.0D;
        private long fullAt = -1L;

        private Crash(ManasSkillInstance instance, LivingEntity owner, int mode) {
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
        }
    }

    private static final class Shot {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final TelekinesisBlockEntity block;
        private final Vec3 velocity;
        private final float damage;
        private final Set<LivingEntity> hit = new HashSet<>();
        private int age;

        private Shot(ManasSkillInstance instance, LivingEntity owner, int mode, TelekinesisBlockEntity block, Vec3 velocity, float damage) {
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
            this.block = block;
            this.velocity = velocity;
            this.damage = damage;
        }
    }

    private static final Map<LivingEntity, Crash> CRASHES = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();

    private AttractionCrash() {
    }

    private static Vec3 getCenter(LivingEntity owner) {
        return owner.position().add(0.0D, owner.getBbHeight() * 0.75D, 0.0D);
    }

    private static double getMaxSize(Crash crash) {
        return crash.instance.isMastered(crash.owner) ? CONFIG.crashMaxSizeMastered : CONFIG.crashMaxSize;
    }

    private static int getMaxBlocks(Crash crash) {
        return crash.instance.isMastered(crash.owner) ? CONFIG.crashMaxBlocksMastered : CONFIG.crashMaxBlocks;
    }

    private static Vec3 getSlot(LivingEntity owner, int index) {
        int layer = 0;
        int first = 0;
        int count = RING_FIRST;
        while (index >= first + count) {
            first += count;
            layer++;
            count = RING_FIRST + layer * 8;
        }

        double base = Math.max(owner.getBbWidth(), owner.getBbHeight() * 0.5D) + 1.0D;
        double radius = (base + layer * RING_GAP) * owner.getScale();
        int at = index - first;
        double y = 1.0D - (at + 0.5D) * 2.0D / count;
        double flat = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
        double angle = at * GOLDEN_ANGLE + layer;
        return new Vec3(Math.cos(angle) * flat * radius, y * radius * SHELL_SQUASH, Math.sin(angle) * flat * radius);
    }

    public static boolean hold(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        Crash crash = CRASHES.get(owner);
        if (heldTicks == 0 || crash == null) {
            if (heldTicks > 0 || EnergyHelper.isOutOfEnergy(owner, instance, mode)) {
                return false;
            }

            release(level, owner, false);
            crash = new Crash(instance, owner, mode);
            CRASHES.put(owner, crash);
            playSound(level, getCenter(owner), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.5F);
            playSound(level, getCenter(owner), SoundEvents.PORTAL_TRIGGER, 0.4F, 1.6F);
        } else if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(owner, instance, mode)) {
            release(level, owner, true);
            return false;
        }

        double growth = instance.isMastered(owner) ? CONFIG.crashGrowthMastered : CONFIG.crashGrowth;
        crash.size = Math.min(getMaxSize(crash), 1.0D + growth * heldTicks / 20.0D);
        int pulls = Mth.floor(CONFIG.crashBlocksPerSecond * (heldTicks + 1) / 20.0D) - Mth.floor(CONFIG.crashBlocksPerSecond * heldTicks / 20.0D);
        for (int i = 0; i < pulls && crash.held.size() < getMaxBlocks(crash); i++) {
            pullBlock(level, crash);
        }

        Vec3 center = getCenter(owner);
        if (!crash.batch.isEmpty() && (heldTicks % BATCH_TICKS == 0 || crash.held.size() >= getMaxBlocks(crash))) {
            GravityClusterEntity cluster = GravityClusterEntity.create(level, center, crash.batch);
            cluster.setGather(level.getGameTime(), GATHER_SPEED);
            cluster.setSpin(SPIN);
            level.addFreshEntity(cluster);
            crash.clusters.add(cluster);
            crash.batch.clear();
        }
        for (GravityClusterEntity cluster : crash.clusters) {
            cluster.setPos(center);
            cluster.keepAlive();
        }

        attractItems(level, crash, center);
        if (heldTicks % 4 == 0) {
            outline(level, crash, center);
        }

        if (crash.instance.isMastered(owner) && crash.size >= getMaxSize(crash)) {
            if (crash.fullAt < 0L) {
                crash.fullAt = level.getGameTime();
                message(owner, Component.translatable("tracadamia.skill.attraction.collapsing").withStyle(ChatFormatting.DARK_PURPLE));
            }

            long charge = level.getGameTime() - crash.fullAt;
            float progress = Mth.clamp(charge / (CONFIG.collapseChargeSeconds * 20.0F), 0.0F, 1.0F);
            crash.clusters.forEach(cluster -> cluster.setSpin(Mth.lerp(progress, SPIN, COLLAPSE_SPIN)));
            for (int i = 0; i < 3 + (int) (progress * 10.0F); i++) {
                Vec3 out = new Vec3(level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D).normalize().scale(2.5D + level.random.nextDouble() * 2.0D);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x + out.x, center.y + out.y, center.z + out.z, 0, -out.x, -out.y, -out.z, 0.08D);
            }
            if (charge % 10 == 0) {
                playSound(level, center, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F + progress);
            }
            if (progress >= 1.0F) {
                collapse(level, crash);
                return false;
            }
        }

        if (heldTicks > 0 && heldTicks % 20 == 0) {
            instance.addMasteryPoint(owner);
        }
        return true;
    }

    private static void pullBlock(ServerLevel level, Crash crash) {
        LivingEntity owner = crash.owner;
        if (!GroundBlocks.canGrief(level, owner)) {
            return;
        }

        RandomSource random = level.random;
        double half = crash.size * 0.5D;
        double halfY = (crash.size + 1.0D) * 0.5D;
        Vec3 center = getCenter(owner);
        int top = Mth.floor(center.y + halfY);
        int bottom = Mth.floor(center.y - halfY);
        double footing = owner.getBbWidth() * 0.5D + 1.0D;
        for (int tries = 0; tries < PICK_TRIES; tries++) {
            int x = Mth.floor(center.x + (random.nextDouble() * 2.0D - 1.0D) * half);
            int z = Mth.floor(center.z + (random.nextDouble() * 2.0D - 1.0D) * half);
            boolean under = Math.abs(x + 0.5D - owner.getX()) <= footing && Math.abs(z + 0.5D - owner.getZ()) <= footing;
            for (int y = top; y >= bottom; y--) {
                if (under && y < owner.getY()) {
                    break;
                }
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (!canTake(level, crash, pos, state)) {
                    break;
                }

                float hardness = state.getDestroySpeed(level, pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                GroundBlocks.griefed(level, crash.instance, owner, pos);
                Vec3 slot = getSlot(owner, crash.held.size());
                crash.held.add(new Held(state, hardness, slot));
                crash.batch.add(new GravityClusterEntity.Part(state, slot, Vec3.atCenterOf(pos)));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.3D, 0.3D, 0.3D, 0.02D);
                playSound(level, Vec3.atCenterOf(pos), state.getSoundType().getHitSound(), 0.6F, 0.6F);
                return;
            }
        }
    }

    private static boolean canTake(ServerLevel level, Crash crash, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL && !state.getCollisionShape(level, pos).isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F && GroundBlocks.canBreak(level, crash.instance, crash.owner, pos);
    }

    private static void attractItems(ServerLevel level, Crash crash, Vec3 center) {
        double half = crash.size * 0.5D;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(half, (crash.size + 1.0D) * 0.5D, half))) {
            Vec3 offset = center.subtract(item.position());
            double distance = offset.length();
            if (distance > 0.5D) {
                item.setDeltaMovement(offset.scale(Math.min(ITEM_SPEED, distance) / distance));
                item.hasImpulse = true;
            }
        }
    }

    private static void outline(ServerLevel level, Crash crash, Vec3 center) {
        double half = crash.size * 0.5D;
        for (int i = 0; i < 4; i++) {
            double angle = level.random.nextDouble() * Mth.TWO_PI;
            double x = center.x + Math.cos(angle) * half;
            double z = center.z + Math.sin(angle) * half;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, crash.owner.getY() + 0.2D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void release(ServerLevel level, LivingEntity owner, boolean cooldown) {
        Crash crash = CRASHES.remove(owner);
        if (crash == null) {
            return;
        }

        crash.clusters.forEach(GravityClusterEntity::discard);
        if (cooldown) {
            crash.instance.setCoolDown(CONFIG.crashCooldown, crash.mode);
        }
        if (crash.held.isEmpty()) {
            return;
        }

        boolean mastered = crash.instance.isMastered(owner);
        double speed = CONFIG.crashShotSpeed;
        double multiplier = mastered ? CONFIG.crashHardnessMultiplierMastered : CONFIG.crashHardnessMultiplier;
        Vec3 center = getCenter(owner);
        int count = crash.held.size();
        for (int i = 0; i < count; i++) {
            Held held = crash.held.get(i);
            double y = 1.0D - (i + 0.5D) * 2.0D / count;
            double flat = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double angle = i * GOLDEN_ANGLE;
            Vec3 direction = new Vec3(Math.cos(angle) * flat, y * 0.6D + 0.15D, Math.sin(angle) * flat).normalize();
            Vec3 start = center.add(held.slot());
            TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, held.state(), SHOT_SCALE, start);
            level.addFreshEntity(block);
            float damage = (float) (Math.max(0.0F, held.hardness()) * multiplier * speed);
            SHOTS.add(new Shot(crash.instance, owner, crash.mode, block, direction.scale(speed / 20.0D), damage));
        }

        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 1.0D, 1.0D, 1.0D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.8F);
        playSound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.5F);
        playSound(level, center, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.5F, 0.6F);
    }

    private static void collapse(ServerLevel level, Crash crash) {
        LivingEntity owner = crash.owner;
        CRASHES.remove(owner);
        crash.clusters.forEach(GravityClusterEntity::discard);
        crash.instance.setCoolDown(CONFIG.crashCooldown, crash.mode);
        Vec3 center = getCenter(owner);

        DamageSource death = ((Skill) crash.instance.getSkill()).createSource(crash.instance, owner, TensuraDamageTypes.SUICIDE, crash.mode);
        ((TensuraDamageSource) death).tensura$setBarrierBypassLevel(2.0F);
        ((TensuraDamageSource) death).tensura$setResistanceBypassLevel(2.0F);
        owner.invulnerableTime = 0;
        owner.hurt(death, Float.MAX_VALUE);
        if (owner.isAlive()) {
            owner.kill();
        }

        AttractionBlackHole.open(level, crash.instance, owner, crash.mode, AttractionBlackHole.nextId(), center, CONFIG.collapseSize, CONFIG.collapseDamage, CONFIG.collapseSeconds);
    }

    private static boolean tickShot(Shot shot) {
        TelekinesisBlockEntity block = shot.block;
        if (block.isRemoved() || !(block.level() instanceof ServerLevel level)) {
            return true;
        }

        block.keepAlive();
        Vec3 from = block.position().add(0.0D, block.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = from.add(shot.velocity);
        AABB sweep = block.getBoundingBox().expandTowards(shot.velocity).inflate(0.2D);
        DamageSource source = ((Skill) shot.instance.getSkill()).createSource(shot.instance, shot.owner, TensuraDamageTypes.GRAVITY_ELEMENTAL, shot.mode);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != shot.owner && target.isAlive() && !target.isSpectator() && !shot.hit.contains(target)
                && HitRules.canHit(shot.owner, target))) {
            shot.hit.add(target);
            if (shot.damage > 0.0F) {
                target.invulnerableTime = 0;
                target.hurt(source, shot.damage);
            }
            target.push(shot.velocity.x * 0.4D, 0.15D, shot.velocity.z * 0.4D);
            target.hurtMarked = true;
            playSound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.6F);
            block.discard();
            return true;
        }

        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, block));
        if (hit.getType() == HitResult.Type.BLOCK || ++shot.age > SHOT_TICKS) {
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 at = hit.getLocation();
                level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 3, 0.2D, 0.2D, 0.2D, 0.02D);
                playSound(level, at, block.getBlockState().getSoundType().getBreakSound(), 0.8F, 0.7F);
            }
            block.discard();
            return true;
        }

        block.setPos(block.position().add(shot.velocity));
        return false;
    }

    public static boolean isCrashing(LivingEntity owner) {
        return CRASHES.containsKey(owner);
    }

    public static void forget(LivingEntity owner) {
        if (owner.level() instanceof ServerLevel level) {
            release(level, owner, false);
        }
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private static void playSound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CRASHES.isEmpty()) {
            CRASHES.entrySet().removeIf(entry -> {
                LivingEntity owner = entry.getKey();
                if (!owner.isAlive() || owner.isRemoved()) {
                    entry.getValue().clusters.forEach(GravityClusterEntity::discard);
                    return true;
                }
                return false;
            });
        }
        if (!SHOTS.isEmpty()) {
            SHOTS.removeIf(AttractionCrash::tickShot);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CRASHES.values().forEach(crash -> crash.clusters.forEach(GravityClusterEntity::discard));
        CRASHES.clear();
        SHOTS.forEach(shot -> shot.block.discard());
        SHOTS.clear();
    }

}

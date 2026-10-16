package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.entity.magic.barrier.BarrierEntity;
import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import io.github.manasmods.tensura.entity.magic.spike.PillarEntity;
import io.github.manasmods.tensura.entity.magic.spike.SpikeEntity;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Become My Shield
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderShield {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private static final String SHIELDED_TAG = "tracadamiaShielded";
    private static final int SHIELD_PER_TICK = 4;
    private static final double SHIELD_GAP = 1.5D;
    private static final int SHIELD_RISE = 4;
    private static final double WALL_DEPTH = 0.6D;

    private record Wall(List<PillarEntity> pillars, @Nullable Projectile projectile, ServerLevel level, Vec3 center, Vec3 normal, Vec3 side, BlockState state, int width, int height, long end) {}
    private static final List<Wall> WALLS = new ArrayList<>();

    private NewOrderShield() {
    }

    private static boolean isThreat(LivingEntity user, Projectile projectile) {
        if (projectile.isRemoved() || projectile.getPersistentData().getBoolean(SHIELDED_TAG) || projectile instanceof BeamProjectile || projectile instanceof BarrierEntity
                || projectile instanceof SpikeEntity || projectile instanceof FishingHook) {
            return false;
        }

        Entity owner = projectile.getOwner();
        if (owner == user || owner instanceof LivingEntity living && !HitRules.canHit(living, user)) {
            return false;
        }

        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < 0.04D) {
            return false;
        }

        Vec3 center = user.getBoundingBox().getCenter();
        Vec3 direction = velocity.normalize();
        double along = center.subtract(projectile.position()).dot(direction);
        if (along <= SHIELD_GAP * 0.5D) {
            return false;
        }

        Vec3 closest = projectile.position().add(direction.scale(along));
        return closest.distanceTo(center) <= user.getBbWidth() * 0.5D + user.getBbHeight() * 0.5D + 1.0D;
    }

    static void tickShield(ServerLevel level, LivingEntity user, ManasSkillInstance instance, CompoundTag entry) {
        int raised = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(CONFIG.shieldRange), projectile -> isThreat(user, projectile))) {
            if (raised >= SHIELD_PER_TICK || EnergyHelper.isOutOfEnergy(user, CONFIG.shieldAuraCost, 0.0D)) {
                return;
            }

            raiseWall(level, user, instance, projectile, getWallBlock(entry));
            projectile.getPersistentData().putBoolean(SHIELDED_TAG, true);
            raised++;
        }
    }

    static BlockState getWallBlock(CompoundTag entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.getString("block"));
        Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        return state.isAir() || !state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) ? Blocks.DIRT.defaultBlockState() : state;
    }

    private static void raiseWall(ServerLevel level, LivingEntity user, ManasSkillInstance instance, Projectile projectile, BlockState state) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 direction = velocity.normalize();
        double speed = velocity.length();
        Vec3 start = projectile.position();
        double along = user.getBoundingBox().getCenter().subtract(start).dot(direction);
        double distance = along - SHIELD_GAP;
        if (distance < speed * 1.2D) {
            distance = Math.min(speed * 1.2D, along - 0.6D);
        }
        Vec3 at = start.add(direction.scale(Math.max(0.5D, distance)));
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = user.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        int width = Math.max(1, CONFIG.shieldWallWidth);
        int height = Math.max(1, CONFIG.shieldWallHeight);
        int life = CONFIG.shieldWallSeconds * 20;
        ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, state);
        List<PillarEntity> pillars = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            double offset = i - (width - 1) * 0.5D;
            Vec3 spot = at.add(side.scale(offset));
            PillarEntity pillar = new PillarEntity(level, user);
            pillar.setPos(spot.x, spot.y - height * 0.5D, spot.z);
            pillar.setNoGravity(true);
            pillar.setLife(life);
            pillar.setBlockState(state);
            pillar.setHealth(1.0F);
            pillar.setExtendingTick(SHIELD_RISE + (int) Math.round(Math.abs(offset)));
            pillar.setHeight(height);
            pillar.setPushEntityUp(false);
            pillar.setSkill(instance);
            level.addFreshEntity(pillar);
            pillars.add(pillar);
            for (int grain = 0; grain < 6; grain++) {
                level.sendParticles(dust, spot.x + (level.random.nextDouble() - 0.5D) * 0.8D, pillar.getY(), spot.z + (level.random.nextDouble() - 0.5D) * 0.8D, 0, 0.0D, 1.0D, 0.0D, 1.0D);
            }
        }
        WALLS.add(new Wall(pillars, projectile, level, at, flat, side, state, width, height, level.getGameTime() + life));

        level.sendParticles(dust, at.x, at.y - height * 0.5D, at.z, 20, width * 0.4D, 0.2D, width * 0.4D, 0.1D);
        NewOrderUtil.playSound(level, at, TensuraSoundEvents.CAST_EARTH.get(), 1.0F, 1.2F);
        NewOrderUtil.playSound(level, at, state.getSoundType().getPlaceSound(), 1.0F, 0.8F);
        if (level.random.nextInt(3) == 0) {
            instance.addMasteryPoint(user);
        }
    }

    private static boolean isCrossing(Wall wall) {
        Projectile projectile = wall.projectile();
        if (projectile == null || projectile.isRemoved() || projectile.level() != wall.level()) {
            return false;
        }

        Vec3 offset = projectile.getBoundingBox().getCenter().subtract(wall.center());
        double along = offset.dot(wall.normal());
        double next = along + projectile.getDeltaMovement().dot(wall.normal());
        if (along > WALL_DEPTH || next < -WALL_DEPTH) {
            return false;
        }

        return Math.abs(offset.dot(wall.side())) <= wall.width() * 0.5D + 0.5D && Math.abs(offset.y) <= wall.height() * 0.5D + 0.5D;
    }

    private static boolean tickWall(Wall wall, long time) {
        boolean broken = wall.pillars().stream().anyMatch(PillarEntity::isRemoved);
        if (!broken && isCrossing(wall)) {
            wall.projectile().discard();
            broken = true;
        }
        if (!broken) {
            return false;
        }

        for (PillarEntity pillar : wall.pillars()) {
            if (!pillar.isRemoved()) {
                pillar.discard();
            }
        }
        if (time >= wall.end() - 2L) {
            crumble(wall);
        } else {
            burst(wall);
        }
        return true;
    }

    private static Vec3 randomSpot(Wall wall) {
        RandomSource random = wall.level().random;
        return wall.center().add(wall.side().scale((random.nextDouble() - 0.5D) * wall.width())).add(0.0D, (random.nextDouble() - 0.5D) * wall.height(), 0.0D);
    }

    private static void burst(Wall wall) {
        ServerLevel level = wall.level();
        RandomSource random = level.random;
        ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, wall.state());
        for (int i = 0; i < 24 * wall.width(); i++) {
            Vec3 at = randomSpot(wall);
            Vec3 fly = wall.normal().scale(-(0.4D + random.nextDouble())).add(wall.side().scale((random.nextDouble() - 0.5D) * 0.8D)).add(0.0D, 0.2D + random.nextDouble() * 0.6D, 0.0D);
            level.sendParticles(dust, at.x, at.y, at.z, 0, fly.x, fly.y, fly.z, 1.0D);
        }
        Vec3 center = wall.center();
        for (int i = 0; i < wall.width(); i++) {
            Vec3 at = randomSpot(wall);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 12 * wall.width(), wall.width() * 0.3D, wall.height() * 0.3D, wall.width() * 0.3D, 0.08D);
        NewOrderUtil.playSound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.6F);
        NewOrderUtil.playSound(level, center, wall.state().getSoundType().getBreakSound(), 1.5F, 0.7F);
    }

    private static void crumble(Wall wall) {
        ParticleOptions dust = new BlockParticleOption(ParticleTypes.BLOCK, wall.state());
        for (int i = 0; i < 12 * wall.width(); i++) {
            Vec3 at = randomSpot(wall);
            wall.level().sendParticles(dust, at.x, at.y, at.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!WALLS.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            WALLS.removeIf(wall -> tickWall(wall, time));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WALLS.clear();
    }

}

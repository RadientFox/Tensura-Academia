package com.radient.tensuraacadamia.ability.unique.quirks.attraction;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
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

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AttractionOrbit {

    private static final QuirkSkillsConfig.Attraction CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Attraction;

    private static final float ORBIT_SCALE = 0.6F;

    private static final class Guard {
        private final TelekinesisBlockEntity block;
        private int hits;

        private Guard(TelekinesisBlockEntity block, int hits) {
            this.block = block;
            this.hits = hits;
        }
    }

    private record Orbit(ManasSkillInstance instance, LivingEntity owner, List<Guard> guards, long until) {}

    private static final Map<LivingEntity, Orbit> ORBITS = new HashMap<>();

    private AttractionOrbit() {
    }

    public static boolean summon(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode) {
        if (ORBITS.containsKey(owner)) {
            fail(owner, Component.translatable("tracadamia.skill.attraction.orbiting"));
            return false;
        }

        int count = instance.isMastered(owner) ? CONFIG.orbitBlocksMastered : CONFIG.orbitBlocks;
        List<BlockPos> picks = findBlocks(level, instance, owner, count);
        if (picks.isEmpty()) {
            fail(owner, Component.translatable("tracadamia.skill.attraction.no_blocks"));
            return false;
        }
        if (EnergyHelper.isOutOfEnergy(owner, instance, mode)) {
            return false;
        }

        List<Guard> guards = new ArrayList<>();
        double phase = level.random.nextDouble() * Math.PI * 2.0D;
        for (int slot = 0; slot < picks.size(); slot++) {
            BlockPos pos = picks.get(slot);
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            GroundBlocks.griefed(level, instance, owner, pos);
            TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, ORBIT_SCALE, Vec3.atCenterOf(pos));
            block.hold(owner, TelekinesisBlockEntity.HOLD_ORBIT, slot, picks.size(), phase);
            level.addFreshEntity(block);
            guards.add(new Guard(block, hardness >= CONFIG.orbitHardHardness ? CONFIG.orbitHardHits : 1));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.03D);
        }

        ORBITS.put(owner, new Orbit(instance, owner, guards, level.getGameTime() + CONFIG.orbitSeconds * 20L));
        instance.setCoolDown(CONFIG.orbitCooldown, mode);
        instance.addMasteryPoint(owner);
        owner.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, owner.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
        playSound(level, owner.position(), TensuraSoundEvents.CAST_EARTH.get(), 1.0F, 1.2F);
        return true;
    }

    private static List<BlockPos> findBlocks(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int count) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos feet = owner.blockPosition();
        int reach = (int) Math.ceil(CONFIG.orbitRange);
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-reach, -reach, -reach), feet.offset(reach, reach, reach))) {
            if (pos.distSqr(feet) > CONFIG.orbitRange * CONFIG.orbitRange || isUnder(owner, pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(level, pos)
                    && state.getDestroySpeed(level, pos) >= 0.0F && level.getBlockState(pos.above()).isAir() && GroundBlocks.canBreak(level, instance, owner, pos)) {
                found.add(pos.immutable());
            }
        }

        found.sort(Comparator.comparingDouble((BlockPos pos) -> -level.getBlockState(pos).getDestroySpeed(level, pos)).thenComparingDouble(pos -> pos.distSqr(feet)));
        return found.subList(0, Math.min(count, found.size()));
    }

    private static boolean isUnder(LivingEntity owner, BlockPos pos) {
        double reach = owner.getBbWidth() * 0.5D + 1.0D;
        return pos.getY() < owner.getY() && Math.abs(pos.getX() + 0.5D - owner.getX()) <= reach && Math.abs(pos.getZ() + 0.5D - owner.getZ()) <= reach;
    }

    public static boolean isOrbiting(LivingEntity owner) {
        return ORBITS.containsKey(owner);
    }

    public static void dismiss(ServerLevel level, LivingEntity owner) {
        Orbit orbit = ORBITS.remove(owner);
        if (orbit != null) {
            drop(orbit);
            playSound(level, owner.position(), TensuraSoundEvents.GENERIC_UNCAST.get(), 1.0F, 1.0F);
        }
    }

    private static @Nullable Vec3 getFrom(DamageSource source, LivingEntity owner) {
        if (source.getEntity() == owner) {
            return null;
        }
        if (source.getDirectEntity() != null) {
            return source.getDirectEntity().position().add(0.0D, source.getDirectEntity().getBbHeight() * 0.5D, 0.0D);
        }
        return source.getSourcePosition();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity owner = event.getEntity();
        Orbit orbit = ORBITS.get(owner);
        DamageSource source = event.getSource();
        if (orbit == null || orbit.guards().isEmpty() || event.getAmount() <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(owner.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 from = getFrom(source, owner);
        if (from == null) {
            return;
        }

        Guard guard = orbit.guards().stream().min(Comparator.comparingDouble(block -> block.block.position().distanceToSqr(from))).orElseThrow();
        event.setCanceled(true);
        guard.hits--;
        Vec3 at = guard.block.position().add(0.0D, guard.block.getBbHeight() * 0.5D, 0.0D);
        if (guard.hits > 0) {
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2D, 0.2D, 0.2D, 0.2D);
            playSound(level, at, SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F);
            return;
        }

        orbit.guards().remove(guard);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 6, 0.2D, 0.2D, 0.2D, 0.03D);
        playSound(level, at, guard.block.getBlockState().getSoundType().getBreakSound(), 1.2F, 0.8F);
        playSound(level, at, SoundEvents.SHIELD_BREAK, 0.8F, 1.0F);
        guard.block.discard();
        if (orbit.guards().isEmpty()) {
            ORBITS.remove(owner);
            if (owner instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.attraction.orbit_broken").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    private static boolean tickOrbit(Orbit orbit) {
        LivingEntity owner = orbit.owner();
        boolean over = !owner.isAlive() || owner.isRemoved() || owner.level().getGameTime() >= orbit.until();
        orbit.guards().removeIf(guard -> guard.block.isRemoved());
        if (over || orbit.guards().isEmpty()) {
            drop(orbit);
            return true;
        }

        orbit.guards().forEach(guard -> guard.block.keepAlive());
        return false;
    }

    private static void drop(Orbit orbit) {
        for (Guard guard : orbit.guards()) {
            if (guard.block.level() instanceof ServerLevel level) {
                Vec3 at = guard.block.position();
                level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3D, at.z, 4, 0.2D, 0.2D, 0.2D, 0.02D);
            }
            guard.block.discard();
        }
        orbit.guards().clear();
    }

    public static void forget(LivingEntity owner) {
        Orbit orbit = ORBITS.remove(owner);
        if (orbit != null) {
            drop(orbit);
        }
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    private static void playSound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ORBITS.isEmpty()) {
            ORBITS.values().removeIf(AttractionOrbit::tickOrbit);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ORBITS.values().forEach(AttractionOrbit::drop);
        ORBITS.clear();
    }

}

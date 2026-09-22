package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.network.PermeationPhasePayload;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class PermeationQuirk extends Skill {
    private static final int MAX_MASTERY = 2_500;
    private static final int BLINDER_UNLOCK_MASTERY = 1_250;
    private static final int BLINDER_COOLDOWN = 8;
    private static final Map<UUID, PhaseState> PHASING = new HashMap<>();
    private static final Map<UUID, BlinderRush> BLINDER_RUSHES = new HashMap<>();

    private static final class BlinderRush {
        private final ServerPlayer owner;
        private final UUID targetId;
        private int ticksRemaining = 30;

        private BlinderRush(ServerPlayer owner, LivingEntity target) {
            this.owner = owner;
            this.targetId = target.getUUID();
        }
    }

    private record PhaseState(ServerPlayer player, boolean originalNoPhysics) {
    }

    public PermeationQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/permeationicon.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != 0 || entity.level().isClientSide || !(entity instanceof ServerPlayer player)) return mode == 0;
        BLINDER_RUSHES.remove(player.getUUID());
        boolean enteringPhase = !PHASING.containsKey(player.getUUID());
        enterPhase(player);
        if (enteringPhase) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(motion.y, -0.22D), motion.z);
            player.setOnGround(false);
            player.hurtMarked = true;
        }
        player.resetFallDistance();
        if (player.serverLevel().getGameTime() % 4L == 0L) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + player.getBbHeight() * 0.5D,
                    player.getZ(), 5, 0.25D, 0.45D, 0.25D, 0.01D);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != 0 || entity.level().isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (PHASING.containsKey(player.getUUID())) eject(player, 50.0F, null);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= BLINDER_UNLOCK_MASTERY ? 2 : 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "permeation.permeate";
            case 1 -> "permeation.blinder_touch";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode != 1 || mode >= getModes(instance)
                || instance.onCoolDown(mode)) return;
        LivingEntity target = lookedAtTarget(player, 28.0D);
        if (target == null) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.permeation.no_target"), true);
            return;
        }
        leavePhase(player);
        enterPhase(player);
        BLINDER_RUSHES.put(player.getUUID(), new BlinderRush(player, target));
        instance.addMasteryPoint(player);
        instance.setCoolDown(BLINDER_COOLDOWN, mode);
    }

    @SubscribeEvent
    public static void cancelDamageWhilePermeating(LivingIncomingDamageEvent event) {
        if (PHASING.containsKey(event.getEntity().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, BlinderRush>> iterator = BLINDER_RUSHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BlinderRush> entry = iterator.next();
            BlinderRush rush = entry.getValue();
            ServerPlayer player = rush.owner;
            if (!player.isAlive()) {
                leavePhase(player);
                iterator.remove();
                continue;
            }
            LivingEntity target = player.serverLevel().getEntity(rush.targetId) instanceof LivingEntity living && living.isAlive()
                    ? living : null;
            if (target == null) {
                eject(player, 0.0F, null);
                iterator.remove();
                continue;
            }

            Vec3 targetPosition = target.position();
            Vec3 horizontal = new Vec3(targetPosition.x - player.getX(), 0.0D, targetPosition.z - player.getZ());
            if (horizontal.lengthSqr() > 0.04D) {
                Vec3 step = horizontal.normalize().scale(Math.min(1.5D, horizontal.length()));
                phaseTeleport(player, new Vec3(player.getX() + step.x, Math.min(player.getY(), targetPosition.y - 2.0D),
                        player.getZ() + step.z));
                player.setDeltaMovement(step.x, 0.0D, step.z);
                player.hurtMarked = true;
            }
            if (horizontal.lengthSqr() <= 2.25D || --rush.ticksRemaining <= 0) {
                Vec3 ejection = eject(player, 0.0F, target);
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().source(DamageTypes.WIND_CHARGE, player), 100.0F);
                target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 100, 2));
                target.setDeltaMovement(target.getDeltaMovement().x, 1.25D, target.getDeltaMovement().z);
                target.hurtMarked = true;
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, ejection.x, ejection.y + 0.8D, ejection.z,
                        12, 0.8D, 0.35D, 0.8D, 0.08D);
                iterator.remove();
            }
        }
    }

    private static Vec3 eject(ServerPlayer player, float damage, @Nullable LivingEntity excluded) {
        ServerLevel level = player.serverLevel();
        Vec3 ejection = findSurface(level, player);
        Vec3 motion = player.getDeltaMovement();
        leavePhase(player);
        player.connection.teleport(ejection.x, ejection.y, ejection.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(motion.x, 1.35D, motion.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        if (damage > 0.0F) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(ejection, ejection).inflate(1.5D), entity -> entity != player && entity != excluded && entity.isAlive())) {
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().source(DamageTypes.WIND_CHARGE, player), damage);
            }
        }
        level.playSound(null, BlockPos.containing(ejection), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 1.25F);
        level.sendParticles(ParticleTypes.END_ROD, ejection.x, ejection.y + 0.8D, ejection.z, 28, 0.65D, 0.35D, 0.65D, 0.08D);
        return ejection;
    }

    private static void phaseTeleport(ServerPlayer player, Vec3 destination) {
        player.connection.teleport(destination.x, destination.y, destination.z, player.getYRot(), player.getXRot());
    }

    private static void enterPhase(ServerPlayer player) {
        if (PHASING.putIfAbsent(player.getUUID(), new PhaseState(player, player.noPhysics)) == null) {
            syncPhase(player, true);
        }
        player.noPhysics = true;
    }

    private static void leavePhase(ServerPlayer player) {
        PhaseState state = PHASING.remove(player.getUUID());
        player.noPhysics = state != null && state.originalNoPhysics();
        syncPhase(player, false);
    }

    private static void syncPhase(ServerPlayer player, boolean active) {
        PermeationPhasePayload payload = new PermeationPhasePayload(player.getId(), active);
        AABB area = player.getBoundingBox().inflate(128.0D);
        for (ServerPlayer viewer : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, area)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static Vec3 findSurface(ServerLevel level, ServerPlayer player) {
        Vec3 current = player.position();
        AABB box = player.getBoundingBox();
        for (int y = Math.max(level.getMinBuildHeight(), player.getBlockY()); y < level.getMaxBuildHeight(); y++) {
            Vec3 candidate = new Vec3(current.x, y, current.z);
            if (level.noCollision(player, box.move(candidate.subtract(current)))) return candidate;
        }
        return current;
    }

    private static @Nullable LivingEntity lookedAtTarget(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().normalize().scale(range));
        LivingEntity selected = null;
        double closest = range * range;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(2.0D),
                entity -> entity != player && entity.isAlive())) {
            var hit = target.getBoundingBox().inflate(0.4D).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < closest) {
                selected = target;
                closest = start.distanceToSqr(hit.get());
            }
        }
        return selected;
    }
}

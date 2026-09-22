package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.network.AcceleratorRingsFlightPayload;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AcceleratorRingsQuirk extends Skill {
    private static final int MAX_RINGS = 5;
    private static final int TICKS_PER_RING = 20;
    private static final double NORMAL_COOLDOWN_PER_RING = 1.5D;
    private static final double MASTERED_COOLDOWN_PER_RING = 1.0D;
    private static final double DISTANCE_PER_RING = 75.0D;
    private static final double BASE_DASH_SPEED = 2.0D;
    private static final double DASH_SPEED_PER_RING = 0.5D;
    private static final double STEERING_RESPONSE = 0.18D;
    private static final double FLIGHT_SYNC_RANGE = 128.0D;
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, Acceleration> ACCELERATIONS = new HashMap<>();
    private static final DustParticleOptions RING_PARTICLE = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.08F), 1.15F);

    private static final class Charge {
        private int rings;
        private long lastHeldAt;
    }

    private static final class Acceleration {
        private final ServerPlayer owner;
        private Vec3 direction;
        private final int damageRings;
        private final boolean pushEnabled;
        private final double speed;
        private final int ticksPerRing;
        private final Map<UUID, Boolean> pushedTargets = new HashMap<>();
        private int ringsRemaining;
        private int ticksOnRing;

        private Acceleration(ServerPlayer owner, Vec3 direction, int rings, boolean pushEnabled) {
            this.owner = owner;
            this.direction = direction;
            this.damageRings = rings;
            this.ringsRemaining = rings;
            this.pushEnabled = pushEnabled;
            this.speed = BASE_DASH_SPEED + DASH_SPEED_PER_RING * rings;
            this.ticksPerRing = (int) Math.ceil(DISTANCE_PER_RING / this.speed);
            this.ticksOnRing = ticksPerRing;
        }
    }

    public AcceleratorRingsQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/accelerationringsicon.png");
    }

    @Override
    public int getMaxMastery() {
        return 2_500;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 20_000.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.accelerator_rings.push_on"), true);
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.accelerator_rings.push_off"), true);
        }
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 0 ? "accelerator_rings.accel" : super.getModeId(instance, mode);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != 0) return false;
        if (entity.level().isClientSide) return true;
        if (!(entity instanceof ServerPlayer player)) return false;

        long now = player.serverLevel().getGameTime();
        Charge charge = CHARGES.computeIfAbsent(player.getUUID(), ignored -> new Charge());
        if (heldTicks <= 1) charge.rings = 0;
        charge.lastHeldAt = now;

        if (heldTicks > 0 && heldTicks % TICKS_PER_RING == 0 && charge.rings < MAX_RINGS) {
            charge.rings++;
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.65F, 1.15F + charge.rings * 0.08F);
            instance.addMasteryPoint(player);
        }

        if (!player.onGround()) {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.resetFallDistance();
        }
        if (now % 3L == 0L && charge.rings > 0) emitChargeRings(player.serverLevel(), player, charge.rings, now);
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != 0 || !(entity instanceof ServerPlayer player) || entity.level().isClientSide) return;
        Charge charge = CHARGES.remove(player.getUUID());
        int rings = charge == null ? 0 : charge.rings;
        if (rings <= 0) return;

        Vec3 direction = player.getLookAngle().normalize();
        Acceleration acceleration = new Acceleration(player, direction, rings, instance.isToggled());
        ACCELERATIONS.put(player.getUUID(), acceleration);
        syncFlightAnimation(player, acceleration.ringsRemaining * acceleration.ticksPerRing);
        double cooldownPerRing = instance.isMastered(player) ? MASTERED_COOLDOWN_PER_RING : NORMAL_COOLDOWN_PER_RING;
        instance.setCoolDown((int) Math.round(rings * cooldownPerRing), 0);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.0F, 1.1F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        CHARGES.entrySet().removeIf(entry -> now - entry.getValue().lastHeldAt > 2L);

        Iterator<Acceleration> iterator = ACCELERATIONS.values().iterator();
        while (iterator.hasNext()) {
            Acceleration acceleration = iterator.next();
            ServerPlayer player = acceleration.owner;
            if (!player.isAlive()) {
                syncFlightAnimation(player, 0);
                iterator.remove();
                continue;
            }

            Vec3 lookedDirection = player.getLookAngle().normalize();
            acceleration.direction = acceleration.direction.scale(1.0D - STEERING_RESPONSE)
                    .add(lookedDirection.scale(STEERING_RESPONSE)).normalize();
            player.setDeltaMovement(acceleration.direction.scale(acceleration.speed));
            player.hurtMarked = true;
            player.resetFallDistance();
            if (now % 2L == 0L) emitLaunchRing(player.serverLevel(), player, acceleration.ringsRemaining, now);

            if (acceleration.pushEnabled && pushHitTargets(acceleration)) {
                syncFlightAnimation(player, 0);
                iterator.remove();
                continue;
            }

            if (--acceleration.ticksOnRing <= 0) {
                acceleration.ringsRemaining--;
                acceleration.ticksOnRing = acceleration.ticksPerRing;
                if (acceleration.ringsRemaining <= 0) {
                    syncFlightAnimation(player, 0);
                    iterator.remove();
                }
            }
        }
    }

    private static boolean pushHitTargets(Acceleration acceleration) {
        ServerPlayer player = acceleration.owner;
        AABB hitbox = player.getBoundingBox().inflate(0.75D).expandTowards(acceleration.direction.scale(acceleration.speed));
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, hitbox,
                entity -> entity != player && entity.isAlive() && !acceleration.pushedTargets.containsKey(entity.getUUID()))) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().source(DamageTypes.WIND_CHARGE, player), 50.0F * acceleration.damageRings);
            target.setDeltaMovement(acceleration.direction.scale(acceleration.speed));
            target.hurtMarked = true;
            acceleration.pushedTargets.put(target.getUUID(), Boolean.TRUE);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            return true;
        }
        return false;
    }

    private static void syncFlightAnimation(ServerPlayer player, int duration) {
        AcceleratorRingsFlightPayload payload = new AcceleratorRingsFlightPayload(player.getId(), duration);
        AABB area = player.getBoundingBox().inflate(FLIGHT_SYNC_RANGE);
        for (ServerPlayer viewer : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, area)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private static void emitChargeRings(ServerLevel level, LivingEntity entity, int rings, long time) {
        Vec3 forward = entity.getLookAngle().normalize();
        for (int ring = 0; ring < rings; ring++) {
            emitRing(level, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                    forward, 0.75D + ring * 0.34D, time * 0.12D + ring * 0.45D);
        }
    }

    private static void emitLaunchRing(ServerLevel level, LivingEntity entity, int rings, long time) {
        emitRing(level, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(),
                entity.getLookAngle().normalize(), 0.9D + rings * 0.14D, time * 0.20D);
    }

    private static void emitRing(ServerLevel level, double x, double y, double z, Vec3 forward, double radius, double rotation) {
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        for (int point = 0; point < 16; point++) {
            double angle = rotation + Math.PI * 2.0D * point / 16.0D;
            Vec3 position = new Vec3(x, y, z).add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            level.sendParticles(RING_PARTICLE, position.x, position.y, position.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.network.WarpGateOpenPayload;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.subclass.ISpatialMovement;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WarpGateQuirk extends Skill implements ISpatialMovement {
    private static final int GATE_TICKS = 600;
    private static final int OVERUSE_TICKS = 1200;
    private static final String OVERUSE = "TracadamiaWarpOveruse";
    private static final String TRAVEL = "TracadamiaWarpTravel";
    private static final String REDIRECTED = "TracadamiaWarpRedirected";
    private static final DustParticleOptions PURPLE = new DustParticleOptions(Vec3.fromRGB24(0x49275E).toVector3f(), 1.2F);
    private static final DustParticleOptions BLACK = new DustParticleOptions(Vec3.fromRGB24(0x09070D).toVector3f(), 1.7F);
    private static final DustParticleOptions MASTERED = new DustParticleOptions(Vec3.fromRGB24(0xBDDFFF).toVector3f(), 0.9F);
    private static final Map<UUID, Gate> GATES = new HashMap<>();
    private static final Map<UUID, Long> REDIRECT_HELD = new HashMap<>();
    private static final Map<UUID, MenuSession> MENU_SESSIONS = new HashMap<>();

    private record MenuSession(long token, long expires) {}

    private static final class Gate {
        final UUID owner;
        final ServerLevel source;
        final ServerLevel destination;
        final Vec3 entrance;
        final Vec3 facing;
        final Vec3 right;
        final Vec3 exit;
        final long expires;
        final boolean mastered;
        final Set<UUID> inside = new HashSet<>();

        Gate(ServerPlayer player, ServerLevel destination, Vec3 entrance, Vec3 exit, long expires, boolean mastered) {
            owner = player.getUUID();
            source = player.serverLevel();
            this.destination = destination;
            this.entrance = entrance;
            this.exit = exit;
            this.expires = expires;
            this.mastered = mastered;
            Vec3 horizontal = player.getLookAngle().multiply(1, 0, 1).normalize();
            facing = horizontal.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : horizontal;
            right = new Vec3(-facing.z, 0, facing.x);
        }

        boolean contains(Entity entity) {
            Vec3 relative = entity.getBoundingBox().getCenter().subtract(entrance);
            return Math.abs(relative.dot(facing)) <= 0.85 && Math.abs(relative.dot(right)) <= 1.45
                    && Math.abs(relative.y) <= 1.8;
        }
    }

    public WarpGateQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/warpgateicon.png");
    }
    @Override public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.warp_gate.description");
    }
    @Override public int getModes(ManasSkillInstance instance) { return 2; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 2);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 0 ? "warp_gate.gate" : mode == 1 ? "warp_gate.redirect" : super.getModeId(instance, mode);
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public boolean canWarp(ManasSkillInstance instance, LivingEntity owner) { return false; }
    @Override public boolean canPortal(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public boolean canDimensionTravel(ManasSkillInstance instance, LivingEntity owner) { return true; }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (mode != 0 || !(owner instanceof ServerPlayer player)) return;
        if (player.isShiftKeyDown()) {
            if (GATES.remove(player.getUUID()) != null) {
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.PLAYERS, 0.6F, 0.6F);
                player.displayClientMessage(Component.literal("Gate closed."), true);
            }
        } else openDestinationMenu(player);
    }

    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (mode != 1 || !(owner instanceof ServerPlayer player)) return false;
        REDIRECT_HELD.put(player.getUUID(), player.serverLevel().getGameTime() + 2);
        return true;
    }

    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) {
        if (mode == 1) REDIRECT_HELD.remove(owner.getUUID());
    }

    private static void openDestinationMenu(ServerPlayer player) {
        long token = ThreadLocalRandom.current().nextLong();
        MENU_SESSIONS.put(player.getUUID(), new MenuSession(token, player.serverLevel().getGameTime() + 6000));
        PacketDistributor.sendToPlayer(player, new WarpGateOpenPayload(token,
                player.server.levelKeys().stream().map(key -> key.location().toString()).sorted().toList()));
    }

    public static void openGate(ServerPlayer player, long token, String dimension, int x, int y, int z) {
        MenuSession session = MENU_SESSIONS.get(player.getUUID());
        if (session == null || session.token != token || session.expires < player.serverLevel().getGameTime()
                || SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.WARP_GATE.get()).isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        ServerLevel destination = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        BlockPos exitBlock = new BlockPos(x, y, z);
        if (destination == null || y < destination.getMinBuildHeight() || y + 2 > destination.getMaxBuildHeight()
                || !destination.getWorldBorder().isWithinBounds(exitBlock)) {
            player.displayClientMessage(Component.literal("Invalid destination dimension or coordinates."), true);
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 forward = player.getLookAngle().normalize();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye, eye.add(forward.scale(3)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 entrance = hit.getType() == HitResult.Type.BLOCK
                ? hit.getLocation().subtract(forward.scale(0.35)).add(0, 0.2, 0)
                : eye.add(forward.scale(3)).add(0, -0.5, 0);
        Vec3 exit = new Vec3(x + 0.5, y, z + 0.5);
        var instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.WARP_GATE.get()).orElseThrow();
        long now = player.serverLevel().getGameTime();
        double distance = entrance.distanceTo(exit);
        double base = distance * (instance.isMastered(player) ? 3 : 5);
        if (player.serverLevel() != destination) base = base * 10 + 10_000;
        double cost = Math.ceil(base * Math.pow(3, overuseStacks(player, now)));
        if (!Double.isFinite(cost) || !pay(player, cost)) {
            player.displayClientMessage(Component.literal("Not enough aura to open Gate."), true);
            return;
        }
        GATES.put(player.getUUID(), new Gate(player, destination, entrance, exit,
                now + GATE_TICKS, instance.isMastered(player)));
        double total = player.getPersistentData().getDouble(TRAVEL) + distance;
        int earned = Math.min(64, (int) (total / 3000));
        player.getPersistentData().putDouble(TRAVEL, total - earned * 3000);
        if (earned > 0) {
            long[] old = player.getPersistentData().getLongArray(OVERUSE);
            long[] updated = java.util.Arrays.copyOf(old, old.length + earned);
            java.util.Arrays.fill(updated, old.length, updated.length, now + OVERUSE_TICKS);
            player.getPersistentData().putLongArray(OVERUSE, updated);
            int count = overuseStacks(player, now);
            player.displayClientMessage(Component.literal("Warp Overuse: " + count + " stack" + (count == 1 ? "" : "s") + "."), true);
        }
        MENU_SESSIONS.remove(player.getUUID());
        player.serverLevel().playSound(null, entrance.x, entrance.y, entrance.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.7F);
        instance.addMasteryPoint(player);
        player.displayClientMessage(Component.literal("Gate opened for 30 seconds. Shift-use Gate to close it."), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void shroudDamage(LivingIncomingDamageEvent event) {
        LivingEntity owner = event.getEntity();
        if (owner.level().isClientSide || event.getSource().getEntity() == null
                && !(event.getSource().getDirectEntity() instanceof Projectile)) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).orElse(null);
        if (instance == null || !instance.isToggled()) return;
        if (owner.getRandom().nextFloat() < 0.8F) {
            event.setCanceled(true);
            instance.addMasteryPoint(owner);
        } else event.setAmount(event.getAmount() * 1.05F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void redirectImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ServerPlayer player
                && redirect(player, event.getProjectile(), true)) event.setCanceled(true);
    }

    public static void registerSkillEvents() {
        EntityEvents.PROJECTILE_HIT.register((hit, projectile, deflection, result) -> {
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer player
                    && redirect(player, projectile, true)) result.set(EntityEvents.ProjectileHitResult.PASS);
        });
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        Iterator<Gate> gates = GATES.values().iterator();
        while (gates.hasNext()) {
            Gate gate = gates.next();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(gate.owner);
            if (owner == null || !owner.isAlive() || owner.serverLevel() != gate.source || now >= gate.expires
                    || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).isEmpty()) {
                gates.remove();
                continue;
            }
            renderGate(gate, now);
            gate.inside.removeIf(id -> {
                Entity entity = gate.source.getEntity(id);
                return entity == null || !gate.contains(entity);
            });
            for (LivingEntity entrant : gate.source.getEntitiesOfClass(LivingEntity.class,
                    new AABB(gate.entrance.add(-2, -2, -2), gate.entrance.add(2, 2, 2)),
                    entity -> entity.isAlive() && gate.contains(entity) && !gate.inside.contains(entity.getUUID()))) {
                gate.inside.add(entrant.getUUID());
                travel(owner, gate, entrant);
            }
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ManasSkillInstance instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.WARP_GATE.get()).orElse(null);
            if (instance != null && instance.isToggled() && player.isAlive()) renderShroud(player, instance.isMastered(player), now);
            if (now % 20 == 0 && (player.getPersistentData().contains(OVERUSE)
                    || player.hasEffect(MHAEffects.WARP_OVERUSE))) overuseStacks(player, now);
        }
        REDIRECT_HELD.entrySet().removeIf(entry -> entry.getValue() < now);
        for (UUID id : REDIRECT_HELD.keySet()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive()) continue;
            for (Projectile projectile : player.serverLevel().getEntitiesOfClass(Projectile.class,
                    player.getBoundingBox().inflate(3), shot -> shot.getOwner() != player
                            && !shot.getPersistentData().getBoolean(REDIRECTED))) redirect(player, projectile, false);
        }
    }

    private static void travel(ServerPlayer owner, Gate gate, LivingEntity entrant) {
        if (!entrant.teleportTo(gate.destination, gate.exit.x, gate.exit.y, gate.exit.z,
                Set.of(), entrant.getYRot(), entrant.getXRot())) return;
        entrant.fallDistance = 0;
        gate.destination.sendParticles(BLACK, gate.exit.x, gate.exit.y + 1, gate.exit.z,
                30, 0.55, 0.8, 0.55, 0);
        gate.destination.sendParticles(ParticleTypes.SMOKE, gate.exit.x, gate.exit.y + 1, gate.exit.z,
                12, 0.5, 0.7, 0.5, 0.02);
        gate.source.playSound(null, gate.entrance.x, gate.entrance.y, gate.entrance.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1, 0.75F);
        SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).ifPresent(skill -> skill.addMasteryPoint(owner));
    }

    private static int overuseStacks(ServerPlayer player, long now) {
        long[] old = player.getPersistentData().getLongArray(OVERUSE);
        ArrayList<Long> active = new ArrayList<>(old.length);
        long longest = 0;
        boolean shortened = false;
        for (long expiry : old) if (expiry > now) {
            long capped = Math.min(expiry, now + OVERUSE_TICKS);
            shortened |= capped != expiry;
            active.add(capped);
            longest = Math.max(longest, capped);
        }
        if (shortened || active.size() != old.length) player.getPersistentData().putLongArray(OVERUSE,
                active.stream().mapToLong(Long::longValue).toArray());
        if (active.isEmpty()) player.removeEffect(MHAEffects.WARP_OVERUSE);
        else if (player.getEffect(MHAEffects.WARP_OVERUSE) == null
                || player.getEffect(MHAEffects.WARP_OVERUSE).getAmplifier() != active.size() - 1
                || player.getEffect(MHAEffects.WARP_OVERUSE).getDuration() > longest - now + 20)
            player.addEffect(new MobEffectInstance(MHAEffects.WARP_OVERUSE,
                    (int) Math.min(Integer.MAX_VALUE, longest - now), active.size() - 1, false, true, false));
        return active.size();
    }

    private static boolean redirect(ServerPlayer owner, Projectile shot, boolean directHit) {
        long now = owner.serverLevel().getGameTime();
        if (REDIRECT_HELD.getOrDefault(owner.getUUID(), 0L) < now || shot.getOwner() == owner
                || shot.getPersistentData().getBoolean(REDIRECTED)) return false;
        Entity shooter = shot.getOwner();
        if (shooter == null || shooter == owner || shooter.level() != owner.level()) return false;
        Vec3 approaching = owner.getEyePosition().subtract(shot.position());
        if (approaching.lengthSqr() > 16 || !directHit && shot.getDeltaMovement().dot(approaching) <= 0) return false;
        double damage = shot instanceof TensuraProjectile tensura ? tensura.getDamage() + tensura.getSecondaryDamage()
                : shot instanceof AbstractArrow arrow ? arrow.getBaseDamage() : 5;
        double cost = Math.max(0, damage) * 10 * Math.pow(3, overuseStacks(owner, now));
        if (!pay(owner, cost)) return false;
        Vec3 direction = shooter.getBoundingBox().getCenter().subtract(owner.getEyePosition()).normalize();
        if (direction.lengthSqr() < 0.01) { refund(owner, cost); return false; }
        Vec3 exit = owner.getEyePosition().add(direction.scale(1.8));
        owner.serverLevel().sendParticles(BLACK, shot.getX(), shot.getY(), shot.getZ(),
                18, 0.3, 0.3, 0.3, 0);
        owner.serverLevel().sendParticles(BLACK, exit.x, exit.y, exit.z, 18, 0.3, 0.5, 0.3, 0);
        owner.serverLevel().sendParticles(PURPLE, exit.x, exit.y, exit.z, 5, 0.3, 0.5, 0.3, 0);
        shot.getPersistentData().putBoolean(REDIRECTED, true);
        shot.setOwner(owner);
        shot.setPos(exit);
        shot.shoot(direction.x, direction.y, direction.z,
                (float) Math.max(2.5, shot.getDeltaMovement().length() * 1.75), 0);
        shot.hurtMarked = true;
        SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).ifPresent(skill -> skill.addMasteryPoint(owner));
        return true;
    }

    private static boolean pay(ServerPlayer owner, double cost) {
        var energy = TensuraStorages.getExistenceFrom(owner);
        if (energy.getAura() < cost) return false;
        energy.setAura(energy.getAura() - cost);
        energy.markDirty();
        return true;
    }

    private static void refund(ServerPlayer owner, double cost) {
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setAura(energy.getAura() + cost);
        energy.markDirty();
    }

    private static void renderGate(Gate gate, long now) {
        if (now % 2 != 0) return;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16 + now * 0.025;
            double width = Math.cos(angle) * 1.33;
            double height = Math.sin(angle) * 1.68;
            Vec3 point = gate.entrance.add(gate.right.scale(width)).add(0, height, 0);
            gate.source.sendParticles(gate.mastered && i % 4 == 0 ? MASTERED : PURPLE,
                    point.x, point.y, point.z, 1, 0.04, 0.04, 0.04, 0);
        }
        for (int i = 0; i < 24; i++) {
            double angle = gate.source.random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(gate.source.random.nextDouble());
            Vec3 point = gate.entrance.add(gate.right.scale(Math.cos(angle) * radius * 1.28))
                    .add(0, Math.sin(angle) * radius * 1.6, 0)
                    .add(gate.facing.scale((gate.source.random.nextDouble() - 0.5) * 0.2));
            gate.source.sendParticles(i % 4 == 0 ? ParticleTypes.SMOKE : BLACK,
                    point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void renderShroud(ServerPlayer player, boolean mastered, long now) {
        if (now % 2 != 0) return;
        double x = player.getX(), y = player.getY() + 0.9, z = player.getZ();
        player.serverLevel().sendParticles(BLACK, x, y, z, 5, 0.35, 0.8, 0.35, 0);
        player.serverLevel().sendParticles(ParticleTypes.SMOKE, x, y, z, 3, 0.3, 0.7, 0.3, 0.005);
        if (mastered) player.serverLevel().sendParticles(MASTERED, x, y, z, 2, 0.45, 0.9, 0.45, 0);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        GATES.remove(event.getEntity().getUUID());
        REDIRECT_HELD.remove(event.getEntity().getUUID());
        MENU_SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        GATES.clear();
        REDIRECT_HELD.clear();
        MENU_SESSIONS.clear();
    }
}

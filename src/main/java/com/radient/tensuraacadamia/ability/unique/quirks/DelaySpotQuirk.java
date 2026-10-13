package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DelaySpotQuirk extends Skill {

    public static final double RADIUS = 20.0D;
    public static final int GROW_TICKS = 16;
    public static final int CLOSE_TICKS = 12;
    public static final int PHASE_ACTIVE = 0;
    public static final int PHASE_CLOSING = 1;

    private static final ResourceLocation DELAY_SPOT_SLOWNESS = ResourceLocation.fromNamespaceAndPath("tracadamia", "delay_spot_slowness");
    private static final double PROJECTILE_SLOW_FACTOR = 0.03D;

    private static final Map<UUID, Set<UUID>> AFFECTED = new HashMap<>();
    private static final Map<UUID, Map<UUID, Vec3>> SLOWED_PROJECTILES = new HashMap<>();
    private static final Map<UUID, Retract> RETRACTING = new HashMap<>();

    public DelaySpotQuirk() {
        super(SkillType.UNIQUE);
    }

    public static double growth(double ticks) {
        double t = Math.min(1.0D, Math.max(0.0D, ticks / GROW_TICKS));
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    public static double shrink(double ticks) {
        double t = Math.min(1.0D, Math.max(0.0D, ticks / CLOSE_TICKS));
        return 1.0D - t * t * t;
    }

    private static void applyField(ServerLevel level, LivingEntity user, double radius) {
        MinecraftServer server = level.getServer();
        UUID userUUID = user.getUUID();

        double radiusSqr = radius * radius;
        Vec3 center = user.position().add(0, user.getBbHeight() * 0.5D, 0);
        AABB area = AABB.ofSize(center, radius * 2.0D, radius * 2.0D, radius * 2.0D);

        Set<UUID> affected = AFFECTED.computeIfAbsent(userUUID, uuid -> new HashSet<>());
        Set<UUID> inRange = new HashSet<>();

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, candidate -> candidate != user && candidate.isAlive() && !candidate.isAlliedTo(user) && candidate.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSqr)) {
            slowEntity(target);
            inRange.add(target.getUUID());
        }

        Iterator<UUID> iterator = affected.iterator();
        while (iterator.hasNext()) {
            UUID targetUUID = iterator.next();

            if (inRange.contains(targetUUID)) {
                continue;
            }

            LivingEntity target = findLiving(server, targetUUID);
            if (target != null) {
                unslowEntity(target);
            }

            iterator.remove();
        }

        affected.addAll(inRange);

        Map<UUID, Vec3> slowed = SLOWED_PROJECTILES.computeIfAbsent(userUUID, uuid -> new HashMap<>());
        Set<UUID> projectilesInRange = new HashSet<>();

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, area, candidate -> candidate.isAlive() && !isFriendly(candidate, user) && candidate.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSqr)) {
            if (slowProjectile(projectile, slowed)) {
                projectilesInRange.add(projectile.getUUID());
            }
        }

        Iterator<Map.Entry<UUID, Vec3>> projectileIterator = slowed.entrySet().iterator();
        while (projectileIterator.hasNext()) {
            Map.Entry<UUID, Vec3> entry = projectileIterator.next();

            if (projectilesInRange.contains(entry.getKey())) {
                continue;
            }

            restoreProjectile(server, entry.getKey(), entry.getValue());
            projectileIterator.remove();
        }
    }

    private static @Nullable LivingEntity findLiving(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(uuid) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static void slowEntity(LivingEntity target) {
        AttributeInstance movementSpeed = target.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movementSpeed == null) {
            return;
        }

        if (movementSpeed.getModifier(DELAY_SPOT_SLOWNESS) != null) {
            return;
        }

        movementSpeed.addTransientModifier(new AttributeModifier(DELAY_SPOT_SLOWNESS, -0.99D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void unslowEntity(LivingEntity target) {
        AttributeInstance movementSpeed = target.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movementSpeed == null) {
            return;
        }

        AttributeModifier modifier = movementSpeed.getModifier(DELAY_SPOT_SLOWNESS);

        if (modifier != null) {
            movementSpeed.removeModifier(modifier);
        }
    }

    private static void removeAllSlowness(MinecraftServer server, UUID userUUID) {
        Set<UUID> affected = AFFECTED.remove(userUUID);

        if (affected == null || affected.isEmpty()) {
            return;
        }

        for (UUID targetUUID : affected) {
            LivingEntity target = findLiving(server, targetUUID);
            if (target != null) {
                unslowEntity(target);
            }
        }
    }

    private static boolean isFriendly(Projectile projectile, LivingEntity user) {
        Entity owner = projectile.getOwner();
        return owner != null && (owner == user || owner.isAlliedTo(user));
    }

    private static boolean slowProjectile(Projectile projectile, Map<UUID, Vec3> slowed) {
        Vec3 velocity = projectile.getDeltaMovement();
        Vec3 original = slowed.get(projectile.getUUID());

        if (original == null) {
            if (velocity.lengthSqr() < 1.0E-4D) {
                return false;
            }
            original = velocity;
            slowed.put(projectile.getUUID(), original);
        } else if (velocity.lengthSqr() < 1.0E-8D) {
            slowed.remove(projectile.getUUID());
            return false;
        }

        projectile.setDeltaMovement(original.scale(PROJECTILE_SLOW_FACTOR));
        projectile.hurtMarked = true;
        return true;
    }

    private static void restoreProjectile(MinecraftServer server, UUID projectileUUID, Vec3 original) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(projectileUUID) instanceof Projectile projectile) {
                if (projectile.isAlive()) {
                    projectile.setDeltaMovement(original);
                    projectile.hurtMarked = true;
                }
                break;
            }
        }
    }

    private static void removeAllProjectileSlowness(MinecraftServer server, UUID userUUID) {
        Map<UUID, Vec3> slowed = SLOWED_PROJECTILES.remove(userUUID);

        if (slowed == null || slowed.isEmpty()) {
            return;
        }

        slowed.forEach((projectileUUID, original) -> restoreProjectile(server, projectileUUID, original));
    }

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/delayspot.png");
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.literal("Flowers bloom in your heart.");
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "delayspot.delay_spot";
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != 0) {
            return false;
        }

        if (entity.level().isClientSide()) {
            return true;
        }

        UUID userUUID = entity.getUUID();

        if (RETRACTING.remove(userUUID) != null) {
            entity.removeEffect(MHAEffects.DELAY_SPOT_FIELD);
        }

        applyField((ServerLevel) entity.level(), entity, RADIUS * growth(heldTicks));

        entity.addEffect(new MobEffectInstance(MHAEffects.DELAY_SPOT_FIELD, 4, PHASE_ACTIVE, false, false, false));

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != 0) {
            return;
        }

        if (entity.level().isClientSide()) {
            return;
        }

        RETRACTING.put(entity.getUUID(), new Retract(growth(heldTicks)));
        entity.addEffect(new MobEffectInstance(MHAEffects.DELAY_SPOT_FIELD, 4, PHASE_CLOSING, false, false, false));
    }

    private static final class Retract {
        final double fromGrowth;
        int ticks;

        Retract(double fromGrowth) {
            this.fromGrowth = fromGrowth;
        }
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID)
    public static final class Retraction {
        @SubscribeEvent
        public static void tick(ServerTickEvent.Post event) {
            if (RETRACTING.isEmpty()) {
                return;
            }

            MinecraftServer server = event.getServer();
            Iterator<Map.Entry<UUID, Retract>> iterator = RETRACTING.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Retract> entry = iterator.next();
                UUID userUUID = entry.getKey();
                Retract retract = entry.getValue();
                retract.ticks++;

                LivingEntity user = findLiving(server, userUUID);

                if (user == null || !user.isAlive() || retract.ticks >= CLOSE_TICKS) {
                    iterator.remove();
                    removeAllSlowness(server, userUUID);
                    removeAllProjectileSlowness(server, userUUID);

                    if (user != null) {
                        user.removeEffect(MHAEffects.DELAY_SPOT_FIELD);
                    }
                    continue;
                }

                double radius = RADIUS * retract.fromGrowth * shrink(retract.ticks);
                applyField((ServerLevel) user.level(), user, radius);
                user.addEffect(new MobEffectInstance(MHAEffects.DELAY_SPOT_FIELD, 4, PHASE_CLOSING, false, false, false));
            }
        }
    }
}
package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.attraction.AttractionBlackHole;
import com.radient.tensuraacadamia.ability.unique.quirks.attraction.AttractionCrash;
import com.radient.tensuraacadamia.ability.unique.quirks.attraction.AttractionOrbit;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class AttractionQuirk extends Skill {

    private static final QuirkSkillsConfig.Attraction CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Attraction;

    private static final int PULL = 0;
    private static final int OBJECT_CRASH = 1;
    private static final int DEBRIS_ORBIT = 2;
    private static final int BLACK_HOLE = 3;
    private static final int MODES = 4;
    private static final double PULL_CLOSE_ENOUGH = 1.5D;
    private static final double PULL_BREAK_RANGE = 1.5D;
    private static final double PLAYER_SPEED_SCALE = 2.16D;
    private static final DustParticleOptions PULL_DUST = new DustParticleOptions(Vec3.fromRGB24(0xB8A6FF).toVector3f(), 0.8F);

    private static final Map<UUID, LivingEntity> PULLING = new HashMap<>();
    private static final Map<UUID, LivingEntity> ATTRACTING = new HashMap<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/attraction.png");
    }

    public AttractionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case OBJECT_CRASH -> CONFIG.crashAuraCost;
            case DEBRIS_ORBIT -> CONFIG.orbitAuraCost;
            case BLACK_HOLE -> CONFIG.blackHoleAuraCost;
            default -> CONFIG.pullAuraCost;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int next = Math.floorMod(mode + (reverse ? -1 : 1), MODES);
        if (next == BLACK_HOLE && !instance.isMastered(entity)) {
            next = Math.floorMod(next + (reverse ? -1 : 1), MODES);
        }
        return next;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case PULL -> "attraction.pull";
            case OBJECT_CRASH -> "attraction.object_crash";
            case DEBRIS_ORBIT -> "attraction.debris_orbit";
            case BLACK_HOLE -> "attraction.black_hole";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == DEBRIS_ORBIT && entity.isShiftKeyDown() && AttractionOrbit.isOrbiting(entity) || super.canIgnoreCoolDown(instance, entity, mode);
    }

    @Override
    public void onProjectileHit(ManasSkillInstance instance, LivingEntity entity, EntityHitResult hitResult, Projectile projectile,
                                Changeable<ProjectileDeflection> deflection, Changeable<EntityEvents.ProjectileHitResult> result) {
        if (!instance.isToggled() || !(projectile instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident || !(entity.level() instanceof ServerLevel level) || !instance.canInteractSkill(entity)) {
            return;
        }

        ItemStack caught = arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY ? ItemStack.EMPTY : arrow.getPickupItemStackOrigin().copy();
        if (!caught.isEmpty() && entity instanceof Player player && !player.getInventory().add(caught)) {
            player.drop(caught, false);
        }

        result.set(EntityEvents.ProjectileHitResult.PASS);
        arrow.discard();
        level.sendParticles(PULL_DUST, entity.getX(), entity.getY(0.6D), entity.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F);
        if (entity.getRandom().nextInt(3) == 0) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == DEBRIS_ORBIT) {
            if (entity.isShiftKeyDown()) {
                AttractionOrbit.dismiss(level, entity);
            } else {
                AttractionOrbit.summon(level, instance, entity, mode);
            }
        } else if (mode == BLACK_HOLE) {
            if (!instance.isMastered(entity)) {
                fail(entity, "tracadamia.skill.attraction.needs_mastery");
            } else if (AttractionBlackHole.throwOrb(level, instance, entity, mode)) {
                instance.setCoolDown(CONFIG.blackHoleCooldown, mode);
                instance.addMasteryPoint(entity);
            }
        }
    }

    // Attract, Passive Toggle

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.level().isClientSide) {
            ATTRACTING.put(entity.getUUID(), entity);
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        ATTRACTING.remove(entity.getUUID());
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.level().isClientSide) {
            ATTRACTING.put(entity.getUUID(), entity);
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ATTRACTING.isEmpty()) {
            ATTRACTING.values().removeIf(AttractionQuirk::tickAttract);
        }
    }

    private static boolean tickAttract(LivingEntity entity) {
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ATTRACTION.get()).orElse(null);
        if (!entity.isAlive() || entity.isRemoved() || instance == null || !instance.isToggled() || !(entity.level() instanceof ServerLevel level)) {
            return true;
        }

        if (!instance.canInteractSkill(entity)) {
            return false;
        }

        double radius = instance.isMastered(entity) ? CONFIG.attractRadiusMastered : CONFIG.attractRadius;
        Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        AABB area = entity.getBoundingBox().inflate(radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, item -> !(item.hasPickUpDelay() && item.getOwner() == entity))) {
            attract(item, center, radius);
        }

        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
            attract(orb, center, radius);
        }

        return false;
    }

    private static void attract(Entity entity, Vec3 center, double radius) {
        Vec3 offset = center.subtract(entity.position());
        double distance = offset.length();
        if (distance > radius || distance < 0.3D) {
            return;
        }

        entity.setDeltaMovement(offset.scale(Math.min(CONFIG.attractSpeed, distance) / distance));
        entity.hasImpulse = true;
    }

    // PDrags the nearest entity in while held

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode == OBJECT_CRASH && entity.level() instanceof ServerLevel level) {
            return AttractionCrash.hold(level, instance, entity, heldTicks, mode);
        }
        if (mode != PULL || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks == 0) {
            LivingEntity target = findNearest(level, entity);
            if (target == null) {
                fail(entity, "tracadamia.skill.attraction.nothing");
                return false;
            }

            if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
                return false;
            }

            PULLING.put(entity.getUUID(), target);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
        } else if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return false;
        }

        LivingEntity target = PULLING.get(entity.getUUID());
        double range = CONFIG.pullRange * PULL_BREAK_RANGE;
        if (target == null || !target.isAlive() || target.level() != entity.level() || target.distanceToSqr(entity) > range * range) {
            return false;
        }

        pull(level, instance, entity, target);
        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        return true;
    }

    private static @Nullable LivingEntity findNearest(ServerLevel level, LivingEntity entity) {
        return level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(CONFIG.pullRange),
                        target -> target != entity && target.isAlive() && !target.isSpectator() && HitRules.canHit(entity, target) && target.distanceTo(entity) <= CONFIG.pullRange)
                .stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(entity)))
                .orElse(null);
    }

    private static void pull(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {
        Vec3 offset = owner.position().subtract(target.position());
        double distance = offset.length();
        if (distance < PULL_CLOSE_ENOUGH) {
            return;
        }

        double strength = instance.isMastered(owner) ? CONFIG.pullStrengthMastered : CONFIG.pullStrength;
        double pull = Math.min(distance - PULL_CLOSE_ENOUGH + 0.1D, strength - getTopSpeed(target) * CONFIG.pullResistance);
        if (pull <= 0.0D) {
            if (target.tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.5D), target.getZ(), 3, 0.2D, 0.3D, 0.2D, 0.01D);
            }

            return;
        }

        Vec3 motion = offset.scale(pull / distance);
        Changeable<Vec3> moved = Changeable.of(target.getDeltaMovement().scale(0.4D).add(motion.scale(0.6D)));
        if (target.getType().is(TensuraEntityTags.NO_FORCED_MOVE) || TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, owner, instance, moved).isFalse()) {
            return;
        }

        target.setDeltaMovement(moved.get());
        target.hurtMarked = true;
        target.resetFallDistance();

        if (target.tickCount % 2 == 0) {
            Vec3 from = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 to = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D);
            for (int i = 1; i < 6; i++) {
                Vec3 point = from.lerp(to, i / 6.0D + owner.getRandom().nextDouble() * 0.1D);
                level.sendParticles(PULL_DUST, point.x, point.y, point.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
    }

    private static double getTopSpeed(LivingEntity target) {
        double speed = target.getAttributeValue(Attributes.MOVEMENT_SPEED) * (target instanceof Player ? PLAYER_SPEED_SCALE : 1.0D);
        return target.isSprinting() ? speed * 1.3D : speed;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == PULL && PULLING.remove(entity.getUUID()) != null) {
            instance.setCoolDown(CONFIG.pullCooldown, PULL);
        }
        if (mode == OBJECT_CRASH && entity.level() instanceof ServerLevel level && AttractionCrash.isCrashing(entity)) {
            AttractionCrash.release(level, entity, true);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        PULLING.remove(entity.getUUID());
        ATTRACTING.remove(entity.getUUID());
        AttractionCrash.forget(entity);
        AttractionOrbit.forget(entity);
        AttractionBlackHole.forget(entity);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PULLING.clear();
        ATTRACTING.clear();
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

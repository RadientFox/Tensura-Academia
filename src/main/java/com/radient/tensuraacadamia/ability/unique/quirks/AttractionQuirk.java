package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
    private static final double PULL_CLOSE_ENOUGH = 1.5D;
    private static final double PULL_BREAK_RANGE = 1.5D;
    private static final double PLAYER_SPEED_SCALE = 2.16D;
    private static final DustParticleOptions PULL_DUST = new DustParticleOptions(Vec3.fromRGB24(0xB8A6FF).toVector3f(), 0.8F);

    private static final Map<UUID, LivingEntity> PULLING = new HashMap<>();
    private static final Map<UUID, LivingEntity> ATTRACTING = new HashMap<>();

    public AttractionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.pullAuraCost;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == PULL ? "attraction.pull" : super.getModeId(instance, mode);
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
                        target -> target != entity && target.isAlive() && !target.isSpectator() && target.distanceTo(entity) <= CONFIG.pullRange)
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
        target.setDeltaMovement(target.getDeltaMovement().scale(0.4D).add(motion.scale(0.6D)));
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
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        PULLING.remove(entity.getUUID());
        ATTRACTING.remove(entity.getUUID());
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

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ExtraArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.ExtraArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ExtraArms;

    private static final int WALL_CLIMB = 0;

    public static final int ARMS = 2;
    private static final double WALL_REACH = 0.06D;

    private static final String WALL_CLIMB_TAG = "wallClimb";
    private static final String COUNTER_TAG = "counter";

    private static boolean countering = false;

    public ExtraArmsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == WALL_CLIMB ? CONFIG.wallClimbAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == WALL_CLIMB ? "extra_arms.wall_climb" : super.getModeId(instance, mode);
    }

    private static Optional<ManasSkillInstance> getExtraArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.EXTRA_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static long getCounterTime(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, COUNTER_TAG);
    }

    // Arms in the back of the head?

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || isClimbing(instance);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    // Small hits from behind
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (countering || entity.level().isClientSide || !(source.getEntity() instanceof LivingEntity attacker) || attacker == entity || !attacker.isAlive()) {
            return;
        }

        Optional<ManasSkillInstance> extraArms = getExtraArms(entity).filter(ManasSkillInstance::isToggled);
        if (extraArms.isEmpty() || event.getAmount() > entity.getMaxHealth() * CONFIG.counterThreshold) {
            return;
        }

        ManasSkillInstance instance = extraArms.get();
        long time = entity.level().getGameTime();
        long last = getCounterTime(instance);
        if (MultiArms.isInFront(entity, attacker.position()) || attacker.distanceTo(entity) > CONFIG.counterRange + attacker.getBbWidth() * 0.5D
                || (last != Long.MIN_VALUE && time - last < CONFIG.counterCooldown)) {
            return;
        }

        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        float damage = (float) ((attack == null ? 1.0D : attack.getValue()) * CONFIG.counterDamage);
        DamageSource punch = entity instanceof Player player ? entity.damageSources().playerAttack(player) : entity.damageSources().mobAttack(entity);

        countering = true;
        try {
            attacker.invulnerableTime = 0;
            attacker.hurt(punch, damage);
            attacker.knockback(0.4D, entity.getX() - attacker.getX(), entity.getZ() - attacker.getZ());
        } finally {
            countering = false;
        }

        instance.getOrCreateTag().putLong(COUNTER_TAG, time);
        instance.markDirty();
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.6D), attacker.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.2D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.2F);
        }

        if (entity.getRandom().nextBoolean()) {
            instance.addMasteryPoint(entity);
        }
    }

    // Wall Climb

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != WALL_CLIMB || entity.level().isClientSide) {
            return;
        }

        boolean climbing = !isClimbing(instance);
        if (climbing && EnergyHelper.isOutOfEnergy(entity, instance, WALL_CLIMB)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (climbing) {
            tag.putBoolean(WALL_CLIMB_TAG, true);
            instance.addMasteryPoint(entity);
        } else {
            tag.remove(WALL_CLIMB_TAG);
        }

        instance.markDirty();
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, climbing ? 1.2F : 0.8F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(climbing ? "tracadamia.skill.extra_arms.wall_climb_on" : "tracadamia.skill.extra_arms.wall_climb_off"), true);
        }
    }

    public static boolean isClimbing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(WALL_CLIMB_TAG);
    }

    // Walls work like ladders while climbing is on
    // used by LivingEntityClimbMixin
    public static boolean canWallClimb(LivingEntity entity) {
        return entity instanceof Player && !entity.isSpectator() && getExtraArms(entity).filter(ExtraArmsQuirk::isClimbing).isPresent() && isTouchingWall(entity);
    }

    public static boolean isTouchingWall(LivingEntity entity) {
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, entity.getBoundingBox().inflate(WALL_REACH, 0.0D, WALL_REACH).deflate(0.0D, 0.01D, 0.0D))) {
            if (!shape.isEmpty()) {
                return true;
            }
        }

        return false;
    }

}

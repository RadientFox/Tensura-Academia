package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.DamageReduction;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class SpringlikeLimbsQuirk extends Skill {

    private static final QuirkSkillsConfig.SpringlikeLimbs CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).SpringlikeLimbs;

    private static final int SPRING_PUNCH = 0;
    private static final int SUPER_JUMP = 1;
    private static final int ENERGY = 2;

    private static final String ARM_ENERGY_TAG = "armEnergy";
    private static final String LEG_ENERGY_TAG = "legEnergy";
    private static final String SPRING_PUNCH_TAG = "springPunch";
    private static final String SUPER_JUMP_TAG = "superJump";
    private static final String SUPER_JUMP_USED_TAG = "superJumpUsed";

    private static final ResourceLocation SUPER_JUMP_ID = ResourceLocation.fromNamespaceAndPath("tracadamia", "springlike_limbs_super_jump");
    private static final ResourceLocation SPRING_PUNCH_ID = ResourceLocation.fromNamespaceAndPath("tracadamia", "springlike_limbs_spring_punch");

    private static final Set<UUID> CROUCHING = new HashSet<>();

    public SpringlikeLimbsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == ENERGY ? 0.0D : CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == SPRING_PUNCH ? ENERGY : mode - 1;
        }

        return mode == ENERGY ? SPRING_PUNCH : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SPRING_PUNCH -> "springlike_limbs.spring_punch";
            case SUPER_JUMP -> "springlike_limbs.super_jump";
            case ENERGY -> "springlike_limbs.energy";
            default -> super.getModeId(instance, mode);
        };
    }

    private static Optional<ManasSkillInstance> getSpringlikeLimbs(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.SPRINGLIKE_LIMBS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Store Arms and Store Legs, Passive

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        if (isArmed(instance, SUPER_JUMP_TAG)) {
            applySuperJump(entity);
        }

        updateSpringPunch(instance, entity);

        if (!instance.isToggled()) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        if (isArmed(instance, SUPER_JUMP_TAG)) {
            applySuperJump(owner);
        }

        updateSpringPunch(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        removeSuperJump(entity);

        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(SPRING_PUNCH_ID);
        }
    }

    private static double getEnergy(ManasSkillInstance instance, String key) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(key);
    }

    private static void setEnergy(ManasSkillInstance instance, String key, double energy) {
        instance.getOrCreateTag().putDouble(key, energy);
        instance.markDirty();
    }

    private static double getMaxEnergy(ManasSkillInstance instance, LivingEntity entity, String key) {
        boolean mastered = instance.isMastered(entity);
        if (key.equals(ARM_ENERGY_TAG)) {
            return mastered ? CONFIG.maxArmEnergyMastered : CONFIG.maxArmEnergy;
        }

        return mastered ? CONFIG.maxLegEnergyMastered : CONFIG.maxLegEnergy;
    }

    private static void addEnergy(LivingEntity entity, ManasSkillInstance instance, String key, double amount) {
        double max = getMaxEnergy(instance, entity, key);
        double energy = getEnergy(instance, key);
        if (energy >= max) {
            return;
        }

        setEnergy(instance, key, Math.min(max, energy + amount));
    }

    // Energy
    private static void showEnergy(LivingEntity entity, ManasSkillInstance instance) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.springlike_limbs.energy",
                    Mth.floor(getEnergy(instance, ARM_ENERGY_TAG)), Mth.floor(getMaxEnergy(instance, entity, ARM_ENERGY_TAG)),
                    Mth.floor(getEnergy(instance, LEG_ENERGY_TAG)), Mth.floor(getMaxEnergy(instance, entity, LEG_ENERGY_TAG))).withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (instance.getMastery() < 0.0D || owner.level().isClientSide || source.getDirectEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        // Spring Punch
        if (isArmed(instance, SPRING_PUNCH_TAG)) {
            setEnergy(instance, ARM_ENERGY_TAG, 0.0D);
            setArmed(instance, SPRING_PUNCH_TAG, false);
            updateSpringPunch(instance, owner);
            playSpring(owner, SoundEvents.PISTON_EXTEND);
            instance.addMasteryPoint(owner);
            instance.setCoolDown(CONFIG.springPunchCooldown, SPRING_PUNCH);
            return true;
        }

        // Store Arms, Passive
        if (instance.isToggled()) {
            addEnergy(owner, instance, ARM_ENERGY_TAG, CONFIG.armEnergyPerAttack);
        }

        return true;
    }

    // Store Legs fall damage
    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || owner.level().isClientSide || !source.is(DamageTypeTags.IS_FALL)) {
            return true;
        }

        float damage = amount.get();
        amount.set(DamageReduction.reduce(owner, source, damage, CONFIG.fallReduction));
        addEnergy(owner, instance, LEG_ENERGY_TAG, damage - amount.get());
        return true;
    }

    // Store Legs crouching
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        if (!player.isShiftKeyDown()) {
            CROUCHING.remove(player.getUUID());
            return;
        }

        if (CROUCHING.add(player.getUUID())) {
            getSpringlikeLimbs(player).filter(ManasSkillInstance::isToggled).ifPresent(instance -> addEnergy(player, instance, LEG_ENERGY_TAG, CONFIG.legEnergyPerMove));
        }
    }

    // Store Legs jumping and Super Jump energy
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        getSpringlikeLimbs(entity).ifPresent(instance -> {
            if (!isArmed(instance, SUPER_JUMP_TAG)) {
                if (instance.isToggled()) {
                    addEnergy(entity, instance, LEG_ENERGY_TAG, CONFIG.legEnergyPerMove);
                }
                return;
            }

            double energy = Math.max(0.0D, getEnergy(instance, LEG_ENERGY_TAG) - CONFIG.superJumpCost);
            setEnergy(instance, LEG_ENERGY_TAG, energy);
            setArmed(instance, SUPER_JUMP_USED_TAG, true);
            playSpring(entity, SoundEvents.PISTON_EXTEND);
            if (entity.getRandom().nextBoolean()) {
                instance.addMasteryPoint(entity);
            }

            if (energy < CONFIG.superJumpCost) {
                disarmSuperJump(instance, entity);
            }
        });
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide) {
            return;
        }

        switch (mode) {
            case SPRING_PUNCH -> toggleSpringPunch(instance, entity);
            case SUPER_JUMP -> toggleSuperJump(instance, entity);
            case ENERGY -> {
                showEnergy(entity, instance);
                instance.setCoolDown(CONFIG.energyCooldown, mode);
            }
        }
    }

    private static boolean isArmed(ManasSkillInstance instance, String key) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(key);
    }

    private static void setArmed(ManasSkillInstance instance, String key, boolean armed) {
        instance.getOrCreateTag().putBoolean(key, armed);
        instance.markDirty();
    }

    // Spring Punch
    private static void updateSpringPunch(ManasSkillInstance instance, LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null || entity.level().isClientSide) {
            return;
        }

        double perEnergy = instance.isMastered(entity) ? CONFIG.springPunchDamageMastered : CONFIG.springPunchDamage;
        double bonus = isArmed(instance, SPRING_PUNCH_TAG) ? getEnergy(instance, ARM_ENERGY_TAG) * perEnergy : 0.0D;
        if (bonus <= 0.0D) {
            attack.removeModifier(SPRING_PUNCH_ID);
            return;
        }

        AttributeModifier current = attack.getModifier(SPRING_PUNCH_ID);
        if (current != null && current.amount() == bonus) {
            return;
        }

        attack.addOrReplacePermanentModifier(new AttributeModifier(SPRING_PUNCH_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void toggleSpringPunch(ManasSkillInstance instance, LivingEntity entity) {
        if (isArmed(instance, SPRING_PUNCH_TAG)) {
            setArmed(instance, SPRING_PUNCH_TAG, false);
            updateSpringPunch(instance, entity);
            message(entity, "tracadamia.skill.springlike_limbs.spring_punch_disarmed", ChatFormatting.YELLOW);
            return;
        }

        if (getEnergy(instance, ARM_ENERGY_TAG) <= 0.0D) {
            fail(entity);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SPRING_PUNCH)) {
            return;
        }

        setArmed(instance, SPRING_PUNCH_TAG, true);
        updateSpringPunch(instance, entity);
        playSpring(entity, SoundEvents.PISTON_CONTRACT);
        message(entity, "tracadamia.skill.springlike_limbs.spring_punch_armed", ChatFormatting.GREEN);
    }

    // Super Jump
    private static void toggleSuperJump(ManasSkillInstance instance, LivingEntity entity) {
        if (isArmed(instance, SUPER_JUMP_TAG)) {
            disarmSuperJump(instance, entity);
            return;
        }

        if (getEnergy(instance, LEG_ENERGY_TAG) < CONFIG.superJumpCost) {
            fail(entity);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SUPER_JUMP)) {
            return;
        }

        setArmed(instance, SUPER_JUMP_TAG, true);
        applySuperJump(entity);
        playSpring(entity, SoundEvents.PISTON_CONTRACT);
        message(entity, "tracadamia.skill.springlike_limbs.super_jump_armed", ChatFormatting.GREEN);
    }

    private static void disarmSuperJump(ManasSkillInstance instance, LivingEntity entity) {
        if (isArmed(instance, SUPER_JUMP_USED_TAG)) {
            setArmed(instance, SUPER_JUMP_USED_TAG, false);
            instance.setCoolDown(CONFIG.superJumpCooldown, SUPER_JUMP);
        }

        setArmed(instance, SUPER_JUMP_TAG, false);
        removeSuperJump(entity);
        message(entity, "tracadamia.skill.springlike_limbs.super_jump_disarmed", ChatFormatting.YELLOW);
    }

    private static void applySuperJump(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(SUPER_JUMP_ID)) {
            double gravity = entity.getAttributeValue(Attributes.GRAVITY);
            double strength = jump.getValue();
            double bonus = getJumpVelocity(getJumpHeight(strength, gravity) + CONFIG.superJumpHeight, gravity) - strength;
            jump.addOrReplacePermanentModifier(new AttributeModifier(SUPER_JUMP_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }

        AttributeInstance safeFall = entity.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null && !safeFall.hasModifier(SUPER_JUMP_ID)) {
            safeFall.addOrReplacePermanentModifier(new AttributeModifier(SUPER_JUMP_ID, CONFIG.superJumpHeight, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeSuperJump(LivingEntity entity) {
        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(SUPER_JUMP_ID);
        }

        AttributeInstance safeFall = entity.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null) {
            safeFall.removeModifier(SUPER_JUMP_ID);
        }
    }

    // Jump height from jump strength
    private static double getJumpHeight(double velocity, double gravity) {
        double height = 0.0D;
        for (int tick = 0; tick < 200 && velocity > 0.0D; tick++) {
            height += velocity;
            velocity = (velocity - gravity) * 0.98D;
        }

        return height;
    }

    // Jump strength for a jump height
    private static double getJumpVelocity(double height, double gravity) {
        double low = 0.0D;
        double high = 10.0D;
        for (int step = 0; step < 32; step++) {
            double middle = (low + high) * 0.5D;
            if (getJumpHeight(middle, gravity) < height) {
                low = middle;
            } else {
                high = middle;
            }
        }

        return high;
    }

    private static void playSpring(LivingEntity entity, SoundEvent sound) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    private static void message(LivingEntity entity, String key, ChatFormatting color) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(color), true);
        }
    }

    private static void fail(LivingEntity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        message(entity, "tracadamia.skill.springlike_limbs.not_enough_energy", ChatFormatting.RED);
    }

}

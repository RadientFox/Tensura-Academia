package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.List;

public class MuscleAugmentationQuirk extends Skill {

    private static final QuirkSkillsConfig.MuscleAugmentation CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).MuscleAugmentation;

    private static final int ENHANCEMENT = 0;
    private static final int STRIKE = 1;
    private static final int SHIELD = 2;

    private static final double STRIKE_OUTPUT_STEP = 0.5D;
    private static final DecimalFormat OUTPUT_FORMAT = new DecimalFormat("#.#");

    private static final String SIZE_TAG = "size";
    private static final String STRIKE_READY_TAG = "strikeReady";
    private static final String STRIKE_OUTPUT_TAG = "strikeOutput";

    private static final ResourceLocation MUSCLE = ResourceLocation.fromNamespaceAndPath("tracadamia", "muscle_augmentation");
    private static final ResourceLocation STRIKE_SLOW = ResourceLocation.fromNamespaceAndPath("tracadamia", "muscle_overload_strike");
    private static final ResourceLocation STRIKE_POWER = ResourceLocation.fromNamespaceAndPath("tracadamia", "muscle_overload_strike_power");

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/muscle_augmentation.png");
    }

    public MuscleAugmentationQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public List<Integer> getModeLearningList(ManasSkillInstance instance) {
        return List.of(STRIKE, SHIELD);
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (!isOverloadUnlocked(instance)) {
            return ENHANCEMENT;
        }

        if (reverse) {
            return mode == ENHANCEMENT ? SHIELD : mode - 1;
        }

        return mode == SHIELD ? ENHANCEMENT : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ENHANCEMENT -> "muscle_augmentation.enhancement";
            case STRIKE -> "muscle_augmentation.strike";
            case SHIELD -> "muscle_augmentation.shield";
            default -> super.getModeId(instance, mode);
        };
    }

    // muscle mass
    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateMuscles(instance, entity);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateMuscles(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);


        setModifier(entity, Attributes.SCALE, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.ATTACK_DAMAGE, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.ARMOR, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.KNOCKBACK_RESISTANCE, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.MAX_HEALTH, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(entity, Attributes.MOVEMENT_SPEED, STRIKE_SLOW, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.BLOCK_INTERACTION_RANGE, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.ENTITY_INTERACTION_RANGE, MUSCLE, 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void updateMuscles(ManasSkillInstance instance, LivingEntity entity) {
        double sizeGained = getSize(instance) - 1.0D;
        setModifier(entity, Attributes.SCALE, MUSCLE, sizeGained, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.ATTACK_DAMAGE, MUSCLE, sizeGained * CONFIG.damagePerSize, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        setModifier(entity, Attributes.BLOCK_INTERACTION_RANGE, MUSCLE, sizeGained * 2.0, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.ENTITY_INTERACTION_RANGE, MUSCLE, sizeGained * 2.0, AttributeModifier.Operation.ADD_VALUE);

        double mass = Math.max(0.0D, entity.getAttributeValue(Attributes.SCALE) - 1.0D);
        setModifier(entity, Attributes.ARMOR, MUSCLE, mass * CONFIG.armorPerSize, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.KNOCKBACK_RESISTANCE, MUSCLE, mass * CONFIG.knockbackResistancePerSize, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.MAX_HEALTH, MUSCLE, mass * CONFIG.healthPerSize, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        setModifier(entity, Attributes.MOVEMENT_SPEED, STRIKE_SLOW, isStrikeReady(instance) ? -CONFIG.strikeSpeedPenalty : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        if (amount == 0.0D) {
            attributeInstance.removeModifier(id);
            return;
        }

        AttributeModifier current = attributeInstance.getModifier(id);
        if (current != null && current.amount() == amount) {
            return;
        }

        attributeInstance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        switch (mode) {
            case ENHANCEMENT -> {
                if (entity.isShiftKeyDown()) {
                    setSize(instance, 1.0D);
                    updateMuscles(instance, entity);
                }
            }

            case STRIKE -> {
                if (!isOverloadUnlocked(instance) || learnMode(instance, entity, mode)) {
                    return;
                }

                if (isStrikeReady(instance)) {
                    setStrikeReady(instance, entity, false);
                    sendMessage(entity, Component.translatable("tracadamia.skill.muscle_augmentation.strike_cancel").withStyle(ChatFormatting.GRAY));
                    return;
                }

                if (isShielding(instance, entity)) {
                    sendMessage(entity, Component.translatable("tracadamia.skill.muscle_augmentation.shield_active").withStyle(ChatFormatting.RED));
                    return;
                }

                setStrikeReady(instance, entity, true);
                instance.addMasteryPoint(entity);
                sendMessage(entity, Component.translatable("tracadamia.skill.muscle_augmentation.strike_ready").withStyle(ChatFormatting.GOLD));
            }

            case SHIELD -> {
                if (isOverloadUnlocked(instance)) {
                    learnMode(instance, entity, mode);
                }
            }
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        return switch (mode) {
            case ENHANCEMENT -> enhance(instance, entity, heldTicks);
            case SHIELD -> {
                if (!canShield(instance, entity)) {
                    yield false;
                }

                addHeldMastery(instance, entity, heldTicks);
                yield true;
            }
            default -> false;
        };
    }

    // muscle enhancement
    private boolean enhance(ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        if (entity.isShiftKeyDown()) {
            return false;
        }

        double maxSize = instance.isMastered(entity) ? CONFIG.maxSizeMastered : CONFIG.maxSize;
        double size = getSize(instance);
        if (size >= maxSize) {
            return false;
        }

        double newSize = Math.min(size + CONFIG.growthPerTick, maxSize);
        setSize(instance, newSize);
        updateMuscles(instance, entity);
        addHeldMastery(instance, entity, heldTicks);

        playGrowEffects(entity, newSize, heldTicks);
        return true;
    }

    private static void playGrowEffects(LivingEntity entity, double size, int heldTicks) {
        if (heldTicks % 2 == 0) {
            float auraSize = (float) (entity.getAttributeValue(Attributes.SCALE) * 4.0D);
            TensuraParticleHelper.addServerAuraParticles(entity, TensuraParticleUtils.getRedAura(1.0F, auraSize, -0.3F), 1, 0.03D);
        }

        if (heldTicks % 30 == 0) {
            float pitch = (float) Math.max(0.6D, 1.1D - (size - 1.0D) * 0.1D);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.TRANSFORM_OGRE.get(), SoundSource.PLAYERS, 1.0F, pitch);
        }
    }

    private static double getSize(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null || !tag.contains(SIZE_TAG) ? 1.0D : Math.max(1.0D, tag.getDouble(SIZE_TAG));
    }

    private static void setSize(ManasSkillInstance instance, double size) {
        instance.getOrCreateTag().putDouble(SIZE_TAG, size);
        instance.markDirty();
    }

    // muscle overload strike
    @Override
    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == STRIKE && isOverloadUnlocked(instance);
    }

    @Override
    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        if (mode != STRIKE || delta == 0.0D) {
            return;
        }

        double output = Mth.clamp(getStrikeOutput(instance) + Math.signum(delta) * STRIKE_OUTPUT_STEP, 1.0D, CONFIG.strikeMaxOutput);
        instance.getOrCreateTag().putDouble(STRIKE_OUTPUT_TAG, output);
        instance.markDirty();
        setModifier(entity, Attributes.ATTACK_DAMAGE, STRIKE_POWER, isStrikeReady(instance) ? output - 1.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        sendMessage(entity, Component.translatable("tracadamia.skill.muscle_augmentation.strike_output", OUTPUT_FORMAT.format(output)).withStyle(ChatFormatting.AQUA));
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (!isStrikeReady(instance) || isShielding(instance, owner)) {
            return true;
        }

        // melee only
        if (source.getDirectEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        setStrikeReady(instance, owner, false);
        return true;
    }

    private static double getStrikeOutput(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(STRIKE_OUTPUT_TAG)) {
            return CONFIG.strikeMaxOutput;
        }

        return Mth.clamp(tag.getDouble(STRIKE_OUTPUT_TAG), 1.0D, CONFIG.strikeMaxOutput);
    }

    private static boolean isStrikeReady(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(STRIKE_READY_TAG);
    }

    private static void setStrikeReady(ManasSkillInstance instance, LivingEntity entity, boolean ready) {
        instance.getOrCreateTag().putBoolean(STRIKE_READY_TAG, ready);
        instance.markDirty();
        setModifier(entity, Attributes.MOVEMENT_SPEED, STRIKE_SLOW, ready ? -CONFIG.strikeSpeedPenalty : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, Attributes.ATTACK_DAMAGE, STRIKE_POWER, ready ? getStrikeOutput(instance) - 1.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    // muscle overload shield
    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!isShielding(instance, owner)) {
            return true;
        }

        // melee only
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker != source.getEntity() || attacker == owner) {
            return true;
        }

        amount.set(amount.get() * (float) (1.0D - CONFIG.shieldReduction));
        instance.setCoolDown(CONFIG.shieldCooldown, SHIELD);
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private boolean canShield(ManasSkillInstance instance, LivingEntity entity) {
        return isOverloadUnlocked(instance) && isModeLearned(instance, entity, SHIELD) && !instance.onCoolDown(SHIELD);
    }

    private boolean isShielding(ManasSkillInstance instance, LivingEntity entity) {
        return canShield(instance, entity) && TickingSkill.isTickingSkill(entity, this, SHIELD);
    }

    private boolean isOverloadUnlocked(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() * CONFIG.overloadUnlockMastery;
    }

    private boolean isModeLearned(ManasSkillInstance instance, LivingEntity entity, int mode) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getDouble(getModeId(instance, mode)) >= getLearningPointRequirement(instance, entity, mode);
    }

    private static void addHeldMastery(ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
    }

    private static void sendMessage(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

}

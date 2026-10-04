package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import com.radient.tensuraacadamia.util.SizeReach;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

public class EnduranceQuirk extends Skill {

    private static final QuirkSkillsConfig.Endurance CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Endurance;

    private static final int ENHANCE = 0;
    private static final String GAINED_TAG = "gained";
    private static final double SAME = 1.0E-6D;
    private static final int GROW_EFFECT_TICKS = 5;
    private static final int GROW_SOUND_TICKS = 10;
    private static final ResourceLocation ENDURANCE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "endurance");

    public EnduranceQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == ENHANCE ? "endurance.enhance" : super.getModeId(instance, mode);
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getEndurance(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ENDURANCE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double getGained(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : Math.max(0.0D, tag.getDouble(GAINED_TAG));
    }

    private static void setGained(ManasSkillInstance instance, double gained) {
        instance.getOrCreateTag().putDouble(GAINED_TAG, gained);
        instance.markDirty();
    }

    private static double getMaxGained(ManasSkillInstance instance, LivingEntity entity) {
        return Math.max(0.0D, (instance.isMastered(entity) ? CONFIG.maxSizeMastered : CONFIG.maxSize) - 1.0D);
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        double gained = getGained(instance);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, ENDURANCE, -CONFIG.speedLostPerSize * gained, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.ARMOR, ENDURANCE, CONFIG.armorPerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, ENDURANCE, CONFIG.damagePerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.SCALE, ENDURANCE, gained, AttributeModifier.Operation.ADD_VALUE);
        SizeReach.update(entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != ENHANCE || !(entity.level() instanceof ServerLevel level) || !entity.isShiftKeyDown() || getGained(instance) <= 0.0D) {
            return;
        }

        setGained(instance, 0.0D);
        update(instance, entity);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.5D), entity.getZ(), 16, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.02D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != ENHANCE || entity.isShiftKeyDown() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        double gained = getGained(instance);
        double max = getMaxGained(instance, entity);
        if (gained >= max - SAME) {
            if (heldTicks == 0) {
                fail(entity, Component.translatable("tracadamia.skill.endurance.max"));
            }
            return false;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ENHANCE)) {
            return false;
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        gained = Math.min(max, gained + Math.max(SAME, CONFIG.growthPerTick));
        setGained(instance, gained);
        update(instance, entity);
        if (heldTicks % GROW_EFFECT_TICKS == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), entity.getX(), entity.getY(0.5D), entity.getZ(), 4,
                    entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.05D);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.endurance.speed_taken", Math.round(gained * CONFIG.speedLostPerSize * 100.0D)).withStyle(ChatFormatting.GRAY), true);
            }
        }

        if (heldTicks % GROW_SOUND_TICKS == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, (float) Math.max(0.5D, 1.2D - gained * 0.16D));
        }

        if (gained >= max - SAME) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
            instance.setCoolDown(CONFIG.cooldown, ENHANCE);
            return false;
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == ENHANCE && heldTicks > 0) {
            instance.setCoolDown(CONFIG.cooldown, ENHANCE);
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (getGained(instance) > 0.0D) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        setGained(instance, 0.0D);
        update(instance, entity);
        SizeReach.update(entity, this);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

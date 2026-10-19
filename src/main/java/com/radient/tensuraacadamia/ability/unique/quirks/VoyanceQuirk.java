package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class VoyanceQuirk extends Skill {

    private static final QuirkSkillsConfig.Voyance CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Voyance;

    private static final int VOYANCE = 0;

    public static final String ACTIVE_TAG = "voyance";
    public static final String RANGE_TAG = "sightRange";

    private static final ResourceLocation DODGE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "voyance_dodge");

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/voyance.png");
    }

    public VoyanceQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return isActive(instance) ? 0.0D : CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == VOYANCE ? "voyance.voyance" : super.getModeId(instance, mode);
    }

    public static Optional<ManasSkillInstance> getVoyance(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.VOYANCE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static boolean isActive(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(ACTIVE_TAG);
    }

    public static boolean isActive(LivingEntity entity) {
        return getVoyance(entity).filter(VoyanceQuirk::isActive).isPresent();
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != VOYANCE || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        boolean active = !isActive(instance);
        if (active && EnergyHelper.isOutOfEnergy(entity, instance, VOYANCE)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        tag.putBoolean(ACTIVE_TAG, active);
        tag.putInt(RANGE_TAG, CONFIG.sightRange);
        instance.markDirty();
        updateDodge(instance, entity);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), active ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, active ? 0.6F : 1.2F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(active ? "tracadamia.skill.voyance.on" : "tracadamia.skill.voyance.off").withStyle(ChatFormatting.GRAY), true);
        }

        if (active) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateDodge(instance, entity);
        if (!isActive(instance)) {
            return;
        }

        instance.addMasteryPoint(entity);
        if (instance.getOrCreateTag().getInt(RANGE_TAG) != CONFIG.sightRange) {
            instance.getOrCreateTag().putInt(RANGE_TAG, CONFIG.sightRange);
            instance.markDirty();
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateDodge(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        Modifiers.set(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE, DODGE, 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    // Projectile dodge
    // quirk is lowkey shit
    private static void updateDodge(ManasSkillInstance instance, LivingEntity entity) {
        Modifiers.set(entity, TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE, DODGE, isActive(instance) ? CONFIG.dodgeChance : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

}

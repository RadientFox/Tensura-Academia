package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class KineticBoosterQuirk extends Skill {

    private static final QuirkSkillsConfig.KineticBooster CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).KineticBooster;

    private static final int INCREASE = 0;

    private static final int HOLD_INTERVAL = 10;

    private static final String LEVEL_TAG = "outputLevel";

    private static final ResourceLocation OUTPUT = ResourceLocation.fromNamespaceAndPath("tracadamia", "kinetic_booster_output");

    public KineticBoosterQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == INCREASE ? "kinetic_booster.increase" : super.getModeId(instance, mode);
    }

    // Increase
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != INCREASE || entity.level().isClientSide) {
            return;
        }

        changeLevel(instance, entity);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != INCREASE || entity.level().isClientSide) {
            return false;
        }

        if (heldTicks > 0 && heldTicks % HOLD_INTERVAL == 0) {
            changeLevel(instance, entity);
        }

        return true;
    }

    // Crouch to decrease
    private static void changeLevel(ManasSkillInstance instance, LivingEntity entity) {
        int outputLevel = Mth.clamp(getLevel(instance) + (entity.isShiftKeyDown() ? -1 : 1), 0, CONFIG.maxLevel);
        instance.getOrCreateTag().putInt(LEVEL_TAG, outputLevel);
        instance.markDirty();
        updateOutput(instance, entity);

        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.kinetic_booster.output_level", outputLevel, CONFIG.maxLevel).withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static int getLevel(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(LEVEL_TAG), 0, CONFIG.maxLevel);
    }

    private static void updateOutput(ManasSkillInstance instance, LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return;
        }

        double perLevel = instance.isMastered(entity) ? CONFIG.damagePerLevelMastered : CONFIG.damagePerLevel;
        double bonus = getLevel(instance) * perLevel;
        if (bonus <= 0.0D) {
            attack.removeModifier(OUTPUT);
            return;
        }

        AttributeModifier current = attack.getModifier(OUTPUT);
        if (current != null && current.amount() == bonus) {
            return;
        }

        attack.addOrReplacePermanentModifier(new AttributeModifier(OUTPUT, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // Output level mastery
    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateOutput(instance, entity);
        if (getLevel(instance) <= 0) {
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
        updateOutput(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);

        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(OUTPUT);
        }
    }

}

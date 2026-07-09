package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class QuirkBestowal extends Skill {
        private static final QuirkSkillsConfig.QuirkBestowal CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).QuirkBestowal;
        public static final ResourceLocation QUIRK_BESTOWAL = ResourceLocation.fromNamespaceAndPath("tensuraacadamia", "quirk_bestowal");

    public QuirkBestowal() {
            super(SkillType.UNIQUE);
        }

    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    public double getDefaultAcquiringMagiculeCost() {
        return CONFIG.mpAcquirement;
    }

    public int getModes(ManasSkillInstance instance) {
            return 1;
        }


        public @Nullable ResourceLocation getSkillIcon() {
            return ResourceLocation.fromNamespaceAndPath("tensuraacadamia", "textures/skill/unique/quirk_bestowal.png");
        }

        public String getModeId(ManasSkillInstance instance, int mode) {
            String var10000;
            switch (mode) {
                case 0 -> var10000 = "quirk_bestowal.bestow";
                default -> var10000 = super.getModeId(instance, mode);
            }

            return var10000;
        }



    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0;
    }
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        onToggle(instance, entity, QUIRK_BESTOWAL, true);
        AttributeInstance chantSpeed = entity.getAttribute(TensuraAttributes.CHANT_SPEED);
        if (chantSpeed != null && !chantSpeed.hasModifier(QUIRK_BESTOWAL)) {
            chantSpeed.addOrReplacePermanentModifier(new AttributeModifier(QUIRK_BESTOWAL, CONFIG.chantSpeed, AttributeModifier.Operation.ADD_VALUE));
        }

    }

    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        onToggle(instance, entity, QUIRK_BESTOWAL, false);
        AttributeInstance chantSpeed = entity.getAttribute(TensuraAttributes.CHANT_SPEED);
        if (chantSpeed != null) {
            chantSpeed.removeModifier(QUIRK_BESTOWAL);
        }

    }

    public static void onToggle(ManasSkillInstance instance, LivingEntity entity, ResourceLocation id, boolean on) {
        AttributeInstance speed;
        AttributeInstance attackSpeed;
        if (on) {
            speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && !speed.hasModifier(id)) {
                speed.addOrReplacePermanentModifier(new AttributeModifier(id, instance.isMastered(entity) ? CONFIG.movementSpeedMastered : CONFIG.movementSpeed, AttributeModifier.Operation.ADD_VALUE));
            }

            attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null && !attackSpeed.hasModifier(id)) {
                attackSpeed.addOrReplacePermanentModifier(new AttributeModifier(id, instance.isMastered(entity) ? CONFIG.attackSpeedMastered : CONFIG.attackSpeed, AttributeModifier.Operation.ADD_VALUE));
            }
        } else {
            speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(id);
            }

            attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);
            if (attackSpeed != null) {
                attackSpeed.removeModifier(id);
            }
        }

    }

    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
        Level level = entity.level();
            if (CONFIG.powerStock) {
                if (instance.isMastered(entity)) {
                    if (level.getMaxLocalRawBrightness(entity.blockPosition()) <= 3) {
                        if (!SkillUtils.hasSkill(entity, (ManasSkill) QuirkSkills.POWER_STOCK.get())) {
                            if (!(instance.getMastery() < (double) 0.0F) && !instance.isTemporarySkill()) {
                                if (EnergyHelper.getBaseMaxAura(entity) > CONFIG.apAcquirement + 10000) {
                                    EnergyHelper.setMaxAura(entity, EnergyHelper.getBaseMaxAura(entity) -CONFIG.apAcquirement);
                                    TensuraSkillInstance eye = new TensuraSkillInstance(QuirkSkills.POWER_STOCK.get());
                                    eye.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                                    SkillHelper.learnSkill(entity, eye);
                                    if (entity instanceof ServerPlayer player) {
                                        player.displayClientMessage(Component.translatable("tensuraacadamia.skill.quirk_bestowal.grant").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }



    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, 5.0, false);

        if (entity instanceof Player){
            if (target instanceof Player){
                if (!SkillUtils.hasSkill(target, (ManasSkill) QuirkSkills.QUIRK_BESTOWAL.get())) {
                    if (!(instance.getMastery() < (double) 0.0F) && !instance.isTemporarySkill()) {
                        TensuraSkillInstance eye = new TensuraSkillInstance(QuirkSkills.QUIRK_BESTOWAL.get());
                        eye.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                        SkillHelper.learnSkill(target, eye);
                        SkillAPI.getSkillsFrom(entity).forgetSkill(QuirkSkills.QUIRK_BESTOWAL.get());
                        if (entity instanceof ServerPlayer player) {
                            player.displayClientMessage(Component.translatable("tensuraacadamia.skill.quirk_bestowal.pass").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
                        }
                        if (target instanceof ServerPlayer player) {
                            player.displayClientMessage(Component.translatable("tensuraacadamia.skill.quirk_bestowal.passed").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
                        }
                    }
                }
            }

        }


    }

}

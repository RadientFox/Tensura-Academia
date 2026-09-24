package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.attribute.ManasCoreAttribute;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class LifeForceQuirk extends Skill {
    private static final QuirkSkillsConfig.LifeForce CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).LifeForce;
    public static final ResourceLocation LIFE_FORCE = ResourceLocation.fromNamespaceAndPath("tracadamia", "life_force");

    public LifeForceQuirk() {
        super(SkillType.UNIQUE);
    }

    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }


    public int getModes(ManasSkillInstance instance) {
        return 1;
    }


    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/quirk_bestowal.png");
    }

    public String getModeId(ManasSkillInstance instance, int mode) {
        String var10000;
        switch (mode) {
            case 0 -> var10000 = "quirk_bestowal.bestow";
            default -> var10000 = super.getModeId(instance, mode);
        }

        return var10000;
    }


    private static final ResourceLocation HEALTH_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "life_force_health");
    private static final ResourceLocation SHP_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "life_force_shp");




    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");

        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300, (CONFIG.slownessLevel - 1), false, false, false));



        addHealthModifier(entity, (CONFIG.HPBonus - 1));
        addSHPModifier(entity, (CONFIG.SHPBonus - 1));





    }


    private static void addHealthModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void addSHPModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(SHP_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }




    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MAX_HEALTH);

        AttributeInstance attribute2 = entity.getAttribute(TensuraAttributes.MAX_SPIRITUAL_HEALTH);


        if (attribute != null) {
            attribute.removeModifier(HEALTH_MODIFIER);
        }
        if (attribute2 != null) {
            attribute2.removeModifier(SHP_MODIFIER);
        }

    }




    }

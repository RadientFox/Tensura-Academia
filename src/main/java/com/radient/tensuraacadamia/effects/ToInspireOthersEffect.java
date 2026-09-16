package com.radient.tensuraacadamia.effects;

import com.radient.tensuraacadamia.config.skills.OFAConfig;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.awt.*;

public class ToInspireOthersEffect extends MobEffect {

    private static final OFAConfig.OFA1st CONFIG = ConfigRegistry.getConfig(OFAConfig.class).OFA1st;
    public static final ResourceLocation OFA1ST = ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_1");

    public ToInspireOthersEffect() {
        super(MobEffectCategory.BENEFICIAL, new Color(40, 255, 44).getRGB());

        addAttributeModifier(
                ManasCoreAttributes.CRITICAL_ATTACK_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "ofa_inspiration_crit"),
                CONFIG.allyCrit,
                AttributeModifier.Operation.ADD_VALUE);

        addAttributeModifier(
                TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "ofa_inspiration_melee_dodge"),
                CONFIG.allyDodge,
                AttributeModifier.Operation.ADD_VALUE);

        addAttributeModifier(
                TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "ofa_inspiration_ranged_dodge"),
                CONFIG.allyDodge,
                AttributeModifier.Operation.ADD_VALUE);

        addAttributeModifier(
                TensuraAttributes.MAGICULE_REGENERATION_MULTIPLIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "ofa_inspiration_mp_regen"),
                CONFIG.regenMult,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.AURA_REGENERATION_MULTIPLIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "ofa_inspiration_ap_regen"),
                CONFIG.regenMult,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    }


}

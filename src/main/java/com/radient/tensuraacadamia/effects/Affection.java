package com.radient.tensuraacadamia.effects;

import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

import java.awt.Color;

public class Affection extends TensuraMobEffect {

    public Affection() {
        super(MobEffectCategory.BENEFICIAL, new Color(40, 255, 44).getRGB());

        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.MAX_HEALTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_health"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_armor"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_armor"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.ATTACK_KNOCKBACK,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.BLOCK_BREAK_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.BLOCK_INTERACTION_RANGE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.ENTITY_INTERACTION_RANGE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.FLYING_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.JUMP_STRENGTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.KNOCKBACK_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_armor"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.LUCK,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_armor"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.MAX_ABSORPTION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_armor"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.MINING_EFFICIENCY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_attack"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.MOVEMENT_EFFICIENCY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.OXYGEN_BONUS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.SAFE_FALL_DISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.SNEAKING_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.STEP_HEIGHT,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                Attributes.SUBMERGED_MINING_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);


        addAttributeModifier(
                Attributes.WATER_MOVEMENT_EFFICIENCY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_movement"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);



//tensura stats


        addAttributeModifier(
                TensuraAttributes.MAX_SPIRITUAL_HEALTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_health"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAX_MAGICULE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAX_AURA,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.AURA_REGENERATION_MULTIPLIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGICULE_REGENERATION_MULTIPLIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.FLAME_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.FLAME_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.ABILITY_LEARNING_GAIN,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.ABILITY_MASTERY_GAIN,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.ANALYSIS_DISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.AURA_GAIN,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGICULE_GAIN,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.AUTO_MELEE_DODGE_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.ANALYSIS_LEVEL,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.CHANT_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DARK_VISION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DARKNESS_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DARKNESS_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DARKNESS_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DODGE_INVULNERABILITY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DODGE_NEGATE_CHANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.DODGE_STRENGTH,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.EARTH_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.EARTH_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.EARTH_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.FLAME_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.GRAVITY_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.GRAVITY_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.HEAT_SENSE_RADIUS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.ILLUSION_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LAVA_CAPACITY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LAW_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LIGHT_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LIGHT_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LIGHT_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LIGHTNING_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.LIGHTNING_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGIC_BARRIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGIC_COST_MULTIPLIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGIC_INTERFERENCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MAGIC_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.MULTILAYER_BARRIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.PRESENCE_CONCEALMENT,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.PHYSICAL_BARRIER,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.PRESENCE_SENSE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.PHYSICAL_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.PRESENCE_SENSE_RADIUS,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.RESISTANCE_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.SOUND_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.SPACE_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.SPACE_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.SPACE_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.VIEW_ZOOM,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WARP_SHOT,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WATER_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WATER_CAPACITY,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WATER_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WATER_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WIND_BOOST,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WIND_RESIST_DEGRADATION,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

        addAttributeModifier(
                TensuraAttributes.WIND_RESISTANCE,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "affection_energy"),
                10.0,
                Operation.ADD_MULTIPLIED_TOTAL);

    }
}
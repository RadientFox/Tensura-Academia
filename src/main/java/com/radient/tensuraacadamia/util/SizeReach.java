package com.radient.tensuraacadamia.util;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * If you add a skill that increase range add it here and use the proper functions
 * so the range doesn't multiply against each other
 */

public final class SizeReach {
// this was made because I was getting confused with reach scaling
// some races increase base height and reach but stacking with quirks that increase range broke them? even though I would add value?
//the solution was to have reach be based on the size in general, but that would increase range too much if you had multiplie size/interaction range abilities
//Easier to have all size/interaction ranges go through here to go to the appropriate amount
// maybe there's a better solution and Im an idiot

    private static final QuirkSkillsConfig CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class);

    private static final ResourceLocation SIZE_REACH = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "size_reach");
    private static final List<ResourceLocation> REPLACED = List.of(
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "muscle_augmentation"),
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "body_bulk"),
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "endurance"),
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "body_morph")

    );
    private static final List<Holder<Attribute>> RANGES = List.of(Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE);

    private SizeReach() {
    }

    public static void update(LivingEntity entity) {
        update(entity, null);
    }

    public static void update(LivingEntity entity, @Nullable ManasSkill leaving) {
        if (entity.level().isClientSide) {
            return;
        }

        double perSize =
                Math.max(getRate(entity, QuirkSkills.MUSCLE_AUGMENTATION.get(), leaving, CONFIG.MuscleAugmentation.reachPerSize),
                        Math.max(getRate(entity, QuirkSkills.BODY_MORPH.get(), leaving, CONFIG.BodyMorph.reachPerSize),
                        Math.max(getRate(entity, QuirkSkills.BODY_BULK.get(), leaving, CONFIG.BodyBulk.reachPerSize),
                        Math.max(getRate(entity, QuirkSkills.ENDURANCE.get(), leaving, CONFIG.Endurance.reachPerSize),
                                (getRate(entity, QuirkSkills.GIGANTIFICATION.get(), leaving, CONFIG.Gigantification.reachPerSize)
                                        )
                                )
                                )
                        )
                );

        double reach = Math.max(0.0D, entity.getAttributeValue(Attributes.SCALE) - 1.0D) * perSize;
        for (Holder<Attribute> range : RANGES) {
            AttributeInstance instance = entity.getAttribute(range);
            if (instance != null) {
                REPLACED.forEach(instance::removeModifier);
            }

            Modifiers.set(entity, range, SIZE_REACH, reach, AttributeModifier.Operation.ADD_VALUE);
        }
    }

    private static double getRate(LivingEntity entity, ManasSkill skill, @Nullable ManasSkill leaving, double perSize) {
        return skill != leaving && SkillAPI.getSkillsFrom(entity).getSkill(skill).filter(instance -> instance.getMastery() >= 0.0D).isPresent() ? perSize : 0.0D;
    }

}

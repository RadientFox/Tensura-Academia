package com.radient.tensuraacadamia.util;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpiderQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

public final class QuirkCosmetics {

    public static final String FAMILY_PREFIX = "quirk_";

    private QuirkCosmetics() {
    }

    public static void addFamilies(LivingEntity entity, boolean borrowed, Set<String> families) {
        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(entity).getLearnedSkills()) {
            if (!TensuraAcadamia.MODID.equals(instance.getSkillId().getNamespace()) || instance.getMastery() < 0.0D) {
                continue;
            }

            if (borrowed || !instance.isTemporarySkill()) {
                families.add(FAMILY_PREFIX + instance.getSkillId().getPath());
            }
        }

        if (SpiderQuirk.hasIronSpider(entity)) {
            families.add(FAMILY_PREFIX + "iron_spider");
        }
    }
}

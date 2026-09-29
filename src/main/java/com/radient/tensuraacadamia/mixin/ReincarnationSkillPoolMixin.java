package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Add custom quirks before Tensura filters the reincarnation pool. */
@Mixin(value = ReincarnationMenu.class, remap = false)
public abstract class ReincarnationSkillPoolMixin {
    @ModifyVariable(method = "getReincarnationSkills", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<ManasSkill> tracadamia$addCustomQuirks(List<ManasSkill> pool) {
        List<ManasSkill> expanded = new ArrayList<>(pool);
        addIfMissing(expanded, QuirkSkills.HALF_COLD_HALF_HOT.get());
        addIfMissing(expanded, QuirkSkills.BEAMS_FROM_HIS_EYES.get());
        addIfMissing(expanded, QuirkSkills.IMPURE_BEAM.get());
        addIfMissing(expanded, QuirkSkills.VINES.get());
        addIfMissing(expanded, QuirkSkills.BLAST.get());
        addIfMissing(expanded, QuirkSkills.DARK_SHADOW.get());
        return expanded;
    }

    private static void addIfMissing(List<ManasSkill> skills, ManasSkill skill) {
        if (!skills.contains(skill)) skills.add(skill);
    }
}

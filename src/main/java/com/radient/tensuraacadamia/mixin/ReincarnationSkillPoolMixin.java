package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.menu.ReincarnationMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Add the quirk before Tensura applies eligibility checks, on both old and new pool implementations. */
@Mixin(value = ReincarnationMenu.class, remap = false)
public abstract class ReincarnationSkillPoolMixin {
    @ModifyVariable(method = "getReincarnationSkills", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static List<ManasSkill> tracadamia$addThermalQuirk(List<ManasSkill> pool) {
        ManasSkill skill = QuirkSkills.HALF_COLD_HALF_HOT.get();
        if (pool.contains(skill)) return pool;
        List<ManasSkill> expanded = new ArrayList<>(pool);
        expanded.add(skill);
        return expanded;
    }
}

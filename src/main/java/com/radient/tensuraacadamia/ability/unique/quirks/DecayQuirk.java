package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.tensura.ability.skill.Skill;

public class DecayQuirk extends Skill{

    public DecayQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }
}

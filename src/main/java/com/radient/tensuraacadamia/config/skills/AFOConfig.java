package com.radient.tensuraacadamia.config.skills;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;

public final class AFOConfig extends ManasConfig {
    @Comment("Allow mastered Usurper to evolve into All For One when its normal requirements are met. False keeps All For One command-only.")
    public boolean allowUsurperEvolution = false;

    @Override
    public String getFileName() {
        return "tracadamia/ability/skill/afo_config";
    }
}

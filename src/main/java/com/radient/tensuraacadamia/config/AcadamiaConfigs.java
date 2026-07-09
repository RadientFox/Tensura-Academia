package com.radient.tensuraacadamia.config;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;

public class AcadamiaConfigs {
    public AcadamiaConfigs() {
    }

    public static void init() {

        ConfigRegistry.registerConfig(new QuirkSkillsConfig());
    }
}

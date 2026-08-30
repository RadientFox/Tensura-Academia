package com.radient.tensuraacadamia.ability.ultimate.ofausers;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class OFA1st extends Skill {
        private static final QuirkSkillsConfig.Power_Stock CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Power_Stock;
        public static final ResourceLocation POWER_STOCK = ResourceLocation.fromNamespaceAndPath("tracadamia", "one_for_all_1");

    public OFA1st() {
            super(SkillType.ULTIMATE);
        }


        public int getModes(ManasSkillInstance instance) {
            return 3;
        }

        public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {

            if (reverse) {
                return mode == 0 ? 3 : mode - 1;
            } else {
                return mode == 3 ? 0 : mode + 1;
            }
        }

        public @Nullable ResourceLocation getSkillIcon() {
            return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/ultimate/one_for_all_1.png");
        }

        public String getModeId(ManasSkillInstance instance, int mode) {
            String var10000;
            switch (mode) {
                case 0 -> var10000 = "one_for_all_1.output";
                case 1 -> var10000 = "one_for_all_1.smash";
                case 2 -> var10000 = "one_for_all_1.cowling";
                case 3 -> var10000 = "one_for_all_1.bestow";
                default -> var10000 = super.getModeId(instance, mode);
            }

            return var10000;
        }



    }

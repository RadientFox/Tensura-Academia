package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public class Power_Stock extends Skill {
        private static final QuirkSkillsConfig.Power_Stock CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Power_Stock;
        public static final ResourceLocation SPINEL = ResourceLocation.fromNamespaceAndPath("tensuraacadamia", "power_stock");

    public Power_Stock() {
            super(Skill.SkillType.UNIQUE);
        }


        public int getModes(ManasSkillInstance instance) {
            return 2;
        }

        public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {

                if (reverse) {
                    return mode == 0 ? 1 : mode - 1;
                } else {
                    return mode == 1 ? 0 : mode + 1;
                }



        }

        public @Nullable ResourceLocation getSkillIcon() {
            return ResourceLocation.fromNamespaceAndPath("tensuraacadamia", "textures/skill/unique/power_stock.png");
        }

        public String getModeId(ManasSkillInstance instance, int mode) {
            String var10000;
            switch (mode) {
                case 0 -> var10000 = "power_stock.output";
                case 1 -> var10000 = "power_stock.smash";
                default -> var10000 = super.getModeId(instance, mode);
            }

            return var10000;
        }





}

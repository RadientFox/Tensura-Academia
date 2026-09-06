package com.radient.tensuraacadamia.config.skills;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

public class OFAConfig extends ManasConfig {

    public OFAConfig.OFA OFA = new OFAConfig.OFA();
    public OFAConfig.OFA1st OFA1st = new OFAConfig.OFA1st();


    public OFAConfig() {
    }

    public String getFileName() {
        return "tracadamia/ability/skill/ofa_config";
    }


    public static class OFA extends ManasSubConfig {

        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 100_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 2_500.0;
        @Comment("Chance to activate Evolving Might")
        public double evolvingMight = 0.15;
        @Comment("Power percent used for Evolving Might")
        public double evolvingMightPower = 0.25;
        @Comment("Evolving Might Cooldown.")
        public double MightCooldown = 10.0;
        @Comment("Stockpile damage conversion.")
        public double stockpileConversion = 0.01;

        public OFA() {
        }
    }

    public static class OFA1st extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("Ally Crit Chance")
        public double allyCrit = 100.0;
        @Comment("Ally Dodge Chance")
        public double allyDodge = 30.0;
        @Comment("Ally Regen Multiplier")
        public double regenMult = 15.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 400.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Delaware Smash AP cost")
        public double delawareCost = 75_000.0;
        @Comment("Texas Smash AP cost")
        public double texasCost = 300_000.0;
        @Comment("Oklahoma Smash AP cost")
        public double OklahomaCost = 500_000.0;
        @Comment("United States Smash AP cost minimum (percentage)")
        public double USCost = 10.0;
        @Comment("United States Smash should use all ap remaining ap(true) or only use minimum(false)")
        public boolean USfull = true;
        @Comment("Smash Percent damage boost while in full cowling")
        public double fullcowlsmash = 0.25;
        @Comment("Full cowling Damage increase max")
        public double fullcowldamage = 350.0;
        @Comment("Full cowling Armor increase")
        public double fullcowlarmor = 120.0;
        @Comment("Full cowling Speed")
        public double fullcowlSpeed = 0.3;
        @Comment("Full cowling Jump Height")
        public double fullcowlJump = 0.3;


        public OFA1st() {
        }
    }
}

package com.radient.tensuraacadamia.config.skills;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

public class OFAConfig extends ManasConfig {

    public OFAConfig.OFA OFA = new OFAConfig.OFA();
    public OFAConfig.OFA1st OFA1st = new OFAConfig.OFA1st();
    public OFAConfig.OFA2nd OFA2nd = new OFAConfig.OFA2nd();
    public OFAConfig.OFA3rd OFA3rd = new OFAConfig.OFA3rd();
    public OFAConfig.OFA4th OFA4th = new OFAConfig.OFA4th();
    public OFAConfig.OFA5th OFA5th = new OFAConfig.OFA5th();
    public OFAConfig.OFA6th OFA6th = new OFAConfig.OFA6th();
    public OFAConfig.OFA7th OFA7th = new OFAConfig.OFA7th();
    public OFAConfig.OFA8th OFA8th = new OFAConfig.OFA8th();
    public OFAConfig.OFA9th OFA9th = new OFAConfig.OFA9th();


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
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        public double fullcowlSpeed = 0.5;

        public OFA1st() {
        }
    }


    public static class OFA2nd extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 750.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 2_000_000;


        public OFA2nd() {
        }

    }    public static class OFA3rd extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 1_000.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 2_500_000;


        public OFA3rd() {
        }
    }

    public static class OFA4th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 2_900.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 4_000_000;


        public OFA4th() {
        }
    }

    public static class OFA5th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 3_250.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 5_500_000;


        public OFA5th() {
        }
    }

    public static class OFA6th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 3_500.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 7_000_000;


        public OFA6th() {
        }
    }
    public static class OFA7th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 4_000.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 8_000_000;


        public OFA7th() {
        }
    }

    public static class OFA8th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 6_000.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 10_000_000;


        public OFA8th() {
        }
    }


    public static class OFA9th extends ManasSubConfig {
        @Comment("Aura Acquirement Cost.")
        public double apAcquirement = 1_000_000.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 10_000.0;
        @Comment("ofa user 100% output damage")
        public double fullDamage = 6_500.0;
        @Comment("Detroit Smash AP cost")
        public double detroitCost = 100_000.0;
        @Comment("Carolina Smash AP cost")
        public double carolinaCost = 250_000.0;
        @Comment("Carolina Smash dash distance")
        public double carolinaDistance = 20.0;
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
        @Comment("100% output EP needed")
        public double fullOutputEP = 15_000_000;


        public OFA9th() {
        }
    }
}

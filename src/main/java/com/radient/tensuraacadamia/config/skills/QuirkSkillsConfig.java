package com.radient.tensuraacadamia.config.skills;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

public class QuirkSkillsConfig extends ManasConfig {

    public Power_Stock Power_Stock = new Power_Stock();
    public QuirkBestowal QuirkBestowal = new QuirkBestowal();
    public DangerSense DangerSense = new DangerSense();
    public MuscleAugmentation MuscleAugmentation = new MuscleAugmentation();
    public FatAbsorption FatAbsorption = new FatAbsorption();
    public QuadArms QuadArms = new QuadArms();
    public ImpactRecoil ImpactRecoil = new ImpactRecoil();
    public WeatherManipulation WeatherManipulation = new WeatherManipulation();
    public Strongarm Strongarm = new Strongarm();
    public KineticBooster KineticBooster = new KineticBooster();
    public AbsorbAndRelease AbsorbAndRelease = new AbsorbAndRelease();
    public Rupture Rupture = new Rupture();
    public Burst Burst = new Burst();


    public QuirkSkillsConfig() {
    }

    public String getFileName() {
        return "tracadamia/ability/skill/quirk_config";
    }


    public static class Power_Stock extends ManasSubConfig {
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

        public Power_Stock() {
        }
    }

    public static class QuirkBestowal extends ManasSubConfig {
        @Comment("Magicule Acquirement Cost.")
        public double mpAcquirement = 100.0;
        @Comment("Skill Mastery Points.")
        public double masteryPoints = 2_500.0;
        @Comment("The chant speed multiplier when activated.")
        public double chantSpeed = 2.0;
        @Comment("The bonus movement speed when activated.")
        public double movementSpeed = 0.01;
        @Comment("The bonus movement speed when activated with mastery.")
        public double movementSpeedMastered = 0.02;
        @Comment("The bonus movement speed when activated.")
        public double attackSpeed = 0.2;
        @Comment("The bonus movement speed when activated with mastery.")
        public double attackSpeedMastered = 0.4;
        @Comment("Should Power Stock be obtainable naturally")
        public boolean powerStock = true;
        @Comment("Aura Acquirement Cost For Power Stock.")
        public double apAcquirement = 100_000.0;

        public QuirkBestowal() {
        }
    }

    public static class DangerSense extends ManasSubConfig {
        @Comment("Aura Acquirement Cost")
        public double apAcquirement = 400_000.0;
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Detect dodge chance")
        public double dodgeChance = 60.0;
        @Comment("Detect dodge chance while mastered")
        public double dodgeChanceMastered = 80.0;
        @Comment("Detect dodge chance against dodge bypass while mastered")
        public double bypassDodgeChance = 50.0;

        @Comment("Perfect Dodge: Perfect dodge amount")
        public int perfectDodges = 6;
        @Comment("Perfect Dodge: Perfect dodge amount while mastered")
        public int perfectDodgesMastered = 12;
        @Comment("Perfect Dodge: Refill time in seconds after running out")
        public int perfectDodgeRefill = 60;

        public DangerSense() {
        }
    }

    public static class MuscleAugmentation extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Mastery needed for muscle overload modes")
        public double overloadUnlockMastery = 0.75;

        @Comment("Muscle Mass: Armor per size gained")
        public double armorPerSize = 5.0;
        @Comment("Muscle Mass: Knockback resistance per size gained")
        public double knockbackResistancePerSize = 0.1;
        @Comment("Muscle Mass: Max health bonus per size")
        public double healthPerSize = 0.25;

        @Comment("Muscle Enhancement: Size gained per tick")
        public double growthPerTick = 0.02;
        @Comment("Muscle Enhancement: Max size")
        public double maxSize = 2.0;
        @Comment("Muscle Enhancement: Max size with mastery")
        public double maxSizeMastered = 5.0;
        @Comment("Muscle Enhancement: Bonus melee damage per size gained")
        public double damagePerSize = 1.0;

        @Comment("Muscle Overload: Strike max damage multiplier")
        public double strikeMaxOutput = 3.0;
        @Comment("Muscle Overload: Strike speed reduction")
        public double strikeSpeedPenalty = 0.7;
        @Comment("Muscle Overload: Shield melee damage reduction")
        public double shieldReduction = 0.8;
        @Comment("Muscle Overload: Shield cooldown in seconds after blocking")
        public int shieldCooldown = 7;

        public MuscleAugmentation() {
        }
    }

    public static class FatAbsorption extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Fat Stock: Fat gained per saturation")
        public double fatPerSaturation = 1.0;
        @Comment("Fat Stock: Armor per fat")
        public double armorPerFat = 0.02;
        @Comment("Fat Stock: Max armor")
        public double maxArmor = 20.0;
        @Comment("Fat Stock: Knockback resistance per fat")
        public double knockbackResistancePerFat = 0.0005;
        @Comment("Fat Stock: Max knockback resistance")
        public double maxKnockbackResistance = 1.0;
        @Comment("Fat Stock: Damage reduction per fat")
        public double damageReductionPerFat = 0.0002;
        @Comment("Fat Stock: Max damage reduction")
        public double maxDamageReduction = 0.5;
        @Comment("Fat Stock: Fat lost per damage taken")
        public double damageFatLoss = 0.25;
        @Comment("Fat Stock: Fat needed for max torso size")
        public double torsoMaxFat = 1_000.0;
        @Comment("Fat Stock: Torso width at max size")
        public double torsoMaxWidth = 1.2;
        @Comment("Fat Stock: Torso depth at max size")
        public double torsoMaxDepth = 2.0;

        @Comment("Restrain: Fat needed")
        public double restrainFat = 100.0;
        @Comment("Restrain: Range in blocks")
        public double restrainRange = 0.5;

        @Comment("Shield into Spear: Bonus damage per fat")
        public double spearDamagePerFat = 1.0;
        @Comment("Shield into Spear: Fat needed to degrade resistances")
        public double spearResistanceFat = 1_000.0;
        @Comment("Shield into Spear: Fat needed to degrade nullifications")
        public double spearNullificationFat = 7_000.0;
        @Comment("Shield into Spear: Fat needed to ignore resistances and nullifications")
        public double spearIgnoreFat = 15_000.0;

        public FatAbsorption() {
        }
    }

    public static class QuadArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Mining Efficiency: Mining speed multiplier")
        public double miningSpeedMultiplier = 2.0;
        @Comment("Mining Efficiency: Swim speed bonus")
        public double swimSpeedBonus = 0.15;

        @Comment("Double Impact: Ticks before 2nd attack")
        public int doubleImpactDelay = 5;

        @Comment("Hold: Grab range")
        public double holdRange = 2.0;
        @Comment("Hold: Distance the grabbed entity is held")
        public double holdDistance = 1.5;

        public QuadArms() {
        }
    }

    public static class ImpactRecoil extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Impact Recoil: Physical damage reduction")
        public double damageReduction = 0.5;
        @Comment("Impact Recoil: Physical damage reflected")
        public double recoilPercent = 0.5;

        public ImpactRecoil() {
        }
    }

    public static class WeatherManipulation extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Elemental Damage: Water, wind, and lightning damage boost")
        public double elementalBoost = 3.5;

        @Comment("Lightning Strike: Range")
        public double strikeRange = 30.0;
        @Comment("Lightning Strike: Damage")
        public double strikeDamage = 50.0;
        @Comment("Lightning Strike: Damage with mastery")
        public double strikeDamageMastered = 75.0;

        @Comment("Summon Storm: Radius")
        public double stormRadius = 7.0;
        @Comment("Summon Storm: Radius with mastery")
        public double stormRadiusMastered = 15.0;
        @Comment("Summon Storm: Damage per bolt")
        public double stormDamage = 100.0;
        @Comment("Summon Storm: Damage per bolt with mastery")
        public double stormDamageMastered = 150.0;
        @Comment("Summon Storm: Ticks between the two bolts")
        public int stormBoltDelay = 10;

        @Comment("Bolt Charge: Charge time in seconds")
        public int chargeSeconds = 10;
        @Comment("Bolt Charge: Charge time in seconds with mastery")
        public int chargeSecondsMastered = 5;
        @Comment("Bolt Charge: Range in blocks")
        public double chargeRange = 30.0;
        @Comment("Bolt Charge: Damage per bolt")
        public double barrageDamage = 200.0;
        @Comment("Bolt Charge: Damage per bolt with mastery")
        public double barrageDamageMastered = 300.0;
        @Comment("Bolt Charge: Barrage length in seconds")
        public int barrageSeconds = 4;
        @Comment("Bolt Charge: Barrage length in seconds with mastery")
        public int barrageSecondsMastered = 8;
        @Comment("Bolt Charge: Ticks between barrage bolts")
        public int barrageInterval = 10;

        public WeatherManipulation() {
        }
    }

    public static class Strongarm extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Rotation: Max rotations")
        public int maxRotations = 250;
        @Comment("Rotation: Max rotations while mastered")
        public int maxRotationsMastered = 500;
        @Comment("Rotation: Seconds between punches before rotations are lost")
        public int rotationTimeout = 5;
        @Comment("Rotation: Rotations lost per second")
        public int rotationLoss = 5;

        @Comment("Rotation Increase: Physical damage bonus per rotation")
        public double damagePerRotation = 0.01;
        @Comment("Rotation Increase: Physical damage bonus per rotation while mastered")
        public double damagePerRotationMastered = 0.02;

        @Comment("Rotation Bypass: Rotations needed to bypass")
        public int bypassRotations = 200;

        @Comment("Enhanced Physical: Bonus physical damage")
        public double enhancedDamage = 15.0;
        @Comment("Enhanced Physical: Bonus physical damage while mastered")
        public double enhancedDamageMastered = 30.0;

        @Comment("Strongarm Moves: Stun length in ticks after each hit")
        public int moveStunTicks = 10;

        @Comment("Flurry: Punch amount")
        public int flurryPunches = 15;
        @Comment("Flurry: Punch amount while mastered")
        public int flurryPunchesMastered = 30;
        @Comment("Flurry: Length in seconds")
        public double flurrySeconds = 3.0;
        @Comment("Flurry: Length in seconds while mastered")
        public double flurrySecondsMastered = 6.0;
        @Comment("Flurry: Melee damage multiplier")
        public double flurryDamage = 1.2;

        @Comment("Bullet Punches: Lunge distance in blocks")
        public double bulletLungeDistance = 4.5;
        @Comment("Bullet Punches: Punch amount")
        public int bulletPunches = 6;
        @Comment("Bullet Punches: Punch amount while mastered")
        public int bulletPunchesMastered = 12;
        @Comment("Bullet Punches: Length in seconds")
        public double bulletSeconds = 2.0;
        @Comment("Bullet Punches: Length in seconds while mastered")
        public double bulletSecondsMastered = 4.0;
        @Comment("Bullet Punches: Melee damage multiplier")
        public double bulletDamage = 1.8;
        @Comment("Bullet Punches: Rotations gained per hit")
        public int bulletRotations = 2;

        @Comment("Quick Draw: Counter window in seconds")
        public double quickDrawWindow = 1.5;
        @Comment("Quick Draw: Counter window in seconds while mastered")
        public double quickDrawWindowMastered = 3.0;
        @Comment("Quick Draw: Punch amount")
        public int quickDrawPunches = 10;
        @Comment("Quick Draw: Punch amount while mastered")
        public int quickDrawPunchesMastered = 20;
        @Comment("Quick Draw: Ticks between punches")
        public int quickDrawInterval = 2;
        @Comment("Quick Draw: Melee damage multiplier")
        public double quickDrawDamage = 0.75;
        @Comment("Quick Draw: Stun length in seconds")
        public int quickDrawStun = 3;
        @Comment("Quick Draw: Stun length in seconds while mastered")
        public int quickDrawStunMastered = 5;

        public Strongarm() {
        }
    }

    public static class KineticBooster extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Increase: Max output level")
        public int maxLevel = 10;
        @Comment("Increase: Physical damage bonus per level")
        public double damagePerLevel = 0.10;
        @Comment("Increase: Physical damage bonus per level while mastered")
        public double damagePerLevelMastered = 0.20;

        public KineticBooster() {
        }
    }

    public static class SpringlikeLimbs extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Store Arms: Energy stored per physical attack")
        public double armEnergyPerAttack = 1.0;
        @Comment("Store Arms: Max energy")
        public double maxArmEnergy = 100.0;
        @Comment("Store Arms: Max energy while mastered")
        public double maxArmEnergyMastered = 200.0;

        @Comment("Store Legs: Energy stored per crouch or jump")
        public double legEnergyPerMove = 1.0;
        @Comment("Store Legs: Fall damage reduction, the reduced damage is stored as energy")
        public double fallReduction = 0.75;
        @Comment("Store Legs: Max energy")
        public double maxLegEnergy = 100.0;
        @Comment("Store Legs: Max energy while mastered")
        public double maxLegEnergyMastered = 200.0;

        @Comment("Spring Punch: Damage bonus per energy used")
        public double springPunchDamage = 0.01;
        @Comment("Spring Punch: Damage bonus per energy used while mastered")
        public double springPunchDamageMastered = 0.02;

        @Comment("Super Jump: Jump height bonus in blocks")
        public double superJumpHeight = 4.0;
        @Comment("Super Jump: Energy used per jump")
        public double superJumpCost = 5.0;

        public SpringlikeLimbs() {
        }
    }

    public static class AbsorbAndRelease extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Absorb: Absorbed damage multiplier while mastered")
        public double absorbMultiplierMastered = 1.5;
        @Comment("Absorb: Max stored damage")
        public double maxStored = 1_500.0;
        @Comment("Absorb: Max stored damage while mastered")
        public double maxStoredMastered = 2_000.0;

        @Comment("Release: Radius in blocks, damage is split between all targets")
        public double releaseRadius = 6.0;
        @Comment("Release: Radius in blocks while mastered")
        public double releaseRadiusMastered = 10.0;
        @Comment("Release: Released damage taken by the user")
        public double releaseSelfDamage = 0.25;
        @Comment("Release: Released damage taken by the user while mastered")
        public double releaseSelfDamageMastered = 0.10;

        public AbsorbAndRelease() {
        }
    }

    public static class Rupture extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Rupture: Charge time in seconds")
        public int chargeSeconds = 5;
        @Comment("Rupture: Radius in blocks")
        public double radius = 10.0;
        @Comment("Rupture: Radius in blocks while mastered")
        public double radiusMastered = 15.0;
        @Comment("Rupture: Damage multiplier on current health, split between four directions")
        public double healthMultiplier = 2.0;
        @Comment("Rupture: Damage multiplier on current health while mastered")
        public double healthMultiplierMastered = 3.0;

        public Rupture() {
        }
    }

    public static class Burst extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Burst: Charge time in seconds")
        public int chargeSeconds = 5;
        @Comment("Burst: Radius in blocks")
        public double radius = 15.0;
        @Comment("Burst: Radius in blocks while mastered")
        public double radiusMastered = 25.0;
        @Comment("Burst: Damage multiplier on max health")
        public double healthMultiplier = 2.0;
        @Comment("Burst: Damage multiplier on max health while mastered")
        public double healthMultiplierMastered = 3.0;
        @Comment("Burst: Explosion breaks blocks, follows Tensura's skill griefing gamerule")
        public boolean breakBlocks = true;

        public Burst() {
        }
    }
}

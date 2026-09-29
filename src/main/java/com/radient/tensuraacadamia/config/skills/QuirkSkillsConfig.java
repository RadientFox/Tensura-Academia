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
    public SpringlikeLimbs SpringlikeLimbs = new SpringlikeLimbs();
    public AbsorbAndRelease AbsorbAndRelease = new AbsorbAndRelease();
    public Rupture Rupture = new Rupture();
    public Burst Burst = new Burst();
    public Multiplier Multiplier = new Multiplier();
    public Mole Mole = new Mole();
    public ShockAbsorption ShockAbsorption = new ShockAbsorption();
    public Telekinesis Telekinesis = new Telekinesis();
    public Scanning Scanning = new Scanning();
    public Neutralization Neutralization = new Neutralization();
    public Infrared Infrared = new Infrared();
    public Attraction Attraction = new Attraction();
    public VibrationDetection VibrationDetection = new VibrationDetection();
    public Vibrate Vibrate = new Vibrate();
    public DupliArms DupliArms = new DupliArms();
    public BodyMorph BodyMorph = new BodyMorph();
    public ExtraArms ExtraArms = new ExtraArms();
    public Tail Tail = new Tail();
    public ZeroGravity ZeroGravity = new ZeroGravity();
    public SlideAndGlide SlideAndGlide = new SlideAndGlide();


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
        @Comment("Aura cost per activation")
        public double auraCost = 0.0;

        @Comment("Detect: Cooldown in seconds")
        public int detectCooldown = 0;
        @Comment("Detect: Dodge chance")
        public double dodgeChance = 60.0;
        @Comment("Detect: Dodge chance while mastered")
        public double dodgeChanceMastered = 80.0;
        @Comment("Detect: Dodge chance against dodge bypass while mastered")
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
        @Comment("Aura cost per activation")
        public double auraCost = 750.0;

        @Comment("Muscle Mass: Armor per size gained")
        public double armorPerSize = 5.0;
        @Comment("Muscle Mass: Knockback resistance per size gained")
        public double knockbackResistancePerSize = 0.1;
        @Comment("Muscle Mass: Max health bonus per size")
        public double healthPerSize = 0.25;

        @Comment("Muscle Enhancement: Cooldown in seconds")
        public int enhancementCooldown = 0;
        @Comment("Muscle Enhancement: Size gained per tick")
        public double growthPerTick = 0.02;
        @Comment("Muscle Enhancement: Max size")
        public double maxSize = 2.0;
        @Comment("Muscle Enhancement: Max size with mastery")
        public double maxSizeMastered = 5.0;
        @Comment("Muscle Enhancement: Bonus melee damage per size gained")
        public double damagePerSize = 1.0;

        @Comment("Muscle Overload Strike: Cooldown in seconds")
        public int strikeModeCooldown = 5;
        @Comment("Muscle Overload: Strike max damage multiplier")
        public double strikeMaxOutput = 3.0;
        @Comment("Muscle Overload: Strike speed reduction")
        public double strikeSpeedPenalty = 0.7;

        @Comment("Muscle Overload Shield: Cooldown in seconds")
        public int shieldModeCooldown = 0;
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
        @Comment("Aura cost per activation")
        public double auraCost = 200.0;

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

        @Comment("Restrain: Cooldown in seconds")
        public int restrainCooldown = 5;
        @Comment("Restrain: Fat needed")
        public double restrainFat = 100.0;
        @Comment("Restrain: Range in blocks")
        public double restrainRange = 0.5;

        @Comment("Shield into Spear: Cooldown in seconds")
        public int spearCooldown = 5;
        @Comment("Shield into Spear: Bonus damage per fat")
        public double spearDamagePerFat = 1.0;
        @Comment("Shield into Spear: Fat needed to degrade resistances")
        public double spearResistanceFat = 1_000.0;
        @Comment("Shield into Spear: Fat needed to degrade nullifications")
        public double spearNullificationFat = 7_000.0;
        @Comment("Shield into Spear: Fat needed to ignore resistances and nullifications")
        public double spearIgnoreFat = 15_000.0;

        @Comment("BMI Check: Cooldown in seconds")
        public int bmiCooldown = 0;

        public FatAbsorption() {
        }
    }

    public static class QuadArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 0.0;

        @Comment("Mining Efficiency: Mining speed multiplier")
        public double miningSpeedMultiplier = 2.0;
        @Comment("Mining Efficiency: Swim speed bonus")
        public double swimSpeedBonus = 0.15;

        @Comment("Double Impact: Ticks before 2nd attack")
        public int doubleImpactDelay = 5;

        @Comment("Hold: Cooldown in seconds")
        public int holdCooldown = 2;
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
        @Comment("Aura cost per activation")
        public double auraCost = 0.0;

        @Comment("Impact Recoil: Physical damage reduction")
        public double damageReduction = 0.5;
        @Comment("Impact Recoil: Physical damage reflected")
        public double recoilPercent = 0.5;

        public ImpactRecoil() {
        }
    }

    public static class WeatherManipulation extends ManasSubConfig  {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 500.0;

        @Comment("Elemental Damage: Water, wind, and lightning damage boost")
        public double elementalBoost = 3.5;
        @Comment("Elemental Resistance: Water and wind resistance")
        public double elementalResistance = 0.5;

        @Comment("Lightning Strike: Cooldown in seconds")
        public int strikeCooldown = 5;
        @Comment("Lightning Strike: Range")
        public double strikeRange = 30.0;
        @Comment("Lightning Strike: Damage")
        public double strikeDamage = 50.0;
        @Comment("Lightning Strike: Damage with mastery")
        public double strikeDamageMastered = 75.0;

        @Comment("Summon Storm: Cooldown in seconds")
        public int stormCooldown = 15;
        @Comment("Summon Storm: Radius")
        public double stormRadius = 10.0;
        @Comment("Summon Storm: Radius with mastery")
        public double stormRadiusMastered = 20.0;
        @Comment("Summon Storm: Damage per bolt")
        public double stormDamage = 100.0;
        @Comment("Summon Storm: Damage per bolt with mastery")
        public double stormDamageMastered = 150.0;
        @Comment("Summon Storm: Ticks between the two bolts")
        public int stormBoltDelay = 10;

        @Comment("Bolt Charge: Cooldown in seconds")
        public int boltChargeCooldown = 20;
        @Comment("Bolt Charge: Charge time in seconds")
        public int chargeSeconds = 10;
        @Comment("Bolt Charge: Charge time in seconds with mastery")
        public int chargeSecondsMastered = 5;
        @Comment("Bolt Charge: Range in blocks")
        public double chargeRange = 45.0;
        @Comment("Bolt Charge: Damage of the lightning striking around the user while charging")
        public double chargeStrikeDamage = 50.0;
        @Comment("Bolt Charge: Damage of the lightning striking around the user while charging, with mastery")
        public double chargeStrikeDamageMastered = 75.0;
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
        @Comment("Aura cost per activation")
        public double auraCost = 500.0;

        @Comment("Rotation: Max rotations")
        public int maxRotations = 250;
        @Comment("Rotation: Max rotations while mastered")
        public int maxRotationsMastered = 500;
        @Comment("Rotation: Seconds between punches before rotations are lost")
        public int rotationTimeout = 5;
        @Comment("Rotation: Rotations lost per second")
        public int rotationLoss = 5;

        @Comment("Rotation Increase: Physical damage bonus % per rotation")
        public double damagePerRotation = 0.005;
        @Comment("Rotation Increase: Physical damage bonus % per rotation while mastered")
        public double damagePerRotationMastered = 0.01;

        @Comment("Rotation Bypass: Rotations needed for physical attack degredation")
        public int bypassRotations = 250;

        @Comment("Enhanced Physical: Bonus physical damage while toggled")
        public double enhancedDamage = 15.0;
        @Comment("Enhanced Physical: Bonus physical damage while toggled and mastered")
        public double enhancedDamageMastered = 30.0;

        @Comment("Strongarm Moves: Stun length in ticks after each hit")
        public int moveStunTicks = 10;

        @Comment("Flurry: Cooldown in seconds")
        public int flurryCooldown = 12;
        @Comment("Flurry: Punch amount")
        public int flurryPunches = 10;
        @Comment("Flurry: Punch amount while mastered")
        public int flurryPunchesMastered = 20;
        @Comment("Flurry: Length in seconds")
        public double flurrySeconds = 2.0;
        @Comment("Flurry: Length in seconds while mastered")
        public double flurrySecondsMastered = 4.0;
        @Comment("Flurry: Melee damage multiplier")
        public double flurryDamage = 1.2;

        @Comment("Bullet Punches: Cooldown in seconds")
        public int bulletCooldown = 15;
        @Comment("Bullet Punches: Lunge distance in blocks")
        public double bulletLungeDistance = 4.5;
        @Comment("Bullet Punches: Punch amount")
        public int bulletPunches = 6;
        @Comment("Bullet Punches: Punch amount while mastered")
        public int bulletPunchesMastered = 12;
        @Comment("Bullet Punches: Length in seconds")
        public double bulletSeconds = 2.0;
        @Comment("Bullet Punches: Length in seconds while mastered")
        public double bulletSecondsMastered = 3.0;
        @Comment("Bullet Punches: Melee damage multiplier")
        public double bulletDamage = 1.8;
        @Comment("Bullet Punches: Rotations gained per hit")
        public int bulletRotations = 2;

        @Comment("Quick Draw: Cooldown in seconds")
        public int quickDrawCooldown = 15;
        @Comment("Quick Draw: Counter window in seconds")
        public double quickDrawWindow = 1.5;
        @Comment("Quick Draw: Counter window in seconds while mastered")
        public double quickDrawWindowMastered = 3.0;
        @Comment("Quick Draw: Punch amount")
        public int quickDrawPunches = 5;
        @Comment("Quick Draw: Punch amount while mastered")
        public int quickDrawPunchesMastered = 10;
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
        @Comment("Aura cost per activation")
        public double auraCost = 100.0;

        @Comment("Increase: Max output level")
        public int maxLevel = 10;
        @Comment("Increase: Physical damage added per level")
        public double outputDamage = 5.0;
        @Comment("Increase: Physical damage added per level while mastered")
        public double outputDamageMastered = 10.0;

        public KineticBooster() {
        }
    }

    public static class SpringlikeLimbs extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 100.0;

        @Comment("Store Arms: Energy stored per physical attack")
        public double armEnergyPerAttack = 2.0;
        @Comment("Store Arms: Max energy")
        public double maxArmEnergy = 100.0;
        @Comment("Store Arms: Max energy while mastered")
        public double maxArmEnergyMastered = 200.0;

        @Comment("Store Legs: Energy stored per crouch or jump")
        public double legEnergyPerMove = 1.0;
        @Comment("Store Legs: Fall damage reduction, the reduced damage is stored as energy")
        public double fallReduction = 0.75;
        @Comment("Store Legs: Max energy")
        public double maxLegEnergy = 50.0;
        @Comment("Store Legs: Max energy while mastered")
        public double maxLegEnergyMastered = 100.0;

        @Comment("Spring Punch: Cooldown in seconds")
        public int springPunchCooldown = 6;
        @Comment("Spring Punch: Damage bonus % per energy used")
        public double springPunchDamage = 0.005;
        @Comment("Spring Punch: Damage bonus per energy used while mastered")
        public double springPunchDamageMastered = 0.01;

        @Comment("Super Jump: Cooldown in seconds")
        public int superJumpCooldown = 3;
        @Comment("Super Jump: Jump height bonus in blocks")
        public double superJumpHeight = 4.0;
        @Comment("Super Jump: Energy used per jump")
        public double superJumpCost = 5.0;

        @Comment("Energy: Cooldown in seconds")
        public int energyCooldown = 0;

        public SpringlikeLimbs() {
        }
    }

    public static class AbsorbAndRelease extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 250.0;

        @Comment("Absorb: Absorbed damage multiplier")
        public double absorbMultiplier = 1.15;
        @Comment("Absorb: Absorbed damage multiplier while mastered")
        public double absorbMultiplierMastered = 2.3;
        @Comment("Absorb: Max stored damage")
        public double maxStored = 2_000.0;
        @Comment("Absorb: Max stored damage while mastered")
        public double maxStoredMastered = 4_000.0;
        @Comment("Absorb: Stored damage only shows once it reaches this share of the max")
        public double visibleThreshold = 0.15;

        @Comment("Release: Cooldown in seconds")
        public int releaseCooldown = 10;
        @Comment("Release: Radius in blocks, damage is split between all targets")
        public double releaseRadius = 12.0;
        @Comment("Release: Radius in blocks while mastered")
        public double releaseRadiusMastered = 24.0;
        @Comment("Release: Released damage taken by the user")
        public double releaseSelfDamage = 0.25;
        @Comment("Release: Released damage taken by the user while mastered")
        public double releaseSelfDamageMastered = 0.125;

        @Comment("Stored: Cooldown in seconds")
        public int storedCooldown = 0;

        public AbsorbAndRelease() {
        }
    }

    public static class Rupture extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 1000.0;

        @Comment("Cooldown in seconds")
        public int activationCooldown = 180;
        @Comment("Rupture: Charge time in seconds")
        public int chargeSeconds = 5;
        @Comment("Rupture: Radius in blocks")
        public double radius = 12.5;
        @Comment("Rupture: Radius in blocks while mastered")
        public double radiusMastered = 25.0;
        @Comment("Rupture: Damage multiplier on current health, split between four directions")
        public double healthMultiplier = 3.0;
        @Comment("Rupture: Damage multiplier on current health while mastered")
        public double healthMultiplierMastered = 4.0;
        @Comment("Rupture: Chance to take no damage from activating it")
        public double surviveChance = 0.25;
        @Comment("Rupture: Chance to take no damage from activating it while mastered")
        public double surviveChanceMastered = 0.5;

        public Rupture() {
        }
    }

    public static class Burst extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 1000.0;

        @Comment("Cooldown in seconds")
        public int activationCooldown = 180;
        @Comment("Burst: Charge time in seconds")
        public int chargeSeconds = 5;
        @Comment("Burst: Radius in blocks")
        public double radius = 15.0;
        @Comment("Burst: Radius in blocks while mastered")
        public double radiusMastered = 25.0;
        @Comment("Burst: Damage multiplier on max health")
        public double healthMultiplier = 3.0;
        @Comment("Burst: Damage multiplier on max health while mastered")
        public double healthMultiplierMastered = 4.0;
        @Comment("Burst: Explosion breaks blocks")
        public boolean breakBlocks = true;
        @Comment("Burst: Chance to take no damage from activating it")
        public double surviveChance = 0.25;
        @Comment("Burst: Chance to take no damage from activating it while mastered")
        public double surviveChanceMastered = 0.5;

        public Burst() {
        }
    }

    public static class Multiplier extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 100.0;

        @Comment("Increase: Max output level")
        public int maxLevel = 10;
        @Comment("Increase: Physical damage multiplier per level")
        public double multiplierPerLevel = 0.05;
        @Comment("Increase: Physical damage multiplier per level while mastered")
        public double multiplierPerLevelMastered = 0.10;

        public Multiplier() {
        }
    }

    public static class Mole extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Tunnel: Aura cost per activation")
        public double auraCost = 200.0;
        @Comment("Tunnel: Cooldown in seconds")
        public int tunnelCooldown = 0;
        @Comment("Tunnel: Movement speed bonus while tunneling")
        public double tunnelSpeedBonus = 0.5;

        @Comment("Ore Affinity: Highlight range in blocks")
        public int oreRange = 32;
        @Comment("Fortune levels added to when mining")
        public int fortuneBonus = 2;

        public Mole() {
        }
    }

    public static class ShockAbsorption extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost to toggle on")
        public double auraCost = 0.0;

        @Comment("Physical damage reduction")
        public double damageReduction = 0.15;
        @Comment("Physical damage reduction while mastered")
        public double damageReductionMastered = 0.30;

        public ShockAbsorption() {
        }
    }

    public static class Telekinesis extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 200.0;
        @Comment("Throw: Cooldown in seconds")
        public int throwCooldown = 3;
        @Comment("Multi-Throw: Cooldown in seconds")
        public int multiThrowCooldown = 8;
        @Comment("Boulder: Cooldown in seconds")
        public int boulderCooldown = 10;
        @Comment("Wall: Cooldown in seconds once the wall comes down")
        public int wallCooldown = 12;

        @Comment("Throw: Radius to pick up blocks from")
        public int pickupRadius = 4;
        @Comment("Throw: Range to grab the block being looked at")
        public double targetRange = 10.0;
        @Comment("Throw: Seconds blocks can be held before they fall")
        public int heldSeconds = 30;
        @Comment("Throw: Target range")
        public double throwRange = 30.0;
        @Comment("Throw: Block speed in blocks per tick")
        public double throwSpeed = 1.6;
        @Comment("Throw: Base damage per block")
        public double baseDamage = 10.0;
        @Comment("Throw: Base damage per block with mastery")
        public double baseDamageMastered = 15.0;
        @Comment("Multi-Throw: Min blocks picked up")
        public int multiThrowMin = 6;
        @Comment("Multi-Throw: Max blocks picked up")
        public int multiThrowMax = 10;
        @Comment("Multi-Throw: Ticks between each block being thrown")
        public int multiThrowInterval = 3;
        @Comment("Boulder: Min blocks gathered")
        public int boulderMin = 4;
        @Comment("Boulder: Max blocks gathered")
        public int boulderMax = 6;
        @Comment("Boulder: Min blocks gathered with mastery")
        public int boulderMinMastered = 8;
        @Comment("Boulder: Max blocks gathered with mastery")
        public int boulderMaxMastered = 12;
        @Comment("Boulder: Speed in blocks per tick")
        public double boulderSpeed = 1.2;
        @Comment("Wall: Blocks gathered into the wall")
        public int wallBlocks = 9;

        public Telekinesis() {
        }
    }

    public static class Scanning extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost per activation")
        public double auraCost = 100.0;
        @Comment("Scan: Cooldown in seconds after turning it off")
        public int scanCooldown = 0;
        @Comment("Lock On: Cooldown in seconds")
        public int lockOnCooldown = 1;

        @Comment("Scan: Range entities are scanned at")
        public double scanRange = 40.0;
        @Comment("Lock On: Range before lock on ends")
        public double lockOnRange = 70.0;
        @Comment("Lock On: Range before lock on ends, with mastery")
        public double lockOnRangeMastered = 105.0;

        public Scanning() {
        }
    }

    public static class Neutralization extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Neutralize: Seconds quirks are shut off")
        public int neutralizeSeconds = 10;
        @Comment("Neutralize: Seconds quirks are shut off with mastery")
        public int neutralizeSecondsMastered = 15;

        public Neutralization() {
        }
    }

    public static class Infrared extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost to turn it on")
        public double auraCost = 100.0;
        @Comment("Infrared: Cooldown in seconds after turning it off")
        public int cooldown = 2;
        @Comment("Infrared: Range heat signatures are seen through walls")
        public double range = 64.0;
        @Comment("Infrared: Range with mastery")
        public double rangeMastered = 80.0;

        public Infrared() {
        }
    }

    public static class Attraction extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Attract: Radius items and experience are pulled from")
        public double attractRadius = 10.0;
        @Comment("Attract: Radius with mastery")
        public double attractRadiusMastered = 20.0;
        @Comment("Attract: Speed in blocks per tick")
        public double attractSpeed = 0.45;

        @Comment("Pull: Aura cost when it starts and every second after")
        public double pullAuraCost = 50.0;
        @Comment("Pull: Cooldown in seconds after letting go")
        public int pullCooldown = 3;
        @Comment("Pull: Range to find the nearest entity")
        public double pullRange = 16.0;
        @Comment("Pull: Strength in blocks per tick")
        public double pullStrength = 0.4;
        @Comment("Pull: Strength with mastery")
        public double pullStrengthMastered = 0.8;
        @Comment("Pull: How strongly the target's running speed affects the pull")
        public double pullResistance = 1.0;

        public Attraction() {
        }
    }

    public static class VibrationDetection extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost to turn it on")
        public double auraCost = 100.0;
        @Comment("Detect: Cooldown in seconds after turning it off")
        public int cooldown = 0;
        @Comment("Detect: Range vibrations are felt from")
        public double range = 128.0;
        @Comment("Detect: Range with mastery")
        public double rangeMastered = 192.0;
        @Comment("Earth Affinity: Earth boost added")
        public double earthAffinityBoost = 2.0;

        public VibrationDetection() {
        }
    }

    public static class Vibrate extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Vibration Redirection: Share of physical damage taken that is shrugged off")
        public double redirectionReduction = 0.15;
        @Comment("Vibration Control: Earth boost added")
        public double earthBoost = 1.0;

        @Comment("Earthquake: Aura cost")
        public double earthquakeAuraCost = 500.0;
        @Comment("Earthquake: Cooldown in seconds")
        public int earthquakeCooldown = 10;
        @Comment("Earthquake: Radius in blocks")
        public double earthquakeRadius = 15.0;
        @Comment("Earthquake: Radius in blocks with mastery")
        public double earthquakeRadiusMastered = 30.0;
        @Comment("Earthquake: Earth damage")
        public double earthquakeDamage = 50.0;
        @Comment("Earthquake: Earth damage with mastery")
        public double earthquakeDamageMastered = 100.0;
        @Comment("Earthquake: Seconds targets are stunned")
        public int earthquakeStunSeconds = 3;

        @Comment("Tremoring Earth: Aura cost")
        public double tremorAuraCost = 3_000.0;
        @Comment("Tremoring Earth: Cooldown in seconds")
        public int tremorCooldown = 20;
        @Comment("Tremoring Earth: Width of the square area in blocks")
        public double tremorSize = 25.0;
        @Comment("Tremoring Earth: Width of the square area with mastery")
        public double tremorSizeMastered = 50.0;
        @Comment("Tremoring Earth: Earth damage")
        public double tremorDamage = 200.0;
        @Comment("Tremoring Earth: Earth damage with mastery")
        public double tremorDamageMastered = 400.0;
        @Comment("Tremoring Earth: Shatters the ground?")
        public boolean tremorBreaksBlocks = true;

        @Comment("Vibrate: Aura cost")
        public double vibrateAuraCost = 400.0;
        @Comment("Vibrate: Cooldown in seconds")
        public int vibrateCooldown = 10;
        @Comment("Vibrate: Reach in blocks")
        public double vibrateRange = 3.0;
        @Comment("Vibrate: Seconds the target vibrates")
        public int vibrateSeconds = 2;
        @Comment("Vibrate: Damage dealt as soon as the hands touch the target")
        public double vibrateDamage = 50.0;
        @Comment("Vibrate: Damage dealt as soon as the hands touch the target with mastery")
        public double vibrateDamageMastered = 100.0;
        @Comment("Vibrate: % of the target's max health dealt over the vibration")
        public double vibrateMaxHealthDamage = 0.15;
        @Comment("Vibrate: % of the target's max health dealt over the vibration with mastery")
        public double vibrateMaxHealthDamageMastered = 0.3;

        public Vibrate() {
        }
    }

    public static class DupliArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Damage each arm deals as a multiple of a normal arm's")
        public double armDamage = 1.5;

        @Comment("Extra Arms: % of an extra arm tool's mining speed added to yours")
        public double miningAssist = 0.5;
        @Comment("Extra Arms: Ticks between each extra arm's follow up hit")
        public int weaponAssistDelay = 3;

        @Comment("Arm Growth: Aura cost")
        public double armGrowthAuraCost = 100.0;
        @Comment("Arm Growth: Cooldown in seconds")
        public int armGrowthCooldown = 1;
        @Comment("Arm Growth: Most arms that can be grown")
        public int maxArms = 2;
        @Comment("Arm Growth: Most arms that can be grown with mastery")
        public int maxArmsMastered = 4;

        @Comment("Arm Block: Aura cost")
        public double armBlockAuraCost = 100.0;
        @Comment("Arm Block: Cooldown in seconds after lowering the arms")
        public int armBlockCooldown = 2;
        @Comment("Arm Block and Backpack Carry: Cooldown in seconds after the arms are destroyed")
        public int armBlockBrokenCooldown = 10;
        @Comment("Arm Block and Backpack Carry: Arm durability as a % of max HP")
        public double armBlockDurability = 0.5;
        @Comment("Arm Block and Backpack Carry: % of durability recovered per second while the arms rest")
        public double armBlockRegen = 0.05;

        @Comment("Octoblow: Aura cost")
        public double octoblowAuraCost = 800.0;
        @Comment("Octoblow: Cooldown in seconds")
        public int octoblowCooldown = 25;
        @Comment("Octoblow: Range in blocks")
        public double octoblowRange = 4.0;
        @Comment("Octoblow: Hits from each arm")
        public int octoblowHits = 1;
        @Comment("Octoblow: Hits from each arm with mastery")
        public int octoblowHitsMastered = 2;

        @Comment("Octospansion: Aura cost")
        public double octospansionAuraCost = 1_500.0;
        @Comment("Octospansion: Cooldown in seconds")
        public int octospansionCooldown = 40;
        @Comment("Octospansion: Range in blocks")
        public double octospansionRange = 5.0;
        @Comment("Octospansion: Arms in the huge arm")
        public int octospansionArms = 8;
        @Comment("Octospansion: Arms in the huge arm with mastery")
        public int octospansionArmsMastered = 16;
        @Comment("Octospansion: Knockback strength")
        public double octospansionKnockback = 3.0;

        @Comment("Backpack Carry: Aura cost")
        public double carryAuraCost = 100.0;
        @Comment("Backpack Carry: Cooldown in seconds after putting the target down")
        public int carryCooldown = 2;
        @Comment("Backpack Carry: Grab range")
        public double carryRange = 3.0;
        @Comment("Backpack Carry: Largest size that can be carried")
        public double carryMaxSize = 3.0;

        public DupliArms() {
        }
    }

    public static class BodyMorph extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Enhanced Body: Size added")
        public double sizeBonus = 0.5;
        @Comment("Enhanced Body: Armor added")
        public double armorBonus = 15.0;
        @Comment("Enhanced Body: Melee damage added")
        public double meleeBonus = 15.0;

        @Comment("Smash: Aura cost")
        public double smashAuraCost = 400.0;
        @Comment("Smash: Cooldown in seconds")
        public int smashCooldown = 10;
        @Comment("Smash: Width of the square area hit")
        public double smashSize = 5.0;
        @Comment("Smash: Width of the square area hit with mastery")
        public double smashSizeMastered = 10.0;
        @Comment("Smash: Damage")
        public double smashDamage = 30.0;
        @Comment("Smash: Damage with mastery")
        public double smashDamageMastered = 60.0;

        @Comment("Grab: Aura cost")
        public double grabAuraCost = 300.0;
        @Comment("Grab: Cooldown in seconds after throwing or letting go")
        public int grabCooldown = 5;
        @Comment("Grab: Grab range")
        public double grabRange = 4.0;
        @Comment("Grab: Farthest a throw can be aimed in blocks")
        public double throwDistance = 50.0;
        @Comment("Grab: Farthest a throw can be aimed in blocks with mastery")
        public double throwDistanceMastered = 100.0;
        @Comment("Grab: Throw speed in blocks per tick, only raised when the aimed area is too far for it")
        public double throwSpeed = 1.2;
        @Comment("Grab: Damage a thrown target takes when it hits a block")
        public double throwImpactDamage = 25.0;
        @Comment("Grab: Damage a thrown target takes when it hits a block, with mastery")
        public double throwImpactDamageMastered = 50.0;
        @Comment("Grab: Blocks in a rock chunk")
        public int rockBlocks = 8;
        @Comment("Grab: Rock damage before the hardness of its blocks is added")
        public double rockDamage = 15.0;
        @Comment("Grab: Rock damage with mastery before the hardness of its blocks is added")
        public double rockDamageMastered = 30.0;

        @Comment("Squeeze: Aura cost")
        public double squeezeAuraCost = 500.0;
        @Comment("Squeeze: Cooldown in seconds")
        public int squeezeCooldown = 15;
        @Comment("Squeeze: Most seconds squeeze can last")
        public int squeezeSeconds = 5;
        @Comment("Squeeze: Damage per second")
        public double squeezeDamage = 25.0;
        @Comment("Squeeze: Damage per second with mastery")
        public double squeezeDamageMastered = 50.0;

        public BodyMorph() {
        }
    }

    public static class ExtraArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Arms in the back of the head: Largest hit countered, as a % of max HP")
        public double counterThreshold = 0.15;
        @Comment("Arms in the back of the head: % of melee damage the counter punch deals")
        public double counterDamage = 1.0 / 3.0;
        @Comment("Arms in the back of the head: Reach of the counter punch")
        public double counterRange = 4.0;
        @Comment("Arms in the back of the head: Ticks between counters")
        public int counterCooldown = 10;

        @Comment("Wall Climb: Aura cost to start climbing")
        public double wallClimbAuraCost = 50.0;

        public ExtraArms() {
        }
    }

    public static class Tail extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Tail Catch: Fall damage reduction")
        public double fallReduction = 0.25;
        @Comment("Tail HP as a % of max HP")
        public double tailHealth = 0.25;
        @Comment("Seconds every tail move is disabled after the tail is broken")
        public int tailBrokenSeconds = 30;

        @Comment("Tail Whip: Aura cost")
        public double whipAuraCost = 200.0;
        @Comment("Tail Whip: Cooldown in seconds")
        public int whipCooldown = 4;
        @Comment("Tail Whip: Range")
        public double whipRange = 3.5;
        @Comment("Tail Whip: Damage added to melee damage")
        public double whipDamage = 15.0;
        @Comment("Tail Whip: Damage added to melee damage with mastery")
        public double whipDamageMastered = 30.0;
        @Comment("Tail Whip: Sideways knockback speed")
        public double whipKnockback = 1.4;
        @Comment("Tail Whip: Extra damage when the target hits a wall")
        public double wallDamage = 25.0;

        @Comment("Spiral Hit: Aura cost")
        public double spiralAuraCost = 300.0;
        @Comment("Spiral Hit: Cooldown in seconds")
        public int spiralCooldown = 8;
        @Comment("Spiral Hit: Radius")
        public double spiralRadius = 2.0;
        @Comment("Spiral Hit: Radius with mastery")
        public double spiralRadiusMastered = 4.0;
        @Comment("Spiral Hit: Damage added to melee damage")
        public double spiralDamage = 30.0;
        @Comment("Spiral Hit: Damage added to melee damage with mastery")
        public double spiralDamageMastered = 60.0;

        @Comment("Tail Leap: Aura cost")
        public double leapAuraCost = 200.0;
        @Comment("Tail Leap: Cooldown in seconds")
        public int leapCooldown = 6;
        @Comment("Tail Leap: Distance in blocks")
        public double leapDistance = 15.0;
        @Comment("Tail Leap: Distance in blocks with mastery")
        public double leapDistanceMastered = 30.0;
        @Comment("Tail Leap: Damage to anything collided with")
        public double leapDamage = 15.0;
        @Comment("Tail Leap: Damage to anything collided with, with mastery")
        public double leapDamageMastered = 30.0;
        @Comment("Tail Leap: Knockback in blocks")
        public double leapKnockback = 4.0;
        @Comment("Tail Leap: Knockback in blocks with mastery")
        public double leapKnockbackMastered = 8.0;

        @Comment("Wrap: Aura cost")
        public double wrapAuraCost = 300.0;
        @Comment("Wrap: Cooldown in seconds after letting go")
        public int wrapCooldown = 5;
        @Comment("Wrap: Range")
        public double wrapRange = 3.0;

        public Tail() {
        }
    }

}

package com.radient.tensuraacadamia.config.skills;

import io.github.manasmods.manascore.config.api.Comment;
import io.github.manasmods.manascore.config.api.ManasConfig;
import io.github.manasmods.manascore.config.api.ManasSubConfig;

import java.util.List;

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
    public LifeForce LifeForce = new LifeForce();
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
    public BodyBulk BodyBulk = new BodyBulk();
    public Bruiser Bruiser = new Bruiser();
    public Endurance Endurance = new Endurance();
    public Jet Jet = new Jet();
    public Gigantification Gigantification = new Gigantification();
    public TransformingArms TransformingArms = new TransformingArms();
    public Landmine Landmine = new Landmine();
    public Grabbing Grabbing = new Grabbing();
    public Weld Weld = new Weld();
    public Whale Whale = new Whale();
    public WholeBodyLens WholeBodyLens = new WholeBodyLens();
    public Wing Wing = new Wing();
    public ZombieVirus ZombieVirus = new ZombieVirus();
    public WaterPump WaterPump = new WaterPump();
    public WaterGun WaterGun = new WaterGun();
    public WaterControl WaterControl = new WaterControl();
    public Voyance Voyance = new Voyance();
    public ViralCosmos ViralCosmos = new ViralCosmos();
    public ToolArms ToolArms = new ToolArms();
    public TongueTank TongueTank = new TongueTank();
    public PainBlocker PainBlocker = new PainBlocker();
    public FierceGains FierceGains = new FierceGains();
    public MuscleExpansion MuscleExpansion = new MuscleExpansion();
    public TwinImpact TwinImpact = new TwinImpact();
    public Traject Traject = new Traject();
    public TreeFrog TreeFrog = new TreeFrog();
    public Tripod Tripod = new Tripod();
    public Warping Warping = new Warping();
    public NewOrder NewOrder = new NewOrder();
    public Blast Blast = new Blast();

    public static class Blast extends ManasSubConfig {
        @Comment("Maximum block hardness Big Ass Blast can destroy. Negative-hardness blocks are always protected.")
        public double maxBlockHardness = 3;
    }



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
        public double armorPerSize = 25.0;
        @Comment("Muscle Mass: Knockback resistance per size gained")
        public double knockbackResistancePerSize = 0.1;
        @Comment("Muscle Mass: Max health bonus per size")
        public double healthPerSize = 1.0;
        @Comment("Muscle Mass: Reach per size")
        public double reachPerSize = 2.0;

        @Comment("Muscle Enhancement: Cooldown in seconds")
        public int enhancementCooldown = 0;
        @Comment("Muscle Enhancement: Size gained per tick")
        public double growthPerTick = 0.02;
        @Comment("Muscle Enhancement: Max size")
        public double maxSize = 1.5;
        @Comment("Muscle Enhancement: Max size with mastery")
        public double maxSizeMastered = 2.0;
        @Comment("Muscle Enhancement: Bonus melee damage per size gained")
        public double damagePerSize = 200.0;

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
        public double torsoMaxWidth = 2.4;
        @Comment("Fat Stock: Torso depth at max size")
        public double torsoMaxDepth = 4.0;

        @Comment("Restrain: Cooldown in seconds")
        public int restrainCooldown = 5;
        @Comment("Restrain: Fat needed")
        public double restrainFat = 100.0;
        @Comment("Restrain: Range")
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
        @Comment("Bolt Charge: Range")
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

        @Comment("Summon Tornado: Cooldown in seconds")
        public int tornadoCooldown = 30;
        @Comment("Summon Tornado: Tornadoes summoned")
        public int tornadoCount = 1;
        @Comment("Summon Tornado: Tornadoes summoned with mastery")
        public int tornadoCountMastered = 2;
        @Comment("Summon Tornado: Seconds the tornadoes last")
        public int tornadoSeconds = 10;
        @Comment("Summon Tornado: Wind damage dealt every second to anything caught in a tornado")
        public double tornadoDamage = 225.0;
        @Comment("Summon Tornado: Wind damage dealt every second with mastery")
        public double tornadoDamageMastered = 450.0;
        @Comment("Summon Tornado: Radius a tornado sucks entities in from")
        public double tornadoPullRadius = 12.0;
        @Comment("Summon Tornado: Radius of a tornado's damaging core")
        public double tornadoCoreRadius = 3.0;
        @Comment("Summon Tornado: Pull strength")
        public double tornadoPull = 0.12;
        @Comment("Summon Tornado: Speed the tornadoes drift forward per tick")
        public double tornadoSpeed = 0.15;
        @Comment("Summon Tornado: Blocks in front of the user the tornadoes form")
        public double tornadoDistance = 8.0;
        @Comment("Summon Tornado: Blocks between tornadoes when more than one is summoned")
        public double tornadoSpacing = 10.0;
        @Comment("Summon Tornado: Tornado size")
        public double tornadoSize = 0.6;

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
        @Comment("Bullet Punches: Lunge distance")
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
        @Comment("Super Jump: Jump height bonus")
        public double superJumpHeight = 4.0;
        @Comment("Super Jump: Energy used per jump")
        public double superJumpCost = 5.0;

        @Comment("Energy: Cooldown in seconds")
        public int energyCooldown = 0;

        @Comment("Mastery: Energy each body part builds up every second while toggled on")
        public double energyPerSecondMastered = 2.0;

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
        @Comment("Absorb: Stored damage only shows once it reaches this percentage of the max")
        public double visibleThreshold = 0.15;

        @Comment("Release: Cooldown in seconds")
        public int releaseCooldown = 10;
        @Comment("Release: Radius, damage is split between all targets")
        public double releaseRadius = 12.0;
        @Comment("Release: Radius while mastered")
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
        @Comment("Rupture: Radius ")
        public double radius = 12.5;
        @Comment("Rupture: Radius  while mastered")
        public double radiusMastered = 25.0;
        @Comment("Rupture: Damage multiplier on current health, split between four directions")
        public double healthMultiplier = 3.0;
        @Comment("Rupture: Damage multiplier on current health while mastered")
        public double healthMultiplierMastered = 4.0;
        @Comment("Rupture: Chance to take no damage from activating it")
        public double surviveChance = 0.25;
        @Comment("Rupture: Chance to take no damage from activating it while mastered")
        public double surviveChanceMastered = 0.5;

        @Comment("Arm Rupture: Aura cost")
        public double armAuraCost = 300.0;
        @Comment("Arm Rupture: Cooldown in seconds")
        public int armCooldown = 5;
        @Comment("Arm Rupture: % of max health it takes to use")
        public double armHealthCost = 1.0 / 7.0;
        @Comment("Arm Rupture: Damage multiplier based on max health")
        public double armHealthMultiplier = 1.5;
        @Comment("Arm Rupture: Damage multiplier based on max health while mastered")
        public double armHealthMultiplierMastered = 2.0;
        @Comment("Arm Rupture: Radius the rupture hits")
        public double armRadius = 10.0;
        @Comment("Arm Rupture: Speed the arm moves at")
        public double armSpeed = 40.0;
        @Comment("Arm Rupture: Seconds before it ruptures mid-air")
        public double armSeconds = 3.0;
        @Comment("Arm Rupture: Seconds for a ruptured arm to grow back")
        public double armRegrowSeconds = 200.0;
        @Comment("Arm Rupture: Seconds with Self Regeneration")
        public double armRegrowSecondsSelfRegeneration = 100.0;
        @Comment("Arm Rupture: Seconds with Ultraspeed Regeneration")
        public double armRegrowSecondsUltraspeedRegeneration = 33.0;
        @Comment("Arm Rupture: Seconds with Infinite Regeneration")
        public double armRegrowSecondsInfiniteRegeneration = 8.0;

        public Rupture() {
        }
    }

    public static class Burst extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Body Burst: Aura cost")
        public double auraCost = 1000.0;

        @Comment("Body Burst: Cooldown in seconds")
        public int activationCooldown = 180;
        @Comment("Body Burst: Radius ")
        public double radius = 15.0;
        @Comment("Body Burst: Radius while mastered")
        public double radiusMastered = 25.0;
        @Comment("Body Burst: Damage multiplier based on max health")
        public double healthMultiplier = 3.0;
        @Comment("Body Burst: Damage multiplier based on max health while mastered")
        public double healthMultiplierMastered = 4.0;
        @Comment("Burst: Explosions break blocks")
        public boolean breakBlocks = true;
        @Comment("Body Burst: Chance to take no damage from activating it")
        public double surviveChance = 0.25;
        @Comment("Body Burst: Chance to take no damage from activating it while mastered")
        public double surviveChanceMastered = 0.5;

        @Comment("Regenerating Body: Seconds for a limb lost to Burst to grow back")
        public double regrowSeconds = 200.0;
        @Comment("Regenerating Body: Seconds with Self Regeneration")
        public double regrowSecondsSelfRegeneration = 100.0;
        @Comment("Regenerating Body: Seconds with Ultraspeed Regeneration")
        public double regrowSecondsUltraspeedRegeneration = 33.0;
        @Comment("Regenerating Body: Seconds with Infinite")
        public double regrowSecondsInfiniteRegeneration = 8.0;

        @Comment("Suicide Burst: Aura cost")
        public double suicideAuraCost = 500.0;
        @Comment("Suicide Burst: Cooldown in seconds")
        public int suicideCooldown = 30;
        @Comment("Suicide Burst: % of max health the burst takes")
        public double suicideHealthCost = 0.0;
        @Comment("Suicide Burst: Launch speed")
        public double suicideSpeed = 75.0;
        @Comment("Suicide Burst: Damage multiplier based on max health for each limb")
        public double suicideHealthMultiplier = 1.5;
        @Comment("Suicide Burst: Damage multiplier based on max health for each limb while mastered")
        public double suicideHealthMultiplierMastered = 2.0;

        public Burst() {
        }
    }

    public static class LifeForce extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Health Multiplier")
        public int HPBonus = 3;
        @Comment("Spiritual Health Multiplier")
        public int SHPBonus = 3;
        @Comment("Regen Multiplier")
        public int regenBonus = 3;
        @Comment("Slowness Level")
        public int slownessLevel = 2;

        public LifeForce() {
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

        @Comment("Ore Affinity: Highlight range ")
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

        @Comment("Rebound Attack: % of the last absorbed attack's damage added to physical attack damage")
        public double reboundShare = 0.25;
        @Comment("Rebound Attack: % added while mastered")
        public double reboundShareMastered = 0.5;

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
        @Comment("Throw: Max block speed per tick")
        public double throwSpeed = 1.6;
        @Comment("Throw: Base damage per block")
        public double baseDamage = 10.0;
        @Comment("Throw: Base damage per block with mastery")
        public double baseDamageMastered = 15.0;
        @Comment("Multi-Throw: Max blocks picked up")
        public int multiThrowMin = 6;
        @Comment("Multi-Throw: Max blocks picked up")
        public int multiThrowMax = 10;
        @Comment("Multi-Throw: Ticks between each block being thrown")
        public int multiThrowInterval = 3;
        @Comment("Boulder: Max blocks gathered")
        public int boulderMin = 4;
        @Comment("Boulder: Max blocks gathered")
        public int boulderMax = 6;
        @Comment("Boulder: Max blocks gathered with mastery")
        public int boulderMinMastered = 8;
        @Comment("Boulder: Max blocks gathered with mastery")
        public int boulderMaxMastered = 12;
        @Comment("Boulder: Speed  per tick")
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
        @Comment("Scan: Cooldown in seconds")
        public int scanCooldown = 0;
        @Comment("Lock On: Cooldown in seconds")
        public int lockOnCooldown = 2;

        @Comment("Scan: Range entities are scanned at")
        public double scanRange = 40.0;
        @Comment("Lock On: Range before lock on ends")
        public double lockOnRange = 70.0;
        @Comment("Lock On: Range before lock on ends, with mastery")
        public double lockOnRangeMastered = 105.0;

        @Comment("On Target: Critical attack chance added while locked on")
        public double onTargetCritChance = 100.0;

        @Comment("Weakness Detection: Aura cost")
        public double weaknessAuraCost = 200.0;
        @Comment("Weakness Detection: Cooldown in seconds")
        public int weaknessCooldown = 15;
        @Comment("Weakness Detection: Seconds the weakness lasts")
        public int weaknessSeconds = 30;
        @Comment("Weakness Detection: Extra damage taken from the chosen type")
        public double weaknessDamage = 0.15;
        @Comment("Weakness Detection: Extra damage taken from the chosen type with mastery")
        public double weaknessDamageMastered = 0.3;

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
        @Comment("Infrared: Cooldown in seconds")
        public int cooldown = 0;
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
        @Comment("Attract: Speed  per tick")
        public double attractSpeed = 0.45;

        @Comment("Pull: Aura cost when it starts and every second after")
        public double pullAuraCost = 50.0;
        @Comment("Pull: Cooldown in seconds")
        public int pullCooldown = 3;
        @Comment("Pull: Range to find the nearest entity")
        public double pullRange = 16.0;
        @Comment("Pull: Strength  per tick")
        public double pullStrength = 0.4;
        @Comment("Pull: Strength with mastery")
        public double pullStrengthMastered = 0.8;
        @Comment("Pull: How strongly the target's running speed affects the pull")
        public double pullResistance = 1.0;

        @Comment("Object Crash: Aura cost per second")
        public double crashAuraCost = 300.0;
        @Comment("Object Crash: Cooldown in seconds")
        public int crashCooldown = 15;
        @Comment("Object Crash: Size the area grows each second")
        public double crashGrowth = 1.0;
        @Comment("Object Crash: Size the area grows each second with mastery")
        public double crashGrowthMastered = 3.0;
        @Comment("Object Crash: Max size of the area")
        public double crashMaxSize = 25.0;
        @Comment("Object Crash: Max size of the area with mastery")
        public double crashMaxSizeMastered = 50.0;
        @Comment("Object Crash: Blocks pulled in each second")
        public int crashBlocksPerSecond = 12;
        @Comment("Object Crash: Most blocks that can be held")
        public int crashMaxBlocks = 48;
        @Comment("Object Crash: Most blocks that can be held with mastery")
        public int crashMaxBlocksMastered = 96;
        @Comment("Object Crash: Damage per hardness of each block x speed in blocks per second")
        public double crashHardnessMultiplier = 2.0;
        @Comment("Object Crash: Damage per hardness of each block with mastery x speed in blocks per second")
        public double crashHardnessMultiplierMastered = 3.0;
        @Comment("Object Crash: Speed the blocks are shot out at")
        public double crashShotSpeed = 30.0;
        @Comment("Object Crash: Seconds the full size has to be held before collapsing into a black hole")
        public int collapseChargeSeconds = 5;
        @Comment("Object Crash: Width of the area the collapsed black hole absorbs")
        public double collapseSize = 75.0;
        @Comment("Object Crash: Gravity damage of the collapsed black hole's explosion")
        public double collapseDamage = 2_500.0;
        @Comment("Object Crash: Seconds the collapsed black hole absorbs blocks before exploding")
        public int collapseSeconds = 6;

        @Comment("Debris Orbit: Aura cost")
        public double orbitAuraCost = 200.0;
        @Comment("Debris Orbit: Cooldown in seconds")
        public int orbitCooldown = 20;
        @Comment("Debris Orbit: Blocks pulled in")
        public int orbitBlocks = 3;
        @Comment("Debris Orbit: Blocks pulled in with mastery")
        public int orbitBlocksMastered = 6;
        @Comment("Debris Orbit: Range blocks are pulled from")
        public double orbitRange = 6.0;
        @Comment("Debris Orbit: Seconds the blocks orbit")
        public int orbitSeconds = 35;
        @Comment("Debris Orbit: Hardness a block needs to be able to take more than one hit")
        public double orbitHardHardness = 50.0;
        @Comment("Debris Orbit: Hits a hard block can take")
        public int orbitHardHits = 3;

        @Comment("Miniature Black Hole: Aura cost")
        public double blackHoleAuraCost = 3_000.0;
        @Comment("Miniature Black Hole: Cooldown in seconds")
        public int blackHoleCooldown = 30;
        @Comment("Miniature Black Hole: Range before it expands")
        public double blackHoleRange = 48.0;
        @Comment("Miniature Black Hole: Speed")
        public double blackHoleSpeed = 30.0;
        @Comment("Miniature Black Hole: Width of the area it absorbs")
        public double blackHoleSize = 20.0;
        @Comment("Miniature Black Hole: Gravity damage of the explosion")
        public double blackHoleDamage = 500.0;
        @Comment("Miniature Black Hole: Seconds it absorbs blocks before exploding")
        public int blackHoleSeconds = 2;

        public Attraction() {
        }
    }

    public static class VibrationDetection extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Aura cost to turn it on")
        public double auraCost = 100.0;
        @Comment("Detect: Cooldown in seconds")
        public int cooldown = 0;
        @Comment("Detect: Range vibrations are felt from")
        public double range = 128.0;
        @Comment("Detect: Range with mastery")
        public double rangeMastered = 192.0;
        @Comment("Earth Affinity: Earth boost added")
        public double earthAffinityBoost = 2.0;

        @Comment("Vibration Check: Aura cost, needs Vibrate")
        public double checkAuraCost = 300.0;
        @Comment("Vibration Check: Cooldown in seconds")
        public int checkCooldown = 10;
        @Comment("Vibration Check: Width of the square area ")
        public double checkSize = 25.0;
        @Comment("Vibration Check: Width of the square area  with mastery")
        public double checkSizeMastered = 50.0;
        @Comment("Vibration Check: Seconds everything caught stays marked")
        public int markSeconds = 10;

        public VibrationDetection() {
        }
    }

    public static class Vibrate extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Vibration Redirection: Percentage of physical damage taken that is shrugged off")
        public double redirectionReduction = 0.15;
        @Comment("Vibration Control: Earth boost added")
        public double earthBoost = 1.0;

        @Comment("Earthquake: Aura cost")
        public double earthquakeAuraCost = 500.0;
        @Comment("Earthquake: Cooldown in seconds")
        public int earthquakeCooldown = 10;
        @Comment("Earthquake: Radius ")
        public double earthquakeRadius = 15.0;
        @Comment("Earthquake: Radius  with mastery")
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
        @Comment("Tremoring Earth: Width of the square area ")
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
        @Comment("Vibrate: Reach ")
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

        @Comment("Targeted Tremor: Aura cost, needs Vibration Detection")
        public double targetedTremorAuraCost = 2_000.0;
        @Comment("Targeted Tremor: Cooldown in seconds")
        public int targetedTremorCooldown = 15;
        @Comment("Targeted Tremor: Earth damage")
        public double targetedTremorDamage = 400.0;
        @Comment("Targeted Tremor: Earth damage with mastery")
        public double targetedTremorDamageMastered = 800.0;
        @Comment("Targeted Tremor: Speed the tremor travels  per tick")
        public double targetedTremorSpeed = 1.5;

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
        @Comment("Arm Block: Cooldown in seconds")
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
        @Comment("Octoblow: Range ")
        public double octoblowRange = 4.0;
        @Comment("Octoblow: Hits from each arm")
        public int octoblowHits = 1;
        @Comment("Octoblow: Hits from each arm with mastery")
        public int octoblowHitsMastered = 2;

        @Comment("Octospansion: Aura cost")
        public double octospansionAuraCost = 1_500.0;
        @Comment("Octospansion: Cooldown in seconds")
        public int octospansionCooldown = 40;
        @Comment("Octospansion: Range ")
        public double octospansionRange = 5.0;
        @Comment("Octospansion: Arms in the huge arm")
        public int octospansionArms = 8;
        @Comment("Octospansion: Arms in the huge arm with mastery")
        public int octospansionArmsMastered = 16;
        @Comment("Octospansion: Knockback strength")
        public double octospansionKnockback = 3.0;

        @Comment("Backpack Carry: Aura cost")
        public double carryAuraCost = 100.0;
        @Comment("Backpack Carry: Cooldown in seconds")
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
        @Comment("Enhanced Body: Reach per size")
        public double reachPerSize = 1.0;

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
        @Comment("Smash: % of physical damage dealt")
        public double smashDamagePercent = 0.25;

        @Comment("Grab: Aura cost")
        public double grabAuraCost = 300.0;
        @Comment("Grab: Cooldown in seconds")
        public int grabCooldown = 5;
        @Comment("Grab: Grab range")
        public double grabRange = 4.0;
        @Comment("Grab: Farthest a throw can be aimed ")
        public double throwDistance = 50.0;
        @Comment("Grab: Farthest a throw can be aimed  with mastery")
        public double throwDistanceMastered = 100.0;
        @Comment("Grab: Throw speed  per tick, only raised when the aimed area is too far for it")
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
        @Comment("Squeeze: % of physical damage dealt")
        public double squeezeDamagePercent = 0.15;

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
        @Comment("Tail Leap: Distance")
        public double leapDistance = 15.0;
        @Comment("Tail Leap: Distance with mastery")
        public double leapDistanceMastered = 30.0;
        @Comment("Tail Leap: Damage to anything collided with")
        public double leapDamage = 15.0;
        @Comment("Tail Leap: Damage to anything collided with, with mastery")
        public double leapDamageMastered = 30.0;
        @Comment("Tail Leap: Knockback ")
        public double leapKnockback = 4.0;
        @Comment("Tail Leap: Knockback  with mastery")
        public double leapKnockbackMastered = 8.0;

        @Comment("Wrap: Aura cost")
        public double wrapAuraCost = 300.0;
        @Comment("Wrap: Cooldown in second")
        public int wrapCooldown = 5;
        @Comment("Wrap: Range")
        public double wrapRange = 3.0;

        public Tail() {
        }
    }

    public static class ZeroGravity extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Zero Gravity: Aura cost")
        public double floatAuraCost = 150.0;
        @Comment("Zero Gravity: Cooldown in seconds")
        public int floatCooldown = 1;
        @Comment("Zero Gravity: Most targets/blocks floating at once")
        public int maxFloats = 3;
        @Comment("Zero Gravity: Most targets/blocks floating at once with mastery")
        public int maxFloatsMastered = 6;
        @Comment("Zero Gravity: Seconds until anything floating is released")
        public int floatSeconds = 45;
        @Comment("Zero Gravity: Speed per tick a floating block gains per point of damage it takes")
        public double punchSpeed = 0.05;
        @Comment("Zero Gravity: Fastest a hit floating block can move  per tick")
        public double maxPunchSpeed = 4.0;
        @Comment("Zero Gravity: % of a floating block's speed kept each tick")
        public double blockDrag = 0.95;
        @Comment("Zero Gravity: Rising speed per tick")
        public double raiseSpeed = 0.2;
        @Comment("Zero Gravity: Speed a floating player gains each tick moving in a direction %")
        public double floatAcceleration = 0.03;
        @Comment("Zero Gravity: % of a floating player's speed lost to drag each tick")
        public double floatDragPercent = 0.025;
        @Comment("Zero Gravity: Most speed per tick drag can take away")
        public double floatDragLimit = 0.05;
        @Comment("Zero Gravity: Speed a floating player gains each tick while using wind manipulation or domination")
        public double windThrust = 0.1;
        @Comment("Zero Gravity: Fastest wind manipulation or domination can push a floating player per tick")
        public double windMaxSpeed = 1.5;
        @Comment("Zero Gravity: Fastest a floating player can move per tick")
        public double maxPlayerSpeed = 3.0;

        @Comment("Gravity Boost: Gravity boost added")
        public double gravityBoost = 1.0;

        @Comment("Self Gravity: Aura cost")
        public double selfAuraCost = 500.0;
        @Comment("Self Gravity: Cooldown in seconds")
        public int selfCooldown = 0;

        @Comment("Meteor Shower: Aura cost")
        public double showerAuraCost = 1_000.0;
        @Comment("Meteor Shower: Cooldown in seconds")
        public int showerCooldown = 30;
        @Comment("Meteor Shower: Blocks affected")
        public int showerBlocks = 10;
        @Comment("Meteor Shower: Blocks affected with mastery")
        public int showerBlocksMastered = 20;
        @Comment("Meteor Shower: Radius the blocks are taken from")
        public double showerSize = 15.0;
        @Comment("Meteor Shower: Area blocks are taken from with mastery")
        public double showerSizeMastered = 30.0;
        @Comment("Meteor Shower: Height the blocks form above the user")
        public double showerHeight = 200.0;
        @Comment("Meteor Shower: Speed the blocks ascend per second")
        public double showerSpeed = 20.0;
        @Comment("Meteor Shower: Speed the blocks ascend per second with mastery")
        public double showerSpeedMastered = 40.0;
        @Comment("Meteor Shower: Seconds before the blocks automatically falls")
        public int showerHoldSeconds = 15;
        @Comment("Meteor Shower: Damage multiplier on block hardness")
        public double showerHardness = 2.0;
        @Comment("Meteor Shower: Damage multiplier on block hardness with mastery")
        public double showerHardnessMastered = 3.0;
        @Comment("Meteor Shower: Multiplier on the damage")
        public double showerDamageScale = 1.25;

        @Comment("Devastation: Aura cost")
        public double devastationAuraCost = 5_000.0;
        @Comment("Devastation: Cooldown in seconds")
        public int devastationCooldown = 120;
        @Comment("Devastation: Blocks gathered into the meteor")
        public int devastationBlocks = 100;
        @Comment("Devastation: Blocks gathered into the meteor with mastery")
        public int devastationBlocksMastered = 200;
        @Comment("Devastation: radius the blocks are gathered from with mastery")
        public double devastationSize = 20.0;
        @Comment("Devastation: radius the blocks are gathered from with mastery")
        public double devastationSizeMastered = 40.0;
        @Comment("Devastation: Height the meteor forms above the user")
        public double devastationHeight = 200.0;
        @Comment("Devastation: Seconds before the meteor automatically falls")
        public int devastationHoldSeconds = 15;
        @Comment("Devastation: Damage multiplier on block hardness")
        public double devastationHardness = 3.0;
        @Comment("Devastation: Damage multiplier")
        public double devastationDamageScale = 1.25;
        @Comment("Devastation: Crater radius as a multiple of the meteor's radius")
        public double devastationCraterScale = 1.5;
        @Comment("Devastation: Radius of shockwave")
        public double devastationShockwaveRadius = 25.0;
        @Comment("Devastation: % of the meteor's damage the shockwave deals")
        public double devastationShockwaveDamage = 0.25;
        @Comment("Devastation: Wether the meteor breaks blocks")
        public boolean devastationBreaksBlocks = true;

        public ZeroGravity() {
        }
    }

    public static class BodyBulk extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Body Bulk: Aura cost per 1 size grown, taken a little each tick while growing")
        public double auraCostPerSize = 1200.0;
        @Comment("Body Bulk: Cooldown in seconds")
        public int cooldown = 0;
        @Comment("Body Bulk: Size gained each tick")
        public double growthPerTick = 0.02;
        @Comment("Body Bulk: Max size the user can grow")
        public double maxSize = 1.5;
        @Comment("Body Bulk: Max size the user can gain grow with mastery")
        public double maxSizeMastered = 2.5;
        @Comment("Body Bulk: Damage per size gained")
        public double damagePerSize = 100.0;
        @Comment("Body Bulk: Armor per size gained")
        public double armorPerSize = 15.0;
        @Comment("Body Bulk: % of max health added per size gained")
        public double healthPercentPerSize = 0.1;
        @Comment("Body Bulk: Reach per size")
        public double reachPerSize = 1.0;

        @Comment("Stress Overload: % of max health the user has to be at or under")
        public double stressHealth = 0.5;
        @Comment("Stress Overload: Most Body Bulk damage multiplier")
        public double stressMaxMultiplier = 2.0;
        @Comment("Stress Overload: Seconds for the Body Bulk damage to double")
        public double stressDoubleSeconds = 25.0;

        @Comment("Slam: Aura cost")
        public double slamAuraCost = 300.0;
        @Comment("Slam: Cooldown in seconds")
        public int slamCooldown = 8;
        @Comment("Slam: Radius of the slam, grows with size")
        public double slamRadius = 2.0;
        @Comment("Slam: Radius of the shockwave, grows with size")
        public double slamShockwaveRadius = 6.0;
        @Comment("Slam: Melee damage multiplier for targets hit by the slam")
        public double slamDamage = 1.25;
        @Comment("Slam: Melee damage multiplier for targets hit by the shockwave")
        public double slamShockwaveDamage = 0.75;

        public BodyBulk() {
        }
    }

    public static class Bruiser extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Power Up: Aura cost")
        public double powerUpAuraCost = 200.0;
        @Comment("Power Up: Size of a powered up body part")
        public double partScale = 1.5;

        @Comment("Power Up: Right arm damage added")
        public double rightArmDamage = 750.0;
        @Comment("Power Up: Right arm damage added with mastery")
        public double rightArmDamageMastered = 150.0;


        @Comment("Power Up: Right leg knockback resistance added")
        public double rightLegKnockbackResistance = 0.5;
        @Comment("Power Up: Right leg knockback resistance added with mastery")
        public double rightLegKnockbackResistanceMastered = 1.0;

        @Comment("Power Up: Left leg movement speed bonus %")
        public double leftLegSpeed = 0.2;
        @Comment("Power Up: Left leg movement speed bonus % with mastery")
        public double leftLegSpeedMastered = 0.4;
        @Comment("Power Up: Left leg jump boost added")
        public double leftLegJump = 0.2;

        @Comment("Power Move: Aura cost")
        public double powerMoveAuraCost = 800.0;

        @Comment("Right Arm: Cooldown in seconds")
        public int punchCooldown = 3;
        @Comment("Right Arm: Reach")
        public double punchRange = 4.0;
        @Comment("Right Arm: Damage")
        public double punchDamage = 50.0;
        @Comment("Right Arm: Damage with mastery")
        public double punchDamageMastered = 100.0;
        @Comment("Right Arm: % of physical damage added")
        public double punchDamagePercent = 1.0;
        @Comment("Right Arm: % of physical damage added with mastery")
        public double punchDamagePercentMastered = 1.5;
        @Comment("Right Arm: Radius of the shockwave")
        public double punchShockwaveRadius = 12.0;
        @Comment("Right Arm: % of the damage the shockwave deals")
        public double punchShockwaveDamage = 0.5;

        @Comment("Left Arm: Arm durability as a % of max HP")
        public double blockDurability = 1.0 / 3.0;
        @Comment("Left Arm: % of durability recovered per second while resting")
        public double blockRegen = 0.05;
        @Comment("Left Arm: Cooldown in seconds after the arm breaks")
        public int blockBrokenCooldown = 10;

        @Comment("Left Arm: Cooldown in seconds")
        public int swipeCooldown = 3;
        @Comment("Left Arm: Reach of the swipe")
        public double swipeRange = 4.0;
        @Comment("Left Arm: Radius the shockwave pushes mobs away")
        public double swipeShockwaveRadius = 10.0;
        @Comment("Left Arm: Damage")
        public double swipeDamage = 75.0;
        @Comment("Left Arm: Damage with mastery")
        public double swipeDamageMastered = 150.0;
        @Comment("Left Arm: % of physical damage added")
        public double swipeDamagePercent = 1.0;
        @Comment("Left Arm: % of physical damage added with mastery")
        public double swipeDamagePercentMastered = 1.5;
        @Comment("Left Arm: Knockback strength")
        public double swipeKnockback = 2.0;

        @Comment("Right Leg Stomp: Cooldown in seconds")
        public int stompCooldown = 5;
        @Comment("Right Leg: Width of the stomp9")
        public double stompSize = 5.0;
        @Comment("Right Leg: Width of the shockwave")
        public double stompShockwaveSize = 20.0;
        @Comment("Right Leg: Damage")
        public double stompDamage = 50.0;
        @Comment("Right Leg: Damage with mastery")
        public double stompDamageMastered = 100.0;
        @Comment("Right Leg: % of physical damage added")
        public double stompDamagePercent = 1.0;
        @Comment("Right Leg: % of physical damage added with mastery")
        public double stompDamagePercentMastered = 1.5;
        @Comment("Right Leg: % of the stomp's damage the shockwave deals")
        public double stompShockwaveDamage = 0.5;

        @Comment("Left Leg: Cooldown in seconds")
        public int leapCooldown = 10;
        @Comment("Left Leg: Launch speed per tick")
        public double leapSpeed = 5.0;
        @Comment("Left Leg: Launch speed per tick with mastery")
        public double leapSpeedMastered = 8.0;
        @Comment("Left Leg: Up tilt added to leap")
        public double leapLift = 0.1;
        @Comment("Left Leg: Radius of the shockwave, grows with the user's size")
        public double leapShockwaveRadius = 4.0;
        @Comment("Left Leg: Landing shockwave damage, dealt as this * size / 2")
        public double leapShockwaveDamage = 100.0;

        @Comment("Shoulder Check: Aura cost")
        public double shoulderCheckAuraCost = 400.0;
        @Comment("Shoulder Check: Cooldown in seconds")
        public int shoulderCheckCooldown = 6;
        @Comment("Shoulder Check: % of melee damage dealt to everything you go through")
        public double shoulderCheckDamagePercent = 1.15;
        @Comment("Shoulder Check: Blocks travelled")
        public double shoulderCheckDistance = 12.0;
        @Comment("Shoulder Check: Speed")
        public double shoulderCheckSpeed = 32.0;

        public Bruiser() {
        }
    }

    public static class Endurance extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Enhance: Max size")
        public double maxSize = 3.0;
        @Comment("Enhance: Max size with mastery")
        public double maxSizeMastered = 4.5;
        @Comment("Enhance: Damage gained per size")
        public double damagePerSize = 5.5;
        @Comment("Enhance: Armor gained per size")
        public double armorPerSize = 40.0;
        @Comment("Enhance: % of movement speed lost per size gained")
        public double speedLostPerSize = 0.2;
        @Comment("Enhance: Reach per size")
        public double reachPerSize = 2.0;
        @Comment("Enhance: Size gained each tick")
        public double growthPerTick = 0.02;
        @Comment("Enhance: Cooldown in seconds")
        public int cooldown = 0;

        @Comment("Toughen Up: % of damage taken away for each 1% of health missing")
        public double toughenDamageReduction = 0.005;

        @Comment("Charge: Aura cost")
        public double chargeAuraCost = 300.0;
        @Comment("Charge: Cooldown in seconds")
        public int chargeCooldown = 8;
        @Comment("Charge: Seconds the charge can be held")
        public double chargeHoldSeconds = 6.0;
        @Comment("Charge: Seconds the charge can be held with mastery")
        public double chargeHoldSecondsMastered = 8.0;
        @Comment("Charge: Speed in blocks per second")
        public double chargeSpeed = 22.0;
        @Comment("Charge: Damage dealt for each point of armor")
        public double chargeDamagePerArmor = 1.0;
        @Comment("Charge: % of the charge damage blown away blocks deal")
        public double chargeDebrisDamage = 0.5;

        public Endurance() {
        }
    }

    public static class Jet extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Boost: Extra % damage for each block per second the user moves at while propelled by jet")
        public double boostDamagePerSpeed = 0.01;
        @Comment("Wind Boost: Wind boost added while toggled on")
        public double windBoost = 1.0;

        @Comment("Slow Fall: Seconds the user can slow activate slow fall")
        public double slowFallSeconds = 3.0;
        @Comment("Slow Fall: Falling speed while slowed")
        public double slowFallSpeed = 2.0;

        @Comment("Jet Boost: Aura cost")
        public double boostAuraCost = 100.0;
        @Comment("Jet Boost: Cooldown in seconds")
        public int boostCooldown = 3;
        @Comment("Jet Boost: Uses before it goes on cooldown")
        public int boostCharges = 4;
        @Comment("Jet Boost: Boost distance")
        public double boostDistance = 15.0;
        @Comment("Jet Boost: Boost distance with mastery")
        public double boostDistanceMastered = 25.0;
        @Comment("Jet Boost: Up tilt added to the boost")
        public double boostLift = 0.2;
        @Comment("Jet Boost: Damage to anything the caster crashes into")
        public double boostDamage = 25.0;
        @Comment("Jet Boost: Damage with mastery")
        public double boostDamageMastered = 50.0;
        @Comment("Jet Boost: % of physical damage added")
        public double boostDamagePercent = 0.5;
        @Comment("Jet Boost: Speed gained each time the user boosts off a wall or ceiling")
        public double chainSpeed = 4.5;
        @Comment("Jet Boost: Speed gained each time with mastery")
        public double chainSpeedMastered = 5.0;
        @Comment("Jet Boost: Seconds without boosting before the built up speed is lost")
        public int chainSeconds = 10;
        @Comment("Jet Boost: Fastest a boost can go")
        public double maxBoostSpeed = 90.0;
        @Comment("Jet Boost: Speed the user pushes off a wall or ceiling with")
        public double pushOffSpeed = 12.0;

        @Comment("Jet Kick: Aura cost")
        public double kickAuraCost = 300.0;
        @Comment("Jet Kick: Cooldown in seconds")
        public int kickCooldown = 6;
        @Comment("Jet Kick: Range the user can rush a target from")
        public double kickRange = 24.0;
        @Comment("Jet Kick: Speed the user rushes at")
        public double rushSpeed = 35.0;
        @Comment("Jet Kick: Damage")
        public double kickDamage = 50.0;
        @Comment("Jet Kick: Damage with mastery")
        public double kickDamageMastered = 100.0;
        @Comment("Jet Kick: % of physical damage added")
        public double kickDamagePercent = 1.0;
        @Comment("Jet Kick: % of physical damage added with mastery")
        public double kickDamagePercentMastered = 1.5;
        @Comment("Jet Kick: Blocks the target is knocked back")
        public double kickKnockback = 15.0;
        @Comment("Jet Kick: Radius of the shockwave")
        public double kickShockwaveRadius = 4.0;

        @Comment("Jet Push: Aura cost")
        public double pushAuraCost = 500.0;
        @Comment("Jet Push: Cooldown in seconds")
        public int pushCooldown = 15;
        @Comment("Jet Push: Seconds it lasts")
        public int pushSeconds = 7;
        @Comment("Jet Push: Range")
        public double pushRange = 3.0;
        @Comment("Jet Push: How much larger than the user a target can be")
        public double pushMaxSize = 1.5;
        @Comment("Jet Push: Speed")
        public double pushSpeed = 25.0;
        @Comment("Jet Push: Damage when the target is pushed through blocks")
        public double pushDamage = 100.0;
        @Comment("Jet Push: Damage with mastery")
        public double pushDamageMastered = 200.0;
        @Comment("Jet Push: Damage added per point of hardness of the blocks broken")
        public double pushHardnessDamage = 2.0;
        @Comment("Jet Push: Hardest block that can be broken through")
        public double pushMaxHardness = 20.0;

        @Comment("Jet Counter: Aura cost")
        public double counterAuraCost = 300.0;
        @Comment("Jet Counter: Cooldown in seconds")
        public int counterCooldown = 10;
        @Comment("Jet Counter: Seconds the counter window lasts")
        public double counterSeconds = 1.5;
        @Comment("Jet Counter: Seconds the counter window lasts with mastery")
        public double counterSecondsMastered = 3.0;
        @Comment("Jet Counter: Damage")
        public double counterDamage = 100.0;
        @Comment("Jet Counter: Damage with mastery")
        public double counterDamageMastered = 200.0;
        @Comment("Jet Counter: % of physical damage added")
        public double counterDamagePercent = 1.0;
        @Comment("Jet Counter: Seconds the target is stunned")
        public int counterStunSeconds = 3;
        @Comment("Jet Counter: Blocks the user rises before dropping")
        public double counterHeight = 6.0;

        @Comment("Jet Dodge: Aura cost")
        public double dodgeAuraCost = 50.0;
        @Comment("Jet Dodge: Cooldown in seconds")
        public int dodgeCooldown = 1;
        @Comment("Jet Dodge: Distance")
        public double dodgeDistance = 5.0;
        @Comment("Jet Dodge: Distance with mastery")
        public double dodgeDistanceMastered = 10.0;
        @Comment("Jet Dodge: Speed ")
        public double dodgeSpeed = 35.0;
        @Comment("Jet Dodge: Speed the user bounces off a wall they dodge into")
        public double dodgeBounceSpeed = 12.0;
        @Comment("Jet Dodge: Upward speed of that bounce")
        public double dodgeBounceUp = 10.0;

        public Jet() {
        }
    }

    public static class Gigantification extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Grow: Aura cost per block of height grown")
        public double growAuraCost = 60.0;
        @Comment("Grow: Blocks of height gained each tick")
        public double growthPerTick = 0.05;
        @Comment("Grow: Blocks of height gained each tick with mastery")
        public double growthPerTickMastered = 0.10;
        @Comment("Grow: Biggest size the user can grow to")
        public double maxSize = 4.5;
        @Comment("Grow: Biggest size the user can grow to with mastery")
        public double maxSizeMastered = 6.5;
        @Comment("Grow: Armor gained per size gained")
        public double armorPerSize = 7.0;
        @Comment("Grow: Damage gained per size gained")
        public double damagePerSize = 7.0;
        @Comment("Grow: % of movement speed lost per size gained")
        public double speedLostPerSize = 0.075;
        @Comment("Grow: Reach per size of height")
        public double reachPerSize = 1.0;
        @Comment("Kick, Grab, Swat and Crush: Size the user has to gain before they can be used")
        public double minSize = 1.0;

        @Comment("Kick: Aura cost")
        public double kickAuraCost = 2000.0;
        @Comment("Kick: Cooldown in seconds")
        public int kickCooldown = 10;
        @Comment("Kick: Ticks the kick takes to land")
        public int kickWindup = 8;
        @Comment("Kick: Ticks added per size gained")
        public int kickWindupPerSize = 6;
        @Comment("Kick: Damage per size gained")
        public double kickSizeDamage = 7.0;
        @Comment("Kick: Damage per size gained with mastery")
        public double kickSizeDamageMastered = 6.0;
        @Comment("Kick: % of physical damage added")
        public double kickDamagePercent = 1.0;
        @Comment("Kick: % of physical damage added with mastery")
        public double kickDamagePercentMastered = 1.1;
        @Comment("Kick: Blocks a kicked target is knocked back")
        public double kickKnockback = 10.0;
        @Comment("Kick: Blocks of knockback added per size gained")
        public double kickKnockbackPerSize = 6.0;
        @Comment("Kick: Blocks kicked up at normal size")
        public int kickBlocks = 8;
        @Comment("Kick: Blocks kicked up per size gained")
        public double kickBlocksPerSize = 12.0;
        @Comment("Kick: Width of the wall the kick breaks through at normal size")
        public double kickWallWidth = 3.0;
        @Comment("Kick: Height of the wall the kick breaks through at normal size")
        public double kickWallHeight = 2.0;
        @Comment("Kick: Depth of the wall the kick breaks through at normal size")
        public double kickWallDepth = 2.0;
        @Comment("Kick: Blocks added to the wall's width and height per size gained")
        public double kickWallPerSize = 2.0;
        @Comment("Kick: Most blocks one kick can break out of a wall")
        public int kickWallMaxBlocks = 128;
        @Comment("Kick: Hardest block the kick can break or kick up")
        public double kickMaxHardness = 20.0;
        @Comment("Kick: Speed of kicked blocks")
        public double kickBlockSpeed = 30.0;
        @Comment("Kick: Damage per block a kicked block hits with")
        public double kickSpeedDamage = 2.0;
        @Comment("Kick: Damage per block with mastery")
        public double kickSpeedDamageMastered = 3.0;
        @Comment("Kick: Damage per point of hardness of a kicked block")
        public double kickHardnessDamage = 2.0;

        @Comment("Stomp: Aura cost")
        public double stompAuraCost = 600.0;
        @Comment("Stomp: Cooldown in seconds")
        public int stompCooldown = 5;
        @Comment("Stomp: Width at normal size")
        public double stompSize = 5.0;
        @Comment("Stomp: Width added per size gained")
        public double stompSizePerSize = 2.0;
        @Comment("Stomp: Damage per size gained")
        public double stompDamage = 15.0;
        @Comment("Stomp: Damage per size gained with mastery")
        public double stompDamageMastered = 20.0;
        @Comment("Stomp: % of physical damage added")
        public double stompDamagePercent = 0.5;
        @Comment("Stomp: Width of the shockwave multiplied by the stomp's")
        public double stompShockwaveScale = 2.0;
        @Comment("Stomp: % of the stomp's damage the shockwave deals")
        public double stompShockwaveDamage = 0.5;
        @Comment("Stomp: Blocks shot up at normal size")
        public int stompBlocks = 6;
        @Comment("Stomp: Blocks shot up per size gained")
        public double stompBlocksPerSize = 3.0;
        @Comment("Stomp: Damage per point of hardness of a block that hits something")
        public double stompHardnessDamage = 2.0;

        @Comment("Grab: Aura cost")
        public double grabAuraCost = 500.0;
        @Comment("Grab: Cooldown in seconds")
        public int grabCooldown = 6;
        @Comment("Grab: Blocks a thrown target flies at normal size")
        public double throwDistance = 24.0;
        @Comment("Grab: Blocks added to the throw per size gained")
        public double throwDistancePerSize = 12.0;
        @Comment("Grab: Blocks added to the throw per point of physical damage")
        public double throwDistancePercent = 0.3;
        @Comment("Grab: Farthest a target can be thrown")
        public double throwMaxDistance = 1000.0;
        @Comment("Grab: Damage when a target lands or hits a wall")
        public double throwImpactDamage = 50.0;
        @Comment("Grab: Blocks in the boulder at normal size")
        public int rockBlocks = 4;
        @Comment("Grab: Blocks added to the boulder per size gained")
        public double rockBlocksPerSize = 6.0;
        @Comment("Grab: Boulder damage")
        public double rockDamage = 200.0;
        @Comment("Grab: Boulder damage per point of hardness of its blocks")
        public double rockHardnessDamage = 2.0;
        @Comment("Grab: Range the boulder is thrown at")
        public double rockThrowRange = 48.0;
        @Comment("Grab: Boulder speed per tick")
        public double rockThrowSpeed = 2.0;

        @Comment("Swat: Aura cost")
        public double swatAuraCost = 1000.0;
        @Comment("Swat: Cooldown in seconds")
        public int swatCooldown = 12;
        @Comment("Swat: Blocks the target is knocked away")
        public double swatKnockback = 50.0;
        @Comment("Swat: Blocks the target is knocked away with mastery")
        public double swatKnockbackMastered = 100.0;
        @Comment("Swat: % of physical damage dealt, multiplied by size gained / swatSizeDivisor")
        public double swatDamagePercent = 1.0;
        @Comment("Swat: Size gained is divided by this for the damage")
        public double swatSizeDivisor = 2.5;

        @Comment("Crush: Aura cost")
        public double crushAuraCost = 1000.0;
        @Comment("Crush: Cooldown in seconds")
        public int crushCooldown = 12;
        @Comment("Crush: Seconds it can be held for")
        public int crushSeconds = 3;
        @Comment("Crush: Seconds it can be held for with mastery")
        public int crushSecondsMastered = 5;
        @Comment("Crush: % of physical damage dealt every second, multiplied by size gained / crushSizeDivisor")
        public double crushDamagePercent = 1.0;
        @Comment("Crush: Size gained is divided by this for the damage")
        public double crushSizeDivisor = 2.5;

        public Gigantification() {
        }
    }

    public static class TransformingArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Transform: Aura cost")
        public double transformAuraCost = 100.0;
        @Comment("Transform: Cooldown in seconds")
        public int transformCooldown = 0;

        @Comment("Claws: Damage added")
        public double clawDamage = 25.0;
        @Comment("Claws: Damage added with mastery")
        public double clawDamageMastered = 50.0;

        @Comment("Axe: Damage added")
        public double axeDamage = 50.0;
        @Comment("Axe: Damage added with mastery")
        public double axeDamageMastered = 100.0;
        @Comment("Axe: Attacks per second")
        public double axeAttackSpeed = 1.0;
        @Comment("Axe: Axe breaking speed")
        public double axeBreakSpeed = 8.0;

        @Comment("Spear: Damage added")
        public double spearDamage = 35.0;
        @Comment("Spear: Damage added with mastery")
        public double spearDamageMastered = 70.0;
        @Comment("Spear: Attack range added")
        public double spearReach = 5.0;

        @Comment("Axe and Spear: % of a melee hit dealt to everything around the target")
        public double sweepDamage = 0.5;

        public TransformingArms() {
        }
    }

    public static class Landmine extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Landmine, Detonation and Remote Detonation explosions break blocks")
        public boolean explosionsBreakBlocks = true;

        @Comment("Projectile Bombs: Width of the explosion a projectile makes")
        public double projectileSize = 1.0;
        @Comment("Projectile Bombs: Width of the explosion with mastery")
        public double projectileSizeMastered = 2.0;
        @Comment("Projectile Bombs: Explosion damage")
        public double projectileDamage = 25.0;
        @Comment("Projectile Bombs: Explosion damage with mastery")
        public double projectileDamageMastered = 50.0;

        @Comment("Landmine: Aura cost")
        public double mineAuraCost = 100.0;
        @Comment("Landmine: Cooldown in seconds")
        public int mineCooldown = 2;
        @Comment("Landmine: Range to place a landmine on the block being looked at")
        public double mineRange = 5.0;
        @Comment("Landmine: Most landmines a user can have placed")
        public int maxMines = 10;
        @Comment("Landmine: Width of the explosion")
        public double mineSize = 3.0;
        @Comment("Landmine: Width of the explosion with mastery")
        public double mineSizeMastered = 6.0;
        @Comment("Landmine: Explosion damage")
        public double mineDamage = 150.0;
        @Comment("Landmine: Explosion damage with mastery")
        public double mineDamageMastered = 300.0;

        @Comment("Detonation: Aura cost")
        public double bombAuraCost = 200.0;
        @Comment("Detonation: Cooldown in seconds")
        public int bombCooldown = 2;
        @Comment("Detonation and Remote Detonation: Range to place a bomb on a target")
        public double bombRange = 5.0;
        @Comment("Detonation: Most bombs one user can place")
        public int maxBombs = 5;
        @Comment("Detonation: Most bombs with mastery")
        public int maxBombsMastered = 10;
        @Comment("Detonation: Width of the explosion when the target dies")
        public double bombSize = 10.0;
        @Comment("Detonation: Width of the explosion with mastery")
        public double bombSizeMastered = 15.0;
        @Comment("Detonation: Explosion damage")
        public double bombDamage = 200.0;
        @Comment("Detonation: Explosion damage with mastery")
        public double bombDamageMastered = 400.0;
        @Comment("Detonation: % of the target's max health added to the damage")
        public double bombHealthPercent = 0.15;
        @Comment("Detonation: % of the target's max health added with mastery")
        public double bombHealthPercentMastered = 0.3;

        @Comment("Remote Detonation: Aura cost")
        public double remoteAuraCost = 200.0;
        @Comment("Remote Detonation: Cooldown in seconds")
        public int remoteCooldown = 2;
        @Comment("Remote Detonation: Most remote bombs a user can have placed")
        public int maxRemoteBombs = 5;
        @Comment("Remote Detonation: Width of the explosion")
        public double remoteSize = 4.0;
        @Comment("Remote Detonation: Width of the explosion with mastery")
        public double remoteSizeMastered = 8.0;
        @Comment("Remote Detonation: Explosion damage")
        public double remoteDamage = 250.0;
        @Comment("Remote Detonation: Explosion damage with mastery")
        public double remoteDamageMastered = 500.0;

        public Landmine() {
        }
    }

    public static class Grabbing extends ManasSubConfig {
        @Comment("Seconds a grab can hold a mob that's hostile")
        public int hostileHoldSeconds = 7;

        public Grabbing() {
        }
    }

    public static class Weld extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Weld, Arrest, Weldcraft, Attach and Transport: Range")
        public double targetRange = 8.0;

        @Comment("Weld: Aura cost")
        public double weldAuraCost = 200.0;
        @Comment("Weld: Cooldown in seconds")
        public int weldCooldown = 10;
        @Comment("Weld: Seconds the target is anchored to the ground")
        public int weldSeconds = 15;

        @Comment("Arrest: Aura cost")
        public double arrestAuraCost = 200.0;
        @Comment("Arrest: Cooldown in seconds")
        public int arrestCooldown = 10;
        @Comment("Arrest: Seconds the target can't use active abilities")
        public int arrestSeconds = 15;

        @Comment("Weldcraft: Aura cost")
        public double weldcraftAuraCost = 400.0;
        @Comment("Weldcraft: Cooldown in seconds")
        public int weldcraftCooldown = 20;
        @Comment("Weldcraft: Seconds the target can't move, fly, see or activate skills")
        public int weldcraftSeconds = 15;

        @Comment("Attach: Aura cost")
        public double attachAuraCost = 100.0;
        @Comment("Attach: Cooldown in seconds")
        public int attachCooldown = 3;

        @Comment("Transport: Aura cost")
        public double transportAuraCost = 100.0;
        @Comment("Transport: Cooldown in seconds")
        public int transportCooldown = 2;
        @Comment("Transport: Max targets welded onto the user at once")
        public int maxTransported = 3;

        public Weld() {
        }
    }

    public static class Whale extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Click Sound: Aura cost")
        public double clickAuraCost = 300.0;
        @Comment("Click Sound: Cooldown in seconds")
        public int clickCooldown = 15;
        @Comment("Click Sound: Width of the area around the user")
        public double clickSize = 10.0;
        @Comment("Click Sound: Seconds the targets are paralyzed")
        public int clickStunSeconds = 3;

        @Comment("Whale Strike: Aura cost")
        public double strikeAuraCost = 200.0;
        @Comment("Whale Strike: Cooldown in seconds")
        public int strikeCooldown = 5;
        @Comment("Whale Strike: Reach of the headbutt")
        public double strikeRange = 5.0;
        @Comment("Whale Strike: Water damage")
        public double strikeDamage = 50.0;
        @Comment("Whale Strike: Water damage with mastery")
        public double strikeDamageMastered = 100.0;
        @Comment("Whale Strike: % of physical damage added")
        public double strikeDamagePercent = 1.0;
        @Comment("Whale Strike: Knockback strength")
        public double strikeKnockback = 1.5;

        public Whale() {
        }
    }

    public static class WholeBodyLens extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Light Affinity: Light boost added")
        public double lightBoost = 1.0;

        @Comment("Light Charge Up: Charge gained every second under the sun")
        public double sunCharge = 5.0;
        @Comment("Light Charge Up: Charge gained every second under the sun with mastery")
        public double sunChargeMastered = 10.0;
        @Comment("Light Charge Up: Charge gained per point of light damage taken")
        public double lightDamageCharge = 0.2;
        @Comment("Light Charge Up: Most light that can be stored")
        public double maxCharge = 1_500.0;

        @Comment("Condensed Light: Aura cost")
        public double beamAuraCost = 200.0;
        @Comment("Condensed Light: Cooldown in seconds")
        public int beamCooldown = 5;
        @Comment("Condensed Light: Range ")
        public double beamRange = 40.0;
        @Comment("Condensed Light: Light damage per charge")
        public double beamDamagePerCharge = 1.0;

        @Comment("Flash Capture: Aura cost")
        public double flashAuraCost = 200.0;
        @Comment("Flash Capture: Cooldown in seconds")
        public int flashCooldown = 10;
        @Comment("Flash Capture: Radius enemies get blinded")
        public double flashRadius = 5.0;
        @Comment("Flash Capture: Radius enemies get blinded with mastery")
        public double flashRadiusMastered = 10.0;
        @Comment("Flash Capture: Seconds enemies stay blinded")
        public double flashSeconds = 6.0;

        @Comment("Lens Array: Aura cost")
        public double lensAuraCost = 300.0;
        @Comment("Lens Array: Cooldown in seconds")
        public int lensCooldown = 20;
        @Comment("Lens Array: Radius hidden enemies get revealed")
        public double lensRadius = 10.0;
        @Comment("Lens Array: Radius hidden enemies get revealed")
        public double lensRadiusMastered = 15.0;
        @Comment("Lens Array: Seconds they stay revealed")
        public double lensSeconds = 30.0;
        @Comment("Lens Array: Highest Presence Concealment it can see through")
        public int lensConcealment = 2;
        @Comment("Lens Array: Highest Presence Concealment it can see through with mastery")
        public int lensConcealmentMastered = 3;

        public WholeBodyLens() {
        }
    }

    public static class Wing extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Air Affinity: Wind boost added")
        public double airBoost = 1.0;
        @Comment("Flight: Flight speed multiplier")
        public double flightSpeedMultiplier = 1.25;

        @Comment("Boost: Aura cost every second")
        public double boostAuraCost = 50.0;
        @Comment("Boost: Speed in blocks per second")
        public double boostSpeed = 50.0;
        @Comment("Boost: Speed multiplier with mastery")
        public double boostSpeedMasteredMultiplier = 1.35;

        @Comment("Sky Hunter: Damage added while in the air")
        public double skyHunterDamage = 10.0;
        @Comment("Sky Hunter: Damage added while in the air with mastery")
        public double skyHunterDamageMastered = 20.0;

        @Comment("Airborne: Aura cost")
        public double airborneAuraCost = 300.0;
        @Comment("Airborne: Cooldown in seconds")
        public int airborneCooldown = 8;
        @Comment("Airborne: Blocks the user flies up")
        public double airborneHeight = 50.0;
        @Comment("Airborne: Radius of the takeoff blast")
        public double airborneRadius = 5.0;
        @Comment("Airborne: Radius of the takeoff blast with mastery")
        public double airborneRadiusMastered = 10.0;
        @Comment("Airborne: Wind damage on takeoff")
        public double airborneDamage = 75.0;
        @Comment("Airborne: Wind damage with mastery")
        public double airborneDamageMastered = 150.0;

        @Comment("Dive: Aura cost")
        public double diveAuraCost = 200.0;
        @Comment("Dive: Cooldown in seconds")
        public int diveCooldown = 5;
        @Comment("Dive: Starting speed in blocks per second")
        public double diveSpeed = 60.0;
        @Comment("Dive: Speed added each time the dive speeds up")
        public double diveSpeedIncrease = 20.0;
        @Comment("Dive: Ticks between each speed up")
        public int diveSpeedTicks = 10;
        @Comment("Dive: Fastest the dive can get")
        public double diveMaxSpeed = 400.0;
        @Comment("Dive: Shockwave damage per size at the starting speed")
        public double diveDamage = 50.0;
        @Comment("Dive: Shockwave radius per size at the starting speed")
        public double diveRadius = 4.0;
        @Comment("Dive: Range to lock onto a target")
        public double diveTargetRange = 64.0;
        @Comment("Dive: Degrees a target can be locked onto")
        public double diveAimDegrees = 15.0;

        public Wing() {
        }
    }

    public static class ZombieVirus extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Zombie Creation: Aura cost")
        public double creationAuraCost = 200.0;
        @Comment("Zombie Creation: Cooldown in seconds")
        public int creationCooldown = 3;
        @Comment("Zombie Creation: Multiplier of the target's EP needed")
        public double epMultiplier = 5.0;
        @Comment("Zombie Creation: Chance a hit turns the target into a zombie")
        public double chance = 0.5;
        @Comment("Zombie Creation: Chance with mastery")
        public double chanceMastered = 1.0;
        @Comment("Zombie Creation: Chance against abnormal condition resistance")
        public double resistantChance = 0.25;
        @Comment("Zombie Creation: Chance against abnormal condition resistance with mastery")
        public double resistantChanceMastered = 0.5;
        @Comment("Zombie Creation: Chance against abnormal condition nullification")
        public double nullifiedChance = 0.12;
        @Comment("Zombie Creation: Chance against abnormal condition nullification with mastery")
        public double nullifiedChanceMastered = 0.25;
        @Comment("Zombie Creation: Seconds before a zombie dies")
        public int zombieSeconds = 180;
        @Comment("Zombie Creation: Max zombie count")
        public int maxZombies = 15;
        @Comment("Zombie Creation: Entities that cant become zombies")
        public List<String> blacklist = List.of("tensura:training_dummy");
        @Comment("Zombie Creation: Mods whose entities can't become zombies")
        public List<String> modBlacklist = List.of();

        @Comment("Zombification End: Aura cost")
        public double endAuraCost = 100.0;
        @Comment("Zombification End: Cooldown in seconds")
        public int endCooldown = 10;

        @Comment("Virus Cloud: Aura cost every second it's held")
        public double cloudAuraCost = 150.0;
        @Comment("Virus Cloud: Cooldown in seconds")
        public int cloudCooldown = 10;
        @Comment("Virus Cloud: Width of the cloud")
        public double cloudSize = 7.5;
        @Comment("Virus Cloud: Width of the cloud with mastery")
        public double cloudSizeMastered = 15.0;
        @Comment("Virus Cloud: Seconds between infection rolls for everything inside")
        public int cloudInfectSeconds = 2;

        @Comment("Horde Call: Aura cost")
        public double hordeAuraCost = 100.0;
        @Comment("Horde Call: Cooldown in seconds")
        public int hordeCooldown = 3;
        @Comment("Horde Call: Range of zombies that hear it")
        public double hordeRange = 64.0;

        public ZombieVirus() {
        }
    }

    public static class WaterPump extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Water Affinity: Water boost added")
        public double waterBoost = 0.25;
        @Comment("Water Affinity: Water boost added with mastery")
        public double waterBoostMastered = 0.5;

        @Comment("Water Beam: Aura cost every second")
        public double beamAuraCost = 50.0;
        @Comment("Water Beam: Range")
        public double beamRange = 20.0;
        @Comment("Water Beam: Water damage")
        public double beamDamage = 50.0;
        @Comment("Water Beam: Ticks between hits")
        public int beamHitTicks = 10;

        @Comment("Hydro Cannon: Aura cost")
        public double cannonAuraCost = 500.0;
        @Comment("Hydro Cannon: Cooldown in seconds")
        public int cannonCooldown = 15;
        @Comment("Hydro Cannon: Water damage")
        public double cannonDamage = 350.0;
        @Comment("Hydro Cannon: Size of the water ball")
        public double cannonSize = 3.0;

        public WaterPump() {
        }
    }

    public static class WaterGun extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Water Affinity: Water boost added")
        public double waterBoost = 0.5;
        @Comment("Water Affinity: Water boost added with mastery")
        public double waterBoostMastered = 1.5;

        @Comment("Spray: Aura cost every second")
        public double sprayAuraCost = 20.0;
        @Comment("Spray: Range")
        public double sprayRange = 10.0;
        @Comment("Spray: Water damage")
        public double sprayDamage = 15.0;
        @Comment("Spray: Ticks between hits")
        public int sprayHitTicks = 10;

        @Comment("Water Bullet: Aura cost")
        public double bulletAuraCost = 150.0;
        @Comment("Water Bullet: Cooldown in seconds")
        public int bulletCooldown = 3;
        @Comment("Water Bullet: Ticks spent charging")
        public int bulletChargeTicks = 10;
        @Comment("Water Bullet: Water damage")
        public double bulletDamage = 75.0;

        @Comment("Water Jet: Aura cost every second")
        public double jetAuraCost = 20.0;
        @Comment("Water Jet: Upward speed in blocks per second")
        public double jetLift = 20.0;

        public WaterGun() {
        }
    }

    public static class WaterControl extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Water Affinity: Water boost added")
        public double waterBoost = 2.0;
        @Comment("Range of the water sources")
        public double waterRange = 8.0;
        @Comment("Water Ball: Water sources used")
        public int ballSourcesUsed = 1;
        @Comment("Suffocation: Water sources used")
        public int suffocationSourcesUsed = 1;
        @Comment("Tidal Wave: Water sources used")
        public int waveSourcesUsed = 1;

        @Comment("Water Ball: Aura cost")
        public double ballAuraCost = 200.0;
        @Comment("Water Ball: Cooldown in seconds")
        public int ballCooldown = 3;
        @Comment("Water Ball: Water damage")
        public double ballDamage = 100.0;
        @Comment("Water Ball: Water damage with mastery")
        public double ballDamageMastered = 150.0;

        @Comment("Suffocation: Aura cost")
        public double suffocationAuraCost = 400.0;
        @Comment("Suffocation: Cooldown in seconds")
        public int suffocationCooldown = 20;
        @Comment("Suffocation: Range")
        public double suffocationRange = 15.0;
        @Comment("Suffocation: Seconds the target is trapped")
        public int suffocationSeconds = 6;
        @Comment("Suffocation: Damage every second once the target is out of air")
        public double suffocationDamage = 20.0;

        @Comment("Tidal Wave: Aura cost")
        public double waveAuraCost = 500.0;
        @Comment("Tidal Wave: Cooldown in seconds")
        public int waveCooldown = 15;
        @Comment("Tidal Wave: Water damage")
        public double waveDamage = 150.0;
        @Comment("Tidal Wave: Water damage with mastery")
        public double waveDamageMastered = 225.0;
        @Comment("Tidal Wave: Width of the wave")
        public double waveWidth = 8.0;
        @Comment("Tidal Wave: Blocks the wave travels")
        public double waveDistance = 16.0;

        public WaterControl() {
        }
    }

    public static class Voyance extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Voyance: Aura cost to activate")
        public double auraCost = 200.0;
        @Comment("Voyance: Sight range")
        public int sightRange = 24;
        @Comment("Voyance: Projectile dodge chance while active")
        public double dodgeChance = 50.0;

        public Voyance() {
        }
    }

    public static class ViralCosmos extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Crop Growth: Width of the area crops grow faster in")
        public int cropRange = 10;
        @Comment("Crop Growth: Width of the area crops grow faster in with mastery")
        public int cropRangeMastered = 20;
        @Comment("Crop Growth: Crop growth multiplier")
        public double cropGrowth = 5.0;

        @Comment("Petal Release: Aura cost")
        public double releaseAuraCost = 200.0;
        @Comment("Petal Release: Cooldown in seconds")
        public int releaseCooldown = 5;
        @Comment("Petal Release: Range")
        public double releaseRange = 20.0;
        @Comment("Petal Release: Poison damage/healing")
        public double releaseDamage = 150.0;
        @Comment("Petal Release: Poison damage/healing with mastery")
        public double releaseDamageMastered = 300.0;

        @Comment("Petal Swarm: Aura cost")
        public double swarmAuraCost = 400.0;
        @Comment("Petal Swarm: Cooldown in seconds")
        public int swarmCooldown = 20;
        @Comment("Petal Swarm: Seconds the swarm lasts")
        public int swarmSeconds = 10;
        @Comment("Petal Swarm: Width of the swarm")
        public double swarmSize = 10.0;
        @Comment("Petal Swarm: Width of the swarm with mastery")
        public double swarmSizeMastered = 15.0;
        @Comment("Petal Swarm: Poison damage every second")
        public double swarmDamage = 50.0;
        @Comment("Petal Swarm: Poison damage every second with mastery")
        public double swarmDamageMastered = 100.0;
        @Comment("Petal Swarm: Healing every second")
        public double swarmHeal = 100.0;
        @Comment("Petal Swarm: Healing every second with mastery")
        public double swarmHealMastered = 200.0;

        @Comment("Flower Growth: Aura cost")
        public double growthAuraCost = 300.0;
        @Comment("Flower Growth: Cooldown in seconds")
        public int growthCooldown = 30;
        @Comment("Flower Growth: Range")
        public double growthRange = 10.0;
        @Comment("Flower Growth: Seconds the flower lasts")
        public int growthSeconds = 30;
        @Comment("Flower Growth: Width of the poison flower's gas")
        public double poisonGasSize = 5.0;
        @Comment("Flower Growth: Lethal poison damage every second from the smaller flowers")
        public double lethalPoisonDamage = 10.0;
        @Comment("Flower Growth: % of max HP added to the lethal poison damage")
        public double lethalPoisonPercent = 0.05;
        @Comment("Flower Growth: Slowness level on the poison flower's host")
        public int poisonSlowness = 1;
        @Comment("Flower Growth: Multiplier of MP and aura cost for the poison flower's host")
        public double poisonCostMultiplier = 2.0;
        @Comment("Flower Growth: Width of the healing flower's gas")
        public double healingGasSize = 5.0;
        @Comment("Flower Growth: Width of the healing flower's gas with mastery")
        public double healingGasSizeMastered = 10.0;
        @Comment("Flower Growth: Seconds between the healing flower's gas")
        public int healingGasSeconds = 5;
        @Comment("Flower Growth: % of max HP the healing gas takes")
        public double healingDrain = 0.05;
        @Comment("Flower Growth: % of max HP taken from targets with abnormal condition resistance")
        public double healingDrainResisted = 0.02;
        @Comment("Flower Growth: Damage the healing flower's host can take before it breaks")
        public double flowerHealth = 500.0;

        public ViralCosmos() {
        }
    }

    public static class ToolArms extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Mining Boost: Mining speed added per arm")
        public double miningBoost = 0.25;
        @Comment("Mining Boost: Mining speed added per arm with mastery")
        public double miningBoostMastered = 0.5;
        @Comment("Parry: Chance for each arm to parry")
        public double parryChance = 0.05;
        @Comment("Parry: Chance for each arm to parry with mastery")
        public double parryChanceMastered = 0.1;

        @Comment("Arm Creation: Aura cost per arm")
        public double armAuraCost = 50.0;
        @Comment("Arm Creation: Cooldown in seconds")
        public int armCooldown = 0;

        public ToolArms() {
        }
    }

    public static class TongueTank extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Tongue Tank: Aura cost")
        public double auraCost = 100.0;
        @Comment("Tongue Tank: Cooldown in seconds")
        public int cooldown = 2;
        @Comment("Tongue Tank: Max charge")
        public double maxCharge = 50.0;
        @Comment("Tongue Tank: Max charge with mastery")
        public double maxChargeMastered = 100.0;
        @Comment("Tongue Tank: Charge built up every second")
        public double chargePerSecond = 10.0;
        @Comment("Tongue Tank: Damage per charge")
        public double damagePerCharge = 5.0;
        @Comment("Tongue Tank: Range of the shot")
        public double range = 40.0;
        @Comment("Tongue Tank: Charge needed to explode")
        public double explosionCharge = 50.0;
        @Comment("Tongue Tank: Size of the explosion at that charge")
        public double explosionSize = 5.0;
        @Comment("Tongue Tank: Charge for every +1 explosion size")
        public double chargePerExplosionSize = 5.0;
        @Comment("Tongue Tank: Explosions break blocks")
        public boolean explosionBreaksBlocks = true;
        @Comment("Tongue Tank: Speed the user is knocked back per charge")
        public double recoilPerCharge = 0.03;
        @Comment("Tongue Tank: % of the shot's damage dealt as fire")
        public double fireDamagePercent = 0.5;
        @Comment("Tongue Tank: Seconds targets hit are set on fire for")
        public double burnSeconds = 3.0;
        @Comment("Ammo Regeneration: Most charge it builds up with mastery")
        public double ammoCap = 50.0;

        @Comment("Barrage Shot: Aura cost")
        public double barrageAuraCost = 150.0;
        @Comment("Barrage Shot: Cooldown in seconds")
        public int barrageCooldown = 4;
        @Comment("Barrage Shot: Number of shots")
        public int barrageShots = 3;
        @Comment("Barrage Shot: Charge each shot fires with")
        public double barrageCharge = 15.0;
        @Comment("Barrage Shot: Ticks between shots")
        public int barrageShotTicks = 4;

        @Comment("Point Blank: Aura cost")
        public double pointBlankAuraCost = 150.0;
        @Comment("Point Blank: Cooldown in seconds")
        public int pointBlankCooldown = 6;
        @Comment("Point Blank: Range to grab a target")
        public double pointBlankRange = 3.0;
        @Comment("Point Blank: Charge the shot hits as")
        public double pointBlankCharge = 35.0;
        @Comment("Point Blank: Charge the shot hits as with mastery")
        public double pointBlankChargeMastered = 70.0;

        @Comment("Air Dive: Landing speed needed for a shockwave")
        public double diveMinSpeed = 0.6;
        @Comment("Air Dive: Largest shockwave radius")
        public double diveMaxRadius = 15.0;
        @Comment("Air Dive: Shockwave radius per block of landing speed")
        public double diveRadiusPerSpeed = 2.5;
        @Comment("Air Dive: Shockwave damage per block of landing speed")
        public double diveDamagePerSpeed = 50.0;

        public TongueTank() {
        }
    }

    public static class PainBlocker extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Damage Mitigation: Aura cost")
        public double mitigationAuraCost = 300.0;
        @Comment("Damage Mitigation: Cooldown in seconds")
        public int mitigationCooldown = 30;
        @Comment("Damage Mitigation: Seconds damage is stored for")
        public int mitigationSeconds = 15;
        @Comment("Damage Mitigation: Seconds damage is stored for with mastery")
        public int mitigationSecondsMastered = 30;

        public PainBlocker() {
        }
    }

    public static class FierceGains extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Hardened Muscles: Armor added")
        public double armor = 15.0;
        @Comment("Hardened Muscles: Armor added with mastery")
        public double armorMastered = 30.0;

        @Comment("Body Block: Aura cost")
        public double blockAuraCost = 100.0;
        @Comment("Body Block: Max seconds it can be held")
        public int blockSeconds = 5;
        @Comment("Body Block: Cooldown in seconds")
        public int blockCooldown = 5;

        @Comment("Combat Pump: Damage taken for each stack")
        public double pumpDamagePerStackTaken = 50.0;
        @Comment("Combat Pump: Physical damage added per stack")
        public double pumpDamagePerStack = 25.0;
        @Comment("Combat Pump: Max stacks")
        public int pumpMaxStacks = 10;
        @Comment("Combat Pump: Max stacks with mastery")
        public int pumpMaxStacksMastered = 15;
        @Comment("Combat Pump: Seconds without being attacked before a stack is lost")
        public int pumpDecaySeconds = 10;

        @Comment("Power Roar: Aura cost")
        public double roarAuraCost = 500.0;
        @Comment("Power Roar: Cooldown in seconds")
        public int roarCooldown = 15;
        @Comment("Power Roar: Sound damage")
        public double roarDamage = 250.0;
        @Comment("Power Roar: Width of the area")
        public double roarSize = 5.0;
        @Comment("Power Roar: Width of the area with mastery")
        public double roarSizeMastered = 10.0;
        @Comment("Power Roar: Seconds targets are paralyzed")
        public int roarParalysisSeconds = 5;
        @Comment("Power Roar: Seconds targets are paralyzed with mastery")
        public int roarParalysisSecondsMastered = 10;
        @Comment("Power Roar: Combat Pump stacks gained")
        public int roarStacks = 2;
        @Comment("Power Roar: Combat Pump stacks gained with mastery")
        public int roarStacksMastered = 4;

        @Comment("Limit Break: Aura cost")
        public double limitBreakAuraCost = 2_000.0;
        @Comment("Limit Break: Cooldown in seconds")
        public int limitBreakCooldown = 600;
        @Comment("Limit Break: Seconds Combat Pump has no cap")
        public int limitBreakSeconds = 60;
        @Comment("Limit Break: Weakness level afterwards")
        public int limitBreakWeakness = 5;
        @Comment("Limit Break: Seconds of weakness, fragility and paralysis afterwards")
        public int limitBreakAfterSeconds = 300;

        @Comment("Stack Breaker: Aura cost")
        public double stackBreakerAuraCost = 500.0;
        @Comment("Stack Breaker: Cooldown in seconds")
        public int stackBreakerCooldown = 10;

        public FierceGains() {
        }
    }

    public static class MuscleExpansion extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Big Muscles: Physical damage added")
        public double bigMusclesDamage = 20.0;
        @Comment("Grabs: Max size multiplier of the target")
        public double grabMaxSize = 1.5;
        @Comment("Grabs: Max size multiplier of the target with mastery")
        public double grabMaxSizeExpanded = 2.5;
        @Comment("Grabs: Reach")
        public double grabRange = 3.0;

        @Comment("Muscle Expansion: Aura cost")
        public double expansionAuraCost = 200.0;
        @Comment("Muscle Expansion: Arm size")
        public double expansionSize = 1.25;
        @Comment("Muscle Expansion: Arm size with mastery")
        public double expansionSizeMastered = 1.5;
        @Comment("Muscle Expansion: Physical damage multiplier")
        public double expansionDamage = 1.15;
        @Comment("Muscle Expansion: Physical damage multiplier with mastery")
        public double expansionDamageMastered = 1.3;

        @Comment("Muscle Armor: % of damage taken the expanded muscles absorb")
        public double muscleArmorReduction = 0.05;
        @Comment("Muscle Armor: % of damage taken the expanded muscles absorb with mastery")
        public double muscleArmorReductionMastered = 0.1;
        @Comment("Muscle Armor: Damage the muscles can absorb before they go back to normal")
        public double muscleArmorHealth = 600.0;
        @Comment("Muscle Armor: Cooldown in seconds after the muscles give out")
        public int muscleArmorBrokenCooldown = 30;

        @Comment("Slam: Aura cost")
        public double slamAuraCost = 300.0;
        @Comment("Slam: Cooldown in seconds")
        public int slamCooldown = 6;
        @Comment("Slam: Damage")
        public double slamDamage = 75.0;
        @Comment("Slam: Damage with mastery")
        public double slamDamageMastered = 150.0;
        @Comment("Slam: % of physical damage added")
        public double slamDamagePercent = 0.5;
        @Comment("Slam: Crater radius")
        public double craterRadius = 2.0;
        @Comment("Slam: Crater radius with mastery")
        public double craterRadiusMastered = 3.0;

        @Comment("Choke Hits: Aura cost every second")
        public double chokeAuraCost = 50.0;
        @Comment("Choke Hits: Ticks between each hit")
        public int chokeHitTicks = 8;
        @Comment("Choke Hits: % of physical damage each hit deals")
        public double chokeDamagePercent = 0.5;

        @Comment("Arm Propulsion: Aura cost")
        public double propulsionAuraCost = 150.0;
        @Comment("Arm Propulsion: Cooldown in seconds")
        public int propulsionCooldown = 4;
        @Comment("Arm Propulsion: Distance launched")
        public double propulsionDistance = 30.0;
        @Comment("Arm Propulsion: % of the distance when used in the air with mastery")
        public double propulsionAirShare = 0.5;
        @Comment("Arm Propulsion: Distance multiplier while the muscles are expanded")
        public double propulsionExpandedMultiplier = 2.0;

        public MuscleExpansion() {
        }
    }

    public static class TwinImpact extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Twin Impact: % of the damage the first hit deals")
        public double firstHitDamage = 0.75;
        @Comment("Twin Impact: % of the original damage the second impact deals")
        public double secondHitDamage = 1.25;
        @Comment("Twin Impact: % of the original damage the second impact deals with mastery")
        public double secondHitDamageMastered = 1.5;
        @Comment("Twin Impact: Seconds before the second impact")
        public double secondHitSeconds = 2.0;

        @Comment("Stored Echo: Targets marked at once")
        public int echoMarks = 1;
        @Comment("Stored Echo: Targets marked at once with mastery")
        public int echoMarksMastered = 3;
        @Comment("Stored Echo: Range marks are removed at")
        public double echoRange = 64.0;
        @Comment("Follow Up: % of extra damage marked targets take")
        public double followUpDamage = 0.15;

        @Comment("Pulling Strike: Aura cost")
        public double pullAuraCost = 200.0;
        @Comment("Pulling Strike: Cooldown in seconds")
        public int pullCooldown = 6;
        @Comment("Pulling Strike: Range to a marked target")
        public double pullRange = 32.0;
        @Comment("Pulling Strike: % of the way back the target is pulled")
        public double pullShare = 0.7;
        @Comment("Pulling Strike: Blocks the target stops short of the user")
        public double pullStopDistance = 3.0;

        public TwinImpact() {
        }
    }

    public static class Traject extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Attack Redirect: Aura cost")
        public double redirectAuraCost = 200.0;
        @Comment("Attack Redirect: Cooldown in seconds")
        public int redirectCooldown = 20;
        @Comment("Attack Redirect: Seconds projectiles are sent back")
        public int redirectSeconds = 5;
        @Comment("Attack Redirect: Seconds projectiles are sent back with mastery")
        public int redirectSecondsMastered = 10;

        @Comment("Forced Vector: Aura cost")
        public double forcedVectorAuraCost = 300.0;
        @Comment("Forced Vector: Cooldown in seconds")
        public int forcedVectorCooldown = 10;
        @Comment("Forced Vector: Range")
        public double forcedVectorRange = 24.0;
        @Comment("Forced Vector: Speed the target is sent at")
        public double forcedVectorSpeed = 2.5;
        @Comment("Forced Vector: Damage is block hardness X speed in blocks per second X this")
        public double forcedVectorDamageScale = 1.0;
        @Comment("Forced Vector: Hardness used for unbreakable blocks")
        public double forcedVectorUnbreakableHardness = 50.0;

        @Comment("Ricochet Shot: Aura cost")
        public double ricochetAuraCost = 150.0;
        @Comment("Ricochet Shot: Cooldown in seconds")
        public int ricochetCooldown = 8;
        @Comment("Ricochet Shot: Range to bounce to the next target")
        public double ricochetRange = 8.0;
        @Comment("Ricochet Shot: Range to bounce to the next target with mastery")
        public double ricochetRangeMastered = 16.0;

        @Comment("Trajectory Change: Aura cost")
        public double markAuraCost = 100.0;
        @Comment("Trajectory Change: Cooldown in seconds")
        public int markCooldown = 3;
        @Comment("Trajectory Change: Range to mark a target")
        public double markRange = 64.0;

        public Traject() {
        }
    }

    public static class TreeFrog extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Webbed Feet: Swim speed added")
        public double swimSpeed = 1.0;

        @Comment("Toxin Skin: Seconds of fatal poison")
        public int toxinSeconds = 5;
        @Comment("Toxin Skin: Fatal poison level")
        public int toxinAmplifier = 1;
        @Comment("Toxin Skin: Fatal poison level with mastery")
        public int toxinAmplifierMastered = 2;

        @Comment("Poison Boost: Poison boost added")
        public double poisonBoost = 1.0;
        @Comment("Toxic Hands: % of the user's damage dealt as poison damage")
        public double toxicHandsShare = 1.0;

        @Comment("Climb: Aura cost")
        public double climbAuraCost = 20.0;

        @Comment("Camouflage: Aura cost")
        public double camouflageAuraCost = 150.0;
        @Comment("Camouflage: Cooldown in seconds")
        public int camouflageCooldown = 10;
        @Comment("Camouflage: Seconds it lasts")
        public int camouflageSeconds = 30;
        @Comment("Camouflage: Presence Concealment level")
        public int camouflageLevel = 2;

        @Comment("Tongue Snatch: Aura cost")
        public double tongueAuraCost = 150.0;
        @Comment("Tongue Snatch: Cooldown in seconds")
        public int tongueCooldown = 6;
        @Comment("Tongue Snatch: Range")
        public double tongueRange = 15.0;
        @Comment("Tongue Snatch: Range with mastery")
        public double tongueRangeMastered = 30.0;
        @Comment("Tongue Slam: Cooldown in seconds")
        public int tongueSlamCooldown = 12;
        @Comment("Tongue Slam: Damage")
        public double tongueSlamDamage = 25.0;
        @Comment("Tongue Slam: Damage added per hardness of the block it's slammed onto")
        public double tongueSlamHardnessMultiplier = 2.0;
        @Comment("Tongue Slam: Damage added per hardness of the block it's slammed onto with mastery")
        public double tongueSlamHardnessMultiplierMastered = 3.0;
        @Comment("Tongue Slam: Seconds of fatal poison afterwards")
        public int tongueSlamPoisonSeconds = 15;

        public TreeFrog() {
        }
    }

    public static class Tripod extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;

        @Comment("Grounded Guard: Armor while on the ground")
        public double groundedArmor = 15.0;
        @Comment("Third Leg: % of movement speed added")
        public double thirdLegSpeed = 0.33;

        @Comment("Defensive Stance: Aura cost")
        public double stanceAuraCost = 200.0;
        @Comment("Defensive Stance: Cooldown in seconds")
        public int stanceCooldown = 12;
        @Comment("Defensive Stance: Seconds the counter window lasts")
        public double stanceSeconds = 3.0;
        @Comment("Defensive Stance: Seconds the counter window lasts with mastery")
        public double stanceSecondsMastered = 5.0;

        @Comment("Pin and Stomp: Aura cost")
        public double stompAuraCost = 150.0;
        @Comment("Pin and Stomp: Cooldown in seconds")
        public int stompCooldown = 6;
        @Comment("Pin and Stomp: Range to the target")
        public double stompRange = 4.0;
        @Comment("Pin and Stomp: Physical attack damage multiplier")
        public double stompDamage = 1.25;
        @Comment("Pin and Stomp: Seconds the target stays pinned")
        public double pinSeconds = 2.0;

        public Tripod() {
        }
    }

    public static class Warping extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 2_500.0;
        @Comment("Spatial Boost: Space boost added")
        public double spaceBoost = 1.0;
        @Comment("Warping: Entity allies have to be within this range to warp / be warped")
        public double allyRange = 30.0;

        @Comment("Warping: Aura cost per warp")
        public double warpAuraCost = 500.0;
        @Comment("Warping: Cooldown in seconds")
        public int warpCooldown = 5;
        @Comment("Warping: Seconds warp takes to activate")
        public double warpActivationSeconds = 1.5;

        @Comment("Goop Defense: Aura cost per projectile")
        public double goopAuraCost = 5_000.0;
        @Comment("Goop Defense: Radius that projectiles get absorbed")
        public double goopRadius = 7.0;

        @Comment("Emergency Exit: Cooldown in seconds")
        public int emergencyCooldown = 300;
        @Comment("Emergency Exit: % of max HP the user is left with")
        public double emergencyHealth = 0.1;

        public Warping() {
        }
    }

    public static class NewOrder extends ManasSubConfig {
        @Comment("Skill Mastery Points")
        public double masteryPoints = 5_000.0;

        @Comment("Say My Name: Aura cost to call out a name")
        public double nameAuraCost = 100.0;
        @Comment("Say My Name: Cooldown in seconds")
        public int nameCooldown = 1;
        @Comment("Say My Name: Seconds a called out name stays ready for a rule")
        public int nameMemorySeconds = 30;

        @Comment("Rules: Aura cost to make a rule")
        public double ruleAuraCost = 4_000.0;
        @Comment("Rules: Cooldown in seconds after making a rule")
        public int ruleCooldown = 2;

        @Comment("Harden Into My Shape: Ticks after the user's hit that the air copy attacks")
        public int hardenDelayTicks = 10;
        @Comment("Harden Into My Shape: % of the user's melee damage the air copy deals as wind damage")
        public double hardenDamageMultiplier = 1.15;
        @Comment("Harden Into My Shape: Size of the air copy compared to the user")
        public double hardenSize = 4.0;


        @Comment("No Longer Exist: Range")
        public double vacuumRange = 32.0;
        @Comment("No Longer Exist: Radius of the vacuum")
        public double vacuumRadius = 20.0;
        @Comment("No Longer Exist: Seconds the vacuum lasts")
        public int vacuumSeconds = 10;
        @Comment("No Longer Exist: Cooldown in seconds")
        public int vacuumCooldown = 7;
        @Comment("No Longer Exist: Aura cost per vacuum")
        public double vacuumAuraCost = 5_000.0;
        @Comment("No Longer Exist: Air taken each tick")
        public int vacuumAirDrain = 15;
        @Comment("No Longer Exist: Ticks between suffocation hits")
        public int vacuumDamageInterval = 10;
        @Comment("No Longer Exist: Suffocation damage per hit")
        public double vacuumDamage = 2.0;
        @Comment("No Longer Exist: % of max HP added to each suffocation hit")
        public double vacuumHealthDamage = 0.05;

        @Comment("The Air Will Restrain You: Range")
        public double restrainRange = 25.0;
        @Comment("The Air Will Restrain You: Seconds the cage traps the target")
        public int restrainSeconds = 7;
        @Comment("The Air Will Restrain You: Cooldown in seconds")
        public int restrainCooldown = 15;
        @Comment("The Air Will Restrain You: Aura cost")
        public double restrainAuraCost = 1_000.0;

        @Comment("The Air Will Explode: Range")
        public double explodeRange = 45.0;
        @Comment("The Air Will Explode: Width of the explosion")
        public double explodeSize = 10.0;
        @Comment("The Air Will Explode: Wind damage")
        public double explodeDamage = 150.0;
        @Comment("The Air Will Explode: Cooldown in seconds")
        public int explodeCooldown = 2;
        @Comment("The Air Will Explode: Aura cost")
        public double explodeAuraCost = 500.0;

        @Comment("The Air Will Protect Me: Barrier HP")
        public double protectHealth = 500.0;
        @Comment("The Air Will Protect Me: Barrier HP with mastery")
        public double protectHealthMastered = 1_000.0;
        @Comment("The Air Will Protect Me: Cooldown in seconds")
        public int protectCooldown = 30;
        @Comment("The Air Will Protect Me: Aura cost")
        public double protectAuraCost = 2_000.0;

        @Comment("The Air Is My Weapon: % of physical damage each punch deals as wind damage")
        public double weaponDamagePercent = 0.75;
        @Comment("The Air Is My Weapon: Arms")
        public int weaponArms = 6;
        @Comment("The Air Is My Weapon: Punches each arm throws")
        public int weaponPunches = 2;
        @Comment("The Air Is My Weapon: Range")
        public double weaponRange = 6.0;
        @Comment("The Air Is My Weapon: Cooldown in seconds")
        public int weaponCooldown = 6;
        @Comment("The Air Is My Weapon: Aura cost")
        public double weaponAuraCost = 1_500.0;

        @Comment("Enhance My Form: Melee damage added")
        public double enhanceAttackDamage = 250.0;
        @Comment("Enhance My Form: Armor added")
        public double enhanceArmor = 70.0;
        @Comment("Enhance My Form: Size added")
        public double enhanceScale = 0.25;
        @Comment("Enhance My Form: % of damage added to every rule's attacks")
        public double enhanceRuleDamagePercent = 0.5;
        @Comment("Enhance My Form: Body Of Air damage reduction")
        public double enhanceBodyDamageReduction = 0.5;

        @Comment("Soul Will Not Degrade: Soul damage reduction")
        public double soulDamageMultiplier = 0.5;

        @Comment("To Become Unseen: Presence concealment added")
        public double unseenConcealment = 2.0;
        @Comment("To See All: Presence sense added")
        public double seeAllPresenceSense = 2.0;

        @Comment("My Body Will Heal: Infinite Regeneration's magicule cost reduction")
        public double healCostReduction = 0.5;

        @Comment("Weakness: % of the target's damage taken away")
        public double weaknessDamageReduction = 0.15;

        @Comment("Crumble: % of the target's armor taken away")
        public double crumbleArmorReduction = 0.5;

        @Comment("Can Not Move: Seconds before the rule ends, just for balancing")
        public int noMoveSeconds = 15;

        @Comment("Will Die: Multiplier of the target's EP the user needs to use")
        public double willDieEpMultiplier = 5.0;

        @Comment("New Order Rebel: Seconds it keeps destroying quirks, one per second")
        public int rebelSeconds = 60;

        @Comment("Become My Shield: Range a projectile must be to create a wall")
        public double shieldRange = 10.0;
        @Comment("Become My Shield: Aura cost per wall")
        public double shieldAuraCost = 200.0;
        @Comment("Become My Shield: Seconds a wall last")
        public int shieldWallSeconds = 3;
        @Comment("Become My Shield: Wall height")
        public int shieldWallHeight = 3;
        @Comment("Become My Shield: Wall width")
        public int shieldWallWidth = 3;

        @Comment("Become My Fist: % of the user's melee damage each of the two fists deals as earth damage")
        public double fistDamageMultiplier = 0.75;
        @Comment("Become My Fist: Cooldown in seconds")
        public int fistCooldown = 1;

        @Comment("The Ground Is Slippery: Width of the area")
        public double slipperySize = 50.0;

        @Comment("The Ground Is Elastic: Width of the area")
        public double elasticSize = 50.0;
        @Comment("The Ground Is Elastic: Jump strength added")
        public double elasticJumpStrength = 0.3;

        @Comment("The Ground Isn't Affected By Gravity: Width of the area")
        public double noFallSize = 50.0;

        @Comment("The Ground Is Spiky: Range")
        public double spikyRange = 30.0;
        @Comment("The Ground Is Spiky: Spikes")
        public int spikyCount = 1;
        @Comment("The Ground Is Spiky: Spikes with mastery")
        public int spikyCountMastered = 3;
        @Comment("The Ground Is Spiky: Earth damage per spike")
        public double spikyDamage = 150.0;
        @Comment("The Ground Is Spiky: Earth damage per spike with mastery")
        public double spikyDamageMastered = 250.0;
        @Comment("The Ground Is Spiky: Cooldown in seconds")
        public int spikyCooldown = 6;
        @Comment("The Ground Is Spiky: Aura cost")
        public double spikyAuraCost = 500.0;

        @Comment("Thunder Strike: Aura cost")
        public double thunderAuraCost = 3_000.0;
        @Comment("Thunder Strike: Cooldown in seconds")
        public int thunderCooldown = 10;
        @Comment("Thunder Strike: Lightning damage")
        public double thunderDamage = 400.0;
        @Comment("Thunder Strike: Range")
        public double thunderRange = 40.0;

        @Comment("Solar Beam: Aura cost")
        public double solarAuraCost = 3_000.0;
        @Comment("Solar Beam: Cooldown in seconds")
        public int solarCooldown = 15;
        @Comment("Solar Beam: Damage")
        public double solarBeamDamage = 100.0;
        @Comment("Solar Beam: Range in blocks")
        public double solarBeamRange = 30.0;
        @Comment("Solar Beam: Seconds the beam fires")
        public int solarBeamSeconds = 4;

        @Comment("Body of Air: Aura cost")
        public double bodyAuraCost = 2_000.0;
        @Comment("Body of Air: Multiplier of the player's HP the construct has")
        public double bodyHealthMultiplier = 10.0;
        @Comment("Body of Air: Cooldown in seconds after the construct is broken")
        public int bodyBreakCooldown = 180;
        @Comment("Body of Air: Cooldown in seconds after exiting")
        public int bodyExitCooldown = 5;
        @Comment("Body of Air: Wind boost added while inside")
        public double bodyWindBoost = 1.0;
        @Comment("Body of Air: Seconds between the construct healing itself")
        public int bodyRegenSeconds = 5;
        @Comment("Body of Air: % of the construct's max HP healed")
        public double bodyRegenPercent = 0.05;
        @Comment("Body of Air: Seconds out of combat before the construct starts healing")
        public int bodyCombatSeconds = 10;

        @Comment("Fist Bump To The Earth: Aura cost")
        public double fistBumpAuraCost = 4_000.0;
        @Comment("Fist Bump To The Earth: Cooldown in seconds")
        public int fistBumpCooldown = 15;
        @Comment("Fist Bump To The Earth: % of physical damage dealt as wind damage")
        public double fistBumpDamagePercent = 150.0;
        @Comment("Fist Bump To The Earth: Width of the explosion")
        public double fistBumpSize = 25.0;
        @Comment("Fist Bump To The Earth: Height of the explosion")
        public double fistBumpHeight = 15.0;
        @Comment("Fist Bump To The Earth: Shatters the ground?")
        public boolean fistBumpBreaksBlocks = true;

        @Comment("Air Spear: Aura cost")
        public double spearAuraCost = 3_000.0;
        @Comment("Air Spear: Cooldown in seconds")
        public int spearCooldown = 15;
        @Comment("Air Spear: Wind damage")
        public double spearDamage = 750.0;
        @Comment("Air Spear: Width of the area the spear hits")
        public double spearSize = 5.0;
        @Comment("Air Spear: Width of the area the spear hits with mastery")
        public double spearSizeMastered = 8.0;
        @Comment("Air Spear: Width of the shockwave")
        public double spearShockwaveSize = 15.0;
        @Comment("Air Spear: Width of the shockwave with mastery")
        public double spearShockwaveSizeMastered = 24.0;
        @Comment("Air Spear: % of the spear's damage the shockwave deals")
        public double spearShockwaveDamagePercent = 0.75;

        @Comment("Wind Clap: Aura cost")
        public double clapAuraCost = 3_000.0;
        @Comment("Wind Clap: Cooldown in seconds")
        public int clapCooldown = 15;
        @Comment("Wind Clap: Wind damage")
        public double clapDamage = 600.0;
        @Comment("Wind Clap: Range")
        public double clapRange = 15.0;
        @Comment("Wind Clap: Blocks the target is knocked away")
        public double clapKnockback = 25.0;

        public NewOrder() {
        }
    }
}

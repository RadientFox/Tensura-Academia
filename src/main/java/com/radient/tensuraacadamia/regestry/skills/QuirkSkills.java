package com.radient.tensuraacadamia.regestry.skills;

import com.radient.tensuraacadamia.ability.ultimate.ofaembers.OFA1st_Embers;
import com.radient.tensuraacadamia.ability.ultimate.ofausers.OFA2nd;
import com.radient.tensuraacadamia.ability.unique.quirks.DangerSenseQuirk;
import com.radient.tensuraacadamia.ability.ultimate.ofausers.OFA1st;
import com.radient.tensuraacadamia.ability.ultimate.AllForOne;
import com.radient.tensuraacadamia.ability.unique.quirks.*;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.impl.SkillRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class QuirkSkills {


    public static final RegistrySupplier<OFA1st> OFA_1ST = register("one_for_all_1", OFA1st::new);
    public static final RegistrySupplier<OFA1st_Embers> OFA1st_Embers = register("one_for_all_1_embers", OFA1st_Embers::new);
    public static final RegistrySupplier<OFA2nd> OFA_2ND = register("one_for_all_2", OFA2nd::new);


    public static final RegistrySupplier<Power_Stock> POWER_STOCK = register("power_stock", Power_Stock::new);
    public static final RegistrySupplier<QuirkBestowal> QUIRK_BESTOWAL = register("quirk_bestowal", QuirkBestowal::new);
    public static final RegistrySupplier<FloatQuirk> FLOAT = register("float", FloatQuirk::new);
    public static final RegistrySupplier<GearshiftQuirk> GEARSHIFT = register("gearshift", GearshiftQuirk::new);
    public static final RegistrySupplier<EngineQuirk> ENGINE = register("engine", EngineQuirk::new);
    public static final RegistrySupplier<Bloodcurdle> BLOODCURDLE = register("bloodcurdle", Bloodcurdle::new);
    public static final RegistrySupplier<DangerSenseQuirk> DANGERSENSE = register("dangersense", DangerSenseQuirk::new);
    public static final RegistrySupplier<CopyQuirk> COPY = register("copy", CopyQuirk::new);
    public static final RegistrySupplier<AllForOne> ALL_FOR_ONE = register("all_for_one", AllForOne::new);
    public static final RegistrySupplier<Blackwhip> BLACKWHIP = register("blackwhip", Blackwhip::new);
    public static final RegistrySupplier<ElectrificationQuirk> ELECTRIFICATION = register("electrification", ElectrificationQuirk::new);
    public static final RegistrySupplier<SmokescreenQuirk> SMOKESCREEN = register("smokescreen", SmokescreenQuirk::new);
    public static final RegistrySupplier<DelaySpotQuirk> DELAYSPOT = register("delayspot", DelaySpotQuirk::new);
    public static final RegistrySupplier<MuscleAugmentationQuirk> MUSCLE_AUGMENTATION = register("muscle_augmentation", MuscleAugmentationQuirk::new);
    public static final RegistrySupplier<FatAbsorptionQuirk> FAT_ABSORPTION = register("fat_absorption", FatAbsorptionQuirk::new);
    public static final RegistrySupplier<ExplosionQuirk> EXPLOSION = register("explosion", ExplosionQuirk::new);
    public static final RegistrySupplier<HalfColdHalfHot> HALF_COLD_HALF_HOT = register("half_cold_half_hot", HalfColdHalfHot::new);
    public static final RegistrySupplier<VoiceQuirk> VOICE = register("voice", VoiceQuirk::new);
    public static final RegistrySupplier<ElasticityQuirk> ELASTICITY = register("elasticity", ElasticityQuirk::new);
    public static final RegistrySupplier<QuadArmsQuirk> QUAD_ARMS = register("quad_arms", QuadArmsQuirk::new);
    public static final RegistrySupplier<ImpactRecoilQuirk> IMPACT_RECOIL = register("impact_recoil", ImpactRecoilQuirk::new);
    public static final RegistrySupplier<WeatherManipulationQuirk> WEATHER_MANIPULATION = register("weather_manipulation", WeatherManipulationQuirk::new);
    public static final RegistrySupplier<StrongarmQuirk> STRONGARM = register("strongarm", StrongarmQuirk::new);
    public static final RegistrySupplier<KineticBoosterQuirk> KINETIC_BOOSTER = register("kinetic_booster", KineticBoosterQuirk::new);
    public static final RegistrySupplier<SpringlikeLimbsQuirk> SPRINGLIKE_LIMBS = register("springlike_limbs", SpringlikeLimbsQuirk::new);
    public static final RegistrySupplier<AcceleratorRingsQuirk> ACCELERATOR_RINGS = register("accelerator_rings", AcceleratorRingsQuirk::new);
    public static final RegistrySupplier<PermeationQuirk> PERMEATION = register("permeation", PermeationQuirk::new);
    public static final RegistrySupplier<PerilDiffusionQuirk> PERIL_DIFFUSION = register("peril_diffusion", PerilDiffusionQuirk::new);
    public static final RegistrySupplier<AbsorbAndReleaseQuirk> ABSORB_AND_RELEASE = register("absorb_and_release", AbsorbAndReleaseQuirk::new);
    public static final RegistrySupplier<RuptureQuirk> RUPTURE = register("rupture", RuptureQuirk::new);
    public static final RegistrySupplier<BurstQuirk> BURST = register("burst", BurstQuirk::new);
    public static final RegistrySupplier<LifeForceQuirk> LifeForce = register("life_force", LifeForceQuirk::new);
    public static final RegistrySupplier<MultiplierQuirk> MULTIPLIER = register("multiplier", MultiplierQuirk::new);
    public static final RegistrySupplier<MoleQuirk> MOLE = register("mole", MoleQuirk::new);
    public static final RegistrySupplier<ShockAbsorptionQuirk> SHOCK_ABSORPTION = register("shock_absorption", ShockAbsorptionQuirk::new);
    public static final RegistrySupplier<TelekinesisQuirk> TELEKINESIS = register("telekinesis", TelekinesisQuirk::new);
    public static final RegistrySupplier<ScanningQuirk> SCANNING = register("scanning", ScanningQuirk::new);
    public static final RegistrySupplier<NeutralizationQuirk> NEUTRALIZATION = register("neutralization", NeutralizationQuirk::new);
    public static final RegistrySupplier<InfraredQuirk> INFRARED = register("infrared", InfraredQuirk::new);
    public static final RegistrySupplier<AttractionQuirk> ATTRACTION = register("attraction", AttractionQuirk::new);
    public static final RegistrySupplier<VibrationDetectionQuirk> VIBRATION_DETECTION = register("vibration_detection", VibrationDetectionQuirk::new);
    public static final RegistrySupplier<VibrateQuirk> VIBRATE = register("vibrate", VibrateQuirk::new);
    public static final RegistrySupplier<DupliArmsQuirk> DUPLI_ARMS = register("dupli_arms", DupliArmsQuirk::new);
    public static final RegistrySupplier<BodyMorphQuirk> BODY_MORPH = register("body_morph", BodyMorphQuirk::new);
    public static final RegistrySupplier<ExtraArmsQuirk> EXTRA_ARMS = register("extra_arms", ExtraArmsQuirk::new);
    public static final RegistrySupplier<TailQuirk> TAIL = register("tail", TailQuirk::new);
    public static final RegistrySupplier<WoodenSwordsQuirk> WOODEN_SWORDS = register("wooden_swords_from_his_hands", WoodenSwordsQuirk::new);
    public static final RegistrySupplier<BeamsFromHisEyesQuirk> BEAMS_FROM_HIS_EYES = register("beams_from_his_eyes", BeamsFromHisEyesQuirk::new);
    public static final RegistrySupplier<ImpureBeamQuirk> IMPURE_BEAM = register("impure_beam", ImpureBeamQuirk::new);
    public static final RegistrySupplier<DoubleQuirk> DOUBLE = register("double", DoubleQuirk::new);
    public static final RegistrySupplier<QueenBeamQuirk> QUEEN_BEAM = register("queen_beam", QueenBeamQuirk::new);
    public static final RegistrySupplier<PopOffQuirk> POP_OFF = register("pop_off", PopOffQuirk::new);
    public static final RegistrySupplier<WhirlwindQuirk> WHIRLWIND = register("whirlwind", WhirlwindQuirk::new);
    public static final RegistrySupplier<HomingQuirk> HOMING = register("homing", HomingQuirk::new);
    public static final RegistrySupplier<SolidAirQuirk> SOLID_AIR = register("solid_air", SolidAirQuirk::new);
    public static final RegistrySupplier<VinesQuirk> VINES = register("vines", VinesQuirk::new);
    public static final RegistrySupplier<BlastQuirk> BLAST = register("blast", BlastQuirk::new);
    public static final RegistrySupplier<DarkShadowQuirk> DARK_SHADOW = register("dark_shadow", DarkShadowQuirk::new);
    public static final RegistrySupplier<AlchemyQuirk> ALCHEMY = register("alchemy", AlchemyQuirk::new);
    public static final RegistrySupplier<IQQuirk> IQ = register("iq", IQQuirk::new);
    public static final RegistrySupplier<TapeQuirk> TAPE = register("tape", TapeQuirk::new);
    public static final RegistrySupplier<CloudQuirk> CLOUD = register("cloud", CloudQuirk::new);
    public static final RegistrySupplier<HellflameQuirk> HELLFLAME = register("hellflame", HellflameQuirk::new);
    public static final RegistrySupplier<TeleportationQuirk> TELEPORTATION = register("teleportation", TeleportationQuirk::new);
    public static final RegistrySupplier<WarpGateQuirk> WARP_GATE = register("warp_gate", WarpGateQuirk::new);
    public static final RegistrySupplier<LeafipulationQuirk> LEAFIPULATION = register("leafipulation", LeafipulationQuirk::new);
    public static final RegistrySupplier<OverhaulQuirk> OVERHAUL = register("overhaul", OverhaulQuirk::new);





    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(ResourceLocation.fromNamespaceAndPath("tracadamia", name), supplier);
    }

    public QuirkSkills() {
    }


    public static void init() {
    }


}

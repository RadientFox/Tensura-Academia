package com.radient.tensuraacadamia.regestry.skills;

import com.radient.tensuraacadamia.ability.unique.quirks.AbsorbAndReleaseQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.AttractionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.Blackwhip;
import com.radient.tensuraacadamia.ability.unique.quirks.BodyBulkQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.BodyMorphQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.BruiserQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.BurstQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.CopyQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.DelaySpotQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.DupliArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.EnduranceQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ExtraArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.FatAbsorptionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.FloatQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.GearshiftQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.GigantificationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ImpactRecoilQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.InfraredQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.JetQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.KineticBoosterQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.LandmineQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.LifeForceQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.MoleQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.MultiplierQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.MuscleAugmentationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.NeutralizationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.Power_Stock;
import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.QuirkBestowal;
import com.radient.tensuraacadamia.ability.unique.quirks.RuptureQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ScanningQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ShockAbsorptionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SpringlikeLimbsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.StrongarmQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.TailQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.TelekinesisQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.TransformingArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.VibrateQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.VibrationDetectionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.WeatherManipulationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ZeroGravityQuirk;
import com.radient.tensuraacadamia.quirks.accelerator_rings.AcceleratorRingsQuirk;
import com.radient.tensuraacadamia.quirks.acid_sweat.AcidSweatQuirk;
import com.radient.tensuraacadamia.quirks.acid.AcidQuirk;
import com.radient.tensuraacadamia.quirks.alchemy.AlchemyQuirk;
import com.radient.tensuraacadamia.quirks.arbor.ArborQuirk;
import com.radient.tensuraacadamia.quirks.assault_dust.AssaultDustQuirk;
import com.radient.tensuraacadamia.quirks.barrier.BarrierQuirk;
import com.radient.tensuraacadamia.quirks.bat.BatQuirk;
import com.radient.tensuraacadamia.quirks.beams_from_his_eyes.BeamsFromHisEyesQuirk;
import com.radient.tensuraacadamia.quirks.blast.BlastQuirk;
import com.radient.tensuraacadamia.quirks.bloodcurdle.Bloodcurdle;
import com.radient.tensuraacadamia.quirks.cloud.CloudQuirk;
import com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowQuirk;
import com.radient.tensuraacadamia.quirks.double_quirk.DoubleQuirk;
import com.radient.tensuraacadamia.quirks.elasticity.ElasticityQuirk;
import com.radient.tensuraacadamia.quirks.electrification.ElectrificationQuirk;
import com.radient.tensuraacadamia.quirks.engine.EngineQuirk;
import com.radient.tensuraacadamia.quirks.explosion.ExplosionQuirk;
import com.radient.tensuraacadamia.quirks.half_cold_half_hot.HalfColdHalfHot;
import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.quirks.impure_beam.ImpureBeamQuirk;
import com.radient.tensuraacadamia.quirks.iq.IQQuirk;
import com.radient.tensuraacadamia.quirks.leafipulation.LeafipulationQuirk;
import com.radient.tensuraacadamia.quirks.overhaul.OverhaulQuirk;
import com.radient.tensuraacadamia.quirks.peril_diffusion.PerilDiffusionQuirk;
import com.radient.tensuraacadamia.quirks.permeation.PermeationQuirk;
import com.radient.tensuraacadamia.quirks.pop_off.PopOffQuirk;
import com.radient.tensuraacadamia.quirks.queen_beam.QueenBeamQuirk;
import com.radient.tensuraacadamia.quirks.smokescreen.SmokescreenQuirk;
import com.radient.tensuraacadamia.quirks.solid_air.SolidAirQuirk;
import com.radient.tensuraacadamia.quirks.super_regeneration.SuperRegenerationQuirk;
import com.radient.tensuraacadamia.quirks.tape.TapeQuirk;
import com.radient.tensuraacadamia.quirks.teleportation.TeleportationQuirk;
import com.radient.tensuraacadamia.quirks.vines.VinesQuirk;
import com.radient.tensuraacadamia.quirks.voice.VoiceQuirk;
import com.radient.tensuraacadamia.quirks.warp_gate.WarpGateQuirk;
import com.radient.tensuraacadamia.quirks.whirlwind.WhirlwindQuirk;
import com.radient.tensuraacadamia.quirks.wooden_swords.WoodenSwordsQuirk;

import com.radient.tensuraacadamia.ability.ultimate.ofaembers.OFA1st_Embers;
import com.radient.tensuraacadamia.ability.ultimate.ofaembers.OFA2nd_Embers;
import com.radient.tensuraacadamia.ability.ultimate.ofausers.*;
import com.radient.tensuraacadamia.ability.unique.quirks.DangerSenseQuirk;
import com.radient.tensuraacadamia.ability.ultimate.ofausers.OFA1st;
import com.radient.tensuraacadamia.quirks.all_for_one.AllForOne;
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
    public static final RegistrySupplier<OFA2nd_Embers> OFA2nd_Embers = register("one_for_all_2_embers", OFA2nd_Embers::new);
    public static final RegistrySupplier<OFA3rd> OFA_3RD = register("one_for_all_3", OFA3rd::new);

    public static final RegistrySupplier<OFA4th> OFA_4TH = register("one_for_all_4", OFA4th::new);

    public static final RegistrySupplier<OFA5th> OFA_5TH = register("one_for_all_5", OFA5th::new);

    public static final RegistrySupplier<OFA6th> OFA_6TH = register("one_for_all_6", OFA6th::new);

    public static final RegistrySupplier<OFA7th> OFA_7TH = register("one_for_all_7", OFA7th::new);

    public static final RegistrySupplier<OFA8th> OFA_8TH = register("one_for_all_8", OFA8th::new);

    public static final RegistrySupplier<OFA9th> OFA_9TH = register("one_for_all_9", OFA9th::new);


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
    public static final RegistrySupplier<TeleportationQuirk> TELEPORTATION = register("teleportation", TeleportationQuirk::new);
    public static final RegistrySupplier<WarpGateQuirk> WARP_GATE = register("warp_gate", WarpGateQuirk::new);
    public static final RegistrySupplier<LeafipulationQuirk> LEAFIPULATION = register("leafipulation", LeafipulationQuirk::new);
    public static final RegistrySupplier<OverhaulQuirk> OVERHAUL = register("overhaul", OverhaulQuirk::new);
    public static final RegistrySupplier<ZeroGravityQuirk> ZERO_GRAVITY = register("zero_gravity", ZeroGravityQuirk::new);
    public static final RegistrySupplier<BodyBulkQuirk> BODY_BULK = register("body_bulk", BodyBulkQuirk::new);
    public static final RegistrySupplier<BruiserQuirk> BRUISER = register("bruiser", BruiserQuirk::new);
    public static final RegistrySupplier<EnduranceQuirk> ENDURANCE = register("endurance", EnduranceQuirk::new);
    public static final RegistrySupplier<JetQuirk> JET = register("jet", JetQuirk::new);
    public static final RegistrySupplier<GigantificationQuirk> GIGANTIFICATION = register("gigantification", GigantificationQuirk::new);
    public static final RegistrySupplier<TransformingArmsQuirk> TRANSFORMING_ARMS = register("transforming_arms", TransformingArmsQuirk::new);
    public static final RegistrySupplier<LandmineQuirk> LANDMINE = register("landmine", LandmineQuirk::new);
    public static final RegistrySupplier<AssaultDustQuirk> ASSAULT_DUST = register("assault_dust", AssaultDustQuirk::new);
    public static final RegistrySupplier<ArborQuirk> ARBOR = register("arbor", ArborQuirk::new);
    public static final RegistrySupplier<AcidQuirk> ACID = register("acid", AcidQuirk::new);
    public static final RegistrySupplier<AcidSweatQuirk> ACID_SWEAT = register("acid_sweat", AcidSweatQuirk::new);
    public static final RegistrySupplier<BarrierQuirk> BARRIER = register("barrier", BarrierQuirk::new);
    public static final RegistrySupplier<BatQuirk> BAT = register("bat", BatQuirk::new);
    public static final RegistrySupplier<SuperRegenerationQuirk> SUPER_REGENERATION = register("super_regeneration", SuperRegenerationQuirk::new);
    public static final RegistrySupplier<DecayQuirk> DECAY = register("decay", DecayQuirk::new);


    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(ResourceLocation.fromNamespaceAndPath("tracadamia", name), supplier);
    }

    public QuirkSkills() {
    }


    public static void init() {
    }


}

package com.radient.tensuraacadamia.regestry.skills;

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


    public static final RegistrySupplier<Power_Stock> POWER_STOCK = register("power_stock", Power_Stock::new);
    public static final RegistrySupplier<QuirkBestowal> QUIRK_BESTOWAL = register("quirk_bestowal", QuirkBestowal::new);
    public static final RegistrySupplier<FloatQuirk> FLOAT = register("float", FloatQuirk::new);
    public static final RegistrySupplier<GearshiftQuirk> GEARSHIFT = register("gearshift", GearshiftQuirk::new);
    public static final RegistrySupplier<Bloodcurdle> BLOODCURDLE = register("bloodcurdle", Bloodcurdle::new);
    public static final RegistrySupplier<DangerSenseQuirk> DANGERSENSE = register("dangersense", DangerSenseQuirk::new);
    public static final RegistrySupplier<CopyQuirk> COPY = register("copy", CopyQuirk::new);
    public static final RegistrySupplier<AllForOne> ALL_FOR_ONE = register("all_for_one", AllForOne::new);
    public static final RegistrySupplier<Blackwhip> BLACKWHIP = register("blackwhip", Blackwhip::new);
    public static final RegistrySupplier<ElectrificationQuirk> ELECTRIFICATION = register("electrification", ElectrificationQuirk::new);
    public static final RegistrySupplier<SmokescreenQuirk> SMOKESCREEN = register("smokescreen", SmokescreenQuirk::new);
    public static final RegistrySupplier<DelaySpotQuirk> DELAYSPOT = register("delayspot", DelaySpotQuirk::new);
    public static final RegistrySupplier<DangerSenseQuirk2> DANGER_SENSE2 = register("danger_sense2", DangerSenseQuirk2::new);
    public static final RegistrySupplier<MuscleAugmentationQuirk> MUSCLE_AUGMENTATION = register("muscle_augmentation", MuscleAugmentationQuirk::new);
    public static final RegistrySupplier<FatAbsorptionQuirk> FAT_ABSORPTION = register("fat_absorption", FatAbsorptionQuirk::new);
    public static final RegistrySupplier<ExplosionQuirk> EXPLOSION = register("explosion", ExplosionQuirk::new);
    public static final RegistrySupplier<HalfColdHalfHot> HALF_COLD_HALF_HOT = register("half_cold_half_hot", HalfColdHalfHot::new);
    public static final RegistrySupplier<VoiceQuirk> VOICE = register("voice", VoiceQuirk::new);
    public static final RegistrySupplier<ElasticityQuirk> ELASTICITY = register("elasticity", ElasticityQuirk::new);
    public static final RegistrySupplier<QuadArmsQuirk> QUAD_ARMS = register("quad_arms", QuadArmsQuirk::new);
    public static final RegistrySupplier<ImpactRecoilQuirk> IMPACT_RECOIL = register("impact_recoil", ImpactRecoilQuirk::new);
    public static final RegistrySupplier<WeatherManipulationQuirk> WEATHER_MANIPULATION = register("weather_manipulation", WeatherManipulationQuirk::new);




    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(ResourceLocation.fromNamespaceAndPath("tracadamia", name), supplier);
    }

    public QuirkSkills() {
    }


    public static void init() {
    }


}

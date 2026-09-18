package com.radient.tensuraacadamia.regestry.skills;

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

    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(ResourceLocation.fromNamespaceAndPath("tracadamia", name), supplier);
    }

    public QuirkSkills() {
    }


    public static void init() {
    }


}

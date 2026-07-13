package com.radient.tensuraacadamia.regestry.skills;

import com.radient.tensuraacadamia.ability.unique.quirks.FloatQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.Power_Stock;
import com.radient.tensuraacadamia.ability.unique.quirks.QuirkBestowal;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.impl.SkillRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class QuirkSkills {

    public static final RegistrySupplier<Power_Stock> POWER_STOCK = register("power_stock", Power_Stock::new);
    public static final RegistrySupplier<QuirkBestowal> QUIRK_BESTOWAL = register("quirk_bestowal", QuirkBestowal::new);
    public static final RegistrySupplier<FloatQuirk> FLOAT = register("float", FloatQuirk::new);


    private static <E extends ManasSkill> RegistrySupplier<E> register(String name, Supplier<E> supplier) {
        return SkillRegistry.SKILLS.register(ResourceLocation.fromNamespaceAndPath("tensuraacadamia", name), supplier);
    }

    public QuirkSkills() {
    }


    public static void init() {
    }


}

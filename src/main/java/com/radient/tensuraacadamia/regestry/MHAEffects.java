package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.effects.*;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MHAEffects {


    public static final DeferredRegister<MobEffect> MOB_EFFECTS;
    private static final Map<RegistrySupplier<MobEffect>, Holder<MobEffect>> HOLDER_CACHE;
    public static final DeferredHolder<MobEffect, MobEffect> OTHERSINSPIRE;
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING;
    public static final DeferredHolder<MobEffect, MobEffect> BLOOD_PARALYSIS;
    public static final DeferredHolder<MobEffect, MobEffect> BAD_TASTE;
    public static final DeferredHolder<MobEffect, MobEffect> COALGULATION;
    public static final DeferredHolder<MobEffect, MobEffect> QUIRK_SICKNESS;
    public static final DeferredHolder<MobEffect, MobEffect> ELECTRIC_BOOST;
    public static final DeferredHolder<MobEffect, MobEffect> WATTAGE;
    public static final DeferredHolder<MobEffect, MobEffect> ASPERSION;
    public static final DeferredHolder<MobEffect, MobEffect> SMOKESCREEN_OBSCURED;
    public static final DeferredHolder<MobEffect, MobEffect> BOUNCE;
    public static final DeferredHolder<MobEffect, MobEffect> SPLINTER;
    public static final DeferredHolder<MobEffect, MobEffect> POP;
    public static final DeferredHolder<MobEffect, MobEffect> TAILWIND;
    public static final DeferredHolder<MobEffect, MobEffect> WIND_FLIGHT;
    public static final DeferredHolder<MobEffect, MobEffect> DARK_SHADOW_FLIGHT;
    public static final DeferredHolder<MobEffect, MobEffect> AFFECTION;


    public MHAEffects() {
    }

    public static void register(IEventBus bus) {
        MOB_EFFECTS.register(bus);
    }


    static {
        MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "tracadamia");
        HOLDER_CACHE = new ConcurrentHashMap();
        OTHERSINSPIRE = MOB_EFFECTS.register("others_inspiration", ToInspireOthersEffect::new);
        BLEEDING = MOB_EFFECTS.register("bleeding", BleedingEffect::new);
        BLOOD_PARALYSIS = MOB_EFFECTS.register("blood_paralysis", BloodParalysisEffect::new);
        BAD_TASTE = MOB_EFFECTS.register("bad_taste", BadTasteEffect::new);
        COALGULATION = MOB_EFFECTS.register("coalgulation", CoalgulationEffect::new);
        QUIRK_SICKNESS = MOB_EFFECTS.register("quirk_sickness", QuirkSicknessEffect::new);
        ELECTRIC_BOOST = MOB_EFFECTS.register("electric_boost", ElectricBoostEffect::new);
        WATTAGE = MOB_EFFECTS.register("wattage", WattageEffect::new);
        ASPERSION = MOB_EFFECTS.register("aspersion", AspersionEffect::new);
        SMOKESCREEN_OBSCURED = MOB_EFFECTS.register("smokescreen_obscured", SmokescreenObscuredEffect::new);
        BOUNCE = MOB_EFFECTS.register("bounce", BounceEffect::new);
        SPLINTER = MOB_EFFECTS.register("splinter", SplinterEffect::new);
        POP = MOB_EFFECTS.register("pop", PopEffect::new);
        TAILWIND = MOB_EFFECTS.register("tailwind", () -> new TailwindEffect(false));
        WIND_FLIGHT = MOB_EFFECTS.register("wind_flight", () -> new TailwindEffect(true));
        DARK_SHADOW_FLIGHT = MOB_EFFECTS.register("dark_shadow_flight", com.radient.tensuraacadamia.effects.DarkShadowFlightEffect::new);
        AFFECTION = MOB_EFFECTS.register("affection", Affection::new);
    }

}

package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.effects.Affection;
import com.radient.tensuraacadamia.effects.BadTasteEffect;
import com.radient.tensuraacadamia.effects.QuirkSicknessEffect;
import com.radient.tensuraacadamia.effects.ToInspireOthersEffect;
import com.radient.tensuraacadamia.quirks.acid.AcidCoatingEffect;
import com.radient.tensuraacadamia.quirks.acid.AcidEffect;
import com.radient.tensuraacadamia.quirks.assault_dust.CompressedDustEffect;
import com.radient.tensuraacadamia.quirks.assault_dust.DustyEffect;
import com.radient.tensuraacadamia.quirks.bat.BatlikeEffect;
import com.radient.tensuraacadamia.quirks.bloodcurdle.BleedingEffect;
import com.radient.tensuraacadamia.quirks.bloodcurdle.BloodParalysisEffect;
import com.radient.tensuraacadamia.quirks.bloodcurdle.CoalgulationEffect;
import com.radient.tensuraacadamia.quirks.electrification.ElectricBoostEffect;
import com.radient.tensuraacadamia.quirks.electrification.WattageEffect;
import com.radient.tensuraacadamia.quirks.leafipulation.LeafAntiHealingEffect;
import com.radient.tensuraacadamia.quirks.pop_off.PopEffect;
import com.radient.tensuraacadamia.quirks.smokescreen.AspersionEffect;
import com.radient.tensuraacadamia.quirks.smokescreen.SmokescreenObscuredEffect;
import com.radient.tensuraacadamia.quirks.vines.BounceEffect;
import com.radient.tensuraacadamia.quirks.warp_gate.WarpOveruseEffect;
import com.radient.tensuraacadamia.quirks.whirlwind.TailwindEffect;
import com.radient.tensuraacadamia.quirks.wooden_swords.SplinterEffect;

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
    public static final DeferredHolder<MobEffect, MobEffect> IRON_LOCK;
    public static final DeferredHolder<MobEffect, MobEffect> WARP_OVERUSE;
    public static final DeferredHolder<MobEffect, MobEffect> LEAF_ANTI_HEALING;
    public static final DeferredHolder<MobEffect, MobEffect> DUSTY;
    public static final DeferredHolder<MobEffect, MobEffect> COMPRESSED_DUST;
    public static final DeferredHolder<MobEffect, MobEffect> ACID;
    public static final DeferredHolder<MobEffect, MobEffect> ACID_LAYBACK;
    public static final DeferredHolder<MobEffect, MobEffect> ACIDMAN;
    public static final DeferredHolder<MobEffect, MobEffect> BATLIKE;
    public static final DeferredHolder<MobEffect, MobEffect> BAT_REST;


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
        DARK_SHADOW_FLIGHT = MOB_EFFECTS.register("dark_shadow_flight", com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowFlightEffect::new);
        AFFECTION = MOB_EFFECTS.register("affection", Affection::new);
        IRON_LOCK = MOB_EFFECTS.register("iron_lock", com.radient.tensuraacadamia.quirks.alchemy.IronLockEffect::new);
        WARP_OVERUSE = MOB_EFFECTS.register("warp_overuse", WarpOveruseEffect::new);
        LEAF_ANTI_HEALING = MOB_EFFECTS.register("leaf_anti_healing", LeafAntiHealingEffect::new);
        DUSTY = MOB_EFFECTS.register("dusty", DustyEffect::new);
        COMPRESSED_DUST = MOB_EFFECTS.register("compressed_dust", CompressedDustEffect::new);
        ACID = MOB_EFFECTS.register("acid", AcidEffect::new);
        ACID_LAYBACK = MOB_EFFECTS.register("acid_layback", () -> new AcidCoatingEffect(true));
        ACIDMAN = MOB_EFFECTS.register("acidman", () -> new AcidCoatingEffect(false));
        BATLIKE = MOB_EFFECTS.register("batlike", () -> new BatlikeEffect(false));
        BAT_REST = MOB_EFFECTS.register("bat_rest", () -> new BatlikeEffect(true));
    }

}

package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.effects.ToInspireOthersEffect;
import com.radient.tensuraacadamia.effects.BleedingEffect;
import com.radient.tensuraacadamia.effects.BloodParalysisEffect;
import com.radient.tensuraacadamia.effects.BadTasteEffect;
import com.radient.tensuraacadamia.effects.CoalgulationEffect;
import com.radient.tensuraacadamia.effects.QuirkSicknessEffect;
import com.radient.tensuraacadamia.effects.ElectricBoostEffect;
import com.radient.tensuraacadamia.effects.WattageEffect;
import com.radient.tensuraacadamia.effects.AspersionEffect;
import com.radient.tensuraacadamia.effects.SmokescreenObscuredEffect;
import com.radient.tensuraacadamia.effects.BounceEffect;
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
    }

}

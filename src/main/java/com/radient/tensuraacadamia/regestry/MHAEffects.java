package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.effects.ToInspireOthersEffect;
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


    public MHAEffects() {
    }

    public static void register(IEventBus bus) {
        MOB_EFFECTS.register(bus);
    }


    static {
        MOB_EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, "tracadamia");
        HOLDER_CACHE = new ConcurrentHashMap();
        OTHERSINSPIRE = MOB_EFFECTS.register("others_inspiration", ToInspireOthersEffect::new);
    }

}

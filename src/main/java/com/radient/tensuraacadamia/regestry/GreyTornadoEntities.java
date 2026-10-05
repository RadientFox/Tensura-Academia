package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.GreyTornadoBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GreyTornadoEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<GreyTornadoBlade>> TORNADO_BLADE =
            ENTITIES.register("grey_tornado_blade", () -> EntityType.Builder
                    .<GreyTornadoBlade>of(GreyTornadoBlade::new, MobCategory.MISC)
                    .sized(3.0F, 4.0F).clientTrackingRange(32).updateInterval(1).noSummon()
                    .build("tracadamia:grey_tornado_blade"));

    private GreyTornadoEntities() {}

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}

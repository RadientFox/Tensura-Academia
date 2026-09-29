package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.PopOffProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PopOffEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<PopOffProjectile>> POP_OFF = ENTITIES.register("pop_off",
            () -> EntityType.Builder.<PopOffProjectile>of(PopOffProjectile::new, MobCategory.MISC)
                    .sized(0.44F, 0.44F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:pop_off"));

    private PopOffEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

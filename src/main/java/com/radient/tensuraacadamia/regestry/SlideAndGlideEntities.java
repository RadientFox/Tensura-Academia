package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.ScrappyThrustProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SlideAndGlideEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<ScrappyThrustProjectile>> SCRAPPY_THRUST = ENTITIES.register("scrappy_thrust",
            () -> EntityType.Builder.<ScrappyThrustProjectile>of(ScrappyThrustProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:scrappy_thrust"));

    private SlideAndGlideEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

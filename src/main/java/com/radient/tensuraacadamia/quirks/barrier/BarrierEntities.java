package com.radient.tensuraacadamia.quirks.barrier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BarrierEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<DomeBarrier>> DOME = ENTITIES.register("barrier_dome",
            () -> EntityType.Builder.<DomeBarrier>of(DomeBarrier::new, MobCategory.MISC).sized(3, 3)
                    .clientTrackingRange(12).updateInterval(1).fireImmune().noSummon().build("tracadamia:barrier_dome"));
    private BarrierEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

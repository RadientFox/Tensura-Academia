package com.radient.tensuraacadamia.quirks.arbor;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ArborEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<ArborConstruct>> CONSTRUCT = ENTITIES.register("arbor_construct",
            () -> EntityType.Builder.<ArborConstruct>of(ArborConstruct::new, MobCategory.MISC).sized(1, 1)
                    .clientTrackingRange(16).updateInterval(1).noSummon().build("tracadamia:arbor_construct"));

    private ArborEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(CONSTRUCT.get(), ArborConstruct.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1).build()));
    }
}

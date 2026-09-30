package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.QuirkCloud;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CloudEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<QuirkCloud>> CLOUD = ENTITIES.register("quirk_cloud",
            () -> EntityType.Builder.<QuirkCloud>of(QuirkCloud::new, MobCategory.MISC)
                    .sized(3.4F, 0.8F).clientTrackingRange(32).updateInterval(1).noSummon()
                    .build("tracadamia:quirk_cloud"));

    private CloudEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

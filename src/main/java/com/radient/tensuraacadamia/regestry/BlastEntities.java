package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.BlastProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BlastEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<BlastProjectile>> BULLET = ENTITIES.register("blast_bullet",
            () -> EntityType.Builder.<BlastProjectile>of(BlastProjectile::new, MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(10).updateInterval(1).noSummon().build("tracadamia:blast_bullet"));
    private BlastEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

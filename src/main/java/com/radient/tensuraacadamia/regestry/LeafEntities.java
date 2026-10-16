package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.LeafProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class LeafEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<LeafProjectile>> LEAF =
            ENTITIES.register("leaf_projectile", () -> EntityType.Builder
                    .<LeafProjectile>of(LeafProjectile::new, MobCategory.MISC)
                    .sized(0.22F, 0.22F).clientTrackingRange(8).updateInterval(1)
                    .noSave().noSummon().build("tracadamia:leaf_projectile"));

    private LeafEntities() { }

    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

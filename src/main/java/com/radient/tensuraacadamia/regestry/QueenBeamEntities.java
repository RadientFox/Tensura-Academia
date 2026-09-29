package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.QueenBeamProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QueenBeamEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<QueenBeamProjectile>> QUEEN_BEAM =
            ENTITIES.register("queen_beam", () -> EntityType.Builder
                    .<QueenBeamProjectile>of(QueenBeamProjectile::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1)
                    .noSave().noSummon().build("tracadamia:queen_beam"));

    private QueenBeamEntities() { }

    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

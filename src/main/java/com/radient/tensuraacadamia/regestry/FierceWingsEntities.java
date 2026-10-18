package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.FeatherProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FierceWingsEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<FeatherProjectile>> FEATHER = ENTITIES.register("feather",
            () -> EntityType.Builder.<FeatherProjectile>of(FeatherProjectile::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:feather"));

    private FierceWingsEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

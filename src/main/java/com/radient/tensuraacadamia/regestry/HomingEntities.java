package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.HomingBarrageProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HomingEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<HomingBarrageProjectile>> BARRAGE = ENTITIES.register("homing_barrage",
            () -> EntityType.Builder.<HomingBarrageProjectile>of(HomingBarrageProjectile::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(10).updateInterval(1).noSummon().build("tracadamia:homing_barrage"));
    private HomingEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); }
}

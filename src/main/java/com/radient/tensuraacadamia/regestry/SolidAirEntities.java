package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.SolidAirWall;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SolidAirEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<SolidAirWall>> WALL = ENTITIES.register("solid_air_wall",
            () -> EntityType.Builder.<SolidAirWall>of(SolidAirWall::new, MobCategory.MISC)
                    .sized(15, 15).clientTrackingRange(12).updateInterval(1).fireImmune().noSummon().build("tracadamia:solid_air_wall"));
    private SolidAirEntities() { }
    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(SolidAirEntities::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(WALL.get(), SolidAirWall.createMobAttributes().add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1).build());
    }
}

package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.VineConstruct;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VinesEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<VineConstruct>> VINE = ENTITIES.register("vine_construct",
            () -> EntityType.Builder.<VineConstruct>of(VineConstruct::new, MobCategory.MISC).sized(0.4F, 0.4F)
                    .clientTrackingRange(12).updateInterval(1).noSummon().build("tracadamia:vine_construct"));
    private VinesEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); bus.addListener(VinesEntities::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(VINE.get(), VineConstruct.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.KNOCKBACK_RESISTANCE, 1).build());
    }
}

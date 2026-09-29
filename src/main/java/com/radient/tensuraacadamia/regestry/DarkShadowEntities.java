package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.DarkShadow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DarkShadowEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<DarkShadow>> SHADOW = ENTITIES.register("dark_shadow",
            () -> EntityType.Builder.<DarkShadow>of(DarkShadow::new, MobCategory.MISC).sized(0.6F, 1.8F)
                    .clientTrackingRange(16).updateInterval(1).noSummon().build("tracadamia:dark_shadow"));
    private DarkShadowEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); bus.addListener(DarkShadowEntities::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) {
        var attributes = DarkShadow.createMobAttributes();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(attributes::add);
        event.put(SHADOW.get(), attributes.add(Attributes.MAX_HEALTH, 20).add(Attributes.ATTACK_DAMAGE, 5).build());
    }
}

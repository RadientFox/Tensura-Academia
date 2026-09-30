package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.entity.AlchemyCoin;
import com.radient.tensuraacadamia.entity.MoltenShield;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AlchemyEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<MoltenShield>> SHIELD = ENTITIES.register("molten_shield",
            () -> EntityType.Builder.<MoltenShield>of(MoltenShield::new, MobCategory.MISC).sized(5, 5).clientTrackingRange(12).updateInterval(1).noSummon().build("tracadamia:molten_shield"));
    public static final DeferredHolder<EntityType<?>, EntityType<AlchemyCoin>> COIN = ENTITIES.register("alchemy_coin",
            () -> EntityType.Builder.<AlchemyCoin>of(AlchemyCoin::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:alchemy_coin"));
    private AlchemyEntities() { }
    public static void register(IEventBus bus) { ENTITIES.register(bus); bus.addListener(AlchemyEntities::attributes); }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(SHIELD.get(), MoltenShield.createMobAttributes().add(Attributes.MAX_HEALTH, MoltenShield.MAX_HEALTH).add(Attributes.MOVEMENT_SPEED, 0).build());
    }
}

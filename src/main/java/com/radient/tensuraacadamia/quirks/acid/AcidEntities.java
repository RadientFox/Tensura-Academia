package com.radient.tensuraacadamia.quirks.acid;

import com.radient.tensuraacadamia.quirks.acid_sweat.AcidSweatAction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AcidEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "tracadamia");
    public static final DeferredHolder<EntityType<?>, EntityType<AcidProjectile>> ACID = ENTITIES.register("acid_projectile",
            () -> EntityType.Builder.<AcidProjectile>of(AcidProjectile::new, MobCategory.MISC).sized(.4F, .4F)
                    .clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("tracadamia:acid_projectile"));

    private AcidEntities() {
    }

    public static final DeferredHolder<EntityType<?>, EntityType<AcidSweatAction>> SWEAT_ACTION = ENTITIES.register("acid_sweat_action",
            () -> EntityType.Builder.<AcidSweatAction>of(AcidSweatAction::new, MobCategory.MISC).sized(.6F, 1.8F)
                    .clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("tracadamia:acid_sweat_action"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
    }
}

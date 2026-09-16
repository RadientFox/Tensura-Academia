package com.radient.tensuraacadamia.regestry;


import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.tensura.entity.projectile.magic.WindTornadoProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;

/*
public class OFAEntitys {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, "stellarprism",);

    public static final RegistrySupplier<EntityType<OFATornado>> ONYX_VOID = ENTITY_TYPES.register("onyx_void",
            () -> EntityType.Builder.<OFATornado>of(OFATornado::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .build("onyx_void"));





    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

}


 */
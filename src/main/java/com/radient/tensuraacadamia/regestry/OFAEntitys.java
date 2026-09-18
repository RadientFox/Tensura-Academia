package com.radient.tensuraacadamia.regestry;


import com.radient.tensuraacadamia.TensuraAcadamia;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.manasmods.tensura.entity.projectile.magic.WindTornadoProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/*
public class OFAEntitys {

    public static final net.neoforged.neoforge.registries.DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TensuraAcadamia.MODID);


    public static final DeferredHolder<EntityType<?>, EntityType<BlackwhipProjectile>> BLACKWHIP_PROJECTILE =
            ENTITY_TYPES.register("blackwhip_projectile",
                    () -> EntityType.Builder.<BlackwhipProjectile>of(
                                    BlackwhipProjectile::new,
                                    MobCategory.MISC
                            )
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(64)
                            .updateInterval(1)


                            .build("blackwhip_projectile"));




}


 */


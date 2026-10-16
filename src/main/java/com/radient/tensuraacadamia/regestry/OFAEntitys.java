package com.radient.tensuraacadamia.regestry;


import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.BlackwhipProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;


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





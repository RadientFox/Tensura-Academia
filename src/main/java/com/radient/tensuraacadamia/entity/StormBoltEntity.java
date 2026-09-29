package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.mixin.LightningBoltAccessor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class StormBoltEntity extends LightningBolt {

    public static final DeferredHolder<EntityType<?>, EntityType<StormBoltEntity>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "storm_bolt"));

    public StormBoltEntity(EntityType<? extends StormBoltEntity> type, Level level) {
        super(type, level);
        setVisualOnly(true);
        ((LightningBoltAccessor) this).tracadamia$setLife(1);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<StormBoltEntity>of(StormBoltEntity::new, MobCategory.MISC)
                .noSave()
                .sized(0.0F, 0.0F)
                .clientTrackingRange(16)
                .updateInterval(Integer.MAX_VALUE)
                .noSummon()
                .build(TYPE.getId().toString()));
    }

}

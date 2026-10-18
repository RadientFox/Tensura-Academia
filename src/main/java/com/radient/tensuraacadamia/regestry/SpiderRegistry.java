package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.TripWebDevice;
import com.radient.tensuraacadamia.entity.WebProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpiderRegistry {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TensuraAcadamia.MODID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, TensuraAcadamia.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, TensuraAcadamia.MODID);

    public static final DeferredItem<Item> COMBAT_TOKEN = ITEMS.register("combat_token", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RAID_TOKEN = ITEMS.register("raid_token", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> RESEARCH_TOKEN = ITEMS.register("research_token", () -> new Item(new Item.Properties()));

    public static final DeferredHolder<Attribute, Attribute> SPIDER_MASTERY = ATTRIBUTES.register("spider_mastery",
            () -> new RangedAttribute("attribute.tracadamia.spider_mastery", 0.0D, 0.0D, 100.0D).setSyncable(true));

    public static final DeferredHolder<EntityType<?>, EntityType<WebProjectile>> WEB = ENTITIES.register("web",
            () -> EntityType.Builder.<WebProjectile>of(WebProjectile::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:web"));
    public static final DeferredHolder<EntityType<?>, EntityType<TripWebDevice>> TRIP_DEVICE = ENTITIES.register("trip_web_device",
            () -> EntityType.Builder.<TripWebDevice>of(TripWebDevice::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).noSummon().build("tracadamia:trip_web_device"));

    private SpiderRegistry() { }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ATTRIBUTES.register(bus);
        ENTITIES.register(bus);
    }

    @SubscribeEvent
    public static void addAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, SPIDER_MASTERY);
    }
}

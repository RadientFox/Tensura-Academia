package com.radient.tensuraacadamia.regestry;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class QuirkVisualItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("tracadamia");

    public static final DeferredItem<Item> SPLINTER_SWORD_VISUAL = ITEMS.register("splinter_sword_visual",
            () -> new Item(new Item.Properties()));

    private QuirkVisualItems() { }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.item.FeatherBladeItem;
import io.github.manasmods.tensura.item.TensuraToolTiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FierceWingsItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("tracadamia");

    public static final DeferredItem<FeatherBladeItem> FEATHER_BLADE = ITEMS.register("feather_blade",
            () -> new FeatherBladeItem(TensuraToolTiers.PURE_MAGISTEEL));
    public static final DeferredItem<FeatherBladeItem> ADAMANTITE_FEATHER_BLADE = ITEMS.register("adamantite_feather_blade",
            () -> new FeatherBladeItem(TensuraToolTiers.ADAMANTITE));
    public static final DeferredItem<FeatherBladeItem> HIHIIROKANE_FEATHER_BLADE = ITEMS.register("hihiirokane_feather_blade",
            () -> new FeatherBladeItem(TensuraToolTiers.HIHIIROKANE));

    private FierceWingsItems() { }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}

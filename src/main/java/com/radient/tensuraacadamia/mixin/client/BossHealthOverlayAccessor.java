package com.radient.tensuraacadamia.mixin.client;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

// Shows the HP of the body of air
// Looks cool
// Didnt know how else to show it
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

    @Accessor("events")
    Map<UUID, LerpingBossEvent> tracadamia$getEvents();

}

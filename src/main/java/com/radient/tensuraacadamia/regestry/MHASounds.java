package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MHASounds {
    public static final ResourceLocation AFO_THEME_ID = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "afo_noteblock_theme");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, TensuraAcadamia.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> AFO_THEME = SOUNDS.register(
            "afo_noteblock_theme", () -> SoundEvent.createVariableRangeEvent(AFO_THEME_ID));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLEED = SOUNDS.register(
            "bleed_sfx", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "bleed_sfx")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BLEED_REVERSED = SOUNDS.register(
            "bleed_sfx_reversed", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "bleed_sfx_reversed")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ELECTRIFICATION = SOUNDS.register(
            "electrification", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "electrification")));
    public static final ResourceLocation BUTTERFLY_EFFECT_ID = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "butterfly_effect");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTERFLY_EFFECT = SOUNDS.register(
            "butterfly_effect", () -> SoundEvent.createVariableRangeEvent(BUTTERFLY_EFFECT_ID));
    public static final ResourceLocation CHARGING_SFX = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "charging_sfx");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGING = SOUNDS.register(
            "charging_sfx", () -> SoundEvent.createVariableRangeEvent(CHARGING_SFX));

    private MHASounds() {
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}

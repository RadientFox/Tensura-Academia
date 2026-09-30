package com.radient.tensuraacadamia;

import com.radient.tensuraacadamia.config.AcadamiaConfigs;
import com.radient.tensuraacadamia.regestry.*;
import com.radient.tensuraacadamia.ability.unique.quirks.PopOffQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.DoubleMenus;
import com.radient.tensuraacadamia.ability.unique.quirks.DoubleCloneManager;
import com.radient.tensuraacadamia.ability.unique.quirks.DoubleQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.ability.unique.quirks.Bloodcurdle;
import com.radient.tensuraacadamia.ability.unique.quirks.ElectrificationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.SmokescreenQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ExplosionQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.VoiceQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.ElasticityQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.AcceleratorRingsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.PermeationQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.EngineQuirk;
import com.radient.tensuraacadamia.network.ElasticityBouncePayload;
import com.radient.tensuraacadamia.network.AcceleratorRingsFlightPayload;
import com.radient.tensuraacadamia.network.PermeationPhasePayload;
import com.radient.tensuraacadamia.ability.ultimate.ofa.GearshiftTrailPayload;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(TensuraAcadamia.MODID)
public class TensuraAcadamia {
    public static final String MODID = "tracadamia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraAcadamia(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloadHandlers);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(Bloodcurdle.class);
        NeoForge.EVENT_BUS.register(DoubleCloneManager.class);
        NeoForge.EVENT_BUS.register(DoubleQuirk.class);
        NeoForge.EVENT_BUS.register(PopOffQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.WhirlwindQuirk.class);
        com.radient.tensuraacadamia.ability.unique.quirks.WhirlwindQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.SolidAirQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.VinesQuirk.class);
        com.radient.tensuraacadamia.ability.unique.quirks.VinesQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk.class);
        com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.AlchemyQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.TapeQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.CloudQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.HellflameQuirk.class);
        NeoForge.EVENT_BUS.register(ElectrificationQuirk.class);
        NeoForge.EVENT_BUS.register(SmokescreenQuirk.class);
        NeoForge.EVENT_BUS.register(ExplosionQuirk.class);
        NeoForge.EVENT_BUS.register(ElasticityQuirk.class);
        NeoForge.EVENT_BUS.register(AcceleratorRingsQuirk.class);
        NeoForge.EVENT_BUS.register(PermeationQuirk.class);
        PermeationQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.ability.unique.quirks.PerilDiffusionQuirk.class);
        com.radient.tensuraacadamia.ability.unique.quirks.PerilDiffusionQuirk.registerSkillEvents();
        EngineQuirk.registerSkillEvents();
        // Register the chat listener directly. Class scanning did not reliably attach
        // Voice's static chat handler in the integrated-server environment.
        NeoForge.EVENT_BUS.addListener(VoiceQuirk::onChatMessage);
        SmokescreenQuirk.registerSkillEvents();
        com.radient.tensuraacadamia.ability.unique.quirks.HalfColdHalfHot.registerSkillEvents();
        QuirkSkills.init();
        DoubleMenus.register(modEventBus);
        MHAEffects.register(modEventBus);
        com.radient.tensuraacadamia.regestry.ThermalIce.register(modEventBus);
        MHASounds.register(modEventBus);
        MHAParticles.init(modEventBus);
        QuirkVisualItems.register(modEventBus);
        QueenBeamEntities.register(modEventBus);
        PopOffEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.HomingEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.SolidAirEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.VinesEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.BlastEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.DarkShadowEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.AlchemyEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.CloudEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.GreyTornadoEntities.register(modEventBus);
        com.radient.tensuraacadamia.ability.unique.quirks.WoodenSwordsQuirk.registerSkillEvents();
        OFAEntitys.ENTITY_TYPES.register(modEventBus);
        AcadamiaConfigs.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ElasticityBouncePayload.TYPE,
                ElasticityBouncePayload.STREAM_CODEC, ElasticityBouncePayload::handle);
        event.registrar("1").playToClient(GearshiftTrailPayload.TYPE,
                GearshiftTrailPayload.STREAM_CODEC, GearshiftTrailPayload::handle);
        event.registrar("1").playToClient(AcceleratorRingsFlightPayload.TYPE,
                AcceleratorRingsFlightPayload.STREAM_CODEC, AcceleratorRingsFlightPayload::handle);
        event.registrar("1").playToClient(PermeationPhasePayload.TYPE,
                PermeationPhasePayload.STREAM_CODEC, PermeationPhasePayload::handle);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }



}

package com.radient.tensuraacadamia;

import com.radient.tensuraacadamia.config.AcadamiaConfigs;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.MHAParticles;
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
        NeoForge.EVENT_BUS.register(ElectrificationQuirk.class);
        NeoForge.EVENT_BUS.register(SmokescreenQuirk.class);
        NeoForge.EVENT_BUS.register(ExplosionQuirk.class);
        NeoForge.EVENT_BUS.register(ElasticityQuirk.class);
        NeoForge.EVENT_BUS.register(AcceleratorRingsQuirk.class);
        NeoForge.EVENT_BUS.register(PermeationQuirk.class);
        EngineQuirk.registerSkillEvents();
        // Register the chat listener directly. Class scanning did not reliably attach
        // Voice's static chat handler in the integrated-server environment.
        NeoForge.EVENT_BUS.addListener(VoiceQuirk::onChatMessage);
        SmokescreenQuirk.registerSkillEvents();
        com.radient.tensuraacadamia.ability.unique.quirks.HalfColdHalfHot.registerSkillEvents();
        QuirkSkills.init();
        MHAEffects.register(modEventBus);
        com.radient.tensuraacadamia.regestry.ThermalIce.register(modEventBus);
        MHASounds.register(modEventBus);
        MHAParticles.init(modEventBus);
      //  OFAEntitys.ENTITY_TYPES.register(modEventBus);
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

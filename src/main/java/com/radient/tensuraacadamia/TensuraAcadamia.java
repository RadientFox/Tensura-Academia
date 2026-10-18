package com.radient.tensuraacadamia;

import com.radient.tensuraacadamia.quirks.acid.AcidEntities;
import com.radient.tensuraacadamia.quirks.arbor.ArborEntities;
import com.radient.tensuraacadamia.quirks.barrier.BarrierEntities;
import com.radient.tensuraacadamia.quirks.leafipulation.LeafEntities;
import com.radient.tensuraacadamia.quirks.overhaul.OverhaulEarth;
import com.radient.tensuraacadamia.quirks.pop_off.PopOffEntities;
import com.radient.tensuraacadamia.quirks.queen_beam.QueenBeamEntities;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.OFAEntitys;
import com.radient.tensuraacadamia.regestry.QuirkVisualItems;

import com.radient.tensuraacadamia.config.AcadamiaConfigs;
import com.radient.tensuraacadamia.regestry.*;
import com.radient.tensuraacadamia.quirks.pop_off.PopOffQuirk;
import com.radient.tensuraacadamia.quirks.double_quirk.DoubleMenus;
import com.radient.tensuraacadamia.quirks.double_quirk.DoubleCloneManager;
import com.radient.tensuraacadamia.quirks.double_quirk.DoubleQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.quirks.bloodcurdle.Bloodcurdle;
import com.radient.tensuraacadamia.quirks.electrification.ElectrificationQuirk;
import com.radient.tensuraacadamia.quirks.smokescreen.SmokescreenQuirk;
import com.radient.tensuraacadamia.quirks.explosion.ExplosionQuirk;
import com.radient.tensuraacadamia.quirks.voice.VoiceQuirk;
import com.radient.tensuraacadamia.quirks.elasticity.ElasticityQuirk;
import com.radient.tensuraacadamia.quirks.accelerator_rings.AcceleratorRingsQuirk;
import com.radient.tensuraacadamia.quirks.permeation.PermeationQuirk;
import com.radient.tensuraacadamia.quirks.engine.EngineQuirk;
import com.radient.tensuraacadamia.quirks.elasticity.ElasticityBouncePayload;
import com.radient.tensuraacadamia.quirks.accelerator_rings.AcceleratorRingsFlightPayload;
import com.radient.tensuraacadamia.quirks.permeation.PermeationPhasePayload;
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
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.whirlwind.WhirlwindQuirk.class);
        com.radient.tensuraacadamia.quirks.whirlwind.WhirlwindQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.homing.HomingQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.solid_air.SolidAirQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.vines.VinesQuirk.class);
        com.radient.tensuraacadamia.quirks.vines.VinesQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowQuirk.class);
        com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.alchemy.AlchemyQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.tape.TapeQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.cloud.CloudQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.teleportation.TeleportationQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.leafipulation.LeafipulationQuirk.class);
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.warp_gate.WarpGateQuirk.class);
        com.radient.tensuraacadamia.quirks.warp_gate.WarpGateQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(ElectrificationQuirk.class);
        NeoForge.EVENT_BUS.register(SmokescreenQuirk.class);
        NeoForge.EVENT_BUS.register(ExplosionQuirk.class);
        NeoForge.EVENT_BUS.register(ElasticityQuirk.class);
        NeoForge.EVENT_BUS.register(AcceleratorRingsQuirk.class);
        NeoForge.EVENT_BUS.register(PermeationQuirk.class);
        PermeationQuirk.registerSkillEvents();
        NeoForge.EVENT_BUS.register(com.radient.tensuraacadamia.quirks.peril_diffusion.PerilDiffusionQuirk.class);
        com.radient.tensuraacadamia.quirks.peril_diffusion.PerilDiffusionQuirk.registerSkillEvents();
        EngineQuirk.registerSkillEvents();
        // Register the chat listener directly. Class scanning did not reliably attach
        // Voice's static chat handler in the integrated-server environment.
        NeoForge.EVENT_BUS.addListener(VoiceQuirk::onChatMessage);
        SmokescreenQuirk.registerSkillEvents();
        com.radient.tensuraacadamia.quirks.half_cold_half_hot.HalfColdHalfHot.registerSkillEvents();
        QuirkSkills.init();
        DoubleMenus.register(modEventBus);
        MHAEffects.register(modEventBus);
        com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalIce.register(modEventBus);
        OverhaulEarth.register(modEventBus);
        MHASounds.register(modEventBus);
        MHAParticles.init(modEventBus);
        QuirkVisualItems.register(modEventBus);
        QueenBeamEntities.register(modEventBus);
        PopOffEntities.register(modEventBus);
        LeafEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.homing.HomingEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.solid_air.SolidAirEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.vines.VinesEntities.register(modEventBus);
        ArborEntities.register(modEventBus);
        AcidEntities.register(modEventBus);
        BarrierEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.acid.AcidQuirk.registerSkillEvents();
        com.radient.tensuraacadamia.quirks.blast.BlastEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.dark_shadow.DarkShadowEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.alchemy.AlchemyEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.cloud.CloudEntities.register(modEventBus);
        com.radient.tensuraacadamia.regestry.GreyTornadoEntities.register(modEventBus);
        com.radient.tensuraacadamia.quirks.wooden_swords.WoodenSwordsQuirk.registerSkillEvents();
        OFAEntitys.ENTITY_TYPES.register(modEventBus);
        AcadamiaConfigs.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(com.radient.tensuraacadamia.quirks.overhaul.OverhaulStatePayload.TYPE,
                com.radient.tensuraacadamia.quirks.overhaul.OverhaulStatePayload.STREAM_CODEC,
                com.radient.tensuraacadamia.quirks.overhaul.OverhaulStatePayload::handle);
        event.registrar("1").playToServer(ElasticityBouncePayload.TYPE,
                ElasticityBouncePayload.STREAM_CODEC, ElasticityBouncePayload::handle);
        event.registrar("1").playToClient(GearshiftTrailPayload.TYPE,
                GearshiftTrailPayload.STREAM_CODEC, GearshiftTrailPayload::handle);
        event.registrar("1").playToClient(AcceleratorRingsFlightPayload.TYPE,
                AcceleratorRingsFlightPayload.STREAM_CODEC, AcceleratorRingsFlightPayload::handle);
        event.registrar("1").playToClient(PermeationPhasePayload.TYPE,
                PermeationPhasePayload.STREAM_CODEC, PermeationPhasePayload::handle);
        event.registrar("1").playToServer(com.radient.tensuraacadamia.quirks.warp_gate.WarpGateDestinationPayload.TYPE,
                com.radient.tensuraacadamia.quirks.warp_gate.WarpGateDestinationPayload.STREAM_CODEC,
                com.radient.tensuraacadamia.quirks.warp_gate.WarpGateDestinationPayload::handle);
        event.registrar("1").playToClient(com.radient.tensuraacadamia.quirks.warp_gate.WarpGateOpenPayload.TYPE,
                com.radient.tensuraacadamia.quirks.warp_gate.WarpGateOpenPayload.STREAM_CODEC,
                com.radient.tensuraacadamia.quirks.warp_gate.WarpGateOpenPayload::handle);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }



}

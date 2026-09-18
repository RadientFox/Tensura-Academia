package com.radient.tensuraacadamia;

import com.radient.tensuraacadamia.config.AcadamiaConfigs;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.ability.unique.quirks.Bloodcurdle;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(TensuraAcadamia.MODID)
public class TensuraAcadamia {
    public static final String MODID = "tracadamia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TensuraAcadamia(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(Bloodcurdle.class);
        QuirkSkills.init();
        MHAEffects.register(modEventBus);
        MHASounds.register(modEventBus);
        MHAParticles.init(modEventBus);
      //  OFAEntitys.ENTITY_TYPES.register(modEventBus);
        AcadamiaConfigs.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }



}

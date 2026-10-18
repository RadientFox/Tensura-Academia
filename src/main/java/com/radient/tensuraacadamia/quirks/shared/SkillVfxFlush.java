package com.radient.tensuraacadamia.quirks.shared;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class SkillVfxFlush {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finish(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) SkillVfx.flush();
    }
    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Buffers {
        @SubscribeEvent
        public static void register(RegisterRenderBuffersEvent event) {
            event.registerRenderBuffer(SkillVfx.HAZE);
            event.registerRenderBuffer(SkillVfx.SHINE);
            event.registerRenderBuffer(SkillVfx.BILLOW);
            event.registerRenderBuffer(SkillVfx.FLAME);
        }
    }
}

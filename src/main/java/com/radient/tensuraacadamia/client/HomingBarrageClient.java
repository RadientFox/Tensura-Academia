package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.HomingEntities;
import com.radient.tensuraacadamia.regestry.BlastEntities;
import io.github.manasmods.tensura.client.entity.projectile.magic.AuraBulletRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HomingBarrageClient {
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HomingEntities.BARRAGE.get(), AuraBulletRenderer::new);
        event.registerEntityRenderer(BlastEntities.BULLET.get(), AuraBulletRenderer::new);
    }
}

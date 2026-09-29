package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.VibrateQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.util.List;

// Brrrrrrrr
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class VibrateClient {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && !VibrateQuirk.getClientEffects().isEmpty()) {
            long time = level.getGameTime();
            VibrateQuirk.getClientEffects().removeIf(effect -> time - effect.start() > effect.duration() + 20 || effect.start() > time);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VibrateQuirk.getClientEffects().clear();
    }

    //Screen shakes
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || VibrateQuirk.getClientEffects().isEmpty()) {
            return;
        }

        float now = minecraft.level.getGameTime() + (float) event.getPartialTick();
        float intensity = 0.0F;
        for (VibrateQuirk.Effect effect : VibrateQuirk.getClientEffects()) {
            float age = now - effect.start();
            if (age < 0.0F || age > effect.duration()) {
                continue;
            }

            float left = 1.0F - age / effect.duration();
            if (effect.kind() == VibrateQuirk.EFFECT_VIBRATE) {
                if (effect.entityId() == minecraft.player.getId()) {
                    intensity = Math.max(intensity, 0.6F);
                }

                continue;
            }

            float reach = effect.radius() * (effect.kind() == VibrateQuirk.EFFECT_TREMOR ? 1.6F : 1.3F);
            float distance = (float) minecraft.player.position().distanceTo(effect.pos());
            float strength = effect.kind() == VibrateQuirk.EFFECT_TREMOR ? 2.4F : 1.2F;
            intensity = Math.max(intensity, strength * left * Mth.clamp(1.0F - distance / reach, 0.0F, 1.0F));
        }

        if (intensity > 0.0F) {
            event.setPitch(event.getPitch() + Mth.sin(now * 3.7F) * intensity * 0.9F);
            event.setYaw(event.getYaw() + Mth.cos(now * 4.1F) * intensity * 0.7F);
            event.setRoll(event.getRoll() + Mth.sin(now * 5.3F) * intensity * 1.4F);
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!isVibrating(event.getEntity())) {
            return;
        }

        float time = event.getEntity().tickCount + event.getPartialTick();
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(Mth.sin(time * 9.1F) * 0.05F, Mth.sin(time * 11.3F) * 0.03F, Mth.cos(time * 8.7F) * 0.05F);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (isVibrating(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    private static boolean isVibrating(Entity entity) {
        List<VibrateQuirk.Effect> effects = VibrateQuirk.getClientEffects();
        if (effects.isEmpty()) {
            return false;
        }

        long time = entity.level().getGameTime();
        for (VibrateQuirk.Effect effect : effects) {
            if (effect.kind() == VibrateQuirk.EFFECT_VIBRATE && effect.entityId() == entity.getId() && time - effect.start() <= effect.duration()) {
                return true;
            }
        }

        return false;
    }

}

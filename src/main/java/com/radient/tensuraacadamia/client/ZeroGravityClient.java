package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ZeroGravityQuirk;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class ZeroGravityClient {

    private static boolean wasFloating;

    @SubscribeEvent
    public static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        boolean floating = ZeroGravityQuirk.isFloating(player);
        player.setDiscardFriction(floating);
        if (!floating) {
            wasFloating = false;
            return;
        }

        ZeroGravityQuirk.FloatPayload settings = ZeroGravityQuirk.getClientFloat();
        Vec3 motion = player.getDeltaMovement();
        if (!wasFloating && player.onGround()) {
            motion = new Vec3(motion.x, Math.max(motion.y, 0.0D) + ZeroGravityQuirk.LIFT, motion.z);
        }

        wasFloating = true;
        Vec3 input = new Vec3(player.input.leftImpulse, (player.input.jumping ? 1 : 0) - (player.input.shiftKeyDown ? 1 : 0), player.input.forwardImpulse);
        if (input.lengthSqr() > 1.0E-7D) {
            Vec3 push = (input.lengthSqr() > 1.0D ? input.normalize() : input).scale(settings.acceleration());
            float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
            float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
            motion = pushUpTo(motion, new Vec3(push.x * cos - push.z * sin, push.y, push.z * cos + push.x * sin), settings.moveMaxSpeed());
        }

        double wind = ZeroGravityQuirk.getClientWindThrust();
        if (wind > 0.0D) {
            motion = pushUpTo(motion, player.getLookAngle().scale(-wind), settings.windMaxSpeed());
        }

        player.setDeltaMovement(motion);
    }

    private static Vec3 pushUpTo(Vec3 motion, Vec3 push, double cap) {
        Vec3 pushed = motion.add(push);
        double speed = pushed.length();
        double limit = Math.max(cap, motion.length());
        return speed > limit ? pushed.scale(limit / speed) : pushed;
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !ZeroGravityQuirk.isFloating(player)) {
            return;
        }

        ZeroGravityQuirk.FloatPayload settings = ZeroGravityQuirk.getClientFloat();
        Vec3 motion = player.getDeltaMovement();
        double speed = motion.length();
        if (speed < 1.0E-7D) {
            return;
        }

        double slowed = Math.min(settings.maxSpeed(), speed - Math.min(speed * settings.dragPercent(), settings.dragLimit()));
        player.setDeltaMovement(motion.scale(Math.max(0.0D, slowed) / speed));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZeroGravityQuirk.clearClient();
        wasFloating = false;
    }

}

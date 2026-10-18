package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.FierceWingsQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class FierceWingsClient {
    private static final double THRUST = 0.06D;

    private FierceWingsClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isFallFlying() || player.input.forwardImpulse <= 0.0F || !FierceWingsQuirk.canWingFly(player)) {
            return;
        }

        Optional<ManasSkillInstance> wings = FierceWingsQuirk.getFierceWings(player);
        if (wings.isEmpty()) {
            return;
        }

        Vec3 motion = player.getDeltaMovement();
        Vec3 target = player.getLookAngle().scale(FierceWingsQuirk.getCruiseSpeed(wings.get()));
        player.setDeltaMovement(motion.add(target.subtract(motion).scale(THRUST)));
    }
}

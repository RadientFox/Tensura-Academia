package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.JetQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class JetClient {

    private static final float KICK_FORWARD = -1.6F;
    private static final float KICK_TICKS = 8.0F;

    // Ticks of slow fall since the user last touched the ground or boosted
    private static int slowFallTicks;
    private static boolean slowFalling;

    private JetClient() {
    }

    // Quick kick
    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (ageInTicks == 0.0F) {
            return;
        }

        long start = JetQuirk.getJet(entity).map(JetQuirk::getKickStart).orElse(Long.MIN_VALUE);
        if (start == Long.MIN_VALUE) {
            return;
        }

        float age = entity.level().getGameTime() - start + ageInTicks - entity.tickCount;
        if (age < 0.0F || age > KICK_TICKS) {
            return;
        }

        float back = age / KICK_TICKS;
        GigantificationClient.poseKick(model, KICK_FORWARD * (1.0F - back * back));
    }

    // Slow Fall while jump is held
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            slowFalling = false;
            return;
        }

        if (JetQuirk.consumeSlowFallRefresh()) {
            slowFallTicks = 0;
        }

        boolean slowing = false;
        if (player.onGround() || player.isInLiquid() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying) {
            slowFallTicks = 0;
        } else {
            JetQuirk.SlowFallPayload settings = JetQuirk.getClientSlowFall();
            Vec3 motion = player.getDeltaMovement();
            if (player.input.jumping && motion.y < -settings.speed() * 0.5D && slowFallTicks < settings.ticks() && JetQuirk.getJet(player).filter(ManasSkillInstance::isToggled).isPresent()) {
                player.setDeltaMovement(motion.x, Math.max(motion.y, -settings.speed()), motion.z);
                slowFallTicks++;
                slowing = true;
            }
        }

        if (slowing != slowFalling) {
            slowFalling = slowing;
            PacketDistributor.sendToServer(new JetQuirk.SlowFallingPayload(slowing));
        }
    }

}

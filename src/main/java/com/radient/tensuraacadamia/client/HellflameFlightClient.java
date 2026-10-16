package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.HellflameQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class HellflameFlightClient {
    private HellflameFlightClient() { }

    @SubscribeEvent public static void steer(ClientTickEvent.Post event) {
        if (HellflameQuirk.isSealed()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.getAbilities().flying || !player.isAlive()
                || SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.HELLFLAME.get())
                        .filter(instance -> instance.isToggled()).isEmpty()) return;
        Vec3 look = player.getLookAngle();
        Vec3 left = new Vec3(look.z, 0, -look.x).normalize();
        Vec3 desired = look.scale(player.input.forwardImpulse * 0.42)
                .add(left.scale(player.input.leftImpulse * 0.36));
        if (player.input.jumping) desired = desired.add(0, 0.35, 0);
        if (player.input.shiftKeyDown) desired = desired.add(0, -0.35, 0);
        if (desired.lengthSqr() > 0.55 * 0.55) desired = desired.normalize().scale(0.55);
        player.setDeltaMovement(player.getDeltaMovement().scale(0.35).add(desired.scale(0.65)));
    }
}

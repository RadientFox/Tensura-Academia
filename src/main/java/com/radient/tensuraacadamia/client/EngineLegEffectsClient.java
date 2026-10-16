package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class EngineLegEffectsClient {
    private EngineLegEffectsClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        for (Player player : minecraft.level.players()) {
            if (!hasEngine(player)) continue;

            Vec3 movement = player.getDeltaMovement();
            double speed = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            if (speed > 0.025D) emitLegSparks(minecraft, player, movement);
        }
    }

    private static void emitLegSparks(Minecraft minecraft, Player player, Vec3 movement) {
        List<Vec3> emitters = getLegEmitterPositions(player, 1.0F);
        long tick = minecraft.level.getGameTime();
        for (int i = 0; i < emitters.size(); i++) {
            if (Math.floorMod(tick + i + player.getId(), 3L) != 0L) continue;
            Vec3 position = emitters.get(i);
            double jitterX = (player.getRandom().nextDouble() - 0.5D) * 0.008D;
            double jitterY = player.getRandom().nextDouble() * 0.009D;
            double jitterZ = (player.getRandom().nextDouble() - 0.5D) * 0.008D;
            minecraft.level.addParticle(MHAParticles.ENGINE_SPARK.get(), position.x, position.y, position.z,
                    -movement.x * 0.14D + jitterX, jitterY, -movement.z * 0.14D + jitterZ);
        }
    }

    private static List<Vec3> getLegEmitterPositions(Player player, float partialTick) {
        Vec3 base = getInterpolatedPosition(player, partialTick);
        float yaw = (float) Math.toRadians(Mth.rotLerp(partialTick, player.yRotO, player.getYRot()));
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        double hipY = base.y + player.getBbHeight() * 0.42D;
        float walkSpeed = Mth.clamp(player.walkAnimation.speed(partialTick), 0.0F, 1.0F);
        float walkPhase = player.walkAnimation.position(partialTick) * 0.6662F;
        float attack = Mth.sin(player.getAttackAnim(partialTick) * Mth.PI);
        boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
        List<Vec3> emitters = new ArrayList<>(12);

        for (int leg = 0; leg < 2; leg++) {
            boolean rightLeg = leg == 1;
            double side = rightLeg ? 1.0D : -1.0D;
            float phase = walkPhase + (rightLeg ? 0.0F : Mth.PI);
            float legAngle = Mth.cos(phase) * walkSpeed * 0.48F;
            if (rightLeg == rightMain) legAngle -= 1.65F * attack;

            double cosine = Math.cos(legAngle);
            double sine = Math.sin(legAngle);
            for (int row = 0; row < 3; row++) {
                double localY = 0.15D + row * 0.20D;
                double localZ = 0.131D;
                double rotatedY = localY * cosine - localZ * sine;
                double rotatedZ = localY * sine + localZ * cosine;
                for (int column = 0; column < 2; column++) {
                    double localX = column == 0 ? -0.042D : 0.042D;
                    emitters.add(new Vec3(base.x, hipY - rotatedY, base.z)
                            .add(right.scale(side * player.getBbWidth() * 0.20D + localX))
                            .subtract(forward.scale(rotatedZ)));
                }
            }
        }
        return emitters;
    }

    private static Vec3 getInterpolatedPosition(Player player, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, player.xOld, player.getX()),
                Mth.lerp(partialTick, player.yOld, player.getY()),
                Mth.lerp(partialTick, player.zOld, player.getZ()));
    }

    private static boolean hasEngine(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.ENGINE.get()).isPresent();
    }
}

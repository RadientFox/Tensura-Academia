package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.FierceGainsQuirk;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class FierceGainsClient {

    private static final float GUARD_PITCH = -1.3F;
    private static final float GUARD_YAW = 0.7F;

    private FierceGainsClient() {
    }

    // Arms crossed in front of the chest while blocking
    public static void poseModel(PlayerModel<?> model, LivingEntity entity) {
        if (!(entity instanceof Player) || FierceGainsQuirk.getFierceGains(entity).filter(instance -> FierceGainsQuirk.isBlocking(instance, entity)).isEmpty()) {
            return;
        }

        model.rightArm.xRot = GUARD_PITCH;
        model.rightArm.yRot = -GUARD_YAW;
        model.rightArm.zRot = 0.0F;
        model.leftArm.xRot = GUARD_PITCH;
        model.leftArm.yRot = GUARD_YAW;
        model.leftArm.zRot = 0.0F;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

}

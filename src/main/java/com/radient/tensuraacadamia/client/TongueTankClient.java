package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.TongueTankQuirk;
import com.radient.tensuraacadamia.util.Limbs;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class TongueTankClient {

    private TongueTankClient() {
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity) {
        if (!TongueTankQuirk.getTongueTank(entity).filter(TongueTankQuirk::isGrabbing).isPresent()) {
            return;
        }

        boolean left = TongueTankQuirk.getGrabArm(entity) == Limbs.LEFT_ARM;
        ModelPart arm = left ? model.leftArm : model.rightArm;
        arm.xRot = model.head.xRot - Mth.HALF_PI;
        arm.yRot = left ? 0.15F : -0.15F;
        arm.zRot = 0.0F;
        (left ? model.leftSleeve : model.rightSleeve).copyFrom(arm);
    }

}

package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.BruiserQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class BruiserClient {

    private static final float GUARD_PITCH = -1.4F;
    private static final float GUARD_YAW = 0.5F;

    private BruiserClient() {
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity) {
        Optional<ManasSkillInstance> found = entity instanceof Player ? BruiserQuirk.getBruiser(entity).filter(BruiserQuirk::isPowered) : Optional.empty();
        int part = found.map(BruiserQuirk::getPart).orElse(-1);
        float size = (float) BruiserQuirk.getPartScale();

        scale(model.rightArm, model.rightSleeve, part == BruiserQuirk.RIGHT_ARM ? size : 1.0F, true);
        scale(model.leftArm, model.leftSleeve, part == BruiserQuirk.LEFT_ARM ? size : 1.0F, true);
        scale(model.rightLeg, model.rightPants, part == BruiserQuirk.RIGHT_LEG ? size : 1.0F, false);
        scale(model.leftLeg, model.leftPants, part == BruiserQuirk.LEFT_LEG ? size : 1.0F, false);

        if (part == BruiserQuirk.LEFT_ARM && found.filter(BruiserQuirk::isBlocking).isPresent()) {
            model.leftArm.xRot = GUARD_PITCH + Mth.clamp(model.head.xRot, -0.6F, 0.6F);
            model.leftArm.yRot = GUARD_YAW;
            model.leftArm.zRot = 0.0F;
            model.leftSleeve.copyFrom(model.leftArm);
        }
    }

    private static void scale(ModelPart limb, ModelPart layer, float size, boolean lengthen) {
        limb.xScale = size;
        limb.yScale = lengthen ? size : 1.0F;
        limb.zScale = size;
        layer.xScale = limb.xScale;
        layer.yScale = limb.yScale;
        layer.zScale = limb.zScale;
    }

}

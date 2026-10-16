package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class EngineKickMixin {
    @Unique
    private float tracadamia$engineAttackTime;
    @Unique
    private boolean tracadamia$engineKickActive;

    @Inject(method = "setupAnim", at = @At("HEAD"))
    private void tracadamia$removeArmSwing(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch,
                                           CallbackInfo callback) {
        this.tracadamia$engineKickActive = false;
        if (!(entity instanceof Player player)
                || SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.ENGINE.get()).isEmpty()) {
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (model.attackTime <= 0.0F) return;

        this.tracadamia$engineAttackTime = model.attackTime;
        this.tracadamia$engineKickActive = true;
        model.attackTime = 0.0F;
    }

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void tracadamia$applyLegKick(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                         float ageInTicks, float netHeadYaw, float headPitch,
                                         CallbackInfo callback) {
        if (!this.tracadamia$engineKickActive) return;
        this.tracadamia$engineKickActive = false;

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        model.attackTime = this.tracadamia$engineAttackTime;
        float kick = Mth.sin(this.tracadamia$engineAttackTime * Mth.PI);
        if (playerIsRightLegKicker(entity)) {
            model.rightLeg.xRot -= 1.65F * kick;
            model.rightLeg.yRot -= 0.12F * kick;
            model.rightPants.copyFrom(model.rightLeg);
        } else {
            model.leftLeg.xRot -= 1.65F * kick;
            model.leftLeg.yRot += 0.12F * kick;
            model.leftPants.copyFrom(model.leftLeg);
        }
    }

    @Unique
    private static boolean playerIsRightLegKicker(LivingEntity entity) {
        return entity instanceof Player player && player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
    }
}

package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.MultiArmsClient;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Poses for the player's own arms
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void tracadamia$restoreOverhaulPose(LivingEntity entity, float swing, float amount, float ticks, float yaw, float pitch, CallbackInfo ci) {
        com.radient.tensuraacadamia.client.OverhaulClient.restorePose((PlayerModel<?>) (Object) this);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tracadamia$quirkPoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        MultiArmsClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks, netHeadYaw, headPitch);
        com.radient.tensuraacadamia.client.OverhaulClient.posePlayer((PlayerModel<?>) (Object) this, entity, ageInTicks);
    }

}

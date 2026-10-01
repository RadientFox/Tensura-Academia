package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.BruiserClient;
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

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tracadamia$quirkPoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        MultiArmsClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks, netHeadYaw, headPitch);
        BruiserClient.poseModel((PlayerModel<?>) (Object) this, entity);
    }

}

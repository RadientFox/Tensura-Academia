package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.BruiserClient;
import com.radient.tensuraacadamia.client.FierceGainsClient;
import com.radient.tensuraacadamia.client.GigantificationClient;
import com.radient.tensuraacadamia.client.JetClient;
import com.radient.tensuraacadamia.client.LimbsClient;
import com.radient.tensuraacadamia.client.MuscleExpansionClient;
import com.radient.tensuraacadamia.client.MultiArmsClient;
import com.radient.tensuraacadamia.client.TongueTankClient;
import com.radient.tensuraacadamia.client.WhaleClient;
import com.radient.tensuraacadamia.client.WingClient;
import com.radient.tensuraacadamia.client.neworder.AirCopyRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Poses for the player's arms
// This is bringing about pseudo animations through poses
// Its cancerous
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tracadamia$quirkPoses(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        MultiArmsClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks, netHeadYaw, headPitch);
        BruiserClient.poseModel((PlayerModel<?>) (Object) this, entity);
        MuscleExpansionClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks);
        FierceGainsClient.poseModel((PlayerModel<?>) (Object) this, entity);
        GigantificationClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks);
        JetClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks);
        WhaleClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks);
        WingClient.poseModel((PlayerModel<?>) (Object) this, entity);
        TongueTankClient.poseModel((PlayerModel<?>) (Object) this, entity);
        LimbsClient.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks);
        AirCopyRenderer.poseModel((PlayerModel<?>) (Object) this, entity, ageInTicks, headPitch);
    }

}

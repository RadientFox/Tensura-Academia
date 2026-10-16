package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.client.OverhaulClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class OverhaulHumanoidMixin {
    @Unique private final float[] tracadamia$limbScales = new float[12];
    @Unique private final boolean[] tracadamia$limbVisibility = new boolean[4];
    @Unique private boolean tracadamia$limbsAdjusted;

    @Unique private ModelPart[] tracadamia$limbs() {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        return new ModelPart[]{model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
    }
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void tracadamia$restoreLimbs(LivingEntity entity, float swing, float amount, float ticks, float yaw, float pitch, CallbackInfo ci) {
        if (!tracadamia$limbsAdjusted) return;
        ModelPart[] limbs = tracadamia$limbs();
        for (int i = 0; i < 4; i++) {
            limbs[i].visible = tracadamia$limbVisibility[i];
            limbs[i].xScale = tracadamia$limbScales[i * 3];
            limbs[i].yScale = tracadamia$limbScales[i * 3 + 1];
            limbs[i].zScale = tracadamia$limbScales[i * 3 + 2];
        }
        tracadamia$limbsAdjusted = false;
    }
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tracadamia$alterLimbs(LivingEntity entity, float swing, float amount, float ticks, float yaw, float pitch, CallbackInfo ci) {
        if (OverhaulClient.mask(entity) == 0 && OverhaulClient.data(entity).getInt("Swelling") == 0) return;
        ModelPart[] limbs = tracadamia$limbs();
        for (int i = 0; i < 4; i++) {
            tracadamia$limbVisibility[i] = limbs[i].visible;
            tracadamia$limbScales[i * 3] = limbs[i].xScale;
            tracadamia$limbScales[i * 3 + 1] = limbs[i].yScale;
            tracadamia$limbScales[i * 3 + 2] = limbs[i].zScale;
        }
        OverhaulClient.poseLimbs((HumanoidModel<?>) (Object) this, entity, ticks);
        tracadamia$limbsAdjusted = true;
    }
}

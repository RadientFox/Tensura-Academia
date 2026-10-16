package com.radient.tensuraacadamia.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// Fixes limb growth not showing with armor
// Any quirk that made like an armo or leg grow didnt do it with armors because I was only messing with part scales
// This just basically fixes that and makes the armor grow, some armors could bug out but it would just be visual
// If an armor bugs taking it off and putting it back on SHOULd fix it?
@Mixin(value = GeoArmorRenderer.class, remap = false)
public abstract class GeoArmorRendererMixin {

    @Unique
    private static final Set<GeoBone> tracadamia$GROWN = Collections.newSetFromMap(new IdentityHashMap<>());

    @Shadow
    protected GeoBone rightArm;

    @Shadow
    protected GeoBone leftArm;

    @Shadow
    protected GeoBone rightLeg;

    @Shadow
    protected GeoBone leftLeg;

    @Shadow
    protected GeoBone rightBoot;

    @Shadow
    protected GeoBone leftBoot;

    @Inject(method = "applyBaseTransformations", at = @At("TAIL"), require = 0)
    private void tracadamia$growLimbs(HumanoidModel<?> baseModel, CallbackInfo ci) {
        if (baseModel != null) {
            tracadamia$grow(this.rightArm, baseModel.rightArm);
            tracadamia$grow(this.leftArm, baseModel.leftArm);
            tracadamia$grow(this.rightLeg, baseModel.rightLeg);
            tracadamia$grow(this.leftLeg, baseModel.leftLeg);
            tracadamia$grow(this.rightBoot, baseModel.rightLeg);
            tracadamia$grow(this.leftBoot, baseModel.leftLeg);
        }
    }

    @Unique
    private static void tracadamia$grow(@Nullable GeoBone bone, ModelPart limb) {
        if (bone == null) {
            return;
        }

        if (limb.xScale != 1.0F || limb.yScale != 1.0F || limb.zScale != 1.0F) {
            bone.updateScale(limb.xScale, limb.yScale, limb.zScale);
            tracadamia$GROWN.add(bone);
        } else if (tracadamia$GROWN.remove(bone)) {
            bone.updateScale(1.0F, 1.0F, 1.0F);
        }
    }

}

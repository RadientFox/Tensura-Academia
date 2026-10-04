package com.radient.tensuraacadamia.client;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.List;


public final class OverhaulAnimations {
    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath("tracadamia", "animations/overhaul.animation.json");
    public static final String[] CLIPS = {"", "slam", "reassemble", "recover_self", "recover_other", "fusion", "eradicate"};
    private OverhaulAnimations() { }

    public static Animation clip(int mode) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(FILE);
        return baked == null || mode <= 0 || mode >= CLIPS.length ? null : baked.getAnimation("animation.overhaul." + CLIPS[mode]);
    }

    public static float[] values(Animation animation, String bone, float tick, boolean position) {
        if (animation != null) for (var track : animation.boneAnimations()) if (track.boneName().equals(bone)) {
            var frames = position ? track.positionKeyFrames() : track.rotationKeyFrames();
            return new float[]{sample(frames.xKeyframes(), tick), sample(frames.yKeyframes(), tick), sample(frames.zKeyframes(), tick)};
        }
        return new float[3];
    }

    public static float sample(List<Keyframe<MathValue>> frames, float tick) {
        double start = 0;
        for (var frame : frames) {
            if (frame.length() > 0 && tick <= start + frame.length())
                return (float) frame.easingType().apply(new AnimationPoint(frame, Math.max(0, tick - start),
                        frame.length(), frame.startValue().get(), frame.endValue().get()));
            start += frame.length();
        }
        return frames.isEmpty() ? 0 : (float) frames.getLast().endValue().get();
    }

    public static float legReach(float thigh, float knee) {

        double kneeY = 6 * Math.cos(thigh);
        return (float) Math.max(kneeY + 2 * Math.abs(Math.sin(thigh)),
                kneeY + Math.max(0, 6 * Math.cos(thigh + knee)) + 2 * Math.abs(Math.sin(thigh + knee)));
    }
    public static float kneelingDrop(Animation clip, float tick) {
        float right = legReach(values(clip, "rightLeg", tick, false)[0], values(clip, "rightKnee", tick, false)[0]);
        float left = legReach(values(clip, "leftLeg", tick, false)[0], values(clip, "leftKnee", tick, false)[0]);
        return Math.clamp(12 - Math.max(right, left), -0.5F, 6);
    }
}

package com.radient.tensuraacadamia.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface TapeDisplayAccessor {
    @Invoker("setTransformation")
    void tracadamia$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void tracadamia$setTransformationInterpolationDuration(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void tracadamia$setPosRotInterpolationDuration(int ticks);
}

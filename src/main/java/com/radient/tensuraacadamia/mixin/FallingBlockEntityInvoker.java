package com.radient.tensuraacadamia.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Falling blocks used by GroundBlocks
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {

    @Invoker("<init>")
    static FallingBlockEntity tracadamia$create(Level level, double x, double y, double z, BlockState state) {
        throw new AssertionError();
    }

}

package com.radient.tensuraacadamia.mixin.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// For Dupli-Arms digging
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {

    @Accessor("destroyingBlocks")
    Int2ObjectMap<BlockDestructionProgress> tracadamia$getDestroyingBlocks();

}

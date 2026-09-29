package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.MoleQuirk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// Mole's fortune
@Mixin(Block.class)
public abstract class BlockDropsMixin {

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;", at = @At("HEAD"))
    private static void tracadamia$startDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        MoleQuirk.startDrops(entity);
    }

    @Inject(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;", at = @At("RETURN"))
    private static void tracadamia$endDrops(BlockState state, ServerLevel level, BlockPos pos, @Nullable BlockEntity blockEntity, @Nullable Entity entity, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        MoleQuirk.endDrops();
    }

}

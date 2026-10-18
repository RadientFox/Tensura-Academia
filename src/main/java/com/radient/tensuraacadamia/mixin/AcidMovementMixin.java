package com.radient.tensuraacadamia.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class AcidMovementMixin {
    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float tracadamia$acidSlide(BlockState state, LevelReader level, BlockPos position, Entity entity, Operation<Float> original) {
        return ((LivingEntity) (Object) this).hasEffect(MHAEffects.ACID_LAYBACK) ? .98F : original.call(state, level, position, entity);
    }
}

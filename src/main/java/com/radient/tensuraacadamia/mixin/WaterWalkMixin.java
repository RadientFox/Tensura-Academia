package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSelf;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Stuff I needed for water walking, its not full proof but its good enough?
// Sometimes you sink/drop a bit, see the water walking stuff in new order
@Mixin(LiquidBlock.class)
public abstract class WaterWalkMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void tracadamia$walkOnWater(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityCollisionContext entityContext) || !(entityContext.getEntity() instanceof Player player) || context.isDescending()) {
            return;
        }

        FluidState fluid = state.getFluidState();
        if (!fluid.is(FluidTags.WATER) || level.getFluidState(pos.above()).is(FluidTags.WATER) || !NewOrderSelf.canWalkOnWater(player)) {
            return;
        }

        VoxelShape surface = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, fluid.getHeight(level, pos), 1.0D);
        if (context.isAbove(surface, pos, true)) {
            cir.setReturnValue(surface);
        }
    }

}

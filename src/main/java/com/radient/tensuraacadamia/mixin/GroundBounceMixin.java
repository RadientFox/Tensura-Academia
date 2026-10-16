package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderGround;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Elastic ground stuff
@Mixin(Entity.class)
public abstract class GroundBounceMixin {

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;updateEntityAfterFallOn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;)V"))
    private void tracadamia$elasticGround(Block block, BlockGetter level, Entity entity) {
        if (!NewOrderGround.bounce(entity)) {
            block.updateEntityAfterFallOn(level, entity);
        }
    }

}

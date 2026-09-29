//package com.radient.tensuraacadamia.mixin;
//
//import com.radient.tensuraacadamia.ability.unique.quirks.ZeroGravityQuirk;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.phys.Vec3;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.ModifyVariable;
//
//@Mixin(LivingEntity.class)
//public abstract class LivingEntityFloatMixin {
//
//    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
//    private Vec3 tracadamia$noFootingWhileFloating(Vec3 input) {
//        LivingEntity entity = (LivingEntity) (Object) this;
//        if (!ZeroGravityQuirk.isFloating(entity)) {
//            return input;
//        }
//
//        return entity instanceof Player ? input.scale(0.5D) : Vec3.ZERO;
//    }
//
//}

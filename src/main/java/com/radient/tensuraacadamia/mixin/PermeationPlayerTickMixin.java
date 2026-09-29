package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.PermeationQuirk;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PermeationPlayerTickMixin {
    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
            opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void tracadamia$maintainPhaseAfterCollisionReset(CallbackInfo callback) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide && PermeationQuirk.isNoClipActive(player)) player.noPhysics = true;
    }
}

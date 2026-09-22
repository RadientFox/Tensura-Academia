package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsOffhands;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public abstract class ServerGamePacketListenerMixin {

    @Shadow(remap = false)
    public ServerPlayer player;

    // Inventory edits for the Quad Arms offhand slots
    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", shift = At.Shift.AFTER, remap = false,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V"),
            cancellable = true, remap = false)
    private void tracadamia$setExtraOffhand(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (QuadArmsOffhands.handleCreativeSlot(this.player, packet.slotNum(), packet.itemStack())) {
            ci.cancel();
        }
    }
}

package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsOffhands;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = CreativeModeInventoryScreen.class, remap = false)
public abstract class CreativeInventoryMixin {

    // Quad Arms offhand slots
    @ModifyArg(method = "selectTab", index = 2, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private int tracadamia$extraOffhandX(Slot target, int index, int x, int y) {
        return QuadArmsOffhands.getCreativeX(target, x);
    }

    @ModifyArg(method = "selectTab", index = 3, remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"))
    private int tracadamia$extraOffhandY(Slot target, int index, int x, int y) {
        return QuadArmsOffhands.getCreativeY(target, y);
    }
}

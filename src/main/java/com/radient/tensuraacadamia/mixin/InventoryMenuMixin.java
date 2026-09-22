package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.QuadArmsOffhands;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryMenu.class, remap = false)
public abstract class InventoryMenuMixin extends RecipeBookMenu<CraftingInput, CraftingRecipe> {

    protected InventoryMenuMixin(MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    // Quad Arms offhand slots
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void tracadamia$addExtraOffhands(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        QuadArmsOffhands.createSlots(owner).forEach(this::addSlot);
    }
}

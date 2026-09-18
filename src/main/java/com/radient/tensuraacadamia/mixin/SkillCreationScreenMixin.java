package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneScreen;
import com.radient.tensuraacadamia.ability.ultimate.afo.SkillCreationScreenAccess;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.client.screen.SkillCreationScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(value = SkillCreationScreen.class, remap = false)
public abstract class SkillCreationScreenMixin implements SkillCreationScreenAccess {
    private static final ResourceLocation AFO_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "textures/skill/ultimate/all_for_one_menu.png");
    private static final ResourceLocation AFO_UI = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "textures/skill/ultimate/all_for_one_ui.png");

    @Shadow private List<ManasSkill> filtered;

    @Accessor("selectedSkill")
    @Override
    public abstract ManasSkill tracadamia$getSelectedSkill();

    @Redirect(method = "renderBg", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"), remap = false)
    private void tracadamia$renderAfoArt(GuiGraphics graphics, ResourceLocation original,
                                         int x, int y, int u, int v, int width, int height) {
        if (!((Object) this instanceof AllForOneScreen)) {
            graphics.blit(original, x, y, u, v, width, height);
        } else if (width == 233) {
            graphics.blit(AFO_BACKGROUND, x, y, 0, 0, width, height, 256, 256);
        } else if (width == 20) {
            graphics.blit(AFO_UI, x, y, 0, 20, 20, 20, 20, 40);
        } else {
            graphics.blit(original, x, y, u, v, width, height);
        }
    }

    @Redirect(method = "renderButtons", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"), remap = false)
    private void tracadamia$renderAfoListRow(GuiGraphics graphics, ResourceLocation texture,
                                             int x, int y, float u, float v,
                                             int width, int height, int textureWidth, int textureHeight) {
        if (!((Object) this instanceof AllForOneScreen)) {
            graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }
        boolean hovered = v > 0.0F;
        graphics.fill(x, y, x + width, y + height, hovered ? 0xFF4A141A : 0xFF220B10);
        graphics.fill(x, y, x + width, y + 1, hovered ? 0xFFB43C43 : 0xFF6C1C24);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF100407);
    }

    @Inject(method = "updateFilteredSkills", at = @At("TAIL"), remap = false)
    private void tracadamia$deduplicateAfoList(CallbackInfo callback) {
        if (!((Object) this instanceof AllForOneScreen)) return;
        Set<ResourceLocation> seen = new HashSet<>();
        List<ManasSkill> unique = new ArrayList<>(filtered.size());
        for (ManasSkill skill : filtered) {
            if (seen.add(skill.getRegistryName())) unique.add(skill);
        }
        filtered = unique;
    }

    @Inject(method = "getSkillName", at = @At("RETURN"), cancellable = true, remap = false)
    private void tracadamia$showCopyCount(ManasSkill skill, CallbackInfoReturnable<MutableComponent> callback) {
        if (!((Object) this instanceof AllForOneScreen)) return;
        MutableComponent name = callback.getReturnValue();
        if (name.getStyle().getColor() == null) {
            name = name.copy().withStyle(ChatFormatting.WHITE);
        }
        int copies = ((AllForOneScreen) (Object) this).getSkillCount(skill);
        if (copies > 1) name = name.copy().append(" x" + copies);
        callback.setReturnValue(name);
    }
}

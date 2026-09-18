package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.client.screen.SkillCreationScreen;
import io.github.manasmods.tensura.menu.SkillCreationMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Tensura's creator screen with only the All For One action and artwork substituted. */
public final class AllForOneScreen extends SkillCreationScreen {
    private static final ResourceLocation SKILL_AMOUNT = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "textures/skill/ultimate/skillamount.png");
    private final Map<ManasSkill, Integer> skillCounts = new IdentityHashMap<>();

    public AllForOneScreen(SkillCreationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 143;
        for (ManasSkill skill : menu.getSkills()) {
            skillCounts.merge(skill, 1, Integer::sum);
        }
    }

    public int getSkillCount(ManasSkill skill) {
        return skillCounts.getOrDefault(skill, 0);
    }

    @Override
    public void renderScrollBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getScrollBarX();
        int y = getScrollBarY() + (int) ((getScrollBarTotalSpace() - getScrollBarHeight()) * getScrollOffset());
        int width = getScrollBarWidth();
        int height = getScrollBarHeight();
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        graphics.fill(x, y, x + width, y + height, hovered ? 0xFF8C3038 : 0xFF5A2029);
        graphics.fill(x, y, x + width, y + 1, hovered ? 0xFFC65B61 : 0xFF9C4148);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF180609);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        ManasSkill selected = ((SkillCreationScreenAccess) (Object) this).tracadamia$getSelectedSkill();
        if (selected != null) {
            int copies = getSkillCount(selected);
            graphics.blit(SKILL_AMOUNT, leftPos + 190, topPos + 10, 125, 9, 23, 22, 256, 256);
            String amount = Integer.toString(copies);
            float scale = Math.min(1.0F, 18.0F / Math.max(1, font.width(amount)));
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + 214, topPos + 16, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, amount, 0, 0, 0xFFF4DDE0, false);
            graphics.pose().popPose();
        }
        int x = leftPos + 162;
        int y = topPos + 116;
        boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        boolean enabled = menu.getMode() != 2 && selected != null;
        graphics.fill(x, y, x + 20, y + 20, enabled ? hovered ? 0xFF5B1820 : 0xFF2C0B11 : 0xFF18080B);
        graphics.fill(x, y, x + 20, y + 1, enabled ? 0xFFA3313A : 0xFF622029);
        graphics.fill(x, y + 19, x + 20, y + 20, 0xFF5C151C);
        graphics.fill(x, y, x + 1, y + 20, 0xFF8B252E);
        graphics.fill(x + 19, y, x + 20, y + 20, 0xFF8B252E);
        if (enabled) graphics.drawCenteredString(font, menu.getMode() == 0 ? "S" : "T", x + 10, y + 6, 0xFFEBC9CB);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX >= leftPos + 162 && mouseX < leftPos + 182
                && mouseY >= topPos + 116 && mouseY < topPos + 136
                && ((SkillCreationScreenAccess) (Object) this).tracadamia$getSelectedSkill() != null
                && menu.getMode() != 2) {
            graphics.renderTooltip(font, Component.translatable(menu.getMode() == 0
                    ? "tracadamia.menu.all_for_one.steal"
                    : "tracadamia.menu.all_for_one.transfer"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 162 && mouseX < leftPos + 182
                && mouseY >= topPos + 116 && mouseY < topPos + 136) {
            ManasSkill selected = ((SkillCreationScreenAccess) (Object) this).tracadamia$getSelectedSkill();
            if (selected == null) return true;
            if (menu.getMode() == 2) return true;
            int index = menu.getSkills().indexOf(selected);
            if (index < 0 || minecraft == null || minecraft.getConnection() == null) return true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, index));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
    public static final class Opening {
        private Opening() {
        }

        @SubscribeEvent
        public static void replace(ScreenEvent.Opening event) {
            if (event.getNewScreen() instanceof SkillCreationScreen original
                    && !(original instanceof AllForOneScreen)
                    && original.getMenu().getAbility() == QuirkSkills.ALL_FOR_ONE.get()
                    && Minecraft.getInstance().player != null) {
                event.setNewScreen(new AllForOneScreen(original.getMenu(),
                        Minecraft.getInstance().player.getInventory(), original.getTitle()));
            }
        }
    }
}

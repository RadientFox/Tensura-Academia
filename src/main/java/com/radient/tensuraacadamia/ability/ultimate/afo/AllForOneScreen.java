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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tensura's creator screen with only the All For One action and artwork substituted. */
public final class AllForOneScreen extends SkillCreationScreen {
    private static final ResourceLocation SKILL_AMOUNT = ResourceLocation.fromNamespaceAndPath(
            TensuraAcadamia.MODID, "textures/skill/ultimate/skillamount.png");
    private static final ResourceLocation[] COUNT_DIGITS = new ResourceLocation[10];

    static {
        for (int digit = 0; digit < COUNT_DIGITS.length; digit++) {
            COUNT_DIGITS[digit] = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID,
                    "textures/skill/ultimate/skillamountnumbers/" + digit + ".png");
        }
    }

    private final Map<ManasSkill, Integer> skillCounts = new IdentityHashMap<>();
    private final Set<ResourceLocation> selectedStockpile = new HashSet<>();

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

    public boolean isStockpileSelection(ManasSkill skill) {
        return menu.getMode() == 2 && selectedStockpile.contains(skill.getRegistryName());
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
            float scale = Math.min(0.5F, 18.0F / (amount.length() * 16.0F));
            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + 214, topPos + 16, 0);
            graphics.pose().scale(scale, scale, 1.0F);
            for (int i = 0; i < amount.length(); i++) {
                graphics.blit(COUNT_DIGITS[amount.charAt(i) - '0'], i * 16, 0, 0, 0, 16, 16, 16, 16);
            }
            graphics.pose().popPose();
        }
        int x = leftPos + 162;
        int y = topPos + 116;
        boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        boolean stockpile = menu.getMode() == 2;
        boolean enabled = selected != null;
        graphics.fill(x, y, x + 20, y + 20, enabled ? hovered ? 0xFF5B1820 : 0xFF2C0B11 : 0xFF18080B);
        graphics.fill(x, y, x + 20, y + 1, enabled ? 0xFFA3313A : 0xFF622029);
        graphics.fill(x, y + 19, x + 20, y + 20, 0xFF5C151C);
        graphics.fill(x, y, x + 1, y + 20, 0xFF8B252E);
        graphics.fill(x + 19, y, x + 20, y + 20, 0xFF8B252E);
        if (enabled) {
            String label = stockpile ? isStockpileSelection(selected) ? "-" : "+" : menu.getMode() == 0 ? "S" : "T";
            graphics.drawCenteredString(font, label, x + 10, y + 6, 0xFFEBC9CB);
        }
        if (stockpile) {
            int confirmX = leftPos + 184;
            boolean confirmEnabled = !selectedStockpile.isEmpty();
            boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + 44 && mouseY >= y && mouseY < y + 20;
            graphics.fill(confirmX, y, confirmX + 44, y + 20,
                    confirmEnabled ? confirmHovered ? 0xFF7A2914 : 0xFF45160C : 0xFF18080B);
            graphics.drawCenteredString(font, "FIRE", confirmX + 22, y + 6,
                    confirmEnabled ? 0xFFFFD7A1 : 0xFF725449);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX >= leftPos + 162 && mouseX < leftPos + 182
                && mouseY >= topPos + 116 && mouseY < topPos + 136
                && ((SkillCreationScreenAccess) (Object) this).tracadamia$getSelectedSkill() != null) {
            if (menu.getMode() == 2) {
                graphics.renderTooltip(font, Component.literal("Select or remove this ability"), mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, Component.translatable(menu.getMode() == 0
                        ? "tracadamia.menu.all_for_one.steal"
                        : "tracadamia.menu.all_for_one.transfer"), mouseX, mouseY);
            }
        }
        if (menu.getMode() == 2 && mouseX >= leftPos + 184 && mouseX < leftPos + 228
                && mouseY >= topPos + 116 && mouseY < topPos + 136) {
            graphics.renderTooltip(font, Component.literal("Consume selected abilities and fire Stockpile Attack"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 162 && mouseX < leftPos + 182
                && mouseY >= topPos + 116 && mouseY < topPos + 136) {
            ManasSkill selected = ((SkillCreationScreenAccess) (Object) this).tracadamia$getSelectedSkill();
            if (selected == null) return true;
            int index = menu.getSkills().indexOf(selected);
            if (index < 0 || minecraft == null || minecraft.getConnection() == null) return true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, index));
            if (menu.getMode() == 2) {
                if (!selectedStockpile.add(selected.getRegistryName())) selectedStockpile.remove(selected.getRegistryName());
            }
            return true;
        }
        if (button == 0 && menu.getMode() == 2 && mouseX >= leftPos + 184 && mouseX < leftPos + 228
                && mouseY >= topPos + 116 && mouseY < topPos + 136) {
            if (selectedStockpile.isEmpty() || minecraft == null || minecraft.getConnection() == null) return true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId,
                    AllForOneMenu.CONFIRM_STOCKPILE_BUTTON));
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

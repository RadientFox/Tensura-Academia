package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.authlib.GameProfile;
import io.github.manasmods.tensura.client.screen.templates.IScrollBar;
import io.github.manasmods.tensura.util.client.RenderHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DoubleScreen extends AbstractContainerScreen<DoubleMenu> implements IScrollBar {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/skill_creation/skill_creation.png");
    private static final ResourceLocation ABILITY_BAR =
            ResourceLocation.fromNamespaceAndPath("tensura", "textures/gui/ability_button.png");
    private static final int VISIBLE_ROWS = 7;
    private static final int ROW_HEIGHT = 13;

    private List<DoubleMenu.Entry> filtered = List.of();
    private DoubleMenu.Entry selectedEntry;
    private EditBox searchField;
    private Button forgetButton;
    private UUID forgetConfirmation;
    private String nameFilter = "";
    private float scrollOffset;
    private boolean scrolling;
    private int listStartIndex;
    private int textStartIndex;
    private final Map<UUID, LivingEntity> portraits = new HashMap<>();

    public DoubleScreen(DoubleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 233;
        imageHeight = 140;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0.0F;
        listStartIndex = 0;
        textStartIndex = 0;
        scrolling = false;
        searchField = new EditBox(font, leftPos + 19, topPos + 27, 79, 9, Component.empty());
        searchField.setBordered(false);
        searchField.setMaxLength(128);
        searchField.setResponder(value -> {
            nameFilter = value;
            updateFiltered();
        });
        addRenderableWidget(searchField);
        forgetButton = addRenderableWidget(Button.builder(Component.translatable(
                "tracadamia.skill.double.menu.forget"), button -> forgetSelected())
                .bounds(leftPos + 124, topPos + 115, 98, 18).build());
        updateFiltered();
        searchField.setValue(nameFilter);
    }

    private void updateFiltered() {
        String filter = nameFilter == null ? "" : nameFilter.toLowerCase(Locale.ROOT);
        filtered = menu.entries().stream()
                .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        if (selectedEntry == null || !filtered.contains(selectedEntry)) {
            selectedEntry = filtered.stream().filter(entry -> entry.id().equals(menu.selectedId()))
                    .findFirst().orElse(filtered.isEmpty() ? null : filtered.getFirst());
        }
        listStartIndex = 0;
        scrollOffset = 0.0F;
        textStartIndex = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        forgetButton.active = selectedEntry != null && !selectedEntry.self();
        forgetButton.setMessage(Component.translatable(selectedEntry != null
                && selectedEntry.id().equals(forgetConfirmation)
                ? "tracadamia.skill.double.menu.confirm_forget" : "tracadamia.skill.double.menu.forget"));
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void forgetSelected() {
        if (selectedEntry == null || selectedEntry.self()) return;
        if (!selectedEntry.id().equals(forgetConfirmation)) {
            forgetConfirmation = selectedEntry.id();
            return;
        }
        int index = menu.entries().indexOf(selectedEntry);
        if (index >= 0 && minecraft != null && minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId,
                    menu.entries().size() + index));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RenderHelper.drawCenteredText(graphics, font, title, 56, titleLabelY, 0xFFFFFFFF, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        renderEntries(graphics, mouseX, mouseY);
        IScrollBar.super.renderScrollBar(graphics, mouseX, mouseY);
        renderSelectedIcon(graphics);
        renderDetails(graphics, mouseX, mouseY);
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        int end = Math.min(listStartIndex + VISIBLE_ROWS, filtered.size());
        for (int index = listStartIndex; index < end; index++) {
            DoubleMenu.Entry entry = filtered.get(index);
            int x = leftPos + 6;
            int y = topPos + 43 + (index - listStartIndex) * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + 89 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean selected = selectedEntry != null && selectedEntry.id().equals(entry.id());
            graphics.blit(ABILITY_BAR, x, y, 0.0F, hovered || selected ? 13.0F : 0.0F,
                    89, ROW_HEIGHT, 89, 26);
            String label = entry.name() + (entry.self() ? "  YOU" : "");
            int color = selected ? 0xFF9BE8E5 : 0xFFFFFFFF;
            String shortened = font.plainSubstrByWidth(label, 82);
            graphics.drawString(font, shortened, x + 3, y + 3, color, false);
        }
    }

    private void renderSelectedIcon(GuiGraphics graphics) {
        if (selectedEntry == null) return;
        int x = leftPos + 156;
        int y = topPos + 6;
        if (selectedEntry.player()) {
            PlayerSkin skin = null;
            if (minecraft != null && minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(selectedEntry.id());
                if (info != null) skin = info.getSkin();
            }
            if (skin == null && minecraft != null) {
                GameProfile profile = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, selectedEntry.profile()).result()
                        .map(ResolvableProfile::gameProfile)
                        .orElseGet(() -> new GameProfile(selectedEntry.id(), selectedEntry.name()));
                skin = minecraft.getSkinManager().getInsecureSkin(profile);
            }
            if (skin == null) skin = DefaultPlayerSkin.get(new GameProfile(selectedEntry.id(), selectedEntry.name()));
            PlayerFaceRenderer.draw(graphics, skin, x, y, 32);
        } else {
            LivingEntity portrait = portraits.computeIfAbsent(selectedEntry.id(), ignored -> {
                if (minecraft == null || minecraft.level == null) return null;
                Entity entity = BuiltInRegistries.ENTITY_TYPE.get(selectedEntry.entityType()).create(minecraft.level);
                return entity instanceof LivingEntity living ? living : null;
            });
            if (portrait != null && minecraft.getEntityRenderDispatcher().getRenderer(portrait) != null) {
                int scale = (int) (28.0F / Math.max(0.4F, Math.min(portrait.getBbWidth(), portrait.getBbHeight())));
                float offset = (portrait.getEyeHeight() - portrait.getBbHeight() / 2.0F) / portrait.getScale();
                InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + 32, y + 32,
                        scale, offset, 0.0F, 0.0F, portrait);
            } else {
                PlayerFaceRenderer.draw(graphics, DefaultPlayerSkin.get(selectedEntry.id()), x, y, 32);
            }
        }
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedEntry == null) return;
        Component details = Component.literal(selectedEntry.name()).append("\n")
                .append(Component.literal("Type: ")).append(Component.literal(selectedEntry.self() ? "Self" :
                        selectedEntry.player() ? "Player" : "Entity")).append("\n")
                .append(Component.literal("Unlocked"))
                .append("\n").append(Component.literal("Studying: ")).append(Component.literal(menu.researchName()))
                .append("\n").append(Component.literal("Research: ")).append(Component.literal(menu.completedStacks() + "/3"))
                .append("\n").append(Component.literal("Hold progress: "))
                .append(Component.literal(menu.progressPercent() + "%"))
                .append("\n").append(Component.literal("Non-self doubles: "))
                .append(Component.literal(menu.livingNonSelf() + "/" + menu.duplicateLimit()));
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(details, 94);
        int maxStart = Math.max(0, lines.size() - 7);
        textStartIndex = Math.max(0, Math.min(maxStart, textStartIndex));
        int end = Math.min(lines.size(), textStartIndex + 7);
        boolean hovered = mouseX >= leftPos + 125 && mouseX < leftPos + 219
                && mouseY >= topPos + 43 && mouseY < topPos + 109;
        RenderHelper.drawScrollableTextInAreaSetHighlight(graphics, font, lines,
                leftPos + 125, topPos + 43, 94, 66, 0, textStartIndex, end, hovered, 0x857A15, 0x0D28D2);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (IScrollBar.super.clickedScrollBar(mouseX, mouseY)) return true;
        if (searchField != null && !searchField.isHovered()) searchField.setFocused(false);
        if (button == 0) {
            int end = Math.min(listStartIndex + VISIBLE_ROWS, filtered.size());
            for (int index = listStartIndex; index < end; index++) {
                int x = leftPos + 6;
                int y = topPos + 43 + (index - listStartIndex) * ROW_HEIGHT;
                if (mouseX < x || mouseY < y || mouseX >= x + 89 || mouseY >= y + ROW_HEIGHT) continue;
                selectedEntry = filtered.get(index);
                forgetConfirmation = null;
                textStartIndex = 0;
                int serverIndex = menu.entries().indexOf(selectedEntry);
                if (serverIndex >= 0 && minecraft != null && minecraft.getConnection() != null) {
                    menu.setSelectedId(selectedEntry.id());
                    minecraft.getConnection().send(new ServerboundContainerButtonClickPacket(menu.containerId, serverIndex));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isScrolling()) return IScrollBar.super.draggedScrollBar(mouseY);
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isScrolling()) {
            setScrolling(false);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + 125 && mouseX < leftPos + 219
                && mouseY >= topPos + 43 && mouseY < topPos + 109) {
            textStartIndex = Math.max(0, textStartIndex + (int) -scrollY);
            return true;
        }
        if (IScrollBar.super.scrolledScrollBar(scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public int getScrollBarX() {
        return leftPos + 98;
    }

    @Override
    public int getScrollBarY() {
        return topPos + 43;
    }

    @Override
    public int getScrollBarTotalSpace() {
        return 91;
    }

    @Override
    public int getScrollBarListSize() {
        return filtered.size();
    }

    @Override
    public int getScrollBarRenderCount() {
        return VISIBLE_ROWS;
    }

    @Override
    public float getScrollOffset() {
        return scrollOffset;
    }

    @Override
    public boolean isScrolling() {
        return scrolling;
    }

    @Override
    public void setListStartIndex(int index) {
        listStartIndex = Math.max(0, Math.min(Math.max(0, filtered.size() - VISIBLE_ROWS), index));
    }

    @Override
    public void setScrolling(boolean scrolling) {
        this.scrolling = scrolling;
    }

    @Override
    public void setScrollOffset(float offset) {
        scrollOffset = offset;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

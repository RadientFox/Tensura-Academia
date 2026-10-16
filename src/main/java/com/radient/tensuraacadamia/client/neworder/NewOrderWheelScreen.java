package com.radient.tensuraacadamia.client.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderRule;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderRules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class NewOrderWheelScreen extends Screen {

    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation CHECKMARK = ResourceLocation.withDefaultNamespace("icon/checkmark");
    private static final int BOX = 30;
    private static final int ICON = 16;
    private static final int CENTER = 32;
    private static final int GAP = 54;
    private static final int MIN_RADIUS = 80;
    private static final int LABEL_WIDTH = 84;
    private static final int MIN_LABEL_WIDTH = 48;
    private static final int LABEL_LINES = 3;
    private static final int LABEL_ROOM = 34;
    private static final int KEYS = 10;
    private static final int TITLE_Y = 12;
    private static final int TEXT_BOTTOM = TITLE_Y + 36;
    private static final int RING_POINTS = 90;
    private static final int RING = 0x60A0A0A0;
    private static final int TOOLTIP_WIDTH = 200;

    private record Entry(NewOrderRule rule, int slot) {}

    private NewOrderPayloads.RulesPayload state;
    private final List<Entry> entries = new ArrayList<>();
    private long received;

    public NewOrderWheelScreen(NewOrderPayloads.RulesPayload state) {
        super(Component.translatable("tracadamia.skill.new_order"));
        update(state);
    }

    public void update(NewOrderPayloads.RulesPayload state) {
        this.state = state;
        Minecraft minecraft = Minecraft.getInstance();
        this.received = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        this.entries.clear();
        if (state.kind() == NewOrderRule.NONE) {
            for (int slot = 0; slot < state.rules().size(); slot++) {
                this.entries.add(new Entry(getRule(state.rules().get(slot)), slot));
            }
        } else {
            for (NewOrderRule rule : NewOrderRule.values()) {
                if (rule.getKind() == state.kind()) {
                    this.entries.add(new Entry(rule, getSlot(rule)));
                }
            }
        }
    }

    private static NewOrderRule getRule(NewOrderPayloads.RuleEntry entry) {
        NewOrderRule rule = NewOrderRule.byIndex(entry.rule());
        return rule == null ? NewOrderRule.ENHANCE : rule;
    }

    private int getSlot(NewOrderRule rule) {
        int found = -1;
        for (int slot = 0; slot < this.state.rules().size(); slot++) {
            NewOrderPayloads.RuleEntry entry = this.state.rules().get(slot);
            if (getRule(entry) != rule) {
                continue;
            }
            if (rule.getKind() != NewOrderRule.ENTITY || entry.target().getString().equals(this.state.name().getString())) {
                return slot;
            }
            if (found < 0) {
                found = slot;
            }
        }
        return rule.getKind() == NewOrderRule.ENTITY ? -1 : found;
    }

    private static ResourceLocation getIcon(NewOrderRule rule) {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order/" + rule.getId());
    }

    private Item getBlockItem() {
        ResourceLocation id = ResourceLocation.tryParse(this.state.block());
        Item item = id == null ? Items.AIR : BuiltInRegistries.BLOCK.get(id).asItem();
        return item == Items.AIR ? Items.DIRT : item;
    }

    private @Nullable Component getBlocker(Entry entry) {
        if (this.state.kind() == NewOrderRule.NONE) {
            return Component.translatable("tracadamia.skill.new_order.no_name");
        }
        if ((entry.rule() == NewOrderRule.REBEL || entry.rule() == NewOrderRule.WILL_DIE) && !this.state.mastered()) {
            return Component.translatable("tracadamia.skill.new_order.needs_mastery");
        }
        if (entry.slot() >= 0) {
            return Component.translatable("tracadamia.skill.new_order.already", entry.rule().getName());
        }
        return null;
    }

    private boolean canRevoke(Entry entry) {
        return entry.slot() >= 0 && (entry.rule() != NewOrderRule.REBEL || this.state.rules().get(entry.slot()).rebelTicks() < 0);
    }

    private int getCenterY() {
        return (this.height + TEXT_BOTTOM) / 2;
    }

    private int getRadius() {
        int fits = (int) (this.entries.size() * (BOX + GAP) / Mth.TWO_PI);
        return Math.min(Math.max(MIN_RADIUS, fits), Math.max(BOX, Math.min(this.width, this.height - TEXT_BOTTOM) / 2 - BOX / 2 - LABEL_ROOM));
    }

    private int getLabelWidth() {
        int spacing = (int) (getRadius() * Mth.TWO_PI / Math.max(1, this.entries.size()));
        return Mth.clamp(spacing - 6, MIN_LABEL_WIDTH, LABEL_WIDTH);
    }

    private float getAngle(int index) {
        return -Mth.HALF_PI + index * Mth.TWO_PI / Math.max(1, this.entries.size());
    }

    private int getX(int index) {
        return this.width / 2 + Math.round(Mth.cos(getAngle(index)) * getRadius());
    }

    private int getY(int index) {
        return getCenterY() + Math.round(Mth.sin(getAngle(index)) * getRadius());
    }

    private int getHovered(double mouseX, double mouseY) {
        for (int index = 0; index < this.entries.size(); index++) {
            if (Math.abs(mouseX - getX(index)) <= BOX / 2.0D && Math.abs(mouseY - getY(index)) <= BOX / 2.0D) {
                return index;
            }
        }
        return -1;
    }

    private long getRebelSeconds(NewOrderPayloads.RuleEntry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level == null ? this.received : minecraft.level.getGameTime();
        return (Math.max(0L, entry.rebelTicks() - (now - this.received)) + 19L) / 20L;
    }

    private Component describe(NewOrderPayloads.RuleEntry entry) {
        NewOrderRule rule = getRule(entry);
        MutableComponent text = rule.getKind() == NewOrderRule.ENTITY ? Component.translatable("tracadamia.skill.new_order.rule_on", rule.getName(), entry.target()) : rule.getName().copy();
        if (entry.rebelTicks() >= 0) {
            text = Component.translatable("tracadamia.skill.new_order.seconds", text, getRebelSeconds(entry));
        }
        return text;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = getCenterY();
        int radius = getRadius();
        for (int point = 0; point < RING_POINTS; point++) {
            float angle = point * Mth.TWO_PI / RING_POINTS;
            int x = centerX + Math.round(Mth.cos(angle) * radius);
            int y = centerY + Math.round(Mth.sin(angle) * radius);
            graphics.fill(x - 1, y - 1, x + 1, y + 1, RING);
        }

        if (this.state.kind() != NewOrderRule.NONE) {
            drawNamed(graphics, centerX - CENTER / 2, centerY - CENTER / 2 - 6);
            String name = this.font.plainSubstrByWidth(this.state.name().getString(), radius * 2 - BOX * 2);
            graphics.drawCenteredString(this.font, name, centerX, centerY + CENTER / 2, 0xFFFFFF);
        }

        int hovered = getHovered(mouseX, mouseY);
        for (int index = 0; index < this.entries.size(); index++) {
            drawEntry(graphics, this.entries.get(index), getX(index), getY(index), index == hovered, index + 1);
        }

        graphics.drawCenteredString(this.font, this.title, centerX, TITLE_Y, 0xFFFFFF);
        Component hint = Component.translatable("tracadamia.skill.new_order.pick", this.state.name());
        graphics.drawCenteredString(this.font, hint, centerX, TITLE_Y + 12, 0xFFFFFF);
//        graphics.drawCenteredString(this.font, getInEffect(), centerX, TITLE_Y + 23, 0xA0A0A0);

        if (hovered >= 0) {
            graphics.renderTooltip(this.font, getTooltip(this.entries.get(hovered)), mouseX, mouseY);
        }
    }

//    private Component getInEffect() {
//        if (this.state.rules().isEmpty()) {
//            return Component.translatable("tracadamia.skill.new_order.in_effect_none");
//        }
//
//        MutableComponent rules = Component.empty();
//        for (int slot = 0; slot < this.state.rules().size(); slot++) {
//            if (slot > 0) {
//                rules.append(", ");
//            }
//            rules.append(describe(this.state.rules().get(slot)));
//        }
//        return Component.translatable("tracadamia.skill.new_order.in_effect", rules);
//    }

    private List<FormattedCharSequence> getTooltip(Entry entry) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component name = entry.slot() >= 0 ? describe(this.state.rules().get(entry.slot())) : entry.rule().getName();
        lines.add(name.copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
        lines.addAll(this.font.split(entry.rule().getDescription().withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        Component blocker = getBlocker(entry);
        if (blocker != null && entry.slot() < 0) {
            lines.addAll(this.font.split(blocker.copy().withStyle(ChatFormatting.RED), TOOLTIP_WIDTH));
        }
        if (entry.slot() >= 0) {
            lines.addAll(this.font.split(Component.translatable(canRevoke(entry) ? "tracadamia.skill.new_order.revoke_hint" : "tracadamia.skill.new_order.rebel_locked")
                    .withStyle(canRevoke(entry) ? ChatFormatting.YELLOW : ChatFormatting.RED), TOOLTIP_WIDTH));
        }
        return lines;
    }

    private void drawEntry(GuiGraphics graphics, Entry entry, int x, int y, boolean hovered, int number) {
        int left = x - BOX / 2;
        int top = y - BOX / 2;
        boolean made = entry.slot() >= 0;
        boolean usable = getBlocker(entry) == null || made && canRevoke(entry);
        graphics.blitSprite(!usable ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON, left, top, BOX, BOX);
        graphics.blitSprite(getIcon(entry.rule()), x - ICON / 2, y - ICON / 2, ICON, ICON);
        if (made) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.blitSprite(CHECKMARK, left + BOX - 11, top + 2, 9, 8);
            graphics.pose().popPose();
        }
        if (number <= KEYS && this.state.kind() != NewOrderRule.NONE) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            graphics.drawString(this.font, String.valueOf(number % KEYS), left + 3, top + 2, 0xA0A0A0);
            graphics.pose().popPose();
        }

        int color = made ? 0x55FF55 : usable ? 0xFFFFFF : 0x909090;
        List<FormattedCharSequence> label = this.font.split(entry.rule().getName(), getLabelWidth());
        for (int line = 0; line < Math.min(LABEL_LINES, label.size()); line++) {
            FormattedCharSequence text = label.get(line);
            graphics.drawString(this.font, text, x - this.font.width(text) / 2, top + BOX + 3 + line * 10, color);
        }
    }

    private void drawNamed(GuiGraphics graphics, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (this.state.kind()) {
            case NewOrderRule.USER -> {
                if (minecraft.player != null) {
                    PlayerFaceRenderer.draw(graphics, minecraft.player.getSkin(), x, y, CENTER);
                }
            }
            case NewOrderRule.ENTITY -> {
                if (minecraft.level != null && minecraft.level.getEntity(this.state.entityId()) instanceof LivingEntity living) {
                    PlayerInfo info = living instanceof Player player && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(player.getUUID()) : null;
                    if (info != null) {
                        PlayerFaceRenderer.draw(graphics, info.getSkin(), x, y, CENTER);
                    } else {
                        int scale = Math.max(2, (int) (CENTER * 0.8F / Math.max(living.getBbHeight(), living.getBbWidth())));
                        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + CENTER, y + CENTER, scale, 0.0F, 0.6F, 0.0F, living);
                    }
                }
            }
            case NewOrderRule.GROUND -> drawLargeItem(graphics, getBlockItem(), x, y);
            default -> drawLargeItem(graphics, Items.WIND_CHARGE, x, y);
        }
    }

    private void drawLargeItem(GuiGraphics graphics, Item item, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(CENTER / (float) ICON, CENTER / (float) ICON, 1.0F);
        graphics.renderItem(new ItemStack(item), 0, 0);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = getHovered(mouseX, mouseY);
        if (index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Entry entry = this.entries.get(index);
        if (button == 1) {
            revoke(entry);
        } else if (button == 0) {
            choose(entry);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int index = keyCode == GLFW.GLFW_KEY_0 ? KEYS - 1 : keyCode - GLFW.GLFW_KEY_1;
        if (index >= 0 && index < Math.min(KEYS, this.entries.size())) {
            choose(this.entries.get(index));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void choose(Entry entry) {
        if (getBlocker(entry) != null) {
            click(0.6F);
            return;
        }

        PacketDistributor.sendToServer(new NewOrderPayloads.RuleChoicePayload(NewOrderRules.MAKE, entry.rule().ordinal()));
        click(1.0F);
        this.onClose();
    }

    private void revoke(Entry entry) {
        if (!canRevoke(entry)) {
            click(0.6F);
            return;
        }

        PacketDistributor.sendToServer(new NewOrderPayloads.RuleChoicePayload(NewOrderRules.REVOKE, entry.slot()));
        click(0.8F);
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}

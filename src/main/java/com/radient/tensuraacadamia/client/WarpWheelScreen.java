package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.WarpingQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

//Im probaly gonna reuse this for another quirk
public class WarpWheelScreen extends Screen {

    private static final int BOX = 30;
    private static final int ICON = 24;
    private static final int GAP = 16;
    private static final int MIN_RADIUS = 70;
    private static final int LABEL_ROOM = 12;
    private static final int TITLE_Y = 12;
    private static final int TEXT_BOTTOM = TITLE_Y + 36;
    private static final int RING_POINTS = 90;
    private static final int RING = 0x50A8A8A8;
    private static final int BACKING = 0xB0121212;
    private static final int EDGE = 0xFF5E5E5E;
    private static final int HOVERED = 0xFFFFFFFF;
    private static final int PICKED = 0xFF55FF55;
    private static final int CHOSEN = 0xFFFFAA00;
    private static final int LINK = 0xC055FF55;

    private final int wheel;
    private final List<WarpingQuirk.WheelEntry> entries;
    private final @Nullable UUID chosen;
    private @Nullable WarpingQuirk.WheelEntry first;

    public WarpWheelScreen(WarpingQuirk.WheelPayload payload) {
        super(Component.translatable(switch (payload.wheel()) {
            case WarpingQuirk.WHEEL_GOOP -> "tracadamia.skill.mode.warping.goop_defense";
            case WarpingQuirk.WHEEL_EMERGENCY -> "tracadamia.skill.mode.warping.emergency_exit";
            default -> "tracadamia.skill.mode.warping.warping";
        }));
        this.wheel = payload.wheel();
        this.entries = payload.entries();
        this.chosen = payload.chosen().orElse(null);
    }

    private int getCenterY() {
        return (this.height + TEXT_BOTTOM) / 2;
    }

    private int getRadius() {
        int fits = (int) (this.entries.size() * (BOX + GAP) / Mth.TWO_PI);
        return Math.min(Math.max(MIN_RADIUS, fits), Math.max(BOX, Math.min(this.width, this.height - TEXT_BOTTOM) / 2 - BOX / 2 - LABEL_ROOM));
    }

    private int getX(int index) {
        return this.width / 2 + Math.round(Mth.cos(getAngle(index)) * getRadius());
    }

    private int getY(int index) {
        return getCenterY() + Math.round(Mth.sin(getAngle(index)) * getRadius());
    }

    private float getAngle(int index) {
        return -Mth.HALF_PI + index * Mth.TWO_PI / Math.max(1, this.entries.size());
    }

    private int getHovered(double mouseX, double mouseY) {
        for (int index = 0; index < this.entries.size(); index++) {
            if (Math.abs(mouseX - getX(index)) <= BOX / 2.0D && Math.abs(mouseY - getY(index)) <= BOX / 2.0D) {
                return index;
            }
        }
        return -1;
    }

    private Component getName(WarpingQuirk.WheelEntry entry) {
        return entry.kind() == WarpingQuirk.SELF ? Component.translatable("tracadamia.skill.warping.you") : Component.literal(entry.name());
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

        int hovered = getHovered(mouseX, mouseY);
        int firstIndex = this.first == null ? -1 : this.entries.indexOf(this.first);
        if (firstIndex >= 0 && hovered >= 0 && hovered != firstIndex) {
            drawLink(graphics, getX(firstIndex), getY(firstIndex), getX(hovered), getY(hovered));
        }

        for (int index = 0; index < this.entries.size(); index++) {
            WarpingQuirk.WheelEntry entry = this.entries.get(index);
            int edge = index == hovered ? HOVERED : index == firstIndex ? PICKED : entry.id().equals(this.chosen) ? CHOSEN : EDGE;
            drawEntry(graphics, entry, getX(index), getY(index), edge);
        }

        graphics.drawCenteredString(this.font, this.title, centerX, TITLE_Y, 0xD0D0D0);
        for (int line = 0; line < 2; line++) {
            Component text = getHint(line);
            if (text != null) {
                graphics.drawCenteredString(this.font, text, centerX, TITLE_Y + 12 + line * 11, line == 0 ? 0xFFFFFF : 0xAAAAAA);
            }
        }
    }

    private @Nullable Component getHint(int line) {
        return switch (this.wheel) {
            case WarpingQuirk.WHEEL_GOOP -> line == 0 ? Component.translatable("tracadamia.skill.warping.pick_goop") : Component.translatable("tracadamia.skill.warping.pick_goop_random");
            case WarpingQuirk.WHEEL_EMERGENCY -> line == 0 ? Component.translatable("tracadamia.skill.warping.pick_emergency") : null;
            default -> this.first == null
                    ? line == 0 ? Component.translatable("tracadamia.skill.warping.pick_subject") : null
                    : line == 0 ? Component.translatable("tracadamia.skill.warping.pick_destination", getName(this.first)) : Component.translatable("tracadamia.skill.warping.back");
        };
    }

    private void drawLink(GuiGraphics graphics, int fromX, int fromY, int toX, int toY) {
        int steps = (int) (Math.sqrt((toX - fromX) * (toX - fromX) + (toY - fromY) * (toY - fromY)) / 4.0D);
        for (int step = 1; step < steps; step++) {
            int x = fromX + (toX - fromX) * step / steps;
            int y = fromY + (toY - fromY) * step / steps;
            graphics.fill(x - 1, y - 1, x + 1, y + 1, LINK);
        }
    }

    private void drawEntry(GuiGraphics graphics, WarpingQuirk.WheelEntry entry, int x, int y, int edge) {
        int left = x - BOX / 2;
        int top = y - BOX / 2;
        graphics.fill(left - 1, top - 1, left + BOX + 1, top + BOX + 1, edge);
        graphics.fill(left, top, left + BOX, top + BOX, BACKING);
        drawIcon(graphics, entry, x - ICON / 2, y - ICON / 2);

        Component name = getName(entry);
        String shown = this.font.plainSubstrByWidth(name.getString(), BOX * 3);
        graphics.drawCenteredString(this.font, shown, x, top + BOX + 3, entry.kind() == WarpingQuirk.CREATURE ? 0xCCCCCC : 0xFFFFFF);
    }

    private void drawIcon(GuiGraphics graphics, WarpingQuirk.WheelEntry entry, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entry.kind() != WarpingQuirk.CREATURE) {
            ClientPacketListener connection = minecraft.getConnection();
            PlayerInfo info = connection == null ? null : connection.getPlayerInfo(entry.id());
            PlayerSkin skin = info == null ? DefaultPlayerSkin.get(entry.id()) : info.getSkin();
            PlayerFaceRenderer.draw(graphics, skin, x, y, ICON);
            return;
        }

        if (minecraft.level != null && minecraft.level.getEntity(entry.entityId()) instanceof LivingEntity living) {
            int scale = Math.max(2, (int) (ICON * 0.8F / Math.max(living.getBbHeight(), living.getBbWidth())));
            InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + ICON, y + ICON, scale, 0.0F, 0.6F, 0.0F, living);
            return;
        }

        graphics.drawCenteredString(this.font, entry.name().isEmpty() ? "?" : entry.name().substring(0, 1).toUpperCase(), x + ICON / 2, y + ICON / 2 - 4, 0xBBBBBB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.first != null) {
            this.first = null;
            click(0.8F);
            return true;
        }

        int index = getHovered(mouseX, mouseY);
        if (button != 0 || index < 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        WarpingQuirk.WheelEntry entry = this.entries.get(index);
        if (this.wheel != WarpingQuirk.WHEEL_WARP) {
            choose(entry.id(), entry.id());
        } else if (this.first == null) {
            this.first = entry;
            click(1.2F);
        } else if (this.first.id().equals(entry.id())) {
            this.first = null;
            click(0.8F);
        } else {
            choose(this.first.id(), entry.id());
        }
        return true;
    }

    private void choose(UUID first, UUID second) {
        PacketDistributor.sendToServer(new WarpingQuirk.WheelChoicePayload(this.wheel, first, second));
        click(1.0F);
        this.onClose();
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}

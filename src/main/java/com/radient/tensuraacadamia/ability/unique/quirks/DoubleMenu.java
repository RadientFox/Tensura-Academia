package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DoubleMenu extends AbstractContainerMenu {
    public record Entry(UUID id, String name, boolean self, boolean player,
                        ResourceLocation entityType, CompoundTag profile) {
    }

    private final UUID creatorId;
    private final List<Entry> entries;
    private UUID selectedId;
    private final String researchName;
    private final int completedStacks;
    private final int progressPercent;
    private final DataSlot livingNonSelf = DataSlot.standalone();
    private final DataSlot duplicateLimit = DataSlot.standalone();
    private final Player owner;

    public DoubleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(DoubleMenus.DOUBLE_MENU.get(), containerId);
        owner = inventory.player;
        creatorId = buffer.readUUID();
        int size = Math.min(buffer.readVarInt(), 4096);
        entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUUID(), buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readResourceLocation(), buffer.readNbt()));
        }
        selectedId = buffer.readUUID();
        researchName = buffer.readUtf(128);
        completedStacks = buffer.readVarInt();
        progressPercent = buffer.readVarInt();
        livingNonSelf.set(buffer.readVarInt());
        duplicateLimit.set(buffer.readVarInt());
        addDataSlot(livingNonSelf);
        addDataSlot(duplicateLimit);
    }

    public DoubleMenu(int containerId, Inventory inventory, ServerPlayer creator, List<Entry> entries,
                      UUID selectedId, String researchName, int completedStacks,
                      int progressPercent, int livingNonSelf) {
        super(DoubleMenus.DOUBLE_MENU.get(), containerId);
        owner = creator;
        this.creatorId = creator.getUUID();
        this.entries = List.copyOf(entries);
        this.selectedId = selectedId;
        this.researchName = researchName;
        this.completedStacks = completedStacks;
        this.progressPercent = progressPercent;
        this.livingNonSelf.set(livingNonSelf);
        duplicateLimit.set(DoubleQuirk.duplicateLimit(creator));
        addDataSlot(this.livingNonSelf);
        addDataSlot(duplicateLimit);
    }

    public void writeOpenData(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(creatorId);
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.id());
            buffer.writeUtf(entry.name(), 128);
            buffer.writeBoolean(entry.self());
            buffer.writeBoolean(entry.player());
            buffer.writeResourceLocation(entry.entityType());
            buffer.writeNbt(entry.profile());
        }
        buffer.writeUUID(selectedId);
        buffer.writeUtf(researchName, 128);
        buffer.writeVarInt(completedStacks);
        buffer.writeVarInt(progressPercent);
        buffer.writeVarInt(livingNonSelf.get());
        buffer.writeVarInt(duplicateLimit.get());
    }

    public List<Entry> entries() {
        return entries;
    }

    public UUID selectedId() {
        return selectedId;
    }

    public void setSelectedId(UUID selectedId) {
        this.selectedId = selectedId;
    }

    public String researchName() {
        return researchName;
    }

    public int completedStacks() {
        return completedStacks;
    }

    public int progressPercent() {
        return progressPercent;
    }

    public int livingNonSelf() {
        return livingNonSelf.get();
    }

    public int duplicateLimit() {
        return duplicateLimit.get();
    }

    @Override
    public void broadcastChanges() {
        if (owner instanceof ServerPlayer player) {
            livingNonSelf.set(DoubleCloneManager.countNonSelf(player));
            duplicateLimit.set(DoubleQuirk.duplicateLimit(player));
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getUUID().equals(creatorId)
                && SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.DOUBLE.get().getRegistryName()).isPresent();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)
                || button < 0 || button >= entries.size() * 2) return false;

        if (button >= entries.size()) {
            Entry entry = entries.get(button - entries.size());
            if (entry.self() || !DoubleResearchData.forget(serverPlayer, entry.id())) return false;
            serverPlayer.displayClientMessage(Component.translatable(
                    "tracadamia.skill.double.menu.forgotten", entry.name()), true);
            serverPlayer.closeContainer();
            return true;
        }

        Entry entry = entries.get(button);
        if (!entry.self() && !DoubleResearchData.isUnlocked(serverPlayer, entry.id())) return false;
        DoubleResearchData.setSelected(serverPlayer, entry.id());
        selectedId = entry.id();
        serverPlayer.displayClientMessage(Component.translatable("tracadamia.skill.double.menu.selected", entry.name()), true);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}

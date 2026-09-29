package com.radient.tensuraacadamia.ability.unique.quirks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilityPreset;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class DoubleResearchData {
    private static final String ROOT = "tracadamia_double";
    private static final String UNLOCKED = "Unlocked";
    private static final String RESEARCH = "Research";

    record Award(int stacks, boolean unlocked) {
    }

    private DoubleResearchData() {
    }

    static CompoundTag capture(LivingEntity source) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("Name", source.getName().getString());
        snapshot.putString("EntityType", BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString());

        CompoundTag equipment = new CompoundTag();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = source.getItemBySlot(slot);
            if (!stack.isEmpty()) equipment.put(slot.getName(), stack.copy().save(source.registryAccess()));
        }
        snapshot.put("Equipment", equipment);

        CompoundTag curios = new CompoundTag();
        CuriosApi.getCuriosInventory(source).ifPresent(handler -> handler.getCurios().forEach((slotId, slotHandler) -> {
            ListTag stacks = new ListTag();
            for (int i = 0; i < slotHandler.getStacks().getSlots(); i++) {
                ItemStack stack = slotHandler.getStacks().getStackInSlot(i);
                if (stack.isEmpty()) stacks.add(new CompoundTag());
                else stacks.add(stack.copy().save(source.registryAccess()));
            }
            curios.put(slotId, stacks);
        }));
        snapshot.put("Curios", curios);

        ListTag attributes = new ListTag();
        for (var holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance attribute = source.getAttribute(holder);
            if (attribute == null) continue;
            ResourceLocation id = attribute.getAttribute().unwrapKey().map(key -> key.location()).orElse(null);
            double value = attribute.getValue();
            if (id == null || !Double.isFinite(value)) continue;
            CompoundTag saved = new CompoundTag();
            saved.putString("Id", id.toString());
            saved.putDouble("Value", value);
            attributes.add(saved);
        }
        snapshot.put("Attributes", attributes);

        var existence = TensuraStorages.getExistenceFrom(source);
        snapshot.putDouble("MaxAura", finiteNonNegative(EnergyHelper.getMaxAura(source)));
        snapshot.putDouble("Aura", finiteNonNegative(existence.getAura()));
        snapshot.putDouble("MaxMagicule", finiteNonNegative(EnergyHelper.getMaxMagicule(source)));
        snapshot.putDouble("Magicule", finiteNonNegative(existence.getMagicule()));

        if (source instanceof Player player) {
            ResolvableProfile profile = new ResolvableProfile(player.getGameProfile());
            ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, profile).result()
                    .ifPresent(tag -> snapshot.put("Profile", tag));
        }

        ListTag skills = new ListTag();
        SkillAPI.getSkillsFrom(source).getLearnedSkills().forEach(skill -> skills.add(skill.toNBT()));
        snapshot.put("Skills", skills);

        var ability = TensuraStorages.getAbilityFrom(source);
        snapshot.putInt("ActivePreset", ability.getActivePreset());
        ListTag presets = new ListTag();
        for (AbilityPreset preset : ability.getPresets()) {
            CompoundTag savedPreset = new CompoundTag();
            savedPreset.putString("Name", preset.getName());
            ListTag slots = new ListTag();
            for (AbilitySlot slot : preset.getAbilities()) slots.add(slot.serialize());
            savedPreset.put("Slots", slots);
            presets.add(savedPreset);
        }
        snapshot.put("AbilityPresets", presets);
        return snapshot;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    static void copyOnRespawn(Player original, Player replacement) {
        CompoundTag saved = original.getPersistentData().getCompound(ROOT).copy();
        saved.remove("ActiveStudy");
        replacement.getPersistentData().put(ROOT, saved);
    }

    static void ensurePlayerEntry(ServerPlayer player) {
        CompoundTag root = root(player);
        if (!root.contains("Selected")) root.putString("Selected", player.getUUID().toString());
        player.getPersistentData().put(ROOT, root);
    }

    static List<DoubleMenu.Entry> entries(ServerPlayer player) {
        ensurePlayerEntry(player);
        CompoundTag unlocked = root(player).getCompound(UNLOCKED);
        List<DoubleMenu.Entry> entries = new ArrayList<>();
        CompoundTag playerProfile = ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE,
                new ResolvableProfile(player.getGameProfile())).result()
                .filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast).orElseGet(CompoundTag::new);
        entries.add(new DoubleMenu.Entry(player.getUUID(), player.getName().getString(), true, true,
                BuiltInRegistries.ENTITY_TYPE.getKey(player.getType()), playerProfile));
        for (String key : unlocked.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag entry = unlocked.getCompound(key);
                ResourceLocation type = ResourceLocation.tryParse(entry.getString("EntityType"));
                if (type == null) type = ResourceLocation.withDefaultNamespace("pig");
                entries.add(new DoubleMenu.Entry(id, entry.getString("Name"), false, entry.contains("Profile"),
                        type, entry.getCompound("Profile").copy()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        entries.subList(1, entries.size()).sort(Comparator.comparing(DoubleMenu.Entry::name,
                String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    static boolean isUnlocked(ServerPlayer player, UUID id) {
        return player.getUUID().equals(id) || root(player).getCompound(UNLOCKED).contains(id.toString());
    }

    static CompoundTag snapshot(ServerPlayer player, UUID id) {
        if (player.getUUID().equals(id)) return capture(player);
        CompoundTag saved = root(player).getCompound(UNLOCKED).getCompound(id.toString());
        return saved.isEmpty() ? null : saved.copy();
    }

    static UUID selected(ServerPlayer player) {
        ensurePlayerEntry(player);
        CompoundTag root = root(player);
        try {
            UUID selected = UUID.fromString(root.getString("Selected"));
            return isUnlocked(player, selected) ? selected : player.getUUID();
        } catch (IllegalArgumentException ignored) {
            return player.getUUID();
        }
    }

    static void setSelected(ServerPlayer player, UUID id) {
        if (!isUnlocked(player, id)) return;
        CompoundTag root = root(player);
        root.putString("Selected", id.toString());
        player.getPersistentData().put(ROOT, root);
    }

    static boolean forget(ServerPlayer player, UUID id) {
        if (player.getUUID().equals(id) || !isUnlocked(player, id)) return false;
        CompoundTag root = root(player);
        String key = id.toString();
        root.getCompound(UNLOCKED).remove(key);
        root.getCompound(RESEARCH).remove(key);
        if (key.equals(root.getString("Selected"))) root.putString("Selected", player.getUUID().toString());
        if (key.equals(root.getString("LastResearchId"))) root.remove("LastResearchId");
        if (id.equals(activeStudyId(root))) root.remove("ActiveStudy");
        player.getPersistentData().put(ROOT, root);
        return true;
    }

    static int stacks(ServerPlayer player, UUID targetId) {
        return root(player).getCompound(RESEARCH).getCompound(targetId.toString()).getInt("Stacks");
    }

    static int advanceStudy(ServerPlayer player, UUID targetId, String targetName) {
        CompoundTag root = root(player);
        CompoundTag active = root.getCompound("ActiveStudy");
        if (!active.hasUUID("Target") || !active.getUUID("Target").equals(targetId)) {
            active = new CompoundTag();
            active.putUUID("Target", targetId);
        }
        active.putString("Name", targetName);
        int heldTicks = active.getInt("HeldTicks") + 1;
        active.putInt("HeldTicks", heldTicks);
        root.put("ActiveStudy", active);
        player.getPersistentData().put(ROOT, root);
        return heldTicks;
    }

    static void clearStudy(ServerPlayer player) {
        CompoundTag root = root(player);
        root.remove("ActiveStudy");
        player.getPersistentData().put(ROOT, root);
    }

    static String researchName(ServerPlayer player) {
        CompoundTag root = root(player);
        UUID activeId = activeStudyId(root);
        if (activeId != null) return root.getCompound("ActiveStudy").getString("Name");
        CompoundTag research = root.getCompound(RESEARCH);
        String latest = root.getString("LastResearchId");
        if (latest.isBlank()) latest = research.getAllKeys().stream().sorted().findFirst().orElse(null);
        if (latest == null) return "None";
        if (root.getCompound(UNLOCKED).contains(latest)) return root.getCompound(UNLOCKED).getCompound(latest).getString("Name");
        String name = research.getCompound(latest).getString("Name");
        return name.isBlank() ? "Target " + latest.substring(0, Math.min(8, latest.length())) : name;
    }

    static int researchStacks(ServerPlayer player) {
        CompoundTag root = root(player);
        UUID activeId = activeStudyId(root);
        if (activeId != null) {
            if (root.getCompound(UNLOCKED).contains(activeId.toString())) return 3;
            return root.getCompound(RESEARCH).getCompound(activeId.toString()).getInt("Stacks");
        }
        CompoundTag research = root.getCompound(RESEARCH);
        String latest = root.getString("LastResearchId");
        if (latest.isBlank()) latest = research.getAllKeys().stream().sorted().findFirst().orElse(null);
        if (latest == null) return 0;
        return root.getCompound(UNLOCKED).contains(latest) ? 3 : research.getCompound(latest).getInt("Stacks");
    }

    static int progressPercent(ServerPlayer player) {
        CompoundTag active = root(player).getCompound("ActiveStudy");
        int ticks = Math.max(0, Math.min(DoubleQuirk.RESEARCH_STACK_TICKS, active.getInt("HeldTicks")));
        return ticks * 100 / DoubleQuirk.RESEARCH_STACK_TICKS;
    }

    @Nullable
    private static UUID activeStudyId(CompoundTag root) {
        CompoundTag active = root.getCompound("ActiveStudy");
        return active.hasUUID("Target") ? active.getUUID("Target") : null;
    }

    static Award awardStack(ServerPlayer player, LivingEntity target) {
        CompoundTag root = root(player);
        CompoundTag progress = root.getCompound(RESEARCH);
        CompoundTag unlocked = root.getCompound(UNLOCKED);
        String id = target.getUUID().toString();
        if (unlocked.contains(id)) return new Award(3, true);
        root.putString("LastResearchId", id);
        CompoundTag current = progress.getCompound(id);
        int stacks = Math.min(3, current.getInt("Stacks") + 1);
        boolean complete = stacks >= 3;
        if (complete) {
            unlocked.put(id, capture(target));
            progress.remove(id);
        } else {
            current.putInt("Stacks", stacks);
            current.putString("Name", target.getName().getString());
            progress.put(id, current);
        }
        root.put(RESEARCH, progress);
        root.put(UNLOCKED, unlocked);
        player.getPersistentData().put(ROOT, root);
        return new Award(stacks, complete);
    }

    private static CompoundTag root(ServerPlayer player) {
        return player.getPersistentData().getCompound(ROOT);
    }
}

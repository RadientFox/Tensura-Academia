package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.CopyQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AllForOneStock {
    private static final String STOCK_TAG = "tracadamia_all_for_one_copies";
    private static final String SKILL_ID_TAG = "skill_id";
    private static final String SKILL_DATA_TAG = "skill_data";

    private AllForOneStock() {
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().contains(STOCK_TAG, Tag.TAG_LIST)) {
            event.getEntity().getPersistentData().put(STOCK_TAG,
                    event.getOriginal().getPersistentData().getList(STOCK_TAG, Tag.TAG_COMPOUND).copy());
        }
    }

    public static boolean canHandle(ManasSkill skill) {
        if (skill == QuirkSkills.ALL_FOR_ONE.get()) return false;
        if (skill instanceof Magic) return true;
        if (!(skill instanceof Skill typed)) return false;
        return switch (typed.getType()) {
            case COMMON, EXTRA, INTRINSIC -> true;
            case UNIQUE, ULTIMATE -> "tracadamia".equals(skill.getRegistryName().getNamespace());
            default -> false;
        };
    }

    private static boolean canHandle(ManasSkillInstance instance) {
        return instance != null && canHandle(instance.getSkill())
                && !instance.isTemporarySkill() && !CopyQuirk.isCopiedSkill(instance);
    }

    public static Map<ResourceLocation, Integer> counts(LivingEntity owner) {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        for (ManasSkillInstance instance : SkillAPI.getSkillsFrom(owner).getLearnedSkills()) {
            if (canHandle(instance)) counts.merge(instance.getSkillId(), 1, Integer::sum);
        }
        for (Tag entry : copies(owner)) {
            ResourceLocation id = ResourceLocation.tryParse(((CompoundTag) entry).getString(SKILL_ID_TAG));
            if (id != null && SkillAPI.getSkillRegistry().contains(id)
                    && canHandle(ManasSkillInstance.fromNBT(((CompoundTag) entry).getCompound(SKILL_DATA_TAG)))) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts;
    }

    public static List<ResourceLocation> orderedIds(LivingEntity owner) {
        List<ResourceLocation> ids = new ArrayList<>(counts(owner).keySet());
        ids.sort(Comparator.comparing(id -> displayName(id).getString()));
        return ids;
    }

    public static Component displayName(ResourceLocation id) {
        ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
        return skill == null ? Component.literal(id.toString()) : skill.getName();
    }

    public static void printStock(ServerPlayer player) {
        Map<ResourceLocation, Integer> counts = counts(player);
        player.sendSystemMessage(Component.literal("All For One stock:"));
        if (counts.isEmpty()) {
            player.sendSystemMessage(Component.literal("  Empty"));
            return;
        }
        for (ResourceLocation id : orderedIds(player)) {
            player.sendSystemMessage(Component.literal("  ").append(displayName(id))
                    .append(Component.literal(" x" + counts.get(id))));
        }
    }

    public static boolean moveOne(LivingEntity from, LivingEntity to, ResourceLocation id) {
        if (from == to || !SkillAPI.getSkillRegistry().contains(id)) return false;
        ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
        if (!canHandle(skill)) return false;

        SkillStorage source = SkillAPI.getSkillsFrom(from);
        SkillStorage destination = SkillAPI.getSkillsFrom(to);
        ManasSkillInstance instance = source.getSkill(id).orElse(null);
        CompoundTag copy = findCopy(from, id);
        if (copy == null && !canHandle(instance)) return false;

        ManasSkillInstance moving;
        if (copy != null) {
            moving = ManasSkillInstance.fromNBT(copy.getCompound(SKILL_DATA_TAG));
        } else {
            moving = instance.copy();
        }
        if (!canHandle(moving)) return false;

        if (destination.getSkill(id).isPresent()) {
            addCopy(to, moving);
        } else {
            moving.getOrCreateTag().putBoolean("NoMagiculeCost", true);
            if (!SkillHelper.learnSkill(to, moving)) {
                addCopy(to, moving);
            }
        }

        if (copy != null) {
            removeCopy(from, id);
        } else {
            source.forgetSkill(id);
        }
        return true;
    }

    public static boolean discardOne(LivingEntity owner, ResourceLocation id) {
        if (!SkillAPI.getSkillRegistry().contains(id) || !canHandle(SkillAPI.getSkillRegistry().get(id))) return false;
        if (findCopy(owner, id) != null) {
            removeCopy(owner, id);
            return true;
        }
        SkillStorage storage = SkillAPI.getSkillsFrom(owner);
        if (!canHandle(storage.getSkill(id).orElse(null))) return false;
        storage.forgetSkill(id);
        return true;
    }

    /** Removes every ability held by All For One and returns the consumed instances. */
    public static List<ManasSkillInstance> sacrificeAll(LivingEntity owner) {
        List<ManasSkillInstance> sacrificed = new ArrayList<>();
        SkillStorage storage = SkillAPI.getSkillsFrom(owner);
        for (ManasSkillInstance instance : new ArrayList<>(storage.getLearnedSkills())) {
            if (!canHandle(instance)) continue;
            sacrificed.add(instance.copy());
            storage.forgetSkill(instance.getSkillId());
        }

        ListTag remainingCopies = new ListTag();
        for (Tag entry : copies(owner)) {
            CompoundTag copy = (CompoundTag) entry;
            ManasSkillInstance instance = ManasSkillInstance.fromNBT(copy.getCompound(SKILL_DATA_TAG));
            if (canHandle(instance)) sacrificed.add(instance);
            else remainingCopies.add(copy.copy());
        }
        owner.getPersistentData().put(STOCK_TAG, remainingCopies);
        storage.markDirty();
        return sacrificed;
    }

    /** Consumes one copy of each specifically selected stock entry. */
    public static List<ManasSkillInstance> sacrifice(LivingEntity owner, Iterable<ResourceLocation> selectedIds) {
        List<ManasSkillInstance> sacrificed = new ArrayList<>();
        SkillStorage storage = SkillAPI.getSkillsFrom(owner);
        for (ResourceLocation id : selectedIds) {
            if (!SkillAPI.getSkillRegistry().contains(id) || !canHandle(SkillAPI.getSkillRegistry().get(id))) continue;
            CompoundTag copy = findCopy(owner, id);
            if (copy != null) {
                sacrificed.add(ManasSkillInstance.fromNBT(copy.getCompound(SKILL_DATA_TAG)));
                removeCopy(owner, id);
                continue;
            }
            ManasSkillInstance instance = storage.getSkill(id).orElse(null);
            if (!canHandle(instance)) continue;
            sacrificed.add(instance.copy());
            storage.forgetSkill(id);
        }
        if (!sacrificed.isEmpty()) storage.markDirty();
        return sacrificed;
    }

    private static ListTag copies(LivingEntity owner) {
        return owner.getPersistentData().getList(STOCK_TAG, Tag.TAG_COMPOUND);
    }

    private static void addCopy(LivingEntity owner, ManasSkillInstance instance) {
        ListTag list = copies(owner);
        CompoundTag entry = new CompoundTag();
        entry.putString(SKILL_ID_TAG, instance.getSkillId().toString());
        entry.put(SKILL_DATA_TAG, instance.toNBT());
        list.add(entry);
        owner.getPersistentData().put(STOCK_TAG, list);
    }

    private static CompoundTag findCopy(LivingEntity owner, ResourceLocation id) {
        for (Tag entry : copies(owner)) {
            CompoundTag copy = (CompoundTag) entry;
            if (id.toString().equals(copy.getString(SKILL_ID_TAG))
                    && canHandle(ManasSkillInstance.fromNBT(copy.getCompound(SKILL_DATA_TAG)))) return copy;
        }
        return null;
    }

    private static void removeCopy(LivingEntity owner, ResourceLocation id) {
        ListTag list = copies(owner);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag copy = list.getCompound(i);
            if (id.toString().equals(copy.getString(SKILL_ID_TAG))
                    && canHandle(ManasSkillInstance.fromNBT(copy.getCompound(SKILL_DATA_TAG)))) {
                list.remove(i);
                owner.getPersistentData().put(STOCK_TAG, list);
                return;
            }
        }
    }
}

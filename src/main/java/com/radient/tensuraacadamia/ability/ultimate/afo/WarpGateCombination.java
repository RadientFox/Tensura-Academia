package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.CopyQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class WarpGateCombination {
    private static final String DATA = "TracadamiaAfoWarpGate";
    private static final ResourceLocation COST_ID = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "afo_warp_gate");

    private WarpGateCombination() {}

    public static boolean combined(LivingEntity owner) { return owner.getPersistentData().contains(DATA, Tag.TAG_COMPOUND); }

    public static boolean eligible(LivingEntity owner) {
        var skills = SkillAPI.getSkillsFrom(owner);
        return valid(skills.getSkill(QuirkSkills.CLOUD.get()).orElse(null))
                && valid(skills.getSkill(QuirkSkills.TELEPORTATION.get()).orElse(null));
    }

    private static boolean valid(ManasSkillInstance instance) {
        return instance != null && !instance.isTemporarySkill() && !instance.isSubInstance()
                && !CopyQuirk.isCopiedSkill(instance);
    }

    public static boolean select(LivingEntity owner) {
        if (owner.level().isClientSide || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ALL_FOR_ONE.get()).isEmpty()) return false;
        if (combined(owner)) return uncombine(owner);
        if (AllForOneCombine.combined(owner) || !eligible(owner)
                || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).isPresent()) return false;
        var max = owner.getAttribute(TensuraAttributes.MAX_MAGICULE);
        if (max == null || !Double.isFinite(max.getValue()) || max.getValue() < AllForOneCombine.MAGICULE_RESERVATION) {
            message(owner, "Combining requires 50,000 maximum magicules.");
            return false;
        }
        var storage = SkillAPI.getSkillsFrom(owner);
        var cloud = storage.getSkill(QuirkSkills.CLOUD.get()).orElseThrow();
        var teleport = storage.getSkill(QuirkSkills.TELEPORTATION.get()).orElseThrow();
        CompoundTag saved = new CompoundTag();
        saved.put("Cloud", cloud.toNBT().copy());
        saved.put("Teleportation", teleport.toNBT().copy());
        ListTag slots = new ListTag();
        var ability = TensuraStorages.getAbilityFrom(owner);
        for (int preset = 0; preset < ability.getPresets().size(); preset++) {
            List<AbilitySlot> presetSlots = ability.getAbilitySlots(preset);
            for (int index = 0; index < presetSlots.size(); index++) {
                var slot = presetSlots.get(index);
                if (slot.getSkill() != cloud.getSkill() && slot.getSkill() != teleport.getSkill()) continue;
                CompoundTag location = new CompoundTag();
                location.putInt("Preset", preset);
                location.putInt("Index", index);
                location.put("Slot", slot.serialize());
                slots.add(location);
            }
        }
        saved.put("Slots", slots);
        var energy = TensuraStorages.getExistenceFrom(owner);
        double before = energy.getMagicule();
        owner.getPersistentData().put(DATA, saved);
        reconcileCost(owner);
        double after = Math.min(before, max.getValue());
        saved.putDouble("ReservedMagicules", Math.max(0, before - after));
        energy.setMagicule(after);
        energy.markDirty();
        storage.forgetSkill(cloud.getSkillId());
        storage.forgetSkill(teleport.getSkillId());
        var result = QuirkSkills.WARP_GATE.get().createDefaultInstance();
        result.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        storage.updateSkill(result, false);
        boolean placed = false;
        for (Tag tag : slots) {
            var location = (CompoundTag) tag;
            int preset = location.getInt("Preset"), index = location.getInt("Index");
            if (!placed) {
                ability.setAbilitySlot(preset, index, result.getSkill(), 0);
                placed = true;
            } else ability.setAbilitySlot(preset, index, AbilitySlot.getEmpty());
        }
        storage.markDirty();
        ability.markDirty();
        message(owner, "Combined Cloud and Teleportation into Warp Gate. 50,000 maximum magicules reserved.");
        return true;
    }

    private static boolean uncombine(LivingEntity owner) {
        CompoundTag saved = owner.getPersistentData().getCompound(DATA);
        var cloud = ManasSkillInstance.fromNBT(saved.getCompound("Cloud").copy());
        var teleport = ManasSkillInstance.fromNBT(saved.getCompound("Teleportation").copy());
        var storage = SkillAPI.getSkillsFrom(owner);
        var ability = TensuraStorages.getAbilityFrom(owner);
        List<CompoundTag> vacant = new ArrayList<>();
        for (Tag tag : saved.getList("Slots", Tag.TAG_COMPOUND)) {
            var location = (CompoundTag) tag;
            int p = location.getInt("Preset"), i = location.getInt("Index");
            if (p < ability.getPresets().size() && i < ability.getAbilitySlots(p).size()) {
                var current = ability.getAbilitySlot(p, i);
                if (current.isEmpty() || current.getSkill() == QuirkSkills.WARP_GATE.get()) vacant.add(location);
            }
        }
        storage.forgetSkill(QuirkSkills.WARP_GATE.get().getRegistryName());
        for (var original : List.of(cloud, teleport)) {
            storage.getSkill(original.getSkillId()).ifPresent(current -> AllForOneStock.addCopy(owner, current.copy()));
            storage.forgetSkill(original.getSkillId());
            storage.updateSkill(original, false);
            if (original.isToggled()) original.onToggleOn(owner);
        }
        for (var location : vacant) ability.setAbilitySlot(location.getInt("Preset"), location.getInt("Index"),
                AbilitySlot.fromNBT(location.getCompound("Slot")));
        owner.getPersistentData().remove(DATA);
        reconcileCost(owner);
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setMagicule(Math.min(owner.getAttributeValue(TensuraAttributes.MAX_MAGICULE),
                energy.getMagicule() + saved.getDouble("ReservedMagicules")));
        energy.markDirty();
        storage.markDirty();
        ability.markDirty();
        message(owner, "Uncombined Warp Gate. Cloud, Teleportation, and reserved magicules restored.");
        return true;
    }

    public static void reconcileCost(LivingEntity owner) {
        var max = owner.getAttribute(TensuraAttributes.MAX_MAGICULE);
        if (max == null) return;
        if (combined(owner) && !max.hasModifier(COST_ID)) max.addPermanentModifier(new AttributeModifier(COST_ID,
                -AllForOneCombine.MAGICULE_RESERVATION, AttributeModifier.Operation.ADD_VALUE));
        else if (!combined(owner)) max.removeModifier(COST_ID);
        if (combined(owner) && SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.WARP_GATE.get()).isEmpty()) {
            var result = QuirkSkills.WARP_GATE.get().createDefaultInstance();
            result.getOrCreateTag().putBoolean("NoMagiculeCost", true);
            SkillAPI.getSkillsFrom(owner).updateSkill(result, false);
            SkillAPI.getSkillsFrom(owner).markDirty();
        }
    }

    @SubscribeEvent public static void onClone(PlayerEvent.Clone event) {
        if (combined(event.getOriginal())) event.getEntity().getPersistentData().put(DATA,
                event.getOriginal().getPersistentData().getCompound(DATA).copy());
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { reconcileCost(event.getEntity()); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { reconcileCost(event.getEntity()); }

    private static void message(LivingEntity owner, String text) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(text), true);
    }
}

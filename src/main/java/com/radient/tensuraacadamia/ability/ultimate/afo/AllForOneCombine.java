package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.CopyQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.PermeationQuirk;
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
public final class AllForOneCombine {
    public static final double MAGICULE_RESERVATION = 50_000;
    private static final String DATA = "TracadamiaAfoCombination";
    private static final ResourceLocation COST_ID = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "afo_combination");

    private AllForOneCombine() {}

    public static boolean combined(LivingEntity owner) { return owner.getPersistentData().contains(DATA, Tag.TAG_COMPOUND); }

    public static List<ResourceLocation> entries(LivingEntity owner) {
        if (combined(owner)) return List.of(QuirkSkills.PERIL_DIFFUSION.get().getRegistryName());
        var storage = SkillAPI.getSkillsFrom(owner);
        if (!eligible(storage.getSkill(QuirkSkills.PERMEATION.get()).orElse(null))) return List.of();
        List<ResourceLocation> choices = new ArrayList<>();
        for (var partner : List.of(QuirkSkills.DANGERSENSE.get())) {
            if (eligible(storage.getSkill(partner).orElse(null))) choices.add(partner.getRegistryName());
        }
        return choices;
    }

    private static boolean eligible(ManasSkillInstance instance) {
        return instance != null && !instance.isTemporarySkill() && !CopyQuirk.isCopiedSkill(instance) && !instance.isSubInstance();
    }

    public static boolean select(LivingEntity owner, ResourceLocation partnerId) {
        if (owner.level().isClientSide || PermeationQuirk.isPhasing(owner)
                || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ALL_FOR_ONE.get()).isEmpty()
                || !entries(owner).contains(partnerId)) return false;
        if (combined(owner)) return uncombine(owner);
        var max = owner.getAttribute(TensuraAttributes.MAX_MAGICULE);
        if (max == null || !Double.isFinite(max.getValue()) || max.getValue() < MAGICULE_RESERVATION) {
            message(owner, "Combining requires 50,000 maximum magicules.");
            return false;
        }
        var storage = SkillAPI.getSkillsFrom(owner);
        if (storage.getSkill(QuirkSkills.PERIL_DIFFUSION.get()).isPresent()) return false;
        var phase = storage.getSkill(QuirkSkills.PERMEATION.get()).orElseThrow();
        var danger = storage.getSkill(partnerId).orElseThrow();
        CompoundTag saved = new CompoundTag();
        saved.put("Permeation", phase.toNBT().copy());
        saved.put("DangerSense", danger.toNBT().copy());
        ListTag slots = new ListTag();
        var ability = TensuraStorages.getAbilityFrom(owner);
        for (int preset = 0; preset < ability.getPresets().size(); preset++) {
            List<AbilitySlot> presetSlots = ability.getAbilitySlots(preset);
            for (int index = 0; index < presetSlots.size(); index++) {
                var slot = presetSlots.get(index);
                if (slot.getSkill() != phase.getSkill() && slot.getSkill() != danger.getSkill()) continue;
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
        storage.forgetSkill(phase.getSkillId());
        storage.forgetSkill(danger.getSkillId());
        var result = QuirkSkills.PERIL_DIFFUSION.get().createDefaultInstance();
        result.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        storage.updateSkill(result, false);
        for (Tag tag : slots) {
            var location = (CompoundTag) tag;
            AbilitySlot prior = AbilitySlot.fromNBT(location.getCompound("Slot"));
            if (prior.getSkill() == phase.getSkill()) ability.setAbilitySlot(location.getInt("Preset"), location.getInt("Index"), result.getSkill(), 0);
        }
        storage.markDirty();
        ability.markDirty();
        message(owner, "Combined Permeation and Danger Sense into Peril Diffusion. 50,000 maximum magicules reserved.");
        return true;
    }

    private static boolean uncombine(LivingEntity owner) {
        CompoundTag saved = owner.getPersistentData().getCompound(DATA);
        var phase = ManasSkillInstance.fromNBT(saved.getCompound("Permeation").copy());
        var danger = ManasSkillInstance.fromNBT(saved.getCompound("DangerSense").copy());
        var storage = SkillAPI.getSkillsFrom(owner);
        ListTag slots = saved.getList("Slots", Tag.TAG_COMPOUND).copy();
        double reserved = saved.getDouble("ReservedMagicules");
        var ability = TensuraStorages.getAbilityFrom(owner);
        List<CompoundTag> vacant = new ArrayList<>();
        for (Tag tag : slots) {
            var location = (CompoundTag) tag;
            int p = location.getInt("Preset"), i = location.getInt("Index");
            if (p < ability.getPresets().size() && i < ability.getAbilitySlots(p).size()) {
                var current = ability.getAbilitySlot(p, i);
                if (current.isEmpty() || current.getSkill() == QuirkSkills.PERIL_DIFFUSION.get()) vacant.add(location);
            }
        }
        storage.forgetSkill(QuirkSkills.PERIL_DIFFUSION.get().getRegistryName());
        // Keep any independently reacquired originals as AFO stock instead of losing them.
        for (var original : List.of(phase, danger)) {
            storage.getSkill(original.getSkillId()).ifPresent(current -> AllForOneStock.addCopy(owner, current.copy()));
            storage.forgetSkill(original.getSkillId());
        }
        storage.updateSkill(phase, false);
        storage.updateSkill(danger, false);
        for (var location : vacant) ability.setAbilitySlot(location.getInt("Preset"), location.getInt("Index"), AbilitySlot.fromNBT(location.getCompound("Slot")));
        owner.getPersistentData().remove(DATA);
        reconcileCost(owner);
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setMagicule(Math.min(owner.getAttributeValue(TensuraAttributes.MAX_MAGICULE), energy.getMagicule() + reserved));
        energy.markDirty();
        if (phase.isToggled()) phase.onToggleOn(owner);
        if (danger.isToggled()) danger.onToggleOn(owner);
        storage.markDirty();
        ability.markDirty();
        message(owner, "Uncombined. Original quirks, mastery, and reserved magicules restored.");
        return true;
    }

    public static void reconcileCost(LivingEntity owner) {
        var max = owner.getAttribute(TensuraAttributes.MAX_MAGICULE);
        if (max == null) return;
        if (combined(owner) && !max.hasModifier(COST_ID)) max.addPermanentModifier(new AttributeModifier(COST_ID, -MAGICULE_RESERVATION, AttributeModifier.Operation.ADD_VALUE));
        else if (!combined(owner)) max.removeModifier(COST_ID);
        if (combined(owner) && SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.PERIL_DIFFUSION.get()).isEmpty()) {
            var result = QuirkSkills.PERIL_DIFFUSION.get().createDefaultInstance();
            result.getOrCreateTag().putBoolean("NoMagiculeCost", true);
            SkillAPI.getSkillsFrom(owner).updateSkill(result, false);
            SkillAPI.getSkillsFrom(owner).markDirty();
        }
    }

    @SubscribeEvent public static void onClone(PlayerEvent.Clone event) {
        if (combined(event.getOriginal())) event.getEntity().getPersistentData().put(DATA, event.getOriginal().getPersistentData().getCompound(DATA).copy());
    }
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) { reconcileCost(event.getEntity()); }
    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) { reconcileCost(event.getEntity()); }

    private static void message(LivingEntity owner, String text) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(text), true);
    }
}

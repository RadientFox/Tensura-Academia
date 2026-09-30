package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraItemTags;
import io.github.manasmods.tensura.item.misc.SmithingSchematicItem;
import io.github.manasmods.tensura.registry.advancement.TensuraCriteriaTriggers;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class IQQuirk extends Skill {
    public static final double ACQUISITION_COST = 35_000;
    private static final ResourceLocation EFFICIENCY = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "iq_efficiency");

    public IQQuirk() { super(SkillType.UNIQUE); }

    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/unique/iqicon.png");
    }
    @Override public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.iq.description");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return ACQUISITION_COST; }
    @Override public int getModes(ManasSkillInstance instance) { return 0; }
    @Override public boolean canInteractSkill(ManasSkillInstance instance, LivingEntity owner) { return false; }
    @Override public boolean canTick(ManasSkillInstance instance, LivingEntity owner) { return true; }

    @Override public void onLearnSkill(ManasSkillInstance instance, LivingEntity owner) {
        super.onLearnSkill(instance, owner);
        unlockSchematics(owner);
    }
    @Override public void onTick(ManasSkillInstance instance, LivingEntity owner) { unlockSchematics(owner); }
    @Override public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        unlockSchematics(owner);
    }
    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        super.onForgetSkill(instance, owner);
        efficiency(owner, false);
    }

    private static void unlockSchematics(LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player)) return;
        var data = TensuraStorages.getPlayerDataFrom(player);
        boolean changed = false;
        for (var item : BuiltInRegistries.ITEM) {
            if ((item instanceof SmithingSchematicItem || item.getDefaultInstance().is(TensuraItemTags.SCHEMATICS))
                    && !data.hasSchematic(item)) {
                data.unlockSchematic(item);
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
            TensuraCriteriaTriggers.LEARN_ALL_SCHEMATICS.get().trigger(player);
        }
    }

    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || owner.level().isClientSide()) return;
        var skill = QuirkSkills.IQ.get();
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(skill).orElse(null);
        if (instance == null && !skill.hasAttributeApplied(owner, ManasCoreAttributes.CRITICAL_ATTACK_CHANCE, EFFICIENCY)) return;
        efficiency(owner, instance != null && owner.isAlive() && skill.isInSlot(owner, instance));
    }

    private static void efficiency(LivingEntity owner, boolean active) {
        modifier(owner, TensuraAttributes.AURA_GAIN, 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, active);
        modifier(owner, TensuraAttributes.MAGICULE_GAIN, 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, active);
        modifier(owner, TensuraAttributes.DODGE_NEGATE_CHANCE, 75, AttributeModifier.Operation.ADD_VALUE, active);
        modifier(owner, ManasCoreAttributes.CRITICAL_ATTACK_CHANCE, 75, AttributeModifier.Operation.ADD_VALUE, active);
    }

    private static void modifier(LivingEntity owner, Holder<Attribute> type, double amount,
                                 AttributeModifier.Operation operation, boolean active) {
        var attribute = owner.getAttribute(type);
        if (attribute == null) return;
        if (!active) attribute.removeModifier(EFFICIENCY);
        else if (!attribute.hasModifier(EFFICIENCY))
            attribute.addTransientModifier(new AttributeModifier(EFFICIENCY, amount, operation));
    }
}

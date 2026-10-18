package com.radient.tensuraacadamia.quirks.super_regeneration;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.effect.template.TensuraMobEffectInstance;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class SuperRegenerationQuirk extends Skill {
    private static final String PASSIVES = "SuperRegenerationPassives";

    public SuperRegenerationQuirk() { super(SkillType.UNIQUE); }

    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 65000; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public MutableComponent getSkillDescription() { return Component.translatable("tracadamia.skill.super_regeneration.description"); }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/superregenerationicon.png"); }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public int getModes(ManasSkillInstance instance) { return 2; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 2);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 1 ? "super_regeneration.to_never_die" : "super_regeneration.super_recovery";
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (owner.level().isClientSide || !owner.isAlive() || instance.getMastery() < 0 || mode < 0 || mode > 1) return;
        int passives = instance.isToggled() ? instance.getOrCreateTag().getInt(PASSIVES) : 0;
        passives ^= 1 << mode;
        instance.getOrCreateTag().putInt(PASSIVES, passives);
        instance.setToggled(passives != 0);
        instance.markDirty();
        refresh(instance, owner);
        if (owner instanceof Player player) player.displayClientMessage(Component.translatable(
                enabled(instance, mode) ? "tracadamia.super_regeneration.enabled" : "tracadamia.super_regeneration.disabled",
                Component.translatable("tracadamia.skill.mode." + getModeId(instance, mode))), true);
    }

    @Override public void onToggleOn(ManasSkillInstance instance, LivingEntity owner) {
        if (owner.level().isClientSide) return;
        if (instance.getOrCreateTag().getInt(PASSIVES) == 0) instance.getOrCreateTag().putInt(PASSIVES, 3);
        instance.markDirty();
        refresh(instance, owner);
    }

    @Override public void onToggleOff(ManasSkillInstance instance, LivingEntity owner) {
        instance.getOrCreateTag().putInt(PASSIVES, 0);
        instance.markDirty();
        clearRecovery(owner);
    }

    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) { clearRecovery(owner); }
    @Override public boolean canTick(ManasSkillInstance instance, LivingEntity owner) { return instance.isToggled(); }
    @Override public void onTick(ManasSkillInstance instance, LivingEntity owner) {
        if (owner.level().isClientSide || !owner.isAlive()) return;
        refresh(instance, owner);
        if (enabled(instance, 0) && owner.getHealth() < owner.getMaxHealth()) instance.addMasteryPoint(owner);
    }

    public static boolean enabled(ManasSkillInstance instance, int passive) {
        return instance != null && instance.getMastery() >= 0 && instance.isToggled()
                && (instance.getOrCreateTag().getInt(PASSIVES) & 1 << passive) != 0;
    }

    public static ManasSkillInstance instance(LivingEntity owner) {
        return SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.SUPER_REGENERATION.get()).orElse(null);
    }

    public static boolean ownsRecoveryEffect(LivingEntity owner) {
        var effect = owner.getEffect(TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION));
        if (effect == null) return false;
        var source = ((TensuraMobEffectInstance) (Object) effect).tensura$getSourceAbility();
        return source != null && source.getSkill() == QuirkSkills.SUPER_REGENERATION.get();
    }

    public static void disableRecovery(ManasSkillInstance instance, LivingEntity owner) {
        int passives = instance.getOrCreateTag().getInt(PASSIVES) & ~1;
        instance.getOrCreateTag().putInt(PASSIVES, passives);
        instance.setToggled(passives != 0);
        instance.markDirty();
        // let the effect remove itself after exhaustion to avoid changing the list mid tick
    }

    private static void refresh(ManasSkillInstance instance, LivingEntity owner) {
        if (!enabled(instance, 0)) { clearRecovery(owner); return; }
        var effect = TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION);
        if (owner.hasEffect(effect) && !ownsRecoveryEffect(owner)) return;
        // passive refresh is every 100 ticks, keep the effect alive between refreshes
        TensuraMobEffect.addEffect(owner, new MobEffectInstance(effect, 240, 1, false, false, true), owner, instance.getSkill());
    }

    private static void clearRecovery(LivingEntity owner) {
        if (!owner.level().isClientSide && ownsRecoveryEffect(owner))
            owner.removeEffect(TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION));
    }
}

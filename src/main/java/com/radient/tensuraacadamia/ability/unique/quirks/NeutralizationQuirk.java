package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.effects.NeutralizeEffect;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class NeutralizationQuirk extends Skill {

    private static final QuirkSkillsConfig.Neutralization CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Neutralization;

    private static final String RESTORE_TAG = "tracadamia_neutralized_quirks";

    public static final DeferredHolder<MobEffect, MobEffect> NEUTRALIZE = DeferredHolder.create(Registries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "neutralize"));

    public NeutralizationQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @SubscribeEvent
    public static void registerEffect(RegisterEvent event) {
        event.register(Registries.MOB_EFFECT, NEUTRALIZE.getId(), NeutralizeEffect::new);
    }


    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (canNeutralize(instance, owner) && target != owner && isPhysicalHit(owner, source)) {
            neutralize(instance, owner, target);
        }

        return true;
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (canNeutralize(instance, owner) && source.getEntity() instanceof LivingEntity attacker && attacker != owner && isPhysicalHit(attacker, source)) {
            neutralize(instance, owner, attacker);
        }

        return true;
    }

    private boolean canNeutralize(ManasSkillInstance instance, LivingEntity owner) {
        return instance.getMastery() >= 0.0D && (!(owner instanceof Player) || isInSlot(owner, instance));
    }

    private static boolean isPhysicalHit(LivingEntity attacker, DamageSource source) {
        return source.getDirectEntity() == attacker && attacker.getMainHandItem().isEmpty() && !source.is(DamageTypeTags.IS_PROJECTILE) && TensuraDamageHelper.isPhysicalAttack(source);
    }

    private void neutralize(ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        int seconds = instance.isMastered(owner) ? CONFIG.neutralizeSecondsMastered : CONFIG.neutralizeSeconds;
        boolean fresh = !isNeutralized(target);
        addEffect(target, instance, 0, owner, NEUTRALIZE, seconds * 20, 0);
        if (!isNeutralized(target)) {
            return;
        }

        shutOffQuirks(target);

        if (fresh) {
            level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.5D), target.getZ(), 20, 0.3D, 0.5D, 0.3D, 0.02D);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
            instance.addMasteryPoint(owner);
        }

        if (target instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.neutralization.neutralized", seconds).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void shutOffQuirks(LivingEntity target) {
        SkillStorage skills = SkillAPI.getSkillsFrom(target);
        ListTag restore = target.getPersistentData().getList(RESTORE_TAG, Tag.TAG_STRING);
        for (ManasSkillInstance quirk : List.copyOf(skills.getLearnedSkills())) {
            if (isQuirk(quirk.getSkill()) && quirk.isToggled()) {
                quirk.setToggled(false);
                quirk.onToggleOff(target);
                skills.checkAndMarkDirty(quirk);

                StringTag id = StringTag.valueOf(quirk.getSkillId().toString());
                if (!restore.contains(id)) {
                    restore.add(id);
                }
            }
        }

        if (!restore.isEmpty()) {
            target.getPersistentData().put(RESTORE_TAG, restore);
        }

        for (TickingSkill held : List.copyOf(skills.getHeldSkills())) {
            if (isQuirk(held.getSkill())) {
                skills.getSkill(held.getSkill()).ifPresent(quirk -> skills.handleSkillRelease(quirk, held.getKeyNumber(), held.getMode(), false));
            }
        }
    }

    private static boolean isQuirk(ManasSkill skill) {
        ResourceLocation id = skill.getRegistryName();
        return id != null && id.getNamespace().equals(TensuraAcadamia.MODID);
    }

    public static boolean isNeutralized(LivingEntity entity) {
        return entity.hasEffect(NEUTRALIZE);
    }

    private static void restoreQuirks(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (entity.level().isClientSide || !data.contains(RESTORE_TAG)) {
            return;
        }

        Set<String> ids = data.getList(RESTORE_TAG, Tag.TAG_STRING).stream().map(Tag::getAsString).collect(Collectors.toSet());
        data.remove(RESTORE_TAG);

        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        for (ManasSkillInstance quirk : List.copyOf(skills.getLearnedSkills())) {
            if (!quirk.isToggled() && ids.contains(quirk.getSkillId().toString()) && quirk.canBeToggled(entity)) {
                quirk.setToggled(true);
                quirk.onToggleOn(entity);
                skills.checkAndMarkDirty(quirk);
            }
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().is(NEUTRALIZE)) {
            restoreQuirks(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().is(NEUTRALIZE.getKey())) {
            restoreQuirks(event.getEntity());
        }
    }

    private static EventResult blockQuirk(ManasSkillInstance instance, LivingEntity entity, boolean allowToggleOff) {
        if (instance == null || entity.level().isClientSide || !isQuirk(instance.getSkill()) || !isNeutralized(entity)) {
            return EventResult.pass();
        }

        if (allowToggleOff && instance.isToggled()) {
            return EventResult.pass();
        }

        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.neutralization.blocked").withStyle(ChatFormatting.RED), true);
        }

        return EventResult.interruptFalse();
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SkillEvents.ACTIVATE_SKILL.register((instance, entity, keyNumber, mode) -> blockQuirk(instance.get(), entity, false));
            SkillEvents.TOGGLE_SKILL.register((instance, entity) -> blockQuirk(instance.get(), entity, true));
        });
    }

}

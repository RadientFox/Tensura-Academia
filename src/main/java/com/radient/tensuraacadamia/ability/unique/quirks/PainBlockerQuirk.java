package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class PainBlockerQuirk extends Skill {

    private static final QuirkSkillsConfig.PainBlocker CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).PainBlocker;

    private static final int DAMAGE_MITIGATION = 0;
    private static final int LEND_SECONDS = 10;
    private static final int SPARKLE_TICKS = 10;
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("0.#");

    private static final class Mitigation {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final long until;
        private float stored;

        private Mitigation(ManasSkillInstance instance, LivingEntity owner, long until) {
            this.instance = instance;
            this.owner = owner;
            this.until = until;
        }
    }

    private static final List<Mitigation> MITIGATIONS = new ArrayList<>();
    private static boolean releasing;

    public PainBlockerQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == DAMAGE_MITIGATION ? CONFIG.mitigationAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == DAMAGE_MITIGATION ? "pain_blocker.damage_mitigation" : super.getModeId(instance, mode);
    }

    public static Optional<ManasSkillInstance> getPainBlocker(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.PAIN_BLOCKER.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // temp skills

    private static void lendSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        ManasSkillInstance owned = skills.getSkill(skill).orElse(null);
        if (owned == null) {
            TensuraSkillInstance lent = skill.createLearningInstance(entity);
            lent.setMastery(0.0D);
            lent.setRemoveTime(LEND_SECONDS);
            if (SkillHelper.learnSkill(entity, lent, LEND_SECONDS)) {
                skills.getSkill(skill).filter(learned -> !learned.isToggled()).ifPresent(learned -> {
                    learned.setToggled(true);
                    learned.onToggleOn(entity);
                    skills.markDirty();
                });
            }
        } else if (owned.isTemporarySkill() && owned.getRemoveTime() < LEND_SECONDS) {
            owned.setRemoveTime(LEND_SECONDS);
            skills.markDirty();
        }
    }

    private static void takeSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        if (skills.getSkill(skill).filter(ManasSkillInstance::isTemporarySkill).isPresent()) {
            skills.forgetSkill(skill);
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            lendSkill(entity, ResistanceSkills.PAIN_NULLIFICATION.get());
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        lendSkill(entity, ResistanceSkills.PAIN_NULLIFICATION.get());
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, ResistanceSkills.PAIN_NULLIFICATION.get());
        List.copyOf(MITIGATIONS).stream().filter(mitigation -> mitigation.owner == entity).forEach(PainBlockerQuirk::release);
    }

    // Damage mitigation

    private static Optional<Mitigation> getMitigation(LivingEntity entity) {
        return MITIGATIONS.stream().filter(mitigation -> mitigation.owner == entity).findFirst();
    }

    public static boolean isMitigating(LivingEntity entity) {
        return getMitigation(entity).isPresent();
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != DAMAGE_MITIGATION || !(entity.level() instanceof ServerLevel level) || isMitigating(entity) || EnergyHelper.isOutOfEnergy(entity, instance, DAMAGE_MITIGATION)) {
            return;
        }

        int seconds = instance.isMastered(entity) ? CONFIG.mitigationSecondsMastered : CONFIG.mitigationSeconds;
        MITIGATIONS.add(new Mitigation(instance, entity, level.getGameTime() + seconds * 20L));
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 20, entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.5D, 0.1D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.8F);
        message(entity, Component.translatable("tracadamia.skill.pain_blocker.mitigating", seconds).withStyle(ChatFormatting.GRAY));
        instance.setCoolDown(CONFIG.mitigationCooldown, DAMAGE_MITIGATION);
        instance.addMasteryPoint(entity);
    }

    // Hits are stored after armor and resistances
    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (releasing || owner.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        getMitigation(owner).ifPresent(mitigation -> {
            mitigation.stored += Math.max(0.0F, amount.get());
            amount.set(0.0F);
        });
        return true;
    }

    private static void release(Mitigation mitigation) {
        MITIGATIONS.remove(mitigation);
        LivingEntity owner = mitigation.owner;
        if (!owner.isAlive() || owner.isRemoved() || mitigation.stored <= 0.0F) {
            return;
        }

        releasing = true;
        try {
            owner.invulnerableTime = 0;
            owner.hurt(owner.damageSources().genericKill(), mitigation.stored);
        } finally {
            releasing = false;
        }

        if (owner.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ(), 12, owner.getBbWidth() * 0.4D, owner.getBbHeight() * 0.3D, owner.getBbWidth() * 0.4D, 0.1D);
            level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MITIGATIONS.isEmpty()) {
            return;
        }

        for (Mitigation mitigation : List.copyOf(MITIGATIONS)) {
            LivingEntity owner = mitigation.owner;
            if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
                MITIGATIONS.remove(mitigation);
                continue;
            }

            if (level.getGameTime() >= mitigation.until) {
                release(mitigation);
                continue;
            }

            if (owner.tickCount % SPARKLE_TICKS == 0) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ(), 2, owner.getBbWidth() * 0.4D, owner.getBbHeight() * 0.3D, owner.getBbWidth() * 0.4D, 0.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MITIGATIONS.clear();
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class WhaleQuirk extends Skill {

    private static final QuirkSkillsConfig.Whale CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Whale;

    private static final int CLICK_SOUND = 0;
    private static final int WHALE_STRIKE = 1;
    private static final int MODES = 2;

    public static final String STRIKE_TAG = "whaleStrike";

    private static final int LEND_SECONDS = 10;
    private static final int PARALYSIS_LEVEL = 4;

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/whale.png");
    }

    public WhaleQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CLICK_SOUND -> CONFIG.clickAuraCost;
            case WHALE_STRIKE -> CONFIG.strikeAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CLICK_SOUND -> "whale.click_sound";
            case WHALE_STRIKE -> "whale.whale_strike";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getWhale(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WHALE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case CLICK_SOUND -> clickSound(level, instance, entity);
            case WHALE_STRIKE -> whaleStrike(level, instance, entity);
        }
    }

    // temp water breathing

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
            lendSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        lendSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
    }

    // Click Sound

    private static void clickSound(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, CLICK_SOUND)) {
            return;
        }

        Vec3 center = entity.getBoundingBox().getCenter();
        AABB area = AABB.ofSize(center, CONFIG.clickSize, CONFIG.clickSize, CONFIG.clickSize);
        int ticks = CONFIG.clickStunSeconds * 20;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != entity && target.isAlive() && !target.isSpectator() && HitRules.canHit(entity, target))) {
            MobEffectInstance paralysis = new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), ticks, PARALYSIS_LEVEL);
            if (TensuraMobEffect.addEffect(target, paralysis, entity, instance.getSkill(), CLICK_SOUND)) {
                StrongarmQuirk.stun(target, ticks);
            }

            Vec3 point = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.NOTE, point.x, point.y + target.getBbHeight() * 0.5D, point.z, 3, 0.3D, 0.2D, 0.3D, 1.0D);
        }

        Vec3 look = entity.getLookAngle();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, center.x, center.y, center.z, 0, 0.0D, 1.0D, 0.0D, CONFIG.clickSize / 3.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, center.x, center.y, center.z, 0, look.x, 0.0D, look.z, CONFIG.clickSize / 4.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.9F);
        playSound(level, center, SoundEvents.DOLPHIN_AMBIENT, 1.5F, 0.6F);
        instance.setCoolDown(CONFIG.clickCooldown, CLICK_SOUND);
        instance.addMasteryPoint(entity);
    }

    // Whale Strike

    private static void whaleStrike(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.strikeRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, WHALE_STRIKE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        float damage = (float) ((mastered ? CONFIG.strikeDamageMastered : CONFIG.strikeDamage) + getMeleeDamage(entity) * CONFIG.strikeDamagePercent);
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.WATER_ELEMENTAL, WHALE_STRIKE);
        ((TensuraDamageSource) source).tensura$setElement(Element.WATER);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.clearFire();
        Vec3 away = target.position().subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, entity.getYRot()) : away.normalize();
        SkillHelper.knockBack(target, entity, instance, away, CONFIG.strikeKnockback, 0.0D, 0.4D);
        target.hurtMarked = true;

        // Water sprayed from the head onto the target
        Vec3 from = entity.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        for (double step = 0.0D; step <= 1.0D; step += 0.1D) {
            Vec3 spot = from.lerp(to, step);
            level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y, spot.z, 4, 0.15D, 0.15D, 0.15D, 0.1D);
        }

        level.sendParticles(ParticleTypes.SPLASH, to.x, to.y, to.z, 40, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.3D);
        level.sendParticles(ParticleTypes.FALLING_WATER, to.x, to.y + target.getBbHeight() * 0.4D, to.z, 20, target.getBbWidth() * 0.5D, 0.2D, target.getBbWidth() * 0.5D, 0.0D);
        playSound(level, to, SoundEvents.GENERIC_SPLASH, 1.2F, 0.7F);
        playSound(level, to, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);

        instance.getOrCreateTag().putLong(STRIKE_TAG, level.getGameTime());
        instance.markDirty();
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.strikeCooldown, WHALE_STRIKE);
        instance.addMasteryPoint(entity);
    }

    public static long getStrikeStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, STRIKE_TAG);
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

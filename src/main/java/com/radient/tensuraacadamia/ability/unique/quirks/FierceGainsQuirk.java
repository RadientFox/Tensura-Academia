package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class FierceGainsQuirk extends Skill {

    private static final QuirkSkillsConfig.FierceGains CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).FierceGains;

    private static final int BODY_BLOCK = 0;
    private static final int POWER_ROAR = 1;
    private static final int LIMIT_BREAK = 2;
    private static final int STACK_BREAKER = 3;
    private static final int MODES = 4;
    private static final int LEND_SECONDS = 10;
    private static final int PUMP_CHECK = 20;

    private static final String STACKS_TAG = "pumpStacks";
    private static final String TAKEN_TAG = "pumpTaken";
    private static final String HIT_TAG = "pumpHit";
    private static final String DECAY_TAG = "pumpDecay";
    private static final String LIMIT_TAG = "limitBreak";
    private static final ResourceLocation PUMP = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "combat_pump");
    private static final Map<LivingEntity, ManasSkillInstance> PUMPED = new WeakHashMap<>();

    public static final String BLOCKING_TAG = "bodyBlock";
    private static final long BLOCK_GRACE = 10L;
    private static final long BLOCK_RENEW = 5L;
    private static final ResourceLocation ARMOR = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "fierce_gains_armor");

    public FierceGainsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BODY_BLOCK -> CONFIG.blockAuraCost;
            case POWER_ROAR -> CONFIG.roarAuraCost;
            case LIMIT_BREAK -> CONFIG.limitBreakAuraCost;
            case STACK_BREAKER -> CONFIG.stackBreakerAuraCost;
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
            case BODY_BLOCK -> "fierce_gains.body_block";
            case POWER_ROAR -> "fierce_gains.power_roar";
            case LIMIT_BREAK -> "fierce_gains.limit_break";
            case STACK_BREAKER -> "fierce_gains.stack_breaker";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getFierceGains(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.FIERCE_GAINS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

//temp skill lending
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
            lendSkill(entity, ResistanceSkills.PIERCE_NULLIFICATION.get());
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, ResistanceSkills.PIERCE_NULLIFICATION.get());
        Modifiers.set(entity, Attributes.ARMOR, ARMOR, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        CompoundTag tag = instance.getOrCreateTag();
        tag.remove(STACKS_TAG);
        tag.remove(LIMIT_TAG);
        updatePump(instance, entity);
        PUMPED.remove(entity);
    }

    // Hardened Muscles

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateArmor(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateArmor(instance, entity);
        setStacks(instance, entity, 0);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateArmor(instance, entity);
        lendSkill(entity, ResistanceSkills.PIERCE_NULLIFICATION.get());
        if (instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateArmor(instance, owner);
        setStacks(instance, owner, 0);
    }

    public static int getStacks(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(STACKS_TAG);
    }

    private static boolean isLimitBroken(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(LIMIT_TAG) > entity.level().getGameTime();
    }

    private static int getCap(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isMastered(entity) ? CONFIG.pumpMaxStacksMastered : CONFIG.pumpMaxStacks;
    }

    private static void setStacks(ManasSkillInstance instance, LivingEntity entity, int stacks) {
        int capped = Math.max(0, isLimitBroken(instance, entity) ? stacks : Math.min(stacks, getCap(instance, entity)));
        CompoundTag tag = instance.getOrCreateTag();
        int before = tag.getInt(STACKS_TAG);
        if (capped != before) {
            tag.putInt(STACKS_TAG, capped);
            tag.putLong(DECAY_TAG, entity.level().getGameTime());
            instance.markDirty();
            if (entity instanceof Player player && capped > 0) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.fierce_gains.pump", capped).withStyle(ChatFormatting.GOLD), true);
            }
        }
        updatePump(instance, entity);
        if (capped > 0 || isLimitBroken(instance, entity)) {
            PUMPED.put(entity, instance);
        }
    }

    private static void updatePump(ManasSkillInstance instance, LivingEntity entity) {
        int stacks = getStacks(instance);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, PUMP, stacks * CONFIG.pumpDamagePerStack, AttributeModifier.Operation.ADD_VALUE);
        boolean degrading = instance.isMastered(entity) && stacks > 0 && stacks >= getCap(instance, entity);
        Modifiers.set(entity, TensuraAttributes.PHYSICAL_RESIST_DEGRADATION, PUMP, degrading ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || source.getEntity() == null || source.getEntity() == owner || amount.get() <= 0.0F || owner.level().isClientSide) {
            return true;
        }

        CompoundTag tag = instance.getOrCreateTag();
        double taken = tag.getDouble(TAKEN_TAG) + amount.get();
        int gained = (int) (taken / CONFIG.pumpDamagePerStackTaken);
        tag.putDouble(TAKEN_TAG, taken - gained * CONFIG.pumpDamagePerStackTaken);
        tag.putLong(HIT_TAG, owner.level().getGameTime());
        instance.markDirty();
        if (gained > 0) {
            setStacks(instance, owner, getStacks(instance) + gained);
            instance.addMasteryPoint(owner);
        }
        return true;
    }

    private static boolean tickPump(LivingEntity entity, ManasSkillInstance instance) {
        if (!entity.isAlive() || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
            return true;
        }

        CompoundTag tag = instance.getOrCreateTag();
        long time = level.getGameTime();
        if (tag.contains(LIMIT_TAG) && tag.getLong(LIMIT_TAG) <= time) {
            tag.remove(LIMIT_TAG);
            instance.markDirty();
            endLimitBreak(level, instance, entity);
        }

        int stacks = getStacks(instance);
        long quiet = CONFIG.pumpDecaySeconds * 20L;
        if (stacks > 0 && time - tag.getLong(HIT_TAG) >= quiet && time - tag.getLong(DECAY_TAG) >= quiet) {
            setStacks(instance, entity, stacks - 1);
            stacks--;
        }
        return stacks <= 0 && !isLimitBroken(instance, entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!PUMPED.isEmpty() && event.getServer().getTickCount() % PUMP_CHECK == 0) {
            PUMPED.entrySet().removeIf(entry -> tickPump(entry.getKey(), entry.getValue()));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PUMPED.clear();
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case POWER_ROAR -> roar(level, instance, entity);
            case LIMIT_BREAK -> limitBreak(level, instance, entity);
            case STACK_BREAKER -> stackBreaker(level, instance, entity);
            default -> {
            }
        }
    }

    private static void roar(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_ROAR)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double half = (mastered ? CONFIG.roarSizeMastered : CONFIG.roarSize) * 0.5D;
        int paralysis = (mastered ? CONFIG.roarParalysisSecondsMastered : CONFIG.roarParalysisSeconds) * 20;
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.SOUND_BLAST, POWER_ROAR);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(half), target -> target != entity && target.isAlive() && !target.isSpectator()
                && HitRules.canHit(entity, target))) {
            target.invulnerableTime = 0;
            target.hurt(source, (float) CONFIG.roarDamage);
            TensuraMobEffect.addEffect(target, new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), paralysis, 0), entity, instance.getSkill(), POWER_ROAR);
        }

        if (instance.isToggled()) {
            setStacks(instance, entity, getStacks(instance) + (mastered ? CONFIG.roarStacksMastered : CONFIG.roarStacks));
        }
        instance.setCoolDown(CONFIG.roarCooldown, POWER_ROAR);
        instance.addMasteryPoint(entity);
        Vec3 center = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, half * 2.0D / 3.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    private static void limitBreak(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isToggled()) {
            fail(entity, Component.translatable("tracadamia.skill.fierce_gains.needs_pump"));
            return;
        }
        if (isLimitBroken(instance, entity) || EnergyHelper.isOutOfEnergy(entity, instance, LIMIT_BREAK)) {
            return;
        }

        instance.getOrCreateTag().putLong(LIMIT_TAG, level.getGameTime() + CONFIG.limitBreakSeconds * 20L);
        instance.markDirty();
        PUMPED.put(entity, instance);
        updatePump(instance, entity);
        instance.setCoolDown(CONFIG.limitBreakCooldown, LIMIT_BREAK);
        instance.addMasteryPoint(entity);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ(), 6, entity.getBbWidth() * 0.5D, 0.2D, entity.getBbWidth() * 0.5D, 0.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5F, 1.2F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.fierce_gains.limit_break").withStyle(ChatFormatting.RED), true);
        }
    }

    private static void endLimitBreak(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int ticks = CONFIG.limitBreakAfterSeconds * 20;
        TensuraMobEffect.addEffect(entity, new MobEffectInstance(MobEffects.WEAKNESS, ticks, CONFIG.limitBreakWeakness - 1), entity, instance.getSkill(), LIMIT_BREAK);
        TensuraMobEffect.addEffect(entity, new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FRAGILITY), ticks, 0), entity, instance.getSkill(), LIMIT_BREAK);
        TensuraMobEffect.addEffect(entity, new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), ticks, 0), entity, instance.getSkill(), LIMIT_BREAK);
        setStacks(instance, entity, getStacks(instance));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.5F, 0.6F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.fierce_gains.limit_over").withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    private static void stackBreaker(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int stacks = getStacks(instance);
        if (stacks <= 0) {
            fail(entity, Component.translatable("tracadamia.skill.fierce_gains.no_stacks"));
            return;
        }

        double reach = entity instanceof Player player ? player.entityInteractionRange() : 3.0D;
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, reach, false, true);
        if (target == null || target == entity) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, STACK_BREAKER)) {
            return;
        }

        float damage = (float) (stacks * CONFIG.pumpDamagePerStack + entity.getAttributeValue(Attributes.ATTACK_DAMAGE));
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, STACK_BREAKER);
        ((TensuraDamageSource) source).tensura$setResistanceBypassLevel(1.0F);
        setStacks(instance, entity, 0);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.stackBreakerCooldown, STACK_BREAKER);
        instance.addMasteryPoint(entity);
        Vec3 at = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.4D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    private static void updateArmor(ManasSkillInstance instance, LivingEntity entity) {
        double armor = instance.isMastered(entity) ? CONFIG.armorMastered : CONFIG.armor;
        Modifiers.set(entity, Attributes.ARMOR, ARMOR, instance.isToggled() ? armor : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    // Body Block

    public static boolean isBlocking(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(BLOCKING_TAG) >= entity.level().getGameTime();
    }

    private static void setBlocking(ManasSkillInstance instance, LivingEntity entity, boolean blocking) {
        long now = entity.level().getGameTime();
        long until = instance.getOrCreateTag().getLong(BLOCKING_TAG);
        if (blocking ? until - now < BLOCK_RENEW : until != 0L) {
            instance.getOrCreateTag().putLong(BLOCKING_TAG, blocking ? now + BLOCK_GRACE : 0L);
            instance.markDirty();
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != BODY_BLOCK || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks == 0 && EnergyHelper.isOutOfEnergy(entity, instance, BODY_BLOCK)) {
            return false;
        }

        if (heldTicks >= CONFIG.blockSeconds * 20) {
            stopBlocking(instance, entity);
            return false;
        }

        if (heldTicks == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        }

        setBlocking(instance, entity, true);
        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == BODY_BLOCK && !entity.level().isClientSide && isBlocking(instance, entity)) {
            stopBlocking(instance, entity);
        }
    }

    private static void stopBlocking(ManasSkillInstance instance, LivingEntity entity) {
        setBlocking(instance, entity, false);
        instance.setCoolDown(CONFIG.blockCooldown, BODY_BLOCK);
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (!isBlocking(instance, entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(), 6, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.2D, entity.getBbWidth() * 0.4D, 0.1D);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
        return false;
    }

    // Projectiles bounce back
    @Override
    public void onProjectileHit(ManasSkillInstance instance, LivingEntity entity, EntityHitResult hitResult, Projectile projectile,
                                Changeable<ProjectileDeflection> deflection, Changeable<EntityEvents.ProjectileHitResult> result) {
        if (!isBlocking(instance, entity) || entity.level().isClientSide) {
            return;
        }

        projectile.deflect(ProjectileDeflection.REVERSE, entity, entity, entity instanceof Player);
        result.set(EntityEvents.ProjectileHitResult.PASS);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

}

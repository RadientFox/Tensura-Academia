package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.DamageReduction;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TailQuirk extends Skill {

    private static final QuirkSkillsConfig.Tail CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Tail;

    private static final int TAIL_WHIP = 0;
    private static final int SPIRAL_HIT = 1;
    private static final int TAIL_LEAP = 2;
    private static final int WRAP = 3;
    private static final int MODES = 4;

    public static final int WHIP_TICKS = 6;
    public static final int SPIRAL_TICKS = 8;
    public static final int LEAP_PUSH_TICKS = 5;
    private static final int WHIP_FLIGHT = 15;
    private static final int LEAP_FLIGHT = 60;

    private static final String WHIP_TAG = "tailWhip";
    private static final String SPIRAL_TAG = "spiralHit";
    private static final String LEAP_TAG = "tailLeap";
    private static final String WRAP_TAG = "wrapTarget";
    private static final String WRAP_ID_TAG = "wrapTargetId";
    private static final String TAIL_HEALTH_TAG = "tailHealth";

    private static final List<Leap> LEAPS = new ArrayList<>();
    private static final List<Wrap> WRAPS = new ArrayList<>();

    private record Leap(ManasSkillInstance instance, LivingEntity owner, long start, Set<LivingEntity> hit) {}

    private record Wrap(ManasSkillInstance instance, LivingEntity owner) {}

    public TailQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case TAIL_WHIP -> CONFIG.whipAuraCost;
            case SPIRAL_HIT -> CONFIG.spiralAuraCost;
            case TAIL_LEAP -> CONFIG.leapAuraCost;
            case WRAP -> CONFIG.wrapAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == TAIL_WHIP ? WRAP : mode - 1;
        }

        return mode == WRAP ? TAIL_WHIP : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case TAIL_WHIP -> "tail.tail_whip";
            case SPIRAL_HIT -> "tail.spiral_hit";
            case TAIL_LEAP -> "tail.tail_leap";
            case WRAP -> "tail.wrap";
            default -> super.getModeId(instance, mode);
        };
    }

    private static Optional<ManasSkillInstance> getTail(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TAIL.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Shared with TailClient

    public static long getWhipStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, WHIP_TAG);
    }

    public static long getSpiralStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, SPIRAL_TAG);
    }

    public static long getLeapStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, LEAP_TAG);
    }

    public static boolean isWrapping(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(WRAP_TAG);
    }

    public static int getWrapTargetId(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? -1 : tag.getInt(WRAP_ID_TAG);
    }

    // Tail Catch

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (isWrapping(instance) && WRAPS.stream().noneMatch(wrap -> wrap.owner() == entity)) {
            MultiArms.clearHeld(instance, entity, WRAP_TAG);
        }

        if (!instance.isToggled()) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        release(instance, entity);
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (instance.isToggled() && instance.getMastery() >= 0.0D && source.is(DamageTypeTags.IS_FALL)) {
            amount.set(DamageReduction.reduce(owner, source, amount.get(), CONFIG.fallReduction));
        }

        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case TAIL_WHIP -> tailWhip(level, instance, entity);
            case SPIRAL_HIT -> spiralHit(level, instance, entity);
            case TAIL_LEAP -> tailLeap(level, instance, entity);
            case WRAP -> wrap(level, instance, entity);
        }
    }


    private static void tailWhip(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.onGround()) {
            return;
        }

        LivingEntity target = MultiArms.getTarget(entity, CONFIG.whipRange);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, TAIL_WHIP)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        hit(instance, entity, target, (float) ((mastered ? CONFIG.whipDamageMastered : CONFIG.whipDamage) + getMeleeDamage(entity)), DamageTypes.PLAYER_ATTACK, TAIL_WHIP);

        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        target.setDeltaMovement(right.scale(CONFIG.whipKnockback).add(0.0D, 0.3D, 0.0D));
        target.hurtMarked = true;
        MultiArms.fling(instance, entity, target, (float) CONFIG.wallDamage, TAIL_WHIP, false, WHIP_FLIGHT);

        markTime(instance, WHIP_TAG, level);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
        playSound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.9F);
        instance.setCoolDown(CONFIG.whipCooldown, TAIL_WHIP);
        instance.addMasteryPoint(entity);
    }


    private static void spiralHit(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, SPIRAL_HIT)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.spiralRadiusMastered : CONFIG.spiralRadius;
        float damage = (float) ((mastered ? CONFIG.spiralDamageMastered : CONFIG.spiralDamage) + getMeleeDamage(entity));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius + 1.0D, 1.0D, radius + 1.0D), target -> target != entity && target.isAlive()
                && target.position().subtract(entity.position()).horizontalDistance() <= radius + target.getBbWidth() * 0.5D + entity.getBbWidth() * 0.5D)) {
            hit(instance, entity, target, damage, DamageTypes.PLAYER_ATTACK, SPIRAL_HIT);
            target.knockback(0.8D, entity.getX() - target.getX(), entity.getZ() - target.getZ());
        }

        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0D * i / 8.0D;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, entity.getX() + Math.cos(angle) * radius, entity.getY(0.4D), entity.getZ() + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        markTime(instance, SPIRAL_TAG, level);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
        instance.setCoolDown(CONFIG.spiralCooldown, SPIRAL_HIT);
        instance.addMasteryPoint(entity);
    }


    private static void tailLeap(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, TAIL_LEAP)) {
            return;
        }

        double distance = instance.isMastered(entity) ? CONFIG.leapDistanceMastered : CONFIG.leapDistance;
        entity.setDeltaMovement(MultiArms.getLaunchVelocity(entity.getLookAngle().add(0.0D, 0.2D, 0.0D), distance));
        entity.hurtMarked = true;
        LEAPS.removeIf(leap -> leap.owner() == entity);
        LEAPS.add(new Leap(instance, entity, level.getGameTime(), new HashSet<>()));

        markTime(instance, LEAP_TAG, level);
        playSound(level, entity, SoundEvents.GOAT_LONG_JUMP, 1.2F, 0.8F);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(), entity.getZ(), 8, 0.3D, 0.05D, 0.3D, 0.02D);
        instance.setCoolDown(CONFIG.leapCooldown, TAIL_LEAP);
        instance.addMasteryPoint(entity);
    }

    // true once the user lands
    private static boolean tickLeap(Leap leap) {
        LivingEntity owner = leap.owner();
        if (!owner.isAlive() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - leap.start();
        owner.resetFallDistance();
        boolean mastered = leap.instance().isMastered(owner);
        Vec3 motion = owner.getDeltaMovement();
        Vec3 heading = new Vec3(motion.x, 0.0D, motion.z).lengthSqr() > 1.0E-4D ? new Vec3(motion.x, 0.0D, motion.z).normalize() : owner.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(0.5D), target -> target != owner && target.isAlive() && !leap.hit().contains(target))) {
            leap.hit().add(target);
            hit(leap.instance(), owner, target, (float) (mastered ? CONFIG.leapDamageMastered : CONFIG.leapDamage), DamageTypes.PLAYER_ATTACK, TAIL_LEAP);
            target.setDeltaMovement(MultiArms.getLaunchVelocity(heading.add(0.0D, 0.4D, 0.0D), mastered ? CONFIG.leapKnockbackMastered : CONFIG.leapKnockback));
            target.hurtMarked = true;
            playSound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
        }

        return age > LEAP_FLIGHT || (age > 3 && owner.onGround());
    }


    private static void wrap(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isWrapping(instance)) {
            release(instance, entity);
            instance.setCoolDown(CONFIG.wrapCooldown, WRAP);
            return;
        }

        LivingEntity target = MultiArms.getTarget(entity, CONFIG.wrapRange);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (!MultiArms.isNoLarger(target, entity)) {
            fail(entity, "tracadamia.skill.tail.too_big");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, WRAP)) {
            return;
        }

        target.stopRiding();
        CompoundTag tag = instance.getOrCreateTag();
        tag.putUUID(WRAP_TAG, target.getUUID());
        tag.putInt(WRAP_ID_TAG, target.getId());
        tag.putDouble(TAIL_HEALTH_TAG, entity.getMaxHealth() * CONFIG.tailHealth);
        instance.markDirty();
        WRAPS.add(new Wrap(instance, entity));
        MultiArms.holdAt(entity, target, getWrapOffset(entity, target), MultiArms.HOLD_BODY, true);
        playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.6F);
        instance.addMasteryPoint(entity);
    }

    public static Vec3 getWrapOffset(LivingEntity entity, LivingEntity target) {
        return new Vec3(0.0D, 0.15D, -(entity.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.6D));
    }

    private static void release(ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, WRAP_TAG);
        WRAPS.removeIf(wrap -> wrap.owner() == entity);
    }

    private static boolean tickWrap(Wrap wrap) {
        ManasSkillInstance instance = wrap.instance();
        LivingEntity owner = wrap.owner();
        if (!isWrapping(instance)) {
            return true;
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, owner, WRAP_TAG);
        if (!owner.isAlive() || owner.isRemoved() || target == null) {
            MultiArms.clearHeld(instance, owner, WRAP_TAG);
            instance.setCoolDown(CONFIG.wrapCooldown, WRAP);
            return true;
        }

        MultiArms.holdAt(owner, target, getWrapOffset(owner, target), MultiArms.HOLD_BODY, true);
        if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(owner);
        }

        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        Optional<ManasSkillInstance> tail = getTail(entity).filter(TailQuirk::isWrapping);
        if (tail.isEmpty() || MultiArms.getHeldTarget(tail.get(), entity, WRAP_TAG) != attacker) {
            return;
        }

        ManasSkillInstance instance = tail.get();
        CompoundTag tag = instance.getOrCreateTag();
        double health = tag.getDouble(TAIL_HEALTH_TAG) - event.getAmount();
        event.setCanceled(true);
        if (health > 0.0D) {
            tag.putDouble(TAIL_HEALTH_TAG, health);
            instance.markDirty();
            playSound((ServerLevel) entity.level(), entity, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8F, 0.6F);
            return;
        }

        // Broken through, every tail move rests
        release(instance, entity);
        tag.remove(TAIL_HEALTH_TAG);
        for (int mode = 0; mode < MODES; mode++) {
            instance.setCoolDown(CONFIG.tailBrokenSeconds, mode);
        }

        instance.markDirty();
        playSound((ServerLevel) entity.level(), entity, SoundEvents.PLAYER_HURT, 1.0F, 0.6F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.tail.broken", CONFIG.tailBrokenSeconds).withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!LEAPS.isEmpty()) {
            LEAPS.removeIf(TailQuirk::tickLeap);
        }

        if (!WRAPS.isEmpty()) {
            WRAPS.removeIf(TailQuirk::tickWrap);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LEAPS.clear();
        WRAPS.clear();
    }

    private static void markTime(ManasSkillInstance instance, String key, ServerLevel level) {
        instance.getOrCreateTag().putLong(key, level.getGameTime());
        instance.markDirty();
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, ResourceKey<DamageType> type, int mode) {
        ResourceKey<DamageType> attack = type == DamageTypes.PLAYER_ATTACK && !(owner instanceof Player) ? DamageTypes.MOB_ATTACK : type;
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, attack, mode), damage);
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

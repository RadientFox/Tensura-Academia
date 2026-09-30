package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

public final class HellflameQuirk extends Skill {
    private static final int MAX_MASTERY = 2500;
    private static final int HEAT_LIMIT = 100;
    private static final int FLIGHT_TICKS = 1200;
    private static final int FLIGHT_COOLDOWN_TICKS = 2400;
    private static final String HEAT = "HellflameHeat", HOT_UNTIL = "HellflameHotUntil",
            WEAK_UNTIL = "HellflameWeakUntil", FLIGHT_UNTIL = "HellflameFlightUntil",
            FLIGHT_READY = "HellflameFlightReady", FLIGHT_ACTIVE = "HellflameFlightActive",
            HAD_MAYFLY = "HellflameHadMayfly", HAD_FLYING = "HellflameHadFlying",
            FIST_UNTIL = "HellflameFistUntil", LOCK_UNTIL = "HellflameLockUntil",
            CURTAIN_UNTIL = "HellflameCurtainUntil", CURTAIN_TARGET = "HellflameCurtainTarget",
            CURTAIN_X = "HellflameCurtainX", CURTAIN_Y = "HellflameCurtainY", CURTAIN_Z = "HellflameCurtainZ",
            PROMINENCE_END = "HellflameProminenceEnd", PROMINENCE_TARGET = "HellflameProminenceTarget";

    public HellflameQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return MAX_MASTERY; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.withDefaultNamespace("textures/item/blaze_powder.png");
    }
    @Override public int getModes(ManasSkillInstance instance) { return 4; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 4);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "hellflame.vanishing_fist";
            case 2 -> "hellflame.hells_curtain";
            case 3 -> "hellflame.prominence_burn";
            default -> "hellflame.jet_burn";
        };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }

    @Override public void onToggleOn(ManasSkillInstance instance, LivingEntity owner) {
        if (!(owner instanceof ServerPlayer player)) return;
        CompoundTag tag = instance.getOrCreateTag();
        long now = owner.level().getGameTime();
        if (now < tag.getLong(FLIGHT_READY) || now < tag.getLong(LOCK_UNTIL)) {
            instance.setToggled(false);
            instance.markDirty();
            tell(player, "Hellflame flight is unavailable.");
            return;
        }
        tag.putBoolean(HAD_MAYFLY, player.getAbilities().mayfly);
        tag.putBoolean(HAD_FLYING, player.getAbilities().flying);
        tag.putBoolean(FLIGHT_ACTIVE, true);
        tag.putLong(FLIGHT_UNTIL, now + FLIGHT_TICKS);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        instance.markDirty();
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.9F);
    }

    @Override public void onToggleOff(ManasSkillInstance instance, LivingEntity owner) {
        if (owner instanceof ServerPlayer player) stopFlight(instance, player, true);
    }

    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        if (owner instanceof ServerPlayer player) stopFlight(instance, player, false);
    }

    private static void stopFlight(ManasSkillInstance instance, ServerPlayer player, boolean cooldown) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.getBoolean(FLIGHT_ACTIVE)) return;
        tag.putBoolean(FLIGHT_ACTIVE, false);
        instance.setToggled(false);
        if (cooldown) tag.putLong(FLIGHT_READY, player.level().getGameTime() + cooldownTicks(player, FLIGHT_COOLDOWN_TICKS));
        if (!tag.getBoolean(HAD_MAYFLY) && !player.isCreative() && !player.isSpectator())
            player.getAbilities().mayfly = false;
        if (!tag.getBoolean(HAD_FLYING)) player.getAbilities().flying = false;
        player.onUpdateAbilities();
        instance.markDirty();
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner instanceof ServerPlayer player) || mode < 0 || mode > 3) return;
        long now = owner.level().getGameTime();
        long lockedUntil = instance.getOrCreateTag().getLong(LOCK_UNTIL);
        if (now < lockedUntil) {
            tell(player, "Hellflame is sealed for " + ((lockedUntil - now + 19) / 20) + "s after Prominence Burn.");
            return;
        }
        if ((mode == 1 || mode == 3) && instance.onCoolDown(mode)) {
            tell(player, "This Hellflame move is still cooling down.");
            return;
        }
        switch (mode) {
            case 0 -> jetBurn(instance, player);
            case 1 -> vanishingFist(instance, player);
            case 2 -> hellsCurtain(instance, player);
            case 3 -> prominenceBurn(instance, player);
            default -> { }
        }
    }

    private static boolean spend(ServerPlayer player, double aura) {
        var existence = TensuraStorages.getExistenceFrom(player);
        if (existence.getAura() < aura) { tell(player, "Not enough aura for Hellflame."); return false; }
        existence.setAura(existence.getAura() - aura);
        existence.markDirty();
        return true;
    }

    private static void used(ManasSkillInstance instance, ServerPlayer player, int mode, int cooldownSeconds, int heat) {
        instance.setCoolDown(cooldownSeconds > 0
                ? cooldownTicks(player, cooldownSeconds * 20) / 20 : 0, mode);
        addHeat(instance, player, heat);
        instance.addMasteryPoint(player);
        instance.markDirty();
    }

    private static int cooldownTicks(LivingEntity owner, int ticks) {
        Biome biome = owner.level().getBiome(owner.blockPosition()).value();
        return biome.getPrecipitationAt(owner.blockPosition()) == Biome.Precipitation.SNOW
                ? Math.max(1, (int) Math.round(ticks * 0.67)) : ticks;
    }

    private static void addHeat(ManasSkillInstance instance, ServerPlayer player, int amount) {
        CompoundTag tag = instance.getOrCreateTag();
        long now = player.level().getGameTime();
        if (now < tag.getLong(HOT_UNTIL)) return;
        int heat = Math.min(HEAT_LIMIT, tag.getInt(HEAT) + amount);
        if (heat >= HEAT_LIMIT) {
            tag.putInt(HEAT, 0);
            tag.putLong(HOT_UNTIL, now + 900);
            tag.putLong(WEAK_UNTIL, now + 600);
            tell(player, "Overheated: half damage dealt for 45s, increased damage taken for 30s.");
        } else tag.putInt(HEAT, heat);
    }

    private static DamageSource flame(ServerPlayer player) {
        DamageSource source = player.damageSources().source(TensuraDamageTypes.HEAT_WAVE, player);
        if (source instanceof TensuraDamageSource typed) typed.tensura$setElement(Element.FLAME);
        return source;
    }

    private static void jetBurn(ManasSkillInstance instance, ServerPlayer player) {
        if (!spend(player, 500)) return;
        boolean mastered = instance.getMastery() >= MAX_MASTERY;
        double reach = mastered ? 20 : 10;
        Vec3 origin = player.getEyePosition(), direction = player.getLookAngle().normalize();
        double damage = mastered ? 175 : 100;
        ServerLevel level = player.serverLevel();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach + 2), e -> HomingQuirk.isEnemy(player, e))) {
            Vec3 offset = target.getEyePosition().subtract(origin);
            double distance = offset.dot(direction);
            if (distance <= 0 || distance > reach || offset.subtract(direction.scale(distance)).length()
                    > Math.max(1.2, distance * 0.5) + target.getBbWidth() * 0.5 || !player.hasLineOfSight(target)) continue;
            if (target.hurt(flame(player), (float) damage)
                    && level.random.nextDouble() < (mastered ? 0.2 : 0.1)) target.setRemainingFireTicks(80);
        }
        for (int step = 1; step <= (int) reach; step++) {
            Vec3 center = origin.add(direction.scale(step));
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                    6 + step * 2, step * 0.23, step * 0.23, step * 0.23, 0.04);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.1F, 0.65F);
        used(instance, player, 0, 0, 20);
    }

    private static void vanishingFist(ManasSkillInstance instance, ServerPlayer player) {
        if (!spend(player, 1000)) return;
        instance.getOrCreateTag().putLong(FIST_UNTIL, player.level().getGameTime() + 1200);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1, 0.9F);
        used(instance, player, 1, 90, 15);
    }

    private static LivingEntity aimedTarget(ServerPlayer player, double reach, double halfAngleDegrees) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = start.add(look.scale(reach));
        Vec3 stop = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(player, start, stop, new AABB(start, stop).inflate(1),
                e -> e instanceof LivingEntity living && HomingQuirk.isEnemy(player, living), start.distanceToSqr(stop));
        if (hit != null && hit.getEntity() instanceof LivingEntity living) return living;
        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double minDot = Math.cos(Math.toRadians(halfAngleDegrees));
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(reach), e -> HomingQuirk.isEnemy(player, e))) {
            Vec3 offset = candidate.getEyePosition().subtract(start);
            double distance = offset.length();
            if (distance < 0.01 || distance > reach || offset.scale(1 / distance).dot(look) < minDot
                    || !player.hasLineOfSight(candidate)) continue;
            double score = 1 - offset.scale(1 / distance).dot(look) + distance / (reach * 10);
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }

    private static void hellsCurtain(ManasSkillInstance instance, ServerPlayer player) {
        LivingEntity target = aimedTarget(player, 24, 20);
        if (target == null) { tell(player, "Aim at a visible enemy within 24 blocks."); return; }
        if (!spend(player, 5000)) return;
        CompoundTag tag = instance.getOrCreateTag();
        tag.putUUID(CURTAIN_TARGET, target.getUUID());
        tag.putDouble(CURTAIN_X, target.getX());
        tag.putDouble(CURTAIN_Y, target.getY());
        tag.putDouble(CURTAIN_Z, target.getZ());
        tag.putLong(CURTAIN_UNTIL, player.level().getGameTime() + (instance.getMastery() >= MAX_MASTERY ? 900 : 600));
        player.level().playSound(null, target.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.7F);
        used(instance, player, 2, 0, 30);
    }

    private static void prominenceBurn(ManasSkillInstance instance, ServerPlayer player) {
        double range = instance.getMastery() >= MAX_MASTERY ? 4 : 2.5;
        LivingEntity target = aimedTarget(player, range + 1, 45);
        if (target == null || target.distanceToSqr(player) > (range + 1) * (range + 1)) {
            tell(player, "Prominence Burn needs an enemy directly nearby.");
            return;
        }
        if (!spend(player, 20000)) return;
        CompoundTag tag = instance.getOrCreateTag();
        long now = player.level().getGameTime();
        tag.putUUID(PROMINENCE_TARGET, target.getUUID());
        tag.putLong(PROMINENCE_END, now + 30);
        tag.putLong(LOCK_UNTIL, now + 1200);
        target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 35, 1));
        stopFlight(instance, player, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.5F);
        used(instance, player, 3, 300, 50);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ManasSkillInstance instance = skill(player);
        if (instance == null) return;
        if (instance.onCoolDown(0)) instance.setCoolDown(0, 0);
        if (instance.onCoolDown(2)) instance.setCoolDown(0, 2);
        CompoundTag tag = instance.getOrCreateTag();
        long now = player.level().getGameTime();
        if (tag.getBoolean(FLIGHT_ACTIVE)) {
            if (now >= tag.getLong(FLIGHT_UNTIL) || !player.isAlive()) stopFlight(instance, player, true);
            else {
                player.fallDistance = 0;
                if (now % 5 == 0) player.serverLevel().sendParticles(ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.3, player.getZ(), 5, 0.3, 0.25, 0.3, 0.02);
            }
        }
        if (now < tag.getLong(PROMINENCE_END)) tickProminence(instance, player);
        else if (tag.hasUUID(PROMINENCE_TARGET)) finishProminence(instance, player);
        if (now < tag.getLong(CURTAIN_UNTIL)) tickCurtain(instance, player);
        else if (tag.hasUUID(CURTAIN_TARGET)) { tag.remove(CURTAIN_TARGET); instance.markDirty(); }
        if (now % 20 == 0 && tag.getInt(HEAT) > 0 && now >= tag.getLong(HOT_UNTIL)) {
            tag.putInt(HEAT, tag.getInt(HEAT) - 1);
            instance.markDirty();
        }
        if (now % 20 == 0 && (tag.getInt(HEAT) > 0 || now < tag.getLong(HOT_UNTIL))) {
            tell(player, now < tag.getLong(HOT_UNTIL) ? "Hellflame: OVERHEATED"
                    : "Hellflame heat: " + tag.getInt(HEAT) + "/" + HEAT_LIMIT);
        }
    }

    private static void tickCurtain(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        ServerLevel level = player.serverLevel();
        Vec3 center = new Vec3(tag.getDouble(CURTAIN_X), tag.getDouble(CURTAIN_Y), tag.getDouble(CURTAIN_Z));
        double radius = instance.getMastery() >= MAX_MASTERY / 2.0 ? 10 : 8;
        if (tag.hasUUID(CURTAIN_TARGET) && level.getEntity(tag.getUUID(CURTAIN_TARGET)) instanceof LivingEntity target
                && target.isAlive()) {
            Vec3 offset = target.position().subtract(center).multiply(1, 0, 1);
            if (offset.lengthSqr() > (radius - 0.5) * (radius - 0.5)) {
                Vec3 inside = center.add(offset.normalize().scale(radius - 0.7));
                target.teleportTo(inside.x, target.getY(), inside.z);
                target.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (level.getGameTime() % 20 == 0) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center, center).inflate(radius, 4, radius),
                    e -> HomingQuirk.isEnemy(player, e) && e.position().subtract(center).multiply(1, 0, 1).lengthSqr() <= radius * radius)) {
                target.hurt(flame(player), 20);
            }
        }
        if (level.getGameTime() % 4 == 0) for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32;
            double x = center.x + Math.cos(angle) * radius, z = center.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.FLAME, x, center.y + 1.5, z, 3, 0.1, 1.4, 0.1, 0.01);
        }
    }

    private static void tickProminence(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.hasUUID(PROMINENCE_TARGET)
                || !(player.serverLevel().getEntity(tag.getUUID(PROMINENCE_TARGET)) instanceof LivingEntity target)
                || !target.isAlive()) { tag.remove(PROMINENCE_TARGET); instance.markDirty(); return; }
        Vec3 forward = player.getLookAngle().multiply(1, 0, 1).normalize();
        target.teleportTo(player.getX() + forward.x * 0.8, target.getY(), player.getZ() + forward.z * 0.8);
        player.setDeltaMovement(0, 0.55, 0);
        target.setDeltaMovement(0, 0.55, 0);
        player.hurtMarked = target.hurtMarked = true;
        player.fallDistance = target.fallDistance = 0;
        if (player.level().getGameTime() % 2 == 0) player.serverLevel().sendParticles(ParticleTypes.FLAME,
                target.getX(), target.getY() + 1, target.getZ(), 24, 1, 1.2, 1, 0.05);
    }

    private static void finishProminence(ManasSkillInstance instance, ServerPlayer player) {
        CompoundTag tag = instance.getOrCreateTag();
        UUID id = tag.getUUID(PROMINENCE_TARGET);
        tag.remove(PROMINENCE_TARGET);
        instance.markDirty();
        if (!(player.serverLevel().getEntity(id) instanceof LivingEntity target) || !target.isAlive()) return;
        double radius = instance.getMastery() >= MAX_MASTERY ? 4 : 2.5;
        float damage = instance.getMastery() >= MAX_MASTERY ? 750 : 500;
        ServerLevel level = player.serverLevel();
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(radius), e -> HomingQuirk.isEnemy(player, e))) {
            if (hit.distanceToSqr(target) <= radius * radius) hit.hurt(flame(player), damage);
        }
        target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 300, 1));
        target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100));
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1, target.getZ(),
                180, radius, radius, radius, 0.13);
        level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.7F);
    }

    @SubscribeEvent public static void damage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        ManasSkillInstance defense = skill(target);
        long now = target.level().getGameTime();
        float amount = event.getAmount();
        if (defense != null) {
            DamageSource source = event.getSource();
            Element element = source instanceof TensuraDamageSource typed ? typed.tensura$getElement() : null;
            if (source.is(DamageTypeTags.IS_FIRE) || source.is(TensuraDamageTypes.HEAT_WAVE)
                    || element == Element.FLAME) amount *= 0.2F;
            if (source.is(DamageTypes.DROWN) || source.is(TensuraDamageTypes.WATER_ELEMENTAL)
                    || source.is(TensuraDamageTypes.WATER_BLADE) || source.is(TensuraDamageTypes.WATER_BREATH)
                    || element == Element.WATER) amount *= 1.5F;
            CompoundTag tag = defense.getOrCreateTag();
            if (now < tag.getLong(WEAK_UNTIL)) amount *= 1.5F;
            if (now < tag.getLong(CURTAIN_UNTIL)) {
                Vec3 center = new Vec3(tag.getDouble(CURTAIN_X), tag.getDouble(CURTAIN_Y), tag.getDouble(CURTAIN_Z));
                double radius = defense.getMastery() >= MAX_MASTERY / 2.0 ? 10 : 8;
                if (target.position().subtract(center).multiply(1, 0, 1).lengthSqr() <= radius * radius) amount *= 0.75F;
            }
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            ManasSkillInstance offense = skill(attacker);
            if (offense != null) {
                if (attacker.level().isRaining()) amount *= 0.5F;
                if (now < offense.getOrCreateTag().getLong(HOT_UNTIL)) amount *= 0.5F;
                if (event.getSource().is(DamageTypes.PLAYER_ATTACK)
                        && now < offense.getOrCreateTag().getLong(FIST_UNTIL))
                    amount *= offense.getMastery() >= MAX_MASTERY / 2.0 ? 1.75F : 1.5F;
            }
        }
        event.setAmount(amount);
    }

    @SubscribeEvent public static void igniteMelee(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        ManasSkillInstance instance = skill(attacker);
        if (instance != null && attacker.level().getGameTime() < instance.getOrCreateTag().getLong(FIST_UNTIL)
                && attacker.getRandom().nextDouble() < 0.2) event.getEntity().setRemainingFireTicks(80);
    }

    private static ManasSkillInstance skill(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.HELLFLAME.get()).orElse(null);
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}

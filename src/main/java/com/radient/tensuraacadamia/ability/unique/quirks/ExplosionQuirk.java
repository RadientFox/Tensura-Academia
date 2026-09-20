package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneTheme;
import com.radient.tensuraacadamia.ability.ultimate.afo.ButterflyEffectTheme;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.resist.ResistSkill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** My Hero Academia's Explosion quirk, adapted to Tensura's aura and mastery systems. */
public final class ExplosionQuirk extends Skill {
    private static final int MAX_MASTERY = 8_000;
    private static final int HOWITZER_DURATION = 300;
    private static final double MELEE_EXPLOSION_RADIUS = 2.5D;
    private static final double PULT_MAX_LAUNCH_DISTANCE = 150.0D;
    private static final double PULT_LAUNCH_SPEED = 7.5D;
    private static final String SCATTERSHOT = "explosion_scattershot";
    private static final String CLUSTER_ENABLED = "explosion_cluster";
    private static final String AIR_TICKS = "explosion_air_ticks";
    private static final String COOLDOWN_SECONDS_MIGRATED = "explosion_cooldown_seconds_migrated";
    private static final int FINAL_BOSS_COOLDOWN_TICKS = 300 * 20; // 300 seconds
    private static final Map<UUID, Rush> RUSHES = new HashMap<>();
    private static final Map<UUID, PultLaunch> PULT_LAUNCHES = new HashMap<>();
    private static final Map<UUID, Howitzer> HOWITZERS = new HashMap<>();
    private static final Map<UUID, Long> SPEED_READY_AT = new HashMap<>();
    private static final Map<UUID, Long> FINAL_BOSS_COOLDOWN = new HashMap<>();
    private static final List<ClusterBurst> CLUSTER_BURSTS = new ArrayList<>();
    private static final List<ScheduledExplosion> SCHEDULED_EXPLOSIONS = new ArrayList<>();

    private static final class ClusterBurst {
        final LivingEntity owner;
        final Vec3 center;
        final float damage;
        int delay = 2;
        int remaining = 2;

        ClusterBurst(LivingEntity owner, Vec3 center, float damage) {
            this.owner = owner;
            this.center = center;
            this.damage = damage;
        }
    }
    
    private static final class ScheduledExplosion {
        final ServerPlayer attacker;
        final LivingEntity target;
        final Vec3 targetPos;
        final long executeTick;
        int remainingTicks = 60; // 3 seconds

        ScheduledExplosion(ServerPlayer attacker, LivingEntity target, Vec3 targetPos, long executeTick) {
            this.attacker = attacker;
            this.target = target;
            this.targetPos = targetPos;
            this.executeTick = executeTick;
        }
    }

    private record Rush(LivingEntity owner, Vec3 origin, Vec3 direction, int ticks,
                        double maxDistance, boolean pult, double speed, boolean cluster) {
        Rush nextTick() { return new Rush(owner, origin, direction, ticks + 1, maxDistance, pult, speed, cluster); }
        boolean finished() {
            return ticks >= 20 || owner.position().distanceToSqr(origin) >= maxDistance * maxDistance;
        }
    }

    private record PultLaunch(LivingEntity target, Vec3 origin, Vec3 direction) {
    }

    private static final class Howitzer {
        final ServerPlayer owner;
        final double initialPower;
        final boolean cluster;
        int ticks;

        Howitzer(ServerPlayer owner, double initialPower, boolean cluster) {
            this.owner = owner;
            this.initialPower = initialPower;
            this.cluster = cluster;
        }

        double power() {
            return Math.clamp(Math.max(initialPower, ticks / (double) HOWITZER_DURATION), 0.0D, 1.0D);
        }

        double flightRamp() {
            double progress = Math.clamp(ticks / (double) HOWITZER_DURATION, 0.0D, 1.0D);
            return progress * progress;
        }
    }

    public ExplosionQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/explosion.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 500_000.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.explosion.attacks_on"), true);
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) return;
        ensureCooldownSlots(instance, getModes(instance));
        var tag = instance.getOrCreateTag();
        int ticks = entity.onGround() || entity.isInWaterOrBubble() ? 0
                : Math.min(12_000, tag.getInt(AIR_TICKS) + 1);
        tag.putInt(AIR_TICKS, ticks);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        int mastery = percent(instance);
        if (mastery >= 90) return 7;
        if (mastery >= 75) return 6;
        if (mastery >= 50) return 5;
        if (mastery >= 40) return 4;
        if (mastery >= 25) return 3;
        if (mastery >= 10) return 2;
        return 1;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "explosion.explosive_speed";
            case 1 -> "explosion.stun_grenade";
            case 2 -> percent(instance) >= 50 && instance.getOrCreateTag().getBoolean(SCATTERSHOT)
                    ? "explosion.scattershot" : "explosion.ap_shot";
            case 3 -> "explosion.explode_a_pult";
            case 4 -> "explosion.land_mine_blast";
            case 5 -> "explosion.howitzer_impact";
            case 6 -> "explosion.cluster";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        boolean cluster = cluster(instance);
        return switch (mode) {
            case 0 -> cluster ? 30.0D : 20.0D;
            case 1 -> 500.0D;
            case 2 -> 2_000.0D;
            case 3 -> 3_000.0D;
            case 4 -> 5_000.0D;
            case 5 -> cluster && instance.isMastered(entity) ? 100_000.0D : 50_000.0D;
            default -> 0.0D;
        };
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        return resist(owner, (skill, passive) -> skill.onBeingDamaged(passive, owner, source, amount));
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source,
                                 Changeable<Float> amount) {
        return resist(owner, (skill, passive) -> skill.onTakenDamage(passive, owner, source, amount));
    }

    @Override
    public boolean onEffectAdded(ManasSkillInstance instance, LivingEntity owner, Entity source,
                                 Changeable<MobEffectInstance> effect) {
        return resist(owner, (skill, passive) -> skill.onEffectAdded(passive, owner, source, effect));
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                  DamageSource source, Changeable<Float> amount) {
        if (owner.level().isClientSide || source.getDirectEntity() != owner) return true;
        
        // I'M THE FINAL BOSS!! - True Passive: Cancel AFO theme and play Butterfly Effect in 32 block radius around you
        if (isAFOUserWithTheme(target)) {
            AllForOneTheme.silence(target, ButterflyEffectTheme.DURATION_TICKS);
            if (owner instanceof ServerPlayer ownerPlayer) {
                ButterflyEffectTheme.play(ownerPlayer);
                ownerPlayer.displayClientMessage(Component.literal("I'M THE FINAL BOSS!!"), true);
            }
        }

        if (!instance.isToggled() || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(TensuraDamageTypes.HEAT_WAVE)) return true;
        
        float bonus = percent(instance);
        if (bonus <= 0) return true;
        boolean cluster = cluster(instance);
        float perBurst = cluster ? bonus * 0.5F : bonus;
        ServerLevel level = (ServerLevel) owner.level();
        Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        meleeExplosion(level, owner, center, perBurst, cluster);
        if (cluster) {
            CLUSTER_BURSTS.add(new ClusterBurst(owner, center, perBurst));
        }
        instance.addMasteryPoint(owner);
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) return;
        int modes = getModes(instance);
        ensureCooldownSlots(instance, modes);
        if (mode < 0 || mode >= modes) return;
        var tag = instance.getOrCreateTag();
        if (mode == 2 && player.isCrouching() && percent(instance) >= 50) {
            boolean scatter = !tag.getBoolean(SCATTERSHOT);
            tag.putBoolean(SCATTERSHOT, scatter);
            instance.markDirty();
            player.displayClientMessage(Component.translatable(scatter
                    ? "tracadamia.skill.explosion.scattershot_selected"
                    : "tracadamia.skill.explosion.ap_shot_selected"), true);
            return;
        }
        if (mode == 6) {
            boolean enabled = !tag.getBoolean(CLUSTER_ENABLED);
            tag.putBoolean(CLUSTER_ENABLED, enabled);
            instance.markDirty();
            player.displayClientMessage(Component.translatable(enabled
                    ? "tracadamia.skill.explosion.cluster_on"
                    : "tracadamia.skill.explosion.cluster_off"), true);
            return;
        }
        if (mode == 0 && player.serverLevel().getGameTime()
                < SPEED_READY_AT.getOrDefault(player.getUUID(), 0L)) return;
        double auraCost = getAuraCost(player, instance, mode);
        if (instance.onCoolDown(mode) || !payAura(player, auraCost)) return;

        boolean used = switch (mode) {
            case 0 -> explosiveSpeed(instance, player);
            case 1 -> stunGrenade(instance, player);
            case 2 -> apShot(instance, player);
            case 3 -> explodeAPult(instance, player);
            case 4 -> landMine(instance, player);
            case 5 -> howitzer(instance, player);
            default -> false;
        };
        if (used) instance.addMasteryPoint(player);
        else refundAura(player, auraCost);
    }

    private static boolean explosiveSpeed(ManasSkillInstance instance, ServerPlayer player) {
        boolean cluster = cluster(instance);
        ServerLevel level = player.serverLevel();
        java.util.UUID playerId = player.getUUID();
        Vec3 playerPosition = player.position();
        Vec3 direction = player.getLookAngle().normalize();
        RUSHES.put(playerId, new Rush(player, playerPosition, direction, 0,
                cluster ? 20.0D : 5.0D, false, cluster ? 5.0D : 1.5D, cluster));
        Vec3 exhaust = playerPosition.add(0.0D, player.getBbHeight() * 0.45D, 0.0D)
                .subtract(direction.scale(0.8D));
        orangeExplosion(level, exhaust, 1.1, false);
        directionalBurst(level, exhaust, direction.scale(-1.0D), cluster ? 24 : 9);
        if (cluster) {
            Vec3 side = horizontalDirection(player).cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
            explosionFlash(level, exhaust.add(side.scale(0.45D)), 2);
            explosionFlash(level, exhaust.subtract(side.scale(0.45D)), 2);
        }
        boolean mastered = percent(instance) >= 100;
        if (cluster && !mastered) {
            instance.setCoolDown(1, 0);
            SPEED_READY_AT.remove(playerId);
        } else if (!cluster && mastered) {
            instance.setCoolDown(0, 0);
            SPEED_READY_AT.remove(playerId);
        } else {
            instance.setCoolDown(0, 0);
            SPEED_READY_AT.put(playerId, level.getGameTime() + 10L);
        }
        return true;
    }
    
    private static boolean isAFOUserWithTheme(LivingEntity entity) {
        if (!AllForOneTheme.isPlaying(entity.getUUID())) return false;
        
        var instance = SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ALL_FOR_ONE.get().getRegistryName());
        return instance.isPresent() && instance.get().isToggled();
    }
    
    private static void triggerFinalBossAttack(ManasSkillInstance instance, ServerPlayer player, LivingEntity afoTarget) {
        long now = player.serverLevel().getGameTime();
        if (FINAL_BOSS_COOLDOWN.getOrDefault(player.getUUID(), 0L) > now) {
            player.displayClientMessage(Component.literal("Final Boss on cooldown: " + 
                    ((FINAL_BOSS_COOLDOWN.get(player.getUUID()) - now) / 20) + " seconds"), true);
            return;
        }
        
        ServerLevel level = player.serverLevel();
        Vec3 targetPos = afoTarget.position();
        
        // Stop AFO theme and play Butterfly Effect for one minute.
        AllForOneTheme.silence(afoTarget, ButterflyEffectTheme.DURATION_TICKS);
        ButterflyEffectTheme.play(player);
        
        // Freeze both combatants for the wind-up, then release them on the impact beat.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
        afoTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
        afoTarget.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 60, 2));
        
        // Schedule the massive explosion after slowdown
        SCHEDULED_EXPLOSIONS.add(new ScheduledExplosion(player, afoTarget, targetPos, now + 60));
        
        // Set cooldown
        FINAL_BOSS_COOLDOWN.put(player.getUUID(), now + FINAL_BOSS_COOLDOWN_TICKS);
    }

    private static boolean stunGrenade(ManasSkillInstance instance, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 base = player.position();
        Vec3 forward = horizontalDirection(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        boolean mastered = instance.isMastered(player);
        AABB search = player.getBoundingBox().inflate(5.0D, 5.0D, 5.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                target -> target != player && target.isAlive())) {
            AABB bounds = target.getBoundingBox();
            if (!intersectsForwardBox(bounds, base, forward, right, 0.0D, 5.0D, 2.5D, 0.0D, 5.0D)) continue;
            boolean direct = intersectsForwardBox(bounds, base, forward, right,
                    0.0D, 1.0D, 0.5D, 0.0D, 1.0D);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS),
                    60, direct ? 2 : 1));
            hit(level, player, target, (direct ? 40.0F : 20.0F) * (mastered ? 3.0F : 1.0F));
        }
        stunGrenadeVisual(level, base, forward, right);
        instance.setCoolDown(3, 1);
        return true;
    }

    private static boolean apShot(ManasSkillInstance instance, ServerPlayer player) {
        boolean scatter = percent(instance) >= 50 && instance.getOrCreateTag().getBoolean(SCATTERSHOT);
        if (scatter) return scatterShot(instance, player);

        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition();
        Vec3 forward = player.getLookAngle().normalize();
        double range = 20.0D;
        double halfWidth = 0.8D;
        Vec3 end = origin.add(forward.scale(range));
        float damage = instance.isMastered(player) ? 300.0F : 150.0F;
        AABB search = player.getBoundingBox().expandTowards(forward.scale(range)).inflate(halfWidth);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                target -> target != player && target.isAlive())) {
            if (target.getBoundingBox().inflate(halfWidth).clip(origin, end).isPresent()) {
                hit(level, player, target, damage);
            }
        }
        int steps = 12;
        for (int i = 1; i <= steps; i++) {
            Vec3 point = origin.add(forward.scale(range * i / steps));
            explosionFlash(level, point, 1);
            smallExplosionFlash(level, point, 2, 0.09D);
        }
        explosionFlash(level, end, 2);
        playExplosionSound(level, origin.add(forward.scale(Math.min(3.0D, range))), 1.2D);
        instance.setCoolDown(5, 2);
        return true;
    }

    private static boolean scatterShot(ManasSkillInstance instance, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 base = player.position();
        Vec3 forward = horizontalDirection(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        AABB search = player.getBoundingBox().expandTowards(forward.scale(5.0D)).inflate(1.5D, 1.0D, 1.5D);
        float damage = instance.isMastered(player) ? 200.0F : 100.0F;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, search,
                target -> target != player && target.isAlive())) {
            if (intersectsForwardBox(target.getBoundingBox(), base, forward, right,
                    0.0D, 5.0D, 1.5D, 0.0D, 3.0D)) {
                hit(level, player, target, damage);
            }
        }

        for (int depth = 1; depth <= 5; depth++) {
            for (int lane = -1; lane <= 1; lane++) {
                Vec3 point = base.add(forward.scale(depth - 0.35D))
                        .add(right.scale(lane)).add(0.0D, 1.0D, 0.0D);
                explosionFlash(level, point, 1);
                smallExplosionFlash(level, point, 1, 0.08D);
            }
        }
        Vec3 center = base.add(forward.scale(2.5D)).add(0.0D, 1.0D, 0.0D);
        playExplosionSound(level, center, 1.5D);
        instance.setCoolDown(5, 2);
        return true;
    }

    private static boolean explodeAPult(ManasSkillInstance instance, ServerPlayer player) {
        Vec3 direction = player.getLookAngle().normalize();
        player.setDeltaMovement(direction.scale(1.5));
        player.hurtMarked = true;
        player.resetFallDistance();
        RUSHES.put(player.getUUID(), new Rush(player, player.position(), direction, 0, 5.0D, true, 1.5D, false));
        instance.setCoolDown(10, 3);
        orangeExplosion(player.serverLevel(), player.position().subtract(direction), 1.2, false);
        return true;
    }

    private static boolean landMine(ManasSkillInstance instance, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        AABB area = new AABB(center.x - 5.0D, center.y, center.z - 5.0D,
                center.x + 5.0D, center.y + 1.0D, center.z + 5.0D);
        for (Entity target : level.getEntities(player, area,
                target -> target.isAlive() && (target instanceof LivingEntity || target instanceof Projectile))) {
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(movement.x * 0.35D, target instanceof Projectile ? 2.0D : 1.5D,
                    movement.z * 0.35D);
            target.hurtMarked = true;
        }
        SmokescreenQuirk.disableInArea(level, center, 5.0D, 100);
        groundExplosionField(level, center, 5.0D, 2.5D);
        instance.setCoolDown(20, 4);
        return true;
    }

    private static boolean howitzer(ManasSkillInstance instance, ServerPlayer player) {
        boolean cluster = cluster(instance) && instance.isMastered(player);
        double height = heightAboveGround(player, cluster ? 110 : 40);
        double required = cluster ? 100.0D : 30.0D;
        if (height < required) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.explosion.too_low", (int) required), true);
            return false;
        }
        int airTicks = instance.getOrCreateTag().getInt(AIR_TICKS);
        double heightScale = Math.clamp((height - required) / (cluster ? 50.0D : 70.0D), 0.0D, 1.0D);
        double timeScale = Math.clamp(airTicks / 200.0D, 0.0D, 1.0D);
        double power = Math.max(heightScale, timeScale);
        HOWITZERS.put(player.getUUID(), new Howitzer(player, power, cluster));
        player.startAutoSpinAttack(HOWITZER_DURATION, 0.0F, ItemStack.EMPTY);
        instance.setCoolDown(cluster ? 240 : 120, 5);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Process scheduled Final Boss explosions
        Iterator<ScheduledExplosion> explosionIterator = SCHEDULED_EXPLOSIONS.iterator();
        while (explosionIterator.hasNext()) {
            ScheduledExplosion scheduled = explosionIterator.next();
            
            if (!scheduled.attacker.isAlive() || !scheduled.target.isAlive() 
                    || !(scheduled.attacker.level() instanceof ServerLevel level)) {
                explosionIterator.remove();
                continue;
            }
            
            if (scheduled.remainingTicks > 0) {
                scheduled.remainingTicks--;
                // Show countdown visual
                if (scheduled.remainingTicks % 20 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, 
                            scheduled.target.getX(), scheduled.target.getY() + 0.5, scheduled.target.getZ(),
                            1, 0.5, 0.5, 0.5, 0.02);
                }
                continue;
            }
            
            // Execute the explosion
            if (!scheduled.target.isAlive()) {
                explosionIterator.remove();
                continue;
            }
            
            // The freeze-frame ends on the impact beat: remove the slow and let survivors burst forward.
            scheduled.attacker.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            scheduled.attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));
            scheduled.target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            scheduled.target.removeEffect(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS));
            scheduled.target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1));

            // Point blank 1000 damage
            hit(level, scheduled.attacker, scheduled.target, 1000.0F);
            
            // 50 damage in 30x30x30 area behind them
            Vec3 behindDirection = scheduled.target.getLookAngle().scale(-1.0D);
            Vec3 areaCenter = scheduled.target.position().add(behindDirection.scale(15.0D));
            double radius = 15.0D;
            
            for (LivingEntity affected : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(areaCenter, areaCenter).inflate(radius), target -> target != scheduled.target && target.isAlive())) {
                hit(level, scheduled.attacker, affected, 50.0F);
                Vec3 away = affected.position().subtract(areaCenter).normalize();
                affected.setDeltaMovement(away.x * 3.0, 2.0, away.z * 3.0);
                affected.hurtMarked = true;
            }
            
            // Visual effects
            impactExplosionField(level, scheduled.targetPos, 15.0D, true);
            explosionFlash(level, scheduled.targetPos, 20);
            level.sendParticles(ParticleTypes.FLASH, scheduled.targetPos.x, scheduled.targetPos.y, scheduled.targetPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            playExplosionSound(level, scheduled.targetPos, 15.0D);
            
            scheduled.attacker.displayClientMessage(Component.literal("I'M THE FINAL BOSS!!"), true);
            explosionIterator.remove();
        }
        
        Iterator<ClusterBurst> clusterIterator = CLUSTER_BURSTS.iterator();
        while (clusterIterator.hasNext()) {
            ClusterBurst burst = clusterIterator.next();
            if (!burst.owner.isAlive() || !(burst.owner.level() instanceof ServerLevel level)) {
                clusterIterator.remove();
                continue;
            }
            if (--burst.delay > 0) continue;
            meleeExplosion(level, burst.owner, burst.center, burst.damage, true);
            if (--burst.remaining == 0) clusterIterator.remove();
            else burst.delay = 2;
        }

        Iterator<Map.Entry<UUID, Rush>> rushIterator = RUSHES.entrySet().iterator();
        while (rushIterator.hasNext()) {
            Map.Entry<UUID, Rush> entry = rushIterator.next();
            Rush rush = entry.getValue();
            if (!rush.owner.isAlive() || !(rush.owner.level() instanceof ServerLevel level) || rush.finished()) {
                rushIterator.remove();
                continue;
            }
            
            // I'M THE FINAL BOSS!! - Check for passing AFO user during cluster explosive speed
            if (rush.cluster && rush.ticks > 0) {
                var instance = SkillAPI.getSkillsFrom(rush.owner).getSkill(QuirkSkills.EXPLOSION.get().getRegistryName());
                if (instance.isPresent()) {
                    AABB sweptPath = rush.owner.getBoundingBox().expandTowards(rush.direction.scale(rush.speed())).inflate(3.0D);
                    for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                            sweptPath, target -> target != rush.owner && target.isAlive())) {
                        if (rush.owner instanceof ServerPlayer player && isAFOUserWithTheme(nearby)) {
                            triggerFinalBossAttack(instance.get(), player, nearby);
                            rushIterator.remove();
                            continue;
                        }
                    }
                }
            }
            
            rush.owner.setDeltaMovement(rush.direction.scale(rush.speed()));
            rush.owner.hurtMarked = true;
            rush.owner.resetFallDistance();
            if (rush.ticks % 2 == 0) {
                Vec3 trail = rush.owner.position().add(0.0D, rush.owner.getBbHeight() * 0.45D, 0.0D)
                        .subtract(rush.direction.scale(0.8D));
                explosionFlash(level, trail, 1);
                smallExplosionFlash(level, trail, 3, 0.12D);
            }
            if (!rush.pult()) {
                entry.setValue(rush.nextTick());
                continue;
            }
            LivingEntity nearest = nearestLiving(level, rush.owner, rush.owner.getBoundingBox().inflate(1.5));
            if (nearest != null) {
                hit(level, rush.owner, nearest, 5.0F);
                Vec3 launch = rush.direction.add(0.0, 0.5, 0.0).normalize().scale(PULT_LAUNCH_SPEED);
                nearest.setDeltaMovement(launch);
                nearest.hurtMarked = true;
                PULT_LAUNCHES.put(nearest.getUUID(), new PultLaunch(nearest, nearest.position(), launch.normalize()));
                orangeExplosion(level, nearest.position(), 2.0, false);
                rushIterator.remove();
            } else {
                entry.setValue(rush.nextTick());
            }
        }

        // Cap Explode-A-Pult's knockback explicitly. Motion modifiers can otherwise
        // preserve its velocity far beyond the intended range.
        Iterator<PultLaunch> pultIterator = PULT_LAUNCHES.values().iterator();
        while (pultIterator.hasNext()) {
            PultLaunch launch = pultIterator.next();
            if (!launch.target.isAlive() || launch.target.isRemoved()
                    || !(launch.target.level() instanceof ServerLevel)) {
                pultIterator.remove();
                continue;
            }
            if (launch.target.position().distanceToSqr(launch.origin) >= PULT_MAX_LAUNCH_DISTANCE * PULT_MAX_LAUNCH_DISTANCE) {
                Vec3 cappedPosition = launch.origin.add(launch.direction.scale(PULT_MAX_LAUNCH_DISTANCE));
                launch.target.setPos(cappedPosition.x, cappedPosition.y, cappedPosition.z);
                launch.target.setDeltaMovement(Vec3.ZERO);
                launch.target.hurtMarked = true;
                pultIterator.remove();
            }
        }

        Iterator<Howitzer> howitzerIterator = HOWITZERS.values().iterator();
        while (howitzerIterator.hasNext()) {
            Howitzer attack = howitzerIterator.next();
            if (!attack.owner.isAlive() || attack.owner.isRemoved()
                    || !(attack.owner.level() instanceof ServerLevel level)) {
                howitzerIterator.remove();
                continue;
            }
            attack.ticks++;
            if (attack.ticks < HOWITZER_DURATION && !attack.owner.onGround() && !attack.owner.horizontalCollision) {
                if (!attack.owner.isAutoSpinAttack()) {
                    attack.owner.startAutoSpinAttack(HOWITZER_DURATION - attack.ticks, 0.0F, ItemStack.EMPTY);
                }
                Vec3 look = attack.owner.getLookAngle().normalize();
                double power = attack.power();
                double flightRamp = attack.flightRamp();
                double speed = (attack.cluster ? 0.55D : 0.4D)
                        + flightRamp * (attack.cluster ? 5.0D : 3.85D);
                Vec3 desired = look.scale(speed);
                desired = new Vec3(desired.x, Math.min(-0.08D, desired.y - 0.06D), desired.z);
                double steering = 0.22D + flightRamp * 0.38D;
                Vec3 motion = attack.owner.getDeltaMovement().scale(1.0D - steering).add(desired.scale(steering));
                attack.owner.setDeltaMovement(motion);
                attack.owner.hurtMarked = true;
                attack.owner.resetFallDistance();
                howitzerTrail(level, attack.owner, look, attack.ticks, flightRamp, attack.cluster);
                continue;
            }
            double power = attack.power();
            double size = attack.cluster ? 20.0 + 30.0 * power : 10.0 + 20.0 * power;
            float damage = (float) (attack.cluster ? 500.0 + 4_500.0 * power : 300.0 + 2_700.0 * power);
            Vec3 center = attack.owner.position();
            double radius = size / 2.0;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center, center).inflate(radius), target -> target != attack.owner && target.isAlive())) {
                hit(level, attack.owner, target, damage);
                Vec3 away = target.position().subtract(center).normalize();
                target.setDeltaMovement(away.x * 2.0, 2.5, away.z * 2.0);
                target.hurtMarked = true;
            }
            if (attack.owner.isAutoSpinAttack()) {
                attack.owner.startAutoSpinAttack(1, 0.0F, ItemStack.EMPTY);
            }
            impactExplosionField(level, center, radius, attack.cluster);
            howitzerIterator.remove();
        }
    }

    private static int percent(ManasSkillInstance instance) {
        return Math.clamp((int) Math.floor(instance.getMastery() / 80.0D), 0, 100);
    }

    private static void ensureCooldownSlots(ManasSkillInstance instance, int required) {
        List<Integer> current = instance.getCooldownList();
        var tag = instance.getOrCreateTag();
        if (current.size() >= required && tag.getBoolean(COOLDOWN_SECONDS_MIGRATED)) return;
        List<Integer> expanded = new ArrayList<>(current);
        boolean changed = false;
        while (expanded.size() < required) expanded.add(0);
        if (expanded.size() != current.size()) changed = true;

        if (!tag.getBoolean(COOLDOWN_SECONDS_MIGRATED)) {
            int[] maximumSeconds = {1, 3, 5, 10, 20,
                    cluster(instance) && percent(instance) >= 100 ? 240 : 120, 0};
            for (int mode = 0; mode < expanded.size() && mode < maximumSeconds.length; mode++) {
                if (expanded.get(mode) > maximumSeconds[mode]) {
                    expanded.set(mode, maximumSeconds[mode]);
                    changed = true;
                }
            }
            tag.putBoolean(COOLDOWN_SECONDS_MIGRATED, true);
            changed = true;
        }
        if (changed) {
            instance.setCoolDownList(expanded);
            instance.markDirty();
        }
    }

    private static boolean cluster(ManasSkillInstance instance) {
        return percent(instance) >= 90 && instance.getOrCreateTag().getBoolean(CLUSTER_ENABLED);
    }

    private static ManasSkillInstance passiveInstance(ResistSkill skill) {
        ManasSkillInstance passive = new TensuraSkillInstance(skill);
        passive.setMastery(0);
        passive.setToggled(true);
        return passive;
    }

    private static boolean alreadyResistant(LivingEntity owner, ResistSkill skill) {
        return SkillUtils.isSkillToggled(owner, skill);
    }

    private static boolean resist(LivingEntity owner,
                                  java.util.function.BiFunction<ResistSkill, ManasSkillInstance, Boolean> call) {
        ResistSkill heatNull = ResistanceSkills.HEAT_NULLIFICATION.get();
        ResistSkill flame = ResistanceSkills.FLAME_ATTACK_RESISTANCE.get();
        if (!alreadyResistant(owner, heatNull) && !call.apply(heatNull, passiveInstance(heatNull))) return false;
        if (!alreadyResistant(owner, flame) && !call.apply(flame, passiveInstance(flame))) return false;
        return true;
    }

    private static boolean payAura(LivingEntity entity, double cost) {
        var existence = TensuraStorages.getExistenceFrom(entity);
        if (existence.getAura() < cost) {
            if (entity instanceof Player player)
                player.displayClientMessage(Component.translatable("tracadamia.skill.explosion.not_enough_aura", (long) cost), true);
            return false;
        }
        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        return true;
    }

    private static void refundAura(LivingEntity entity, double cost) {
        var existence = TensuraStorages.getExistenceFrom(entity);
        existence.setAura(existence.getAura() + cost);
        existence.markDirty();
    }

    private static DamageSource explosionSource(ServerLevel level, LivingEntity owner) {
        DamageSource source = level.damageSources().source(DamageTypes.EXPLOSION, owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);
        tensuraSource.tensura$setResistanceBypassLevel(1.0F);
        return source;
    }

    private static DamageSource heatSource(ServerLevel level, LivingEntity owner) {
        DamageSource source = level.damageSources().source(TensuraDamageTypes.HEAT_WAVE, owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);
        tensuraSource.tensura$setElement(Element.FLAME);
        return source;
    }

    /** Finds the same closest valid target as the former stream/min pipeline without per-tick stream allocation. */
    private static LivingEntity nearestLiving(ServerLevel level, LivingEntity owner, AABB bounds) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                candidate -> candidate != owner && candidate.isAlive())) {
            double distance = target.distanceToSqr(owner);
            if (distance < nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void hit(ServerLevel level, LivingEntity owner, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(explosionSource(level, owner), damage);
    }

    private static void heatHit(ServerLevel level, LivingEntity owner, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(heatSource(level, owner), damage);
    }

    private static void meleeExplosion(ServerLevel level, LivingEntity owner, Vec3 center,
                                       float damage, boolean cluster) {
        double radiusSquared = MELEE_EXPLOSION_RADIUS * MELEE_EXPLOSION_RADIUS;
        AABB area = new AABB(center, center).inflate(MELEE_EXPLOSION_RADIUS);
        for (LivingEntity affected : level.getEntitiesOfClass(LivingEntity.class, area,
                affected -> affected != owner && affected.isAlive()
                        && affected.getBoundingBox().getCenter().distanceToSqr(center) <= radiusSquared)) {
            heatHit(level, owner, affected, damage);
        }
        orangeExplosion(level, center, MELEE_EXPLOSION_RADIUS, false);
        smallExplosionFlash(level, center, cluster ? 8 : 3, cluster ? 0.28D : 0.18D);
    }

    private static Vec3 horizontalDirection(LivingEntity entity) {
        Vec3 look = entity.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() < 1.0E-5D) {
            double yaw = Math.toRadians(entity.getYRot());
            horizontal = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        return horizontal.normalize();
    }

    private static boolean intersectsForwardBox(AABB bounds, Vec3 base, Vec3 forward, Vec3 right,
                                                double minimumDepth, double maximumDepth, double halfWidth,
                                                double minimumHeight, double maximumHeight) {
        Vec3 center = bounds.getCenter();
        Vec3 relative = center.subtract(base);
        double halfX = bounds.getXsize() * 0.5D;
        double halfZ = bounds.getZsize() * 0.5D;
        double depth = relative.dot(forward);
        double lateral = relative.dot(right);
        double depthExtent = Math.abs(forward.x) * halfX + Math.abs(forward.z) * halfZ;
        double lateralExtent = Math.abs(right.x) * halfX + Math.abs(right.z) * halfZ;
        return depth + depthExtent >= minimumDepth && depth - depthExtent <= maximumDepth
                && Math.abs(lateral) <= halfWidth + lateralExtent
                && bounds.maxY >= base.y + minimumHeight && bounds.minY <= base.y + maximumHeight;
    }

    private static void stunGrenadeVisual(ServerLevel level, Vec3 base, Vec3 forward, Vec3 right) {
        for (int i = 0; i < 9; i++) {
            double depth = 0.25D + (i % 5);
            double side = (i % 3 - 1) * 1.15D;
            double height = 0.7D + (i % 4) * 1.05D;
            explosionFlash(level, base.add(forward.scale(depth)).add(right.scale(side)).add(0.0D, height, 0.0D), 1);
        }
        Vec3 center = base.add(forward.scale(2.0D)).add(0.0D, 2.5D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z,
                14, 1.35D, 1.35D, 1.35D, 0.035D);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z,
                10, 1.25D, 1.25D, 1.25D, 0.04D);
        playExplosionSound(level, center, 2.0D);
    }

    private static void directionalBurst(ServerLevel level, Vec3 center, Vec3 direction, int count) {
        smallExplosionFlash(level, center, count, 0.18D);
        Vec3 smokeCenter = center.add(direction.normalize().scale(0.35D));
        level.sendParticles(ParticleTypes.POOF, smokeCenter.x, smokeCenter.y, smokeCenter.z,
                Math.max(3, count / 3), 0.22D, 0.22D, 0.22D, 0.025D);
    }

    private static void howitzerTrail(ServerLevel level, ServerPlayer player, Vec3 look, int ticks,
                                      double ramp, boolean cluster) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) right = new Vec3(1.0D, 0.0D, 0.0D);
        else right = right.normalize();
        Vec3 up = right.cross(look).normalize();
        double angle = ticks * (0.55D + ramp * 1.4D);
        double helixRadius = 0.4D + ramp * 0.8D;
        Vec3 offset = right.scale(Math.cos(angle) * helixRadius).add(up.scale(Math.sin(angle) * helixRadius));
        Vec3 exhaust = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                .subtract(look.scale(0.9D + ramp * 1.35D));
        Vec3 spark = exhaust.add(offset);
        int intensity = Math.clamp((int) Math.floor(ramp * 5.0D), 0, 5);
        int sparks = cluster ? 8 + intensity * 4 : 3 + intensity * 2;
        smallExplosionFlash(level, spark, sparks, cluster ? 0.2D + ramp * 0.2D : 0.1D + ramp * 0.12D);
        smallExplosionFlash(level, exhaust.subtract(offset.scale(0.65D)),
                cluster ? 5 + intensity * 2 : 2 + intensity, 0.1D + ramp * 0.1D);
        if (ticks % Math.max(1, 4 - intensity / 2) == 0) {
            level.sendParticles(ParticleTypes.POOF, exhaust.x, exhaust.y, exhaust.z,
                    2 + intensity, 0.16D + ramp * 0.2D, 0.16D + ramp * 0.2D,
                    0.16D + ramp * 0.2D, 0.018D + ramp * 0.02D);
        }
        int burstInterval = cluster ? Math.max(1, 3 - intensity / 2) : Math.max(1, 6 - intensity);
        if (ticks % burstInterval == 0) {
            explosionFlash(level, exhaust, cluster ? 5 + intensity * 2 : 1 + intensity);
            explosionFlash(level, exhaust.subtract(look.scale(0.5D + ramp)), cluster ? 3 + intensity : 1 + intensity / 2);
            if (cluster || intensity >= 3) {
                smallExplosionFlash(level, exhaust.add(right.scale(helixRadius)), 3 + intensity, 0.14D + ramp * 0.1D);
                smallExplosionFlash(level, exhaust.subtract(right.scale(helixRadius)), 3 + intensity, 0.14D + ramp * 0.1D);
            }
        }
        int soundInterval = Math.max(4, 16 - intensity * 2);
        if (ticks % soundInterval == 0) playExplosionSound(level, exhaust, 0.8D + ramp * 1.4D);
    }

    private static void orangeExplosion(ServerLevel level, Vec3 center, double radius, boolean large) {
        explosionFlash(level, center, large ? 3 : 1);
        playExplosionSound(level, center, radius);
    }

    private static void explosionFlash(ServerLevel level, Vec3 center, int count) {
        level.sendParticles(MHAParticles.ORANGE_EXPLOSION.get(), center.x, center.y, center.z,
                Math.max(1, count), 0.12D, 0.12D, 0.12D, 0.005D);
    }

    private static void smallExplosionFlash(ServerLevel level, Vec3 center, int count, double spread) {
        level.sendParticles(MHAParticles.SMALL_ORANGE_EXPLOSION.get(), center.x, center.y, center.z,
                Math.max(1, count), spread, spread, spread, 0.003D);
    }

    private static void playExplosionSound(ServerLevel level, Vec3 center, double radius) {
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, Math.min(4.0F, 0.5F + (float) radius * 0.12F), 0.85F + level.random.nextFloat() * 0.2F);
    }

    private static void groundExplosionField(ServerLevel level, Vec3 center, double halfSize, double spacing) {
        for (double x = -halfSize; x <= halfSize + 0.01D; x += spacing) {
            for (double z = -halfSize; z <= halfSize + 0.01D; z += spacing) {
                double worldX = center.x + x;
                double worldZ = center.z + z;
                double groundY = groundSurface(level, worldX, center.y + 6.0D, worldZ, 20);
                Vec3 blast = new Vec3(worldX, groundY + 1.0D, worldZ);
                explosionFlash(level, blast, 1);
                smallExplosionFlash(level, blast, 1, 0.08D);
            }
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0D, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        playExplosionSound(level, center, halfSize);
    }

    private static void impactExplosionField(ServerLevel level, Vec3 center, double radius, boolean cluster) {
        explosionFlash(level, center.add(0.0D, 0.2D, 0.0D), cluster ? 8 : 3);
        if (cluster) smallExplosionFlash(level, center.add(0.0D, 0.4D, 0.0D), 16, 0.65D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.4D, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        int points = cluster
                ? Math.clamp((int) Math.ceil(radius * 3.0D), 24, 80)
                : Math.clamp((int) Math.ceil(radius * 2.0D), 10, 48);
        double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < points; i++) {
            double distance = radius * Math.sqrt((i + 0.5D) / points);
            double angle = i * goldenAngle;
            double worldX = center.x + Math.cos(angle) * distance;
            double worldZ = center.z + Math.sin(angle) * distance;
            double groundY = groundSurface(level, worldX, center.y + 3.0D, worldZ,
                    Math.max(10, (int) Math.ceil(radius)));
            Vec3 blast = new Vec3(worldX, groundY + 0.08D, worldZ);
            explosionFlash(level, blast, cluster ? 2 : 1);
            if (cluster) smallExplosionFlash(level, blast, 2, 0.12D);
        }
        int ringPoints = Math.clamp((int) Math.ceil(radius * 1.5D), 12, 36);
        for (int i = 0; i < ringPoints; i++) {
            double angle = Math.PI * 2.0D * i / ringPoints;
            double xDirection = Math.cos(angle);
            double zDirection = Math.sin(angle);
            Vec3 wave = center.add(xDirection * Math.min(radius, 2.0D), 0.35D,
                    zDirection * Math.min(radius, 2.0D));
            level.sendParticles(ParticleTypes.POOF, wave.x, wave.y, wave.z,
                    0, xDirection * 0.28D, 0.06D, zDirection * 0.28D, 1.0D);
        }
        playExplosionSound(level, center, radius);
    }

    private static double groundSurface(ServerLevel level, double x, double startY, double z, int scanDown) {
        int top = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(startY));
        int bottom = Math.max(level.getMinBuildHeight(), top - scanDown);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos((int) Math.floor(x), top, (int) Math.floor(z));
        for (int y = top; y >= bottom; y--) {
            pos.setY(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y + 1.0D;
        }
        return startY;
    }

    private static double heightAboveGround(LivingEntity entity, int max) {
        int x = entity.getBlockX();
        int z = entity.getBlockZ();
        int start = entity.getBlockY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, start - 1, z);
        for (int y = start - 1; y >= Math.max(entity.level().getMinBuildHeight(), start - max); y--) {
            pos.setY(y);
            if (!entity.level().getBlockState(pos).getCollisionShape(entity.level(), pos).isEmpty())
                return entity.getY() - y - 1.0D;
        }
        return max;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUSHES.clear();
        PULT_LAUNCHES.clear();
        HOWITZERS.clear();
        SPEED_READY_AT.clear();
        CLUSTER_BURSTS.clear();
        FINAL_BOSS_COOLDOWN.clear();
        SCHEDULED_EXPLOSIONS.clear();
    }
}

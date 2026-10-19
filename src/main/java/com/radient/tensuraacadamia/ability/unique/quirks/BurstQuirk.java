package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Limbs;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.magic.field.MagicExplosion;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class BurstQuirk extends Skill {

    private static final QuirkSkillsConfig.Burst CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Burst;

    private static final int SUICIDE_BURST = 0;
    private static final int BODY_BURST = 1;
    private static final int MODES = 2;

    private static final int LAUNCH_TICKS = 40;

    //Explosion
    private static class BurstExplosion extends MagicExplosion {
        private BurstExplosion(Level level, Entity owner) {
            super(level, owner);
        }

        @Override
        public void applyEffect(LivingEntity target, boolean instant) {
            if (instant) {
                super.applyEffect(target, true);
            }
        }
    }

    private static final class Launch {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final Vec3 direction;
        private final long start;
        private Vec3 lastPos;
        private boolean airborne;

        private Launch(ManasSkillInstance instance, LivingEntity owner, Vec3 direction, long start) {
            this.instance = instance;
            this.owner = owner;
            this.direction = direction;
            this.start = start;
            this.lastPos = owner.position();
        }
    }

    private static final List<Launch> LAUNCHES = new ArrayList<>();
    private static boolean bursting = false;

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/burst.png");
    }

    public BurstQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SUICIDE_BURST -> CONFIG.suicideAuraCost;
            case BODY_BURST -> CONFIG.auraCost;
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
            case SUICIDE_BURST -> "burst.suicide_burst";
            case BODY_BURST -> "burst.body_burst";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getBurst(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.BURST.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case SUICIDE_BURST -> suicideBurst(level, instance, entity);
            case BODY_BURST -> bodyBurst(level, instance, entity);
        }
    }

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
    public boolean onDeath(ManasSkillInstance instance, LivingEntity entity, DamageSource source) {
        if (!bursting && instance.isToggled() && entity.level() instanceof ServerLevel level) {
            explode(level, instance, entity, BODY_BURST, getBodyBurstDamage(instance, entity), getBodyBurstRadius(instance, entity));
        }
        return true;
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        LAUNCHES.removeIf(launch -> launch.owner == entity);
    }

    // Suicide Burst

    private static void suicideBurst(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (Limbs.countLimbs(entity) <= 0) {
            fail(entity, "tracadamia.skill.burst.no_limbs");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SUICIDE_BURST)) {
            return;
        }

        if (CONFIG.suicideHealthCost > 0.0D) {
            payHealth(level, instance, entity, SUICIDE_BURST, CONFIG.suicideHealthCost);
            if (!entity.isAlive()) {
                return;
            }
        }

        Vec3 look = entity.getLookAngle();
        BruiserQuirk.launch(level, instance, entity, look.scale(CONFIG.suicideSpeed / 20.0D), false);
        LAUNCHES.removeIf(launch -> launch.owner == entity);
        LAUNCHES.add(new Launch(instance, entity, look, level.getGameTime()));

        Vec3 back = entity.getBoundingBox().getCenter().subtract(look.scale(entity.getBbWidth() * 0.6D));
        level.sendParticles(ParticleTypes.EXPLOSION, back.x, back.y, back.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 20, 0.2D, 0.2D, 0.2D, 0.1D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.2F);
        instance.setCoolDown(CONFIG.suicideCooldown, SUICIDE_BURST);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickLaunch(Launch launch) {
        LivingEntity owner = launch.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - launch.start;
        Vec3 moved = owner.position().subtract(launch.lastPos);
        launch.lastPos = owner.position();
        AABB reach = owner.getBoundingBox().expandTowards(moved).inflate(0.5D);
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, reach, other -> other != owner && other.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(owner, other) && HitRules.canHit(owner, other))
                .stream()
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(owner)))
                .orElse(null);
        if (target != null) {
            burstInto(level, launch, target);
            return true;
        }

        if (!owner.onGround() && age > 1L) {
            launch.airborne = true;
        }

        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.SMOKE, owner.getX(), owner.getY(0.5D), owner.getZ(), 3, 0.2D, 0.2D, 0.2D, 0.02D);
        }
        return age > LAUNCH_TICKS || launch.airborne && owner.onGround();
    }

    private static void burstInto(ServerLevel level, Launch launch, LivingEntity target) {
        LivingEntity owner = launch.owner;
        ManasSkillInstance instance = launch.instance;
        int limbs = Limbs.countLimbs(owner);
        if (limbs <= 0) {
            return;
        }

        float damage = (float) (owner.getMaxHealth() * (instance.isMastered(owner) ? CONFIG.suicideHealthMultiplierMastered : CONFIG.suicideHealthMultiplier) * limbs);
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, SUICIDE_BURST), damage);
        for (int limb = 0; limb < Limbs.COUNT; limb++) {
            if (Limbs.hasLimb(owner, limb)) {
                Limbs.loseLimb(owner, limb, Limbs.BURST);
            }
        }

        Vec3 bounce = launch.direction.scale(-0.3D).add(0.0D, 0.35D, 0.0D);
        owner.setDeltaMovement(bounce);
        if (owner instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new BruiserQuirk.LeapPayload(bounce.toVector3f()));
            player.displayClientMessage(Component.translatable("tracadamia.skill.burst.limbs_burst", limbs, target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        } else {
            owner.hurtMarked = true;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z, 12, 0.4D, 0.4D, 0.4D, 0.2D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.7F);
    }


    private static float getBodyBurstDamage(ManasSkillInstance instance, LivingEntity entity) {
        return (float) (entity.getMaxHealth() * (instance.isMastered(entity) ? CONFIG.healthMultiplierMastered : CONFIG.healthMultiplier));
    }

    private static float getBodyBurstRadius(ManasSkillInstance instance, LivingEntity entity) {
        return (float) (instance.isMastered(entity) ? CONFIG.radiusMastered : CONFIG.radius);
    }

    private static void bodyBurst(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, BODY_BURST)) {
            return;
        }

        explode(level, instance, entity, BODY_BURST, getBodyBurstDamage(instance, entity), getBodyBurstRadius(instance, entity));
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.activationCooldown, BODY_BURST);
        if (entity.getRandom().nextDouble() >= (instance.isMastered(entity) ? CONFIG.surviveChanceMastered : CONFIG.surviveChance)) {
            killUser(entity, entity.damageSources().explosion(null, null));
        }
    }

    private static void explode(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int mode, float damage, float radius) {
        MagicExplosion explosion = new BurstExplosion(level, entity);
        explosion.setSkill(entity, instance, (Skill) instance.getSkill(), mode);
        explosion.setElementalAttack(false);
        explosion.setDamage(damage);
        explosion.setSize(radius);
        explosion.setVisualOnly(!CONFIG.breakBlocks);
        explosion.setPos(entity.position().add(0.0D, 0.2D, 0.0D));
        level.addFreshEntity(explosion);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.6F);
    }

    // Lethal unless something prevents death
    private static void killUser(LivingEntity entity, DamageSource source) {
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setResistanceBypassLevel(2.0F);
        tensuraSource.tensura$setDodgeBypass();

        bursting = true;
        try {
            entity.invulnerableTime = 0;
            entity.hurt(source, Math.max(entity.getMaxHealth(), entity.getHealth()) * 100.0F + entity.getAbsorptionAmount());
        } finally {
            bursting = false;
        }
    }

    public static void payHealth(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int mode, double share) {
        float cost = (float) (entity.getMaxHealth() * share);
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.SUICIDE, mode);
        if (entity.getHealth() > cost) {
            entity.setHealth(entity.getHealth() - cost);
            level.broadcastDamageEvent(entity, source);
        } else {
            killUser(entity, entity.damageSources().explosion(null, null));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!LAUNCHES.isEmpty()) {
            LAUNCHES.removeIf(BurstQuirk::tickLaunch);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LAUNCHES.clear();
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

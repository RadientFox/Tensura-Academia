package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TwinImpactQuirk extends Skill {

    private static final QuirkSkillsConfig.TwinImpact CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).TwinImpact;

    private static final int PULLING_STRIKE = 0;
    private static final int MODES = 1;

    private static final int MARKER_TICKS = 5;
    private static final DustParticleOptions ECHO = new DustParticleOptions(new Vector3f(0.4F, 0.85F, 1.0F), 1.2F);
    private static final double AIR_DRAG = 0.91D;
    private static final double FALL_DRAG = 0.98D;
    private static final double GRAVITY = 0.08D;

    private static final class Impact {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final DamageSource source;
        private final long at;
        private float damage;

        private Impact(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, float damage, long at) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.source = source;
            this.damage = damage;
            this.at = at;
        }
    }

    private record Echo(LivingEntity owner, LivingEntity target) {}

    private static final List<Impact> IMPACTS = new ArrayList<>();
    // Oldest first
    private static final List<Echo> ECHOES = new ArrayList<>();
    private static boolean impacting = false;

    public TwinImpactQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == PULLING_STRIKE ? CONFIG.pullAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == PULLING_STRIKE ? "twin_impact.pulling_strike" : super.getModeId(instance, mode);
    }


    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        ECHOES.removeIf(echo -> echo.owner() == entity);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        ECHOES.removeIf(echo -> echo.owner() == entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    public static Optional<ManasSkillInstance> getTwinImpact(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TWIN_IMPACT.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static boolean isProjectileAttack(DamageSource source) {
        return source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if (event.getAmount() <= 0.0F || !(source.getEntity() instanceof LivingEntity owner) || owner == target || !(owner.level() instanceof ServerLevel level)) {
            return;
        }

        ManasSkillInstance instance = getTwinImpact(owner).filter(ManasSkillInstance::isToggled).orElse(null);
        if (instance == null) {
            return;
        }

        float original = event.getAmount();
        float followUp = isMarked(owner, target) ? (float) (1.0D + CONFIG.followUpDamage) : 1.0F;
        event.setAmount(original * followUp);
        if (impacting || !isProjectileAttack(source)) {
            return;
        }

        event.setAmount((float) (original * followUp * CONFIG.firstHitDamage));

        boolean mastered = instance.isMastered(owner);
        float second = (float) (original * (mastered ? CONFIG.secondHitDamageMastered : CONFIG.secondHitDamage));
        long at = level.getGameTime() + Math.max(1L, Math.round(CONFIG.secondHitSeconds * 20.0D));

        for (Impact impact : IMPACTS) {
            if (impact.owner == owner && impact.target == target && impact.at == at) {
                impact.damage = Math.max(impact.damage, second);
                return;
            }
        }

        DamageSource copy = TensuraDamageHelper.copy(source);
        if (mastered) {
            TensuraDamageSource tensuraSource = (TensuraDamageSource) copy;
            tensuraSource.tensura$setResistanceBypassLevel(Math.max(1.0F, tensuraSource.tensura$getResistanceBypassLevel()));
        }

        IMPACTS.add(new Impact(instance, owner, target, copy, second, at));
    }

    private static boolean tickImpact(Impact impact) {
        LivingEntity target = impact.target;
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
            return true;
        }

        if (level.getGameTime() < impact.at) {
            return false;
        }

        impacting = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(impact.source, impact.damage);
        } finally {
            impacting = false;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 from = impact.owner.getEyePosition().subtract(center);
        Vec3 normal = from.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : from.normalize();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, center.x, center.y, center.z, 0, normal.x * 0.6D, normal.y * 0.6D, normal.z * 0.6D, 1.0D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.2D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
        impact.instance.addMasteryPoint(impact.owner);
        return true;
    }


    public static boolean isMarked(LivingEntity owner, LivingEntity target) {
        for (Echo echo : ECHOES) {
            if (echo.owner() == owner && echo.target() == target) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getAmount() <= 0.0F || !(event.getSource().getEntity() instanceof LivingEntity owner) || owner == target || owner.level().isClientSide) {
            return;
        }

        getTwinImpact(owner).filter(ManasSkillInstance::isToggled).ifPresent(instance -> {
            ECHOES.removeIf(echo -> echo.owner() == owner && echo.target() == target);
            ECHOES.add(new Echo(owner, target));
            int marks = Math.max(1, instance.isMastered(owner) ? CONFIG.echoMarksMastered : CONFIG.echoMarks);
            long kept = ECHOES.stream().filter(echo -> echo.owner() == owner).count();
            for (int index = 0; index < ECHOES.size() && kept > marks; ) {
                if (ECHOES.get(index).owner() == owner) {
                    ECHOES.remove(index);
                    kept--;
                } else {
                    index++;
                }
            }
        });
    }

    private static boolean tickEcho(Echo echo) {
        LivingEntity owner = echo.owner();
        LivingEntity target = echo.target();
        if (!owner.isAlive() || owner.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != owner.level()
                || owner.distanceTo(target) > CONFIG.echoRange || !(target.level() instanceof ServerLevel level)) {
            return true;
        }

        if (owner instanceof ServerPlayer player && level.getGameTime() % MARKER_TICKS == 0L) {
            level.sendParticles(player, ECHO, true, target.getX(), target.getY() + target.getBbHeight() + 0.4D, target.getZ(), 4, 0.15D, 0.05D, 0.15D, 0.0D);
        }
        return false;
    }


    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != PULLING_STRIKE || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity target = MultiArms.getTarget(entity, CONFIG.pullRange);
        if (target == null || !isMarked(entity, target)) {
            fail(entity, Component.translatable("tracadamia.skill.twin_impact.not_marked"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, PULLING_STRIKE)) {
            return;
        }

        strike(level, instance, entity, target);
        instance.setCoolDown(CONFIG.pullCooldown, PULLING_STRIKE);
        instance.addMasteryPoint(entity);
    }

    private static void strike(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {
        DamageSource source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);
        AttributeInstance attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
        float damage = EnchantmentHelper.modifyDamage(level, owner.getMainHandItem(), target, source, attack == null ? 1.0F : (float) attack.getValue());
        target.invulnerableTime = 0;
        target.hurt(source, damage);

        if (target.isAlive() && !target.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
            Changeable<Vec3> motion = Changeable.of(getPullVelocity(owner, target));
            if (!TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, owner, instance, motion).isFalse()) {
                if (target.isPassenger()) {
                    target.stopRiding();
                }
                target.setDeltaMovement(motion.get());
                target.setOnGround(false);
                target.hasImpulse = true;
                target.hurtMarked = true;
                target.resetFallDistance();
            }
        }

        Vec3 from = owner.getBoundingBox().getCenter();
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 path = center.subtract(from);
        for (double along = 0.0D; along < 1.0D; along += 0.75D / Math.max(1.0D, path.length())) {
            Vec3 spot = from.add(path.scale(along));
            level.sendParticles(ECHO, spot.x, spot.y, spot.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }

        Vec3 normal = path.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : path.normalize().reverse();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, center.x, center.y, center.z, 0, normal.x * 0.6D, normal.y * 0.6D, normal.z * 0.6D, 1.0D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.2D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.8F);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static Vec3 getPullVelocity(LivingEntity owner, LivingEntity target) {
        Vec3 to = owner.position().subtract(target.position());
        double distance = to.horizontalDistance();
        double room = distance - CONFIG.pullStopDistance - (owner.getBbWidth() + target.getBbWidth()) * 0.5D;
        double travel = Math.max(0.0D, Math.min(distance * CONFIG.pullShare, room));
        if (travel < 0.5D || distance < 1.0E-3D) {
            return new Vec3(0.0D, 0.2D, 0.0D);
        }

        int ticks = Mth.clamp((int) Math.round(6.0D + travel * 0.5D), 6, 24);
        double horizontal = travel * (1.0D - AIR_DRAG) / (1.0D - Math.pow(AIR_DRAG, ticks));

        double perSpeed = 0.0D;
        double still = 0.0D;
        double unit = 1.0D;
        double zero = 0.0D;
        for (int tick = 0; tick < ticks; tick++) {
            perSpeed += unit;
            still += zero;
            unit *= FALL_DRAG;
            zero = (zero - GRAVITY) * FALL_DRAG;
        }
        double rise = to.y * travel / distance;
        double vertical = (rise - still) / perSpeed;
        return new Vec3(to.x / distance * horizontal, vertical, to.z / distance * horizontal);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!IMPACTS.isEmpty()) {
            IMPACTS.removeIf(TwinImpactQuirk::tickImpact);
        }

        if (!ECHOES.isEmpty()) {
            ECHOES.removeIf(TwinImpactQuirk::tickEcho);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        IMPACTS.clear();
        ECHOES.clear();
    }

}

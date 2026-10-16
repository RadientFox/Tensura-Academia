package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.StormBoltEntity;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import io.github.manasmods.tensura.entity.magic.beam.SolarBeamProjectile;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderStrikes {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    public static final int THUNDER_GRAB = 4;
    public static final int THUNDER_THROW = 14;
    private static final int THUNDER_HIT = 17;
    public static final int THUNDER_END = 22;
    public static final int SOLAR_CHARGE = 20;
    private static final int BEAM_FADE = 20;
    private static final int ARC_TICKS = 8;
    private static final double ARC_RANGE = 96.0D;

    private NewOrderStrikes() {
    }

    private static final class Cast {
        private final int action;
        private final ManasSkillInstance instance;
        private final LivingEntity user;
        private final @Nullable LivingEntity target;
        private final long start;
        private @Nullable BeamProjectile beam;

        private Cast(int action, ManasSkillInstance instance, LivingEntity user, @Nullable LivingEntity target, long start) {
            this.action = action;
            this.instance = instance;
            this.user = user;
            this.target = target;
            this.start = start;
        }
    }

    private static final List<Cast> CASTS = new ArrayList<>();

    static boolean isCasting(LivingEntity entity) {
        return CASTS.stream().anyMatch(cast -> cast.user == entity);
    }

    // The construct/copy has to be behind the user
    // Cant be using body of air
    private static boolean canUseCopy(LivingEntity entity) {
        if (!NewOrderRules.hasRule(entity, NewOrderRule.HARDEN)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_harden"));
            return false;
        }
        if (NewOrderBody.isInside(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.inside_body"));
            return false;
        }
        if (isCasting(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.copy_busy"));
            return false;
        }
        return true;
    }

    private static void start(Cast cast, int charge, int ticks) {
        CASTS.add(cast);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(cast.user, new NewOrderPayloads.CopyActionPayload(cast.user.getId(), cast.action, charge, ticks));
    }

    private static void arc(ServerLevel level, Vec3 from, Vec3 to) {
        NewOrderPayloads.ArcPayload arc = new NewOrderPayloads.ArcPayload(from, to, ARC_TICKS);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(from) <= ARC_RANGE * ARC_RANGE || player.distanceToSqr(to) <= ARC_RANGE * ARC_RANGE) {
                PacketDistributor.sendToPlayer(player, arc);
            }
        }
    }

//    private static boolean hasWeatherManipulation(LivingEntity entity) {
//        return SkillAPI.getSkillsFrom(entity).getSkill(ExtraSkills.WEATHER_MANIPULATION.get()).filter(instance -> instance.getMastery() >= 0.0D).isPresent();
//    }

    private static boolean canThunder(LivingEntity entity){
        return
                hasLearned(entity,
                        ExtraSkills.WEATHER_MANIPULATION.get()) || hasLearned(entity, ExtraSkills.WEATHER_DOMINATION.get());
    }

    private static boolean hasLearned(LivingEntity entity, ManasSkill skill)
    {
        return SkillAPI.getSkillsFrom(entity).getSkill(skill).filter(instance -> instance.getMastery() >= 0.0D).isPresent();
    }


    static void thunderStrike(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!canUseCopy(entity)) {
            return;
        }
        if (!level.isThundering() && !canThunder(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_storm"));
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.thunderRange, false, true);
        if (target == null || target == entity) {
            NewOrderUtil.fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.THUNDER_STRIKE)) {
            return;
        }

        instance.setCoolDown(CONFIG.thunderCooldown, NewOrderQuirk.THUNDER_STRIKE);
        instance.addMasteryPoint(entity);
        start(new Cast(NewOrderCopy.ACTION_THUNDER, instance, entity, target, level.getGameTime()), THUNDER_THROW, THUNDER_END);
        NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.CAST_LIGHTNING.get(), 1.0F, 0.8F);
    }

    // true once it's over
    private static boolean tickThunder(ServerLevel level, Cast cast, long age) {
        LivingEntity user = cast.user;
        if (age == THUNDER_GRAB) {
            Vec3 hand = NewOrderCopy.getRaisedHand(user);
            StormBoltEntity bolt = StormBoltEntity.TYPE.get().create(level);
            if (bolt != null) {
                bolt.moveTo(hand);
                level.addFreshEntity(bolt);
            }
            NewOrderUtil.playSound(level, hand, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.9F);
            NewOrderUtil.playSound(level, hand, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F, 1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 30, 0.4D, 0.4D, 0.4D, 0.3D);
            level.sendParticles(ParticleTypes.FLASH, hand.x, hand.y, hand.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        } else if (age > THUNDER_GRAB && age < THUNDER_HIT) {
            Vec3 hand = age < THUNDER_THROW || cast.target == null ? NewOrderCopy.getRaisedHand(user) : NewOrderCopy.getReachingHand(user, cast.target.getBoundingBox().getCenter());
            if (age % 2L == 0L) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 6, 0.35D, 0.35D, 0.35D, 0.12D);
            }
            if (age % 5L == 0L) {
                NewOrderUtil.playSound(level, hand, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.35F, 1.9F);
            }
        } else if (age == THUNDER_HIT) {
            hurl(level, cast);
        }
        return age >= THUNDER_END;
    }

    private static void hurl(ServerLevel level, Cast cast) {
        LivingEntity user = cast.user;
        LivingEntity target = cast.target;
        if (target == null || !target.isAlive() || target.level() != level || target.distanceTo(user) > CONFIG.thunderRange * 1.5D) {
            Vec3 hand = NewOrderCopy.getRaisedHand(user);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 20, 0.6D, 0.6D, 0.6D, 0.2D);
            NewOrderUtil.playSound(level, hand, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.6F);
            return;
        }

        zap(level, cast, NewOrderCopy.getReachingHand(user, target.getBoundingBox().getCenter()), target, NewOrderUtil.ruleDamage(user, CONFIG.thunderDamage));
    }

    private static void zap(ServerLevel level, Cast cast, Vec3 from, LivingEntity target, float damage) {
        Vec3 to = target.getBoundingBox().getCenter();
        arc(level, from, to);
        DamageSource source = ((Skill) cast.instance.getSkill()).createSource(cast.instance, cast.user, TensuraDamageTypes.LIGHTNING_ELEMENTAL, NewOrderQuirk.THUNDER_STRIKE);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        level.sendParticles(ParticleTypes.FLASH, to.x, to.y, to.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 40, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.5D);
        NewOrderUtil.playSound(level, to, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.5F, 1.3F);
        NewOrderUtil.playSound(level, to, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 1.0F);
    }

    static void solarBeam(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!canUseCopy(entity)) {
            return;
        }
        if (!level.isDay()) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_sun"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.SOLAR_BEAM)) {
            return;
        }

        instance.setCoolDown(CONFIG.solarCooldown, NewOrderQuirk.SOLAR_BEAM);
        instance.addMasteryPoint(entity);
        start(new Cast(NewOrderCopy.ACTION_SOLAR, instance, entity, null, level.getGameTime()), SOLAR_CHARGE, getSolarTicks());
        NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.CAST_LIGHT.get(), 1.0F, 0.8F);
        NewOrderUtil.playSound(level, NewOrderCopy.getGather(entity), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
    }

    private static int getSolarTicks() {
        return SOLAR_CHARGE + CONFIG.solarBeamSeconds * 20 + BEAM_FADE;
    }

    // Sun angle
    private static Vec3 getSunDirection(ServerLevel level) {
        float angle = level.getSunAngle(1.0F);
        return new Vec3(-Mth.sin(angle), Mth.cos(angle), 0.0D);
    }

    private static boolean tickSolar(ServerLevel level, Cast cast, long age) {
        LivingEntity user = cast.user;
        if (age < SOLAR_CHARGE) {
            Vec3 gather = NewOrderCopy.getGather(user);
            Vec3 sun = getSunDirection(level);
            for (int i = 0; i < 6; i++) {
                Vec3 from = gather.add(sun.scale(5.0D + level.random.nextDouble() * 5.0D)).add((level.random.nextDouble() - 0.5D) * 2.0D, (level.random.nextDouble() - 0.5D) * 2.0D, (level.random.nextDouble() - 0.5D) * 2.0D);
                Vec3 pull = gather.subtract(from).scale(0.1D);
                level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, pull.x, pull.y, pull.z, 1.0D);
            }
            if (age % 4L == 0L) {
                level.sendParticles((ParticleOptions) TensuraParticleTypes.SOLAR_FLASH.get(), gather.x, gather.y, gather.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            return false;
        }

        if (age == SOLAR_CHARGE) {
            fireBeam(level, cast);
        }

        BeamProjectile beam = cast.beam;
        if (beam == null || beam.isRemoved()) {
            return true;
        }

        aimBeam(beam, user);
        if (age >= getSolarTicks()) {
            beam.discard();
            return true;
        }
        return false;
    }

    private static void fireBeam(ServerLevel level, Cast cast) {
        LivingEntity user = cast.user;
        SolarBeamProjectile beam = new SolarBeamProjectile(level, user);
        beam.setFollowingOwner(false);
        beam.setLife(CONFIG.solarBeamSeconds * 20 + BEAM_FADE);
        beam.setDamage(NewOrderUtil.ruleDamage(user, CONFIG.solarBeamDamage));
        beam.setRange((float) CONFIG.solarBeamRange);
        beam.setSkill(cast.instance);
        beam.setMode(NewOrderQuirk.SOLAR_BEAM);
        aimBeam(beam, user);
        level.addFreshEntity(beam);
        cast.beam = beam;

        Vec3 gather = NewOrderCopy.getGather(user);
        level.sendParticles(ParticleTypes.FLASH, gather.x, gather.y, gather.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        NewOrderUtil.playSound(level, user.position(), TensuraSoundEvents.CAST_LIGHT.get(), 1.2F, 0.5F);
        NewOrderUtil.playSound(level, beam.position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.7F);
    }

    private static void aimBeam(BeamProjectile beam, LivingEntity user) {
        Vec3 origin = NewOrderCopy.getBeamOrigin(user);
        Vec3 aim = getAim(user);
        Vec3 direction = aim.subtract(origin);
        double flat = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double yaw = Math.atan2(direction.z, direction.x);
        double pitch = Math.atan2(direction.y, flat);
        beam.setPos(origin.x, origin.y - beam.getBbHeight() / 2.0F, origin.z);
        beam.setYaw((float) yaw);
        beam.setPitch((float) pitch);
        beam.setYRot((float) (yaw * Mth.RAD_TO_DEG) - 90.0F);
        beam.setXRot((float) (-pitch * Mth.RAD_TO_DEG));
    }

    private static Vec3 getAim(LivingEntity user) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(user, CONFIG.solarBeamRange, false, true);
        if (target != null && target != user) {
            return target.getBoundingBox().getCenter();
        }

        Vec3 eye = user.getEyePosition();
        Vec3 end = eye.add(user.getViewVector(1.0F).scale(CONFIG.solarBeamRange));
        BlockHitResult hit = user.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // always true so it can sit in removeIf
    private static boolean endCast(Cast cast) {
        if (cast.beam != null && !cast.beam.isRemoved()) {
            cast.beam.discard();
        }
        return true;
    }

    private static boolean tickCast(Cast cast, long time) {
        LivingEntity user = cast.user;
        if (!(user.level() instanceof ServerLevel level) || user.isRemoved() || !user.isAlive() || !NewOrderRules.hasRule(user, NewOrderRule.HARDEN) || NewOrderBody.isInside(user)) {
            return endCast(cast);
        }

        long age = time - cast.start;
        boolean done = cast.action == NewOrderCopy.ACTION_THUNDER ? tickThunder(level, cast, age) : tickSolar(level, cast, age);
        return done && endCast(cast);
    }

    static void forget(LivingEntity entity) {
        CASTS.removeIf(cast -> cast.user == entity && endCast(cast));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CASTS.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            List.copyOf(CASTS).stream().filter(cast -> tickCast(cast, time)).forEach(CASTS::remove);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CASTS.forEach(NewOrderStrikes::endCast);
        CASTS.clear();
    }

}

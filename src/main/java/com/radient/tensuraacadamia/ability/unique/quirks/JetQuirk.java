package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.mixin.ServerGamePacketListenerAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class JetQuirk extends Skill {

    private static final QuirkSkillsConfig.Jet CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Jet;

    private static final int JET_BOOST = 0;
    private static final int JET_KICK = 1;
    private static final int JET_PUSH = 2;
    private static final int JET_COUNTER = 3;
    private static final int JET_DODGE = 4;
    private static final int MODES = 5;

    public static final String KICK_TAG = "jetKick";
    private static final String CHAIN_TAG = "chain";
    private static final String CHAIN_UNTIL_TAG = "chainUntil";
    private static final String USED_TAG = "boostsUsed";
    private static final String PUSH_TAG = "pushTarget";
    private static final ResourceLocation WIND = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "jet_wind_boost");

    private static final double SURFACE_PROBE = 0.25D;
    private static final double SURFACE_AXIS = 0.15D;
    private static final double KICK_REACH = 1.0D;
    private static final int RUSH_TICKS = 40;
    private static final double RISE_SPEED = 1.2D;
    private static final int PUSH_HIT_TICKS = 10;
    private static final int MAX_PUSH_BLOCKS = 64;
    private static final int BOOST_TIMEOUT = 60;
    private static final double BOOST_END_SPEED = 0.25D;
    private static final double AIR_DRAG = 0.91D;
    private static final int MAX_CHAIN = 100;
    private static final double SLOW_FALL_GRACE = 0.05D;

    private enum Kind { DODGE, KICK, PUSH, RISE, DIVE }

    private static final class Flight {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private Kind kind;
        private long start;
        private int ticks;
        private Vec3 direction;
        private double speed;
        private @Nullable LivingEntity target;
        private double hardness;
        private long lastCrash = Long.MIN_VALUE / 2L;

        private Flight(ManasSkillInstance instance, LivingEntity owner, Kind kind, long start, int ticks, Vec3 direction, double speed) {
            this.instance = instance;
            this.owner = owner;
            this.kind = kind;
            this.start = start;
            this.ticks = ticks;
            this.direction = direction;
            this.speed = speed;
        }
    }

    // A push like tail leap, followed until it runs out or hits something
    private static final class Boost {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final long start;
        private final Vec3 direction;
        private Vec3 motion;
        private Vec3 lastPos;
        private boolean airborne;

        private Boost(ManasSkillInstance instance, LivingEntity owner, long start, Vec3 motion) {
            this.instance = instance;
            this.owner = owner;
            this.start = start;
            this.direction = motion.normalize();
            this.motion = motion;
            this.lastPos = owner.position();
        }
    }

    private static final List<Flight> FLIGHTS = new ArrayList<>();
    private static final List<Boost> BOOSTS = new ArrayList<>();
    private static final Map<LivingEntity, Long> COUNTERS = new HashMap<>();
    private static final Set<LivingEntity> COUNTER_FALLS = new HashSet<>();
    private static final Map<UUID, Double> LAST_Y = new HashMap<>();
    private static final Set<UUID> SLOW_FALLING = new HashSet<>();

    private static @Nullable SlowFallPayload clientSlowFall;
    private static boolean clientSlowFallRefreshed;

    public JetQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case JET_BOOST -> CONFIG.boostAuraCost;
            case JET_KICK -> CONFIG.kickAuraCost;
            case JET_PUSH -> CONFIG.pushAuraCost;
            case JET_COUNTER -> CONFIG.counterAuraCost;
            case JET_DODGE -> CONFIG.dodgeAuraCost;
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
            case JET_BOOST -> "jet.jet_boost";
            case JET_KICK -> "jet.jet_kick";
            case JET_PUSH -> "jet.jet_push";
            case JET_COUNTER -> "jet.jet_counter";
            case JET_DODGE -> "jet.jet_dodge";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == JET_PUSH && getFlight(entity, Kind.PUSH) != null || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getJet(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.JET.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static long getKickStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, KICK_TAG);
    }

    // Speed in blocks per second the user is propelled at, 0 when not using jet
    public static double getJetSpeed(LivingEntity entity) {
        Flight flight = FLIGHTS.stream().filter(current -> current.owner == entity).findFirst().orElse(null);
        if (flight != null) {
            return flight.speed * 20.0D;
        }

        Boost boost = BOOSTS.stream().filter(current -> current.owner == entity).findFirst().orElse(null);
        return boost == null ? 0.0D : boost.motion.length() * 20.0D;
    }

    public static SlowFallPayload getClientSlowFall() {
        return clientSlowFall != null ? clientSlowFall : SlowFallPayload.fromConfig(false);
    }

    // true once after each jet boost, which gives slow fall its full time back
    public static boolean consumeSlowFallRefresh() {
        boolean refreshed = clientSlowFallRefreshed;
        clientSlowFallRefreshed = false;
        return refreshed;
    }

    public static void setSlowFalling(Player player, boolean falling) {
        if (falling) {
            SLOW_FALLING.add(player.getUUID());
        } else {
            SLOW_FALLING.remove(player.getUUID());
        }
    }

    private static @Nullable Flight getFlight(LivingEntity entity, Kind kind) {
        return FLIGHTS.stream().filter(flight -> flight.owner == entity && flight.kind == kind).findFirst().orElse(null);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case JET_BOOST -> jetBoost(level, instance, entity);
            case JET_KICK -> jetKick(level, instance, entity);
            case JET_PUSH -> jetPush(level, instance, entity);
            case JET_COUNTER -> jetCounter(level, instance, entity);
            case JET_DODGE -> jetDodge(level, instance, entity);
        }
    }

    // Wind Boost

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateWind(entity);
        sendSlowFall(entity, false);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateWind(entity);
        SLOW_FALLING.remove(entity.getUUID());
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateWind(entity);
        if (instance.isToggled()) {
            sendSlowFall(entity, false);
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        AdditiveBoost.apply(entity, TensuraAttributes.WIND_BOOST, WIND, 0.0D);
        List.copyOf(FLIGHTS).stream().filter(flight -> flight.owner == entity).forEach(flight -> land(flight, false));
        FLIGHTS.removeIf(flight -> flight.owner == entity);
        BOOSTS.removeIf(boost -> boost.owner == entity);
        COUNTERS.remove(entity);
        COUNTER_FALLS.remove(entity);
    }

    private static void updateWind(LivingEntity entity) {
        boolean on = getJet(entity).filter(ManasSkillInstance::isToggled).isPresent();
        AdditiveBoost.apply(entity, TensuraAttributes.WIND_BOOST, WIND, on ? CONFIG.windBoost : 0.0D);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            getJet(player).ifPresent(instance -> {
                updateWind(player);
                if (instance.isToggled()) {
                    slowFall(player);
                }
            });
        }
    }

    // Slow Fall, the client slows itself while jump is held and tells the server, which stops the fall damage
    private static void slowFall(ServerPlayer player) {
        double fall = LAST_Y.getOrDefault(player.getUUID(), player.getY()) - player.getY();
        LAST_Y.put(player.getUUID(), player.getY());
        if (player.onGround()) {
            SLOW_FALLING.remove(player.getUUID());
            return;
        }

        if (SLOW_FALLING.contains(player.getUUID()) && fall > 0.0D && fall <= CONFIG.slowFallSpeed / 20.0D + SLOW_FALL_GRACE) {
            player.resetFallDistance();
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1D, player.getZ(), 2, player.getBbWidth() * 0.2D, 0.05D, player.getBbWidth() * 0.2D, 0.01D);
        }
    }

    private static void sendSlowFall(LivingEntity entity, boolean refresh) {
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, SlowFallPayload.fromConfig(refresh));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_Y.remove(event.getEntity().getUUID());
        SLOW_FALLING.remove(event.getEntity().getUUID());
    }

    // Boost
    @SubscribeEvent
    public static void onDealDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        double speed = getJetSpeed(attacker);
        if (speed > 0.0D) {
            event.setAmount((float) (event.getAmount() * (1.0D + speed * CONFIG.boostDamagePerSpeed)));
        }
    }

    // Jet Boost

    private static void jetBoost(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, JET_BOOST)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (level.getGameTime() > tag.getLong(CHAIN_UNTIL_TAG)) {
            tag.remove(CHAIN_TAG);
            tag.remove(USED_TAG);
        }

        // Pushed like tail leap so the user can still steer and land anywhere
        boolean mastered = instance.isMastered(entity);
        double distance = mastered ? CONFIG.boostDistanceMastered : CONFIG.boostDistance;
        Vec3 aim = entity.getLookAngle().add(0.0D, CONFIG.boostLift, 0.0D).normalize();
        double speed = MultiArms.getLaunchVelocity(new Vec3(aim.x, Math.max(aim.y, CONFIG.boostLift), aim.z), distance).length();
        speed = Math.min(CONFIG.maxBoostSpeed / 20.0D, speed + tag.getInt(CHAIN_TAG) * (mastered ? CONFIG.chainSpeedMastered : CONFIG.chainSpeed) / 20.0D);
        Vec3 velocity = aim.scale(speed);

        List.copyOf(FLIGHTS).stream().filter(flight -> flight.owner == entity).forEach(flight -> land(flight, false));
        FLIGHTS.removeIf(flight -> flight.owner == entity);
        BOOSTS.removeIf(boost -> boost.owner == entity);
        BOOSTS.add(new Boost(instance, entity, level.getGameTime(), velocity));
        launch(entity, velocity);

        // Out of uses goes on cooldown
        int used = tag.getInt(USED_TAG) + 1;
        if (used >= CONFIG.boostCharges) {
            used = 0;
            instance.setCoolDown(CONFIG.boostCooldown, JET_BOOST);
        }

        tag.putInt(USED_TAG, used);
        tag.putLong(CHAIN_UNTIL_TAG, level.getGameTime() + CONFIG.chainSeconds * 20L);
        instance.markDirty();
        sendSlowFall(entity, true);

        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 10, 0.3D, 0.05D, 0.3D, 0.05D);
        playSound(level, entity, SoundEvents.BREEZE_JUMP, 1.0F, 1.2F);
        instance.addMasteryPoint(entity);
    }

    // Players move themselves, so they're sent the push
    private static void launch(LivingEntity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new BruiserQuirk.LeapPayload(velocity.toVector3f()));
        } else {
            entity.setOnGround(false);
            entity.hurtMarked = true;
        }
    }

    // true once the boost is over
    private static boolean tickBoost(Boost boost) {
        LivingEntity owner = boost.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - boost.start;
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
            Vec3 moved = player.position().subtract(boost.lastPos);
            if (age > 0L) {
                boost.motion = moved.lengthSqr() > boost.motion.lengthSqr() * AIR_DRAG * AIR_DRAG ? moved : boost.motion.scale(AIR_DRAG);
            }
        } else if (age > 0L) {
            boost.motion = owner.getDeltaMovement();
        }

        boost.lastPos = owner.position();
        if (!owner.onGround() && age > 0L) {
            boost.airborne = true;
        }

        LivingEntity crash = findCrash(level, owner, boost.motion, null);
        if (crash != null) {
            crash(level, boost, crash);
            return true;
        }

        Vec3 wall = age > 0L ? getWall(level, owner, boost.direction, boost.motion) : Vec3.ZERO;
        if (wall.lengthSqr() > 0.0D) {
            pushOff(level, boost, wall);
            return true;
        }

        // Stopping without boosting off a wall loses the built up speed
        if (age > BOOST_TIMEOUT || boost.airborne && owner.onGround() || age > 2L && boost.motion.length() < BOOST_END_SPEED) {
            boost.instance.getOrCreateTag().remove(CHAIN_TAG);
            boost.instance.markDirty();
            return true;
        }

        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.1D, owner.getZ(), 1, owner.getBbWidth() * 0.2D, 0.05D, owner.getBbWidth() * 0.2D, 0.01D);
        }
        return false;
    }

    // Walls and ceilings in the way, as the direction pointing out of them, the floor never counts
    private static Vec3 getWall(ServerLevel level, LivingEntity owner, Vec3 direction, Vec3 motion) {
        AABB box = owner.getBoundingBox();
        double x = Math.abs(direction.x) > SURFACE_AXIS && !level.noCollision(owner, box.move(Math.signum(direction.x) * SURFACE_PROBE, 0.0D, 0.0D)) ? -Math.signum(direction.x) : 0.0D;
        double y = direction.y > SURFACE_AXIS && motion.y > 0.0D && !level.noCollision(owner, box.move(0.0D, SURFACE_PROBE, 0.0D)) ? -1.0D : 0.0D;
        double z = Math.abs(direction.z) > SURFACE_AXIS && !level.noCollision(owner, box.move(0.0D, 0.0D, Math.signum(direction.z) * SURFACE_PROBE)) ? -Math.signum(direction.z) : 0.0D;
        return new Vec3(x, y, z);
    }

    // Boosting off a wall builds up speed and gives every boost back
    private static void pushOff(ServerLevel level, Boost boost, Vec3 normal) {
        LivingEntity owner = boost.owner;
        Vec3 heading = boost.motion.lengthSqr() > 1.0E-4D ? boost.motion.normalize() : boost.direction;
        Vec3 bounce = new Vec3(normal.x != 0.0D ? -heading.x : heading.x, normal.y != 0.0D ? -heading.y : heading.y, normal.z != 0.0D ? -heading.z : heading.z);
        launch(owner, bounce.add(normal.scale(0.5D)).normalize().scale(CONFIG.pushOffSpeed / 20.0D));

        ManasSkillInstance instance = boost.instance;
        CompoundTag tag = instance.getOrCreateTag();
        tag.putInt(CHAIN_TAG, Math.min(MAX_CHAIN, tag.getInt(CHAIN_TAG) + 1));
        tag.putLong(CHAIN_UNTIL_TAG, level.getGameTime() + CONFIG.chainSeconds * 20L);
        tag.putInt(USED_TAG, 0);
        instance.markDirty();
        instance.setCoolDown(0, JET_BOOST);

        Vec3 spot = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D).subtract(normal.scale(owner.getBbWidth() * 0.5D));
        level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y, spot.z, 12, 0.3D, 0.3D, 0.3D, 0.08D);
        level.sendParticles(ParticleTypes.POOF, spot.x, spot.y, spot.z, 6, 0.2D, 0.2D, 0.2D, 0.05D);
        playSound(level, owner, SoundEvents.WIND_CHARGE_BURST.value(), 0.6F, 1.4F);
    }

    // Jet Kick

    private static void jetKick(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.kickRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, JET_KICK)) {
            return;
        }

        Flight flight = new Flight(instance, entity, Kind.KICK, level.getGameTime(), RUSH_TICKS, toward(entity, target), CONFIG.rushSpeed / 20.0D);
        flight.target = target;
        fly(flight);
        playSound(level, entity, SoundEvents.BREEZE_SHOOT, 1.0F, 1.1F);
        instance.setCoolDown(CONFIG.kickCooldown, JET_KICK);
        instance.addMasteryPoint(entity);
    }

    private static void kick(ServerLevel level, Flight flight, LivingEntity target) {
        LivingEntity owner = flight.owner;
        boolean mastered = flight.instance.isMastered(owner);
        float damage = (float) ((mastered ? CONFIG.kickDamageMastered : CONFIG.kickDamage) + getMeleeDamage(owner) * (mastered ? CONFIG.kickDamagePercentMastered : CONFIG.kickDamagePercent));
        hit(flight.instance, owner, target, damage, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, JET_KICK);

        Vec3 away = new Vec3(flight.direction.x, 0.0D, flight.direction.z);
        away = away.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, owner.getYRot()) : away.normalize();
        target.setDeltaMovement(MultiArms.getLaunchVelocity(away.add(0.0D, 0.3D, 0.0D), CONFIG.kickKnockback));
        target.hurtMarked = true;
        shockwave(level, owner, target, CONFIG.kickShockwaveRadius);
        markKick(level, flight.instance);
        owner.setDeltaMovement(flight.direction.scale(-0.2D));
        owner.hurtMarked = true;
    }

    // Jet Push

    private static void jetPush(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Flight pushing = getFlight(entity, Kind.PUSH);
        if (pushing != null) {
            FLIGHTS.remove(pushing);
            land(pushing, false);
            return;
        }

        LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.pushRange + entity.getBbWidth());
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (!MultiArms.canGrab(target)) {
            fail(entity, Component.translatable("tracadamia.skill.arms.boss"));
            return;
        }

        if (target.getBbHeight() > entity.getBbHeight() * CONFIG.pushMaxSize || target.getBbWidth() > entity.getBbWidth() * CONFIG.pushMaxSize) {
            fail(entity, Component.translatable("tracadamia.skill.jet.too_big"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, JET_PUSH)) {
            return;
        }

        target.stopRiding();
        instance.getOrCreateTag().putUUID(PUSH_TAG, target.getUUID());
        instance.markDirty();
        Flight flight = new Flight(instance, entity, Kind.PUSH, level.getGameTime(), CONFIG.pushSeconds * 20, getPushDirection(entity), CONFIG.pushSpeed / 20.0D);
        flight.target = target;
        fly(flight);
        MultiArms.holdAt(entity, target, getPushOffset(entity, target), MultiArms.HOLD_BODY, true);
        playSound(level, entity, SoundEvents.BREEZE_SHOOT, 1.2F, 0.8F);
        instance.addMasteryPoint(entity);
    }

    // Out in front at the user's feet so it never starts in the floor
    private static Vec3 getPushOffset(LivingEntity owner, LivingEntity target) {
        return new Vec3(0.0D, 0.0D, owner.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.3D);
    }

    // Where the user looks, kept off the floor while on the ground
    private static Vec3 getPushDirection(LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        if (look.y >= 0.0D || owner.level().noCollision(owner, owner.getBoundingBox().move(0.0D, -SURFACE_PROBE, 0.0D))) {
            return look;
        }

        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        return flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, owner.getYRot()) : flat.normalize();
    }

    // true once the push crashes into something it can't break
    private static boolean plow(ServerLevel level, Flight flight, LivingEntity target, long age) {
        LivingEntity owner = flight.owner;
        Vec3 step = flight.direction.scale(flight.speed);
        AABB ahead = target.getBoundingBox().move(step).minmax(owner.getBoundingBox().move(step));
        ahead = ahead.setMinY(ahead.minY + 0.01D);
        List<BlockPos> breaking = new ArrayList<>();
        boolean stuck = false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(ahead.minX, ahead.minY, ahead.minZ), BlockPos.containing(ahead.maxX - 1.0E-4D, ahead.maxY - 1.0E-4D, ahead.maxZ - 1.0E-4D))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
                continue;
            }

            if (breaking.size() >= MAX_PUSH_BLOCKS || state.getDestroySpeed(level, pos) > CONFIG.pushMaxHardness || !GroundBlocks.canBreak(level, flight.instance, owner, pos)) {
                stuck = true;
                break;
            }

            breaking.add(pos.immutable());
        }

        for (BlockPos pos : breaking) {
            flight.hardness += Math.max(0.0F, level.getBlockState(pos).getDestroySpeed(level, pos));
            level.destroyBlock(pos, false, owner);
            GroundBlocks.griefed(level, flight.instance, owner, pos);
        }

        if (stuck || !breaking.isEmpty() && age - flight.lastCrash >= PUSH_HIT_TICKS) {
            boolean mastered = flight.instance.isMastered(owner);
            float damage = (float) ((mastered ? CONFIG.pushDamageMastered : CONFIG.pushDamage) + flight.hardness * CONFIG.pushHardnessDamage);
            hit(flight.instance, owner, target, damage, DamageTypes.FLY_INTO_WALL, JET_PUSH);
            flight.hardness = 0.0D;
            flight.lastCrash = age;
            Vec3 point = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            playSound(level, target, SoundEvents.GENERIC_BIG_FALL, 1.2F, 0.6F);
        }

        return stuck;
    }

    // Jet Counter

    private static void jetCounter(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (COUNTERS.containsKey(entity) || EnergyHelper.isOutOfEnergy(entity, instance, JET_COUNTER)) {
            return;
        }

        double seconds = instance.isMastered(entity) ? CONFIG.counterSecondsMastered : CONFIG.counterSeconds;
        COUNTERS.put(entity, level.getGameTime() + Math.round(seconds * 20.0D));
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 10, 0.3D, 0.05D, 0.3D, 0.02D);
        playSound(level, entity, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 1.4F);
        instance.addMasteryPoint(entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCountered(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (entity.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (isCountering(entity) || source.is(DamageTypeTags.IS_FALL) && COUNTER_FALLS.contains(entity)) {
            event.setCanceled(true);
            return;
        }

        Optional<ManasSkillInstance> jet = getJet(entity);
        if (!COUNTERS.containsKey(entity) || jet.isEmpty() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        event.setCanceled(true);
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == entity) {
            return;
        }

        COUNTERS.remove(entity);
        ManasSkillInstance instance = jet.get();
        Flight flight = new Flight(instance, entity, Kind.RISE, level.getGameTime(), Mth.ceil(CONFIG.counterHeight / RISE_SPEED), new Vec3(0.0D, 1.0D, 0.0D), RISE_SPEED);
        flight.target = attacker;
        fly(flight);
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(), 16, 0.4D, 0.1D, 0.4D, 0.1D);
        playSound(level, entity, SoundEvents.BREEZE_JUMP, 1.2F, 0.9F);
        instance.setCoolDown(CONFIG.counterCooldown, JET_COUNTER);
    }

    private static boolean isCountering(LivingEntity entity) {
        return FLIGHTS.stream().anyMatch(flight -> flight.owner == entity && isCounter(flight));
    }

    private static boolean isCounter(Flight flight) {
        return flight.kind == Kind.RISE || flight.kind == Kind.DIVE;
    }

    private static void counterKick(ServerLevel level, Flight flight, LivingEntity target) {
        LivingEntity owner = flight.owner;
        COUNTER_FALLS.add(owner);
        boolean mastered = flight.instance.isMastered(owner);
        float damage = (float) ((mastered ? CONFIG.counterDamageMastered : CONFIG.counterDamage) + getMeleeDamage(owner) * CONFIG.counterDamagePercent);
        hit(flight.instance, owner, target, damage, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, JET_COUNTER);
        StrongarmQuirk.stun(target, CONFIG.counterStunSeconds * 20);
        shockwave(level, owner, target, 2.0D);
        markKick(level, flight.instance);
        owner.setDeltaMovement(0.0D, 0.4D, 0.0D);
        owner.hurtMarked = true;
    }

    // Jet Dodge

    private static void jetDodge(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, JET_DODGE)) {
            return;
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double speed = CONFIG.dodgeSpeed / 20.0D;
        double distance = instance.isMastered(entity) ? CONFIG.dodgeDistanceMastered : CONFIG.dodgeDistance;
        fly(new Flight(instance, entity, Kind.DODGE, level.getGameTime(), Mth.ceil(distance / speed), entity.isShiftKeyDown() ? right : right.reverse(), speed));
        level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 8, 0.2D, 0.05D, 0.2D, 0.05D);
        playSound(level, entity, SoundEvents.BREEZE_JUMP, 0.7F, 1.6F);
        instance.setCoolDown(CONFIG.dodgeCooldown, JET_DODGE);
        instance.addMasteryPoint(entity);
    }

    // Flights

    private static void fly(Flight flight) {
        List.copyOf(FLIGHTS).stream().filter(other -> other.owner == flight.owner).forEach(other -> land(other, false));
        FLIGHTS.removeIf(other -> other.owner == flight.owner);
        FLIGHTS.add(flight);
        flight.owner.resetFallDistance();
    }

    private static Vec3 toward(LivingEntity owner, LivingEntity target) {
        Vec3 offset = target.getBoundingBox().getCenter().subtract(owner.getBoundingBox().getCenter());
        return offset.lengthSqr() < 1.0E-4D ? owner.getLookAngle() : offset.normalize();
    }

    // Space left between the user and the target
    private static double getGap(LivingEntity owner, LivingEntity target) {
        return owner.getBoundingBox().getCenter().distanceTo(target.getBoundingBox().getCenter()) - (owner.getBbWidth() + target.getBbWidth()) * 0.5D;
    }

    private static boolean isTouching(LivingEntity owner, LivingEntity target) {
        return owner.getBoundingBox().inflate(KICK_REACH).intersects(target.getBoundingBox());
    }

    // Blocked sides, as the direction pointing out of whatever was hit
    private static Vec3 getSurface(ServerLevel level, Flight flight) {
        LivingEntity owner = flight.owner;
        AABB box = owner.getBoundingBox();
        Vec3 direction = flight.direction;
        double x = Math.abs(direction.x) > SURFACE_AXIS && !level.noCollision(owner, box.move(Math.signum(direction.x) * SURFACE_PROBE, 0.0D, 0.0D)) ? -Math.signum(direction.x) : 0.0D;
        double y = Math.abs(direction.y) > SURFACE_AXIS && !level.noCollision(owner, box.move(0.0D, Math.signum(direction.y) * SURFACE_PROBE, 0.0D)) ? -Math.signum(direction.y) : 0.0D;
        double z = Math.abs(direction.z) > SURFACE_AXIS && !level.noCollision(owner, box.move(0.0D, 0.0D, Math.signum(direction.z) * SURFACE_PROBE)) ? -Math.signum(direction.z) : 0.0D;
        return new Vec3(x, y, z);
    }

    private static @Nullable LivingEntity findCrash(ServerLevel level, LivingEntity owner, Vec3 motion, @Nullable LivingEntity held) {
        AABB sweep = owner.getBoundingBox().inflate(0.2D).expandTowards(motion);
        return level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != owner && target != held && target.isAlive() && !target.isSpectator() && !MultiArms.isHoldPair(owner, target))
                .stream().min(Comparator.comparingDouble(target -> target.distanceToSqr(owner))).orElse(null);
    }

    // true once the flight is over
    private static boolean tickFlight(Flight flight) {
        LivingEntity owner = flight.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            land(flight, false);
            return true;
        }

        long age = level.getGameTime() - flight.start;
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
        }

        LivingEntity target = flight.target;
        double speed = flight.speed;
        switch (flight.kind) {
            case DODGE -> {
                Vec3 surface = age > 0L ? getSurface(level, flight) : Vec3.ZERO;
                if (surface.lengthSqr() > 0.0D) {
                    dodgeBounce(level, flight, surface);
                    return true;
                }

                if (age >= flight.ticks) {
                    land(flight, true);
                    return true;
                }
            }
            case KICK, DIVE -> {
                if (target == null || !target.isAlive() || target.level() != level || age >= RUSH_TICKS) {
                    land(flight, false);
                    return true;
                }

                flight.direction = toward(owner, target);
                if (isTouching(owner, target)) {
                    if (flight.kind == Kind.KICK) {
                        kick(level, flight, target);
                    } else {
                        counterKick(level, flight, target);
                    }
                    return true;
                }

                // Slows down so the user doesn't fly past the target
                speed = Math.min(speed, Math.max(0.05D, getGap(owner, target)));
            }
            case PUSH -> {
                LivingEntity held = MultiArms.getHeldTarget(flight.instance, owner, PUSH_TAG);
                if (held == null || age >= flight.ticks) {
                    land(flight, false);
                    return true;
                }

                flight.direction = getPushDirection(owner);
                MultiArms.holdAt(owner, held, getPushOffset(owner, held), MultiArms.HOLD_BODY, true);
                if (plow(level, flight, held, age)) {
                    land(flight, false);
                    return true;
                }

                if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
                    flight.instance.addMasteryPoint(owner);
                }
            }
            case RISE -> {
                boolean ceiling = age > 0L && getSurface(level, flight).y < 0.0D;
                if (age >= flight.ticks || ceiling) {
                    flight.kind = Kind.DIVE;
                    flight.start = level.getGameTime();
                    flight.speed = CONFIG.rushSpeed * 1.5D / 20.0D;
                    if (target != null) {
                        flight.direction = toward(owner, target);
                    }
                }
            }
        }

        owner.setDeltaMovement(flight.direction.scale(speed));
        owner.hurtMarked = true;
        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.1D, owner.getZ(), 2, owner.getBbWidth() * 0.2D, 0.05D, owner.getBbWidth() * 0.2D, 0.01D);
        }
        return false;
    }

    // Bounces up and away from whatever the dodge ran into
    private static void dodgeBounce(ServerLevel level, Flight flight, Vec3 normal) {
        LivingEntity owner = flight.owner;
        Vec3 away = new Vec3(normal.x, 0.0D, normal.z).normalize();
        launch(owner, away.scale(CONFIG.dodgeBounceSpeed / 20.0D).add(0.0D, CONFIG.dodgeBounceUp / 20.0D, 0.0D));
        Vec3 spot = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D).subtract(away.scale(owner.getBbWidth() * 0.5D));
        level.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y, spot.z, 10, 0.2D, 0.3D, 0.2D, 0.05D);
        playSound(level, owner, SoundEvents.WIND_CHARGE_BURST.value(), 0.5F, 1.6F);
    }

    private static void crash(ServerLevel level, Boost boost, LivingEntity target) {
        LivingEntity owner = boost.owner;
        boolean mastered = boost.instance.isMastered(owner);
        float damage = (float) ((mastered ? CONFIG.boostDamageMastered : CONFIG.boostDamage) + getMeleeDamage(owner) * CONFIG.boostDamagePercent);
        hit(boost.instance, owner, target, damage, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, JET_BOOST);
        Vec3 heading = boost.motion.lengthSqr() > 1.0E-4D ? boost.motion.normalize() : boost.direction;
        target.push(heading.x * 0.6D, 0.2D, heading.z * 0.6D);
        target.hurtMarked = true;
        boost.instance.getOrCreateTag().remove(CHAIN_TAG);
        boost.instance.markDirty();
        launch(owner, heading.scale(0.15D));
        Vec3 point = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 12, 0.3D, 0.3D, 0.3D, 0.3D);
        playSound(level, target, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.7F);
    }

    // Ends a flight early or on time
    private static void land(Flight flight, boolean finished) {
        LivingEntity owner = flight.owner;
        if (flight.kind == Kind.PUSH) {
            LivingEntity held = MultiArms.getHeldTarget(flight.instance, owner, PUSH_TAG);
            MultiArms.clearHeld(flight.instance, owner, PUSH_TAG);
            if (held != null) {
                held.setDeltaMovement(flight.direction.scale(flight.speed * 0.5D));
                held.hurtMarked = true;
            }

            flight.instance.setCoolDown(CONFIG.pushCooldown, JET_PUSH);
        } else if (isCounter(flight)) {
            COUNTER_FALLS.add(owner);
        }

        if (owner.isAlive()) {
            owner.setDeltaMovement(flight.direction.scale(Math.min(flight.speed, 0.4D)));
            owner.hurtMarked = true;
            owner.resetFallDistance();
        }
    }

    private static void shockwave(ServerLevel level, LivingEntity owner, LivingEntity target, double radius) {
        Vec3 point = target.getBoundingBox().getCenter();
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
                other -> other != owner && other != target && other.isAlive() && !other.isSpectator() && other.distanceToSqr(point) <= radius * radius)) {
            Vec3 away = other.position().subtract(point);
            other.knockback(0.8D, -away.x, -away.z);
            other.hurtMarked = true;
        }

        Vec3 look = owner.getLookAngle();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y, point.z, 0, look.x, look.y, look.z, Math.max(1.0D, radius / 3.0D));
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, target, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.6F);
        playSound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
    }

    private static void markKick(ServerLevel level, ManasSkillInstance instance) {
        instance.getOrCreateTag().putLong(KICK_TAG, level.getGameTime());
        instance.markDirty();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FLIGHTS.isEmpty()) {
            FLIGHTS.removeIf(JetQuirk::tickFlight);
        }

        if (!BOOSTS.isEmpty()) {
            BOOSTS.removeIf(JetQuirk::tickBoost);
        }

        if (!COUNTERS.isEmpty()) {
            COUNTERS.entrySet().removeIf(entry -> {
                LivingEntity owner = entry.getKey();
                if (owner.isAlive() && !owner.isRemoved() && owner.level().getGameTime() <= entry.getValue()) {
                    return false;
                }

                getJet(owner).ifPresent(instance -> instance.setCoolDown(CONFIG.counterCooldown, JET_COUNTER));
                return true;
            });
        }

        if (!COUNTER_FALLS.isEmpty()) {
            COUNTER_FALLS.removeIf(entity -> !entity.isAlive() || entity.isRemoved() || entity.onGround() || entity.isInLiquid() || entity.isPassenger() || entity.isFallFlying() || entity instanceof Player player && player.getAbilities().flying);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FLIGHTS.clear();
        BOOSTS.clear();
        COUNTERS.clear();
        COUNTER_FALLS.clear();
        LAST_Y.clear();
        SLOW_FALLING.clear();
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, ResourceKey<DamageType> type, int mode) {
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, type, mode), damage);
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    public record SlowFallPayload(float speed, int ticks, boolean refresh) implements CustomPacketPayload {
        public static final Type<SlowFallPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "jet_slow_fall"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlowFallPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, SlowFallPayload::speed,
                ByteBufCodecs.VAR_INT, SlowFallPayload::ticks,
                ByteBufCodecs.BOOL, SlowFallPayload::refresh,
                SlowFallPayload::new
        );

        public static SlowFallPayload fromConfig(boolean refresh) {
            return new SlowFallPayload((float) (CONFIG.slowFallSpeed / 20.0D), (int) Math.round(CONFIG.slowFallSeconds * 20.0D), refresh);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SlowFallingPayload(boolean falling) implements CustomPacketPayload {
        public static final Type<SlowFallingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "jet_slow_falling"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlowFallingPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SlowFallingPayload::falling,
                SlowFallingPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(SlowFallPayload.TYPE, SlowFallPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            clientSlowFall = payload;
            clientSlowFallRefreshed |= payload.refresh();
        }));
        event.registrar("1").playToServer(SlowFallingPayload.TYPE, SlowFallingPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> setSlowFalling(context.player(), payload.falling())));
    }

}

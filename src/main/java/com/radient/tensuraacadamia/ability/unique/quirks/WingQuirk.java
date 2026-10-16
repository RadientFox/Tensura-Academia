package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSelf;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.mixin.ServerGamePacketListenerAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WingQuirk extends Skill {

    private static final QuirkSkillsConfig.Wing CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Wing;

    private static final int BOOST = 0;
    private static final int DIVE = 1;
    private static final int AIRBORNE = 2;
    private static final int MODES = 3;

    private static final String FLIGHT_TAG = "grantedFlight";
    public static final String FLYING_TAG = "flying";
    public static final String BOOSTING_TAG = "boosting";
    public static final String DIVING_TAG = "diving";
    public static final String BOOST_SPEED_TAG = "boostSpeed";
    public static final String AIRBORNE_TAG = "airborne";
    private static final ResourceLocation WIND = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "wing_wind_boost");

    private static final int DIVE_TICKS = 100;
    private static final double SURFACE_PROBE = 0.25D;
    private static final double MIN_DIVE_DROP = 0.15D;
    private static final double DEFAULT_DIVE_DROP = 0.8D;
    private static final double DIVE_TURN = 0.35D;
    private static final double DIVE_STEER = 0.2D;
    private static final double DIVE_HIT_REACH = 0.5D;
    private static final int RISE_TICKS = 100;
    private static final int STUCK_TICKS = 10;
    private static final double CEILING_PROBE = 0.5D;
    private static final double TOP_GAP = 0.25D;
    private static final double GROUND_REACH = 6.0D;

    private static final float DEFAULT_FLYING_SPEED = 0.05F;
    private static final String FLIGHT_SPEED_TAG = "grantedFlightSpeed";

    private static final class Dive {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final long start;
        private final @Nullable LivingEntity target;
        private Vec3 direction;
        private double speed;

        private Dive(ManasSkillInstance instance, LivingEntity owner, long start, Vec3 direction, @Nullable LivingEntity target) {
            this.instance = instance;
            this.owner = owner;
            this.start = start;
            this.direction = direction;
            this.target = target;
            this.speed = CONFIG.diveSpeed;
        }

        private ManasSkillInstance instance() {
            return instance;
        }

        private LivingEntity owner() {
            return owner;
        }

        private long start() {
            return start;
        }

        private Vec3 direction() {
            return direction;
        }
    }

    private static final class Rise {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final long start;
        private final double speed;
        private final double topY;
        private double lastY;
        private int stuck;

        private Rise(ManasSkillInstance instance, LivingEntity owner, long start, double speed) {
            this.instance = instance;
            this.owner = owner;
            this.lastY = owner.getY();
            this.topY = owner.getY() + CONFIG.airborneHeight;
            this.start = start;
            this.speed = speed;
        }
    }

    private static final List<Dive> DIVES = new ArrayList<>();
    private static final List<Rise> RISES = new ArrayList<>();
    private static final Set<LivingEntity> SOFT_LANDING = Collections.newSetFromMap(new WeakHashMap<>());

    private static boolean clientRising;
    private static double clientTopY;
    private static double clientRiseSpeed;
    private static final Set<Player> FLAT = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Player> CLIENT_FLAT = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public WingQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BOOST -> CONFIG.boostAuraCost;
            case DIVE -> CONFIG.diveAuraCost;
            case AIRBORNE -> CONFIG.airborneAuraCost;
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
            case BOOST -> "wing.boost";
            case DIVE -> "wing.dive";
            case AIRBORNE -> "wing.airborne";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getWing(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WING.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == DIVE) {
            dive(level, instance, entity);
        } else if (mode == AIRBORNE) {
            airborne(level, instance, entity);
        } else if (mode == BOOST && !isFlying(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.wing.not_flying"));
        }
    }

    // Flight and Air Affinity

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        instance.setToggled(false);
        update(instance, entity);
        if (entity instanceof Player player) {
            setFlat(player, false);
        }
        super.onForgetSkill(instance, entity);
        DIVES.removeIf(dive -> dive.owner() == entity);
        RISES.removeIf(rise -> rise.owner == entity);
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        boolean on = instance.isToggled() && instance.getMastery() >= 0.0D;
        AdditiveBoost.apply(entity, TensuraAttributes.WIND_BOOST, WIND, on ? CONFIG.airBoost : 0.0D);
        if (entity instanceof ServerPlayer player) {
            updateFlight(instance, player, on);
        }

        CompoundTag tag = instance.getOrCreateTag();
        boolean flying = on && isFlying(entity);
        double boostSpeed = getBoostSpeed(instance, entity);
        if (tag.getBoolean(FLYING_TAG) != flying || tag.getDouble(BOOST_SPEED_TAG) != boostSpeed) {
            tag.putBoolean(FLYING_TAG, flying);
            tag.putDouble(BOOST_SPEED_TAG, boostSpeed);
            if (!flying) {
                tag.remove(BOOSTING_TAG);
            }
            instance.markDirty();
        }
    }

    private static void updateFlight(ManasSkillInstance instance, ServerPlayer player, boolean on) {
        CompoundTag tag = instance.getOrCreateTag();
        if (on) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
                tag.putBoolean(FLIGHT_TAG, true);
                instance.markDirty();
            }

            // Wing flight speed increase
            if (tag.getBoolean(FLIGHT_TAG) && !tag.getBoolean(FLIGHT_SPEED_TAG) && player.getAbilities().getFlyingSpeed() == DEFAULT_FLYING_SPEED) {
                player.getAbilities().setFlyingSpeed((float) (DEFAULT_FLYING_SPEED * CONFIG.flightSpeedMultiplier));
                player.onUpdateAbilities();
                tag.putBoolean(FLIGHT_SPEED_TAG, true);
                instance.markDirty();
            }
            return;
        }

        if (tag.getBoolean(FLIGHT_SPEED_TAG)) {
            tag.remove(FLIGHT_SPEED_TAG);
            instance.markDirty();
            player.getAbilities().setFlyingSpeed(DEFAULT_FLYING_SPEED);
            player.onUpdateAbilities();
        }

        if (!tag.getBoolean(FLIGHT_TAG)) {
            return;
        }

        tag.remove(FLIGHT_TAG);
        instance.markDirty();
        if (!SkillUtils.canFlyLegit(player)) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ManasSkillInstance instance = getWing(player).orElse(null);
            if (instance != null) {
                update(instance, player);
            }
            setFlat(player, (instance != null && instance.getOrCreateTag().getBoolean(BOOSTING_TAG) && isFlying(player) || NewOrderSelf.isFlyBoosting(player)) && !player.isPassenger());
        }
    }

    // Hitbox change to match the body
    // This is because I changed the pose so they're kinda flat and not upright
    public static void setFlat(Player player, boolean flat) {
        Set<Player> flats = getFlats(player);
        if (flat == flats.contains(player)) {
            return;
        }

        if (flat) {
            flats.add(player);
            if (player.getForcedPose() == null) {
                player.setForcedPose(Pose.STANDING);
            }
        } else {
            flats.remove(player);
            if (player.getForcedPose() == Pose.STANDING) {
                player.setForcedPose(null);
            }
        }
        player.refreshDimensions();
    }

    public static boolean isFlat(Player player) {
        return getFlats(player).contains(player);
    }

    private static Set<Player> getFlats(Player player) {
        return player.level().isClientSide ? CLIENT_FLAT : FLAT;
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && isFlat(player)) {
            float width = event.getNewSize().width();
            event.setNewSize(EntityDimensions.scalable(width, width).withEyeHeight(width * 2.0F / 3.0F));
        }
    }

    // Boost

    public static boolean isFlying(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getAbilities().flying || player.isFallFlying();
        }

        return !entity.onGround() && !entity.isInLiquid();
    }

    // Multiplier multiplies the boost too
    // When I add AFO quirk counter changes for boost from multiplier and kinetic boost this will get nerfed slightly
    private static double getBoostSpeed(ManasSkillInstance instance, LivingEntity entity) {
        return CONFIG.boostSpeed * MultiplierQuirk.getOutput(entity) * (instance.isMastered(entity) ? CONFIG.boostSpeedMasteredMultiplier : 1.0D);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != BOOST || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (!isFlying(entity)) {
            setBoosting(instance, false);
            return false;
        }

        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, BOOST)) {
            setBoosting(instance, false);
            return false;
        }

        if (heldTicks == 0) {
            sonicBoom(level, entity, entity.getLookAngle());
        }

        setBoosting(instance, true);
        if (!(entity instanceof Player)) {
            entity.setDeltaMovement(entity.getLookAngle().scale(getBoostSpeed(instance, entity) / 20.0D));
            entity.hurtMarked = true;
        }

        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == BOOST) {
            setBoosting(instance, false);
        }
    }

    private static void setBoosting(ManasSkillInstance instance, boolean boosting) {
        setTag(instance, BOOSTING_TAG, boosting);
    }

    private static void setTag(ManasSkillInstance instance, String key, boolean value) {
        if (instance.getOrCreateTag().getBoolean(key) != value) {
            instance.getOrCreateTag().putBoolean(key, value);
            instance.markDirty();
        }
    }

    // Dive

    private static boolean isDiving(LivingEntity entity) {
        return DIVES.stream().anyMatch(dive -> dive.owner() == entity);
    }

    private static void dive(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isDiving(entity)) {
            return;
        }

        if (entity.onGround() || hitGround(level, entity)) {
            fail(entity, Component.translatable("tracadamia.skill.wing.on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, DIVE)) {
            return;
        }

        LivingEntity target = getDiveTarget(level, entity);
        Vec3 direction = target != null ? target.getBoundingBox().getCenter().subtract(entity.getBoundingBox().getCenter()).normalize() : getDiveLook(entity, null);
        if (entity instanceof ServerPlayer player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        endRise(entity);
        DIVES.add(new Dive(instance, entity, level.getGameTime(), keepFalling(direction), target));
        setTag(instance, DIVING_TAG, true);
        entity.resetFallDistance();
        playSound(level, entity.position(), SoundEvents.PHANTOM_SWOOP, 1.2F, 0.8F);
        instance.setCoolDown(CONFIG.diveCooldown, DIVE);
        instance.addMasteryPoint(entity);
    }

    private static boolean hitGround(ServerLevel level, LivingEntity entity) {
        return !level.noCollision(entity, entity.getBoundingBox().move(0.0D, -SURFACE_PROBE, 0.0D));
    }

    private static @Nullable LivingEntity getDiveTarget(ServerLevel level, LivingEntity entity) {
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getLookAngle();
        double aim = Math.cos(Math.toRadians(CONFIG.diveAimDegrees));
        LivingEntity best = null;
        double bestAim = aim;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(CONFIG.diveTargetRange),
                other -> other != entity && other.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(entity, other) && HitRules.canHit(entity, other))) {
            Vec3 to = other.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance < 1.0E-3D || distance > CONFIG.diveTargetRange || to.y > 0.0D) {
                continue;
            }

            double dot = to.scale(1.0D / distance).dot(look);
            if (dot > bestAim && entity.hasLineOfSight(other)) {
                bestAim = dot;
                best = other;
            }
        }
        return best;
    }

    // gonna help for dive angle
    private static Vec3 getDiveLook(LivingEntity entity, @Nullable Vec3 current) {
        Vec3 look = entity.getLookAngle();
        if (look.y <= -MIN_DIVE_DROP) {
            return look;
        }

        if (current != null) {
            return current;
        }

        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-6D ? Vec3.ZERO : flat.normalize();
        return flat.scale(Math.sqrt(1.0D - DEFAULT_DIVE_DROP * DEFAULT_DIVE_DROP)).add(0.0D, -DEFAULT_DIVE_DROP, 0.0D);
    }

    private static Vec3 keepFalling(Vec3 direction) {
        return direction.y > -MIN_DIVE_DROP ? new Vec3(direction.x, -MIN_DIVE_DROP, direction.z).normalize() : direction.normalize();
    }

    // Helps homing in on targets
    // It was hard to hit stuff so I added this
    // could be a skill issue
    private static void steer(Dive dive) {
        LivingEntity owner = dive.owner();
        LivingEntity target = dive.target;
        Vec3 wanted;
        double turn;
        if (target != null && target.isAlive() && !target.isRemoved() && target.level() == owner.level()) {
            wanted = target.getBoundingBox().getCenter().subtract(owner.getBoundingBox().getCenter());
            turn = DIVE_TURN;
        } else {
            wanted = getDiveLook(owner, dive.direction);
            turn = DIVE_STEER;
        }

        if (wanted.lengthSqr() > 1.0E-6D) {
            dive.direction = keepFalling(dive.direction.lerp(wanted.normalize(), turn));
        }
    }

    private static @Nullable LivingEntity getDiveHit(ServerLevel level, LivingEntity owner, Vec3 step) {
        AABB swept = owner.getBoundingBox().expandTowards(step).inflate(DIVE_HIT_REACH);
        LivingEntity hit = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, swept,
                other -> other != owner && other.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(owner, other) && HitRules.canHit(owner, other))) {
            double distance = other.distanceToSqr(owner);
            if (distance < closest) {
                closest = distance;
                hit = other;
            }
        }
        return hit;
    }

    private static boolean tickDive(Dive dive) {
        LivingEntity owner = dive.owner();
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - dive.start();
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
        }

        if (owner.isInLiquid() || age >= DIVE_TICKS) {
            return true;
        }

        // Gets faster the longer youre diving
        if (age > 0L && age % Math.max(1, CONFIG.diveSpeedTicks) == 0L && dive.speed < CONFIG.diveMaxSpeed) {
            dive.speed = Math.min(CONFIG.diveMaxSpeed, dive.speed + CONFIG.diveSpeedIncrease);
            sonicBoom(level, owner, dive.direction());
        }

        if (age > 0L) {
            steer(dive);
        }

        Vec3 step = dive.direction().scale(dive.speed / 20.0D);
        LivingEntity struck = getDiveHit(level, owner, step);
        if (struck != null) {
            land(level, dive, struck.position());
            return true;
        }

        BlockHitResult ahead = level.clip(new ClipContext(owner.position(), owner.position().add(step), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (age > 0L && (owner.onGround() || hitGround(level, owner) || ahead.getType() == HitResult.Type.BLOCK)) {
            land(level, dive, ahead.getType() == HitResult.Type.BLOCK ? ahead.getLocation() : null);
            return true;
        }

        owner.setDeltaMovement(step);
        owner.hurtMarked = true;
        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + owner.getBbHeight(), owner.getZ(), 2, owner.getBbWidth() * 0.3D, 0.1D, owner.getBbWidth() * 0.3D, 0.01D);
        }
        return false;
    }

    // increase shockwave by user size and speed so it looks cooler and is BETTER
    private static void land(ServerLevel level, Dive dive, @Nullable Vec3 hit) {
        LivingEntity owner = dive.owner();
        owner.resetFallDistance();
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        Vec3 feet = getGround(level, owner, hit, dive.speed / 20.0D);
        double size = owner.getScale() * dive.speed / CONFIG.diveSpeed;
        double radius = CONFIG.diveRadius * size;
        float damage = (float) (CONFIG.diveDamage * size);
        ManasSkillInstance instance = dive.instance();
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(feet, feet).inflate(radius, 2.0D, radius).expandTowards(0.0D, owner.getBbHeight(), 0.0D),
                other -> other != owner && other.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(owner, other) && HitRules.canHit(owner, other) && other.position().subtract(feet).horizontalDistance() <= radius)) {
            other.invulnerableTime = 0;
            other.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, DIVE), damage);
            SkillHelper.knockBack(other, owner, instance, other.position().subtract(feet).multiply(1.0D, 0.0D, 1.0D), 0.8D, 0.0D, 0.4D);
            other.hurtMarked = true;
        }

        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, feet.x, feet.y + 0.05D, feet.z, 0, 0.0D, 1.0D, 0.0D, radius / 1.5D);
        level.sendParticles(ParticleTypes.EXPLOSION, feet.x, feet.y + 0.3D, feet.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.5D, feet.z);
        GroundBlocks.dust(level, below, level.getBlockState(below), 12);
        playSound(level, feet, SoundEvents.MACE_SMASH_GROUND, 1.2F, 0.7F);
        playSound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.2F);
    }

    // somewhat buggy, sometiems the server decides this is like a block or 2 higher than it should be
    // maybe im slow?
    private static Vec3 getGround(ServerLevel level, LivingEntity owner, @Nullable Vec3 hit, double reach) {
        if (hit != null) {
            return hit;
        }

        Vec3 feet = owner.position();
        BlockHitResult floor = level.clip(new ClipContext(feet.add(0.0D, 0.5D, 0.0D), feet.subtract(0.0D, reach + 3.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : feet;
    }

    // Just shoots you straight up and does some damage

    private static boolean isRising(LivingEntity entity) {
        return RISES.stream().anyMatch(rise -> rise.owner == entity);
    }

    private static void endRise(LivingEntity entity) {
        RISES.removeIf(rise -> {
            if (rise.owner != entity) {
                return false;
            }
            setTag(rise.instance, AIRBORNE_TAG, false);
            return true;
        });
    }

    private static void airborne(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isRising(entity)) {
            return;
        }

        if (!hitGround(level, entity)) {
            fail(entity, Component.translatable("tracadamia.skill.wing.not_on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, AIRBORNE)) {
            return;
        }

        DIVES.removeIf(dive -> {
            if (dive.owner() != entity) {
                return false;
            }
            setTag(dive.instance(), DIVING_TAG, false);
            return true;
        });

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.airborneRadiusMastered : CONFIG.airborneRadius;
        float damage = (float) (mastered ? CONFIG.airborneDamageMastered : CONFIG.airborneDamage);
        DamageSource source = ((TensuraDamageSource) ((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.WIND_ELEMENTAL, AIRBORNE)).tensura$setElement(Element.WIND);
        Vec3 center = entity.getBoundingBox().getCenter();
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                other -> other != entity && other.isAlive() && !other.isSpectator() && HitRules.canHit(entity, other) && !MultiArms.isHoldPair(entity, other)
                        && other.getBoundingBox().getCenter().distanceTo(center) <= radius + other.getBbWidth() * 0.5D)) {
            other.invulnerableTime = 0;
            other.hurt(source, damage);
            SkillHelper.knockBack(other, entity, instance, other.position().subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D), 1.0D, 0.0D, 0.5D);
            other.hurtMarked = true;
        }

        if (entity instanceof ServerPlayer player && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        Rise rise = new Rise(instance, entity, level.getGameTime(), getBoostSpeed(instance, entity) / 20.0D);
        RISES.add(rise);
        setTag(instance, AIRBORNE_TAG, true);
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new AirbornePayload(true, rise.topY, rise.speed));
        }
        entity.resetFallDistance();

        // Takeoff shockwave on the ground
        BlockHitResult floor = level.clip(new ClipContext(entity.position().add(0.0D, 0.5D, 0.0D), entity.position().subtract(0.0D, GROUND_REACH, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Vec3 ground = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : entity.position();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, ground.x, ground.y + 0.05D, ground.z, 0, 0.0D, 1.0D, 0.0D, radius / 1.5D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, ground.x, ground.y + 0.5D, ground.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, ground.x, ground.y + 0.3D, ground.z, 40, radius * 0.4D, 0.1D, radius * 0.4D, 0.15D);
        if (floor.getType() == HitResult.Type.BLOCK) {
            BlockPos below = floor.getBlockPos();
            GroundBlocks.dust(level, below, level.getBlockState(below), 16);
        }
        playSound(level, ground, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.5F, 1.0F);
        playSound(level, ground, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.5F, 0.6F);
        playSound(level, ground, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.3F);
        instance.setCoolDown(CONFIG.airborneCooldown, AIRBORNE);
        instance.addMasteryPoint(entity);
    }

    // becomes true once you reached max height the skill takes you
    private static boolean tickRise(Rise rise) {
        LivingEntity owner = rise.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - rise.start;
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
        }

        boolean ceiling = !level.noCollision(owner, owner.getBoundingBox().move(0.0D, CEILING_PROBE, 0.0D));
        rise.stuck = owner.getY() - rise.lastY < 0.05D ? rise.stuck + 1 : 0;
        rise.lastY = owner.getY();

        //the client does the main part this is for finding the top
        if (owner instanceof ServerPlayer player) {
            boolean top = owner.getY() >= rise.topY - TOP_GAP;
            if (top || age >= RISE_TICKS || ceiling || rise.stuck >= STUCK_TICKS) {
                if (!top) {
                    PacketDistributor.sendToPlayer(player, new AirbornePayload(false, rise.topY, 0.0D));
                }
                hover(owner);
                return true;
            }

            spiral(level, owner, age);
            return false;
        }

        double left = rise.topY - owner.getY();
        if (left <= TOP_GAP || age >= RISE_TICKS || ceiling || rise.stuck >= STUCK_TICKS) {
            hover(owner);
            return true;
        }

        double step = Math.min(rise.speed, left);
        owner.setDeltaMovement(0.0D, step, 0.0D);
        owner.hurtMarked = true;
        spiral(level, owner, age);
        return false;
    }

    // cool effects
    private static void spiral(ServerLevel level, LivingEntity owner, long age) {
        Vec3 center = owner.getBoundingBox().getCenter();
        double spread = Math.max(0.6D, owner.getBbWidth());
        for (int arm = 0; arm < 3; arm++) {
            double angle = age * 0.9D + arm * Mth.TWO_PI / 3.0D;
            level.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(angle) * spread, center.y - owner.getBbHeight() * 0.3D, center.z + Math.sin(angle) * spread, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (age % 4L == 0L) {
            level.sendParticles(ParticleTypes.GUST, center.x, owner.getY(), center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void hover(LivingEntity owner) {
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = true;
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player && player.getAbilities().mayfly) {
            if (!player.getAbilities().flying) {
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            return;
        }
        SOFT_LANDING.add(owner);
    }

    public static boolean isClientRising() {
        return clientRising;
    }

    public static void tickClientRise(Player player) {
        if (!clientRising) {
            return;
        }

        double left = clientTopY - player.getY();
        if (left <= 0.01D) {
            clientRising = false;
            player.setDeltaMovement(Vec3.ZERO);
            // Hovers right away instead of dipping until the server says so
            if (player.getAbilities().mayfly) {
                player.getAbilities().flying = true;
            }
            return;
        }

        player.setDeltaMovement(0.0D, Math.min(clientRiseSpeed, left), 0.0D);
        player.resetFallDistance();
    }

    public static void clearClient() {
        clientRising = false;
    }

    public record AirbornePayload(boolean rising, double topY, double speed) implements CustomPacketPayload {
        public static final Type<AirbornePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "wing_airborne"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AirbornePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, AirbornePayload::rising,
                ByteBufCodecs.DOUBLE, AirbornePayload::topY,
                ByteBufCodecs.DOUBLE, AirbornePayload::speed,
                AirbornePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(AirbornePayload.TYPE, AirbornePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            clientRising = payload.rising();
            clientTopY = payload.topY();
            clientRiseSpeed = payload.speed();
        }));
    }


    public static boolean isAirborne(LivingEntity entity) {
        return !entity.onGround() && !entity.isInLiquid() && !entity.isPassenger();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getAmount() <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || attacker == event.getEntity() || attacker.level().isClientSide || !isAirborne(attacker)) {
            return;
        }

        getWing(attacker).filter(ManasSkillInstance::isToggled).ifPresent(instance ->
                event.setAmount(event.getAmount() + (float) (instance.isMastered(attacker) ? CONFIG.skyHunterDamageMastered : CONFIG.skyHunterDamage)));
    }

    private static void sonicBoom(ServerLevel level, LivingEntity entity, Vec3 direction) {
        Vec3 behind = entity.getBoundingBox().getCenter().subtract(direction.scale(entity.getBbWidth() + 1.0D));
        double size = Math.max(1.0D, entity.getScale());
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, behind.x, behind.y, behind.z, 0, direction.x, direction.y, direction.z, 3.0D * size);
        level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 30, 0.6D * size, 0.6D * size, 0.6D * size, 0.25D);
        level.sendParticles(ParticleTypes.EXPLOSION, behind.x, behind.y, behind.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, behind, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0F, 0.5F);
        playSound(level, behind, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.6F);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!event.getEntity().level().isClientSide && (isDiving(event.getEntity()) || isRising(event.getEntity()) || SOFT_LANDING.remove(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!DIVES.isEmpty()) {
            DIVES.removeIf(dive -> {
                boolean over = tickDive(dive);
                if (over) {
                    setTag(dive.instance(), DIVING_TAG, false);
                }
                return over;
            });
        }

        if (!RISES.isEmpty()) {
            RISES.removeIf(rise -> {
                boolean over = tickRise(rise);
                if (over) {
                    setTag(rise.instance, AIRBORNE_TAG, false);
                }
                return over;
            });
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DIVES.clear();
        RISES.clear();
        SOFT_LANDING.clear();
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

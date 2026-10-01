package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.mixin.ServerGamePacketListenerAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class BruiserQuirk extends Skill {

    private static final QuirkSkillsConfig.Bruiser CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Bruiser;

    private static final int POWER_UP = 0;
    private static final int POWER_MOVE = 1;

    public static final int RIGHT_ARM = 0;
    public static final int LEFT_ARM = 1;
    public static final int RIGHT_LEG = 2;
    public static final int LEFT_LEG = 3;
    private static final String[] PART_NAMES = {"right_arm", "left_arm", "right_leg", "left_leg"};

    private static final String PART_TAG = "part";
    private static final String POWERED_TAG = "powered";
    private static final String BLOCKING_TAG = "blocking";
    private static final String ARM_HEALTH_TAG = "armHealth";
    private static final String ARM_TIME_TAG = "armTime";
    private static final ResourceLocation POWER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "bruiser");

    private static final int LEAP_TIMEOUT = 300;
    private static final int LEAP_TAKEOFF = 20;
    private static final double AIR_DRAG = 0.91D;
    private static final int STOMP_LIFTS = 12;

    private static final List<Leap> LEAPS = new ArrayList<>();

    private static final class Leap {
        private final LivingEntity owner;
        private final long start;
        private boolean airborne;
        private Vec3 lastPos;
        private Vec3 motion;

        private Leap(LivingEntity owner, long start, Vec3 motion) {
            this.owner = owner;
            this.start = start;
            this.lastPos = owner.position();
            this.motion = motion;
        }
    }

    public BruiserQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == POWER_UP ? CONFIG.powerUpAuraCost : CONFIG.powerMoveAuraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == POWER_UP ? POWER_MOVE : POWER_UP;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case POWER_UP -> "bruiser.power_up";
            case POWER_MOVE -> "bruiser.power_move";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getBruiser(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.BRUISER.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static int getPart(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? RIGHT_ARM : Mth.clamp(tag.getInt(PART_TAG), RIGHT_ARM, LEFT_LEG);
    }

    public static boolean isPowered(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(POWERED_TAG);
    }

    public static boolean isBlocking(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(BLOCKING_TAG);
    }

    public static double getPartScale() {
        return CONFIG.partScale;
    }

    private static void setFlag(ManasSkillInstance instance, String key, boolean value) {
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(key) == value) {
            return;
        }

        if (value) {
            tag.putBoolean(key, true);
        } else {
            tag.remove(key);
        }
        instance.markDirty();
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        boolean powered = isPowered(instance);
        int part = getPart(instance);
        boolean mastered = instance.isMastered(entity);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, POWER, powered && part == RIGHT_ARM ? mastered ? CONFIG.rightArmDamageMastered : CONFIG.rightArmDamage : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ARMOR, POWER, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.KNOCKBACK_RESISTANCE, POWER, powered && part == RIGHT_LEG ? mastered ? CONFIG.rightLegKnockbackResistanceMastered : CONFIG.rightLegKnockbackResistance : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, POWER, powered && part == LEFT_LEG ? mastered ? CONFIG.leftLegSpeedMastered : CONFIG.leftLegSpeed : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.JUMP_STRENGTH, POWER, powered && part == LEFT_LEG ? CONFIG.leftLegJump : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static double getBonusDamage(LivingEntity entity, double share) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 0.0D : attack.getValue() * share;
    }

    private static InteractionHand getHand(LivingEntity entity, boolean right) {
        return (entity.getMainArm() == HumanoidArm.RIGHT) == right ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == POWER_UP) {
            powerUp(level, instance, entity);
            return;
        }

        if (!isPowered(instance)) {
            fail(entity, Component.translatable("tracadamia.skill.bruiser.not_powered"));
            return;
        }

        switch (getPart(instance)) {
            case RIGHT_ARM -> punch(level, instance, entity);
            case LEFT_ARM -> {
                if (entity.isShiftKeyDown()) {
                    swipe(level, instance, entity);
                }
            }
            case RIGHT_LEG -> stomp(level, instance, entity);
            case LEFT_LEG -> leap(level, instance, entity);
        }
    }

    private static void powerUp(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (entity.isShiftKeyDown()) {
            int part = (getPart(instance) + 1) % PART_NAMES.length;
            instance.getOrCreateTag().putInt(PART_TAG, part);
            setFlag(instance, BLOCKING_TAG, false);
            instance.markDirty();
            update(instance, entity);
            if (entity instanceof Player player) {
                Component name = Component.translatable("tracadamia.skill.bruiser.part." + PART_NAMES[part]);
                player.displayClientMessage(Component.translatable("tracadamia.skill.bruiser.powering", name).withStyle(ChatFormatting.GOLD), true);
            }

            playSound(level, entity, isPowered(instance) ? TensuraSoundEvents.TRANSFORM_OGRE.get() : SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, 1.2F);
            return;
        }

        if (isPowered(instance)) {
            setFlag(instance, POWERED_TAG, false);
            setFlag(instance, BLOCKING_TAG, false);
            update(instance, entity);
            level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.5D), entity.getZ(), 10, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.02D);
            playSound(level, entity, SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0F, 0.7F);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_UP)) {
            return;
        }

        setFlag(instance, POWERED_TAG, true);
        update(instance, entity);
        float auraSize = (float) (entity.getAttributeValue(Attributes.SCALE) * 3.0D);
        TensuraParticleHelper.addServerAuraParticles(entity, TensuraParticleUtils.getRedAura(1.0F, auraSize, -0.3F), 2, 0.05D);
        playSound(level, entity, TensuraSoundEvents.TRANSFORM_OGRE.get(), 1.0F, 1.1F);
        instance.addMasteryPoint(entity);
    }

    private static void punch(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_MOVE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        float damage = (float) ((mastered ? CONFIG.punchDamageMastered : CONFIG.punchDamage) + getBonusDamage(entity, mastered ? CONFIG.punchDamagePercentMastered : CONFIG.punchDamagePercent));
        double reach = CONFIG.punchRange * Math.max(1.0D, entity.getScale());
        Vec3 look = entity.getLookAngle();
        LivingEntity target = MultiArms.getTarget(entity, reach);
        Vec3 point = target != null ? target.getBoundingBox().getCenter() : entity.getEyePosition().add(look.scale(reach));
        if (target != null) {
            hit(instance, entity, target, damage);
            target.knockback(1.5D, -look.x, -look.z);
            target.hurtMarked = true;
        }

        double radius = CONFIG.punchShockwaveRadius;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
                other -> other != entity && other != target && other.isAlive() && !other.isSpectator() && other.getBoundingBox().getCenter().distanceTo(point) <= radius)) {
            hit(instance, entity, other, (float) (damage * CONFIG.punchShockwaveDamage));
            Vec3 away = other.position().subtract(point);
            other.knockback(0.8D, -away.x, -away.z);
            other.hurtMarked = true;
        }

        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y, point.z, 0, look.x, look.y, look.z, 1.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y, point.z, 0, look.x, look.y, look.z, radius / 3.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        entity.swing(getHand(entity, true), true);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        instance.setCoolDown(CONFIG.punchCooldown, POWER_MOVE);
        instance.addMasteryPoint(entity);
    }

    private static void swipe(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_MOVE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        float damage = (float) ((mastered ? CONFIG.swipeDamageMastered : CONFIG.swipeDamage) + getBonusDamage(entity, mastered ? CONFIG.swipeDamagePercentMastered : CONFIG.swipeDamagePercent));
        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double range = CONFIG.swipeRange * Math.max(1.0D, entity.getScale());
        double reach = Math.max(range, CONFIG.swipeShockwaveRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(reach, 2.0D, reach),
                target -> target != entity && target.isAlive() && !target.isSpectator())) {
            Vec3 offset = target.position().subtract(entity.position());
            double distance = offset.horizontalDistance();
            if (distance > reach || new Vec3(offset.x, 0.0D, offset.z).normalize().dot(forward) < 0.0D) {
                continue;
            }

            Vec3 away = new Vec3(offset.x, 0.0D, offset.z).normalize().add(right.scale(-0.5D)).normalize();
            double strength = CONFIG.swipeKnockback * (distance <= range ? 1.0D : 1.0D - (distance - range) / Math.max(1.0D, reach - range) * 0.6D);
            if (distance <= range) {
                hit(instance, entity, target, damage);
            }

            target.setDeltaMovement(target.getDeltaMovement().add(away.x * strength, 0.35D, away.z * strength));
            target.hurtMarked = true;
        }

        Vec3 middle = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).add(forward.scale(range * 0.6D));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, middle.x, middle.y, middle.z, 3, range * 0.3D, 0.1D, range * 0.3D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, middle.x, middle.y, middle.z, 0, forward.x, 0.0D, forward.z, reach / 3.0D);
        entity.swing(getHand(entity, false), true);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
        playSound(level, entity, SoundEvents.ENDER_DRAGON_FLAP, 0.8F, 1.2F);
        instance.setCoolDown(CONFIG.swipeCooldown, POWER_MOVE);
        instance.addMasteryPoint(entity);
    }

    private static void stomp(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.onGround()) {
            fail(entity, Component.translatable("tracadamia.skill.bruiser.not_on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_MOVE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        float damage = (float) ((mastered ? CONFIG.stompDamageMastered : CONFIG.stompDamage) + getBonusDamage(entity, mastered ? CONFIG.stompDamagePercentMastered : CONFIG.stompDamagePercent));
        double half = CONFIG.stompSize * 0.5D;
        double shock = CONFIG.stompShockwaveSize * 0.5D;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(shock, 3.0D, shock),
                target -> target != entity && target.isAlive() && !target.isSpectator())) {
            double dx = Math.abs(target.getX() - entity.getX());
            double dz = Math.abs(target.getZ() - entity.getZ());
            if (dx <= half && dz <= half) {
                hit(instance, entity, target, damage);
                target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
            } else if (dx <= shock && dz <= shock) {
                hit(instance, entity, target, (float) (damage * CONFIG.stompShockwaveDamage));
                Vec3 away = new Vec3(target.getX() - entity.getX(), 0.0D, target.getZ() - entity.getZ()).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.6D, 0.3D, away.z * 0.6D));
            } else {
                continue;
            }

            target.hurtMarked = true;
        }

        RandomSource random = level.random;
        BlockPos center = entity.blockPosition();
        int reach = Mth.ceil(half);
        int lifts = 0;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                BlockPos surface = GroundBlocks.findSurface(level, center.offset(x, 0, z));
                if (surface == null || surface.getY() >= entity.getY()) {
                    continue;
                }

                if (lifts < STOMP_LIFTS && random.nextFloat() < 0.35F && GroundBlocks.lift(level, instance, entity, surface, 0.25D + random.nextDouble() * 0.1D)) {
                    lifts++;
                } else {
                    GroundBlocks.dust(level, surface, level.getBlockState(surface), 2);
                }
            }
        }

        VibrateQuirk.shakeGround(level, entity.position(), half, 8);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, half / 1.5D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, shock / 1.5D);
        level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY() + 0.2D, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, entity, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.7F);
        playSound(level, entity, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
        instance.setCoolDown(CONFIG.stompCooldown, POWER_MOVE);
        instance.addMasteryPoint(entity);
    }

    private static void leap(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.onGround()) {
            fail(entity, Component.translatable("tracadamia.skill.bruiser.not_on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_MOVE)) {
            return;
        }

        Vec3 look = entity.getLookAngle();
        Vec3 aim = look.y < 0.0D ? Vec3.directionFromRotation(0.0F, entity.getYRot()) : look;
        Vec3 velocity = aim.add(0.0D, CONFIG.leapLift, 0.0D).normalize().scale(instance.isMastered(entity) ? CONFIG.leapSpeedMastered : CONFIG.leapSpeed);
        entity.setDeltaMovement(velocity);
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new LeapPayload(velocity.toVector3f()));
        } else {
            entity.setOnGround(false);
            entity.hurtMarked = true;
        }

        entity.resetFallDistance();
        LEAPS.removeIf(leap -> leap.owner == entity);
        LEAPS.add(new Leap(entity, level.getGameTime(), velocity));

        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, 2.0D);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(), entity.getZ(), 16, 0.5D, 0.05D, 0.5D, 0.05D);
        playSound(level, entity, SoundEvents.GOAT_LONG_JUMP, 1.5F, 0.6F);
        playSound(level, entity, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
        instance.setCoolDown(CONFIG.leapCooldown, POWER_MOVE);
        instance.addMasteryPoint(entity);
    }

    private static boolean isLeapBroken(LivingEntity entity) {
        return entity.isInLiquid() || entity.isFallFlying() || entity.isPassenger() || entity.shouldDiscardFriction() || entity instanceof Player player && player.getAbilities().flying;
    }

    private static boolean tickLeap(Leap leap) {
        LivingEntity owner = leap.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - leap.start;
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
        }

        if (owner.onGround() && leap.airborne) {
            land(level, owner);
            return true;
        }

        if (!owner.onGround() && age > 0L) {
            leap.airborne = true;
        }

        if (age > LEAP_TIMEOUT || !leap.airborne && age > LEAP_TAKEOFF || isLeapBroken(owner)) {
            return true;
        }

        if (owner instanceof ServerPlayer player) {
            Vec3 moved = player.position().subtract(leap.lastPos);
            if (leap.airborne) {
                leap.motion = moved.lengthSqr() > leap.motion.lengthSqr() * AIR_DRAG * AIR_DRAG ? moved : leap.motion.scale(AIR_DRAG);
            }

            player.setDeltaMovement(leap.motion);
        }

        leap.lastPos = owner.position();

        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY(), owner.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
        return false;
    }

    private static void land(ServerLevel level, LivingEntity owner) {
        owner.resetFallDistance();
        Vec3 feet = owner.position();
        BlockHitResult floor = level.clip(new ClipContext(feet.add(0.0D, 0.5D, 0.0D), feet.subtract(0.0D, 2.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        double ground = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation().y : feet.y;
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, feet.x, ground + 0.1D, feet.z, 0, 0.0D, 1.0D, 0.0D, 2.5D);
        BlockPos below = BlockPos.containing(feet.x, ground - 0.5D, feet.z);
        GroundBlocks.dust(level, below, level.getBlockState(below), 12);
        playSound(level, owner, SoundEvents.MACE_SMASH_GROUND, 1.2F, 0.7F);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && LEAPS.stream().anyMatch(leap -> leap.owner == entity)) {
            event.setCanceled(true);
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != POWER_MOVE || entity.isShiftKeyDown() || !isPowered(instance) || getPart(instance) != LEFT_ARM || !(entity.level() instanceof ServerLevel level)) {
            setFlag(instance, BLOCKING_TAG, false);
            return false;
        }

        if (heldTicks == 0) {
            if (EnergyHelper.isOutOfEnergy(entity, instance, POWER_MOVE)) {
                return false;
            }

            setFlag(instance, BLOCKING_TAG, true);
            playSound(level, entity, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.8F);
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return isBlocking(instance);
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == POWER_MOVE && isBlocking(instance)) {
            setFlag(instance, BLOCKING_TAG, false);
            setArmHealth(instance, entity, getArmHealth(instance, entity));
        }
    }

    private static double getMaxArmHealth(LivingEntity entity) {
        return entity.getMaxHealth() * CONFIG.blockDurability;
    }

    private static double getArmHealth(ManasSkillInstance instance, LivingEntity entity) {
        double max = getMaxArmHealth(entity);
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(ARM_HEALTH_TAG)) {
            return max;
        }

        double health = tag.getDouble(ARM_HEALTH_TAG);
        if (!isBlocking(instance)) {
            health += (entity.level().getGameTime() - tag.getLong(ARM_TIME_TAG)) / 20.0D * max * CONFIG.blockRegen;
        }

        return Math.min(max, health);
    }

    private static void setArmHealth(ManasSkillInstance instance, LivingEntity entity, double health) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.putDouble(ARM_HEALTH_TAG, health);
        tag.putLong(ARM_TIME_TAG, entity.level().getGameTime());
        instance.markDirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        getBruiser(entity).filter(BruiserQuirk::isBlocking).ifPresent(instance -> block(instance, entity, event));
    }

    private static void block(ManasSkillInstance instance, LivingEntity entity, LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null || !MultiArms.isInFront(entity, source.getSourcePosition())) {
            return;
        }

        ServerLevel level = (ServerLevel) entity.level();
        if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity()) {
            attacker.knockback(0.6D, entity.getX() - attacker.getX(), entity.getZ() - attacker.getZ());
        }

        double health = getArmHealth(instance, entity);
        float amount = event.getAmount();
        if (amount < health) {
            setArmHealth(instance, entity, health - amount);
            event.setCanceled(true);
            playSound(level, entity, SoundEvents.SHIELD_BLOCK, 1.0F, 0.7F + entity.getRandom().nextFloat() * 0.3F);
            return;
        }

        event.setAmount((float) (amount - health));
        setFlag(instance, BLOCKING_TAG, false);
        setArmHealth(instance, entity, getMaxArmHealth(entity));
        instance.setCoolDown(CONFIG.blockBrokenCooldown, POWER_MOVE);
        playSound(level, entity, SoundEvents.SHIELD_BREAK, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY(0.6D), entity.getZ(), 10, 0.4D, 0.3D, 0.4D, 0.1D);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.bruiser.arm_broken", CONFIG.blockBrokenCooldown).withStyle(ChatFormatting.RED), true);
        }
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, POWER_MOVE), damage);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (isPowered(instance)) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        setFlag(instance, BLOCKING_TAG, false);
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        setFlag(instance, POWERED_TAG, false);
        setFlag(instance, BLOCKING_TAG, false);
        update(instance, entity);
        LEAPS.removeIf(leap -> leap.owner == entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!LEAPS.isEmpty()) {
            LEAPS.removeIf(BruiserQuirk::tickLeap);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LEAPS.clear();
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

    public record LeapPayload(Vector3f velocity) implements CustomPacketPayload {
        public static final Type<LeapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "bruiser_leap"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LeapPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, LeapPayload::velocity,
                LeapPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(LeapPayload.TYPE, LeapPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            Player player = context.player();
            player.setOnGround(false);
            player.setDeltaMovement(new Vec3(payload.velocity()));
        }));
    }

}

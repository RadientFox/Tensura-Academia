package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.DamageReduction;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class MuscleExpansionQuirk extends Skill {

    private static final QuirkSkillsConfig.MuscleExpansion CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).MuscleExpansion;

    private static final int MUSCLE_EXPANSION = 0;
    private static final int SLAM = 1;
    private static final int CHOKE_HITS = 2;
    private static final int ARM_PROPULSION = 3;
    private static final int MODES = 4;

    public static final String EXPANDED_TAG = "expanded";
    public static final String ARM_SIZE_TAG = "armSize";
    public static final String SLAM_TAG = "slam";
    public static final String CHOKING_TAG = "choking";
    private static final String SLAM_HOLD_TAG = "slamHold";
    private static final String CHOKE_HOLD_TAG = "chokeHold";
    private static final String ARMOR_TAG = "muscleArmor";

    private static final ResourceLocation BIG_MUSCLES = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "big_muscles");
    private static final ResourceLocation EXPANSION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "muscle_expansion");

    public static final int SLAM_LIFT_TICKS = 6;
    private static final long CHOKE_GRACE = 10L;
    private static final long CHOKE_RENEW = 5L;
    private static final double PROPULSION_LIFT = 0.25D;

    private record Slam(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, long start) {}

    private static final List<Slam> SLAMS = new ArrayList<>();

    public MuscleExpansionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case MUSCLE_EXPANSION -> isExpanded(instance) ? 0.0D : CONFIG.expansionAuraCost;
            case SLAM -> CONFIG.slamAuraCost;
            case CHOKE_HITS -> CONFIG.chokeAuraCost;
            case ARM_PROPULSION -> CONFIG.propulsionAuraCost;
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
            case MUSCLE_EXPANSION -> "muscle_expansion.muscle_expansion";
            case SLAM -> "muscle_expansion.slam";
            case CHOKE_HITS -> "muscle_expansion.choke_hits";
            case ARM_PROPULSION -> "muscle_expansion.arm_propulsion";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == MUSCLE_EXPANSION && isExpanded(instance) || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getMuscleExpansion(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.MUSCLE_EXPANSION.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case MUSCLE_EXPANSION -> expand(level, instance, entity);
            case SLAM -> slam(level, instance, entity);
            case ARM_PROPULSION -> propel(level, instance, entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, BIG_MUSCLES, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, EXPANSION, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        stopChoking(instance, entity);
        List.copyOf(SLAMS).stream().filter(slam -> slam.owner() == entity).forEach(slam -> {
            SLAMS.remove(slam);
            MultiArms.clearHeld(slam.instance(), entity, SLAM_HOLD_TAG);
        });
    }

    // Big Muscles and Muscle Expansion

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateMuscles(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateMuscles(instance, entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateMuscles(instance, entity);
        if (instance.isToggled() || isExpanded(instance)) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateMuscles(instance, owner);
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || !isExpanded(instance) || amount.get() <= 0.0F || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        float before = amount.get();
        amount.set(DamageReduction.reduce(owner, source, before, instance.isMastered(owner) ? CONFIG.muscleArmorReductionMastered : CONFIG.muscleArmorReduction));
        CompoundTag tag = instance.getOrCreateTag();
        double absorbed = tag.getDouble(ARMOR_TAG) + before - amount.get();
        if (absorbed < CONFIG.muscleArmorHealth) {
            tag.putDouble(ARMOR_TAG, absorbed);
            instance.markDirty();
            return true;
        }

        tag.remove(ARMOR_TAG);
        tag.putBoolean(EXPANDED_TAG, false);
        instance.markDirty();
        updateMuscles(instance, owner);
        instance.setCoolDown(CONFIG.muscleArmorBrokenCooldown, MUSCLE_EXPANSION);
        level.sendParticles(ParticleTypes.POOF, owner.getX(), owner.getY(0.6D), owner.getZ(), 16, owner.getBbWidth() * 0.5D, owner.getBbHeight() * 0.3D, owner.getBbWidth() * 0.5D, 0.03D);
        playSound(level, owner, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.2F, 0.6F);
        playSound(level, owner, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
        if (owner instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.muscle_expansion.armor_broken").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    public static boolean isExpanded(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(EXPANDED_TAG);
    }

    public static float getArmSize(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null || !tag.contains(ARM_SIZE_TAG) ? 1.0F : tag.getFloat(ARM_SIZE_TAG);
    }

    private static void updateMuscles(ManasSkillInstance instance, LivingEntity entity) {
        boolean mastered = instance.isMastered(entity);
        boolean expanded = isExpanded(instance);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, BIG_MUSCLES, instance.isToggled() ? CONFIG.bigMusclesDamage : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, EXPANSION, expanded ? (mastered ? CONFIG.expansionDamageMastered : CONFIG.expansionDamage) - 1.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        float size = expanded ? (float) (mastered ? CONFIG.expansionSizeMastered : CONFIG.expansionSize) : 1.0F;
        if (getArmSize(instance) != size && !entity.level().isClientSide) {
            instance.getOrCreateTag().putFloat(ARM_SIZE_TAG, size);
            instance.markDirty();
        }
    }

    private static void expand(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean expanding = !isExpanded(instance);
        if (expanding && EnergyHelper.isOutOfEnergy(entity, instance, MUSCLE_EXPANSION)) {
            return;
        }

        instance.getOrCreateTag().putBoolean(EXPANDED_TAG, expanding);
        instance.getOrCreateTag().remove(ARMOR_TAG);
        instance.markDirty();
        updateMuscles(instance, entity);
        playSound(level, entity, expanding ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, expanding ? 0.6F : 1.2F);
        if (expanding) {
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6D, entity.getZ(), 12, entity.getBbWidth() * 0.6D, entity.getBbHeight() * 0.2D, entity.getBbWidth() * 0.6D, 0.1D);
            instance.addMasteryPoint(entity);
        }
    }

    // Grabs

    private static @Nullable LivingEntity findGrab(ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.grabRange + entity.getBbWidth());
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return null;
        }

        if (!MultiArms.canGrab(entity, target)) {
            fail(entity, Component.translatable(MultiArms.getGrabFail(target)));
            return null;
        }

        double maxSize = isExpanded(instance) ? CONFIG.grabMaxSizeExpanded : CONFIG.grabMaxSize;
        if (target.getBbHeight() > entity.getBbHeight() * maxSize || target.getBbWidth() > entity.getBbWidth() * maxSize) {
            fail(entity, Component.translatable("tracadamia.skill.muscle_expansion.too_big"));
            return null;
        }
        return target;
    }

    private static double getPhysicalDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    // Slam

    private static void slam(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (SLAMS.stream().anyMatch(slam -> slam.owner() == entity)) {
            return;
        }

        LivingEntity target = findGrab(instance, entity);
        if (target == null || EnergyHelper.isOutOfEnergy(entity, instance, SLAM)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        tag.putUUID(SLAM_HOLD_TAG, target.getUUID());
        tag.putLong(SLAM_TAG, level.getGameTime());
        instance.markDirty();
        SLAMS.add(new Slam(instance, entity, target, level.getGameTime()));
        MultiArms.holdAt(entity, target, getLiftOffset(entity, target), MultiArms.HOLD_BODY, true);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
        instance.setCoolDown(CONFIG.slamCooldown, SLAM);
        instance.addMasteryPoint(entity);
    }

    private static Vec3 getLiftOffset(LivingEntity owner, LivingEntity target) {
        return new Vec3(0.0D, owner.getBbHeight() + 0.1D, owner.getBbWidth() * 0.2D);
    }

    private static boolean tickSlam(Slam slam) {
        LivingEntity owner = slam.owner();
        LivingEntity target = slam.target();
        if (!owner.isAlive() || owner.isRemoved() || !target.isAlive() || target.isRemoved() || owner.level() != target.level() || !(owner.level() instanceof ServerLevel level)) {
            MultiArms.clearHeld(slam.instance(), owner, SLAM_HOLD_TAG);
            return true;
        }

        if (level.getGameTime() - slam.start() < SLAM_LIFT_TICKS) {
            MultiArms.holdAt(owner, target, getLiftOffset(owner, target), MultiArms.HOLD_BODY, true);
            return false;
        }

        impact(level, slam);
        return true;
    }

    private static void impact(ServerLevel level, Slam slam) {
        LivingEntity owner = slam.owner();
        LivingEntity target = slam.target();
        ManasSkillInstance instance = slam.instance();
        MultiArms.clearHeld(instance, owner, SLAM_HOLD_TAG);

        boolean mastered = instance.isMastered(owner);
        double radius = mastered ? CONFIG.craterRadiusMastered : CONFIG.craterRadius;
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.yBodyRot);
        Vec3 spot = owner.position().add(forward.scale(owner.getBbWidth() * 0.5D + radius + 0.5D));
        Vec3 ground = getFloor(level, owner, spot);
        crater(level, instance, owner, ground, radius);
        ground = getFloor(level, owner, ground);

        target.teleportTo(ground.x, ground.y, ground.z);
        target.setDeltaMovement(0.0D, -0.5D, 0.0D);
        target.hurtMarked = true;
        float damage = (float) ((mastered ? CONFIG.slamDamageMastered : CONFIG.slamDamage) + getPhysicalDamage(owner) * CONFIG.slamDamagePercent);
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, SLAM), damage);

        owner.swing(InteractionHand.MAIN_HAND, true);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, ground.x, ground.y + 0.05D, ground.z, 0, 0.0D, 1.0D, 0.0D, radius);
        level.sendParticles(ParticleTypes.EXPLOSION, ground.x, ground.y + 0.3D, ground.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, target, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.7F);
        playSound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.7F);
    }

    private static Vec3 getFloor(ServerLevel level, LivingEntity owner, Vec3 spot) {
        BlockHitResult floor = level.clip(new ClipContext(spot.add(0.0D, 1.0D, 0.0D), spot.subtract(0.0D, 6.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : spot;
    }

    private static void crater(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 center, double radius) {
        int reach = Mth.ceil(radius);
        BlockPos middle = BlockPos.containing(center.x, center.y - 0.5D, center.z);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                for (int y = 0; y >= -reach; y--) {
                    if (x * x + z * z + y * y * 4 > radius * radius) {
                        continue;
                    }

                    BlockPos pos = middle.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !GroundBlocks.canBreak(level, instance, owner, pos)) {
                        continue;
                    }

                    GroundBlocks.dust(level, pos, state, 1);
                    level.setBlock(pos, state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
                    GroundBlocks.griefed(level, instance, owner, pos);
                }
            }
        }
    }

    // Choke Hits

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != CHOKE_HITS || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks == 0) {
            LivingEntity grabbed = findGrab(instance, entity);
            if (grabbed == null || EnergyHelper.isOutOfEnergy(entity, instance, CHOKE_HITS)) {
                return false;
            }

            instance.getOrCreateTag().putUUID(CHOKE_HOLD_TAG, grabbed.getUUID());
            instance.markDirty();
            playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.6F);
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, entity, CHOKE_HOLD_TAG);
        if (target == null || heldTicks > 0 && heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, CHOKE_HITS)) {
            stopChoking(instance, entity);
            return false;
        }

        MultiArms.holdAt(entity, target, getChokeOffset(entity, target), MultiArms.HOLD_BODY);
        if (instance.getOrCreateTag().getLong(CHOKING_TAG) - level.getGameTime() < CHOKE_RENEW) {
            instance.getOrCreateTag().putLong(CHOKING_TAG, level.getGameTime() + CHOKE_GRACE);
            instance.markDirty();
        }

        int every = Math.max(1, CONFIG.chokeHitTicks);
        if (heldTicks % every == every - 1) {
            target.invulnerableTime = 0;
            target.hurt(((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, CHOKE_HITS), (float) (getPhysicalDamage(entity) * CONFIG.chokeDamagePercent));
            entity.swing(InteractionHand.MAIN_HAND, true);
            Vec3 point = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 6, 0.2D, 0.2D, 0.2D, 0.2D);
            playSound(level, target, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
        }

        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == CHOKE_HITS && !entity.level().isClientSide) {
            stopChoking(instance, entity);
        }
    }

    // Held up in the left hand
    private static Vec3 getChokeOffset(LivingEntity owner, LivingEntity target) {
        double height = Math.max(0.1D, (owner.getBbHeight() - target.getBbHeight()) * 0.85D);
        return new Vec3(-owner.getBbWidth() * 0.4D, height, (owner.getBbWidth() + target.getBbWidth()) * 0.5D + 0.2D);
    }

    public static boolean isChoking(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(CHOKING_TAG) >= entity.level().getGameTime();
    }

    private static void stopChoking(ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, CHOKE_HOLD_TAG);
        if (instance.getOrCreateTag().contains(CHOKING_TAG)) {
            instance.getOrCreateTag().remove(CHOKING_TAG);
            instance.markDirty();
        }
    }

    private static void propel(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean grounded = entity.onGround();
        if (!grounded && !instance.isMastered(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.muscle_expansion.not_on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ARM_PROPULSION)) {
            return;
        }

        Vec3 look = entity.getLookAngle();
        Vec3 aim = grounded && look.y < 0.0D ? Vec3.directionFromRotation(0.0F, entity.getYRot()) : look;
        double distance = CONFIG.propulsionDistance * (grounded ? 1.0D : CONFIG.propulsionAirShare) * (isExpanded(instance) ? CONFIG.propulsionExpandedMultiplier : 1.0D);
        double speed = MultiArms.getLaunchVelocity(new Vec3(aim.x, Math.max(aim.y, PROPULSION_LIFT), aim.z), distance).length();
        Vec3 velocity = (grounded ? new Vec3(aim.x, Math.max(aim.y, PROPULSION_LIFT), aim.z).normalize() : aim).scale(speed);
        BruiserQuirk.launch(level, instance, entity, velocity, false);

        entity.swing(InteractionHand.MAIN_HAND, true);
        entity.swing(InteractionHand.OFF_HAND, true);
        if (grounded) {
            level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, 1.5D);
            BlockPos below = entity.blockPosition().below();
            GroundBlocks.dust(level, below, level.getBlockState(below), 10);
            playSound(level, entity, SoundEvents.MACE_SMASH_GROUND, 1.2F, 0.8F);
        } else {
            Vec3 behind = entity.getBoundingBox().getCenter().subtract(aim.scale(entity.getBbWidth()));
            level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, behind.x, behind.y, behind.z, 0, aim.x, aim.y, aim.z, 1.5D);
            playSound(level, entity, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.7F);
        }

        instance.setCoolDown(CONFIG.propulsionCooldown, ARM_PROPULSION);
        instance.addMasteryPoint(entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SLAMS.isEmpty()) {
            SLAMS.removeIf(MuscleExpansionQuirk::tickSlam);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SLAMS.clear();
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

}

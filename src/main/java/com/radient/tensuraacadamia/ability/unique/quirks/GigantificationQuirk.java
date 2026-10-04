package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.Modifiers;
import com.radient.tensuraacadamia.util.SizeReach;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class GigantificationQuirk extends Skill {

    private static final QuirkSkillsConfig.Gigantification CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Gigantification;

    private static final int GROW = 0;
    private static final int KICK = 1;
    private static final int GRAB = 2;
    private static final int SWAT = 3;
    private static final int CRUSH = 4;
    private static final int MODES = 5;

    public static final String KICK_TAG = "kickStart";
    public static final String KICK_TICKS_TAG = "kickTicks";
    public static final String STOMP_TAG = "stomping";
    private static final String GAINED_TAG = "gained";
    private static final String GRAB_TAG = "grabTarget";
    private static final String ROCK_TAG = "rock";
    private static final String CRUSH_TAG = "crushTarget";
    private static final ResourceLocation GIGANT = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "gigantification");
    private static final ResourceLocation KICK_LOCK = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "gigantification_kick");

    private static final double SAME = 1.0E-6D;
    private static final int GROW_EFFECT_TICKS = 4;
    private static final int GROW_SOUND_TICKS = 20;
    private static final int MAX_KICKED = 128;
    private static final int MAX_STOMPED = 48;
    private static final double KICK_AIM_RANGE = 32.0D;
    private static final double KICK_ARC = 0.5D;
    private static final double KICK_REACH = 2.0D;
    private static final double SWAT_ARC = 0.0D;
    private static final double DEBRIS_GRAVITY = 0.08D;
    private static final int DEBRIS_TICKS = 200;

    private record Windup(ManasSkillInstance instance, LivingEntity owner, long landAt, boolean stomp) {}

    private record Grab(ManasSkillInstance instance, LivingEntity owner) {}

    private static final class Debris {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final TelekinesisBlockEntity block;
        private final BlockState state;
        private final float hardness;
        private final @Nullable BlockPos home;
        private final boolean kicked;
        private final Set<LivingEntity> hit = new HashSet<>();
        private Vec3 velocity;
        private int age;

        private Debris(ManasSkillInstance instance, LivingEntity owner, TelekinesisBlockEntity block, BlockState state, float hardness, @Nullable BlockPos home, boolean kicked, Vec3 velocity) {
            this.instance = instance;
            this.owner = owner;
            this.block = block;
            this.state = state;
            this.hardness = hardness;
            this.home = home;
            this.kicked = kicked;
            this.velocity = velocity;
        }
    }

    private static final List<Windup> WINDUPS = new ArrayList<>();
    private static final List<Grab> GRABS = new ArrayList<>();
    private static final List<Debris> DEBRIS = new ArrayList<>();

    public GigantificationQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case GROW -> entity.isShiftKeyDown() ? 0.0D : CONFIG.growAuraCost * (instance.isMastered(entity) ? CONFIG.growthPerTickMastered : CONFIG.growthPerTick);
            case KICK -> entity.isShiftKeyDown() ? CONFIG.stompAuraCost : CONFIG.kickAuraCost;
            case GRAB -> CONFIG.grabAuraCost;
            case SWAT -> CONFIG.swatAuraCost;
            case CRUSH -> CONFIG.crushAuraCost;
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
            case GROW -> "gigantification.grow";
            case KICK -> "gigantification.kick";
            case GRAB -> "gigantification.grab";
            case SWAT -> "gigantification.swat";
            case CRUSH -> "gigantification.crush";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == GRAB && isHolding(instance, entity) || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getGigantification(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.GIGANTIFICATION.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static double getGained(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : Math.max(0.0D, tag.getDouble(GAINED_TAG));
    }

    private static void setGained(ManasSkillInstance instance, double gained) {
        instance.getOrCreateTag().putDouble(GAINED_TAG, gained);
        instance.markDirty();
    }

    // Standing height for each point of size
    private static double getHeightPerSize(LivingEntity entity) {
        return entity.getDimensions(Pose.STANDING).height() / Math.max(0.01F, entity.getScale());
    }

    // Size gained or lost each tick
    private static double getGrowth(ManasSkillInstance instance, LivingEntity entity) {
        return (instance.isMastered(entity) ? CONFIG.growthPerTickMastered : CONFIG.growthPerTick) / getHeightPerSize(entity);
    }

    private static double getMaxGained(ManasSkillInstance instance, LivingEntity entity) {
        double maxSize = instance.isMastered(entity) ? CONFIG.maxSizeMastered : CONFIG.maxSize;
        return Math.max(0.0D, maxSize - (entity.getScale() - getGained(instance)));
    }

    // Kick, Grab, Swat and Crush need growth first
    private static boolean isBigEnough(ManasSkillInstance instance, LivingEntity entity) {
        if (getGained(instance) >= CONFIG.minSize - SAME) {
            return true;
        }

        fail(entity, Component.translatable("tracadamia.skill.gigantification.too_small"));
        return false;
    }

    // Uused for the arm pose
    public static boolean isHandFull(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && (tag.hasUUID(GRAB_TAG) || tag.hasUUID(CRUSH_TAG) || tag.getBoolean(ROCK_TAG));
    }

    private static boolean isHolding(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && (tag.hasUUID(GRAB_TAG) || tag.getBoolean(ROCK_TAG) && TelekinesisQuirk.isHoldingRock(entity));
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        double gained = getGained(instance);
        Modifiers.set(entity, Attributes.SCALE, GIGANT, gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ARMOR, GIGANT, CONFIG.armorPerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, GIGANT, CONFIG.damagePerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, GIGANT, -Math.min(0.95D, CONFIG.speedLostPerSize * gained), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        SizeReach.update(entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case KICK -> {
                if (isBigEnough(instance, entity)) {
                    kick(level, instance, entity);
                }
            }
            case GRAB -> {
                if (isHolding(instance, entity) || isBigEnough(instance, entity)) {
                    grab(level, instance, entity);
                }
            }
            case SWAT -> {
                if (isBigEnough(instance, entity)) {
                    swat(level, instance, entity);
                }
            }
        }
    }

    // Grow and Shrink
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (mode == CRUSH) {
            return crush(level, instance, entity, heldTicks);
        }

        if (mode != GROW) {
            return false;
        }

        double gained = getGained(instance);
        double growth = getGrowth(instance, entity);
        if (entity.isShiftKeyDown()) {
            if (gained <= 0.0D) {
                return false;
            }

            gained = Math.max(0.0D, gained - growth);
            setGained(instance, gained);
            update(instance, entity);
            if (heldTicks % GROW_EFFECT_TICKS == 0) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.5D), entity.getZ(), 3, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.02D);
            }
            return gained > 0.0D;
        }

        double max = getMaxGained(instance, entity);
        if (gained >= max - SAME) {
            if (heldTicks == 0) {
                fail(entity, Component.translatable("tracadamia.skill.gigantification.max_size"));
            }
            return false;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, GROW)) {
            return false;
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        gained = Math.min(max, gained + growth);
        setGained(instance, gained);
        update(instance, entity);
        if (heldTicks % GROW_EFFECT_TICKS == 0) {
            level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(), entity.getZ(), 4, entity.getBbWidth() * 0.5D, 0.1D, entity.getBbWidth() * 0.5D, 0.03D);
        }

        if (heldTicks % GROW_SOUND_TICKS == 0) {
            playSound(level, entity.position(), SoundEvents.RAVAGER_STEP, 1.5F, (float) Math.max(0.5D, 1.0D - gained * 0.08D));
        }

        if (gained >= max - SAME) {
            playSound(level, entity.position(), SoundEvents.ANVIL_LAND, 0.4F, 0.5F);
            return false;
        }

        return true;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (getGained(instance) > 0.0D) {
            instance.addMasteryPoint(entity);
        }

        if (isHolding(instance, entity) && GRABS.stream().noneMatch(grab -> grab.owner() == entity)) {
            release(instance, entity);
        }
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == CRUSH) {
            endCrush(instance, entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        release(instance, entity);
        endCrush(instance, entity);
        TelekinesisQuirk.dropRock(entity);
        WINDUPS.removeIf(windup -> windup.owner() == entity);
        lockMoving(entity, false);
        setGained(instance, 0.0D);
        update(instance, entity);
        SizeReach.update(entity, this);
    }

    // Kick and Stomp

    private static void kick(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (WINDUPS.stream().anyMatch(windup -> windup.owner() == entity)) {
            return;
        }

        boolean stomp = entity.isShiftKeyDown();
        if (stomp && !entity.onGround()) {
            fail(entity, Component.translatable("tracadamia.skill.gigantification.not_on_ground"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, KICK)) {
            return;
        }

        int windup = Math.max(1, CONFIG.kickWindup + (int) Math.round(CONFIG.kickWindupPerSize * getGained(instance)));
        CompoundTag tag = instance.getOrCreateTag();
        tag.putLong(KICK_TAG, level.getGameTime());
        tag.putInt(KICK_TICKS_TAG, windup);
        tag.putBoolean(STOMP_TAG, stomp);
        instance.markDirty();
        WINDUPS.add(new Windup(instance, entity, level.getGameTime() + windup, stomp));
        lockMoving(entity, !stomp);
        playSound(level, entity.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
        instance.setCoolDown(stomp ? CONFIG.stompCooldown : CONFIG.kickCooldown, KICK);
        instance.addMasteryPoint(entity);
    }

    public static boolean isKicking(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.contains(KICK_TAG) && !tag.getBoolean(STOMP_TAG) && entity.level().getGameTime() - tag.getLong(KICK_TAG) < tag.getInt(KICK_TICKS_TAG);
    }

    private static void lockMoving(LivingEntity entity, boolean locked) {
        if (entity instanceof Player) {
            return;
        }

        for (Holder<Attribute> attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }

            if (locked) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(KICK_LOCK, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else {
                instance.removeModifier(KICK_LOCK);
            }
        }
    }

    private static void landKick(ServerLevel level, ManasSkillInstance instance, LivingEntity owner) {
        double gained = getGained(instance);
        boolean mastered = instance.isMastered(owner);
        int kicked = kickWall(level, instance, owner, gained);

        List<LivingEntity> targets = getInFront(level, owner, getReach(instance, owner) * KICK_REACH, KICK_ARC, owner.getY() - 1.0D, owner.getY() + owner.getBbHeight() * 0.6D);
        if (!targets.isEmpty()) {
            float damage = (float) ((mastered ? CONFIG.kickSizeDamageMastered : CONFIG.kickSizeDamage) * gained + getMeleeDamage(owner) * (mastered ? CONFIG.kickDamagePercentMastered : CONFIG.kickDamagePercent));
            for (LivingEntity target : targets) {
                hit(instance, owner, target, damage, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK);
                Vec3 away = getAway(owner, target);
                target.setDeltaMovement(MultiArms.getLaunchVelocity(away.add(0.0D, 0.35D, 0.0D), CONFIG.kickKnockback + CONFIG.kickKnockbackPerSize * gained));
                target.hurtMarked = true;
                Vec3 point = target.getBoundingBox().getCenter();
                level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y, point.z, 0, away.x, 0.0D, away.z, 1.0D + gained * 0.3D);
            }

            Vec3 point = targets.get(0).getBoundingBox().getCenter();
            playSound(level, point, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.5F);
            playSound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.8F);
            return;
        }

        int count = Math.min(MAX_KICKED, kicked + CONFIG.kickBlocks + (int) Math.round(CONFIG.kickBlocksPerSize * gained));
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int width = Math.max(1, Mth.ceil(Math.sqrt(count - kicked)));
        double reach = owner.getBbWidth() * 0.5D + 1.0D;
        double speed = CONFIG.kickBlockSpeed / 20.0D;
        RandomSource random = level.random;
        for (int row = 0; row < width && kicked < count; row++) {
            for (int column = 0; column < width && kicked < count; column++) {
                Vec3 spot = owner.position().add(forward.scale(reach + row)).add(right.scale(column - (width - 1) * 0.5D));
                BlockPos surface = GroundBlocks.findSurface(level, BlockPos.containing(spot));
                if (surface == null || !canKick(level, instance, owner, surface)) {
                    continue;
                }

                BlockState state = level.getBlockState(surface);
                float hardness = Math.max(0.0F, state.getDestroySpeed(level, surface));
                TelekinesisBlockEntity block = takeBlock(level, instance, owner, surface, state);
                Vec3 from = Vec3.atCenterOf(surface).add(0.0D, 1.0D, 0.0D);
                block.setPos(from.x, from.y - 0.5D, from.z);
                Vec3 velocity = MultiArms.getThrowVelocity(owner, block, from, KICK_AIM_RANGE, speed).add((random.nextDouble() - 0.5D) * 0.2D, random.nextDouble() * 0.1D, (random.nextDouble() - 0.5D) * 0.2D);
                DEBRIS.add(new Debris(instance, owner, block, state, hardness, null, true, velocity));
                kicked++;
            }
        }

        level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.2D, owner.getZ(), 10, owner.getBbWidth() * 0.4D, 0.1D, owner.getBbWidth() * 0.4D, 0.05D);
        playSound(level, owner.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.5F, 0.6F);
    }

    private static boolean canKick(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getDestroySpeed(level, pos) <= CONFIG.kickMaxHardness && GroundBlocks.canBreak(level, instance, owner, pos);
    }

    private static int kickWall(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, double gained) {
        double width = CONFIG.kickWallWidth + CONFIG.kickWallPerSize * gained;
        double height = CONFIG.kickWallHeight + CONFIG.kickWallPerSize * gained;
        double depth = CONFIG.kickWallDepth + CONFIG.kickWallPerSize * 0.5D * gained;
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 front = owner.position().add(forward.scale(owner.getBbWidth() * 0.5D));
        Vec3 side = right.scale(width * 0.5D);
        Vec3 far = front.add(forward.scale(depth));
        AABB area = new AABB(front.add(side), front.subtract(side)).minmax(new AABB(far.add(side), far.subtract(side))).setMaxY(owner.getY() + height);

        List<BlockPos> wall = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
            Vec3 offset = Vec3.atCenterOf(pos).subtract(front);
            double along = offset.dot(forward);
            double up = pos.getY() + 0.5D - owner.getY();
            if (along >= 0.0D && along <= depth && Math.abs(offset.dot(right)) <= width * 0.5D && up >= 0.0D && up <= height && canKick(level, instance, owner, pos)) {
                wall.add(pos.immutable());
            }
        }

        if (wall.isEmpty()) {
            return 0;
        }

        wall.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(front)));
        double speed = CONFIG.kickBlockSpeed / 20.0D;
        RandomSource random = level.random;
        int flying = 0;
        for (BlockPos pos : wall.subList(0, Math.min(wall.size(), CONFIG.kickWallMaxBlocks))) {
            BlockState state = level.getBlockState(pos);
            Vec3 velocity = forward.scale(speed * (0.6D + random.nextDouble() * 0.4D)).add((random.nextDouble() - 0.5D) * 0.3D, 0.1D + random.nextDouble() * 0.2D, (random.nextDouble() - 0.5D) * 0.3D);
            if (flying < MAX_KICKED) {
                TelekinesisBlockEntity block = takeBlock(level, instance, owner, pos, state);
                DEBRIS.add(new Debris(instance, owner, block, state, Math.max(0.0F, state.getDestroySpeed(level, pos)), null, true, velocity));
                flying++;
            } else {
                level.removeBlock(pos, false);
                GroundBlocks.griefed(level, instance, owner, pos);
                GroundBlocks.dropBlock(level, pos, state, velocity.scale(0.5D));
            }
        }

        Vec3 point = front.add(forward.scale(0.5D)).add(0.0D, Math.min(height, owner.getBbHeight()) * 0.4D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y, point.z, 0, forward.x, 0.0D, forward.z, Math.max(1.0D, width / 3.0D));
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
        return flying;
    }

    private static void landStomp(ServerLevel level, ManasSkillInstance instance, LivingEntity owner) {
        double gained = getGained(instance);
        boolean mastered = instance.isMastered(owner);
        float damage = (float) ((mastered ? CONFIG.stompDamageMastered : CONFIG.stompDamage) * gained + getMeleeDamage(owner) * CONFIG.stompDamagePercent);
        double half = (CONFIG.stompSize + CONFIG.stompSizePerSize * gained) * 0.5D;
        double shock = half * CONFIG.stompShockwaveScale;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(shock, 3.0D, shock),
                target -> target != owner && target.isAlive() && !target.isSpectator() && !MultiArms.isHoldPair(owner, target))) {
            double dx = Math.abs(target.getX() - owner.getX());
            double dz = Math.abs(target.getZ() - owner.getZ());
            if (dx <= half && dz <= half) {
                hit(instance, owner, target, damage, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK);
                target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.6D, 0.0D));
            } else if (dx <= shock && dz <= shock) {
                hit(instance, owner, target, (float) (damage * CONFIG.stompShockwaveDamage), owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK);
                Vec3 away = new Vec3(target.getX() - owner.getX(), 0.0D, target.getZ() - owner.getZ()).normalize();
                target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.6D, 0.3D, away.z * 0.6D));
            } else {
                continue;
            }

            target.hurtMarked = true;
        }

        int count = Math.min(MAX_STOMPED, CONFIG.stompBlocks + (int) Math.round(CONFIG.stompBlocksPerSize * gained));
        RandomSource random = level.random;
        int reach = Mth.ceil(half);
        for (int tries = 0, lifted = 0; tries < count * 4 && lifted < count; tries++) {
            BlockPos surface = GroundBlocks.findSurface(level, owner.blockPosition().offset(random.nextInt(reach * 2 + 1) - reach, 0, random.nextInt(reach * 2 + 1) - reach));
            if (surface == null || surface.getY() >= owner.getY() || owner.getBoundingBox().inflate(0.5D).intersects(new AABB(surface.above())) || !GroundBlocks.canBreak(level, instance, owner, surface)) {
                continue;
            }

            BlockState state = level.getBlockState(surface);
            float hardness = Math.max(0.0F, state.getDestroySpeed(level, surface));
            TelekinesisBlockEntity block = takeBlock(level, instance, owner, surface, state);
            Vec3 velocity = new Vec3((random.nextDouble() - 0.5D) * 0.1D, 0.6D + random.nextDouble() * 0.4D, (random.nextDouble() - 0.5D) * 0.1D);
            DEBRIS.add(new Debris(instance, owner, block, state, hardness, surface, false, velocity));
            lifted++;
        }

        VibrateQuirk.shakeGround(level, owner.position(), half, 8);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, owner.getX(), owner.getY() + 0.1D, owner.getZ(), 0, 0.0D, 1.0D, 0.0D, half / 1.5D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, owner.getX(), owner.getY() + 0.1D, owner.getZ(), 0, 0.0D, 1.0D, 0.0D, shock / 1.5D);
        level.sendParticles(ParticleTypes.EXPLOSION, owner.getX(), owner.getY() + 0.2D, owner.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, owner.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.6F);
        playSound(level, owner.position(), SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
    }

    private static TelekinesisBlockEntity takeBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
        GroundBlocks.griefed(level, instance, owner, pos);
        level.levelEvent(2001, pos, Block.getId(state));
        TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, 1.0F, Vec3.atCenterOf(pos));
        level.addFreshEntity(block);
        return block;
    }

    // true once the block lands
    private static boolean tickDebris(Debris debris) {
        TelekinesisBlockEntity block = debris.block;
        if (block.isRemoved() || !(block.level() instanceof ServerLevel level)) {
            return true;
        }

        block.keepAlive();
        debris.velocity = debris.velocity.subtract(0.0D, DEBRIS_GRAVITY, 0.0D);
        Vec3 from = block.position().add(0.0D, block.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = from.add(debris.velocity);
        AABB sweep = block.getBoundingBox().expandTowards(debris.velocity).inflate(0.1D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != debris.owner && target.isAlive() && !target.isSpectator() && !debris.hit.contains(target))) {
            debris.hit.add(target);
            boolean mastered = debris.instance.isMastered(debris.owner);
            double damage = debris.kicked
                    ? debris.velocity.length() * 20.0D * (mastered ? CONFIG.kickSpeedDamageMastered : CONFIG.kickSpeedDamage) + debris.hardness * CONFIG.kickHardnessDamage
                    : debris.hardness * CONFIG.stompHardnessDamage;
            hit(debris.instance, debris.owner, target, (float) damage, DamageTypes.FALLING_BLOCK);
            target.push(debris.velocity.x * 0.3D, 0.1D, debris.velocity.z * 0.3D);
            target.hurtMarked = true;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris.state), target.getX(), target.getY(0.5D), target.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.1D);
            if (debris.kicked) {
                settle(level, debris, target.position());
                return true;
            }
        }

        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, block));
        boolean landed = hit.getType() == HitResult.Type.BLOCK && (debris.kicked || debris.velocity.y < 0.0D);
        if (landed || ++debris.age > DEBRIS_TICKS || to.y < level.getMinBuildHeight()) {
            settle(level, debris, landed ? hit.getLocation() : to);
            return true;
        }

        block.setPos(block.position().add(debris.velocity));
        return false;
    }

    private static void settle(ServerLevel level, Debris debris, Vec3 point) {
        if (debris.home != null && level.getBlockState(debris.home).isAir()) {
            level.setBlock(debris.home, debris.state, Block.UPDATE_ALL);
        } else {
            GroundBlocks.dropBlock(level, BlockPos.containing(point.x, point.y + 0.5D, point.z), debris.state, debris.velocity.scale(0.3D));
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris.state), point.x, point.y + 0.5D, point.z, 8, 0.3D, 0.2D, 0.3D, 0.1D);
        debris.block.discard();
    }

    // Grab

    private static void grab(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.hasUUID(GRAB_TAG)) {
            LivingEntity target = MultiArms.getHeldTarget(instance, entity, GRAB_TAG);
            release(instance, entity);
            if (target == null || entity.isShiftKeyDown()) {
                return;
            }

            throwTarget(level, instance, entity, target);
            return;
        }

        if (tag.getBoolean(ROCK_TAG) && TelekinesisQuirk.isHoldingRock(entity)) {
            tag.remove(ROCK_TAG);
            instance.markDirty();
            GRABS.removeIf(grab -> grab.owner() == entity);
            if (entity.isShiftKeyDown()) {
                TelekinesisQuirk.dropRock(entity);
                return;
            }

            TelekinesisQuirk.throwRock(level, entity, CONFIG.rockThrowRange, CONFIG.rockThrowSpeed);
            instance.setCoolDown(CONFIG.grabCooldown, GRAB);
            return;
        }

        LivingEntity target = MultiArms.getNearbyTarget(entity, getReach(instance, entity));
        if (target != null) {
            if (!MultiArms.canGrab(target)) {
                fail(entity, Component.translatable("tracadamia.skill.arms.boss"));
                return;
            }

            if (EnergyHelper.isOutOfEnergy(entity, instance, GRAB)) {
                return;
            }

            target.stopRiding();
            tag.putUUID(GRAB_TAG, target.getUUID());
            instance.markDirty();
            GRABS.add(new Grab(instance, entity));
            MultiArms.holdAt(entity, target, getHandOffset(entity, target, false), MultiArms.HOLD_BODY);
            entity.swing(InteractionHand.MAIN_HAND, true);
            playSound(level, entity.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.5F);
            instance.addMasteryPoint(entity);
            return;
        }

        int count = Math.min(TelekinesisBlockEntity.BOULDER_SHAPE.size(), CONFIG.rockBlocks + (int) Math.round(CONFIG.rockBlocksPerSize * getGained(instance)));
        if (!TelekinesisQuirk.liftRock(level, instance, entity, count, CONFIG.rockDamage, CONFIG.rockHardnessDamage, TelekinesisBlockEntity.HOLD_HAND)) {
            fail(entity, Component.translatable("tracadamia.skill.gigantification.no_rock"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, GRAB)) {
            TelekinesisQuirk.dropRock(entity);
            return;
        }

        tag.putBoolean(ROCK_TAG, true);
        instance.markDirty();
        GRABS.add(new Grab(instance, entity));
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, entity.position(), SoundEvents.ROOTED_DIRT_BREAK, 1.5F, 0.5F);
        instance.addMasteryPoint(entity);
    }

    private static Vec3 getHandOffset(LivingEntity owner, LivingEntity target, boolean squeezed) {
        double forward = Math.max(owner.getBbHeight() * TelekinesisBlockEntity.HAND_FORWARD, owner.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.1D);
        double up = owner.getBbHeight() * TelekinesisBlockEntity.HAND_HEIGHT - (squeezed ? target.getBbHeight() * 0.5D : 0.0D);
        return new Vec3(owner.getBbWidth() * TelekinesisBlockEntity.HAND_SIDE, Math.max(0.0D, up), forward);
    }

    private static void throwTarget(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {
        double distance = Math.min(CONFIG.throwMaxDistance, CONFIG.throwDistance + CONFIG.throwDistancePerSize * getGained(instance) + getMeleeDamage(owner) * CONFIG.throwDistancePercent);
        Vec3 look = owner.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, owner.getYRot()) : flat.normalize();
        double angle = Mth.clamp(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)), Math.toRadians(15.0D), Math.toRadians(45.0D));

        double reach = Math.max(1.0D, distance);
        double drop = Math.max(0.0D, target.getY() - owner.getY());
        double cos = Math.cos(angle);
        double speed = Math.sqrt(MultiArms.THROW_GRAVITY * reach * reach / (2.0D * cos * cos * (reach * Math.tan(angle) + drop)));
        Vec3 velocity = flat.scale(cos * speed).add(0.0D, Math.sin(angle) * speed, 0.0D);
        int ticks = Mth.ceil(reach / (cos * speed)) + 20;
        MultiArms.throwAt(instance, owner, target, velocity, (float) CONFIG.throwImpactDamage, GRAB, ticks);
        owner.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, owner.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.4F);
        instance.setCoolDown(CONFIG.grabCooldown, GRAB);
    }

    private static void release(ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, GRAB_TAG);
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(ROCK_TAG)) {
            tag.remove(ROCK_TAG);
            instance.markDirty();
        }
        GRABS.removeIf(grab -> grab.owner() == entity);
    }

    // true once nothing is held
    private static boolean tickGrab(Grab grab) {
        ManasSkillInstance instance = grab.instance();
        LivingEntity owner = grab.owner();
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(ROCK_TAG)) {
            if (TelekinesisQuirk.isHoldingRock(owner) && owner.isAlive()) {
                return false;
            }

            tag.remove(ROCK_TAG);
            instance.markDirty();
            return true;
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, owner, GRAB_TAG);
        if (!owner.isAlive() || owner.isRemoved() || target == null) {
            MultiArms.clearHeld(instance, owner, GRAB_TAG);
            instance.setCoolDown(CONFIG.grabCooldown, GRAB);
            return true;
        }

        MultiArms.holdAt(owner, target, getHandOffset(owner, target, false), MultiArms.HOLD_BODY);
        if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(owner);
        }

        return false;
    }

    // Swat

    // Swipes the hand in front
    private static void swat(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, SWAT)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double reach = getReach(instance, entity);
        float damage = (float) (getMeleeDamage(entity) * CONFIG.swatDamagePercent * getGained(instance) / CONFIG.swatSizeDivisor);
        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        for (LivingEntity target : getInFront(level, entity, reach, SWAT_ARC, entity.getY() - 1.0D, entity.getY() + entity.getBbHeight() + 1.0D)) {
            hit(instance, entity, target, damage, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, SWAT);
            Vec3 away = getAway(entity, target).add(right.scale(-0.3D)).normalize();
            target.setDeltaMovement(MultiArms.getLaunchVelocity(away.add(0.0D, 0.3D, 0.0D), mastered ? CONFIG.swatKnockbackMastered : CONFIG.swatKnockback));
            target.hurtMarked = true;
            Vec3 point = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 8, target.getBbWidth() * 0.3D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.3D, 0.2D);
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        Vec3 middle = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).add(forward.scale(reach * 0.6D));
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, middle.x, middle.y, middle.z, 3, reach * 0.3D, 0.1D, reach * 0.3D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, middle.x, middle.y, middle.z, 0, forward.x, 0.0D, forward.z, reach / 3.0D);
        playSound(level, middle, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.4F);
        playSound(level, middle, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.8F);
        instance.setCoolDown(CONFIG.swatCooldown, SWAT);
        instance.addMasteryPoint(entity);
    }

    private static List<LivingEntity> getInFront(ServerLevel level, LivingEntity owner, double reach, double arc, double minY, double maxY) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        AABB area = owner.getBoundingBox().inflate(reach, 0.0D, reach).setMinY(minY).setMaxY(maxY);
        return level.getEntitiesOfClass(LivingEntity.class, area, target -> target != owner && target.isAlive() && !target.isSpectator() && !MultiArms.isHoldPair(owner, target)).stream()
                .filter(target -> {
                    Vec3 flat = target.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
                    return flat.length() - target.getBbWidth() * 0.5D <= reach && (flat.lengthSqr() < 1.0E-4D || flat.normalize().dot(forward) >= arc);
                }).toList();
    }

    private static Vec3 getAway(LivingEntity owner, LivingEntity target) {
        Vec3 away = target.position().subtract(owner.position()).multiply(1.0D, 0.0D, 1.0D);
        return away.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, owner.getYRot()) : away.normalize();
    }

    // Crush
    // false once the crush is over
    private static boolean crush(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        if (heldTicks == 0) {
            if (!isBigEnough(instance, entity)) {
                return false;
            }

            LivingEntity target = MultiArms.getHeldTarget(instance, entity, GRAB_TAG);
            if (target == null) {
                target = MultiArms.getNearbyTarget(entity, getReach(instance, entity));
            }

            if (target == null) {
                fail(entity, Component.translatable("tensura.targeting.not_targeted"));
                return false;
            }

            if (!MultiArms.canGrab(target)) {
                fail(entity, Component.translatable("tracadamia.skill.arms.boss"));
                return false;
            }

            if (EnergyHelper.isOutOfEnergy(entity, instance, CRUSH)) {
                return false;
            }

            release(instance, entity);
            target.stopRiding();
            instance.getOrCreateTag().putUUID(CRUSH_TAG, target.getUUID());
            instance.markDirty();
            entity.swing(InteractionHand.MAIN_HAND, true);
            playSound(level, entity.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.5F);
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, entity, CRUSH_TAG);
        int ticks = (instance.isMastered(entity) ? CONFIG.crushSecondsMastered : CONFIG.crushSeconds) * 20;
        if (target == null || heldTicks >= ticks) {
            endCrush(instance, entity);
            return false;
        }

        MultiArms.holdAt(entity, target, getHandOffset(entity, target, true), MultiArms.HOLD_BODY, true);
        if ((heldTicks + 1) % 20 == 0) {
            hit(instance, entity, target, (float) (getMeleeDamage(entity) * CONFIG.crushDamagePercent * getGained(instance) / CONFIG.crushSizeDivisor), entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, CRUSH);
            Vec3 point = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 8, target.getBbWidth() * 0.3D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.3D, 0.1D);
            playSound(level, point, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
            playSound(level, point, SoundEvents.SNIFFER_EGG_CRACK, 1.0F, 0.6F);
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    private static void endCrush(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.hasUUID(CRUSH_TAG)) {
            MultiArms.clearHeld(instance, entity, CRUSH_TAG);
            instance.setCoolDown(CONFIG.crushCooldown, CRUSH);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!WINDUPS.isEmpty()) {
            List<Windup> ready = WINDUPS.stream().filter(windup -> !windup.owner().isAlive() || windup.owner().level().getGameTime() >= windup.landAt()).toList();
            WINDUPS.removeAll(ready);
            for (Windup windup : ready) {
                lockMoving(windup.owner(), false);
                if (windup.owner().isAlive() && windup.owner().level() instanceof ServerLevel level) {
                    if (windup.stomp()) {
                        landStomp(level, windup.instance(), windup.owner());
                    } else {
                        landKick(level, windup.instance(), windup.owner());
                    }
                }
            }
        }

        if (!GRABS.isEmpty()) {
            GRABS.removeIf(GigantificationQuirk::tickGrab);
        }

        if (!DEBRIS.isEmpty()) {
            DEBRIS.removeIf(GigantificationQuirk::tickDebris);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WINDUPS.clear();
        GRABS.clear();
        DEBRIS.clear();
    }

    private static double getReach(ManasSkillInstance instance, LivingEntity entity) {
        AttributeInstance reach = entity.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        return reach != null ? reach.getValue() : 3.0D + getGained(instance) * CONFIG.reachPerSize;
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, ResourceKey<DamageType> type) {
        hit(instance, owner, target, damage, type, KICK);
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, ResourceKey<DamageType> type, int mode) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, type, mode);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
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

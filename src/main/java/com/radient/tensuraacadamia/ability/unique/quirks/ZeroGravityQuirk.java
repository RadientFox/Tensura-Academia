package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.GravityClusterEntity;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.mixin.ServerGamePacketListenerAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
public class ZeroGravityQuirk extends Skill {

    private static final QuirkSkillsConfig.ZeroGravity CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ZeroGravity;

    private static final int ZERO_GRAVITY = 0;
    private static final int SELF_GRAVITY = 1;
    private static final int METEOR_SHOWER = 2;
    private static final int DEVASTATION = 3;
    private static final int[] MODE_ORDER = {ZERO_GRAVITY, SELF_GRAVITY, METEOR_SHOWER, DEVASTATION};

    public static final int ACTION_RISE = 0;
    public static final int ACTION_WIND = 1;

    private static final ResourceLocation FLOAT = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "zero_gravity");
    private static final ResourceLocation GRAVITY_BOOST = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "zero_gravity_boost");
    public static final double LIFT = 0.1D;
    public static final int LIFT_TICKS = 6;
    private static final double BLOCK_LIFT = 0.1D;
    private static final double BLOCK_LIFT_HEIGHT = 1.0D;
    private static final double STOP_SPEED = 0.02D;
    private static final int PUNCH_GRACE = 5;
    private static final double FALL_ACCELERATION = 0.08D;
    private static final double SHOWER_FALL_SPEED = 5.0D;
    private static final double METEOR_FALL_SPEED = 6.0D;
    private static final double GATHER_SPEED = 3.0D;
    private static final int GATHER_TICKS = 200;
    private static final int GATHER_LAYERS = 3;
    private static final float METEOR_SPIN = 1.5F;
    private static final double TARGET_RANGE = 64.0D;
    private static final double RAIN_SPREAD = 1.0D;
    private static final int RAIN_STAGGER = 2;
    private static final double SHOWER_HOMING = 1.5D;
    private static final double METEOR_HOMING = 1.0D;

    private static final List<Floating> FLOATING = new ArrayList<>();
    private static final List<Shower> SHOWERS = new ArrayList<>();
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final Map<LivingEntity, Entity> RAISING = new HashMap<>();
    private static final Set<UUID> WIND_THRUSTING = new HashSet<>();

    private static double clientWindThrust;
    private static @Nullable FloatPayload clientFloat;

    private static final class Floating {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final Entity target;
        private final long start;
        private final long until;
        private final float hardness;
        private final double liftY;
        private final boolean lifted;
        private Vec3 velocity = Vec3.ZERO;
        private Vec3 lastPos;
        private @Nullable LivingEntity puncher;
        private long punched = Long.MIN_VALUE / 2L;
        private @Nullable LivingEntity struck;
        private long struckAt = Long.MIN_VALUE / 2L;

        private Floating(ManasSkillInstance instance, LivingEntity owner, Entity target, long start, float hardness, boolean lifted) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.start = start;
            this.until = start + CONFIG.floatSeconds * 20L;
            this.hardness = hardness;
            this.lifted = lifted;
            this.lastPos = target.position();
            this.liftY = target.getY() + BLOCK_LIFT_HEIGHT;
        }
    }

    private static final class Rock {
        private final TelekinesisBlockEntity block;
        private final BlockState state;
        private final float hardness;
        private final double originY;
        private final Vec3 spread;
        private double peakY;
        private long topTime = -1L;
        private boolean falling;
        private long fallAt;
        private double fallSpeed;
        private boolean done;

        private Rock(TelekinesisBlockEntity block, BlockState state, float hardness, double originY, Vec3 spread) {
            this.block = block;
            this.state = state;
            this.hardness = hardness;
            this.originY = originY;
            this.spread = spread;
            this.peakY = originY;
        }
    }

    private static final class Shower {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final List<Rock> rocks;
        private final double riseSpeed;
        private final double hardness;
        private @Nullable LivingEntity target;

        private Shower(ManasSkillInstance instance, LivingEntity owner, List<Rock> rocks, double riseSpeed, double hardness, @Nullable LivingEntity target) {
            this.instance = instance;
            this.owner = owner;
            this.rocks = rocks;
            this.riseSpeed = riseSpeed;
            this.hardness = hardness;
            this.target = target;
        }

        private boolean isAloft() {
            return this.rocks.stream().anyMatch(rock -> !rock.done && !rock.falling);
        }

        private void release(long time) {
            int order = 0;
            for (Rock rock : this.rocks) {
                if (!rock.done && !rock.falling) {
                    rock.falling = true;
                    rock.fallAt = time + (long) order * RAIN_STAGGER;
                    order++;
                }
            }
        }
    }

    private record Part(BlockState state, float hardness) {}

    private static final class Meteor {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final GravityClusterEntity cluster;
        private final List<Part> parts;
        private final double radius;
        private final double originY;
        private final long formAt;
        private final double peakY;
        private Vec3 center;
        private long formedAt = -1L;
        private boolean falling;
        private double fallSpeed;
        private @Nullable LivingEntity target;

        private Meteor(ManasSkillInstance instance, LivingEntity owner, GravityClusterEntity cluster, List<Part> parts, double radius, double originY, Vec3 center, long formAt, @Nullable LivingEntity target) {
            this.instance = instance;
            this.owner = owner;
            this.cluster = cluster;
            this.parts = parts;
            this.radius = radius;
            this.originY = originY;
            this.center = center;
            this.formAt = formAt;
            this.peakY = center.y;
            this.target = target;
        }
    }

    public ZeroGravityQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ZERO_GRAVITY -> CONFIG.floatAuraCost;
            case SELF_GRAVITY -> CONFIG.selfAuraCost;
            case METEOR_SHOWER -> CONFIG.showerAuraCost;
            case DEVASTATION -> CONFIG.devastationAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODE_ORDER.length;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int index = 0;
        for (int i = 0; i < MODE_ORDER.length; i++) {
            if (MODE_ORDER[i] == mode) {
                index = i;
            }
        }

        return MODE_ORDER[Math.floorMod(index + (reverse ? -1 : 1), MODE_ORDER.length)];
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ZERO_GRAVITY -> "zero_gravity.zero_gravity";
            case SELF_GRAVITY -> "zero_gravity.self_gravity";
            case METEOR_SHOWER -> "zero_gravity.meteor_shower";
            case DEVASTATION -> "zero_gravity.devastation";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() || mode == ZERO_GRAVITY && getOwnAimedFloat(instance, entity) != null || super.canIgnoreCoolDown(instance, entity, mode);
    }

    private static @Nullable Entity getOwnAimedFloat(ManasSkillInstance instance, LivingEntity entity) {
        double range = instance.isMastered(entity) ? CONFIG.floatRangeMastered : CONFIG.floatRange;
        Entity aimed = ObjectSelectionHelper.getTargetingEntity(Entity.class, entity, range + 1.0D, 0.2D, false, true, false);
        return aimed != null && aimed.distanceTo(entity) <= range + aimed.getBbWidth() && isOwnFloat(entity, aimed) ? aimed : null;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case ZERO_GRAVITY -> zeroGravity(level, instance, entity);
            case SELF_GRAVITY -> selfGravity(instance, entity);
            case METEOR_SHOWER -> meteorShower(level, instance, entity);
            case DEVASTATION -> devastation(level, instance, entity);
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != ZERO_GRAVITY || entity.level().isClientSide) {
            return false;
        }

        Entity raised = RAISING.get(entity);
        if (raised == null || raised.isRemoved() || !isOwnFloat(entity, raised)) {
            RAISING.remove(entity);
            return false;
        }

        setRise(raised, CONFIG.raiseSpeed);
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == ZERO_GRAVITY) {
            Entity raised = RAISING.remove(entity);
            if (raised != null && !raised.isRemoved()) {
                setRise(raised, 0.0D);
            }
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        List.copyOf(FLOATING).stream().filter(floating -> floating.owner == entity).forEach(ZeroGravityQuirk::release);
        FLOATING.removeIf(floating -> floating.owner == entity);
        SHOWERS.stream().filter(shower -> shower.owner == entity).forEach(shower -> shower.release(entity.level().getGameTime()));
        METEORS.stream().filter(meteor -> meteor.owner == entity).forEach(meteor -> meteor.falling = true);
        RAISING.remove(entity);
        AdditiveBoost.apply(entity, TensuraAttributes.GRAVITY_BOOST, GRAVITY_BOOST, 0.0D);
    }

    private static Optional<ManasSkillInstance> getZeroGravity(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ZERO_GRAVITY.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static boolean isFloating(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(FLOAT);
    }

    public static FloatPayload getClientFloat() {
        return clientFloat != null ? clientFloat : FloatPayload.fromConfig();
    }

    public static double getClientWindThrust() {
        return clientWindThrust;
    }

    public static void clearClient() {
        clientWindThrust = 0.0D;
        clientFloat = null;
    }

    private static int getMaxFloats(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isMastered(entity) ? CONFIG.maxFloatsMastered : CONFIG.maxFloats;
    }

    private static long countFloats(LivingEntity owner) {
        return FLOATING.stream().filter(floating -> floating.owner == owner).count();
    }

    private static boolean isSkyBusy(LivingEntity owner) {
        return SHOWERS.stream().anyMatch(shower -> shower.owner == owner && shower.isAloft());
    }

    private static @Nullable Floating getFloating(Entity target) {
        return FLOATING.stream().filter(floating -> floating.target == target).findFirst().orElse(null);
    }

    private static boolean isOwnFloat(LivingEntity owner, Entity target) {
        Floating floating = getFloating(target);
        return floating != null && floating.owner == owner;
    }

    private static boolean canFloatMore(ManasSkillInstance instance, LivingEntity entity) {
        if (isSkyBusy(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.sky_busy"));
            return false;
        }

        int max = getMaxFloats(instance, entity);
        if (countFloats(entity) >= max) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.max_floats", max));
            return false;
        }

        return true;
    }

    private static @Nullable LivingEntity getLookTarget(LivingEntity entity) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, TARGET_RANGE, false, true);
        return target == null || target == entity ? null : target;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateGravityBoost(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateGravityBoost(entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || entity.getAttribute(TensuraAttributes.GRAVITY_BOOST) != null && entity.getAttribute(TensuraAttributes.GRAVITY_BOOST).hasModifier(GRAVITY_BOOST);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateGravityBoost(entity);
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

    private static void updateGravityBoost(LivingEntity entity) {
        boolean on = getZeroGravity(entity).filter(ManasSkillInstance::isToggled).isPresent();
        AdditiveBoost.apply(entity, TensuraAttributes.GRAVITY_BOOST, GRAVITY_BOOST, on ? CONFIG.gravityBoost : 0.0D);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updateGravityBoost(player);
            updateWindThrust(player);
        }
    }

    private static void updateWindThrust(ServerPlayer player) {
        boolean thrusting = isFloating(player) && !player.isShiftKeyDown() && isBreathingWind(player);
        if (thrusting == WIND_THRUSTING.contains(player.getUUID())) {
            return;
        }

        if (thrusting) {
            WIND_THRUSTING.add(player.getUUID());
        } else {
            WIND_THRUSTING.remove(player.getUUID());
        }

        PacketDistributor.sendToPlayer(player, new MotionPayload(ACTION_WIND, thrusting ? (float) CONFIG.windThrust : 0.0F));
    }

    private static boolean isBreathingWind(LivingEntity entity) {
        return TickingSkill.isTickingSkill(entity, ExtraSkills.WIND_MANIPULATION.get()) || TickingSkill.isTickingSkill(entity, ExtraSkills.WIND_DOMINATION.get());
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WIND_THRUSTING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        if (event.getSource().is(DamageTypeTags.IS_FALL) && getZeroGravity(entity).filter(ManasSkillInstance::isToggled).isPresent()) {
            event.setCanceled(true);
        }
    }

    private static void zeroGravity(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (entity.isShiftKeyDown()) {
            RAISING.remove(entity);
            List.copyOf(FLOATING).stream().filter(floating -> floating.owner == entity && floating.target != entity).forEach(floating -> {
                release(floating);
                FLOATING.remove(floating);
            });
            return;
        }

        Entity aimed = getOwnAimedFloat(instance, entity);
        if (aimed != null) {
            RAISING.put(entity, aimed);
            return;
        }

        if (!canFloatMore(instance, entity)) {
            return;
        }

        double range = instance.isMastered(entity) ? CONFIG.floatRangeMastered : CONFIG.floatRange;
        LivingEntity target = MultiArms.getTarget(entity, range);
        if (target != null && isFloating(target)) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.already_floating"));
            return;
        }

        BlockPos block = target == null ? getFloatableBlock(level, instance, entity, range) : null;
        if (target == null && block == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ZERO_GRAVITY)) {
            return;
        }

        if (target != null) {
            floatEntity(instance, entity, target);
        } else {
            floatBlock(level, instance, entity, block);
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.floatCooldown, ZERO_GRAVITY);
        instance.addMasteryPoint(entity);
    }

    private static void setRise(Entity target, double speed) {
        if (target instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new MotionPayload(ACTION_RISE, (float) speed));
            return;
        }

        if (target instanceof LivingEntity living) {
            Vec3 motion = living.getDeltaMovement();
            living.setDeltaMovement(motion.x, speed, motion.z);
            living.hurtMarked = true;
            return;
        }

        Floating floating = getFloating(target);
        if (floating != null) {
            floating.velocity = new Vec3(floating.velocity.x, speed, floating.velocity.z);
        }
    }

    private static @Nullable BlockPos getFloatableBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, double range) {
        BlockHitResult hit = ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, range + 1.0D);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        return canLift(level, instance, entity, pos) ? pos : null;
    }

    private static boolean canLift(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && state.getRenderShape() == RenderShape.MODEL
                && !state.getCollisionShape(level, pos).isEmpty() && GroundBlocks.canBreak(level, instance, entity, pos);
    }

    private static void floatEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {
        AttributeInstance gravity = target.getAttribute(Attributes.GRAVITY);
        if (gravity == null) {
            return;
        }

        gravity.addOrUpdateTransientModifier(new AttributeModifier(FLOAT, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        target.setDiscardFriction(true);
        target.resetFallDistance();
        boolean lifted = !(target instanceof Player) && target.onGround();
        if (lifted) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, Math.max(motion.y, 0.0D) + LIFT, motion.z);
            target.hurtMarked = true;
        }

        FLOATING.add(new Floating(instance, owner, target, target.level().getGameTime(), 0.0F, lifted));
        if (target instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, FloatPayload.fromConfig());
        }

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY(0.2D), target.getZ(), 20, target.getBbWidth() * 0.5D, 0.1D, target.getBbWidth() * 0.5D, 0.05D);
            playSound(level, target.position(), SoundEvents.SHULKER_BULLET_HIT, 1.0F, 0.8F);
        }
    }

    private static TelekinesisBlockEntity liftPunchable(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
        GroundBlocks.griefed(level, instance, owner, pos);
        TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, 1.0F, Vec3.atCenterOf(pos));
        block.setPunchable(true);
        level.addFreshEntity(block);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 12, 0.4D, 0.4D, 0.4D, 0.05D);
        return block;
    }

    private static void floatBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        TelekinesisBlockEntity block = liftPunchable(level, instance, owner, pos, state);
        block.noPhysics = false;
        FLOATING.add(new Floating(instance, owner, block, level.getGameTime(), hardness, false));
        playSound(level, Vec3.atCenterOf(pos), SoundEvents.SHULKER_BULLET_HIT, 1.0F, 0.8F);
    }

    private static void selfGravity(ManasSkillInstance instance, LivingEntity entity) {
        Floating self = FLOATING.stream().filter(floating -> floating.owner == entity && floating.target == entity).findFirst().orElse(null);
        if (entity.isShiftKeyDown()) {
            if (self != null) {
                release(self);
                FLOATING.remove(self);
            }
            return;
        }

        if (self != null || isFloating(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.already_floating"));
            return;
        }

        if (!canFloatMore(instance, entity) || EnergyHelper.isOutOfEnergy(entity, instance, SELF_GRAVITY)) {
            return;
        }

        floatEntity(instance, entity, entity);
        instance.setCoolDown(CONFIG.selfCooldown, SELF_GRAVITY);
        instance.addMasteryPoint(entity);
    }

    private static void release(Floating floating) {
        if (floating.target instanceof LivingEntity living) {
            AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
            if (gravity != null) {
                gravity.removeModifier(FLOAT);
            }

            living.setDiscardFriction(false);
            living.resetFallDistance();
            return;
        }

        if (floating.target instanceof TelekinesisBlockEntity block && !block.isRemoved() && block.level() instanceof ServerLevel level) {
            GroundBlocks.dropBlock(level, block.blockPosition(), block.getBlockState(), floating.velocity.scale(0.5D));
            block.discard();
        }
    }

    private static boolean tickFloating(Floating floating) {
        Entity target = floating.target;
        if (!(target.level() instanceof ServerLevel level)) {
            return true;
        }

        long time = level.getGameTime();
        if (target.isRemoved() || time >= floating.until || floating.owner.isRemoved() || !floating.owner.isAlive()) {
            release(floating);
            return true;
        }

        if (target instanceof LivingEntity living) {
            if (!isFloating(living)) {
                living.setDiscardFriction(false);
                return true;
            }

            living.setDiscardFriction(true);
            living.resetFallDistance();
            if (floating.lifted && time == floating.start + LIFT_TICKS && RAISING.get(floating.owner) != living) {
                Vec3 motion = living.getDeltaMovement();
                living.setDeltaMovement(motion.x, Math.max(0.0D, motion.y - LIFT), motion.z);
                living.hurtMarked = true;
            }

            if (living instanceof ServerPlayer player) {
                ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
            }

            if (time % 10L == 0L) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, living.getX(), living.getY(), living.getZ(), 2, living.getBbWidth() * 0.4D, 0.05D, living.getBbWidth() * 0.4D, 0.01D);
            }
            return false;
        }

        if (target instanceof TelekinesisBlockEntity block) {
            tickFloatingBlock(level, floating, block, time);
        }

        return false;
    }

    private static void tickFloatingBlock(ServerLevel level, Floating floating, TelekinesisBlockEntity block, long time) {
        block.keepAlive();
        Vec3 now = block.position();
        double speed = floating.velocity.length();
        boolean punched = floating.puncher != null;
        if (punched && speed > STOP_SPEED) {
            LivingEntity hit = findHit(level, floating, block, floating.lastPos, now, time);
            if (hit != null) {
                hitWithBlock(floating.instance, floating.puncher, block, hit, floating.velocity, (float) (floating.hardness * speed * 20.0D / 3.0D));
                floating.velocity = floating.velocity.scale(-0.2D);
                floating.struck = hit;
                floating.struckAt = time;
            } else if (block.horizontalCollision || block.verticalCollision) {
                floating.velocity = Vec3.ZERO;
                playSound(level, now, block.getBlockState().getSoundType().getHitSound(), 1.0F, 0.8F);
            } else {
                floating.velocity = floating.velocity.scale(CONFIG.blockDrag);
            }
        } else if (!punched && RAISING.get(floating.owner) != block) {
            double rise = block.verticalCollision ? 0.0D : Mth.clamp(floating.liftY - now.y, 0.0D, BLOCK_LIFT);
            floating.velocity = new Vec3(0.0D, rise, 0.0D);
        }

        TelekinesisBlockEntity.Punch punch = block.takePunch();
        if (punch != null) {
            floating.velocity = punchVelocity(punch);
            floating.puncher = punch.attacker();
            floating.punched = time;
            playSound(level, now, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.7F);
        }

        if (floating.velocity.length() <= STOP_SPEED && floating.puncher != null) {
            floating.velocity = Vec3.ZERO;
        }

        block.setDeltaMovement(floating.velocity);
        floating.lastPos = now;
    }

    private static Vec3 punchVelocity(TelekinesisBlockEntity.Punch punch) {
        double speed = Math.min(CONFIG.maxPunchSpeed, punch.amount() * CONFIG.punchSpeed);
        return punch.direction().normalize().scale(speed);
    }

    private static @Nullable LivingEntity findHit(ServerLevel level, Floating floating, TelekinesisBlockEntity block, Vec3 from, Vec3 to, long time) {
        AABB sweep = block.getBoundingBox().move(from.subtract(to)).expandTowards(to.subtract(from)).inflate(0.2D);
        boolean grace = time - floating.punched <= PUNCH_GRACE;
        boolean recoil = time - floating.struckAt <= PUNCH_GRACE * 2L;
        return level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target.isAlive() && !target.isSpectator() && target != floating.owner
                        && !(grace && target == floating.puncher) && !(recoil && target == floating.struck))
                .stream()
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(from)))
                .orElse(null);
    }

    private static void hitWithBlock(ManasSkillInstance instance, @Nullable LivingEntity attacker, TelekinesisBlockEntity block, LivingEntity target, Vec3 velocity, float damage) {
        DamageSource source = new DamageSource(target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.THROWN), block, attacker);
        ((TensuraDamageSource) source).tensura$setAbilityInstance(instance);
        ((TensuraDamageSource) source).tensura$setAbilityMode(ZERO_GRAVITY);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
        target.push(velocity.x * 0.5D, 0.1D, velocity.z * 0.5D);
        target.hurtMarked = true;
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.getBlockState()), target.getX(), target.getY(0.5D), target.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.1D);
            playSound(level, target.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
        }
    }

    private static void meteorShower(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Shower shower = SHOWERS.stream().filter(current -> current.owner == entity).findFirst().orElse(null);
        if (shower != null && shower.isAloft()) {
            LivingEntity target = getLookTarget(entity);
            if (target != null) {
                shower.target = target;
            }

            shower.release(level.getGameTime());
            playSound(level, entity.position(), SoundEvents.WITHER_SHOOT, 1.0F, 0.6F);
            return;
        }

        if (entity.isShiftKeyDown()) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.nothing_up"));
            return;
        }

        if (shower != null) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.shower_up"));
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double half = (mastered ? CONFIG.showerSizeMastered : CONFIG.showerSize) * 0.5D;
        List<BlockPos> picks = gatherBlocks(level, instance, entity, half, mastered ? CONFIG.showerBlocksMastered : CONFIG.showerBlocks, 1, true);
        if (picks.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.no_blocks"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, METEOR_SHOWER)) {
            return;
        }

        RandomSource random = entity.getRandom();
        List<Rock> rocks = new ArrayList<>();
        for (BlockPos pos : picks) {
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            TelekinesisBlockEntity block = liftBlock(level, instance, entity, pos, state);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * RAIN_SPREAD;
            rocks.add(new Rock(block, state, hardness, pos.getY(), new Vec3(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance)));
        }

        double speed = (mastered ? CONFIG.showerSpeedMastered : CONFIG.showerSpeed) / 20.0D;
        SHOWERS.add(new Shower(instance, entity, rocks, speed, mastered ? CONFIG.showerHardnessMastered : CONFIG.showerHardness, getLookTarget(entity)));
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, entity.position(), SoundEvents.SHULKER_BULLET_HIT, 1.5F, 0.5F);
        instance.addMasteryPoint(entity);
    }

    private static void takeBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos, BlockState state) {
        level.removeBlock(pos, false);
        GroundBlocks.griefed(level, instance, owner, pos);
        if (level.random.nextInt(3) == 0) {
            level.levelEvent(2001, pos, Block.getId(state));
        }
    }

    private static TelekinesisBlockEntity liftBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos pos, BlockState state) {
        takeBlock(level, instance, owner, pos, state);
        TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, 1.0F, Vec3.atCenterOf(pos));
        level.addFreshEntity(block);
        return block;
    }

    private static List<BlockPos> gatherBlocks(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, double half, int count, int layers, boolean nearest) {
        RandomSource random = entity.getRandom();
        AABB footprint = entity.getBoundingBox().inflate(0.5D, 1.0D, 0.5D);
        List<BlockPos> surfaces = new ArrayList<>();
        for (int x = Mth.floor(entity.getX() - half); x <= Mth.floor(entity.getX() + half); x++) {
            for (int z = Mth.floor(entity.getZ() - half); z <= Mth.floor(entity.getZ() + half); z++) {
                BlockPos surface = GroundBlocks.findSurface(level, new BlockPos(x, entity.getBlockY(), z));
                if (surface != null) {
                    surfaces.add(surface);
                }
            }
        }

        Vec3 feet = entity.position();
        List<BlockPos> picks = new ArrayList<>();
        for (int layer = 0; layer < layers && picks.size() < count; layer++) {
            List<BlockPos> row = new ArrayList<>();
            for (BlockPos surface : surfaces) {
                BlockPos pos = surface.below(layer);
                if (!footprint.intersects(new AABB(pos)) && canLift(level, instance, entity, pos)) {
                    row.add(pos);
                }
            }

            if (nearest) {
                row.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(feet)));
            } else {
                for (int i = row.size() - 1; i > 0; i--) {
                    int j = random.nextInt(i + 1);
                    BlockPos swap = row.get(i);
                    row.set(i, row.get(j));
                    row.set(j, swap);
                }
            }

            for (int i = 0; i < row.size() && picks.size() < count; i++) {
                picks.add(row.get(i));
            }
        }

        return picks;
    }

    private static boolean tickShower(Shower shower) {
        long time = shower.owner.level().getGameTime();
        if (shower.owner.isRemoved() || !shower.owner.isAlive()) {
            shower.release(time);
        }

        if (shower.target != null && (!shower.target.isAlive() || shower.target.isRemoved())) {
            shower.target = null;
        }

        for (Rock rock : shower.rocks) {
            if (rock.done) {
                continue;
            }

            TelekinesisBlockEntity block = rock.block;
            if (block.isRemoved() || !(block.level() instanceof ServerLevel level)) {
                rock.done = true;
                continue;
            }

            block.keepAlive();
            if (!rock.falling) {
                double top = rock.originY + CONFIG.showerHeight;
                double step = Math.min(shower.riseSpeed, top - block.getY());
                if (step > 1.0E-3D) {
                    block.setPos(block.getX(), block.getY() + step, block.getZ());
                } else if (rock.topTime < 0L) {
                    rock.topTime = time;
                } else if (time - rock.topTime >= CONFIG.showerHoldSeconds * 20L) {
                    rock.falling = true;
                    rock.fallAt = time;
                }

                rock.peakY = Math.max(rock.peakY, block.getY());
                continue;
            }

            if (time < rock.fallAt) {
                continue;
            }

            Vec3 drift = Vec3.ZERO;
            if (shower.target != null) {
                Vec3 goal = shower.target.position().add(rock.spread);
                Vec3 offset = new Vec3(goal.x - block.getX(), 0.0D, goal.z - block.getZ());
                drift = offset.length() > SHOWER_HOMING ? offset.normalize().scale(SHOWER_HOMING) : offset;
            }

            rock.fallSpeed = Math.min(rock.fallSpeed + FALL_ACCELERATION, SHOWER_FALL_SPEED);
            Vec3 move = new Vec3(drift.x, -rock.fallSpeed, drift.z);
            Vec3 from = block.position().add(0.0D, block.getBbHeight() * 0.5D, 0.0D);
            Vec3 to = from.add(move).subtract(0.0D, block.getBbHeight() * 0.5D, 0.0D);
            LivingEntity direct = level.getEntitiesOfClass(LivingEntity.class, block.getBoundingBox().expandTowards(move),
                    target -> target != shower.owner && target.isAlive() && !target.isSpectator()).stream().findFirst().orElse(null);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, block));
            if (direct != null || hit.getType() == HitResult.Type.BLOCK || to.y < level.getMinBuildHeight()) {
                Vec3 point = direct != null ? direct.position() : hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
                landRock(level, shower, rock, point, direct);
                continue;
            }

            block.setPos(block.position().add(move));
        }

        if (shower.rocks.stream().allMatch(rock -> rock.done)) {
            shower.instance.setCoolDown(CONFIG.showerCooldown, METEOR_SHOWER);
            return true;
        }

        return false;
    }

    private static void landRock(ServerLevel level, Shower shower, Rock rock, Vec3 point, @Nullable LivingEntity direct) {
        rock.done = true;
        double height = Math.max(0.0D, rock.peakY - rock.originY);
        float damage = (float) ((height / 2.0D + Math.max(0.0F, rock.hardness) * shower.hardness) * CONFIG.showerDamageScale);
        double radius = Math.max(1.0D, height / 100.0D);

        if (direct != null) {
            hurt(shower.instance, shower.owner, direct, DamageTypes.FALLING_BLOCK, METEOR_SHOWER, damage, false);
        }

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(radius),
                target -> target != shower.owner && target != direct && target.isAlive() && target.position().distanceTo(point) <= radius + target.getBbWidth() * 0.5D)) {
            hurt(shower.instance, shower.owner, target, DamageTypes.EXPLOSION, METEOR_SHOWER, damage * 0.5F, false);
            Vec3 away = target.position().subtract(point).multiply(1.0D, 0.0D, 1.0D);
            target.push(away.x * 0.3D, 0.3D, away.z * 0.3D);
            target.hurtMarked = true;
        }

        groundShockwave(level, point, radius);
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y + 0.5D, point.z, Math.max(1, (int) radius), radius * 0.4D, 0.2D, radius * 0.4D, 0.0D);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock.state), point.x, point.y + 0.5D, point.z, 20, 0.5D, 0.3D, 0.5D, 0.15D);
        playSound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.2F + level.random.nextFloat() * 0.3F);
        GroundBlocks.dropBlock(level, BlockPos.containing(point.x, point.y + 0.5D, point.z), rock.state, new Vec3(0.0D, 0.2D, 0.0D));
        rock.block.discard();
    }

    private static void groundShockwave(ServerLevel level, Vec3 point, double radius) {
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y + 0.1D, point.z, 0, 0.0D, 1.0D, 0.0D, radius / 1.5D);
    }

    private static void devastation(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Meteor meteor = METEORS.stream().filter(current -> current.owner == entity).findFirst().orElse(null);
        if (meteor != null && !meteor.falling) {
            LivingEntity target = getLookTarget(entity);
            if (target != null) {
                meteor.target = target;
            }

            meteor.falling = true;
            playSound(level, entity.position(), SoundEvents.WITHER_SHOOT, 1.5F, 0.4F);
            return;
        }

        if (entity.isShiftKeyDown()) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.nothing_up"));
            return;
        }

        if (meteor != null) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.meteor_up"));
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double half = (mastered ? CONFIG.devastationSizeMastered : CONFIG.devastationSize) * 0.5D;
        List<BlockPos> picks = gatherBlocks(level, instance, entity, half, mastered ? CONFIG.devastationBlocksMastered : CONFIG.devastationBlocks, GATHER_LAYERS, false);
        if (picks.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.zero_gravity.no_blocks"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, DEVASTATION)) {
            return;
        }

        Vec3 center = entity.position().add(0.0D, CONFIG.devastationHeight, 0.0D);
        List<Vec3> slots = getSphereSlots(picks.size());
        List<GravityClusterEntity.Part> shapes = new ArrayList<>();
        List<Part> parts = new ArrayList<>();
        double originY = 0.0D;
        double farthest = 0.0D;
        for (int i = 0; i < picks.size(); i++) {
            BlockPos pos = picks.get(i);
            BlockState state = level.getBlockState(pos);
            parts.add(new Part(state, state.getDestroySpeed(level, pos)));
            takeBlock(level, instance, entity, pos, state);
            Vec3 origin = Vec3.atCenterOf(pos);
            shapes.add(new GravityClusterEntity.Part(state, slots.get(i), origin));
            farthest = Math.max(farthest, origin.distanceTo(center.add(slots.get(i))));
            originY += pos.getY();
        }

        long time = level.getGameTime();
        GravityClusterEntity cluster = GravityClusterEntity.create(level, center, shapes);
        cluster.setGather(time, (float) GATHER_SPEED);
        level.addFreshEntity(cluster);

        double radius = slots.stream().mapToDouble(Vec3::length).max().orElse(0.0D) + 0.5D;
        long formAt = time + Math.min(GATHER_TICKS, Mth.ceil(farthest / GATHER_SPEED));
        METEORS.add(new Meteor(instance, entity, cluster, parts, radius, originY / picks.size(), center, formAt, getLookTarget(entity)));
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, entity.position(), SoundEvents.WITHER_SPAWN, 1.0F, 0.6F);
        instance.addMasteryPoint(entity);
    }

    private static List<Vec3> getSphereSlots(int count) {
        List<Vec3> slots = new ArrayList<>();
        for (int radius = 1; slots.size() < count; radius++) {
            slots.clear();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z <= radius * radius) {
                            slots.add(new Vec3(x, y, z));
                        }
                    }
                }
            }
        }

        slots.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        return new ArrayList<>(slots.subList(0, count));
    }

    private static boolean tickMeteor(Meteor meteor) {
        GravityClusterEntity cluster = meteor.cluster;
        if (cluster.isRemoved() || !(cluster.level() instanceof ServerLevel level)) {
            return true;
        }

        long time = level.getGameTime();
        if (meteor.owner.isRemoved() || !meteor.owner.isAlive()) {
            meteor.falling = true;
        }

        if (meteor.target != null && (!meteor.target.isAlive() || meteor.target.isRemoved())) {
            meteor.target = null;
        }

        cluster.keepAlive();
        if (meteor.formedAt < 0L) {
            if (time >= meteor.formAt || meteor.falling) {
                meteor.formedAt = time;
                cluster.setFormed();
                cluster.setSpin(METEOR_SPIN);
            }
            return false;
        }

        if (!meteor.falling) {
            if (time - meteor.formedAt >= CONFIG.devastationHoldSeconds * 20L) {
                meteor.falling = true;
            }
            return false;
        }

        Vec3 drift = Vec3.ZERO;
        if (meteor.target != null) {
            Vec3 offset = new Vec3(meteor.target.getX() - meteor.center.x, 0.0D, meteor.target.getZ() - meteor.center.z);
            drift = offset.length() > METEOR_HOMING ? offset.normalize().scale(METEOR_HOMING) : offset;
        }

        meteor.fallSpeed = Math.min(meteor.fallSpeed + FALL_ACCELERATION, METEOR_FALL_SPEED);
        Vec3 move = new Vec3(drift.x, -meteor.fallSpeed, drift.z);
        Vec3 bottom = meteor.center.subtract(0.0D, meteor.radius, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(meteor.center, bottom.add(move), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, cluster));
        LivingEntity direct = level.getEntitiesOfClass(LivingEntity.class, new AABB(meteor.center, meteor.center).inflate(meteor.radius).expandTowards(move),
                target -> target != meteor.owner && target.isAlive() && !target.isSpectator() && target.getBoundingBox().getCenter().distanceTo(meteor.center) <= meteor.radius + meteor.fallSpeed).stream().findFirst().orElse(null);
        if (hit.getType() == HitResult.Type.BLOCK || direct != null || bottom.y + move.y < level.getMinBuildHeight()) {
            Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : direct != null ? direct.position() : bottom.add(move);
            landMeteor(level, meteor, point);
            return true;
        }

        meteor.center = meteor.center.add(move);
        cluster.setPos(meteor.center);
        if (time % 4L == 0L) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, meteor.center.x, meteor.center.y + meteor.radius, meteor.center.z, 6, meteor.radius * 0.5D, 0.5D, meteor.radius * 0.5D, 0.02D);
        }
        return false;
    }

    private static void landMeteor(ServerLevel level, Meteor meteor, Vec3 point) {
        double height = Math.max(0.0D, meteor.peakY - meteor.originY);
        double hardness = meteor.parts.stream().mapToDouble(part -> Math.max(0.0F, part.hardness())).sum();
        float damage = (float) ((height / 2.0D + hardness * CONFIG.devastationHardness) * CONFIG.devastationDamageScale);
        double crater = meteor.radius * CONFIG.devastationCraterScale;
        double reach = crater + CONFIG.devastationShockwaveRadius;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(reach), target -> target != meteor.owner && target.isAlive())) {
            double distance = target.getBoundingBox().getCenter().distanceTo(point) - target.getBbWidth() * 0.5D;
            if (distance <= crater) {
                hurt(meteor.instance, meteor.owner, target, TensuraDamageTypes.EARTH_ELEMENTAL, DEVASTATION, damage, true);
            } else if (distance <= reach) {
                hurt(meteor.instance, meteor.owner, target, TensuraDamageTypes.EARTH_ELEMENTAL, DEVASTATION, (float) (damage * CONFIG.devastationShockwaveDamage), true);
                Vec3 away = target.position().subtract(point).multiply(1.0D, 0.0D, 1.0D).normalize();
                target.push(away.x * 1.5D, 0.6D, away.z * 1.5D);
                target.hurtMarked = true;
            }
        }

//        if (CONFIG.devastationBreaksBlocks) { // old config area
//            digCrater(level, meteor, point, crater);
//        }
        
        digCrater(level, meteor, point, crater);

        RandomSource random = level.random;
        BlockPos middle = BlockPos.containing(point.x, point.y + 1.0D, point.z);
        for (Part part : meteor.parts) {
            if (random.nextInt(3) == 0) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double spread = 0.3D + random.nextDouble() * 0.6D;
                GroundBlocks.dropBlock(level, middle, part.state(), new Vec3(Math.cos(angle) * spread, 0.5D + random.nextDouble() * 0.6D, Math.sin(angle) * spread));
            }
        }

        meteor.cluster.discard();
        groundShockwave(level, point, crater);
        groundShockwave(level, point, reach);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, point.x, point.y + 1.0D, point.z, 3, crater * 0.3D, 0.5D, crater * 0.3D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, point.x, point.y + 1.0D, point.z, 60, crater, 1.0D, crater, 0.05D);
        playSound(level, point, SoundEvents.GENERIC_EXPLODE.value(), 6.0F, 0.5F);
        playSound(level, point, SoundEvents.MACE_SMASH_GROUND_HEAVY, 6.0F, 0.5F);
        meteor.instance.setCoolDown(CONFIG.devastationCooldown, DEVASTATION);
    }

    private static void digCrater(ServerLevel level, Meteor meteor, Vec3 point, double crater) {
        int reach = Mth.ceil(crater);
        BlockPos middle = BlockPos.containing(point);
        for (int x = -reach; x <= reach; x++) {
            for (int y = -reach; y <= reach; y++) {
                for (int z = -reach; z <= reach; z++) {
                    if (x * x + y * y + z * z > crater * crater) {
                        continue;
                    }

                    BlockPos pos = middle.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && GroundBlocks.canBreak(level, meteor.instance, meteor.owner, pos)) {
                        level.setBlock(pos, state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
                        GroundBlocks.griefed(level, meteor.instance, meteor.owner, pos);
                    }
                }
            }
        }
    }

    private static void hurt(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, ResourceKey<DamageType> type, int mode, float damage, boolean earth) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, type, mode);
        if (earth) {
            ((TensuraDamageSource) source).tensura$setElement(Element.EARTH);
        }

        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FLOATING.isEmpty()) {
            FLOATING.removeIf(ZeroGravityQuirk::tickFloating);
        }

        if (!SHOWERS.isEmpty()) {
            SHOWERS.removeIf(ZeroGravityQuirk::tickShower);
        }

        if (!METEORS.isEmpty()) {
            METEORS.removeIf(ZeroGravityQuirk::tickMeteor);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FLOATING.forEach(ZeroGravityQuirk::release);
        FLOATING.clear();
        SHOWERS.clear();
        METEORS.clear();
        RAISING.clear();
        WIND_THRUSTING.clear();
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

    public record MotionPayload(int action, float value) implements CustomPacketPayload {
        public static final Type<MotionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "zero_gravity_motion"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MotionPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MotionPayload::action,
                ByteBufCodecs.FLOAT, MotionPayload::value,
                MotionPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FloatPayload(float acceleration, float dragPercent, float dragLimit, float windMaxSpeed, float maxSpeed) implements CustomPacketPayload {
        public static final Type<FloatPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "zero_gravity_float"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FloatPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, FloatPayload::acceleration,
                ByteBufCodecs.FLOAT, FloatPayload::dragPercent,
                ByteBufCodecs.FLOAT, FloatPayload::dragLimit,
                ByteBufCodecs.FLOAT, FloatPayload::windMaxSpeed,
                ByteBufCodecs.FLOAT, FloatPayload::maxSpeed,
                FloatPayload::new
        );

        public static FloatPayload fromConfig() {
            return new FloatPayload((float) CONFIG.floatAcceleration, (float) CONFIG.floatDragPercent, (float) CONFIG.floatDragLimit, (float) CONFIG.windMaxSpeed, (float) CONFIG.maxPlayerSpeed);
        }

        public double moveMaxSpeed() {
            return this.dragPercent > 0.0F ? this.acceleration / this.dragPercent : this.maxSpeed;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(FloatPayload.TYPE, FloatPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientFloat = payload));
        event.registrar("1").playToClient(MotionPayload.TYPE, MotionPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.action() == ACTION_WIND) {
                clientWindThrust = payload.value();
                return;
            }

            Player player = context.player();
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, payload.value(), motion.z);
        }));
    }

}

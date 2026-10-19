package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.subclass.ISpatialMovement;
import io.github.manasmods.tensura.entity.magic.barrier.BarrierEntity;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WarpingQuirk extends Skill {

    private static final QuirkSkillsConfig.Warping CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Warping;

    private static final int WARPING = 0;
    private static final int GOOP_DEFENSE = 1;
    private static final int EMERGENCY_EXIT = 2;
    private static final int MODES = 3;

    public static final int WHEEL_WARP = 0;
    public static final int WHEEL_GOOP = 1;
    public static final int WHEEL_EMERGENCY = 2;

    public static final int SELF = 0;
    public static final int PLAYER = 1;
    public static final int CREATURE = 2;

    private static final String GOOP_TAG = "goopDefense";
    private static final String GOOP_TARGET_TAG = "goopTarget";
    private static final String EMERGENCY_TAG = "emergencyExit";
    private static final String EMERGENCY_TARGET_TAG = "emergencyAlly";
    private static final String GOOPED_TAG = "tracadamiaGooped";
    private static final double MIN_GOOP_SPEED = 0.1D;

    private static final ResourceLocation SPACE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "warping_space_boost");
    private static final DustParticleOptions GOOP = new DustParticleOptions(new Vector3f(0.2F, 0.2F, 0.22F), 1.6F);
    private static final DustParticleOptions GOOP_EDGE = new DustParticleOptions(new Vector3f(0.55F, 0.55F, 0.58F), 1.0F);

    private static final double SPILL = 0.35D;
    private static final double EXIT_DISTANCE = 4.0D;

    // Warp in progress
    private record Gate(ManasSkillInstance instance, ServerPlayer user, LivingEntity subject, LivingEntity destination, long start, int ticks) {}

    private static final List<Gate> GATES = new ArrayList<>();

    private static @Nullable WheelPayload clientWheel;

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/warping.png");
    }

    public WarpingQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case WARPING -> CONFIG.warpAuraCost;
            case GOOP_DEFENSE -> CONFIG.goopAuraCost;
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
            case WARPING -> "warping.warping";
            case GOOP_DEFENSE -> "warping.goop_defense";
            case EMERGENCY_EXIT -> "warping.emergency_exit";
            default -> super.getModeId(instance, mode);
        };
    }

    // Just lets you choose an emergency ally or turn the mode on/off while the move is on cooldown
    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == GOOP_DEFENSE || mode == EMERGENCY_EXIT || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getWarping(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WARPING.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case WARPING -> {
                if (entity instanceof ServerPlayer player) {
                    openWheel(player, WHEEL_WARP, null);
                }
            }
            case GOOP_DEFENSE -> {
                if (entity.isShiftKeyDown() && entity instanceof ServerPlayer player) {
                    openWheel(player, WHEEL_GOOP, getTarget(instance, GOOP_TARGET_TAG));
                } else {
                    toggleGoop(level, instance, entity);
                }
            }
            case EMERGENCY_EXIT -> {
                if (entity.isShiftKeyDown()) {
                    toggleEmergency(level, instance, entity);
                } else if (entity instanceof ServerPlayer player) {
                    openWheel(player, WHEEL_EMERGENCY, getTarget(instance, EMERGENCY_TARGET_TAG));
                }
            }
        }
    }

    // Spatial Boost, Passive Toggle

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateSpace(entity, true);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateSpace(entity, false);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateSpace(entity, instance.isToggled());
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
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        updateSpace(entity, false);
    }

    private static void updateSpace(LivingEntity entity, boolean on) {
        if (!entity.level().isClientSide) {
            AdditiveBoost.apply(entity, TensuraAttributes.SPACE_BOOST, SPACE, on ? CONFIG.spaceBoost : 0.0D);
        }
    }

    // Player allies and nearby entity allies

    private static boolean isAlly(LivingEntity owner, LivingEntity target) {
        return owner.isAlliedTo(target) || target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()) || SubordinateHelper.isAlly(owner, target);
    }

    private static boolean isUsable(LivingEntity entity) {
        return entity.isAlive() && !entity.isRemoved() && !entity.isSpectator();
    }

    private static List<LivingEntity> getAllies(LivingEntity user, boolean otherWorlds) {
        List<LivingEntity> allies = new ArrayList<>();
        MinecraftServer server = user.getServer();
        if (server == null) {
            return allies;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (!otherWorlds && level != user.level()) {
                continue;
            }

            for (ServerPlayer player : level.players()) {
                if (player != user && isUsable(player) && isAlly(user, player)) {
                    allies.add(player);
                }
            }
        }

        double range = CONFIG.allyRange;
        allies.addAll(user.level().getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(range),
                other -> !(other instanceof Player) && other != user && isUsable(other) && isAlly(user, other) && other.distanceTo(user) <= range));
        return allies;
    }

    private static @Nullable LivingEntity findAlly(LivingEntity user, UUID id, boolean anyRange) {
        if (user.getUUID().equals(id)) {
            return user;
        }

        MinecraftServer server = user.getServer();
        if (server == null) {
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity ally && isUsable(ally) && isAlly(user, ally)) {
                return ally instanceof Player || anyRange || ally.level() == user.level() && ally.distanceTo(user) <= CONFIG.allyRange ? ally : null;
            }
        }
        return null;
    }

    private static @Nullable UUID getTarget(ManasSkillInstance instance, String key) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    private static void setTarget(ManasSkillInstance instance, LivingEntity entity, String key, @Nullable UUID id) {
        if (id == null) {
            instance.getOrCreateTag().remove(key);
        } else {
            instance.getOrCreateTag().putUUID(key, id);
        }
        instance.markDirty();
        SkillAPI.getSkillsFrom(entity).markDirty();
    }

    // Warping wheel
    // its like the main ability
    private static void openWheel(ServerPlayer player, int wheel, @Nullable UUID chosen) {
        List<LivingEntity> allies = getAllies(player, true);
        if (allies.isEmpty()) {
            fail(player, Component.translatable("tracadamia.skill.warping.no_allies"));
            return;
        }

        List<WheelEntry> entries = new ArrayList<>();
        if (wheel != WHEEL_EMERGENCY) {
            entries.add(new WheelEntry(player.getUUID(), player.getId(), player.getGameProfile().getName(), SELF));
        }
        for (LivingEntity ally : allies) {
            entries.add(new WheelEntry(ally.getUUID(), ally.level() == player.level() ? ally.getId() : -1, ally.getName().getString(), ally instanceof Player ? PLAYER : CREATURE));
        }

        PacketDistributor.sendToPlayer(player, new WheelPayload(wheel, entries, Optional.ofNullable(chosen)));
        player.playNotifySound(TensuraSoundEvents.SPATIAL_STORAGE.get(), SoundSource.PLAYERS, 0.75F, 1.2F);
    }

    private static void choose(ServerPlayer player, WheelChoicePayload choice) {
        ManasSkillInstance instance = getWarping(player).orElse(null);
        if (instance == null || !player.isAlive()) {
            return;
        }

        switch (choice.wheel()) {
            case WHEEL_WARP -> warp(player, instance, choice.first(), choice.second());
            case WHEEL_GOOP -> {
                LivingEntity ally = findAlly(player, choice.first(), true);
                if (ally == null) {
                    fail(player, Component.translatable("tracadamia.skill.warping.ally_gone"));
                } else if (ally == player) {
                    setTarget(instance, player, GOOP_TARGET_TAG, null);
                    message(player, Component.translatable("tracadamia.skill.warping.goop_random"));
                } else {
                    setTarget(instance, player, GOOP_TARGET_TAG, ally.getUUID());
                    message(player, Component.translatable("tracadamia.skill.warping.goop_target", ally.getDisplayName()));
                }
            }
            case WHEEL_EMERGENCY -> {
                LivingEntity ally = findAlly(player, choice.first(), true);
                if (ally == null || ally == player) {
                    fail(player, Component.translatable("tracadamia.skill.warping.ally_gone"));
                    return;
                }

                setTarget(instance, player, EMERGENCY_TARGET_TAG, ally.getUUID());
                message(player, Component.translatable(isEmergencyOn(instance) ? "tracadamia.skill.warping.emergency_target" : "tracadamia.skill.warping.emergency_target_off", ally.getDisplayName()));
            }
        }
    }

    private static void warp(ServerPlayer player, ManasSkillInstance instance, UUID first, UUID second) {
        if (instance.onCoolDown(WARPING)) {
            return;
        }

        LivingEntity subject = findAlly(player, first, false);
        LivingEntity destination = findAlly(player, second, false);
        if (subject == null || destination == null || subject == destination) {
            fail(player, Component.translatable("tracadamia.skill.warping.ally_gone"));
            return;
        }

        if (GATES.stream().anyMatch(gate -> gate.user() == player) || EnergyHelper.isOutOfEnergy(player, instance, WARPING)) {
            return;
        }

        GATES.removeIf(gate -> gate.subject() == subject);
        GATES.add(new Gate(instance, player, subject, destination, player.level().getGameTime(), Math.max(1, (int) Math.round(CONFIG.warpActivationSeconds * 20.0D))));
        playSound((ServerLevel) subject.level(), subject.getEyePosition(), SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.5F);
    }

    private static Vec3 getMouth(LivingEntity entity) {
        return entity.getEyePosition().add(0.0D, -entity.getBbHeight() * 0.08D, 0.0D).add(entity.getLookAngle().scale(entity.getBbWidth() * 0.5D));
    }

    // Starts the goop from the mouth
    private static void cover(ServerLevel level, LivingEntity subject, double progress) {
        RandomSource random = level.random;
        Vec3 mouth = getMouth(subject);
        level.sendParticles(GOOP, mouth.x, mouth.y, mouth.z, progress < SPILL ? 3 + (int) (progress / SPILL * 6.0D) : 1, 0.06D, 0.06D, 0.06D, 0.0D);
        if (progress < SPILL) {
            level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 1, 0.04D, 0.04D, 0.04D, 0.005D);
            return;
        }

        AABB box = subject.getBoundingBox();
        Vec3 center = box.getCenter();
        double covered = Math.min(1.0D, (progress - SPILL) / (1.0D - SPILL));
        double top = box.maxY;
        double bottom = top - box.getYsize() * covered;
        double radiusX = box.getXsize() * 0.5D + 0.05D;
        double radiusZ = box.getZsize() * 0.5D + 0.05D;
        int count = 10 + (int) (covered * 40.0D);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double y = bottom + random.nextDouble() * (top - bottom);
            level.sendParticles(i % 3 == 0 ? GOOP_EDGE : GOOP, center.x + Math.cos(angle) * radiusX, y, center.z + Math.sin(angle) * radiusZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // This is the goop going downward
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            level.sendParticles(ParticleTypes.SMOKE, center.x + Math.cos(angle) * radiusX, bottom, center.z + Math.sin(angle) * radiusZ, 1, 0.0D, -0.02D, 0.0D, 0.01D);
        }
    }


    private static boolean tickGate(Gate gate) {
        ServerPlayer user = gate.user();
        LivingEntity subject = gate.subject();
        LivingEntity destination = gate.destination();
        if (user.hasDisconnected() || !isUsable(subject) || !isUsable(destination) || !(subject.level() instanceof ServerLevel level)) {
            fail(user, Component.translatable("tracadamia.skill.warping.ally_gone"));
            return false;
        }

        long elapsed = level.getGameTime() - gate.start();
        double progress = (double) elapsed / gate.ticks();
        cover(level, subject, progress);
        if (progress > 1.0D - SPILL && destination.level() instanceof ServerLevel there) {
            ring(there, destination.position(), Math.max(0.5D, subject.getBbWidth() * 0.8D));
        }

        if (elapsed % 6L == 0L) {
            playSound(level, subject.position(), SoundEvents.HONEY_BLOCK_STEP, 0.8F, 0.5F);
        }

        if (elapsed < gate.ticks()) {
            return true;
        }

        Vec3 from = subject.position();
        ServerLevel fromLevel = level;
        Vec3 to = destination.position();
        ISpatialMovement.warp(subject, user, to.x, to.y, to.z, destination.level().dimension());

        if (!subject.isRemoved() && subject.level() == fromLevel && subject.position().distanceToSqr(from) < 0.01D) {
            return false;
        }

        portal(fromLevel, from, subject.getBbHeight(), subject.getBbWidth());
        portal((ServerLevel) destination.level(), to, subject.getBbHeight(), subject.getBbWidth());
        gate.instance().setCoolDown(CONFIG.warpCooldown, WARPING);
        gate.instance().addMasteryPoint(user);
        message(user, Component.translatable("tracadamia.skill.warping.warped", subject.getDisplayName(), destination.getDisplayName()));
        return false;
    }

    // Getting hit will interrupt the warp
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (GATES.isEmpty() || event.getNewDamage() <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        GATES.removeIf(gate -> {
            if (gate.subject() != entity) {
                return false;
            }

            Vec3 center = entity.getBoundingBox().getCenter();
            level.sendParticles(GOOP, center.x, center.y, center.z, 25, entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.5D, 0.1D);
            playSound(level, center, SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.6F);
            fail(gate.user(), Component.translatable("tracadamia.skill.warping.interrupted"));
            return true;
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!GATES.isEmpty()) {
            GATES.removeIf(gate -> !tickGate(gate));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        GATES.clear();
    }

    private static void ring(ServerLevel level, Vec3 base, double radius) {
        for (int point = 0; point < 16; point++) {
            double angle = point * Mth.TWO_PI / 16.0D;
            level.sendParticles(GOOP_EDGE, base.x + Math.cos(angle) * radius, base.y + 0.1D, base.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // Ring of goop, might remove later
    private static void portal(ServerLevel level, Vec3 base, double height, double width) {
        ring(level, base, Math.max(0.5D, width * 0.8D));
        level.sendParticles(GOOP, base.x, base.y + height * 0.5D, base.z, 30, width * 0.5D, height * 0.4D, width * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y + height * 0.5D, base.z, 8, width * 0.4D, height * 0.4D, width * 0.4D, 0.02D);
        playSound(level, base, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.8F, 0.7F);
        playSound(level, base, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.6F);
    }


    private static boolean isGoopOn(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(GOOP_TAG);
    }

    private static void setGoop(ManasSkillInstance instance, LivingEntity entity, boolean on) {
        instance.getOrCreateTag().putBoolean(GOOP_TAG, on);
        instance.markDirty();
        SkillAPI.getSkillsFrom(entity).markDirty();
    }

    private static void toggleGoop(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean on = !isGoopOn(instance);
        setGoop(instance, entity, on);
        playSound(level, entity.position(), on ? SoundEvents.HONEY_BLOCK_PLACE : SoundEvents.HONEY_BLOCK_BREAK, 1.0F, 0.6F);
        message(entity, Component.translatable(on ? "tracadamia.skill.warping.goop_on" : "tracadamia.skill.warping.goop_off"));
        if (on) {
            instance.addMasteryPoint(entity);
        }
    }

    private static @Nullable LivingEntity getGoopReceiver(LivingEntity user, ManasSkillInstance instance) {
        UUID chosen = getTarget(instance, GOOP_TARGET_TAG);
        if (chosen != null) {
            LivingEntity ally = findAlly(user, chosen, true);
            if (ally != null && ally != user && ally.level() == user.level()) {
                return ally;
            }
        }

        List<LivingEntity> allies = getAllies(user, false);
        return allies.isEmpty() ? null : allies.get(user.getRandom().nextInt(allies.size()));
    }

    private static boolean canAbsorb(LivingEntity user, Projectile projectile, Vec3 center, long time) {
        if (projectile.isRemoved() || projectile instanceof BarrierEntity || projectile instanceof FishingHook || projectile.getDeltaMovement().length() < MIN_GOOP_SPEED || projectile.getBoundingBox().getCenter().distanceTo(center) > CONFIG.goopRadius) {
            return false;
        }

        if (projectile.getPersistentData().contains(GOOPED_TAG)) {
            return false;
        }

        Entity shooter = projectile.getOwner();
        return shooter != user && !(shooter instanceof LivingEntity living && isAlly(user, living));
    }

    private static void tickGoop(ServerLevel level, ManasSkillInstance instance, LivingEntity user) {
        Vec3 center = user.getBoundingBox().getCenter();
        long time = level.getGameTime();
        if (time % 10L == 0L) {
            level.sendParticles(GOOP, center.x, center.y, center.z, 2, user.getBbWidth() * 0.6D, user.getBbHeight() * 0.4D, user.getBbWidth() * 0.6D, 0.0D);
        }

        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, user.getBoundingBox().inflate(CONFIG.goopRadius), projectile -> canAbsorb(user, projectile, center, time))) {
            LivingEntity ally = getGoopReceiver(user, instance);
            if (ally == null) {
                return;
            }

            if (EnergyHelper.isOutOfEnergy(user, instance, GOOP_DEFENSE)) {
                setGoop(instance, user, false);
                message(user, Component.translatable("tracadamia.skill.warping.goop_off"));
                return;
            }

            sendThrough(level, projectile, ally);
            instance.addMasteryPoint(user);
        }
    }

    private static Vec3 getExit(ServerLevel level, LivingEntity ally, Vec3 target) {
        double distance = EXIT_DISTANCE + ally.getBbWidth() * 0.5D;
        double start = level.random.nextDouble() * Mth.TWO_PI;
        for (int tries = 0; tries < 8; tries++) {
            double angle = start + tries * Mth.TWO_PI / 8.0D;
            Vec3 exit = target.add(Math.cos(angle) * distance, ally.getBbHeight() * 0.25D, Math.sin(angle) * distance);
            if (level.getBlockState(BlockPos.containing(exit)).getCollisionShape(level, BlockPos.containing(exit)).isEmpty()
                    && level.clip(new ClipContext(exit, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ally)).getType() == HitResult.Type.MISS) {
                return exit;
            }
        }
        return target.add(0.0D, ally.getBbHeight() * 0.5D + 1.5D, 0.0D);
    }

    private static void sendThrough(ServerLevel level, Projectile projectile, LivingEntity ally) {
        Vec3 from = projectile.position();
        double speed = Math.max(MIN_GOOP_SPEED * 5.0D, projectile.getDeltaMovement().length());
        Vec3 target = ally.getBoundingBox().getCenter();
        Vec3 exit = getExit(level, ally, target);
        Vec3 toward = target.subtract(exit).normalize();

        projectile.moveTo(exit.x, exit.y, exit.z, projectile.getYRot(), projectile.getXRot());
        projectile.shoot(toward.x, toward.y, toward.z, (float) speed, 0.0F);
        projectile.hasImpulse = true;
        projectile.getPersistentData().putLong(GOOPED_TAG, level.getGameTime());

        level.sendParticles(GOOP, from.x, from.y, from.z, 20, 0.35D, 0.35D, 0.35D, 0.0D);
        level.sendParticles(GOOP_EDGE, from.x, from.y, from.z, 10, 0.5D, 0.5D, 0.5D, 0.0D);
        level.sendParticles(GOOP, exit.x, exit.y, exit.z, 20, 0.35D, 0.35D, 0.35D, 0.0D);
        level.sendParticles(GOOP_EDGE, exit.x, exit.y, exit.z, 10, 0.5D, 0.5D, 0.5D, 0.0D);
        playSound(level, from, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.8F);
        playSound(level, exit, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6F, 1.2F);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            getWarping(player).filter(WarpingQuirk::isGoopOn).ifPresent(instance -> tickGoop(player.serverLevel(), instance, player));
        }
    }

    //

    private static boolean isEmergencyOn(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(EMERGENCY_TAG);
    }

    private static void toggleEmergency(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean on = !isEmergencyOn(instance);
        instance.getOrCreateTag().putBoolean(EMERGENCY_TAG, on);
        instance.markDirty();
        playSound(level, entity.position(), on ? TensuraSoundEvents.SPATIAL_STORAGE.get() : TensuraSoundEvents.GENERIC_UNCAST.get(), 0.8F, 1.0F);
        message(entity, Component.translatable(on ? "tracadamia.skill.warping.emergency_on" : "tracadamia.skill.warping.emergency_off"));
        if (on && getTarget(instance, EMERGENCY_TARGET_TAG) == null) {
            fail(entity, Component.translatable("tracadamia.skill.warping.no_emergency_ally"));
        }
    }

    // Goes last so it doesnt take priority over infinite regen's death prevention
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        ManasSkillInstance instance = getWarping(entity).filter(WarpingQuirk::isEmergencyOn).orElse(null);
        UUID id = instance == null ? null : getTarget(instance, EMERGENCY_TARGET_TAG);
        if (id == null || instance.onCoolDown(EMERGENCY_EXIT)) {
            return;
        }

        LivingEntity ally = findAlly(entity, id, true);
        if (ally == null || ally == entity || SkillUtils.shouldCancelTeleportation(entity) || ally.level() != entity.level() && SkillUtils.shouldCancelDimensionTravel(entity)) {
            return;
        }

        event.setCanceled(true);
        entity.setHealth(Math.max(1.0F, entity.getMaxHealth() * (float) CONFIG.emergencyHealth));
        entity.clearFire();
        entity.resetFallDistance();
        entity.invulnerableTime = 20;
        portal((ServerLevel) entity.level(), entity.position(), entity.getBbHeight(), entity.getBbWidth());
        ISpatialMovement.warp(entity, entity, ally.getX(), ally.getY(), ally.getZ(), ally.level().dimension());
        portal((ServerLevel) entity.level(), entity.position(), entity.getBbHeight(), entity.getBbWidth());
        instance.setCoolDown(CONFIG.emergencyCooldown, EMERGENCY_EXIT);
        instance.addMasteryPoint(entity);
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    public static @Nullable WheelPayload takeClientWheel() {
        WheelPayload wheel = clientWheel;
        clientWheel = null;
        return wheel;
    }

    public static void clearClient() {
        clientWheel = null;
    }

    public record WheelEntry(UUID id, int entityId, String name, int kind) {
        public static final StreamCodec<RegistryFriendlyByteBuf, WheelEntry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, WheelEntry::id,
                ByteBufCodecs.VAR_INT, WheelEntry::entityId,
                ByteBufCodecs.STRING_UTF8, WheelEntry::name,
                ByteBufCodecs.VAR_INT, WheelEntry::kind,
                WheelEntry::new
        );
    }

    public record WheelPayload(int wheel, List<WheelEntry> entries, Optional<UUID> chosen) implements CustomPacketPayload {
        public static final Type<WheelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "warping_wheel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WheelPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WheelPayload::wheel,
                WheelEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), WheelPayload::entries,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), WheelPayload::chosen,
                WheelPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record WheelChoicePayload(int wheel, UUID first, UUID second) implements CustomPacketPayload {
        public static final Type<WheelChoicePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "warping_choice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WheelChoicePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WheelChoicePayload::wheel,
                UUIDUtil.STREAM_CODEC, WheelChoicePayload::first,
                UUIDUtil.STREAM_CODEC, WheelChoicePayload::second,
                WheelChoicePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(WheelPayload.TYPE, WheelPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientWheel = payload));
        event.registrar("1").playToServer(WheelChoicePayload.TYPE, WheelChoicePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                choose(player, payload);
            }
        }));
    }

}

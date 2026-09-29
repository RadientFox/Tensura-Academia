package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class VibrationDetectionQuirk extends Skill {

    private static final QuirkSkillsConfig.VibrationDetection CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).VibrationDetection;

    private static final int DETECT = 0;
    private static final String ACTIVE_TAG = "detectActive";
    private static final String FILTER_TAG = "detectFilter";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_HOSTILE = 1;
    private static final int FILTER_NEUTRAL = 2;
    private static final int FILTER_PASSIVE = 3;
    private static final int FILTER_PLAYERS = 4;
    private static final String[] FILTER_NAMES = {"all", "hostile", "neutral", "passive", "players"};
    private static final int SEND_INTERVAL = 2;
    private static final int SCAN_INTERVAL = 4;
    private static final int MAX_RIPPLES = 64;
    private static final int MAX_MOVING = 256;
    private static final int CLIENT_MAX_RIPPLES = 128;
    private static final double MOVE_THRESHOLD = 1.0E-4D;
    private static final int FELT_TICKS = 40;
    private static final int MAX_SKIPPED = 4096;
    private static final int CALLER_DEPTH = 16;
    private static final ResourceLocation EARTH_AFFINITY = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "vibration_earth_affinity");

    private static final Map<UUID, Listener> LISTENERS = new HashMap<>();

    // Seen on the client, used by VibrationDetectionClient
    private static final Map<Integer, Long> CLIENT_MOVING = new HashMap<>();
    private static final List<ClientRipple> CLIENT_RIPPLES = new ArrayList<>();
    private static double clientRange;

    public record Ripple(float x, float y, float z, float strength) {}

    public record ClientRipple(Vec3 pos, float strength, long time) {}

    private static final class Listener {
        private final ServerPlayer player;
        private final List<Ripple> ripples = new ArrayList<>();
        private final Set<Integer> moving = new HashSet<>();
        private final Map<Integer, Long> felt = new HashMap<>();
        private final Set<Long> skipped = new HashSet<>();
        private Map<Integer, Vec3> lastPositions = new HashMap<>();
        private double range;
        private int filter;

        private Listener(ServerPlayer player) {
            this.player = player;
        }
    }

    public VibrationDetectionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == DETECT ? "vibration_detection.detect" : super.getModeId(instance, mode);
    }

    // Detect
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != DETECT || !(entity.level() instanceof ServerLevel)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (entity.isShiftKeyDown()) {
            int filter = (tag.getInt(FILTER_TAG) + 1) % FILTER_NAMES.length;
            tag.putInt(FILTER_TAG, filter);
            instance.markDirty();
            if (entity instanceof Player player) {
                Component name = Component.translatable("tracadamia.skill.vibration_detection.filter." + FILTER_NAMES[filter]);
                player.displayClientMessage(Component.translatable("tracadamia.skill.vibration_detection.filter", name).withStyle(ChatFormatting.GRAY), true);
            }

            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.6F, 1.4F);
            return;
        }

        if (tag.getBoolean(ACTIVE_TAG)) {
            tag.remove(ACTIVE_TAG);
            instance.markDirty();
            instance.setCoolDown(CONFIG.cooldown, DETECT);
            LISTENERS.remove(entity.getUUID());
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SCULK_CLICKING_STOP, SoundSource.PLAYERS, 1.0F, 0.8F);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, DETECT)) {
            return;
        }

        tag.putBoolean(ACTIVE_TAG, true);
        instance.markDirty();
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 0.8F);
        instance.addMasteryPoint(entity);
    }

    private static boolean isActive(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(ACTIVE_TAG);
    }

    public static boolean isActive(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.VIBRATION_DETECTION.get()).map(VibrationDetectionQuirk::isActive).orElse(false);
    }

    // Earth Affinity and Vibration Targeting share the toggle

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateEarthBoost(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateEarthBoost(entity);
    }

    // Added after every multiplier so it stacks with Earth Manipulation and Domination
    public static void updateEarthBoost(LivingEntity entity) {
        AdditiveBoost.apply(entity, TensuraAttributes.EARTH_BOOST, EARTH_AFFINITY, getAddedEarthBoost(entity));
    }

    private static double getAddedEarthBoost(LivingEntity entity) {
        boolean affinity = SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.VIBRATION_DETECTION.get()).filter(instance -> instance.getMastery() >= 0.0D && instance.isToggled()).isPresent();
        return (affinity ? CONFIG.earthAffinityBoost : 0.0D) + VibrateQuirk.getEarthBoost(entity);
    }

    private static boolean hasEarthAffinity(LivingEntity entity) {
        AttributeInstance boost = entity.getAttribute(TensuraAttributes.EARTH_BOOST);
        return boost != null && boost.hasModifier(EARTH_AFFINITY);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return isActive(instance) || instance.isToggled() || hasEarthAffinity(entity);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateEarthBoost(entity);
        if (!instance.isToggled() && !isActive(instance)) {
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
        LISTENERS.remove(entity.getUUID());
        updateEarthBoost(entity);
    }

    @SubscribeEvent
    public static void onGameEvent(VanillaGameEvent event) {
        if (LISTENERS.isEmpty() || !event.getVanillaEvent().is(GameEventTags.VIBRATIONS)) {
            return;
        }

        Vec3 pos = event.getEventPosition();
        Entity cause = event.getCause();
        if (cause != null && VibrateQuirk.isImmune(cause) || cause instanceof LivingEntity living && !isGrounded(living)) {
            return;
        }

        float strength = event.getVanillaEvent().is(GameEvent.STEP) ? 0.6F : 1.0F;
        long source = cause != null ? cause.getId() : BlockPos.containing(pos).asLong();
        for (Listener listener : LISTENERS.values()) {
            if (cause == listener.player || listener.player.level() != event.getLevel() || listener.player.distanceToSqr(pos) > listener.range * listener.range) {
                continue;
            }

            if (cause instanceof LivingEntity ? !matches(listener.filter, cause) : listener.filter != FILTER_ALL) {
                continue;
            }

            if (cause instanceof LivingEntity) {
                feel(listener, cause.getId());
            }

            if (listener.skipped.remove(source)) {
                continue;
            }

            if (listener.skipped.size() > MAX_SKIPPED) {
                listener.skipped.clear();
            }

            listener.skipped.add(source);
            if (listener.ripples.size() < MAX_RIPPLES) {
                listener.ripples.add(new Ripple((float) pos.x, (float) pos.y, (float) pos.z, strength));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        updateEarthBoost(player);
        Listener listener = LISTENERS.get(player.getUUID());
        if (!player.isAlive() || !isActive(player)) {
            if (listener != null) {
                LISTENERS.remove(player.getUUID());
            }

            return;
        }

        if (listener == null || listener.player != player) {
            listener = new Listener(player);
            LISTENERS.put(player.getUUID(), listener);
        }

        ManasSkillInstance detection = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.VIBRATION_DETECTION.get()).orElse(null);
        listener.range = detection != null && detection.isMastered(player) ? CONFIG.rangeMastered : CONFIG.range;
        listener.filter = detection != null && detection.getTag() != null ? detection.getTag().getInt(FILTER_TAG) : FILTER_ALL;
        if (player.tickCount % SCAN_INTERVAL == 0) {
            scanMoving(listener);
        }

        if (player.tickCount % SEND_INTERVAL == 0) {
            PacketDistributor.sendToPlayer(player, new VibrationPayload(List.copyOf(listener.moving), List.copyOf(listener.ripples), (float) listener.range));
            listener.moving.clear();
            listener.ripples.clear();
        }
    }

    private static void scanMoving(Listener listener) {
        ServerPlayer player = listener.player;
        double range = listener.range;
        Map<Integer, Vec3> positions = new HashMap<>();
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                entity -> entity != player && entity.isAlive() && entity.distanceToSqr(player) <= range * range && isGrounded(entity))) {
            Vec3 last = listener.lastPositions.get(entity.getId());
            if (last != null && last.distanceToSqr(entity.position()) > MOVE_THRESHOLD && matches(listener.filter, entity) && !VibrateQuirk.isImmune(entity)) {
                feel(listener, entity.getId());
            }

            positions.put(entity.getId(), entity.position());
        }

        listener.lastPositions = positions;
        long time = player.level().getGameTime();
        listener.felt.values().removeIf(last -> time - last > FELT_TICKS);
    }

    private static boolean matches(int filter, Entity entity) {
        return switch (filter) {
            case FILTER_HOSTILE -> !(entity instanceof Player) && isHostile(entity);
            case FILTER_NEUTRAL -> !(entity instanceof Player) && !isHostile(entity) && isNeutral(entity);
            case FILTER_PASSIVE -> !(entity instanceof Player) && !isHostile(entity) && !isNeutral(entity);
            case FILTER_PLAYERS -> entity instanceof Player;
            default -> true;
        };
    }

    private static boolean isHostile(Entity entity) {
        return entity instanceof Enemy || entity.getType().is(TensuraEntityTags.HOSTILE_MONSTER) || entity instanceof Mob mob && mob.isAggressive();
    }

    private static boolean isNeutral(Entity entity) {
        return entity instanceof NeutralMob || entity.getType().is(TensuraEntityTags.NEUTRAL_MOB);
    }

    public static boolean isGrounded(LivingEntity entity) {
        return entity.onGround() || !entity.level().noCollision(entity, entity.getBoundingBox().move(0.0D, -0.1D, 0.0D).deflate(0.01D, 0.0D, 0.01D).setMaxY(entity.getY()));
    }

    private static void feel(Listener listener, int id) {
        listener.felt.put(id, listener.player.level().getGameTime());
        if (listener.moving.size() < MAX_MOVING) {
            listener.moving.add(id);
        }
    }

    public static boolean canTargetThroughWalls(LivingEntity user) {
        if (LISTENERS.isEmpty() || user.level().isClientSide || !LISTENERS.containsKey(user.getUUID())) {
            return false;
        }

        boolean toggled = SkillAPI.getSkillsFrom(user).getSkill(QuirkSkills.VIBRATION_DETECTION.get()).map(ManasSkillInstance::isToggled).orElse(false);
        return toggled && StackWalker.getInstance().walk(frames -> frames.limit(CALLER_DEPTH).anyMatch(frame -> isEarthMagic(frame.getClassName())));
    }

    private static boolean isEarthMagic(String className) {
        return className.startsWith("io.github.manasmods.tensura.ability.magic.") && className.contains(".earth.");
    }

    public static boolean isFelt(LivingEntity user, Entity target) {
        Listener listener = LISTENERS.get(user.getUUID());
        Long last = listener == null ? null : listener.felt.get(target.getId());
        return last != null && user.level().getGameTime() - last <= FELT_TICKS;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LISTENERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LISTENERS.clear();
    }

    public static Map<Integer, Long> getClientMoving() {
        return CLIENT_MOVING;
    }

    public static List<ClientRipple> getClientRipples() {
        return CLIENT_RIPPLES;
    }

    public static double getClientRange() {
        return clientRange;
    }

    public static void clearClient() {
        CLIENT_MOVING.clear();
        CLIENT_RIPPLES.clear();
    }

    public record VibrationPayload(List<Integer> moving, List<Ripple> ripples, float range) implements CustomPacketPayload {
        public static final Type<VibrationPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "vibration"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Ripple> RIPPLE_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Ripple::x,
                ByteBufCodecs.FLOAT, Ripple::y,
                ByteBufCodecs.FLOAT, Ripple::z,
                ByteBufCodecs.FLOAT, Ripple::strength,
                Ripple::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, VibrationPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_MOVING)), VibrationPayload::moving,
                RIPPLE_CODEC.apply(ByteBufCodecs.list(MAX_RIPPLES)), VibrationPayload::ripples,
                ByteBufCodecs.FLOAT, VibrationPayload::range,
                VibrationPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(VibrationPayload.TYPE, VibrationPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            Level level = context.player().level();
            long time = level.getGameTime();
            clientRange = payload.range();
            for (int id : payload.moving()) {
                CLIENT_MOVING.put(id, time);
            }

            for (Ripple ripple : payload.ripples()) {
                CLIENT_RIPPLES.add(new ClientRipple(new Vec3(ripple.x(), ripple.y(), ripple.z()), ripple.strength(), time));
            }

            while (CLIENT_RIPPLES.size() > CLIENT_MAX_RIPPLES) {
                CLIENT_RIPPLES.remove(0);
            }
        }));
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.network.RewindFxPayload;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class RewindQuirk extends Skill {

    private static final QuirkSkillsConfig.Rewind CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Rewind;

    public static final int REWOUND = 0;

    private static final int SAMPLE_INTERVAL = 2;
    private static final int PRUNE_INTERVAL = 100;
    private static final int STALE_TICKS = 600;
    private static final int EFFECT_SLACK = 40;
    private static final int TICKS_PER_SECOND = 20;

    private static final String STOCK_TAG = "stockTicks";
    private static final String HORN_TAG = "hornTicks";
    private static final String LAST_TICK_TAG = "lastTick";
    private static final long MAX_ELAPSED = 100L;

    private static final ResourceLocation RECOIL_MODIFIER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "full_cowling_recoil");
    private static final String RECOIL_EXPIRES_TAG = "tracadamia_cowling_recoil_expires";

    private record Snapshot(long time, float health, double spiritual, double magicule, double aura, double ep,
                            List<MobEffectInstance> effects, double recoil, long recoilExpires) {}

    private static final class History {
        private final ArrayDeque<Snapshot> samples = new ArrayDeque<>();
        private long lastSeen;
        private int limit;
    }

    private static final class Session {
        private final LivingEntity target;
        private int debt;
        private int spent;

        private Session(LivingEntity target) {
            this.target = target;
        }
    }

    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public RewindQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/unique/rewind.png");
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == REWOUND ? "rewind.rewound" : super.getModeId(instance, mode);
    }

    public static boolean isDeletionImmune(LivingEntity entity) {
        return getRewind(entity).isPresent();
    }

    public static Optional<ManasSkillInstance> getRewind(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.REWIND.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static int getStock(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(STOCK_TAG);
    }

    public static void setStock(ManasSkillInstance instance, int ticks) {
        instance.getOrCreateTag().putInt(STOCK_TAG, Math.max(0, ticks));
        instance.markDirty();
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D && !(entity instanceof Player);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        serverTick(instance, entity);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) {
            getRewind(event.getEntity()).ifPresent(instance -> serverTick(instance, event.getEntity()));
        }
    }

    private static void serverTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        CompoundTag tag = instance.getOrCreateTag();
        long elapsed = tag.contains(LAST_TICK_TAG) ? Mth.clamp(now - tag.getLong(LAST_TICK_TAG), 0L, MAX_ELAPSED) : 0L;
        tag.putLong(LAST_TICK_TAG, now);
        long horn = tag.getInt(HORN_TAG) + elapsed;
        int interval = Math.max(1, CONFIG.stockIntervalTicks);
        if (horn >= interval) {
            setStock(instance, getStock(instance) + (int) (horn / interval) * TICKS_PER_SECOND);
            horn %= interval;
        }
        tag.putInt(HORN_TAG, (int) horn);

        record(entity, now, CONFIG.ownerHistorySeconds);
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(CONFIG.trackRange),
                other -> other != entity && other.isAlive() && !other.isSpectator())) {
            record(other, now, CONFIG.otherHistorySeconds);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        finish(instance, entity, null);
    }

    private static boolean isBeingRewound(UUID uuid) {
        for (Session session : SESSIONS.values()) {
            if (session.target.getUUID().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private static void record(LivingEntity entity, long now, int seconds) {
        UUID uuid = entity.getUUID();
        if (isBeingRewound(uuid)) {
            return;
        }

        History history = HISTORY.computeIfAbsent(uuid, id -> new History());
        history.limit = Math.max(history.limit, seconds * TICKS_PER_SECOND / SAMPLE_INTERVAL);
        history.lastSeen = now;
        Snapshot last = history.samples.peekLast();
        if (last != null && now - last.time() < SAMPLE_INTERVAL) {
            return;
        }

        history.samples.addLast(snapshot(entity, now));
        while (history.samples.size() > history.limit) {
            history.samples.pollFirst();
        }
    }

    private static Snapshot snapshot(LivingEntity entity, long now) {
        IExistence existence = TensuraStorages.getExistenceFrom(entity);
        List<MobEffectInstance> effects = entity.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier recoil = maxHealth == null ? null : maxHealth.getModifier(RECOIL_MODIFIER);
        CompoundTag data = entity.getPersistentData();
        return new Snapshot(now, entity.getHealth(), existence.getSpiritualHealth(), existence.getMagicule(), existence.getAura(), existence.getEP(),
                effects, recoil == null ? 0.0D : recoil.amount(), data.contains(RECOIL_EXPIRES_TAG) ? data.getLong(RECOIL_EXPIRES_TAG) : -1L);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel) || mode != REWOUND || SESSIONS.containsKey(entity.getUUID())) {
            return;
        }

        if (getStock(instance) <= 0) {
            fail(entity, Component.translatable("tracadamia.skill.rewind.no_stock"));
            return;
        }

        LivingEntity target = entity;
        if (entity.isShiftKeyDown()) {
            target = MultiArms.getTarget(entity, CONFIG.targetRange);
            if (target == null) {
                fail(entity, Component.translatable("tensura.targeting.not_targeted"));
                return;
            }
        }

        History history = HISTORY.get(target.getUUID());
        if (history == null || history.samples.size() < 2) {
            fail(entity, Component.translatable("tracadamia.skill.rewind.no_history"));
            return;
        }

        SESSIONS.put(entity.getUUID(), new Session(target));
        fx(entity, target.getId());
        playSound(entity, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != REWOUND) {
            return false;
        }

        if (entity.level().isClientSide) {
            return true;
        }

        Session session = SESSIONS.get(entity.getUUID());
        if (session == null) {
            return false;
        }

        LivingEntity target = session.target;
        if (!target.isAlive() || target.isRemoved() || target.level() != entity.level() || (target != entity && target.distanceTo(entity) > CONFIG.targetRange)) {
            finish(instance, entity, "tracadamia.skill.rewind.target_lost");
            return false;
        }

        int stock = getStock(instance);
        if (stock <= 0) {
            finish(instance, entity, "tracadamia.skill.rewind.out_of_stock");
            return false;
        }

        History history = HISTORY.get(target.getUUID());
        if (history == null || history.samples.size() < 2) {
            finish(instance, entity, "tracadamia.skill.rewind.history_end");
            return false;
        }

        int spend = Math.min(stock, Math.max(1, CONFIG.rewindSpeed));
        session.debt += spend;
        session.spent += spend;
        while (session.debt >= SAMPLE_INTERVAL && history.samples.size() > 1) {
            history.samples.pollLast();
            session.debt -= SAMPLE_INTERVAL;
        }

        apply(target, history.samples.peekLast());
        setStock(instance, stock - spend);

        if (heldTicks % 10 == 0) {
            playSound(target, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F, 0.6F);
        }
        if (heldTicks > 0 && heldTicks % TICKS_PER_SECOND == 0) {
            instance.addMasteryPoint(entity);
        }
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.rewind.rewinding", seconds(session.spent), seconds(getStock(instance)))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == REWOUND && !entity.level().isClientSide) {
            finish(instance, entity, null);
        }
    }

    private static void finish(ManasSkillInstance instance, LivingEntity entity, @Nullable String reason) {
        Session session = SESSIONS.remove(entity.getUUID());
        if (session == null) {
            return;
        }

        fx(entity, RewindFxPayload.STOP);
        playSound(session.target, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.4F);
        if (entity instanceof Player player) {
            Component message = reason != null ? Component.translatable(reason, seconds(getStock(instance))).withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("tracadamia.skill.rewind.rewound", seconds(session.spent), seconds(getStock(instance))).withStyle(ChatFormatting.GOLD);
            player.displayClientMessage(message, true);
        }
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / (float) TICKS_PER_SECOND);
    }

    private static void apply(LivingEntity target, Snapshot snapshot) {
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            AttributeModifier current = maxHealth.getModifier(RECOIL_MODIFIER);
            double amount = current == null ? 0.0D : current.amount();
            if (Math.abs(amount - snapshot.recoil()) > 1.0E-6D) {
                maxHealth.removeModifier(RECOIL_MODIFIER);
                if (snapshot.recoil() != 0.0D) {
                    maxHealth.addPermanentModifier(new AttributeModifier(RECOIL_MODIFIER, snapshot.recoil(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }

        CompoundTag data = target.getPersistentData();
        if (snapshot.recoilExpires() < 0L) {
            data.remove(RECOIL_EXPIRES_TAG);
        } else {
            data.putLong(RECOIL_EXPIRES_TAG, snapshot.recoilExpires());
        }

        target.setHealth(Math.min(snapshot.health(), target.getMaxHealth()));

        IExistence existence = TensuraStorages.getExistenceFrom(target);
        existence.setSpiritualHealth(snapshot.spiritual());
        existence.setMagicule(snapshot.magicule());
        existence.setAura(snapshot.aura());
        existence.setEP(snapshot.ep());
        existence.markDirty();

        applyEffects(target, snapshot.effects());
    }

    private static void applyEffects(LivingEntity target, List<MobEffectInstance> effects) {
        Map<Holder<MobEffect>, MobEffectInstance> wanted = new HashMap<>();
        for (MobEffectInstance effect : effects) {
            wanted.put(effect.getEffect(), effect);
        }

        for (MobEffectInstance current : List.copyOf(target.getActiveEffects())) {
            MobEffectInstance want = wanted.get(current.getEffect());
            if (want == null || want.getAmplifier() != current.getAmplifier() || Math.abs(want.getDuration() - current.getDuration()) > EFFECT_SLACK) {
                target.removeEffect(current.getEffect());
            } else {
                wanted.remove(current.getEffect());
            }
        }

        for (MobEffectInstance want : wanted.values()) {
            target.addEffect(new MobEffectInstance(want));
        }
    }

    private static void fx(LivingEntity owner, int targetId) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new RewindFxPayload(owner.getId(), targetId));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % PRUNE_INTERVAL != 0 || HISTORY.isEmpty()) {
            return;
        }

        HISTORY.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > STALE_TICKS && !isBeingRewound(entry.getKey()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HISTORY.clear();
        SESSIONS.clear();
    }

    private static void playSound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.AttributeHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ScanningQuirk extends Skill {

    private static final QuirkSkillsConfig.Scanning CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Scanning;

    private static final int SCAN = 0;
    private static final int LOCK_ON = 1;
    private static final int WEAKNESS_DETECTION = 2;
    private static final int MODES = 3;

    private static final double TARGET_RAY_OFFSET = 1.0D;
    private static final int LOCK_CHECK_INTERVAL = 5;
    private static final int SCAN_UPDATE_INTERVAL = 5;
    private static final int MAX_SIGHTINGS = 64;
    private static final int MAX_QUIRKS = 16;

    private static final String SCAN_TAG = "scanActive";
    private static final String PRESENCE_TAG = "scanPresenceSense";
    private static final double PRESENCE_SENSE_LEVEL = 4.0D;
    private static final String LOCK_ID_TAG = "lockTarget";
    private static final String LOCK_UUID_TAG = "lockTargetUUID";
    private static final String WEAKNESS_TAG = "weaknessType";
    private static final ResourceLocation ON_TARGET = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "on_target");
    private static final int WEAKNESS_PARTICLE_TICKS = 10;

    // Damage types that weakness detection has access to
    public enum Weakness {
        FLAME("flame", 0xFF6A2A, TensuraDamageHelper::isFireDamage),
        WATER("water", 0x3C8CFF, TensuraDamageHelper::isWaterDamage),
        ICE("ice", 0xA8E8FF, TensuraDamageHelper::isCold),
        WIND("wind", 0x9CFFB0, TensuraDamageHelper::isWindDamage),
        PHYSICAL("physical", 0xC8C8C8, TensuraDamageHelper::isPhysicalAttack),
        LIGHTNING("lightning", 0xFFF04A, TensuraDamageHelper::isLightningDamage),
        DARK("dark", 0x7A2AA8, TensuraDamageHelper::isDarkDamage),
        LIGHT("light", 0xFFFFD8, TensuraDamageHelper::isLightDamage);

        private final String name;
        private final DustParticleOptions particle;
        private final Predicate<DamageSource> matches;

        Weakness(String name, int color, Predicate<DamageSource> matches) {
            this.name = name;
            this.particle = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F), 1.0F);
            this.matches = matches;
        }

        public Component getName() {
            return Component.translatable("tracadamia.skill.scanning.weakness." + this.name);
        }
    }

    private record Exposed(LivingEntity owner, LivingEntity target, Weakness weakness, double bonus, long until) {}

    private static final List<Exposed> EXPOSED = new ArrayList<>();

    // Latest scan on the client
    private static @Nullable ScanPayload clientScan;

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/scanning.png");
    }

    public ScanningQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == WEAKNESS_DETECTION ? CONFIG.weaknessAuraCost : CONFIG.auraCost;
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
            case SCAN -> "scanning.scan";
            case LOCK_ON -> "scanning.lock_on";
            case WEAKNESS_DETECTION -> "scanning.weakness_detection";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == WEAKNESS_DETECTION && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case SCAN -> toggleScan(instance, entity);
            case LOCK_ON -> lockOn(instance, entity);
            case WEAKNESS_DETECTION -> {
                if (entity.isShiftKeyDown()) {
                    cycleWeakness(level, instance, entity);
                } else {
                    exposeWeakness(level, instance, entity);
                }
            }
        }
    }

    private static void toggleScan(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(SCAN_TAG)) {
            tag.remove(SCAN_TAG);
            instance.markDirty();
            instance.setCoolDown(CONFIG.scanCooldown, SCAN);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SCAN)) {
            return;
        }

        tag.putBoolean(SCAN_TAG, true);
        instance.markDirty();
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
        instance.addMasteryPoint(entity);
    }

    private static boolean isScanning(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(SCAN_TAG);
    }

    // used by ScanningClient
    public static boolean isScanning(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.SCANNING.get()).map(ScanningQuirk::isScanning).orElse(false);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return isScanning(instance) || instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player)) {
            updateOnTarget(instance, entity);
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
        if (instance.getTag() != null && instance.getTag().getBoolean(PRESENCE_TAG)) {
            AttributeHelper.removePresenceSense(entity, PRESENCE_SENSE_LEVEL);
        }

        Modifiers.set(entity, ManasCoreAttributes.CRITICAL_ATTACK_CHANCE, ON_TARGET, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        EXPOSED.removeIf(exposed -> exposed.owner() == entity);
    }

    // On Target, Passive Toggle
    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        Modifiers.set(entity, ManasCoreAttributes.CRITICAL_ATTACK_CHANCE, ON_TARGET, 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    // Every attack crits while locked on
    private static void updateOnTarget(ManasSkillInstance instance, LivingEntity entity) {
        boolean onTarget = instance.isToggled() && getLockTarget(instance) >= 0;
        Modifiers.set(entity, ManasCoreAttributes.CRITICAL_ATTACK_CHANCE, ON_TARGET, onTarget ? CONFIG.onTargetCritChance : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    public static List<ManasSkillInstance> getQuirks(LivingEntity target) {
        return SkillAPI.getSkillsFrom(target).getLearnedSkills().stream()
                .filter(quirk -> quirk.getMastery() >= 0.0D && TensuraAcadamia.MODID.equals(quirk.getSkillId().getNamespace()))
                .toList();
    }

    private static ScanPayload createScan(ServerPlayer player, ServerLevel level) {
        List<Sighting> sightings = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CONFIG.scanRange),
                        target -> target != player && target.isAlive() && !(target instanceof ArmorStand) && target.distanceTo(player) <= CONFIG.scanRange)
                .stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(player)))
                .limit(MAX_SIGHTINGS)
                .map(target -> new Sighting(target.getId(), getQuirks(target).size()))
                .toList();

        LivingEntity focus = ObjectSelectionHelper.getTargetingEntity(player, CONFIG.scanRange + TARGET_RAY_OFFSET, false, true);
        if (focus == null) {
            return new ScanPayload(sightings, -1, 0.0D, List.of(), false);
        }

        List<ManasSkillInstance> focusQuirks = getQuirks(focus);
        boolean obscured = focusQuirks.stream().anyMatch(ScanningQuirk::isUnreadable);
        List<Component> quirks = focusQuirks.stream().limit(MAX_QUIRKS).<Component>map(quirk -> obscured
                ? Component.literal("?".repeat(ChatFormatting.stripFormatting(quirk.getDisplayName().getString()).length()))
                : quirk.getDisplayName()).toList();
        return new ScanPayload(sightings, focus.getId(), obscured ? 0.0D : TensuraStorages.getExistenceFrom(focus).getEP(), quirks, obscured);
    }

    private static boolean isUnreadable(ManasSkillInstance quirk) {
        String path = quirk.getSkillId().getPath();
        return path.equals("all_for_one") || path.startsWith("one_for_all");
    }

    public static @Nullable ScanPayload getClientScan() {
        return clientScan;
    }

    public record Sighting(int entityId, int quirks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Sighting> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sighting::entityId,
                ByteBufCodecs.VAR_INT, Sighting::quirks,
                Sighting::new
        );
    }

    public record ScanPayload(List<Sighting> sightings, int focusId, double focusEp, List<Component> focusQuirks, boolean obscured) implements CustomPacketPayload {
        public static final Type<ScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "scan"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScanPayload> STREAM_CODEC = StreamCodec.composite(
                Sighting.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SIGHTINGS)), ScanPayload::sightings,
                ByteBufCodecs.VAR_INT, ScanPayload::focusId,
                ByteBufCodecs.DOUBLE, ScanPayload::focusEp,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_QUIRKS)), ScanPayload::focusQuirks,
                ByteBufCodecs.BOOL, ScanPayload::obscured,
                ScanPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ScanPayload.TYPE, ScanPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientScan = payload));
    }

    private static void lockOn(ManasSkillInstance instance, LivingEntity entity) {
        if (getLockTarget(instance) >= 0) {
            clearLock(instance);
            message(entity, Component.translatable("tracadamia.skill.scanning.lock_released").withStyle(ChatFormatting.GRAY));
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, getLockOnRange(instance, entity) + TARGET_RAY_OFFSET, false, true);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, LOCK_ON)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        tag.putInt(LOCK_ID_TAG, target.getId());
        tag.putUUID(LOCK_UUID_TAG, target.getUUID());
        instance.markDirty();

        message(entity, Component.translatable("tracadamia.skill.scanning.locked", target.getDisplayName()).withStyle(ChatFormatting.AQUA));
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.lockOnCooldown, LOCK_ON);
    }

    private static int getLockTarget(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null || !tag.contains(LOCK_ID_TAG) ? -1 : tag.getInt(LOCK_ID_TAG);
    }

    private static void clearLock(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(LOCK_ID_TAG)) {
            return;
        }

        tag.remove(LOCK_ID_TAG);
        tag.remove(LOCK_UUID_TAG);
        instance.markDirty();
    }

    public static int getLockTarget(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.SCANNING.get()).map(ScanningQuirk::getLockTarget).orElse(-1);
    }

    public static double getLockOnRange(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.SCANNING.get()).map(instance -> getLockOnRange(instance, player)).orElse(CONFIG.lockOnRange);
    }

    private static double getLockOnRange(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isMastered(entity) ? CONFIG.lockOnRangeMastered : CONFIG.lockOnRange;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        Skills skills = SkillAPI.getSkillsFrom(player);
        skills.getSkill(QuirkSkills.SCANNING.get()).ifPresent(instance -> {
            if (isScanning(instance) && player.tickCount % SCAN_UPDATE_INTERVAL == 0) {
                PacketDistributor.sendToPlayer(player, createScan(player, level));
                AttributeHelper.addPresenceSense(player, PRESENCE_SENSE_LEVEL);
                if (!instance.getOrCreateTag().getBoolean(PRESENCE_TAG)) {
                    instance.getOrCreateTag().putBoolean(PRESENCE_TAG, true);
                    instance.markDirty();
                }
            } else if (!isScanning(instance) && instance.getTag() != null && instance.getTag().getBoolean(PRESENCE_TAG)) {
                removePresenceSense(player, instance);
                skills.markDirty();
            }

            if (player.tickCount % LOCK_CHECK_INTERVAL == 0) {
                checkLock(player, level, skills, instance);
            }

            updateOnTarget(instance, player);
        });
    }

    // Weakness Detection

    public static Weakness getWeakness(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        Weakness[] types = Weakness.values();
        return types[Math.floorMod(tag == null ? 0 : tag.getInt(WEAKNESS_TAG), types.length)];
    }

    private static void cycleWeakness(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Weakness next = Weakness.values()[(getWeakness(instance).ordinal() + 1) % Weakness.values().length];
        instance.getOrCreateTag().putInt(WEAKNESS_TAG, next.ordinal());
        instance.markDirty();
        message(entity, Component.translatable("tracadamia.skill.scanning.weakness_selected", next.getName()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.4F);
    }

    private static @Nullable LivingEntity getLocked(ServerLevel level, ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(LOCK_UUID_TAG) && level.getEntity(tag.getUUID(LOCK_UUID_TAG)) instanceof LivingEntity target && target.isAlive() ? target : null;
    }

    private static void exposeWeakness(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = getLocked(level, instance);
        if (target == null) {
            fail(entity, "tracadamia.skill.scanning.not_locked");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, WEAKNESS_DETECTION)) {
            return;
        }

        Weakness weakness = getWeakness(instance);
        double bonus = instance.isMastered(entity) ? CONFIG.weaknessDamageMastered : CONFIG.weaknessDamage;
        EXPOSED.removeIf(exposed -> exposed.owner() == entity);
        EXPOSED.add(new Exposed(entity, target, weakness, bonus, level.getGameTime() + CONFIG.weaknessSeconds * 20L));

        level.sendParticles(weakness.particle, target.getX(), target.getY(0.5D), target.getZ(), 30, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.0D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        message(entity, Component.translatable("tracadamia.skill.scanning.weakness_exposed", target.getDisplayName(), weakness.getName()).withStyle(ChatFormatting.AQUA));
        message(target, Component.translatable("tracadamia.skill.scanning.weakness_found", weakness.getName()).withStyle(ChatFormatting.RED));
        instance.setCoolDown(CONFIG.weaknessCooldown, WEAKNESS_DETECTION);
        instance.addMasteryPoint(entity);
    }

    // Extra damage from exposed weaknesses
    public static double getWeaknessBonus(LivingEntity target, DamageSource source) {
        double multiplier = 1.0D;
        for (Exposed exposed : EXPOSED) {
            if (exposed.target() == target && exposed.weakness().matches.test(source)) {
                multiplier *= 1.0D + exposed.bonus();
            }
        }
        return multiplier;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (EXPOSED.isEmpty() || event.getEntity().level().isClientSide) {
            return;
        }

        double multiplier = getWeaknessBonus(event.getEntity(), event.getSource());
        if (multiplier != 1.0D) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    private static boolean tickExposed(Exposed exposed) {
        LivingEntity target = exposed.target();
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || level.getGameTime() > exposed.until()) {
            return true;
        }

        if (level.getGameTime() % WEAKNESS_PARTICLE_TICKS == 0L) {
            level.sendParticles(exposed.weakness().particle, target.getX(), target.getY(0.5D), target.getZ(), 2, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.0D);
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!EXPOSED.isEmpty()) {
            EXPOSED.removeIf(ScanningQuirk::tickExposed);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        EXPOSED.clear();
    }

    private static void checkLock(ServerPlayer player, ServerLevel level, Skills skills, ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(LOCK_UUID_TAG)) {
            return;
        }

        if (!(level.getEntity(tag.getUUID(LOCK_UUID_TAG)) instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > getLockOnRange(instance, player)) {
            clearLock(instance);
            skills.markDirty();
            message(player, Component.translatable("tracadamia.skill.scanning.lock_lost").withStyle(ChatFormatting.GRAY));
            return;
        }

        if (tag.getInt(LOCK_ID_TAG) != target.getId()) {
            tag.putInt(LOCK_ID_TAG, target.getId());
            instance.markDirty();
            skills.markDirty();
        }
    }

    private static void removePresenceSense(LivingEntity entity, ManasSkillInstance instance) {
        AttributeHelper.removePresenceSense(entity, PRESENCE_SENSE_LEVEL);
        instance.getOrCreateTag().remove(PRESENCE_TAG);
        instance.markDirty();
    }

    private static void message(LivingEntity entity, Component component) {
        if (entity instanceof Player player) {
            player.displayClientMessage(component, true);
        }
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        message(entity, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

}

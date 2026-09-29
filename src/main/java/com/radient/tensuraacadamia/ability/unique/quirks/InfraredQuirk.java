package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.race.api.RaceAPI;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.data.TensuraRaceTags;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class InfraredQuirk extends Skill {

    private static final QuirkSkillsConfig.Infrared CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Infrared;

    private static final int INFRARED = 0;
    private static final int UPDATE_INTERVAL = 5;
    private static final int MAX_SIGNATURES = 256;
    private static final String ACTIVE_TAG = "infraredActive";

    // Heat signatures seen on the client
    private static Set<Integer> clientSignatures = Set.of();
    private static double clientRange;

    public InfraredQuirk() {
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
        return mode == INFRARED ? "infrared.infrared" : super.getModeId(instance, mode);
    }

    // Infrared
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != INFRARED || !(entity.level() instanceof ServerLevel)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(ACTIVE_TAG)) {
            tag.remove(ACTIVE_TAG);
            instance.markDirty();
            instance.setCoolDown(CONFIG.cooldown, INFRARED);
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, INFRARED)) {
            return;
        }

        tag.putBoolean(ACTIVE_TAG, true);
        instance.markDirty();
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.4F);
        instance.addMasteryPoint(entity);
    }

    private static boolean isActive(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(ACTIVE_TAG);
    }

    public static boolean isActive(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.INFRARED.get()).map(InfraredQuirk::isActive).orElse(false);
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            grantSenseHeatSource(entity);
        }
    }

    private static void grantSenseHeatSource(LivingEntity entity) {
        ManasSkill senseHeatSource = ExtraSkills.SENSE_HEAT_SOURCE.get();
        if (SkillUtils.hasSkillFully(entity, senseHeatSource)) {
            return;
        }

        TensuraSkillInstance instance = new TensuraSkillInstance(senseHeatSource);
        instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        SkillHelper.learnSkill(entity, instance);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isTemporarySkill()) {
            grantSenseHeatSource(entity);
        }

        if (!isActive(instance)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    // Undead mobs and undead races
    public static boolean hasHeat(Entity entity, Player viewer) {
        EntityType<?> type = entity.getType();
        if (!entity.isAlive() || type.is(TensuraEntityTags.NO_HIGHLIGHT) || (type.is(TensuraEntityTags.CAN_STAY_INVISIBLE) && entity.isInvisibleTo(viewer))) {
            return false;
        }

        if (entity instanceof LivingEntity living) {
            AttributeInstance concealment = living.getAttribute(TensuraAttributes.PRESENCE_CONCEALMENT);
            if (concealment != null && concealment.getValue() > 0.0D) {
                return false;
            }
        }

        if (entity.isOnFire() || type.is(TensuraEntityTags.HOT_SOURCE)) {
            return true;
        }

        if (!(entity instanceof LivingEntity living) || entity instanceof ArmorStand || living.isInvertedHealAndHarm() || type.is(EntityTypeTags.UNDEAD)
                || type.is(TensuraEntityTags.COLD_SOURCE) || type.is(TensuraEntityTags.COLD_BLOODED) || type.is(TensuraEntityTags.NO_BLOOD)) {
            return false;
        }

        return RaceAPI.getRaceFrom(living).getRace().map(race -> !race.is(TensuraRaceTags.COLD_BLOODED) && !race.is(TensuraRaceTags.UNDEAD)).orElse(true);
    }

    private static double getRange(Player player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.INFRARED.get())
                .map(instance -> instance.isMastered(player) ? CONFIG.rangeMastered : CONFIG.range)
                .orElse(CONFIG.range);
    }

    public static Set<Integer> getClientSignatures() {
        return clientSignatures;
    }

    public static double getClientRange() {
        return clientRange;
    }

    public record InfraredPayload(List<Integer> signatures, float range) implements CustomPacketPayload {
        public static final Type<InfraredPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "infrared"));
        public static final StreamCodec<RegistryFriendlyByteBuf, InfraredPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_SIGNATURES)), InfraredPayload::signatures,
                ByteBufCodecs.FLOAT, InfraredPayload::range,
                InfraredPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(InfraredPayload.TYPE, InfraredPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            clientSignatures = Set.copyOf(payload.signatures());
            clientRange = payload.range();
        }));
    }

    // Warm bodies in range, walls don't matter
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % UPDATE_INTERVAL != 0 || !isActive(player)) {
            return;
        }

        double range = getRange(player);
        List<Integer> signatures = player.level().getEntities(player, player.getBoundingBox().inflate(range),
                        target -> target.distanceTo(player) <= range && hasHeat(target, player))
                .stream()
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(player)))
                .limit(MAX_SIGNATURES - 1)
                .map(Entity::getId)
                .collect(Collectors.toCollection(ArrayList::new));
        if (hasHeat(player, player)) {
            signatures.add(player.getId());
        }

        PacketDistributor.sendToPlayer(player, new InfraredPayload(signatures, (float) range));
    }

}

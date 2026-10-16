package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import com.radient.tensuraacadamia.util.SizeReach;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class BodyBulkQuirk extends Skill {

    private static final QuirkSkillsConfig.BodyBulk CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).BodyBulk;

    private static final int BODY_BULK = 0;
    private static final String BULK_TAG = "bulk";
    private static final ResourceLocation BULK = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "body_bulk");
    private static final double MULTIPLIER_STEP = 0.05D;
    private static final double SAME = 1.0E-6D;
    private static final int GROW_PARTICLE_TICKS = 4;
    private static final int GROW_SOUND_TICKS = 20;
    private static final int STRESS_EFFECT_INTERVAL = 10;

    private static final Map<UUID, Integer> STRESS = new HashMap<>();

    public BodyBulkQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCostPerSize * CONFIG.growthPerTick;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == BODY_BULK ? "body_bulk.body_bulk" : super.getModeId(instance, mode);
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getBodyBulk(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.BODY_BULK.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double getGained(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : Math.max(0.0D, tag.getDouble(BULK_TAG));
    }

    private static void setGained(ManasSkillInstance instance, double gained) {
        instance.getOrCreateTag().putDouble(BULK_TAG, gained);
        instance.markDirty();
    }

    private static double getMaxGained(ManasSkillInstance instance, LivingEntity entity) {
        return Math.max(0.0D, (instance.isMastered(entity) ? CONFIG.maxSizeMastered : CONFIG.maxSize) - 1.0D);
    }

    public static double getStressMultiplier(LivingEntity entity) {
        int ticks = STRESS.getOrDefault(entity.getUUID(), 0);
        if (ticks <= 0) {
            return 1.0D;
        }

        double multiplier = Math.pow(2.0D, ticks / Math.max(1.0D, CONFIG.stressDoubleSeconds * 20.0D));
        return Math.min(CONFIG.stressMaxMultiplier, Math.floor(multiplier / MULTIPLIER_STEP) * MULTIPLIER_STEP);
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        double gained = getGained(instance);
        Modifiers.set(entity, Attributes.SCALE, BULK, gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, BULK, CONFIG.damagePerSize * gained * getStressMultiplier(entity), AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ARMOR, BULK, CONFIG.armorPerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        SizeReach.update(entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != BODY_BULK || !(entity.level() instanceof ServerLevel level) || !entity.isShiftKeyDown() || getGained(instance) <= 0.0D) {
            return;
        }

        setGained(instance, 0.0D);
        update(instance, entity);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.5D), entity.getZ(), 20, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.02D);
        playSound(level, entity, SoundEvents.PUFFER_FISH_BLOW_OUT, 1.0F, 0.6F);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != BODY_BULK || entity.isShiftKeyDown() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        double gained = getGained(instance);
        double max = getMaxGained(instance, entity);
        if (gained >= max - SAME) {
            if (heldTicks == 0) {
                fail(entity, Component.translatable("tracadamia.skill.body_bulk.max_size"));
            }
            return false;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, BODY_BULK)) {
            return false;
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        gained = Math.min(max, gained + Math.max(SAME, CONFIG.growthPerTick));
        setGained(instance, gained);
        update(instance, entity);
        if (heldTicks % GROW_PARTICLE_TICKS == 0) {
            level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(), entity.getZ(), 4, entity.getBbWidth() * 0.5D, 0.1D, entity.getBbWidth() * 0.5D, 0.03D);
        }

        if (heldTicks % GROW_SOUND_TICKS == 0) {
            playSound(level, entity, TensuraSoundEvents.TRANSFORM_OGRE.get(), 1.0F, (float) Math.max(0.5D, 1.1D - gained * 0.12D));
        }

        if (gained >= max - SAME) {
            playSound(level, entity, SoundEvents.ANVIL_LAND, 0.4F, 0.6F);
            instance.setCoolDown(CONFIG.cooldown, BODY_BULK);
            return false;
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == BODY_BULK && heldTicks > 0) {
            instance.setCoolDown(CONFIG.cooldown, BODY_BULK);
        }
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        STRESS.remove(entity.getUUID());
        update(instance, entity);
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
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        STRESS.remove(owner.getUUID());
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        STRESS.remove(entity.getUUID());
        setGained(instance, 0.0D);
        update(instance, entity);
        SizeReach.update(entity, this);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            getBodyBulk(player).ifPresent(instance -> tickStress(instance, player));
        }
    }

    private static void tickStress(ManasSkillInstance instance, ServerPlayer player) {
        boolean stressed = instance.isToggled() && getGained(instance) > 0.0D && player.isAlive() && player.getHealth() <= player.getMaxHealth() * CONFIG.stressHealth;
        int ticks = STRESS.getOrDefault(player.getUUID(), 0);
        if (!stressed) {
            if (ticks > 0) {
                STRESS.remove(player.getUUID());
                update(instance, player);
            }
            return;
        }

        STRESS.put(player.getUUID(), ticks + 1);
        update(instance, player);
        ServerLevel level = player.serverLevel();
        if (ticks == 0) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.body_bulk.stress_overload").withStyle(ChatFormatting.DARK_RED), true);
            playSound(level, player, SoundEvents.RAVAGER_ROAR, 0.8F, 1.3F);
        }

        if (ticks % STRESS_EFFECT_INTERVAL == 0) {
            float multiplier = (float) getStressMultiplier(player);
            float auraSize = (float) (player.getAttributeValue(Attributes.SCALE) * 3.0D * multiplier);
            TensuraParticleHelper.addServerAuraParticles(player, TensuraParticleUtils.getRedAura(1.0F, auraSize, -0.3F), 1, 0.03D);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY(1.0D), player.getZ(), 1, player.getBbWidth() * 0.3D, 0.1D, player.getBbWidth() * 0.3D, 0.0D);
            if (ticks % 20 == 0) {
                playSound(level, player, SoundEvents.WARDEN_HEARTBEAT, 0.8F, Mth.clamp(0.8F + multiplier * 0.3F, 0.8F, 1.6F));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STRESS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STRESS.clear();
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

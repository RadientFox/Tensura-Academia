package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Modifiers;
import com.radient.tensuraacadamia.util.SizeReach;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
    private static final int SLAM = 1;
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
        return mode == SLAM ? CONFIG.slamAuraCost : CONFIG.auraCostPerSize * CONFIG.growthPerTick;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == BODY_BULK ? SLAM : BODY_BULK;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BODY_BULK -> "body_bulk.body_bulk";
            case SLAM -> "body_bulk.slam";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == BODY_BULK && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
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
        Modifiers.set(entity, Attributes.MAX_HEALTH, BULK, CONFIG.healthPercentPerSize * gained, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        SizeReach.update(entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode == SLAM && entity.level() instanceof ServerLevel level) {
            slam(level, instance, entity);
            return;
        }

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

    private static void slam(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, SLAM)) {
            return;
        }

        double size = entity.getScale();
        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 point = entity.position().add(forward.scale(entity.getBbWidth() * 0.5D + size));
        double slamRadius = CONFIG.slamRadius * size;
        double shockRadius = Math.max(slamRadius, CONFIG.slamShockwaveRadius * size);
        float melee = (float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, SLAM);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(shockRadius, 2.0D + size, shockRadius),
                target -> target != entity && target.isAlive() && !target.isSpectator() && !MultiArms.isHoldPair(entity, target) && HitRules.canHit(entity, target))) {
            Vec3 away = new Vec3(target.getX() - point.x, 0.0D, target.getZ() - point.z);
            double distance = away.length() - target.getBbWidth() * 0.5D;
            if (distance > shockRadius) {
                continue;
            }

            boolean slammed = distance <= slamRadius;
            target.invulnerableTime = 0;
            target.hurt(source, melee * (float) (slammed ? CONFIG.slamDamage : CONFIG.slamShockwaveDamage));
            Vec3 push = away.lengthSqr() < 1.0E-4D ? forward : away.normalize();
            SkillHelper.knockBack(target, entity, instance, push, slammed ? 0.4D : 0.8D, 0.0D, slammed ? 0.6D : 0.3D);
            target.hurtMarked = true;
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        BlockState ground = level.getBlockState(BlockPos.containing(point.x, entity.getY() - 0.5D, point.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), point.x, entity.getY() + 0.1D, point.z, 40, slamRadius * 0.5D, 0.2D, slamRadius * 0.5D, 0.2D);
        }

        VibrateQuirk.shakeGround(level, point, shockRadius, 8);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, entity.getY() + 0.1D, point.z, 0, 0.0D, 1.0D, 0.0D, slamRadius / 1.5D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, entity.getY() + 0.1D, point.z, 0, 0.0D, 1.0D, 0.0D, shockRadius / 1.5D);
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, entity.getY() + 0.3D, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.7F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        instance.setCoolDown(CONFIG.slamCooldown, SLAM);
        instance.addMasteryPoint(entity);
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

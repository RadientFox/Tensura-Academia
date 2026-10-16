package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TripodQuirk extends Skill {

    private static final QuirkSkillsConfig.Tripod CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Tripod;

    private static final int DEFENSIVE_STANCE = 0;
    private static final int PIN_AND_STOMP = 1;
    private static final int MODES = 2;

    private static final String STANCE_TAG = "stanceUntil";
    private static final int STOMP_DELAY = 6;
    private static final int PIN_SLOWNESS = 6;
    private static final ResourceLocation KNOCKBACK = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tripod_knockback_immunity");
    private static final ResourceLocation GROUNDED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tripod_grounded_guard");
    private static final ResourceLocation THIRD_LEG = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tripod_third_leg");

    private record Stomp(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, long at) {}

    private static final List<Stomp> STOMPS = new ArrayList<>();
    private static boolean countering = false;

    public TripodQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case DEFENSIVE_STANCE -> CONFIG.stanceAuraCost;
            case PIN_AND_STOMP -> CONFIG.stompAuraCost;
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
            case DEFENSIVE_STANCE -> "tripod.defensive_stance";
            case PIN_AND_STOMP -> "tripod.pin_and_stomp";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getTripod(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TRIPOD.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case DEFENSIVE_STANCE -> stance(level, instance, entity);
            case PIN_AND_STOMP -> pin(level, instance, entity);
        }
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        applyPassives(entity, true);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        applyPassives(entity, false);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player)) {
            applyPassives(entity, true);
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
        applyPassives(entity, false);
        STOMPS.removeIf(stomp -> stomp.owner() == entity);
    }

    private static void applyPassives(LivingEntity entity, boolean on) {
        Modifiers.set(entity, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, on ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, KNOCKBACK, on ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, THIRD_LEG, on ? CONFIG.thirdLegSpeed : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.ARMOR, GROUNDED, on && entity.onGround() ? CONFIG.groundedArmor : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static boolean isOn(LivingEntity entity) {
        return getTripod(entity).filter(ManasSkillInstance::isToggled).isPresent();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) {
            getTripod(player).ifPresent(instance -> applyPassives(player, instance.isToggled()));
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!event.getEntity().level().isClientSide && isOn(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Defensive Stance

    public static boolean isInStance(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(STANCE_TAG) > entity.level().getGameTime();
    }

    private static void stance(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, DEFENSIVE_STANCE)) {
            return;
        }

        double seconds = instance.isMastered(entity) ? CONFIG.stanceSecondsMastered : CONFIG.stanceSeconds;
        instance.getOrCreateTag().putLong(STANCE_TAG, level.getGameTime() + Math.round(seconds * 20.0D));
        instance.markDirty();
        instance.setCoolDown(CONFIG.stanceCooldown, DEFENSIVE_STANCE);
        instance.addMasteryPoint(entity);

        level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY(0.5D), entity.getZ(), 16, entity.getBbWidth() * 0.6D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.6D, 0.1D);
        playSound(level, entity, TensuraSoundEvents.DEFENCE_ACTIVATE.get(), 1.0F, 0.8F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.tripod.stance", (int) Math.round(seconds)), true);
        }
    }

    // Physical hits during the stance get redirected
    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (countering || amount <= 0.0F || !isInStance(instance, entity) || !TensuraDamageHelper.isPhysicalAttack(source)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == entity || !(entity.level() instanceof ServerLevel level)) {
            return true;
        }

        countering = true;
        try {
            attacker.invulnerableTime = 0;
            attacker.hurt(((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.REFLECTED, DEFENSIVE_STANCE), amount);
        } finally {
            countering = false;
        }

        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 toward = attacker.getBoundingBox().getCenter().subtract(center);
        Vec3 normal = toward.lengthSqr() < 1.0E-6D ? entity.getLookAngle() : toward.normalize();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, center.x + normal.x * 0.6D, center.y, center.z + normal.z * 0.6D, 0, normal.x, normal.y, normal.z, 0.6D);
        level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.5D), attacker.getZ(), 12, attacker.getBbWidth() * 0.4D, attacker.getBbHeight() * 0.3D, attacker.getBbWidth() * 0.4D, 0.2D);
        playSound(level, entity, SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
        playSound(level, attacker, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
        instance.addMasteryPoint(entity);
        return false;
    }

    // Pin and Stomp

    private static void pin(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.stompRange * Math.max(1.0D, entity.getScale()));
        if (target == null) {
            playSound(level, entity, TensuraSoundEvents.GENERIC_CAST_FAIL.get(), 1.0F, 1.0F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tensura.targeting.not_targeted").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, PIN_AND_STOMP)) {
            return;
        }

        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) Math.round(CONFIG.pinSeconds * 20.0D), PIN_SLOWNESS, false, false, true), entity);
        target.setDeltaMovement(0.0D, Math.min(0.0D, target.getDeltaMovement().y) - 0.4D, 0.0D);
        target.hurtMarked = true;
        STOMPS.removeIf(stomp -> stomp.owner() == entity);
        STOMPS.add(new Stomp(instance, entity, target, level.getGameTime() + STOMP_DELAY));

        entity.swing(InteractionHand.MAIN_HAND, true);
        BlockState ground = target.getBlockStateOn();
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), target.getX(), target.getY() + 0.1D, target.getZ(), 12, target.getBbWidth() * 0.4D, 0.05D, target.getBbWidth() * 0.4D, 0.1D);
        }
        playSound(level, target, SoundEvents.MACE_SMASH_GROUND, 1.0F, 1.2F);
        instance.setCoolDown(CONFIG.stompCooldown, PIN_AND_STOMP);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickStomp(Stomp stomp) {
        LivingEntity owner = stomp.owner();
        LivingEntity target = stomp.target();
        if (!owner.isAlive() || !target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || owner.level() != level) {
            return true;
        }

        if (level.getGameTime() < stomp.at()) {
            return false;
        }

        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * CONFIG.stompDamage);
        target.invulnerableTime = 0;
        target.hurt(((Skill) stomp.instance().getSkill()).createSource(stomp.instance(), owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, PIN_AND_STOMP), damage);

        BlockState ground = target.getBlockStateOn();
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), target.getX(), target.getY() + 0.1D, target.getZ(), 30, target.getBbWidth() * 0.6D, 0.1D, target.getBbWidth() * 0.6D, 0.15D);
        }
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, target.getX(), target.getY() + 0.1D, target.getZ(), 0, 0.0D, 1.0D, 0.0D, Math.max(0.6D, target.getBbWidth()));
        playSound(level, target, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2F, 1.0F);
        playSound(level, target, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!STOMPS.isEmpty()) {
            STOMPS.removeIf(TripodQuirk::tickStomp);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        STOMPS.clear();
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

}

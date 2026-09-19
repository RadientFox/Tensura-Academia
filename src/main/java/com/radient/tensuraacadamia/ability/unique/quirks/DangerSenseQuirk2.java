package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.extra.ThoughtAccelerationSkill;
import io.github.manasmods.tensura.config.ability.skill.ExtraSkillConfig;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.data.TensuraTags;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class DangerSenseQuirk2 extends Skill {

    private static final QuirkSkillsConfig.DangerSense CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).DangerSense;

    private static final ResourceLocation DETECT = ResourceLocation.fromNamespaceAndPath("tracadamia", "danger_sense_detect");
    private static final ResourceLocation DANGER_LEVEL = ResourceLocation.fromNamespaceAndPath("tracadamia", "danger_sense_danger_level");

    private static final String PERFECT_DODGES_USED_TAG = "perfectDodgesUsed";
    private static final String PERFECT_DODGE_REFILL_TAG = "perfectDodgeRefill";

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/dangersense.png");
    }

    public DangerSenseQuirk2() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || isInSlot(entity, instance) || hasAttributeApplied(entity, TensuraAttributes.AUTO_MELEE_DODGE_CHANCE, DETECT);
    }


    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateDetect(instance, entity);

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
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateDetect(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        removeDetect(entity);
    }

    private boolean isDetectActive(ManasSkillInstance instance, LivingEntity entity) {
        return isInSlot(entity, instance) || (instance.isToggled() && instance.isMastered(entity));
    }

    private void updateDetect(ManasSkillInstance instance, LivingEntity entity) {
        if (!isDetectActive(instance, entity)) {
            removeDetect(entity);
            return;
        }

        double chance = instance.isMastered(entity) ? CONFIG.dodgeChanceMastered : CONFIG.dodgeChance;
        setDetectModifier(entity.getAttribute(TensuraAttributes.AUTO_MELEE_DODGE_CHANCE), chance);
        setDetectModifier(entity.getAttribute(TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE), chance);
    }

    private static void setDetectModifier(@Nullable AttributeInstance attribute, double chance) {
        if (attribute == null) {
            return;
        }

        AttributeModifier current = attribute.getModifier(DETECT);
        if (current != null && current.amount() == chance) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(DETECT, chance, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeDetect(LivingEntity entity) {
        AttributeInstance melee = entity.getAttribute(TensuraAttributes.AUTO_MELEE_DODGE_CHANCE);
        if (melee != null) {
            melee.removeModifier(DETECT);
        }

        AttributeInstance projectile = entity.getAttribute(TensuraAttributes.AUTO_PROJECTILE_DODGE_CHANCE);
        if (projectile != null) {
            projectile.removeModifier(DETECT);
        }
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        ThoughtAccelerationSkill.onToggle(instance, entity, DANGER_LEVEL, true);
        updateDetect(instance, entity);

        AttributeInstance chantSpeed = entity.getAttribute(TensuraAttributes.CHANT_SPEED);
        if (chantSpeed != null && !chantSpeed.hasModifier(DANGER_LEVEL)) {
            double amount = ConfigRegistry.getConfig(ExtraSkillConfig.class).ThoughtAcceleration.chantSpeed;
            chantSpeed.addOrReplacePermanentModifier(new AttributeModifier(DANGER_LEVEL, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        ThoughtAccelerationSkill.onToggle(instance, entity, DANGER_LEVEL, false);
        updateDetect(instance, entity);

        AttributeInstance chantSpeed = entity.getAttribute(TensuraAttributes.CHANT_SPEED);
        if (chantSpeed != null) {
            chantSpeed.removeModifier(DANGER_LEVEL);
        }
    }

    @Override
    public boolean onBeingTargeted(ManasSkillInstance instance, Changeable<LivingEntity> target, LivingEntity attacker) {
        if (!instance.isToggled()) {
            return true;
        }

        if (!(target.get() instanceof ServerPlayer player) || !(attacker instanceof Mob mob)) {
            return true;
        }

        if (mob.getTarget() == null || !player.is(mob.getTarget())) {
            ringAlarm(instance, player, mob);
        }

        return true;
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (instance.isToggled() && source.getEntity() instanceof Player attacker && attacker != entity && entity instanceof ServerPlayer player) {
            ringAlarm(instance, player, attacker);
        }

        if (!(source.getEntity() instanceof LivingEntity threat) || threat == entity || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        if (instance.isMastered(entity) && isDetectActive(instance, entity) && isDodgeBypassed(source, threat) && entity.getRandom().nextDouble() * 100.0D < CONFIG.bypassDodgeChance) {
            playDodge(entity);
            return false;
        }

        if (isInSlot(entity, instance) && usePerfectDodge(instance, entity)) {
            playDodge(entity);
            return false;
        }

        return true;
    }

    private static boolean isDodgeBypassed(DamageSource source, LivingEntity attacker) {
        return ((TensuraDamageSource) source).tensura$isDodgeBypass() || source.is(TensuraTags.DamageTypes.BYPASS_DODGE) || attacker.getAttributeValue(TensuraAttributes.DODGE_NEGATE_CHANCE) > 0.0D;
    }

    private static boolean usePerfectDodge(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        long time = entity.level().getGameTime();

        if (tag.contains(PERFECT_DODGE_REFILL_TAG)) {
            if (time < tag.getLong(PERFECT_DODGE_REFILL_TAG)) {
                return false;
            }

            tag.remove(PERFECT_DODGE_REFILL_TAG);
            tag.putInt(PERFECT_DODGES_USED_TAG, 0);
        }

        int max = instance.isMastered(entity) ? CONFIG.perfectDodgesMastered : CONFIG.perfectDodges;
        int used = tag.getInt(PERFECT_DODGES_USED_TAG) + 1;
        if (used > max) {
            tag.putLong(PERFECT_DODGE_REFILL_TAG, time + CONFIG.perfectDodgeRefill * 20L);
            instance.markDirty();
            return false;
        }

        tag.putInt(PERFECT_DODGES_USED_TAG, used);
        if (used >= max) {
            tag.putLong(PERFECT_DODGE_REFILL_TAG, time + CONFIG.perfectDodgeRefill * 20L);
        }

        instance.markDirty();
        return true;
    }

    private static void playDodge(LivingEntity entity) {
        TensuraParticleHelper.addServerParticlesAroundSelf(entity, ParticleTypes.SWEEP_ATTACK, 1.0D);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 2.0F, 1.0F);
    }

    private static void ringAlarm(ManasSkillInstance instance, ServerPlayer player, Entity threat) {
        if (player.getRandom().nextBoolean()) {
            instance.addMasteryPoint(player);
        }

        Vec3 eyeVec = player.getEyePosition();
        Vec3 soundPos = eyeVec.add(threat.getEyePosition().subtract(eyeVec).normalize().scale(5.0D));

        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK), SoundSource.PLAYERS, soundPos.x(), eyeVec.y(), soundPos.z(), 1.0F, 1.0F, player.getRandom().nextLong()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        DangerSenseQuirk2 dangerSense = QuirkSkills.DANGER_SENSE2.get();
        SkillAPI.getSkillsFrom(player).getSkill(dangerSense)
                .filter(instance -> instance.getMastery() >= 0.0D)
                .ifPresent(instance -> dangerSense.updateDetect(instance, player));
    }

}

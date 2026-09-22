package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class StrongarmQuirk extends Skill {

    private static final QuirkSkillsConfig.Strongarm CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Strongarm;

    private static final int FLURRY = 0;
    private static final int BULLET_PUNCHES = 1;
    private static final int QUICK_DRAW = 2;

    private static final int LUNGE_TICKS = 8;
    private static final double GROUND_DRAG = 0.454D;
    private static final double CATCH_RANGE = 1.5D;
    private static final double COMBO_RANGE = 6.0D;

    private static final String ROTATIONS_TAG = "rotations";
    private static final String DECAY_TIME_TAG = "rotationDecayTime";
    private static final String QUICK_DRAW_TAG = "quickDrawUntil";

    private static final ResourceLocation ENHANCED_PHYSICAL = ResourceLocation.fromNamespaceAndPath("tracadamia", "strongarm_enhanced_physical");
    private static final ResourceLocation ROTATION_INCREASE = ResourceLocation.fromNamespaceAndPath("tracadamia", "strongarm_rotation_increase");
    private static final ResourceLocation ROTATION_BYPASS = ResourceLocation.fromNamespaceAndPath("tracadamia", "strongarm_rotation_bypass");

    private static final List<Combo> COMBOS = new ArrayList<>();
    private static final Map<LivingEntity, Stun> STUNNED = new HashMap<>();
    private static boolean comboHitting = false;

    private record Stun(Vec3 pos, long until) {}

    private static class Combo {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final int punches;
        private final int duration;
        private final double multiplier;
        private final int rotations;
        private final long lungeEnd;
        private @Nullable LivingEntity target;
        private Vec3 pinPos = Vec3.ZERO;
        private long startTime;
        private int thrown;
        private boolean offHand;

        private Combo(ManasSkillInstance instance, LivingEntity owner, int mode, int punches, int duration, double multiplier, int rotations, long startTime, long lungeEnd) {
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
            this.punches = Math.max(1, punches);
            this.duration = Math.max(1, duration);
            this.multiplier = multiplier;
            this.rotations = rotations;
            this.startTime = startTime;
            this.lungeEnd = lungeEnd;
        }

        private void lock(LivingEntity target) {
            this.target = target;
            this.pinPos = target.position();
        }

        // Punches spread evenly over the duration
        private long nextPunchTime() {
            return this.startTime + (long) this.thrown * this.duration / this.punches;
        }
    }

    public StrongarmQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == FLURRY ? QUICK_DRAW : mode - 1;
        }

        return mode == QUICK_DRAW ? FLURRY : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case FLURRY -> "strongarm.flurry";
            case BULLET_PUNCHES -> "strongarm.bullet_punches";
            case QUICK_DRAW -> "strongarm.quick_draw";
            default -> super.getModeId(instance, mode);
        };
    }

    private static Optional<ManasSkillInstance> getStrongarm(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.STRONGARM.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static boolean hasEmptyHands(LivingEntity entity) {
        return entity.getMainHandItem().isEmpty() && entity.getOffhandItem().isEmpty();
    }

    // used by StrongarmClient
    public static boolean canAlternatePunch(LivingEntity entity) {
        return hasEmptyHands(entity) && getStrongarm(entity).filter(ManasSkillInstance::isToggled).isPresent();
    }

    private static boolean isPunch(LivingEntity owner, DamageSource source) {
        return source.getDirectEntity() == owner && hasEmptyHands(owner) && TensuraDamageHelper.isPhysicalAttack(source);
    }

    private static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * 20.0D);
    }

    // Rotation

    private static int getMaxRotations(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isMastered(entity) ? CONFIG.maxRotationsMastered : CONFIG.maxRotations;
    }

    private static int getRotations(ManasSkillInstance instance, LivingEntity entity) {
        updateRotations(instance, entity);
        return getStoredRotations(instance);
    }

    private static int getStoredRotations(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(ROTATIONS_TAG);
    }

    // Lose rotations after the punch timeout
    private static void updateRotations(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || tag.getInt(ROTATIONS_TAG) <= 0) {
            return;
        }

        long time = entity.level().getGameTime();
        long decayTime = tag.getLong(DECAY_TIME_TAG);
        if (time < decayTime) {
            return;
        }

        long seconds = (time - decayTime) / 20L + 1L;
        tag.putInt(ROTATIONS_TAG, (int) Math.max(0L, tag.getInt(ROTATIONS_TAG) - seconds * CONFIG.rotationLoss));
        tag.putLong(DECAY_TIME_TAG, decayTime + seconds * 20L);
        instance.markDirty();
        updateModifiers(instance, entity);
    }

    private static void addRotations(ManasSkillInstance instance, LivingEntity entity, int amount) {
        if (!instance.isToggled()) {
            return;
        }

        int rotations = Math.min(getMaxRotations(instance, entity), getRotations(instance, entity) + amount);

        CompoundTag tag = instance.getOrCreateTag();
        tag.putInt(ROTATIONS_TAG, rotations);
        tag.putLong(DECAY_TIME_TAG, entity.level().getGameTime() + CONFIG.rotationTimeout * 20L);
        instance.markDirty();
        updateModifiers(instance, entity);
    }

    private static void updateModifiers(ManasSkillInstance instance, LivingEntity entity) {
        boolean mastered = instance.isMastered(entity);
        int rotations = getStoredRotations(instance);
        double perRotation = mastered ? CONFIG.damagePerRotationMastered : CONFIG.damagePerRotation;

        setModifier(entity, Attributes.ATTACK_DAMAGE, ENHANCED_PHYSICAL,
                mastered ? CONFIG.enhancedDamageMastered : CONFIG.enhancedDamage, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.ATTACK_DAMAGE, ROTATION_INCREASE,
                instance.isToggled() ? rotations * perRotation : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, TensuraAttributes.PHYSICAL_RESIST_DEGRADATION, ROTATION_BYPASS,
                mastered && rotations > CONFIG.bypassRotations ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void removeModifiers(LivingEntity entity) {
        setModifier(entity, Attributes.ATTACK_DAMAGE, ENHANCED_PHYSICAL, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        setModifier(entity, Attributes.ATTACK_DAMAGE, ROTATION_INCREASE, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(entity, TensuraAttributes.PHYSICAL_RESIST_DEGRADATION, ROTATION_BYPASS, 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance attributeInstance = entity.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        if (amount == 0.0D) {
            attributeInstance.removeModifier(id);
            return;
        }

        AttributeModifier current = attributeInstance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation) {
            return;
        }

        attributeInstance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
    }

    private static void showRotations(LivingEntity entity, int rotations, int max) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.strongarm.rotations", rotations, max).withStyle(ChatFormatting.GOLD), true);
        }
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (instance.getMastery() < 0.0D || owner.level().isClientSide || comboHitting || !isPunch(owner, source)) {
            return true;
        }

        addRotations(instance, owner, 1);
        return true;
    }

    // Rotation Increase

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        getRotations(instance, entity);
        updateModifiers(instance, entity);

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
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateModifiers(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateModifiers(instance, entity);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateModifiers(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        removeModifiers(entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (counterWithQuickDraw(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == QUICK_DRAW && entity.isShiftKeyDown()) {
            showRotations(entity, getRotations(instance, entity), getMaxRotations(instance, entity));
            return;
        }

        if (isInCombo(entity)) {
            return;
        }

        if (!hasEmptyHands(entity)) {
            fail(entity, "tracadamia.skill.strongarm.hands_full");
            return;
        }

        switch (mode) {
            case FLURRY -> flurry(level, instance, entity);
            case BULLET_PUNCHES -> bulletPunches(level, instance, entity);
            case QUICK_DRAW -> quickDraw(level, instance, entity);
        }
    }

    // Flurry
    private static void flurry(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean mastered = instance.isMastered(entity);
        int punches = mastered ? CONFIG.flurryPunchesMastered : CONFIG.flurryPunches;
        int duration = secondsToTicks(mastered ? CONFIG.flurrySecondsMastered : CONFIG.flurrySeconds);

        long time = level.getGameTime();
        COMBOS.add(new Combo(instance, entity, FLURRY, punches, duration, CONFIG.flurryDamage, 1, time, time));
        instance.addMasteryPoint(entity);
    }

    // Bullet Punches
    private static void bulletPunches(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Vec3 lunge = Vec3.directionFromRotation(0.0F, entity.getYRot()).scale(CONFIG.bulletLungeDistance * GROUND_DRAG);
        entity.setDeltaMovement(lunge.x, entity.getDeltaMovement().y, lunge.z);
        entity.hurtMarked = true;

        boolean mastered = instance.isMastered(entity);
        int punches = mastered ? CONFIG.bulletPunchesMastered : CONFIG.bulletPunches;
        int duration = secondsToTicks(mastered ? CONFIG.bulletSecondsMastered : CONFIG.bulletSeconds);

        long time = level.getGameTime();
        COMBOS.add(new Combo(instance, entity, BULLET_PUNCHES, punches, duration, CONFIG.bulletDamage, CONFIG.bulletRotations, time, time + LUNGE_TICKS));
    }

    private static @Nullable LivingEntity findLungeTarget(ServerLevel level, LivingEntity owner) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.getYRot());
        return level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(CATCH_RANGE),
                        target -> target != owner && target.isAlive() && !target.isSpectator() && !owner.isAlliedTo(target)
                                && forward.dot(target.position().subtract(owner.position())) >= 0.0D)
                .stream()
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
    }

    // Quick Draw
    private static void quickDraw(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        double window = instance.isMastered(entity) ? CONFIG.quickDrawWindowMastered : CONFIG.quickDrawWindow;
        instance.getOrCreateTag().putLong(QUICK_DRAW_TAG, level.getGameTime() + secondsToTicks(window));
        instance.markDirty();
        level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(0.5D), entity.getZ(), 10, 0.4D, 0.5D, 0.4D, 0.1D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.5F);
    }

    // Quick Draw counter
    private static boolean counterWithQuickDraw(LivingEntity owner, DamageSource source) {
        if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || attacker == owner) {
            return false;
        }

        if (!TensuraDamageHelper.isPhysicalAttack(source) || !hasEmptyHands(owner) || isInCombo(owner)) {
            return false;
        }

        ManasSkillInstance instance = getStrongarm(owner).orElse(null);
        CompoundTag tag = instance == null ? null : instance.getTag();
        if (tag == null || tag.getLong(QUICK_DRAW_TAG) < owner.level().getGameTime()) {
            return false;
        }

        tag.remove(QUICK_DRAW_TAG);
        instance.markDirty();

        int punches = instance.isMastered(owner) ? CONFIG.quickDrawPunchesMastered : CONFIG.quickDrawPunches;
        moveBehind(owner, attacker);
        Combo combo = new Combo(instance, owner, QUICK_DRAW, punches, punches * CONFIG.quickDrawInterval, CONFIG.quickDrawDamage, 0, owner.level().getGameTime(), 0L);
        combo.lock(attacker);
        COMBOS.add(combo);
        instance.addMasteryPoint(owner);
        return true;
    }

    private static void moveBehind(LivingEntity owner, LivingEntity target) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 facing = Vec3.directionFromRotation(0.0F, target.getYRot());
        Vec3 behind = target.position().subtract(facing.scale(target.getBbWidth() * 0.5D + owner.getBbWidth() * 0.5D + 0.5D));
        if (!level.noCollision(owner, owner.getDimensions(owner.getPose()).makeBoundingBox(behind))) {
            return;
        }

        owner.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), target.getYRot(), owner.getXRot());
    }

    // Combo punches and stuns
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Combo combo : List.copyOf(COMBOS)) {
            if (!tickCombo(combo)) {
                COMBOS.remove(combo);
            }
        }

        STUNNED.entrySet().removeIf(entry -> {
            LivingEntity target = entry.getKey();
            if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() > entry.getValue().until()) {
                return true;
            }

            pin(target, entry.getValue().pos());
            return false;
        });
    }

    private static boolean isInCombo(LivingEntity entity) {
        return COMBOS.stream().anyMatch(combo -> combo.owner == entity);
    }

    // false once the combo is over
    private static boolean tickCombo(Combo combo) {
        LivingEntity owner = combo.owner;
        if (!owner.isAlive() || owner.isRemoved() || !hasEmptyHands(owner) || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }

        long time = level.getGameTime();

        // Bullet Punches catch
        if (combo.mode == BULLET_PUNCHES && combo.target == null) {
            LivingEntity caught = findLungeTarget(level, owner);
            if (caught == null) {
                if (time >= combo.lungeEnd) {
                    fail(owner, "tensura.targeting.not_targeted");
                    return false;
                }
                return true;
            }

            combo.lock(caught);
            combo.startTime = time;
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            combo.instance.addMasteryPoint(owner);
        }

        LivingEntity target = combo.target;
        if (target != null) {
            if (!target.isAlive() || target.isRemoved() || target.level() != level || target.distanceTo(owner) > COMBO_RANGE) {
                return false;
            }

            pin(target, combo.pinPos);
        }

        if (time < combo.nextPunchTime()) {
            return true;
        }

        punch(level, combo, target != null ? target : ObjectSelectionHelper.getTargetingEntity(owner, getReach(owner), false, false));
        combo.thrown++;
        if (combo.thrown < combo.punches) {
            return true;
        }

        // Quick Draw stun
        if (combo.mode == QUICK_DRAW && target != null && target.isAlive()) {
            int stun = combo.instance.isMastered(owner) ? CONFIG.quickDrawStunMastered : CONFIG.quickDrawStun;
            stun(target, stun * 20);
        }

        return false;
    }

    private static void pin(LivingEntity target, Vec3 pos) {
        if (target.position().distanceToSqr(pos) > 0.0025D) {
            target.teleportTo(pos.x, pos.y, pos.z);
        }

        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
    }

    // Stun, no moving or using skills
    private static void stun(LivingEntity target, int ticks) {
        long until = target.level().getGameTime() + ticks;
        Stun current = STUNNED.get(target);
        STUNNED.put(target, current == null ? new Stun(target.position(), until) : new Stun(current.pos(), Math.max(current.until(), until)));
    }

    public static boolean isStunned(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return false;
        }

        Stun stun = STUNNED.get(entity);
        return stun != null && stun.until() >= entity.level().getGameTime();
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SkillEvents.ACTIVATE_SKILL.register((instance, entity, keyNumber, mode) -> isStunned(entity) ? EventResult.interruptFalse() : EventResult.pass());
            SkillEvents.TOGGLE_SKILL.register((instance, entity) -> isStunned(entity) ? EventResult.interruptFalse() : EventResult.pass());
        });
    }

    private static double getReach(LivingEntity entity) {
        return entity instanceof Player player ? player.entityInteractionRange() : 3.0D;
    }

    private static void punch(ServerLevel level, Combo combo, @Nullable LivingEntity target) {
        LivingEntity owner = combo.owner;

        // Double hand strikes while toggled
        owner.swing(combo.offHand && combo.instance.isToggled() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
        if (owner.swingTime < 0) {
            combo.offHand = !combo.offHand;
        }

        if (target == null || target == owner) {
            return;
        }

        DamageSource source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) * combo.multiplier);

        // Keep iframes and knockback out of the combo
        int invulnerableTime = target.invulnerableTime;
        Vec3 motion = target.getDeltaMovement();
        comboHitting = true;
        try {
            target.invulnerableTime = 0;
            if (target.hurt(source, damage) && combo.rotations > 0) {
                addRotations(combo.instance, owner, combo.rotations);
            }
        } finally {
            comboHitting = false;
            target.invulnerableTime = invulnerableTime;
            target.setDeltaMovement(motion);
        }

        // Stunned while being hit
        if (target.isAlive()) {
            stun(target, CONFIG.moveStunTicks);
        }

        spawnShockwave(level, owner, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 0.9F + owner.getRandom().nextFloat() * 0.3F);
    }

    // Shockwave behind the target
    private static void spawnShockwave(ServerLevel level, LivingEntity owner, LivingEntity target) {
        Vec3 from = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }

        direction = direction.normalize();
        Vec3 pos = to.add(direction.scale(target.getBbWidth() * 0.5D + 0.3D));
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, pos.x, pos.y, pos.z, 0, direction.x, direction.y, direction.z, 1.0D);
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

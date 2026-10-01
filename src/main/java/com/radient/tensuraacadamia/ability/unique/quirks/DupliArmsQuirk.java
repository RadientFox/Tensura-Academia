package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class DupliArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.DupliArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).DupliArms;

    private static final int ARM_GROWTH = 0;
    private static final int ARM_BLOCK = 1;
    private static final int OCTOBLOW = 2;
    private static final int OCTOSPANSION = 3;
    private static final int BACKPACK_CARRY = 4;

    public static final int OCTOBLOW_ARMS = 16;
    public static final int OCTOSPANSION_WINDUP = 8;
    public static final int OCTOSPANSION_TICKS = 22;
    private static final int BLOCK_ARMS = 2;
    private static final int CARRY_ARMS = 2;
    private static final double CARRY_BACK_HEIGHT = 0.5625D;
    private static final double CARRY_BACK_DEPTH = 0.21D;

    private static final String ARMS_TAG = "arms";
    private static final String GUARD_TAG = "armGuard";
    private static final String GUARD_HEALTH_TAG = "armGuardHealth";
    private static final String GUARD_TIME_TAG = "armGuardTime";
    private static final String CARRY_TAG = "carryTarget";
    private static final String CARRY_HEALTH_TAG = "carryHealth";
    private static final String CARRY_TIME_TAG = "carryTime";
    private static final String OCTOBLOW_TAG = "octoblow";
    private static final String OCTOBLOW_HITS_TAG = "octoblowHits";
    private static final String OCTOSPANSION_TAG = "octospansion";
    private static final String OCTOSPANSION_ARMS_TAG = "octospansionArms";
    private static final String SWING_TAG = "armSwing";

    private static final List<Barrage> BARRAGES = new ArrayList<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static final List<Carry> CARRIES = new ArrayList<>();
    private static boolean skillHitting = false;

    private static final class Barrage {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final float mainDamage;
        private final float armDamage;
        private final int hits;
        private int done;

        private Barrage(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float mainDamage, float armDamage, int hits) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.mainDamage = mainDamage;
            this.armDamage = armDamage;
            this.hits = hits;
        }
    }

    private record Strike(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, long time) {}

    private record Carry(ManasSkillInstance instance, LivingEntity owner) {}

    public DupliArmsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ARM_GROWTH -> CONFIG.armGrowthAuraCost;
            case ARM_BLOCK -> CONFIG.armBlockAuraCost;
            case OCTOBLOW -> CONFIG.octoblowAuraCost;
            case OCTOSPANSION -> CONFIG.octospansionAuraCost;
            case BACKPACK_CARRY -> CONFIG.carryAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 5;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == ARM_GROWTH ? BACKPACK_CARRY : mode - 1;
        }

        return mode == BACKPACK_CARRY ? ARM_GROWTH : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ARM_GROWTH -> "dupli_arms.arm_growth";
            case ARM_BLOCK -> "dupli_arms.arm_block";
            case OCTOBLOW -> "dupli_arms.octoblow";
            case OCTOSPANSION -> "dupli_arms.octospansion";
            case BACKPACK_CARRY -> "dupli_arms.backpack_carry";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case ARM_GROWTH -> armGrowth(level, instance, entity);
            case ARM_BLOCK -> armBlock(level, instance, entity);
            case OCTOBLOW -> octoblow(level, instance, entity);
            case OCTOSPANSION -> octospansion(level, instance, entity);
            case BACKPACK_CARRY -> backpackCarry(level, instance, entity);
        }
    }

    private static Optional<ManasSkillInstance> getDupliArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.DUPLI_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Arms, shared with DupliArmsClient

    public static int getArms(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(ARMS_TAG);
    }

    public static boolean isGuarding(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(GUARD_TAG);
    }

    public static boolean isCarrying(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(CARRY_TAG);
    }

    // Blocking and carrying uses two arms
    public static int getBusyArms(ManasSkillInstance instance) {
        return (isGuarding(instance) ? BLOCK_ARMS : 0) + (isCarrying(instance) ? CARRY_ARMS : 0);
    }

    public static int getFreeArms(ManasSkillInstance instance) {
        return Math.max(0, getArms(instance) - getBusyArms(instance));
    }

    public static long getOctoblowStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, OCTOBLOW_TAG);
    }

    public static int getOctoblowHits(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(OCTOBLOW_HITS_TAG);
    }

    public static long getOctospansionStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, OCTOSPANSION_TAG);
    }

    public static int getOctospansionArms(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(OCTOSPANSION_ARMS_TAG);
    }

    public static long getSwingTime(ManasSkillInstance instance, int slot) {
        return MultiArms.getSwingTime(instance, SWING_TAG + slot);
    }

    // Arms hold items while Extra Arms is on
    public static int getArmSlots(LivingEntity entity) {
        return getDupliArms(entity).filter(ManasSkillInstance::isToggled).map(DupliArmsQuirk::getArms).orElse(0);
    }

    private static int getMaxArms(ManasSkillInstance instance, LivingEntity entity) {
        return Math.min(MultiArms.SLOTS, instance.isMastered(entity) ? CONFIG.maxArmsMastered : CONFIG.maxArms);
    }

    private static void setArms(ManasSkillInstance instance, LivingEntity entity, int arms) {
        instance.getOrCreateTag().putInt(ARMS_TAG, arms);

        if (isCarrying(instance) && getBusyArms(instance) > arms) {
            stopCarry(instance, entity);
        }

        if (isGuarding(instance) && getBusyArms(instance) > arms) {
            setGuard(instance, entity, false);
        }

        instance.markDirty();
    }

    private static void showArms(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.dupli_arms.arms", getArms(instance), getMaxArms(instance, entity)), true);
        }
    }

    // Extra Arms

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
        if (isCarrying(instance) && CARRIES.stream().noneMatch(carry -> carry.owner() == entity)) {
            MultiArms.clearHeld(instance, entity, CARRY_TAG);
        }

        if (!instance.isToggled() || getArms(instance) <= 0) {
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
        stopCarry(instance, entity);
    }

    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity entity, DamageSource source) {
        setArms(instance, entity, 0);
        return true;
    }

    // Tools in the extra arms help dig
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        getDupliArms(player).filter(ManasSkillInstance::isToggled).ifPresent(instance -> {
            float bonus = 0.0F;
            for (int slot = 0; slot < getFreeArms(instance) && slot < MultiArms.SLOTS; slot++) {
                float speed = MultiArms.getExtraOffhand(player, slot).getDestroySpeed(event.getState());
                if (speed > 1.0F) {
                    bonus += speed * (float) CONFIG.miningAssist;
                }
            }

            if (bonus > 0.0F) {
                event.setNewSpeed(event.getNewSpeed() + bonus);
            }
        });
    }

    // Weapons in the extra arms follow up each hit
    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || skillHitting || MultiArms.isArmHitting() || owner.level().isClientSide) {
            return true;
        }

        if (source.getDirectEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source) || MultiArms.hasPendingImpact(instance, owner)) {
            return true;
        }

        int queued = 0;
        for (int slot = 0; slot < getFreeArms(instance) && slot < MultiArms.SLOTS; slot++) {
            ItemStack weapon = MultiArms.getExtraOffhand(owner, slot);
            if (MultiArms.isWeapon(weapon)) {
                queued++;
                float damage = (float) (MultiArms.getArmDamage(owner, weapon) * CONFIG.armDamage);
                MultiArms.queueImpact(instance, owner, target, damage, CONFIG.weaponAssistDelay * queued, SWING_TAG + slot);
            }
        }

        return true;
    }

    // Arm Growth

    private static void armGrowth(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int arms = getArms(instance);
        if (entity.isShiftKeyDown()) {
            if (arms <= 0) {
                fail(entity, "tracadamia.skill.dupli_arms.no_arms");
                return;
            }

            setArms(instance, entity, arms - 1);
            playSound(level, entity, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 1.3F);
            showArms(instance, entity);
            return;
        }

        if (arms >= getMaxArms(instance, entity)) {
            fail(entity, "tracadamia.skill.dupli_arms.max_arms");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ARM_GROWTH)) {
            return;
        }

        setArms(instance, entity, arms + 1);
        playSound(level, entity, SoundEvents.SLIME_SQUISH, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.6D), entity.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.01D);
        showArms(instance, entity);
        instance.setCoolDown(CONFIG.armGrowthCooldown, ARM_GROWTH);
        instance.addMasteryPoint(entity);
    }

    // Uses two arms to guard

    private static void armBlock(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isGuarding(instance)) {
            setGuard(instance, entity, false);
            instance.setCoolDown(CONFIG.armBlockCooldown, ARM_BLOCK);
            return;
        }

        if (getFreeArms(instance) < BLOCK_ARMS) {
            fail(entity, Component.translatable("tracadamia.skill.dupli_arms.not_enough_arms", BLOCK_ARMS));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ARM_BLOCK)) {
            return;
        }

        setGuard(instance, entity, true);
        playSound(level, entity, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 0.8F);
        instance.addMasteryPoint(entity);
    }

    private static void setGuard(ManasSkillInstance instance, LivingEntity entity, boolean guard) {
        setArmHealth(instance, entity, GUARD_HEALTH_TAG, GUARD_TIME_TAG, getGuardHealth(instance, entity));
        CompoundTag tag = instance.getOrCreateTag();
        if (guard) {
            tag.putBoolean(GUARD_TAG, true);
        } else {
            tag.remove(GUARD_TAG);
        }

        instance.markDirty();
    }

    public static double getGuardHealth(ManasSkillInstance instance, LivingEntity entity) {
        return getArmHealth(instance, entity, GUARD_HEALTH_TAG, GUARD_TIME_TAG, isGuarding(instance));
    }

    public static double getCarryHealth(ManasSkillInstance instance, LivingEntity entity) {
        return getArmHealth(instance, entity, CARRY_HEALTH_TAG, CARRY_TIME_TAG, isCarrying(instance));
    }

    // Durability left on the blocking or carrying arms
    private static double getArmHealth(ManasSkillInstance instance, LivingEntity entity, String healthTag, String timeTag, boolean busy) {
        double max = entity.getMaxHealth() * CONFIG.armBlockDurability;
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(healthTag)) {
            return max;
        }

        double health = tag.getDouble(healthTag);
        if (!busy) {
            health += (entity.level().getGameTime() - tag.getLong(timeTag)) / 20.0D * max * CONFIG.armBlockRegen;
        }

        return Math.min(max, health);
    }

    private static void setArmHealth(ManasSkillInstance instance, LivingEntity entity, String healthTag, String timeTag, double health) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.putDouble(healthTag, health);
        tag.putLong(timeTag, entity.level().getGameTime());
        instance.markDirty();
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide) {
            getDupliArms(entity).filter(DupliArmsQuirk::isGuarding).ifPresent(instance -> block(instance, entity, event));
        }
    }

    private static void block(ManasSkillInstance instance, LivingEntity entity, LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) || source.getSourcePosition() == null || !MultiArms.isInFront(entity, source.getSourcePosition())) {
            return;
        }

        if (source.getDirectEntity() instanceof LivingEntity attacker && source.getDirectEntity() == source.getEntity()) {
            attacker.knockback(0.5D, entity.getX() - attacker.getX(), entity.getZ() - attacker.getZ());
        }

        if (!absorb(instance, entity, event, GUARD_HEALTH_TAG, GUARD_TIME_TAG, getGuardHealth(instance, entity))) {
            instance.getOrCreateTag().remove(GUARD_TAG);
            breakArms(instance, entity, BLOCK_ARMS, ARM_BLOCK);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCarriedAttack(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !(event.getSource().getEntity() instanceof Mob attacker)) {
            return;
        }

        getDupliArms(entity).filter(DupliArmsQuirk::isCarrying).ifPresent(instance -> {
            if (MultiArms.getHeldTarget(instance, entity, CARRY_TAG) == attacker
                    && !absorb(instance, entity, event, CARRY_HEALTH_TAG, CARRY_TIME_TAG, getCarryHealth(instance, entity))) {
                stopCarry(instance, entity);
                breakArms(instance, entity, CARRY_ARMS, BACKPACK_CARRY);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCarrierAttack(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity carrier)) {
            return;
        }

        if (getDupliArms(carrier).filter(DupliArmsQuirk::isCarrying).filter(instance -> MultiArms.getHeldTarget(instance, carrier, CARRY_TAG) == target).isPresent()) {
            event.setCanceled(true);
        }
    }

    private static boolean absorb(ManasSkillInstance instance, LivingEntity entity, LivingIncomingDamageEvent event, String healthTag, String timeTag, double health) {
        float amount = event.getAmount();
        if (amount < health) {
            setArmHealth(instance, entity, healthTag, timeTag, health - amount);
            event.setCanceled(true);
            playSound((ServerLevel) entity.level(), entity, SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + entity.getRandom().nextFloat() * 0.4F);
            if (entity.getRandom().nextInt(3) == 0) {
                instance.addMasteryPoint(entity);
            }
            return true;
        }

        event.setAmount((float) (amount - health));
        setArmHealth(instance, entity, healthTag, timeTag, entity.getMaxHealth() * CONFIG.armBlockDurability);
        return false;
    }

    private static void breakArms(ManasSkillInstance instance, LivingEntity entity, int arms, int mode) {
        ServerLevel level = (ServerLevel) entity.level();
        setArms(instance, entity, Math.max(0, getArms(instance) - arms));
        instance.setCoolDown(CONFIG.armBlockBrokenCooldown, mode);
        playSound(level, entity, SoundEvents.SHIELD_BREAK, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY(0.6D), entity.getZ(), 10, 0.4D, 0.3D, 0.4D, 0.1D);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.dupli_arms.arms_destroyed").withStyle(ChatFormatting.RED), true);
        }
    }

    // Octoblow

    private static void octoblow(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.octoblowRange);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, OCTOBLOW)) {
            return;
        }

        int hits = OCTOBLOW_ARMS * (instance.isMastered(entity) ? CONFIG.octoblowHitsMastered : CONFIG.octoblowHits);
        BARRAGES.add(new Barrage(instance, entity, target, (float) getMeleeDamage(entity), (float) (getBaseDamage(entity) * CONFIG.armDamage), hits));

        CompoundTag tag = instance.getOrCreateTag();
        tag.putLong(OCTOBLOW_TAG, level.getGameTime());
        tag.putInt(OCTOBLOW_HITS_TAG, hits);
        instance.markDirty();

        playSound(level, entity, SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.8F);
        instance.setCoolDown(CONFIG.octoblowCooldown, OCTOBLOW);
        instance.addMasteryPoint(entity);
    }

    // true once the barrage is over
    private static boolean tickBarrage(Barrage barrage) {
        LivingEntity owner = barrage.owner;
        LivingEntity target = barrage.target;
        if (!owner.isAlive() || !target.isAlive() || owner.level() != target.level() || target.distanceTo(owner) > CONFIG.octoblowRange + 3.0D) {
            return true;
        }

        boolean mainArm = barrage.done % OCTOBLOW_ARMS == 0;
        barrage.done++;
        boolean last = barrage.done >= barrage.hits;
        hit(barrage.instance, owner, target, mainArm ? barrage.mainDamage : barrage.armDamage, OCTOBLOW);

        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, Math.min(motion.y, 0.1D), 0.0D);
        if (last) {
            Vec3 look = owner.getLookAngle();
            target.knockback(1.2D, -look.x, -look.z);
        }
        target.hurtMarked = true;

        ServerLevel level = (ServerLevel) owner.level();
        Vec3 spot = target.position().add(0.0D, target.getBbHeight() * (0.3D + owner.getRandom().nextDouble() * 0.5D), 0.0D);
        level.sendParticles(ParticleTypes.CRIT, spot.x, spot.y, spot.z, 3, 0.25D, 0.25D, 0.25D, 0.2D);
        if (barrage.done % 2 == 0) {
            level.playSound(null, spot.x, spot.y, spot.z, barrage.done % 4 == 0 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.9F + owner.getRandom().nextFloat() * 0.5F);
        }

        if (mainArm || barrage.done % 3 == 0) {
            owner.swing(InteractionHand.MAIN_HAND, true);
        }

        return last;
    }


    private static void octospansion(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.octospansionRange);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, OCTOSPANSION)) {
            return;
        }

        int arms = instance.isMastered(entity) ? CONFIG.octospansionArmsMastered : CONFIG.octospansionArms;
        float damage = (float) (getMeleeDamage(entity) + getBaseDamage(entity) * CONFIG.armDamage * arms);
        STRIKES.add(new Strike(instance, entity, target, damage, level.getGameTime() + OCTOSPANSION_WINDUP));

        CompoundTag tag = instance.getOrCreateTag();
        tag.putLong(OCTOSPANSION_TAG, level.getGameTime());
        tag.putInt(OCTOSPANSION_ARMS_TAG, arms);
        instance.markDirty();

        playSound(level, entity, SoundEvents.SLIME_SQUISH, 1.2F, 0.5F);
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.octospansionCooldown, OCTOSPANSION);
        instance.addMasteryPoint(entity);
    }

    private static void strike(Strike strike) {
        LivingEntity owner = strike.owner();
        LivingEntity target = strike.target();
        if (!owner.isAlive() || owner.level() != target.level() || !(owner.level() instanceof ServerLevel level)) {
            return;
        }

        owner.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, owner, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.6F);
        if (!target.isAlive() || target.distanceTo(owner) > CONFIG.octospansionRange + 2.0D) {
            playSound(level, owner, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 0.6F);
            return;
        }

        hit(strike.instance(), owner, target, strike.damage(), OCTOSPANSION);
        Vec3 look = owner.getLookAngle();
        target.knockback(CONFIG.octospansionKnockback, -look.x, -look.z);
        target.hurtMarked = true;

        Vec3 spot = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y, spot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, spot.x, spot.y, spot.z, 20, 0.4D, 0.4D, 0.4D, 0.5D);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
    }

    private static void backpackCarry(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isCarrying(instance)) {
            stopCarry(instance, entity);
            instance.setCoolDown(CONFIG.carryCooldown, BACKPACK_CARRY);
            return;
        }

        if (getFreeArms(instance) < CARRY_ARMS) {
            fail(entity, Component.translatable("tracadamia.skill.dupli_arms.not_enough_arms", CARRY_ARMS));
            return;
        }

        LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.carryRange + entity.getBbWidth());
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (target.getBbHeight() > CONFIG.carryMaxSize || target.getBbWidth() > CONFIG.carryMaxSize) {
            fail(entity, "tracadamia.skill.dupli_arms.too_big");
            return;
        }

        if (!MultiArms.canGrab(target)) {
            fail(entity, "tracadamia.skill.arms.boss");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, BACKPACK_CARRY)) {
            return;
        }

        target.stopRiding();
        setArmHealth(instance, entity, CARRY_HEALTH_TAG, CARRY_TIME_TAG, getCarryHealth(instance, entity));
        instance.getOrCreateTag().putUUID(CARRY_TAG, target.getUUID());
        instance.markDirty();
        CARRIES.add(new Carry(instance, entity));
        if (target instanceof NeutralMob neutral) {
            neutral.setPersistentAngerTarget(entity.getUUID());
            neutral.startPersistentAngerTimer();
        }

        if (target instanceof Mob mob && (mob instanceof Enemy || mob instanceof NeutralMob)) {
            mob.setTarget(entity);
        }
        MultiArms.holdAt(entity, target, getCarryOffset(entity, target), MultiArms.HOLD_BODY);
        playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.8F);
        instance.addMasteryPoint(entity);
    }

    private static Vec3 getCarryOffset(LivingEntity owner, LivingEntity target) {
        double height = Math.max(0.0D, owner.getBbHeight() * CARRY_BACK_HEIGHT - target.getBbHeight() * 0.5D);
        return new Vec3(0.0D, height, -(owner.getBbWidth() * CARRY_BACK_DEPTH + target.getBbWidth() * 0.5D));
    }

    private static void stopCarry(ManasSkillInstance instance, LivingEntity entity) {
        if (!isCarrying(instance)) {
            return;
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, entity, CARRY_TAG);
        setArmHealth(instance, entity, CARRY_HEALTH_TAG, CARRY_TIME_TAG, getCarryHealth(instance, entity));
        MultiArms.clearHeld(instance, entity, CARRY_TAG);
        CARRIES.removeIf(carry -> carry.owner() == entity);
        if (target != null && entity.level() instanceof ServerLevel level) {
            Vec3 back = Vec3.directionFromRotation(0.0F, entity.yBodyRot).reverse();
            Vec3 pos = entity.position().add(back.scale(entity.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.3D));
            target.teleportTo(pos.x, entity.getY(), pos.z);
            playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.1F);
        }
    }

    // true once the carry is over
    private static boolean tickCarry(Carry carry) {
        ManasSkillInstance instance = carry.instance();
        LivingEntity owner = carry.owner();
        if (!isCarrying(instance)) {
            return true;
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, owner, CARRY_TAG);
        if (!owner.isAlive() || owner.isRemoved() || target == null) {
            setArmHealth(instance, owner, CARRY_HEALTH_TAG, CARRY_TIME_TAG, getCarryHealth(instance, owner));
            MultiArms.clearHeld(instance, owner, CARRY_TAG);
            instance.setCoolDown(CONFIG.carryCooldown, BACKPACK_CARRY);
            return true;
        }

        MultiArms.holdAt(owner, target, getCarryOffset(owner, target), MultiArms.HOLD_BODY);
        target.setYRot(owner.yBodyRot);
        target.setYBodyRot(owner.yBodyRot);
        target.setYHeadRot(owner.yBodyRot);
        if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(owner);
        }

        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!BARRAGES.isEmpty()) {
            BARRAGES.removeIf(DupliArmsQuirk::tickBarrage);
        }

        if (!STRIKES.isEmpty()) {
            List<Strike> ready = STRIKES.stream().filter(strike -> strike.owner().level().getGameTime() >= strike.time()).toList();
            STRIKES.removeAll(ready);
            ready.forEach(DupliArmsQuirk::strike);
        }

        if (!CARRIES.isEmpty()) {
            CARRIES.removeIf(DupliArmsQuirk::tickCarry);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BARRAGES.clear();
        STRIKES.clear();
        CARRIES.clear();
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    // Melee damage without the held weapon, for arms other than the main one
    private static double getBaseDamage(LivingEntity entity) {
        return entity.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 1.0D : MultiArms.getArmDamage(entity, ItemStack.EMPTY);
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode) {
        skillHitting = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, mode), damage);
        } finally {
            skillHitting = false;
        }
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, String key) {
        fail(entity, Component.translatable(key));
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

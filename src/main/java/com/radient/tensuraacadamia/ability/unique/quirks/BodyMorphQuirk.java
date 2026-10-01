package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.SizeReach;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class BodyMorphQuirk extends Skill {

    private static final QuirkSkillsConfig.BodyMorph CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).BodyMorph;

    private static final int SMASH = 0;
    private static final int GRAB = 1;
    private static final int SQUEEZE = 2;

    public static final int SMASH_TICKS = 14;
    public static final int SMASH_HIT = 6;
    private static final int LIFTS = 30;
    private static final int THROW_FLIGHT = 100;

    private static final String SMASH_TAG = "smash";
    private static final String GRAB_TAG = "grabTarget";
    private static final String ROCK_TAG = "holdingRock";
    private static final String SQUEEZE_TAG = "squeezeTarget";
    private static final String SQUEEZED_UNTIL_TAG = "tracadamia_squeezed_until";

    private static final ResourceLocation ENHANCED_BODY = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "body_morph");

    private static final List<Smash> SMASHES = new ArrayList<>();
    private static final List<Grab> GRABS = new ArrayList<>();

    private record Smash(ManasSkillInstance instance, LivingEntity owner, long time) {}

    private record Grab(ManasSkillInstance instance, LivingEntity owner) {}

    public BodyMorphQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SMASH -> CONFIG.smashAuraCost;
            case GRAB -> CONFIG.grabAuraCost;
            case SQUEEZE -> CONFIG.squeezeAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == SMASH ? SQUEEZE : mode - 1;
        }

        return mode == SQUEEZE ? SMASH : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SMASH -> "body_morph.smash";
            case GRAB -> "body_morph.grab";
            case SQUEEZE -> "body_morph.squeeze";
            default -> super.getModeId(instance, mode);
        };
    }

    private static Optional<ManasSkillInstance> getBodyMorph(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.BODY_MORPH.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Shared with BodyMorphClient
    public static long getSmashStart(ManasSkillInstance instance) {
        return MultiArms.getSwingTime(instance, SMASH_TAG);
    }

    public static boolean isGrabbing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && (tag.hasUUID(GRAB_TAG) || tag.getBoolean(ROCK_TAG));
    }

    public static boolean isSqueezing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(SQUEEZE_TAG);
    }

    // Enhanced Body, true passive
    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return false;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        applyBody(entity, true);

        if (instance.getTag() != null && instance.getTag().hasUUID(GRAB_TAG) && GRABS.stream().noneMatch(grab -> grab.owner() == entity)) {
            MultiArms.clearHeld(instance, entity, GRAB_TAG);
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        applyBody(entity, true);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer player, boolean conqueredEnd) {
        super.onRespawn(instance, player, conqueredEnd);
        applyBody(player, true);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        applyBody(entity, false);
        release(instance, entity);
        TelekinesisQuirk.dropRock(entity);
        SizeReach.update(entity, this);

    }

    // Increased stats
    private static void applyBody(LivingEntity entity, boolean on) {
        setModifier(entity, Attributes.SCALE, on ? CONFIG.sizeBonus : 0.0D);
        setModifier(entity, Attributes.ARMOR, on ? CONFIG.armorBonus : 0.0D);
        setModifier(entity, Attributes.ATTACK_DAMAGE, on ? CONFIG.meleeBonus : 0.0D);
//        setModifier(entity, Attributes.BLOCK_INTERACTION_RANGE, on ? CONFIG.sizeBonus * 2.0D : 0.0D);
//        setModifier(entity, Attributes.ENTITY_INTERACTION_RANGE, on ? CONFIG.sizeBonus * 2.0D : 0.0D);
        SizeReach.update(entity);

    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        AttributeModifier current = instance.getModifier(ENHANCED_BODY);
        if (amount == 0.0D) {
            instance.removeModifier(ENHANCED_BODY);
        } else if (current == null || current.amount() != amount) {
            instance.addOrReplacePermanentModifier(new AttributeModifier(ENHANCED_BODY, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case SMASH -> smash(level, instance, entity);
            case GRAB -> grab(level, instance, entity);
        }
    }

    // Smash
    private static void smash(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, SMASH)) {
            return;
        }

        instance.getOrCreateTag().putLong(SMASH_TAG, level.getGameTime());
        instance.markDirty();
        SMASHES.add(new Smash(instance, entity, level.getGameTime() + SMASH_HIT));
        playSound(level, entity, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1.0F, 0.5F);
        instance.setCoolDown(CONFIG.smashCooldown, SMASH);
        instance.addMasteryPoint(entity);
    }

    private static void landSmash(Smash smash) {
        LivingEntity owner = smash.owner();
        if (!owner.isAlive() || !(owner.level() instanceof ServerLevel level)) {
            return;
        }

        boolean mastered = smash.instance().isMastered(owner);
        double half = (mastered ? CONFIG.smashSizeMastered : CONFIG.smashSize) * 0.5D;
        float damage = (float) (mastered ? CONFIG.smashDamageMastered : CONFIG.smashDamage);
        AABB area = new AABB(owner.getX() - half, owner.getY() - 1.0D, owner.getZ() - half, owner.getX() + half, owner.getY() + 2.0D, owner.getZ() + half);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != owner && target.isAlive())) {
            hit(smash.instance(), owner, target, damage + getBonusDamage(owner, CONFIG.smashDamagePercent), SMASH); // add physical damage % here
            target.push(0.0D, 0.4D, 0.0D);
            target.hurtMarked = true;
        }

        // The ground in the square jumps
        RandomSource random = level.random;
        BlockPos center = owner.blockPosition();
        int reach = (int) Math.ceil(half);
        int lifts = 0;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                BlockPos surface = GroundBlocks.findSurface(level, center.offset(x, 0, z));
                if (surface == null || surface.getY() >= owner.getY()) {
                    continue;
                }

                if (lifts < LIFTS && random.nextFloat() < 0.4F && GroundBlocks.lift(level, smash.instance(), owner, surface, 0.25D + random.nextDouble() * 0.1D)) {
                    lifts++;
                } else if (random.nextInt(2) == 0) {
                    GroundBlocks.dust(level, surface, level.getBlockState(surface), 2);
                }
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION, owner.getX(), owner.getY() + 0.2D, owner.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, owner, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.7F);
        playSound(level, owner, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.6F);
    }

    // Grab
    private static void grab(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        boolean mastered = instance.isMastered(entity);
        double distance = mastered ? CONFIG.throwDistanceMastered : CONFIG.throwDistance;
        if (tag.hasUUID(GRAB_TAG)) {
            LivingEntity target = MultiArms.getHeldTarget(instance, entity, GRAB_TAG);
            release(instance, entity);
            if (target == null || entity.isShiftKeyDown()) {
                return;
            }

            Vec3 velocity = MultiArms.getThrowVelocity(entity, target, target.getBoundingBox().getCenter(), distance, CONFIG.throwSpeed);
            MultiArms.throwAt(instance, entity, target, velocity, (float) (mastered ? CONFIG.throwImpactDamageMastered : CONFIG.throwImpactDamage), GRAB, THROW_FLIGHT);
            entity.swing(InteractionHand.MAIN_HAND, true);
            playSound(level, entity, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
            instance.setCoolDown(CONFIG.grabCooldown, GRAB);
            return;
        }

        if (TelekinesisQuirk.isHoldingRock(entity)) {
            tag.remove(ROCK_TAG);
            instance.markDirty();
            if (entity.isShiftKeyDown()) {
                TelekinesisQuirk.dropRock(entity);
                return;
            }

            TelekinesisQuirk.throwRock(level, entity, distance, CONFIG.throwSpeed);
            instance.setCoolDown(CONFIG.grabCooldown, GRAB);
            return;
        }

        LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.grabRange + entity.getBbWidth());
        if (target != null) {
            if (!MultiArms.canGrab(target)) {
                fail(entity, "tracadamia.skill.arms.boss");
                return;
            }

            if (!MultiArms.isNoLarger(target, entity)) {
                fail(entity, "tracadamia.skill.body_morph.too_big");
                return;
            }

            if (EnergyHelper.isOutOfEnergy(entity, instance, GRAB)) {
                return;
            }

            target.stopRiding();
            tag.putUUID(GRAB_TAG, target.getUUID());
            instance.markDirty();
            GRABS.add(new Grab(instance, entity));
            MultiArms.holdAt(entity, target, getGrabOffset(entity), MultiArms.HOLD_BODY);
            playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.6F);
            instance.addMasteryPoint(entity);
            return;
        }

        // Nothing to grab
        double damage = mastered ? CONFIG.rockDamageMastered : CONFIG.rockDamage;
        if (!TelekinesisQuirk.liftRock(level, instance, entity, CONFIG.rockBlocks, damage)) {
            fail(entity, "tracadamia.skill.body_morph.no_rock");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, GRAB)) {
            TelekinesisQuirk.dropRock(entity);
            return;
        }

        tag.putBoolean(ROCK_TAG, true);
        instance.markDirty();
        GRABS.add(new Grab(instance, entity));
        playSound(level, entity, SoundEvents.ROOTED_DIRT_BREAK, 1.5F, 0.6F);
        instance.addMasteryPoint(entity);
    }

    // Held over the head
    private static Vec3 getGrabOffset(LivingEntity entity) {
        return new Vec3(0.0D, entity.getBbHeight() * TelekinesisBlockEntity.ARMS_HEIGHT, -entity.getBbHeight() * TelekinesisBlockEntity.ARMS_BACK);
    }

    private static void release(ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, GRAB_TAG);
        GRABS.removeIf(grab -> grab.owner() == entity);
    }

    // true once nothing is held
    private static boolean tickGrab(Grab grab) {
        ManasSkillInstance instance = grab.instance();
        LivingEntity owner = grab.owner();
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getBoolean(ROCK_TAG)) {
            if (TelekinesisQuirk.isHoldingRock(owner) && owner.isAlive()) {
                return false;
            }

            tag.remove(ROCK_TAG);
            instance.markDirty();
            return true;
        }

        if (!tag.hasUUID(GRAB_TAG)) {
            return true;
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, owner, GRAB_TAG);
        if (!owner.isAlive() || owner.isRemoved() || target == null) {
            MultiArms.clearHeld(instance, owner, GRAB_TAG);
            instance.setCoolDown(CONFIG.grabCooldown, GRAB);
            return true;
        }

        MultiArms.holdAt(owner, target, getGrabOffset(owner), MultiArms.HOLD_BODY);
        if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(owner);
        }

        return false;
    }

    // Squeeze
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != SQUEEZE || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks == 0) {
            LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.grabRange + entity.getBbWidth());
            if (target == null) {
                fail(entity, "tensura.targeting.not_targeted");
                return false;
            }

            if (!MultiArms.canGrab(target)) {
                fail(entity, "tracadamia.skill.arms.boss");
                return false;
            }

            if (!MultiArms.isNoLarger(target, entity)) {
                fail(entity, "tracadamia.skill.body_morph.too_big");
                return false;
            }

            if (EnergyHelper.isOutOfEnergy(entity, instance, SQUEEZE)) {
                return false;
            }

            target.stopRiding();
            instance.getOrCreateTag().putUUID(SQUEEZE_TAG, target.getUUID());
            instance.markDirty();
            playSound(level, entity, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.5F);
        }

        LivingEntity target = MultiArms.getHeldTarget(instance, entity, SQUEEZE_TAG);
        if (target == null) {
            endSqueeze(instance, entity);
            return false;
        }

        MultiArms.holdAt(entity, target, new Vec3(0.0D, entity.getBbHeight() * 0.45D - target.getBbHeight() * 0.5D, entity.getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + 0.1D), MultiArms.HOLD_BODY);
        target.getPersistentData().putLong(SQUEEZED_UNTIL_TAG, level.getGameTime() + 2L);

        if (heldTicks > 0 && heldTicks % 20 == 0) {
            hit(instance, entity, target, (float) (instance.isMastered(entity) ? CONFIG.squeezeDamageMastered : CONFIG.squeezeDamage) + getBonusDamage(entity, CONFIG.squeezeDamagePercent), SQUEEZE);
            playSound(level, target, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.5F);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5D), target.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.2D);
            instance.addMasteryPoint(entity);
        }

        if (heldTicks >= CONFIG.squeezeSeconds * 20) {
            endSqueeze(instance, entity);
            return false;
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == SQUEEZE) {
            endSqueeze(instance, entity);
        }
    }

    private static void endSqueeze(ManasSkillInstance instance, LivingEntity entity) {
        if (isSqueezing(instance)) {
            MultiArms.clearHeld(instance, entity, SQUEEZE_TAG);
            instance.setCoolDown(CONFIG.squeezeCooldown, SQUEEZE);
        }
    }

    public static boolean isSqueezed(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(SQUEEZED_UNTIL_TAG) && data.getLong(SQUEEZED_UNTIL_TAG) >= entity.level().getGameTime();
    }

    // No skills while being squeezed
    private static EventResult blockSkill(LivingEntity entity) {
        if (entity.level().isClientSide || !isSqueezed(entity)) {
            return EventResult.pass();
        }

        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.body_morph.squeezed").withStyle(ChatFormatting.RED), true);
        }

        return EventResult.interruptFalse();
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SkillEvents.ACTIVATE_SKILL.register((instance, entity, keyNumber, mode) -> blockSkill(entity));
            SkillEvents.TOGGLE_SKILL.register((instance, entity) -> blockSkill(entity));
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SMASHES.isEmpty()) {
            List<Smash> ready = SMASHES.stream().filter(smash -> smash.owner().level().getGameTime() >= smash.time()).toList();
            SMASHES.removeAll(ready);
            ready.forEach(BodyMorphQuirk::landSmash);
        }

        if (!GRABS.isEmpty()) {
            GRABS.removeIf(BodyMorphQuirk::tickGrab);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SMASHES.clear();
        GRABS.clear();
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode) {
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, mode), damage);
    }

    private static float getBonusDamage(LivingEntity owner, double percent) //gets physical damage % increase from configs
    {
        AttributeInstance attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 0.0F : (float) (attack.getValue() * percent);
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

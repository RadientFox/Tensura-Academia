package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.ScrappyThrustProjectile;
import com.radient.tensuraacadamia.network.SlideAndGlideBurstPayload;
import com.radient.tensuraacadamia.network.SlideAndGlideMotionPayload;
import com.radient.tensuraacadamia.network.SlideAndGlideStatePayload;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class SlideAndGlideQuirk extends Skill {

    private static final QuirkSkillsConfig.SlideAndGlide CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).SlideAndGlide;

    public static final int SLIDE = 0;
    public static final int SCRAPPY_THRUST = 1;
    public static final int SLAP_STYLE = 2;
    public static final int KNUCKLE_STYLE = 3;
    private static final int MODES = 4;

    public static final int FLAG_SLIDING = 1;
    public static final int FLAG_SLAPPING = 2;
    public static final int FLAG_KNUCKLE = 4;
    public static final int FLAG_OOMPH = 8;

    private static final String SLIDING_TAG = "sliding";
    private static final String SLIDE_HELD_TAG = "slideHeld";
    private static final String SLIDE_SPEED_TAG = "slideSpeed";
    private static final String KNUCKLE_TAG = "knuckle";
    private static final String OOMPH_TAG = "oomph";

    private static final ResourceLocation SLIDE_SPEED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_speed");
    private static final ResourceLocation SLIDE_STEP = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_step");
    private static final ResourceLocation CRAWL = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "slide_and_glide_crawl");

    private record Strike(LivingEntity owner, LivingEntity target, ManasSkillInstance instance, float light, float blast, float selfBlast) {}

    private static final Map<Integer, Integer> SENT_FLAGS = new HashMap<>();
    private static final Map<UUID, Integer> LOW_HEALTH = new HashMap<>();
    private static final Map<UUID, Integer> MOTION = new HashMap<>();
    private static final Set<UUID> FORCED_CRAWL = new HashSet<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static boolean striking;

    public SlideAndGlideQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/unique/slide_and_glide.png");
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return CONFIG.acquiringCost;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == SCRAPPY_THRUST ? CONFIG.thrustAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int next = mode;
        for (int step = 0; step < MODES; step++) {
            next = Math.floorMod(next + (reverse ? -1 : 1), MODES);
            if (isModeUnlocked(instance, next)) {
                return next;
            }
        }

        return SLIDE;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SLIDE -> "slide_and_glide.slide";
            case SCRAPPY_THRUST -> "slide_and_glide.scrappy_thrust";
            case SLAP_STYLE -> "slide_and_glide.slap_style";
            case KNUCKLE_STYLE -> "slide_and_glide.knuckle_style";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getSlideAndGlide(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.SLIDE_AND_GLIDE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double masteryShare(ManasSkillInstance instance) {
        return Mth.clamp(instance.getMastery() / CONFIG.masteryPoints, 0.0D, 1.0D);
    }

    private static boolean isModeUnlocked(ManasSkillInstance instance, int mode) {
        double share = masteryShare(instance);
        return switch (mode) {
            case SLIDE -> true;
            case SCRAPPY_THRUST -> share >= CONFIG.toggleUnlockMastery;
            case SLAP_STYLE -> share >= CONFIG.slapUnlockMastery;
            case KNUCKLE_STYLE -> share >= 1.0D;
            default -> false;
        };
    }

    public static boolean isSlideToggle(ManasSkillInstance instance) {
        return masteryShare(instance) >= CONFIG.toggleUnlockMastery;
    }

    private static boolean flag(ManasSkillInstance instance, String key) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(key);
    }

    private static void setFlag(ManasSkillInstance instance, String key, boolean value) {
        if (flag(instance, key) == value) {
            return;
        }

        if (value) {
            instance.getOrCreateTag().putBoolean(key, true);
        } else {
            instance.getOrCreateTag().remove(key);
        }

        instance.markDirty();
    }

    public static boolean isSliding(ManasSkillInstance instance) {
        return flag(instance, SLIDING_TAG) || flag(instance, SLIDE_HELD_TAG);
    }

    private static boolean isKnuckle(ManasSkillInstance instance) {
        return flag(instance, KNUCKLE_TAG) && isModeUnlocked(instance, KNUCKLE_STYLE);
    }

    private static boolean isOomph(ManasSkillInstance instance) {
        return flag(instance, OOMPH_TAG);
    }

    private static double damageMultiplier(ManasSkillInstance instance) {
        return isOomph(instance) ? CONFIG.oomphDamageMultiplier : 1.0D;
    }

    public static double getSlideSpeed(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(SLIDE_SPEED_TAG);
    }

    public static boolean canWallSlide(LivingEntity entity) {
        return entity instanceof Player && !entity.isSpectator() && entity.getPose() != Pose.SWIMMING && (!entity.onGround() || entity.horizontalCollision)
                && getSlideAndGlide(entity).filter(instance -> isSliding(instance) && isSlideToggle(instance)).isPresent() && ExtraArmsQuirk.isTouchingWall(entity);
    }

    private static boolean isOnWallOrCeiling(LivingEntity entity) {
        return canWallSlide(entity) || MOTION.getOrDefault(entity.getUUID(), SlideAndGlideMotionPayload.UPRIGHT) == SlideAndGlideMotionPayload.CEILING;
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
            getSlideAndGlide(event.getEntity()).ifPresent(instance -> ((SlideAndGlideQuirk) instance.getSkill()).serverTick(instance, event.getEntity()));
        }
    }

    private void serverTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        if (flag(instance, SLIDE_HELD_TAG) && !TickingSkill.isTickingSkill(entity, this, SLIDE)) {
            setFlag(instance, SLIDE_HELD_TAG, false);
        }

        updateOomph(instance, entity);
        tickSlide(instance, entity);
        int motion = MOTION.getOrDefault(entity.getUUID(), SlideAndGlideMotionPayload.UPRIGHT);
        if (motion != SlideAndGlideMotionPayload.UPRIGHT && !isSliding(instance)) {
            setMotion(entity, SlideAndGlideMotionPayload.UPRIGHT);
        } else {
            applyMotion(entity, motion);
        }
        syncFlags(entity, getFlags(instance, entity));
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        CompoundTag tag = instance.getOrCreateTag();
        for (String key : List.of(SLIDING_TAG, SLIDE_HELD_TAG, SLIDE_SPEED_TAG, KNUCKLE_TAG, OOMPH_TAG)) {
            tag.remove(key);
        }

        instance.markDirty();
        LOW_HEALTH.remove(entity.getUUID());
        applySlide(entity, 0.0D);
        setMotion(entity, SlideAndGlideMotionPayload.UPRIGHT);
        syncFlags(entity, 0);
    }

    public static void receiveMotion(ServerPlayer player, int kind) {
        boolean sliding = getSlideAndGlide(player).filter(SlideAndGlideQuirk::isSliding).isPresent();
        if (SlideAndGlideMotionPayload.isJump(kind)) {
            if (!sliding) {
                return;
            }

            player.fallDistance = 0.0F;
            if (kind == SlideAndGlideMotionPayload.JUMP_CEILING_DROP) {
                setMotion(player, SlideAndGlideMotionPayload.UPRIGHT);
            }
            PacketDistributor.sendToPlayersTrackingEntity(player, new SlideAndGlideMotionPayload(player.getId(), kind));
            return;
        }

        if (kind >= SlideAndGlideMotionPayload.UPRIGHT && kind <= SlideAndGlideMotionPayload.CEILING) {
            setMotion(player, sliding ? kind : SlideAndGlideMotionPayload.UPRIGHT);
        }
    }

    private static void setMotion(LivingEntity entity, int kind) {
        Integer old = kind == SlideAndGlideMotionPayload.UPRIGHT ? MOTION.remove(entity.getUUID()) : MOTION.put(entity.getUUID(), kind);
        applyMotion(entity, kind);
        if ((old == null ? SlideAndGlideMotionPayload.UPRIGHT : old) != kind) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, new SlideAndGlideMotionPayload(entity.getId(), kind));
        }
    }

    private static void applyMotion(LivingEntity entity, int kind) {
        boolean low = kind != SlideAndGlideMotionPayload.UPRIGHT;
        if (entity instanceof Player player) {
            if (low) {
                player.setForcedPose(Pose.SWIMMING);
                FORCED_CRAWL.add(player.getUUID());
            } else if (FORCED_CRAWL.remove(player.getUUID()) && player.getForcedPose() == Pose.SWIMMING) {
                player.setForcedPose(null);
            }
        }

        Modifiers.set(entity, Attributes.SNEAKING_SPEED, CRAWL, low ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, CRAWL, kind == SlideAndGlideMotionPayload.CRAWL ? CONFIG.crawlSpeedBonus : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level) || !isModeUnlocked(instance, mode)) {
            return;
        }

        switch (mode) {
            case SLIDE -> {
                if (isSlideToggle(instance)) {
                    toggleSlide(instance, entity);
                }
            }
            case SCRAPPY_THRUST -> fireThrust(level, instance, entity);
            case KNUCKLE_STYLE -> toggleKnuckle(instance, entity);
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        return switch (mode) {
            case SLIDE -> {
                if (isSlideToggle(instance)) {
                    yield false;
                }

                if (!entity.level().isClientSide) {
                    setFlag(instance, SLIDE_HELD_TAG, true);
                }
                yield true;
            }
            case SLAP_STYLE -> {
                if (!canSlap(instance)) {
                    yield false;
                }

                if (!entity.level().isClientSide && heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
                    instance.addMasteryPoint(entity);
                }
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == SLIDE && !entity.level().isClientSide) {
            setFlag(instance, SLIDE_HELD_TAG, false);
        }
    }

    private void toggleSlide(ManasSkillInstance instance, LivingEntity entity) {
        boolean sliding = !flag(instance, SLIDING_TAG);
        setFlag(instance, SLIDING_TAG, sliding);
        playSound(entity, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, sliding ? 1.6F : 0.9F);
        message(entity, Component.translatable(sliding ? "tracadamia.skill.slide_and_glide.slide_on" : "tracadamia.skill.slide_and_glide.slide_off").withStyle(ChatFormatting.AQUA));
        if (sliding) {
            instance.addMasteryPoint(entity);
        }
    }

    private void tickSlide(ManasSkillInstance instance, LivingEntity entity) {
        boolean sliding = isSliding(instance);
        double speed = 0.0D;
        if (sliding) {
            speed = CONFIG.minSlideSpeed + (CONFIG.maxSlideSpeed - CONFIG.minSlideSpeed) * masteryShare(instance);
            if (isOomph(instance)) {
                speed = (1.0D + speed) * CONFIG.oomphSpeedMultiplier - 1.0D;
            }

            entity.fallDistance = 0.0F;
            if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
                instance.addMasteryPoint(entity);
            }
        }

        applySlide(entity, speed);
        if (Math.abs(getSlideSpeed(instance) - speed) > 1.0E-4D) {
            instance.getOrCreateTag().putDouble(SLIDE_SPEED_TAG, speed);
            instance.markDirty();
        }
    }

    private static void applySlide(LivingEntity entity, double speed) {
        boolean sliding = speed > 0.0D;
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, SLIDE_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.STEP_HEIGHT, SLIDE_STEP, sliding ? CONFIG.slideStepBonus : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private void fireThrust(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (masteryShare(instance) < CONFIG.freeThrustMastery && !isOnWallOrCeiling(entity)) {
            fail(entity, "tracadamia.skill.slide_and_glide.thrust_needs_surface");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SCRAPPY_THRUST)) {
            return;
        }

        shoot(level, entity, entity.getLookAngle(), (float) (CONFIG.thrustDamage * damageMultiplier(instance)));
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.thrustCooldown, SCRAPPY_THRUST);
        instance.addMasteryPoint(entity);
    }

    private static Vec3 muzzle(LivingEntity entity, Vec3 dir) {
        return entity.getEyePosition().add(0.0D, -0.25D, 0.0D).add(dir.scale(0.6D));
    }

    private static void shoot(ServerLevel level, LivingEntity owner, Vec3 direction, float damage) {
        Vec3 dir = direction.lengthSqr() < 1.0E-6D ? owner.getLookAngle() : direction.normalize();
        Vec3 from = muzzle(owner, dir);
        ScrappyThrustProjectile ball = new ScrappyThrustProjectile(level, owner, damage, CONFIG.thrustLifetime);
        ball.setPos(from.x, from.y - 0.2D, from.z);
        ball.setDeltaMovement(dir.scale(CONFIG.thrustSpeed));
        level.addFreshEntity(ball);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.8F);
        burst(owner, SlideAndGlideBurstPayload.THRUST_FLASH, from, dir);
    }

    public static void thrustHit(LivingEntity owner, LivingEntity target, float damage) {
        DamageSource source = getSlideAndGlide(owner).map(instance -> lightSource(instance, owner, SCRAPPY_THRUST))
                .orElseGet(() -> owner.damageSources().source(TensuraDamageTypes.LIGHT_ELEMENTAL, owner));
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    private static DamageSource lightSource(ManasSkillInstance instance, LivingEntity owner, int mode) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.LIGHT_ELEMENTAL, mode);
        ((TensuraDamageSource) source).tensura$setElement(Element.LIGHT);
        return source;
    }

    private boolean canSlap(ManasSkillInstance instance) {
        return isModeUnlocked(instance, SLAP_STYLE) && !instance.onCoolDown(SLAP_STYLE);
    }

    private boolean isSlapping(ManasSkillInstance instance, LivingEntity entity) {
        return canSlap(instance) && TickingSkill.isTickingSkill(entity, this, SLAP_STYLE);
    }

    private void repel(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0D));
            projectile.setOwner(owner);
            projectile.hurtMarked = true;
        }

        LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != owner ? living
                : direct instanceof LivingEntity living && living != owner ? living : null;
        Vec3 aim = attacker == null ? owner.getLookAngle() : attacker.getBoundingBox().getCenter().subtract(muzzle(owner, owner.getLookAngle()));
        if (attacker != null && attacker.distanceToSqr(owner) < 16.0D) {
            attacker.knockback(0.6D, owner.getX() - attacker.getX(), owner.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }

        owner.swing(InteractionHand.MAIN_HAND, true);
        owner.swing(InteractionHand.OFF_HAND, true);
        shoot(level, owner, aim, (float) (CONFIG.thrustDamage * damageMultiplier(instance)));
        Vec3 dir = aim.lengthSqr() < 1.0E-6D ? owner.getLookAngle() : aim.normalize();
        burst(owner, SlideAndGlideBurstPayload.SLAP_REPEL, owner.getEyePosition().add(0.0D, -0.4D, 0.0D).add(dir.scale(0.7D)), dir);
        playSound(owner, SoundEvents.SHIELD_BLOCK, 1.0F, 1.4F);
        instance.setCoolDown(CONFIG.slapCooldown, SLAP_STYLE);
        instance.addMasteryPoint(owner);
        message(owner, Component.translatable("tracadamia.skill.slide_and_glide.slap_repel").withStyle(ChatFormatting.AQUA));
    }

    private void toggleKnuckle(ManasSkillInstance instance, LivingEntity entity) {
        boolean knuckle = !flag(instance, KNUCKLE_TAG);
        setFlag(instance, KNUCKLE_TAG, knuckle);
        playSound(entity, knuckle ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.6F);
        message(entity, Component.translatable(knuckle ? "tracadamia.skill.slide_and_glide.knuckle_on" : "tracadamia.skill.slide_and_glide.knuckle_off")
                .withStyle(knuckle ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (striking || owner.level().isClientSide || !isKnuckle(instance) || source.getEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        float base = amount.get();
        double multiplier = damageMultiplier(instance);
        float boosted = (float) (base * CONFIG.knuckleDamageMultiplier * multiplier);
        amount.set(boosted);
        STRIKES.add(new Strike(owner, target, instance, (float) (base * CONFIG.knuckleLightShare * multiplier),
                (float) (boosted * CONFIG.knuckleBlastShare), (float) (boosted * CONFIG.knuckleSelfBlastShare)));
        instance.addMasteryPoint(owner);
        return true;
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        if (owner.level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }

        boolean attack = (source.getEntity() != null || source.getDirectEntity() != null) && source.getEntity() != owner;
        if (attack && isSlapping(instance, owner)) {
            repel(instance, owner, source);
            return false;
        }

        if (isKnuckle(instance) && isInSlot(owner, instance) && !TensuraDamageHelper.isSpiritual(source) && owner.getRandom().nextDouble() < CONFIG.reflexesChance) {
            dodge(owner);
            return false;
        }

        if (attack && isOomph(instance) && owner.getRandom().nextDouble() < CONFIG.oomphDodgeChance) {
            dodge(owner);
            return false;
        }

        return true;
    }

    private static void dodge(LivingEntity entity) {
        TensuraParticleHelper.addServerParticlesAroundSelf(entity, ParticleTypes.SWEEP_ATTACK, 1.0D);
        playSound(entity, SoundEvents.PLAYER_ATTACK_WEAK, 1.5F, 1.4F);
    }

    private static void strike(Strike strike) {
        LivingEntity owner = strike.owner();
        LivingEntity target = strike.target();
        if (!target.isAlive() || !(target.level() instanceof ServerLevel level)) {
            return;
        }

        if (strike.light() > 0.0F) {
            target.invulnerableTime = 0;
            target.hurt(lightSource(strike.instance(), owner, KNUCKLE_STYLE), strike.light());
        }

        DamageSource blast = ((Skill) strike.instance().getSkill()).createSource(strike.instance(), owner,
                owner instanceof Player ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, KNUCKLE_STYLE);
        if (target.isAlive() && strike.blast() > 0.0F) {
            target.invulnerableTime = 0;
            target.hurt(blast, strike.blast());
        }

        if (owner.isAlive() && strike.selfBlast() > 0.0F) {
            owner.invulnerableTime = 0;
            owner.hurt(blast, strike.selfBlast());
        }

        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 dir = center.subtract(owner.getEyePosition());
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
        burst(target, SlideAndGlideBurstPayload.KNUCKLE_BLAST, center, dir.lengthSqr() < 1.0E-6D ? owner.getLookAngle() : dir.normalize());
    }

    private static void updateOomph(ManasSkillInstance instance, LivingEntity entity) {
        boolean unlocked = masteryShare(instance) >= CONFIG.oomphUnlockMastery;
        boolean low = unlocked && entity.getHealth() < entity.getMaxHealth() * CONFIG.oomphHealthThreshold;
        if (!low) {
            LOW_HEALTH.remove(entity.getUUID());
            if (isOomph(instance)) {
                setFlag(instance, OOMPH_TAG, false);
                message(entity, Component.translatable("tracadamia.skill.slide_and_glide.oomph_off").withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        int ticks = LOW_HEALTH.merge(entity.getUUID(), 1, Integer::sum);
        if (ticks >= CONFIG.oomphDelayTicks && !isOomph(instance)) {
            setFlag(instance, OOMPH_TAG, true);
            playSound(entity, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
            message(entity, Component.translatable("tracadamia.skill.slide_and_glide.oomph_on").withStyle(ChatFormatting.GOLD));
        }
    }

    private int getFlags(ManasSkillInstance instance, LivingEntity entity) {
        int flags = 0;
        if (isSliding(instance)) {
            flags |= FLAG_SLIDING;
        }
        if (isSlapping(instance, entity)) {
            flags |= FLAG_SLAPPING;
        }
        if (isKnuckle(instance)) {
            flags |= FLAG_KNUCKLE;
        }
        if (isOomph(instance)) {
            flags |= FLAG_OOMPH;
        }
        return flags;
    }

    private static void syncFlags(LivingEntity entity, int flags) {
        Integer sent = SENT_FLAGS.get(entity.getId());
        if (sent == null ? flags == 0 : sent == flags) {
            return;
        }

        if (flags == 0) {
            SENT_FLAGS.remove(entity.getId());
        } else {
            SENT_FLAGS.put(entity.getId(), flags);
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SlideAndGlideStatePayload(entity.getId(), flags));
    }

    private static void burst(Entity around, int kind, Vec3 pos, Vec3 dir) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(around, new SlideAndGlideBurstPayload(kind, pos, dir));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) {
            return;
        }

        Integer flags = SENT_FLAGS.get(event.getTarget().getId());
        if (flags != null) {
            PacketDistributor.sendToPlayer(viewer, new SlideAndGlideStatePayload(event.getTarget().getId(), flags));
        }

        Integer motion = MOTION.get(event.getTarget().getUUID());
        if (motion != null) {
            PacketDistributor.sendToPlayer(viewer, new SlideAndGlideMotionPayload(event.getTarget().getId(), motion));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        SENT_FLAGS.remove(event.getEntity().getId());
        MOTION.remove(event.getEntity().getUUID());
        FORCED_CRAWL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        SENT_FLAGS.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT_FLAGS.remove(event.getEntity().getId());
        LOW_HEALTH.remove(event.getEntity().getUUID());
        MOTION.remove(event.getEntity().getUUID());
        FORCED_CRAWL.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (STRIKES.isEmpty()) {
            return;
        }

        List<Strike> strikes = new ArrayList<>(STRIKES);
        STRIKES.clear();
        striking = true;
        try {
            strikes.forEach(SlideAndGlideQuirk::strike);
        } finally {
            striking = false;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SENT_FLAGS.clear();
        LOW_HEALTH.clear();
        STRIKES.clear();
        MOTION.clear();
        FORCED_CRAWL.clear();
    }

    private static void playSound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        message(entity, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TreeFrogQuirk extends Skill {

    private static final QuirkSkillsConfig.TreeFrog CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).TreeFrog;

    private static final int CLIMB = 0;
    private static final int CAMOUFLAGE = 1;
    private static final int TONGUE_SNATCH = 2;
    private static final int MODES = 3;
    private static final double TONGUE_SPEED = 3.0D;
    private static final double PULL_SPEED = 1.1D;
    private static final int PULL_TICKS = 30;
    private static final int LIFT_TICKS = 10;
    private static final double LIFT_HEIGHT = 4.0D;
    private static final double SLAM_AHEAD = 2.0D;
    private static final String TONGUE_HOLD_TAG = "tongueHold";

    private static final String CLIMB_TAG = "treeFrogClimb";
    private static final String CAMOUFLAGE_TAG = "camouflageUntil";
    private static final ResourceLocation WEBBED_FEET = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tree_frog_webbed_feet");
    private static final ResourceLocation POISON = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tree_frog_poison_boost");

    private static final ResourceLocation POISON_BOOST = ResourceLocation.fromNamespaceAndPath("tensura", "poison_boost");
    private static final ResourceKey<DamageType> TOXIC_HANDS = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "toxic_hands"));

    private record ToxicHit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage) {}

    private static final List<ToxicHit> TOXIC_HITS = new ArrayList<>();

    private static final class Tongue {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final boolean slam;
        private final int reach;
        private Vec3 grabbed = Vec3.ZERO;
        private int age;

        private Tongue(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, boolean slam, int reach) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.slam = slam;
            this.reach = reach;
        }
    }

    private static final List<Tongue> TONGUES = new ArrayList<>();
    private static final List<TonguePayload> CLIENT_TONGUES = new ArrayList<>();
    private static boolean toxicHitting = false;

    public TreeFrogQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CLIMB -> CONFIG.climbAuraCost;
            case CAMOUFLAGE -> CONFIG.camouflageAuraCost;
            case TONGUE_SNATCH -> CONFIG.tongueAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CLIMB -> "tree_frog.climb";
            case CAMOUFLAGE -> "tree_frog.camouflage";
            case TONGUE_SNATCH -> "tree_frog.tongue_snatch";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getTreeFrog(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TREE_FROG.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }


    private static void updateSwimSpeed(ManasSkillInstance instance, LivingEntity entity) {
        Modifiers.set(entity, ManasCoreAttributes.SWIM_SPEED_MULTIPLIER, WEBBED_FEET, instance.getMastery() >= 0.0D ? CONFIG.swimSpeed : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        updateSwimSpeed(instance, entity);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        Modifiers.set(entity, ManasCoreAttributes.SWIM_SPEED_MULTIPLIER, WEBBED_FEET, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        updatePoisonBoost(entity, false);
        TONGUES.removeIf(tongue -> {
            if (tongue.owner == entity) {
                endTongue(tongue);
                return true;
            }
            return false;
        });
        if (isCamouflaged(instance, entity)) {
            entity.removeEffect(getConcealment());
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        updateSwimSpeed(instance, owner);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateSwimSpeed(instance, entity);
        updatePoisonBoost(entity, instance.isToggled());
        if (!instance.isToggled() && !isClimbing(instance)) {
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
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updatePoisonBoost(entity, true);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updatePoisonBoost(entity, false);
    }

    // Poison Boost

    private static void updatePoisonBoost(LivingEntity entity, boolean on) {
        Optional<Holder.Reference<Attribute>> boost = BuiltInRegistries.ATTRIBUTE.getHolder(POISON_BOOST);
        if (boost.isPresent() && !entity.level().isClientSide) {
            AdditiveBoost.apply(entity, boost.get(), POISON, on ? CONFIG.poisonBoost : 0.0D);
        }
    }

    // change damage to poison damage

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if (toxicHitting || event.getAmount() <= 0.0F || !(source.getEntity() instanceof LivingEntity owner) || source.getDirectEntity() != owner
                || owner == target || owner.level().isClientSide || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return;
        }

        ManasSkillInstance instance = getTreeFrog(owner).filter(ManasSkillInstance::isToggled).orElse(null);
        if (instance == null) {
            return;
        }

        float poison = event.getAmount() * (float) Mth.clamp(CONFIG.toxicHandsShare, 0.0D, 1.0D);
        event.setAmount(event.getAmount() - poison);
        TOXIC_HITS.add(new ToxicHit(instance, owner, target, poison));
    }

    private static void toxicHit(ToxicHit hit) {
        LivingEntity target = hit.target();
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || hit.damage() <= 0.0F) {
            return;
        }

        DamageSource source = ((Skill) hit.instance().getSkill()).createSource(hit.instance(), hit.owner(), TOXIC_HANDS, 0);
        toxicHitting = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(source, hit.damage());
            target.invulnerableTime = 20;
        } finally {
            toxicHitting = false;
        }

        level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 6,
                target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.05D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), TensuraSoundEvents.ACID_SIZZLE.get(), SoundSource.PLAYERS, 0.5F, 1.2F);
        if (hit.owner().getRandom().nextInt(4) == 0) {
            hit.instance().addMasteryPoint(hit.owner());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TOXIC_HITS.isEmpty()) {
            List<ToxicHit> hits = List.copyOf(TOXIC_HITS);
            TOXIC_HITS.clear();
            hits.forEach(TreeFrogQuirk::toxicHit);
        }
        if (!TONGUES.isEmpty()) {
            TONGUES.removeIf(TreeFrogQuirk::tickTongue);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TOXIC_HITS.clear();
        TONGUES.clear();
    }

    // Poisons whoever hits the user

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || amount.get() <= 0.0F || !(owner.level() instanceof ServerLevel level)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == owner || source.getDirectEntity() != attacker) {
            return true;
        }

        int amplifier = instance.isMastered(owner) ? CONFIG.toxinAmplifierMastered : CONFIG.toxinAmplifier;
        MobEffectInstance poison = new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FATAL_POISON), CONFIG.toxinSeconds * 20, Math.max(0, amplifier), false, true, true);
        if (TensuraMobEffect.addEffect(attacker, poison, owner, instance.getSkill(), 0)) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5D, attacker.getZ(), 6,
                    attacker.getBbWidth() * 0.4D, attacker.getBbHeight() * 0.3D, attacker.getBbWidth() * 0.4D, 0.05D);
            level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F, 1.2F);
            if (owner.getRandom().nextInt(4) == 0) {
                instance.addMasteryPoint(owner);
            }
        }
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case CLIMB -> toggleClimb(level, instance, entity);
            case CAMOUFLAGE -> camouflage(level, instance, entity);
            case TONGUE_SNATCH -> tongue(level, instance, entity);
        }
    }

    private static void tongue(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (TONGUES.stream().anyMatch(tongue -> tongue.owner == entity)) {
            return;
        }

        boolean slam = entity.isShiftKeyDown();
        double range = instance.isMastered(entity) ? CONFIG.tongueRangeMastered : CONFIG.tongueRange;
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, range, false, true);
        if (target == null || target == entity) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }
        if (slam && !MultiArms.canGrab(entity, target)) {
            fail(entity, Component.translatable(MultiArms.getGrabFail(target)));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, TONGUE_SNATCH)) {
            return;
        }

        int reach = Math.max(2, Mth.ceil(entity.distanceTo(target) / TONGUE_SPEED));
        TONGUES.add(new Tongue(instance, entity, target, slam, reach));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TonguePayload(entity.getId(), target.getId(), reach, reach + (slam ? LIFT_TICKS : PULL_TICKS)));
        instance.setCoolDown(slam ? CONFIG.tongueSlamCooldown : CONFIG.tongueCooldown, TONGUE_SNATCH);
        instance.addMasteryPoint(entity);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FROG_TONGUE, SoundSource.PLAYERS, 1.5F, 0.8F);
    }

    private static Vec3 getMouth(LivingEntity entity) {
        return entity.getEyePosition().add(entity.getViewVector(1.0F).scale(0.3D)).subtract(0.0D, 0.15D * entity.getScale(), 0.0D);
    }

    private static boolean tickTongue(Tongue tongue) {
        LivingEntity owner = tongue.owner;
        LivingEntity target = tongue.target;
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || owner.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != level) {
            endTongue(tongue);
            return true;
        }

        int age = tongue.age++;
        if (age < tongue.reach) {
            return false;
        }

        if (age == tongue.reach) {
            tongue.grabbed = getOffset(owner, target);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.8F);
            level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY(0.5D), target.getZ(), 8, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.05D);
        }

        return tongue.slam ? tickSlam(level, tongue, age - tongue.reach) : tickPull(tongue, age - tongue.reach);
    }

    private static Vec3 getOffset(LivingEntity owner, LivingEntity target) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 relative = target.position().subtract(owner.position());
        return new Vec3(relative.dot(right), relative.y, relative.dot(forward));
    }

    private static boolean tickPull(Tongue tongue, int age) {
        LivingEntity owner = tongue.owner;
        LivingEntity target = tongue.target;
        double stop = (owner.getBbWidth() + target.getBbWidth()) * 0.5D + 1.0D;
        Vec3 offset = owner.position().subtract(target.position());
        double distance = offset.length();
        if (age >= PULL_TICKS || distance <= stop) {
            target.setDeltaMovement(target.getDeltaMovement().scale(0.2D));
            target.hurtMarked = true;
            endTongue(tongue);
            return true;
        }

        Changeable<Vec3> pull = Changeable.of(offset.scale(Math.min(PULL_SPEED, distance - stop) / distance).add(0.0D, 0.08D, 0.0D));
        if (target.getType().is(TensuraEntityTags.NO_FORCED_MOVE) || TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, owner, tongue.instance, pull).isFalse() || pull.get() == null) {
            endTongue(tongue);
            return true;
        }

        target.setDeltaMovement(pull.get());
        target.hurtMarked = true;
        target.resetFallDistance();
        return false;
    }

    private static boolean tickSlam(ServerLevel level, Tongue tongue, int age) {
        LivingEntity owner = tongue.owner;
        LivingEntity target = tongue.target;
        if (age < LIFT_TICKS) {
            float lift = (age + 1.0F) / LIFT_TICKS;
            float eased = lift * lift * (3.0F - 2.0F * lift);
            Vec3 above = new Vec3(0.0D, owner.getBbHeight() + LIFT_HEIGHT * owner.getScale(), owner.getBbWidth() * 0.5D);
            CompoundTag tag = tongue.instance.getOrCreateTag();
            if (!tag.hasUUID(TONGUE_HOLD_TAG)) {
                tag.putUUID(TONGUE_HOLD_TAG, target.getUUID());
                tongue.instance.markDirty();
            }
            MultiArms.holdAt(owner, target, tongue.grabbed.lerp(above, eased), MultiArms.HOLD_BODY, true);
            return false;
        }

        MultiArms.clearHeld(tongue.instance, owner, TONGUE_HOLD_TAG);
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.yBodyRot);
        Vec3 spot = owner.position().add(forward.scale((owner.getBbWidth() + target.getBbWidth()) * 0.5D + SLAM_AHEAD * owner.getScale()));
        BlockHitResult floor = level.clip(new ClipContext(spot.add(0.0D, 1.0D, 0.0D), spot.subtract(0.0D, 8.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 ground = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : spot;
        BlockPos below = BlockPos.containing(ground.x, ground.y - 0.5D, ground.z);
        float hardness = Math.max(0.0F, level.getBlockState(below).getDestroySpeed(level, below));
        boolean mastered = tongue.instance.isMastered(owner);
        float damage = (float) (CONFIG.tongueSlamDamage + hardness * (mastered ? CONFIG.tongueSlamHardnessMultiplierMastered : CONFIG.tongueSlamHardnessMultiplier));

        target.teleportTo(ground.x, ground.y, ground.z);
        target.setDeltaMovement(0.0D, -0.5D, 0.0D);
        target.hurtMarked = true;
        target.invulnerableTime = 0;
        target.hurt(((Skill) tongue.instance.getSkill()).createSource(tongue.instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, TONGUE_SNATCH), damage);
        TensuraMobEffect.addEffect(target, new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FATAL_POISON), CONFIG.tongueSlamPoisonSeconds * 20, 0, false, true, true),
                owner, tongue.instance.getSkill(), TONGUE_SNATCH);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, ground.x, ground.y + 0.05D, ground.z, 0, 0.0D, 1.0D, 0.0D, 1.5D);
        level.sendParticles(ParticleTypes.ITEM_SLIME, ground.x, ground.y + 0.3D, ground.z, 14, 0.6D, 0.2D, 0.6D, 0.1D);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.8F, 0.8F);
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.5F, 0.6F);
        endTongue(tongue);
        return true;
    }

    private static void endTongue(Tongue tongue) {
        MultiArms.clearHeld(tongue.instance, tongue.owner, TONGUE_HOLD_TAG);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(tongue.owner, new TonguePayload(tongue.owner.getId(), -1, 0, 0));
    }

    public static List<TonguePayload> getClientTongues() {
        return CLIENT_TONGUES;
    }

    public record TonguePayload(int userId, int targetId, int reach, int ticks) implements CustomPacketPayload {
        public static final Type<TonguePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tree_frog_tongue"));
        public static final StreamCodec<FriendlyByteBuf, TonguePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TonguePayload::userId,
                ByteBufCodecs.VAR_INT, TonguePayload::targetId,
                ByteBufCodecs.VAR_INT, TonguePayload::reach,
                ByteBufCodecs.VAR_INT, TonguePayload::ticks,
                TonguePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TonguePayload.TYPE, TonguePayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> CLIENT_TONGUES.add(payload)));
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    // Climb

    private static void toggleClimb(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean climbing = !isClimbing(instance);
        if (climbing && EnergyHelper.isOutOfEnergy(entity, instance, CLIMB)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (climbing) {
            tag.putBoolean(CLIMB_TAG, true);
            instance.addMasteryPoint(entity);
        } else {
            tag.remove(CLIMB_TAG);
        }

        instance.markDirty();
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_BLOCK_STEP, SoundSource.PLAYERS, 1.0F, climbing ? 1.2F : 0.8F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(climbing ? "tracadamia.skill.tree_frog.climb_on" : "tracadamia.skill.tree_frog.climb_off"), true);
        }
    }

    public static boolean isClimbing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(CLIMB_TAG);
    }

    // Walls work like ladders while climbing is on
    public static boolean canClimb(LivingEntity entity) {
        return entity instanceof Player && !entity.isSpectator() && getTreeFrog(entity).filter(TreeFrogQuirk::isClimbing).isPresent() && ExtraArmsQuirk.isTouchingWall(entity);
    }

    private static Holder<MobEffect> getConcealment() {
        return TensuraMobEffects.getReference(TensuraMobEffects.PRESENCE_CONCEALMENT);
    }

    public static boolean isCamouflaged(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(CAMOUFLAGE_TAG) > entity.level().getGameTime() && entity.hasEffect(getConcealment());
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == CAMOUFLAGE && isCamouflaged(instance, entity);
    }

    private static void camouflage(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isCamouflaged(instance, entity)) {
            entity.removeEffect(getConcealment());
            instance.getOrCreateTag().remove(CAMOUFLAGE_TAG);
            instance.markDirty();
            instance.setCoolDown(CONFIG.camouflageCooldown, CAMOUFLAGE);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_UNCAST.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, CAMOUFLAGE)) {
            return;
        }

        int ticks = CONFIG.camouflageSeconds * 20;
        MobEffectInstance concealment = new MobEffectInstance(getConcealment(), ticks, Math.max(0, CONFIG.camouflageLevel - 1), false, false, true);
        if (!TensuraMobEffect.addEffect(entity, concealment, entity, instance.getSkill(), CAMOUFLAGE)) {
            return;
        }

        instance.getOrCreateTag().putLong(CAMOUFLAGE_TAG, level.getGameTime() + ticks);
        instance.markDirty();
        instance.setCoolDown(CONFIG.camouflageCooldown, CAMOUFLAGE);
        instance.addMasteryPoint(entity);
        level.sendParticles(ParticleTypes.ITEM_SLIME, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 10,
                entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.05D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.PRESENCE_CONCEALMENT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

}

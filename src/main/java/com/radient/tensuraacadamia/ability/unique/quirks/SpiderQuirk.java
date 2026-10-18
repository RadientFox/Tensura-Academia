package com.radient.tensuraacadamia.ability.unique.quirks;

import com.github.hvnbael.trnightmare.racialprogression.RacialProgressionManager;
import com.github.hvnbael.trnightmare.racialprogression.RacialProgressionService;
import com.github.hvnbael.trnightmare.racialprogression.RacialTree;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TripWebDevice;
import com.radient.tensuraacadamia.entity.WebProjectile;
import com.radient.tensuraacadamia.network.QuirkAnimPayload;
import com.radient.tensuraacadamia.network.SpiderCeilingPayload;
import com.radient.tensuraacadamia.network.SpiderSwingPayload;
import com.radient.tensuraacadamia.network.WaldoStrikePayload;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.SpiderRegistry;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class SpiderQuirk extends Skill {

    private static final QuirkSkillsConfig.Spider CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Spider;

    public static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spidey_smarts");

    public static final int WEB_SWING = 0;
    public static final int WEB_SHOOTERS = 1;
    public static final int IMPACT_WEB = 2;
    public static final int TRIP_WEB = 3;
    public static final int ELECTRIC_WEB = 4;
    public static final int WEB_BLOSSOM = 5;
    private static final int MODES = 6;

    public static final List<String> UPGRADES = List.of("super_strength_1", "super_strength_2", "super_strength_3",
            "web_enhancement_1", "web_enhancement_2", "web_enhancement_3", "impact_web", "trip_web", "electric_web", "web_blossom", "iron_spider");
    private static final int STRENGTH_FIRST = 0;
    private static final int WEB_FIRST = 3;
    private static final int GADGET_FIRST = 6;
    private static final int IRON_SPIDER = 10;

    private static final String UPGRADES_TAG = "upgrades";
    private static final String AMMO_TAG = "ammo";
    private static final String RELOAD_TAG = "reloadAt";
    private static final String ELECTRIC_AMMO_TAG = "electricAmmo";
    private static final String ELECTRIC_RELOAD_TAG = "electricReloadAt";
    private static final String WALDOS_TAG = "waldos";

    private static final ResourceLocation POWER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spider_power");
    private static final ResourceLocation SENSE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spider_sense");
    private static final ResourceLocation WALDO_JUMP = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "spider_waldo_jump");

    private static final int SYNC_INTERVAL = 20;
    private static final int BLOSSOM_EXTRA_WEBS = 12;
    private static final double SHOT_SPEED = 2.2D;
    private static final double[] ANCHOR_PITCHES = {55.0D, 45.0D, 65.0D, 35.0D, 75.0D};
    private static final double[] ANCHOR_YAWS = {0.0D, 15.0D, -15.0D, 30.0D, -30.0D};
    private static final double MIN_ANCHOR_RISE = 2.0D;
    private static final int WALDO_LEGS = 4;

    private record WebHits(int count, long last) {}

    private static final Map<UUID, Vec3> SWINGS = new HashMap<>();
    private static final Map<UUID, Long> RELEASED = new HashMap<>();
    private static final Map<UUID, WebHits> WEB_HITS = new HashMap<>();
    private static final Set<UUID> CRAWLING = new HashSet<>();
    private static final Map<UUID, Integer> WALDO_LEG = new HashMap<>();
    private static final Map<UUID, Integer> CEILING = new HashMap<>();
    private static final Set<UUID> FORCED_POSE = new HashSet<>();
    private static final float HANG_EYE = 0.25F;
    public static boolean clientSwinging;
    public static int clientCeiling;

    public SpiderQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/unique/spider.png");
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
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
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
        return WEB_SWING;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case WEB_SWING -> "spider.web_swing";
            case WEB_SHOOTERS -> "spider.web_shooters";
            case IMPACT_WEB -> "spider.impact_web";
            case TRIP_WEB -> "spider.trip_web";
            case ELECTRIC_WEB -> "spider.electric_web";
            case WEB_BLOSSOM -> "spider.web_blossom";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getSpider(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.SPIDER.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double masteryShare(ManasSkillInstance instance) {
        return Mth.clamp(instance.getMastery() / CONFIG.masteryPoints, 0.0D, 1.0D);
    }

    private static int upgrades(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(UPGRADES_TAG);
    }

    public static boolean hasUpgrade(ManasSkillInstance instance, int index) {
        return (upgrades(instance) & (1 << index)) != 0;
    }

    private static int level(ManasSkillInstance instance, int first) {
        int level = 0;
        for (int i = 0; i < 3; i++) {
            if (hasUpgrade(instance, first + i)) {
                level++;
            }
        }
        return level;
    }

    private static boolean isModeUnlocked(ManasSkillInstance instance, int mode) {
        return mode <= WEB_SHOOTERS || hasUpgrade(instance, GADGET_FIRST + mode - IMPACT_WEB);
    }

    public static boolean isWaldosOut(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(WALDOS_TAG) && hasUpgrade(instance, IRON_SPIDER);
    }

    public static boolean hasIronSpider(LivingEntity entity) {
        return getSpider(entity).filter(instance -> hasUpgrade(instance, IRON_SPIDER)).isPresent();
    }

    private static double power(ManasSkillInstance instance) {
        return switch (level(instance, STRENGTH_FIRST)) {
            case 1 -> CONFIG.powerLevel1;
            case 2 -> CONFIG.powerLevel2;
            case 3 -> CONFIG.powerLevel3;
            default -> CONFIG.power;
        };
    }

    private static int maxAmmo(ManasSkillInstance instance) {
        return CONFIG.shooterAmmo + level(instance, WEB_FIRST) * CONFIG.ammoPerWebLevel;
    }

    private static double shooterMultiplier(ManasSkillInstance instance) {
        return 1.0D + level(instance, WEB_FIRST) * CONFIG.damagePerWebLevel;
    }

    public static boolean canCling(LivingEntity entity) {
        if (!(entity instanceof Player player) || entity.isSpectator() || player.getAbilities().flying || entity.isPassenger() || entity.isInWater()) {
            return false;
        }

        if (entity.level().isClientSide ? clientSwinging : SWINGS.containsKey(entity.getUUID())) {
            return false;
        }

        Optional<ManasSkillInstance> spider = getSpider(entity);
        return spider.isPresent() && (isWaldosOut(spider.get()) || ((SpiderQuirk) spider.get().getSkill()).isInSlot(entity, spider.get()));
    }

    public static boolean canWallCrawl(LivingEntity entity) {
        return ceilingMode(entity) == SpiderCeilingPayload.NONE && canCling(entity) && ExtraArmsQuirk.isTouchingWall(entity);
    }

    public static int ceilingMode(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return entity instanceof Player player && player.isLocalPlayer() ? clientCeiling : SpiderCeilingPayload.NONE;
        }
        return CEILING.getOrDefault(entity.getUUID(), SpiderCeilingPayload.NONE);
    }

    public static void receiveCeiling(ServerPlayer player, int kind) {
        if (kind == SpiderCeilingPayload.DROP) {
            setCeiling(player, SpiderCeilingPayload.NONE, false);
            player.fallDistance = 0.0F;
            PacketDistributor.sendToPlayersTrackingEntity(player, new SpiderCeilingPayload(player.getId(), SpiderCeilingPayload.DROP));
            return;
        }

        if (kind < SpiderCeilingPayload.NONE || kind > SpiderCeilingPayload.HANG) {
            return;
        }
        boolean allowed = kind == SpiderCeilingPayload.NONE || canCling(player);
        setCeiling(player, allowed ? kind : SpiderCeilingPayload.NONE, !allowed);
    }

    private static void setCeiling(LivingEntity entity, int kind, boolean tellSelf) {
        Integer old = kind == SpiderCeilingPayload.NONE ? CEILING.remove(entity.getUUID()) : CEILING.put(entity.getUUID(), kind);
        int previous = old == null ? SpiderCeilingPayload.NONE : old;
        if (entity instanceof Player player) {
            applyCeiling(player, kind);
        }

        SpiderCeilingPayload payload = new SpiderCeilingPayload(entity.getId(), kind);
        if (tellSelf) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        } else if (previous != kind) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        }
    }

    private static void applyCeiling(Player player, int kind) {
        Pose pose = switch (kind) {
            case SpiderCeilingPayload.CRAWL -> Pose.SWIMMING;
            case SpiderCeilingPayload.HANG -> Pose.STANDING;
            default -> null;
        };
        if (pose != null) {
            player.setForcedPose(pose);
            player.setPose(pose);
            FORCED_POSE.add(player.getUUID());
        } else if (FORCED_POSE.remove(player.getUUID())) {
            player.setForcedPose(null);
        }
        player.refreshDimensions();
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (event.getPose() == Pose.STANDING && event.getEntity() instanceof Player player
                && ceilingMode(player) == SpiderCeilingPayload.HANG) {
            event.setNewSize(event.getNewSize().withEyeHeight(HANG_EYE));
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        grantSmarts(entity);
    }

    private static void grantSmarts(LivingEntity entity) {
        if (!entity.level().isClientSide && SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.SPIDEY_SMARTS.get()).isEmpty()) {
            SkillHelper.learnSkill(entity, QuirkSkills.SPIDEY_SMARTS.get());
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        applyPassives(entity, 0.0D, false);
        Modifiers.set(entity, Attributes.JUMP_STRENGTH, WALDO_JUMP, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (entity instanceof ServerPlayer player) {
            release(player, SpiderSwingPayload.DETACH);
        }
        if (CRAWLING.remove(entity.getUUID())) {
            QuirkAnimPayload.state(entity, QuirkAnimPayload.SPIDER_CRAWL, false);
        }
        if (WALDO_LEG.remove(entity.getUUID()) != null) {
            QuirkAnimPayload.state(entity, QuirkAnimPayload.SPIDER_WALDOS, false);
        }
        if (CEILING.containsKey(entity.getUUID())) {
            setCeiling(entity, SpiderCeilingPayload.NONE, true);
        }
        AttributeInstance mastery = entity.getAttribute(SpiderRegistry.SPIDER_MASTERY);
        if (mastery != null) {
            mastery.setBaseValue(0.0D);
        }
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
            getSpider(event.getEntity()).ifPresent(instance -> serverTick(instance, event.getEntity()));
        }
    }

    private static void serverTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        applyPassives(entity, power(instance), instance.isToggled());

        UUID uuid = entity.getUUID();
        boolean crawling = !entity.onGround() && canWallCrawl(entity);
        if (crawling != CRAWLING.contains(uuid)) {
            if (crawling) {
                CRAWLING.add(uuid);
            } else {
                CRAWLING.remove(uuid);
            }
            QuirkAnimPayload.state(entity, QuirkAnimPayload.SPIDER_CRAWL, crawling);
        }
        if (CEILING.containsKey(uuid)) {
            entity.fallDistance = 0.0F;
            if (!canCling(entity)) {
                setCeiling(entity, SpiderCeilingPayload.NONE, true);
            } else if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0 && entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3D) {
                instance.addMasteryPoint(entity);
            }
        }
        Vec3 anchor = SWINGS.get(uuid);
        if (anchor != null) {
            entity.fallDistance = 0.0F;
            if (!entity.isAlive() || entity.position().distanceTo(anchor) > CONFIG.swingRange * 1.5D) {
                if (entity instanceof ServerPlayer player) {
                    release(player, SpiderSwingPayload.DETACH);
                }
            } else if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
                instance.addMasteryPoint(entity);
            }
        } else if (RELEASED.containsKey(uuid)) {
            entity.fallDistance = 0.0F;
            if (entity.onGround() || entity.isInWater()) {
                RELEASED.remove(uuid);
            }
        }

        boolean waldos = isWaldosOut(instance);
        Modifiers.set(entity, Attributes.JUMP_STRENGTH, WALDO_JUMP, waldos ? CONFIG.waldoJumpBonus : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (waldos != WALDO_LEG.containsKey(uuid)) {
            if (waldos) {
                WALDO_LEG.put(uuid, 0);
            } else {
                WALDO_LEG.remove(uuid);
            }
            QuirkAnimPayload.state(entity, QuirkAnimPayload.SPIDER_WALDOS, waldos);
        }
        if (waldos && entity.tickCount % Math.max(1, CONFIG.waldoStrikeTicks) == 0) {
            waldoStrike(level, instance, entity);
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getLong(RELOAD_TAG) > 0L && now >= tag.getLong(RELOAD_TAG)) {
            tag.remove(RELOAD_TAG);
            tag.putInt(AMMO_TAG, maxAmmo(instance));
            instance.markDirty();
            message(entity, Component.translatable("tracadamia.skill.spider.reloaded").withStyle(ChatFormatting.WHITE));
        }
        if (tag.getLong(ELECTRIC_RELOAD_TAG) > 0L && now >= tag.getLong(ELECTRIC_RELOAD_TAG)) {
            tag.remove(ELECTRIC_RELOAD_TAG);
            tag.putInt(ELECTRIC_AMMO_TAG, CONFIG.electricAmmo);
            instance.markDirty();
        }

        if (entity.tickCount % SYNC_INTERVAL == 0) {
            syncUpgrades(instance, entity);
            AttributeInstance mastery = entity.getAttribute(SpiderRegistry.SPIDER_MASTERY);
            if (mastery != null) {
                mastery.setBaseValue(Math.floor(masteryShare(instance) * 100.0D));
            }
            grantSmarts(entity);
        }
    }

    private static void syncUpgrades(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }

        RacialTree tree = RacialProgressionManager.INSTANCE.get(TREE);
        Set<String> owned = tree == null ? Set.of() : RacialProgressionService.owned(player, tree);
        int mask = 0;
        for (int i = 0; i < UPGRADES.size(); i++) {
            if (owned.contains(UPGRADES.get(i))) {
                mask |= 1 << i;
            }
        }

        if (mask != upgrades(instance)) {
            instance.getOrCreateTag().putInt(UPGRADES_TAG, mask);
            instance.markDirty();
        }
    }

    private static void applyPassives(LivingEntity entity, double power, boolean sense) {
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, POWER, power, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.ARMOR, POWER, power, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.KNOCKBACK_RESISTANCE, POWER, power, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, TensuraAttributes.PRESENCE_SENSE, SENSE, sense ? CONFIG.presenceSense : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (source.getEntity() == owner && source.getDirectEntity() == owner && TensuraDamageHelper.isPhysicalAttack(source)) {
            amount.set((float) (amount.get() * (1.0D + power(instance))));
        }
        return true;
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (source.is(DamageTypeTags.IS_FALL) && isWaldosOut(instance)) {
            amount.set((float) (amount.get() * (1.0D - CONFIG.waldoFallAbsorb)));
        }
        return true;
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        if (owner.level().isClientSide || !instance.isToggled() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof LivingEntity attacker) || attacker == owner) {
            return true;
        }

        double mine = TensuraStorages.getExistenceFrom(owner).getEP();
        double theirs = Math.max(1.0D, TensuraStorages.getExistenceFrom(attacker).getEP());
        double chance = Math.max(instance.isMastered(owner) ? CONFIG.masteredDodge : 0.0D, (mine / theirs - 1.0D) * CONFIG.dodgePerEpRatio);
        if (owner.getRandom().nextDouble() < Math.min(CONFIG.maxDodge, chance)) {
            TensuraParticleHelper.addServerParticlesAroundSelf(owner, ParticleTypes.SWEEP_ATTACK, 1.0D);
            playSound(owner, SoundEvents.PLAYER_ATTACK_WEAK, 1.5F, 1.6F);
            if (owner.getRandom().nextBoolean()) {
                instance.addMasteryPoint(owner);
            }
            return false;
        }
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level) || !isModeUnlocked(instance, mode)) {
            return;
        }

        switch (mode) {
            case WEB_SWING -> swing(level, instance, entity);
            case WEB_SHOOTERS -> shoot(level, instance, entity);
            case IMPACT_WEB -> impact(level, instance, entity);
            case TRIP_WEB -> trip(level, instance, entity);
            case ELECTRIC_WEB -> electric(level, instance, entity);
            case WEB_BLOSSOM -> blossom(level, instance, entity);
        }
    }

    public static void toggleWaldos(ManasSkillInstance instance, LivingEntity entity) {
        boolean out = !isWaldosOut(instance);
        instance.getOrCreateTag().putBoolean(WALDOS_TAG, out);
        instance.markDirty();
        playSound(entity, out ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, out ? 0.8F : 1.2F);
        playSound(entity, SoundEvents.PISTON_EXTEND, 0.6F, out ? 1.4F : 0.8F);
        message(entity, Component.translatable(out ? "tracadamia.skill.spider.waldos_out" : "tracadamia.skill.spider.waldos_in").withStyle(ChatFormatting.GOLD));
    }

    private static void waldoStrike(ServerLevel level, ManasSkillInstance instance, LivingEntity owner) {
        LivingEntity lastHurt = owner.getLastHurtByMob();
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(CONFIG.waldoRange),
                        candidate -> candidate != owner && candidate.isAlive() && !candidate.isSpectator() && !isAlly(owner, candidate)
                                && (candidate instanceof net.minecraft.world.entity.monster.Enemy || candidate == lastHurt
                                || candidate instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == owner)
                                && candidate.distanceTo(owner) <= CONFIG.waldoRange)
                .stream().min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(owner))).orElse(null);
        if (target == null) {
            return;
        }

        int leg = WALDO_LEG.merge(owner.getUUID(), 1, Integer::sum) % WALDO_LEGS;
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner,
                owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, WEB_SWING);
        target.invulnerableTime = 0;
        target.hurt(source, (float) CONFIG.waldoDamage);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new WaldoStrikePayload(owner.getId(), leg, target.getId()));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (owner.getRandom().nextBoolean()) {
            instance.addMasteryPoint(owner);
        }
    }

    private static BlockHitResult look(ServerLevel level, LivingEntity entity, double range) {
        Vec3 eye = entity.getEyePosition();
        return level.clip(new ClipContext(eye, eye.add(entity.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    }

    private static void swing(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (SWINGS.containsKey(player.getUUID())) {
            release(player, SpiderSwingPayload.RELEASE);
            return;
        }

        Vec3 anchor = findAnchor(level, player);
        if (anchor == null) {
            fail(player, "tracadamia.skill.spider.no_anchor");
            return;
        }

        SWINGS.put(player.getUUID(), anchor);
        RELEASED.put(player.getUUID(), level.getGameTime());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SpiderSwingPayload(player.getId(), SpiderSwingPayload.ATTACH, anchor));
        QuirkAnimPayload.play(player, QuirkAnimPayload.SPIDER_SHOT);
        playSound(player, SoundEvents.FISHING_BOBBER_THROW, 1.0F, 1.6F);
        instance.addMasteryPoint(player);
    }

    private static @Nullable Vec3 findAnchor(ServerLevel level, LivingEntity entity) {
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getLookAngle();
        Vec3 moving = new Vec3(entity.getDeltaMovement().x, 0.0D, entity.getDeltaMovement().z);
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = moving.lengthSqr() > 1.0E-4D ? moving : new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        if (moving.lengthSqr() > 0.09D) {
            flat = flat.add(moving.normalize().scale(0.5D)).normalize();
        }

        for (double pitch : ANCHOR_PITCHES) {
            double rise = Math.sin(Math.toRadians(pitch));
            double reach = Math.cos(Math.toRadians(pitch));
            for (double yaw : ANCHOR_YAWS) {
                double radians = Math.toRadians(yaw);
                Vec3 heading = new Vec3(flat.x * Math.cos(radians) - flat.z * Math.sin(radians), 0.0D, flat.x * Math.sin(radians) + flat.z * Math.cos(radians));
                Vec3 dir = heading.scale(reach).add(0.0D, rise, 0.0D);
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(CONFIG.swingRange)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y >= eye.y + MIN_ANCHOR_RISE) {
                    return hit.getLocation();
                }
            }
        }

        BlockHitResult aimed = look(level, entity, CONFIG.swingRange);
        return aimed.getType() == HitResult.Type.BLOCK ? aimed.getLocation() : null;
    }

    public static void release(ServerPlayer player, int kind) {
        if (SWINGS.remove(player.getUUID()) == null) {
            return;
        }

        RELEASED.put(player.getUUID(), player.level().getGameTime());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SpiderSwingPayload(player.getId(), kind, Vec3.ZERO));
        if (kind != SpiderSwingPayload.DETACH) {
            QuirkAnimPayload.play(player, QuirkAnimPayload.SPIDER_FLIP);
        }
    }

    public static void zip(ServerPlayer player) {
        if (getSpider(player).isEmpty() || SWINGS.containsKey(player.getUUID())) {
            return;
        }

        RELEASED.put(player.getUUID(), player.level().getGameTime());
        player.fallDistance = 0.0F;
        QuirkAnimPayload.play(player, QuirkAnimPayload.SPIDER_SHOT);
        playSound(player, SoundEvents.FISHING_BOBBER_THROW, 0.8F, 1.9F);
    }

    private static boolean useAmmo(LivingEntity entity, ManasSkillInstance instance, String ammoTag, String reloadTag, int max) {
        CompoundTag tag = instance.getOrCreateTag();
        long now = entity.level().getGameTime();
        if (tag.getLong(reloadTag) > now) {
            fail(entity, Component.translatable("tracadamia.skill.spider.reloading", (tag.getLong(reloadTag) - now + 19) / 20));
            return false;
        }

        int ammo = tag.contains(ammoTag) ? Math.min(tag.getInt(ammoTag), max) : max;
        ammo--;
        if (ammo <= 0) {
            tag.putInt(ammoTag, 0);
            tag.putLong(reloadTag, now + CONFIG.reloadSeconds * 20L);
            message(entity, Component.translatable("tracadamia.skill.spider.empty", CONFIG.reloadSeconds).withStyle(ChatFormatting.GRAY));
        } else {
            tag.putInt(ammoTag, ammo);
            message(entity, Component.translatable("tracadamia.skill.spider.ammo", ammo, max).withStyle(ChatFormatting.WHITE));
        }
        instance.markDirty();
        return true;
    }

    private static void fire(ServerLevel level, LivingEntity owner, int kind, float damage, float extra, @Nullable LivingEntity target, Vec3 direction) {
        Vec3 dir = direction.lengthSqr() < 1.0E-6D ? owner.getLookAngle() : direction.normalize();
        Vec3 from = owner.getEyePosition().add(0.0D, -0.3D, 0.0D).add(dir.scale(0.5D));
        WebProjectile web = new WebProjectile(level, owner, kind, damage, extra, target);
        web.setPos(from.x, from.y, from.z);
        web.setDeltaMovement(dir.scale(SHOT_SPEED));
        level.addFreshEntity(web);
    }

    private static void aimed(ServerLevel level, LivingEntity owner, int kind, float damage, float extra) {
        LivingEntity target = MultiArms.getTarget(owner, CONFIG.shotRange);
        Vec3 dir = target == null ? owner.getLookAngle() : target.getBoundingBox().getCenter().subtract(owner.getEyePosition());
        fire(level, owner, kind, damage, extra, target, dir);
        QuirkAnimPayload.play(owner, QuirkAnimPayload.SPIDER_SHOT);
        playSound(owner, SoundEvents.SNOW_GOLEM_SHOOT, 0.8F, 1.6F);
    }

    private static void shoot(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!useAmmo(entity, instance, AMMO_TAG, RELOAD_TAG, maxAmmo(instance))) {
            return;
        }

        aimed(level, entity, WebProjectile.SHOT, (float) (CONFIG.shooterDamage * shooterMultiplier(instance)), 0.0F);
        instance.addMasteryPoint(entity);
    }

    private static void impact(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        aimed(level, entity, WebProjectile.IMPACT, (float) CONFIG.impactDamage, 0.0F);
        instance.setCoolDown(CONFIG.impactCooldown, IMPACT_WEB);
        instance.addMasteryPoint(entity);
    }

    private static void electric(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!useAmmo(entity, instance, ELECTRIC_AMMO_TAG, ELECTRIC_RELOAD_TAG, CONFIG.electricAmmo)) {
            return;
        }

        aimed(level, entity, WebProjectile.ELECTRIC, (float) CONFIG.electricDamage, (float) CONFIG.electricExtraDamage);
        instance.addMasteryPoint(entity);
    }

    private static void trip(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        BlockHitResult hit = look(level, entity, CONFIG.tripRange);
        if (hit.getType() != HitResult.Type.BLOCK) {
            fail(entity, "tracadamia.skill.spider.no_anchor");
            return;
        }

        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 at = hit.getLocation().add(normal.scale(0.2D));
        BlockHitResult far = level.clip(new ClipContext(at, at.add(normal.scale(CONFIG.tripLength)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Vec3 end = far.getType() == HitResult.Type.BLOCK ? far.getLocation() : at.add(normal.scale(CONFIG.tripLength));

        List<TripWebDevice> mine = new ArrayList<>(level.getEntities(SpiderRegistry.TRIP_DEVICE.get(), device -> entity.getUUID().equals(device.getOwnerUUID())));
        mine.sort(Comparator.comparingInt(device -> -device.tickCount));
        for (int i = 0; i <= mine.size() - Math.max(1, CONFIG.maxTripDevices); i++) {
            mine.get(i).discard();
        }

        level.addFreshEntity(new TripWebDevice(level, entity, at, end, CONFIG.tripLifetime * 20));
        QuirkAnimPayload.play(entity, QuirkAnimPayload.SPIDER_TRIP);
        playSound(entity, SoundEvents.TRIPWIRE_ATTACH, 1.0F, 1.2F);
        instance.addMasteryPoint(entity);
    }

    private static void blossom(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, 1.3D, motion.z);
        entity.hurtMarked = true;
        RELEASED.put(entity.getUUID(), level.getGameTime());
        QuirkAnimPayload.play(entity, QuirkAnimPayload.SPIDER_BLOSSOM);

        float damage = (float) CONFIG.blossomDamage;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(CONFIG.blossomRadius),
                target -> target != entity && target.isAlive() && !target.isSpectator() && !isAlly(entity, target)
                        && target.distanceTo(entity) <= CONFIG.blossomRadius)) {
            fire(level, entity, WebProjectile.BLOSSOM, damage, 0.0F, target, target.getBoundingBox().getCenter().subtract(entity.getEyePosition()));
        }

        for (int i = 0; i < BLOSSOM_EXTRA_WEBS; i++) {
            double angle = level.random.nextDouble() * Mth.TWO_PI;
            Vec3 dir = new Vec3(Math.cos(angle), -0.2D - level.random.nextDouble() * 0.6D, Math.sin(angle));
            fire(level, entity, WebProjectile.BLOSSOM, damage, 0.0F, null, dir);
        }

        playSound(entity, SoundEvents.SNOW_GOLEM_SHOOT, 1.2F, 1.0F);
        instance.setCoolDown(CONFIG.blossomCooldown, WEB_BLOSSOM);
        instance.addMasteryPoint(entity);
    }

    private static boolean isAlly(LivingEntity owner, LivingEntity target) {
        return owner.isAlliedTo(target) || target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID());
    }

    public static void webHit(LivingEntity owner, LivingEntity target, int kind, float damage, float extra) {
        Optional<ManasSkillInstance> spider = getSpider(owner);
        int mode = switch (kind) {
            case WebProjectile.IMPACT -> IMPACT_WEB;
            case WebProjectile.ELECTRIC -> ELECTRIC_WEB;
            case WebProjectile.BLOSSOM -> WEB_BLOSSOM;
            default -> WEB_SHOOTERS;
        };
        DamageSource source = spider.map(instance -> ((Skill) instance.getSkill()).createSource(instance, owner, DamageTypes.MOB_PROJECTILE, mode))
                .orElseGet(() -> owner.damageSources().mobProjectile(owner, owner));
        target.invulnerableTime = 0;
        target.hurt(source, damage);

        if (kind == WebProjectile.ELECTRIC && target.isAlive()) {
            DamageSource shock = spider.map(instance -> ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.LIGHTNING_ELEMENTAL, mode))
                    .orElseGet(() -> owner.damageSources().source(TensuraDamageTypes.LIGHTNING_ELEMENTAL, owner));
            target.invulnerableTime = 0;
            target.hurt(shock, extra);
            target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), CONFIG.electricParalysisTicks, 0), owner);
        }

        if (kind == WebProjectile.IMPACT || kind == WebProjectile.BLOSSOM) {
            webTarget(target, owner);
            return;
        }

        long now = target.level().getGameTime();
        WebHits hits = WEB_HITS.get(target.getUUID());
        int count = hits == null || now - hits.last() > CONFIG.webMemorySeconds * 20L ? 1 : hits.count() + 1;
        if (count >= CONFIG.hitsToWeb) {
            WEB_HITS.remove(target.getUUID());
            webTarget(target, owner);
        } else {
            WEB_HITS.put(target.getUUID(), new WebHits(count, now));
        }
    }

    public static void webTarget(LivingEntity target, @Nullable LivingEntity owner) {
        target.addEffect(new MobEffectInstance(MHAEffects.WEBBED, CONFIG.webbedTicks, 0), owner);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) {
            return;
        }

        Vec3 anchor = SWINGS.get(event.getTarget().getUUID());
        if (anchor != null) {
            PacketDistributor.sendToPlayer(viewer, new SpiderSwingPayload(event.getTarget().getId(), SpiderSwingPayload.ATTACH, anchor));
        }
        if (CRAWLING.contains(event.getTarget().getUUID())) {
            QuirkAnimPayload.stateTo(viewer, event.getTarget(), QuirkAnimPayload.SPIDER_CRAWL);
        }
        if (WALDO_LEG.containsKey(event.getTarget().getUUID())) {
            QuirkAnimPayload.stateTo(viewer, event.getTarget(), QuirkAnimPayload.SPIDER_WALDOS);
        }
        Integer ceiling = CEILING.get(event.getTarget().getUUID());
        if (ceiling != null) {
            PacketDistributor.sendToPlayer(viewer, new SpiderCeilingPayload(event.getTarget().getId(), ceiling));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SWINGS.remove(event.getEntity().getUUID());
        RELEASED.remove(event.getEntity().getUUID());
        CRAWLING.remove(event.getEntity().getUUID());
        WALDO_LEG.remove(event.getEntity().getUUID());
        CEILING.remove(event.getEntity().getUUID());
        FORCED_POSE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        CRAWLING.remove(event.getEntity().getUUID());
        WALDO_LEG.remove(event.getEntity().getUUID());
        CEILING.remove(event.getEntity().getUUID());
        FORCED_POSE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player, SpiderSwingPayload.DETACH);
            CRAWLING.remove(player.getUUID());
            WALDO_LEG.remove(player.getUUID());
            if (CEILING.containsKey(player.getUUID())) {
                setCeiling(player, SpiderCeilingPayload.NONE, true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SWINGS.clear();
        RELEASED.clear();
        WEB_HITS.clear();
        CRAWLING.clear();
        WALDO_LEG.clear();
        CEILING.clear();
        FORCED_POSE.clear();
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
        fail(entity, Component.translatable(key));
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        message(entity, message.copy().withStyle(ChatFormatting.RED));
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ViralCosmosQuirk extends Skill {

    private static final QuirkSkillsConfig.ViralCosmos CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ViralCosmos;

    private static final int FLOWER_TYPE = 0;
    private static final int PETAL_RELEASE = 1;
    private static final int PETAL_SWARM = 2;
    private static final int FLOWER_GROWTH = 3;
    private static final int MODES = 4;
    private static final int LEND_SECONDS = 10;

    public static final String HEALING_TAG = "healing";
    public static final int NO_FLOWER = 0;
    public static final int POISON_FLOWER = 1;
    public static final int HEALING_FLOWER = 2;
    public static final int SMALL_FLOWER = 3;

    private static final DustParticleOptions POISON_PETAL = new DustParticleOptions(new Vector3f(0.62F, 0.22F, 0.82F), 1.0F);
    private static final DustParticleOptions HEALING_PETAL = new DustParticleOptions(new Vector3f(1.0F, 0.58F, 0.78F), 1.0F);
    private static final DustParticleOptions POISON_GAS = new DustParticleOptions(new Vector3f(0.45F, 0.75F, 0.25F), 1.6F);
    private static final DustParticleOptions HEALING_GAS = new DustParticleOptions(new Vector3f(0.95F, 0.75F, 0.85F), 1.6F);
    private static final double BOLT_SPEED = 1.5D;
    private static final int BOLT_TICKS = 60;
    private static final int POISON_TICKS = 40;

    private static final class Flower {
        private final LivingEntity host;
        private final LivingEntity owner;
        private final ManasSkillInstance instance;
        private final boolean healing;
        private final @Nullable Flower parent;
        private final long start;
        private final long until;
        private double health;

        private Flower(LivingEntity host, LivingEntity owner, ManasSkillInstance instance, boolean healing, @Nullable Flower parent, long start, long until) {
            this.host = host;
            this.owner = owner;
            this.instance = instance;
            this.healing = healing;
            this.parent = parent;
            this.start = start;
            this.until = until;
            this.health = CONFIG.flowerHealth;
        }

        private int kind() {
            return parent != null ? SMALL_FLOWER : healing ? HEALING_FLOWER : POISON_FLOWER;
        }
    }

    private record Swarm(ManasSkillInstance instance, LivingEntity owner, long start, long until, boolean healing, boolean mastered) {}

    private static final class Bolt {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final boolean healing;
        private final float amount;
        private final long start;
        private Vec3 pos;

        private Bolt(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, boolean healing, float amount, long start) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.healing = healing;
            this.amount = amount;
            this.start = start;
            this.pos = owner.getEyePosition().add(owner.getLookAngle().scale(0.5D));
        }
    }

    private static final Map<LivingEntity, Flower> FLOWERS = new HashMap<>();
    private static final List<Swarm> SWARMS = new ArrayList<>();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final Map<Integer, Integer> CLIENT_FLOWERS = new HashMap<>();

    public ViralCosmosQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (mode == PETAL_SWARM && entity.isShiftKeyDown()) {
            return 0.0D;
        }

        return switch (mode) {
            case PETAL_RELEASE -> CONFIG.releaseAuraCost;
            case PETAL_SWARM -> CONFIG.swarmAuraCost;
            case FLOWER_GROWTH -> CONFIG.growthAuraCost;
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
            case FLOWER_TYPE -> "viral_cosmos.flower_type";
            case PETAL_RELEASE -> "viral_cosmos.petal_release";
            case PETAL_SWARM -> "viral_cosmos.petal_swarm";
            case FLOWER_GROWTH -> "viral_cosmos.flower_growth";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == FLOWER_TYPE || mode == PETAL_SWARM && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getViralCosmos(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.VIRAL_COSMOS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static boolean isHealing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(HEALING_TAG);
    }

    // skill lending

    private static void lendSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        ManasSkillInstance owned = skills.getSkill(skill).orElse(null);
        if (owned == null) {
            TensuraSkillInstance lent = skill.createLearningInstance(entity);
            lent.setMastery(0.0D);
            lent.setRemoveTime(LEND_SECONDS);
            if (SkillHelper.learnSkill(entity, lent, LEND_SECONDS)) {
                skills.getSkill(skill).filter(learned -> !learned.isToggled()).ifPresent(learned -> {
                    learned.setToggled(true);
                    learned.onToggleOn(entity);
                    skills.markDirty();
                });
            }
        } else if (owned.isTemporarySkill() && owned.getRemoveTime() < LEND_SECONDS) {
            owned.setRemoveTime(LEND_SECONDS);
            skills.markDirty();
        }
    }

    private static void takeSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        if (skills.getSkill(skill).filter(ManasSkillInstance::isTemporarySkill).isPresent()) {
            skills.forgetSkill(skill);
        }
    }

    private static void lendResistances(LivingEntity entity) {
        lendSkill(entity, ResistanceSkills.POISON_RESISTANCE.get());
        lendSkill(entity, ResistanceSkills.ABNORMAL_CONDITION_RESISTANCE.get());
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            lendResistances(entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, ResistanceSkills.POISON_RESISTANCE.get());
        takeSkill(entity, ResistanceSkills.ABNORMAL_CONDITION_RESISTANCE.get());
        SWARMS.removeIf(swarm -> swarm.owner() == entity);
    }

    // Enhanced crop Growth

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
        if (!instance.isTemporarySkill()) {
            lendResistances(entity);
        }

        if (!instance.isToggled()) {
            return;
        }

        instance.addMasteryPoint(entity);
        if (!(entity instanceof Player) && entity.level() instanceof ServerLevel level) {
            growCrops(level, instance, entity, 100);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            getViralCosmos(player).filter(ManasSkillInstance::isToggled).ifPresent(instance -> growCrops(player.serverLevel(), instance, player, 20));
        }
    }

    // Extra random ticks on top of the world's own
    public static void growCrops(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int ticks) {
        int half = (instance.isMastered(entity) ? CONFIG.cropRangeMastered : CONFIG.cropRange) / 2;
        double extra = (CONFIG.cropGrowth - 1.0D) * ticks * level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) / 4096.0D;
        if (extra <= 0.0D) {
            return;
        }

        RandomSource random = level.random;
        BlockPos center = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-half, -2, -half), center.offset(half, 2, half))) {
            BlockState state = level.getBlockState(pos);
            if (!isCrop(state) || !state.isRandomlyTicking()) {
                continue;
            }

            int growths = (int) extra + (random.nextDouble() < extra - (int) extra ? 1 : 0);
            BlockPos crop = pos.immutable();
            for (int i = 0; i < growths && level.getBlockState(crop).isRandomlyTicking(); i++) {
                level.getBlockState(crop).randomTick(level, crop, random);
            }
        }
    }

    private static boolean isCrop(BlockState state) {
        return state.is(BlockTags.CROPS) || state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case FLOWER_TYPE -> flowerType(level, instance, entity);
            case PETAL_RELEASE -> petalRelease(level, instance, entity);
            case PETAL_SWARM -> {
                if (entity.isShiftKeyDown()) {
                    endSwarm(level, entity);
                } else {
                    petalSwarm(level, instance, entity);
                }
            }
            case FLOWER_GROWTH -> flowerGrowth(level, instance, entity);
        }
    }

    // Flower Type

    private static void flowerType(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean healing = !isHealing(instance);
        instance.getOrCreateTag().putBoolean(HEALING_TAG, healing);
        instance.markDirty();
        playSound(level, entity.position(), SoundEvents.FLOWERING_AZALEA_PLACE, 1.0F, healing ? 1.4F : 0.8F);
        if (entity instanceof Player player) {
            Component type = Component.translatable(healing ? "tracadamia.skill.viral_cosmos.healing" : "tracadamia.skill.viral_cosmos.poison");
            player.displayClientMessage(Component.translatable("tracadamia.skill.viral_cosmos.type", type).withStyle(healing ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GREEN), true);
        }
    }

    private static DamageSource poisonSource(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.FATAL_POISON, mode);
    }

    // Poison hurts
    // Healing heals
    private static void bloom(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float amount, boolean healing, int mode) {
        Vec3 point = target.getBoundingBox().getCenter();
        if (healing) {
            target.heal(amount);
            level.sendParticles(ParticleTypes.HEART, point.x, point.y + target.getBbHeight() * 0.4D, point.z, 3, target.getBbWidth() * 0.3D, 0.2D, target.getBbWidth() * 0.3D, 0.0D);
        } else {
            target.invulnerableTime = 0;
            target.hurt(poisonSource(instance, owner, mode), amount);
        }

        level.sendParticles(healing ? HEALING_PETAL : POISON_PETAL, point.x, point.y, point.z, 10, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.0D);
    }

    public static boolean isAlly(LivingEntity owner, LivingEntity target) {
        return owner == target || owner.isAlliedTo(target) || target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()) || SubordinateHelper.isAlly(owner, target);
    }

    // Petal Release

    private static void petalRelease(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.releaseRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, PETAL_RELEASE)) {
            return;
        }

        float amount = (float) (instance.isMastered(entity) ? CONFIG.releaseDamageMastered : CONFIG.releaseDamage);
        BOLTS.add(new Bolt(instance, entity, target, isHealing(instance), amount, level.getGameTime()));
        playSound(level, entity.position(), SoundEvents.CHERRY_LEAVES_BREAK, 1.5F, 0.8F);
        instance.setCoolDown(CONFIG.releaseCooldown, PETAL_RELEASE);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickBolt(Bolt bolt) {
        LivingEntity target = bolt.target;
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || level != bolt.owner.level() || level.getGameTime() - bolt.start > BOLT_TICKS) {
            return false;
        }

        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 toward = aim.subtract(bolt.pos);
        double reach = target.getBbWidth() * 0.5D + 0.5D;
        if (toward.length() <= reach + BOLT_SPEED) {
            bloom(level, bolt.instance, bolt.owner, target, bolt.amount, bolt.healing, PETAL_RELEASE);
            playSound(level, aim, SoundEvents.CHERRY_LEAVES_HIT, 1.2F, 1.0F);
            return false;
        }

        Vec3 next = bolt.pos.add(toward.normalize().scale(BOLT_SPEED));
        for (double along = 0.0D; along < BOLT_SPEED; along += 0.3D) {
            Vec3 spot = bolt.pos.add(toward.normalize().scale(along));
            level.sendParticles(bolt.healing ? HEALING_PETAL : POISON_PETAL, spot.x, spot.y, spot.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, next.x, next.y, next.z, 3, 0.3D, 0.3D, 0.3D, 0.0D);
        bolt.pos = next;
        return true;
    }

    // Petal Swarm

    private static void petalSwarm(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, PETAL_SWARM)) {
            return;
        }

        SWARMS.removeIf(swarm -> swarm.owner() == entity);
        long time = level.getGameTime();
        SWARMS.add(new Swarm(instance, entity, time, time + CONFIG.swarmSeconds * 20L, isHealing(instance), instance.isMastered(entity)));
        playSound(level, entity.position(), SoundEvents.CHERRY_LEAVES_PLACE, 2.0F, 0.6F);
        instance.setCoolDown(CONFIG.swarmCooldown, PETAL_SWARM);
        instance.addMasteryPoint(entity);
    }

    private static void endSwarm(ServerLevel level, LivingEntity entity) {
        if (!SWARMS.removeIf(swarm -> swarm.owner() == entity)) {
            fail(entity, Component.translatable("tracadamia.skill.viral_cosmos.no_swarm"));
            return;
        }

        playSound(level, entity.position(), SoundEvents.CHERRY_LEAVES_BREAK, 1.5F, 1.2F);
    }

    public static boolean isSwarming(LivingEntity entity) {
        return SWARMS.stream().anyMatch(swarm -> swarm.owner() == entity);
    }

    private static boolean tickSwarm(Swarm swarm) {
        LivingEntity owner = swarm.owner();
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level) || level.getGameTime() > swarm.until()) {
            return false;
        }

        double half = (swarm.mastered() ? CONFIG.swarmSizeMastered : CONFIG.swarmSize) * 0.5D;
        AABB area = new AABB(owner.position(), owner.position()).inflate(half, 3.0D, half).expandTowards(0.0D, owner.getBbHeight(), 0.0D);
        RandomSource random = level.random;
        for (int i = 0; i < 8; i++) {
            double x = area.minX + random.nextDouble() * area.getXsize();
            double y = owner.getY() + random.nextDouble() * 3.0D;
            double z = area.minZ + random.nextDouble() * area.getZsize();
            level.sendParticles(swarm.healing() ? HEALING_PETAL : POISON_PETAL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (i % 4 == 0) {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        if ((level.getGameTime() - swarm.start()) % 20L != 0L) {
            return true;
        }

        float amount = (float) (swarm.healing() ? swarm.mastered() ? CONFIG.swarmHealMastered : CONFIG.swarmHeal : swarm.mastered() ? CONFIG.swarmDamageMastered : CONFIG.swarmDamage);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target.isAlive() && !target.isSpectator() && (swarm.healing() ? isAlly(owner, target) : HitRules.canHit(owner, target)))) {
            bloom(level, swarm.instance(), owner, target, amount, swarm.healing(), PETAL_SWARM);
        }
        return true;
    }

    // Flower Growth

    private static void flowerGrowth(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        boolean healing = isHealing(instance);
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.growthRange);
        if (target == null && healing) {
            target = entity;
        }

        if (target == null || target == entity && !healing) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, FLOWER_GROWTH)) {
            return;
        }

        long time = level.getGameTime();
        plant(level, new Flower(target, entity, instance, healing, null, time, time + CONFIG.growthSeconds * 20L));
        instance.setCoolDown(CONFIG.growthCooldown, FLOWER_GROWTH);
        instance.addMasteryPoint(entity);
    }

    private static void plant(ServerLevel level, Flower flower) {
        Flower old = FLOWERS.put(flower.host, flower);
        if (old != null) {
            wilt(old, false);
        }

        syncFlower(flower.host, flower.kind());
        Vec3 back = getBack(flower.host);
        level.sendParticles(flower.healing ? HEALING_PETAL : POISON_PETAL, back.x, back.y, back.z, 12, 0.2D, 0.2D, 0.2D, 0.0D);
        playSound(level, back, SoundEvents.BIG_DRIPLEAF_PLACE, 1.0F, flower.parent != null ? 1.6F : 1.0F);
    }

    // Takes the main flower away
    // Takes away any of the other flowers spread
    private static void wilt(Flower flower, boolean remove) {
        if (remove && FLOWERS.get(flower.host) == flower) {
            FLOWERS.remove(flower.host);
        }

        if (FLOWERS.get(flower.host) != flower) {
            syncFlower(flower.host, FLOWERS.containsKey(flower.host) ? FLOWERS.get(flower.host).kind() : NO_FLOWER);
        }

        if (flower.parent == null) {
            List.copyOf(FLOWERS.values()).stream().filter(child -> child.parent == flower).forEach(child -> wilt(child, true));
        }
    }

    public static int getFlower(LivingEntity entity) {
        Flower flower = FLOWERS.get(entity);
        return flower == null ? NO_FLOWER : flower.kind();
    }

    private static Vec3 getBack(LivingEntity host) {
        Vec3 behind = Vec3.directionFromRotation(0.0F, host.yBodyRot).scale(-host.getBbWidth() * 0.5D);
        return host.position().add(behind).add(0.0D, host.getBbHeight() * 0.7D, 0.0D);
    }

    public static double getCostMultiplier(LivingEntity entity) {
        Flower flower = FLOWERS.get(entity);
        return flower != null && !flower.healing && flower.parent == null ? CONFIG.poisonCostMultiplier : 1.0D;
    }

    private static boolean tickFlower(Flower flower) {
        LivingEntity host = flower.host;
        if (!host.isAlive() || host.isRemoved() || !(host.level() instanceof ServerLevel level) || level.getGameTime() > flower.until
                || flower.parent != null && FLOWERS.get(flower.parent.host) != flower.parent) {
            return false;
        }

        long age = level.getGameTime() - flower.start;
        if (flower.parent != null) {
            if (age > 0L && age % 20L == 0L) {
                lethalPoison(host, flower);
            }
            return true;
        }

        if (!flower.healing) {
            if (age % 20L == 0L) {
                TensuraMobEffect.addEffect(host, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, POISON_TICKS, Math.max(0, CONFIG.poisonSlowness), false, true, true), flower.owner, flower.instance.getSkill(), FLOWER_GROWTH);
                poisonGas(level, flower);
            }
            return true;
        }

        if (age > 0L && age % Math.max(1, CONFIG.healingGasSeconds * 20L) == 0L) {
            healingGas(level, flower);
        }
        return true;
    }

    private static void poisonGas(ServerLevel level, Flower flower) {
        LivingEntity host = flower.host;
        double half = CONFIG.poisonGasSize * 0.5D;
        AABB area = new AABB(host.position(), host.position()).inflate(half).expandTowards(0.0D, host.getBbHeight(), 0.0D);
        gasParticles(level, area, POISON_GAS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != host && target.isAlive() && !target.isSpectator()
                && HitRules.canHit(flower.owner, target) && !FLOWERS.containsKey(target))) {
            plant(level, new Flower(target, flower.owner, flower.instance, false, flower, level.getGameTime(), flower.until));
        }
    }

    private static void lethalPoison(LivingEntity target, Flower flower) {
        target.invulnerableTime = 0;
        target.hurt(poisonSource(flower.instance, flower.owner, FLOWER_GROWTH), (float) (CONFIG.lethalPoisonDamage + target.getMaxHealth() * CONFIG.lethalPoisonPercent));
    }

    private static void healingGas(ServerLevel level, Flower flower) {
        LivingEntity host = flower.host;
        double half = (flower.instance.isMastered(flower.owner) ? CONFIG.healingGasSizeMastered : CONFIG.healingGasSize) * 0.5D;
        AABB area = new AABB(host.position(), host.position()).inflate(half).expandTowards(0.0D, host.getBbHeight(), 0.0D);
        gasParticles(level, area, HEALING_GAS);
        double taken = 0.0D;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != host && target.isAlive() && !target.isSpectator()
                && HitRules.canHit(flower.owner, target) && !isAlly(host, target))) {
            if (SkillUtils.hasSkill(target, ResistanceSkills.ABNORMAL_CONDITION_NULLIFICATION.get())) {
                continue;
            }

            double share = SkillUtils.hasSkill(target, ResistanceSkills.ABNORMAL_CONDITION_RESISTANCE.get()) ? CONFIG.healingDrainResisted : CONFIG.healingDrain;
            float before = target.getHealth();
            target.invulnerableTime = 0;
            target.hurt(((Skill) flower.instance.getSkill()).createSource(flower.instance, flower.owner, DamageTypes.MAGIC, FLOWER_GROWTH), (float) (target.getMaxHealth() * share));
            taken += Math.max(0.0F, before - target.getHealth());
        }

        double mendLimit = CONFIG.flowerHealth * 0.5D;
        if (flower.health < mendLimit) {
            double mend = Math.min(taken, mendLimit - flower.health);
            flower.health += mend;
            taken -= mend;
        }

        if (taken > 0.0D) {
            host.heal((float) taken);
            Vec3 point = host.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.HEART, point.x, point.y + host.getBbHeight() * 0.4D, point.z, 4, 0.3D, 0.2D, 0.3D, 0.0D);
        }
    }

    private static void gasParticles(ServerLevel level, AABB area, DustParticleOptions gas) {
        RandomSource random = level.random;
        for (int i = 0; i < 20; i++) {
            level.sendParticles(gas, area.minX + random.nextDouble() * area.getXsize(), area.minY + random.nextDouble() * Math.min(3.0D, area.getYsize()), area.minZ + random.nextDouble() * area.getZsize(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onHostHurt(LivingDamageEvent.Post event) {
        LivingEntity host = event.getEntity();
        Flower flower = host.level().isClientSide ? null : FLOWERS.get(host);
        if (flower == null || !flower.healing || flower.parent != null) {
            return;
        }

        flower.health -= event.getNewDamage();
        if (flower.health <= 0.0D && host.getHealth() > 0.0F) {
            breakFlower(flower);
        }
    }

    //Destroys itself to keep the host alive
    //Acts as a totem of undying
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHostDeath(LivingDeathEvent event) {
        LivingEntity host = event.getEntity();
        Flower flower = host.level().isClientSide ? null : FLOWERS.get(host);
        if (flower == null || !flower.healing || flower.parent != null) {
            return;
        }

        event.setCanceled(true);
        host.setHealth(1.0F);
        breakFlower(flower);
    }

    private static void breakFlower(Flower flower) {
        wilt(flower, true);
        if (flower.host.level() instanceof ServerLevel level) {
            Vec3 back = getBack(flower.host);
            level.sendParticles(HEALING_PETAL, back.x, back.y, back.z, 30, 0.4D, 0.4D, 0.4D, 0.05D);
            playSound(level, back, SoundEvents.BIG_DRIPLEAF_BREAK, 1.2F, 0.8F);
        }
    }

    private static void syncFlower(LivingEntity host, int kind) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(host, new FlowerPayload(host.getId(), kind));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity host && FLOWERS.containsKey(host) && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new FlowerPayload(host.getId(), FLOWERS.get(host).kind()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!BOLTS.isEmpty()) {
            BOLTS.removeIf(bolt -> !tickBolt(bolt));
        }

        if (!SWARMS.isEmpty()) {
            SWARMS.removeIf(swarm -> !tickSwarm(swarm));
        }

        if (!FLOWERS.isEmpty()) {
            for (Flower flower : List.copyOf(FLOWERS.values())) {
                if (FLOWERS.get(flower.host) == flower && !tickFlower(flower)) {
                    wilt(flower, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FLOWERS.clear();
        SWARMS.clear();
        BOLTS.clear();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    public static int getClientFlower(int entityId) {
        return CLIENT_FLOWERS.getOrDefault(entityId, NO_FLOWER);
    }

    public static void clearClient() {
        CLIENT_FLOWERS.clear();
    }

    public record FlowerPayload(int host, int kind) implements CustomPacketPayload {
        public static final Type<FlowerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "viral_cosmos_flower"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FlowerPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FlowerPayload::host,
                ByteBufCodecs.VAR_INT, FlowerPayload::kind,
                FlowerPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(FlowerPayload.TYPE, FlowerPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.kind() == NO_FLOWER) {
                CLIENT_FLOWERS.remove(payload.host());
            } else {
                CLIENT_FLOWERS.put(payload.host(), payload.kind());
            }
        }));
    }

}

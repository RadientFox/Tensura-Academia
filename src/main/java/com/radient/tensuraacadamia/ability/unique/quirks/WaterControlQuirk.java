package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WaterControlQuirk extends Skill {

    private static final QuirkSkillsConfig.WaterControl CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WaterControl;

    private static final int WATER_BALL = 0;
    private static final int SUFFOCATION = 1;
    private static final int TIDAL_WAVE = 2;
    private static final int MODES = 3;
    private static final int LEND_SECONDS = 10;

    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "water_control_water_boost");
    private static final ResourceLocation DEGRADATION = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "water_control_degradation");

    private static final DustParticleOptions WATER_DUST = new DustParticleOptions(new Vector3f(0.2F, 0.5F, 1.0F), 1.3F);
    private static final int AIR_DRAIN = 15;
    private static final double WAVE_SPEED = 0.8D;
    private static final double WAVE_HEIGHT = 3.0D;

    private record Trap(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, long start, long until, Vec3 pos) {}

    private static final class Wave {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final Vec3 origin;
        private final Vec3 direction;
        private final float damage;
        private final Set<LivingEntity> hit = new HashSet<>();
        private double travelled;

        private Wave(ManasSkillInstance instance, LivingEntity owner, Vec3 origin, Vec3 direction, float damage) {
            this.instance = instance;
            this.owner = owner;
            this.origin = origin;
            this.direction = direction;
            this.damage = damage;
        }
    }

    private static final List<Trap> TRAPS = new ArrayList<>();
    private static final List<Wave> WAVES = new ArrayList<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/watercontrol.png");
    }

    public WaterControlQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case WATER_BALL -> CONFIG.ballAuraCost;
            case SUFFOCATION -> CONFIG.suffocationAuraCost;
            case TIDAL_WAVE -> CONFIG.waveAuraCost;
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
            case WATER_BALL -> "water_control.water_ball";
            case SUFFOCATION -> "water_control.suffocation";
            case TIDAL_WAVE -> "water_control.tidal_wave";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getWaterControl(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WATER_CONTROL.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && isTrapped(entity)) {
            event.setCanBreathe(false);
        }
    }

    // temp skill lending...
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

    private static void lendSkills(LivingEntity entity) {
        lendSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
        lendSkill(entity, ResistanceSkills.WATER_ATTACK_RESISTANCE.get());
    }

    // Water Affinity and Water Degradation

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
        if (!instance.isTemporarySkill()) {
            lendSkills(entity);
        }

        if (instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            lendSkills(entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, 0.0D);
        Modifiers.set(entity, TensuraAttributes.WATER_RESIST_DEGRADATION, DEGRADATION, 0.0D, AttributeModifier.Operation.ADD_VALUE);
        takeSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
        takeSkill(entity, ResistanceSkills.WATER_ATTACK_RESISTANCE.get());
        TRAPS.removeIf(trap -> trap.owner() == entity);
        WAVES.removeIf(wave -> wave.owner == entity);
    }

    private static void updateWater(ManasSkillInstance instance, LivingEntity entity) {
        boolean on = instance.isToggled();
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, on ? CONFIG.waterBoost : 0.0D);
        Modifiers.set(entity, TensuraAttributes.WATER_RESIST_DEGRADATION, DEGRADATION, on ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    // Needs water sources to cast moves
    private static boolean bringsWater(LivingEntity entity) {
        return WaterGunQuirk.getWaterGun(entity).isPresent() || WaterPumpQuirk.getWaterPump(entity).isPresent() || isRaining(entity);
    }
    // if its raining then you dont need the water sources since the sky is the source
    private static boolean isRaining(LivingEntity entity) {
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        return level.isRaining() && level.getBiome(pos).value().getPrecipitationAt(pos) == Biome.Precipitation.RAIN;
    }

    // Attempts to find the closest water source
    // This is water bending
    private static List<BlockPos> findSources(LivingEntity entity) {
        double range = CONFIG.waterRange;
        BlockPos center = entity.blockPosition();
        int reach = (int) Math.ceil(range);
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -reach, -reach), center.offset(reach, reach, reach))) {
            if (pos.distSqr(center) <= range * range && entity.level().getFluidState(pos).is(FluidTags.WATER) && entity.level().getFluidState(pos).isSource()) {
                sources.add(pos.immutable());
            }
        }

        sources.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return sources;
    }

    public static boolean hasWater(LivingEntity entity) {
        return bringsWater(entity) || !findSources(entity).isEmpty();
    }

    // Removes the water used
    private static void useWater(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int sources) {
        if (bringsWater(entity)) {
            return;
        }

        int used = 0;
        Vec3 to = entity.getBoundingBox().getCenter();
        for (BlockPos pos : findSources(entity)) {
            if (used >= sources) {
                break;
            }

            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BucketPickup pickup) || !GroundBlocks.canBreak(level, instance, entity, pos) || pickup.pickupBlock(null, level, pos, state).isEmpty()) {
                continue;
            }

            GroundBlocks.griefed(level, instance, entity, pos);
            used++;
            Vec3 from = Vec3.atCenterOf(pos);
            Vec3 path = to.subtract(from);
            for (double along = 0.0D; along < 1.0D; along += 0.5D / Math.max(1.0D, path.length())) {
                Vec3 spot = from.add(path.scale(along));
                level.sendParticles(WATER_DUST, spot.x, spot.y, spot.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
            level.sendParticles(ParticleTypes.SPLASH, from.x, from.y + 0.4D, from.z, 8, 0.3D, 0.1D, 0.3D, 0.0D);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 0.9F);
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (!hasWater(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.water_control.no_water"));
            return;
        }

        switch (mode) {
            case WATER_BALL -> waterBall(level, instance, entity);
            case SUFFOCATION -> suffocation(level, instance, entity);
            case TIDAL_WAVE -> tidalWave(level, instance, entity);
        }
    }

    // Water Ball

    private static void waterBall(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, WATER_BALL)) {
            return;
        }

        useWater(level, instance, entity, CONFIG.ballSourcesUsed);

        WaterPumpQuirk.shootWaterBall(level, instance, entity, WATER_BALL, (float) (instance.isMastered(entity) ? CONFIG.ballDamageMastered : CONFIG.ballDamage), 1.0F, 1.5F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.0F, 1.2F);
        instance.setCoolDown(CONFIG.ballCooldown, WATER_BALL);
        instance.addMasteryPoint(entity);
    }

    // Suffocation

    public static boolean canSuffocate(LivingEntity target) {
        return TensuraStorages.getExistenceFrom(target).getAlignment() != Alignment.MAJIN && Alignment.shouldConsumeAir(target) && !target.canBreatheUnderwater()
                && !target.hasEffect(MobEffects.WATER_BREATHING) && !SkillUtils.hasSkill(target, IntrinsicSkills.WATER_BREATHING.get());
    }

    public static boolean isTrapped(LivingEntity target) {
        return TRAPS.stream().anyMatch(trap -> trap.target() == target);
    }

    private static void suffocation(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.suffocationRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (!canSuffocate(target)) {
            fail(entity, Component.translatable("tracadamia.skill.water_control.cant_suffocate"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, SUFFOCATION)) {
            return;
        }

        useWater(level, instance, entity, CONFIG.suffocationSourcesUsed);

        TRAPS.removeIf(trap -> trap.target() == target);
        long time = level.getGameTime();
        TRAPS.add(new Trap(instance, entity, target, time, time + CONFIG.suffocationSeconds * 20L, target.position()));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.7F);
        instance.setCoolDown(CONFIG.suffocationCooldown, SUFFOCATION);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickTrap(Trap trap) {
        LivingEntity target = trap.target();
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || level.getGameTime() > trap.until()) {
            return false;
        }

        Vec3 pos = trap.pos();
        if (!target.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
            if (!target.isPassenger() && target.position().distanceToSqr(pos) > 0.0025D) {
                target.teleportTo(pos.x, pos.y, pos.z);
            }
            target.setDeltaMovement(Vec3.ZERO);
        }
        target.resetFallDistance();
        target.clearFire();
        target.setAirSupply(Math.max(-20, target.getAirSupply() - AIR_DRAIN));

        long age = level.getGameTime() - trap.start();
        if (target.getAirSupply() <= 0 && age % 20L == 0L) {
            target.invulnerableTime = 0;
            target.hurt(((Skill) trap.instance().getSkill()).createSource(trap.instance(), trap.owner(), DamageTypes.DROWN, SUFFOCATION), (float) CONFIG.suffocationDamage);
        }

        if (age % 2L == 0L) {
            Vec3 center = target.getBoundingBox().getCenter();
            double radius = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.6D + 0.3D;
            RandomSource random = level.random;
            for (int i = 0; i < 24; i++) {
                Vec3 point = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(radius);
                level.sendParticles(WATER_DUST, center.x + point.x, center.y + point.y, center.z + point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.sendParticles(ParticleTypes.SPLASH, center.x, center.y, center.z, 4, radius * 0.4D, radius * 0.4D, radius * 0.4D, 0.0D);
        }
        return true;
    }

    // Tidal Wave

    private static void tidalWave(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, TIDAL_WAVE)) {
            return;
        }

        useWater(level, instance, entity, CONFIG.waveSourcesUsed);

        Vec3 direction = Vec3.directionFromRotation(0.0F, entity.getYRot());
        float damage = (float) (instance.isMastered(entity) ? CONFIG.waveDamageMastered : CONFIG.waveDamage);
        WAVES.add(new Wave(instance, entity, entity.position().add(direction.scale(entity.getBbWidth())), direction, damage));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 2.0F, 0.5F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        instance.setCoolDown(CONFIG.waveCooldown, TIDAL_WAVE);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickWave(Wave wave) {
        LivingEntity owner = wave.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level) || wave.travelled >= CONFIG.waveDistance) {
            return false;
        }

        wave.travelled += WAVE_SPEED;
        Vec3 front = wave.origin.add(wave.direction.scale(wave.travelled));
        Vec3 side = new Vec3(-wave.direction.z, 0.0D, wave.direction.x).scale(CONFIG.waveWidth * 0.5D);
        AABB area = new AABB(front.add(side), front.subtract(side)).expandTowards(0.0D, WAVE_HEIGHT, 0.0D).inflate(0.75D, 0.0D, 0.75D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != owner && target.isAlive() && !target.isSpectator() && !wave.hit.contains(target) && HitRules.canHit(owner, target))) {
            wave.hit.add(target);
            target.invulnerableTime = 0;
            target.hurt(WaterPumpQuirk.waterSource(wave.instance, owner, TIDAL_WAVE), wave.damage);
            target.clearFire();
            SkillHelper.knockBack(target, owner, wave.instance, wave.direction, 1.2D, 0.0D, 0.35D);
            target.hurtMarked = true;
        }

        RandomSource random = level.random;
        for (double along = -1.0D; along <= 1.0D; along += 1.0D / Math.max(2.0D, CONFIG.waveWidth)) {
            Vec3 spot = front.add(side.scale(along));
            double height = WAVE_HEIGHT * (0.7D + random.nextDouble() * 0.3D);
            level.sendParticles(WATER_DUST, spot.x, spot.y + random.nextDouble() * height, spot.z, 2, 0.15D, 0.2D, 0.15D, 0.0D);
            level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y + height, spot.z, 2, 0.2D, 0.1D, 0.2D, 0.1D);
            if (random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.FALLING_WATER, spot.x, spot.y + height, spot.z, 1, 0.2D, 0.0D, 0.2D, 0.0D);
            }
        }

        WaterPumpQuirk.douse(level, wave.instance, owner, front);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TRAPS.isEmpty()) {
            TRAPS.removeIf(trap -> !tickTrap(trap));
        }

        if (!WAVES.isEmpty()) {
            WAVES.removeIf(wave -> !tickWave(wave));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TRAPS.clear();
        WAVES.clear();
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

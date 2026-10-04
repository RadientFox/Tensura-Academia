package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.DamageReduction;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class VibrateQuirk extends Skill {

    private static final QuirkSkillsConfig.Vibrate CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Vibrate;

    private static final int EARTHQUAKE = 0;
    private static final int TREMORING_EARTH = 1;
    private static final int VIBRATE = 2;
    private static final int TARGETED_TREMOR = 3;

    public static final int EFFECT_EARTHQUAKE = 0;
    public static final int EFFECT_TREMOR = 1;
    public static final int EFFECT_VIBRATE = 2;

    private static final double TARGET_RAY_OFFSET = 1.0D;
    private static final int EARTHQUAKE_TICKS = 30;
    private static final int TREMOR_TICKS = 60;
    private static final int VIBRATE_PULSES = 4;
    private static final int VIBRATION_INTERVAL = 2;
    private static final float TREMOR_BREAK_CHANCE = 0.3F;
    private static final int SURFACE_SEARCH = 6;

    private static final int QUAKE_WAVE_TICKS = 12;
    private static final int TREMOR_RINGS_PER_TICK = 2;
    private static final float LIFT_CHANCE = 0.3F;
    private static final int LIFTS_PER_TICK = 24;
    private static final int DEBRIS_PER_TICK = 32;

    private static final int SEEK_TICKS = 200;
    private static final float SEEK_BREAK_CHANCE = 0.25F;
    private static final double SEEK_HIT_REACH = 1.5D;
    private static final double SEEK_HIT_HEIGHT = 8.0D;
    private static final double ERUPT_RADIUS = 3.0D;

    private static final Map<Block, Block> CRACKED = Map.ofEntries(
            Map.entry(Blocks.STONE, Blocks.COBBLESTONE),
            Map.entry(Blocks.COBBLESTONE, Blocks.GRAVEL),
            Map.entry(Blocks.DIRT, Blocks.COARSE_DIRT),
            Map.entry(Blocks.PODZOL, Blocks.COARSE_DIRT),
            Map.entry(Blocks.MYCELIUM, Blocks.COARSE_DIRT),
            Map.entry(Blocks.DIRT_PATH, Blocks.COARSE_DIRT),
            Map.entry(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE),
            Map.entry(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
            Map.entry(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS),
            Map.entry(Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES),
            Map.entry(Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS),
            Map.entry(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            Map.entry(Blocks.SANDSTONE, Blocks.SAND),
            Map.entry(Blocks.RED_SANDSTONE, Blocks.RED_SAND),
            Map.entry(Blocks.ANDESITE, Blocks.GRAVEL),
            Map.entry(Blocks.DIORITE, Blocks.GRAVEL),
            Map.entry(Blocks.GRANITE, Blocks.GRAVEL));

    private static final Set<UUID> IMMUNE = new HashSet<>();
    private static final List<Shaking> SHAKING = new ArrayList<>();
    private static final List<Quake> QUAKES = new ArrayList<>();
    private static final List<Tremor> TREMORS = new ArrayList<>();
    private static final List<Seeker> SEEKERS = new ArrayList<>();

    private static final class Quake {
        private final ServerLevel level;
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final Vec3 center;
        private final double radius;
        private int age;
        private double reached;

        private Quake(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 center, double radius) {
            this.level = level;
            this.instance = instance;
            this.owner = owner;
            this.center = center;
            this.radius = radius;
        }
    }

    // tear up the ground
    private static final class Tremor {
        private final ServerLevel level;
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final BlockPos center;
        private final int reach;
        private int ring = 1;

        private Tremor(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, BlockPos center, int reach) {
            this.level = level;
            this.instance = instance;
            this.owner = owner;
            this.center = center;
            this.reach = reach;
        }
    }

    private static final class Seeker {
        private final ServerLevel level;
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private Vec3 head;
        private int age;

        private Seeker(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity target, Vec3 head) {
            this.level = level;
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.head = head;
        }
    }

    private static final class Shaking {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final float pulseDamage;
        private final long start;
        private final int duration;
        private int pulses;

        private Shaking(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float pulseDamage, long start, int duration) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.pulseDamage = pulseDamage;
            this.start = start;
            this.duration = duration;
        }
    }

    public VibrateQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case EARTHQUAKE -> CONFIG.earthquakeAuraCost;
            case TREMORING_EARTH -> CONFIG.tremorAuraCost;
            case VIBRATE -> CONFIG.vibrateAuraCost;
            case TARGETED_TREMOR -> CONFIG.targetedTremorAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 4;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int modes = hasDetection(entity) ? 4 : 3;
        return Math.floorMod(Math.min(mode, modes - 1) + (reverse ? -1 : 1), modes);
    }

    private static boolean hasDetection(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.VIBRATION_DETECTION.get()).filter(instance -> instance.getMastery() >= 0.0D).isPresent();
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case EARTHQUAKE -> "vibrate.earthquake";
            case TREMORING_EARTH -> "vibrate.tremoring_earth";
            case VIBRATE -> "vibrate.vibrate";
            case TARGETED_TREMOR -> "vibrate.targeted_tremor";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case EARTHQUAKE -> earthquake(level, instance, entity);
            case TREMORING_EARTH -> tremoringEarth(level, instance, entity);
            case VIBRATE -> vibrate(level, instance, entity);
            case TARGETED_TREMOR -> targetedTremor(level, instance, entity);
        }
    }

    // Vibration Control and Vibration Redirection toggle

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (!entity.level().isClientSide) {
            IMMUNE.add(entity.getUUID());
            VibrationDetectionQuirk.updateEarthBoost(entity);
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        IMMUNE.remove(entity.getUUID());
        VibrationDetectionQuirk.updateEarthBoost(entity);
    }

    public static double getEarthBoost(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.VIBRATE.get()).filter(instance -> instance.getMastery() >= 0.0D && instance.isToggled()).isPresent() ? CONFIG.earthBoost : 0.0D;
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        IMMUNE.remove(entity.getUUID());
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() || IMMUNE.contains(entity.getUUID());
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        VibrationDetectionQuirk.updateEarthBoost(entity);
        if (!instance.isToggled()) {
            IMMUNE.remove(entity.getUUID());
            return;
        }

        IMMUNE.add(entity.getUUID());
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    // Vibration Control
    public static boolean isImmune(Entity entity) {
        return !IMMUNE.isEmpty() && IMMUNE.contains(entity.getUUID());
    }

    // Vibration Redirection
    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (instance.isToggled() && instance.getMastery() >= 0.0D && TensuraDamageHelper.isPhysicalAttack(source)) {
            amount.set(DamageReduction.reduce(owner, source, amount.get(), CONFIG.redirectionReduction));
        }

        return true;
    }

    private static void hit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode) {
        target.invulnerableTime = 0;
        target.hurt(((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, mode), damage);
    }

    private static void earthHit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode) {
        target.invulnerableTime = 0;
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.EARTH_ELEMENTAL, mode);
        ((TensuraDamageSource) source).tensura$setElement(Element.EARTH);
        target.hurt(source, damage);
    }

    private static void showEffect(ServerLevel level, int kind, Vec3 pos, float radius, int entityId, int duration) {
        EffectPayload payload = new EffectPayload(kind, pos, radius, entityId, duration);
        for (var player : level.players()) {
            if (player.distanceToSqr(pos) < (radius + 96.0D) * (radius + 96.0D)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    static void shakeGround(ServerLevel level, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            Vec3 pos = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            level.gameEvent(GameEvent.HIT_GROUND, pos, GameEvent.Context.of(level.getBlockState(BlockPos.containing(pos).below())));
        }
    }


    private static void earthquake(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, EARTHQUAKE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.earthquakeRadiusMastered : CONFIG.earthquakeRadius;
        float damage = (float) (mastered ? CONFIG.earthquakeDamageMastered : CONFIG.earthquakeDamage);
        AABB area = entity.getBoundingBox().inflate(radius, 4.0D, radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != entity && target.isAlive()
                && target.position().subtract(entity.position()).horizontalDistance() <= radius && VibrationDetectionQuirk.isGrounded(target))) {
            earthHit(instance, entity, target, damage, EARTHQUAKE);
            target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), CONFIG.earthquakeStunSeconds * 20, 0, false, false, true), entity);
        }

        QUAKES.add(new Quake(level, instance, entity, entity.position(), radius));
        shakeGround(level, entity.position(), radius * 0.5D, 8);
        showEffect(level, EFFECT_EARTHQUAKE, entity.position(), (float) radius, entity.getId(), EARTHQUAKE_TICKS);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.6F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.earthquakeCooldown, EARTHQUAKE);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickQuake(Quake quake) {
        quake.age++;
        double reach = Math.min(quake.radius, quake.radius * quake.age / QUAKE_WAVE_TICKS);
        RandomSource random = quake.level.random;
        BlockPos middle = BlockPos.containing(quake.center);
        int lifts = 0;

        int edge = Mth.ceil(reach);
        for (int x = -edge; x <= edge; x++) {
            double outer = reach * reach - x * x;
            if (outer < 0.0D) {
                continue;
            }

            double inner = quake.reached * quake.reached - x * x;
            int zMin = inner < 0.0D ? 0 : Mth.floor(Math.sqrt(inner)) + 1;
            int zMax = Mth.floor(Math.sqrt(outer));
            for (int z = zMin; z <= zMax; z++) {
                for (int sign = z == 0 ? 1 : -1; sign <= 1; sign += 2) {
                    if (x * x + z * z < 4) {
                        continue;
                    }

                    BlockPos surface = GroundBlocks.findSurface(quake.level, middle.offset(x, 0, z * sign));
                    if (surface == null) {
                        continue;
                    }

                    double fade = 1.0D - Math.sqrt(x * x + z * z) / quake.radius;
                    if (lifts < LIFTS_PER_TICK && random.nextFloat() < LIFT_CHANCE
                            && GroundBlocks.lift(quake.level, quake.instance, quake.owner, surface, 0.22D + 0.2D * fade + random.nextDouble() * 0.06D)) {
                        lifts++;
                    } else if (random.nextInt(3) == 0) {
                        GroundBlocks.dust(quake.level, surface, quake.level.getBlockState(surface), 2);
                    }
                }
            }
        }

        double angle = random.nextDouble() * Math.PI * 2.0D;
        quake.level.playSound(null, quake.center.x + Math.cos(angle) * reach, quake.center.y, quake.center.z + Math.sin(angle) * reach, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
        quake.reached = reach;
        return reach >= quake.radius || !quake.owner.isAlive();
    }


    private static void tremoringEarth(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, TREMORING_EARTH)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double half = (mastered ? CONFIG.tremorSizeMastered : CONFIG.tremorSize) * 0.5D;
        float damage = (float) (mastered ? CONFIG.tremorDamageMastered : CONFIG.tremorDamage);
        AABB area = new AABB(entity.getX() - half, entity.getY() - SURFACE_SEARCH, entity.getZ() - half, entity.getX() + half, entity.getY() + 12.0D, entity.getZ() + half);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != entity && target.isAlive())) {
            earthHit(instance, entity, target, damage, TREMORING_EARTH);
            target.push(0.0D, 0.7D, 0.0D);
            target.hurtMarked = true;
        }

        if (CONFIG.tremorBreaksBlocks) {
            TREMORS.add(new Tremor(level, instance, entity, entity.blockPosition(), Mth.ceil(half)));
        } else {
            QUAKES.add(new Quake(level, instance, entity, entity.position(), half));
        }

        shakeGround(level, entity.position(), half * 0.7D, 12);
        showEffect(level, EFFECT_TREMOR, entity.position(), (float) half, entity.getId(), TREMOR_TICKS);
        level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3.0F, 0.4F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0F, 0.4F);
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.tremorCooldown, TREMORING_EARTH);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickTremor(Tremor tremor) {
        int debris = 0;
        for (int step = 0; step < TREMOR_RINGS_PER_TICK && tremor.ring < tremor.reach; step++) {
            int ring = ++tremor.ring;
            for (int i = -ring; i <= ring; i++) {
                debris = shatter(tremor, i, -ring, debris);
                debris = shatter(tremor, i, ring, debris);
                if (i != -ring && i != ring) {
                    debris = shatter(tremor, -ring, i, debris);
                    debris = shatter(tremor, ring, i, debris);
                }
            }
        }

        RandomSource random = tremor.level.random;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        tremor.level.playSound(null, tremor.center.getX() + Math.cos(angle) * tremor.ring, tremor.center.getY(), tremor.center.getZ() + Math.sin(angle) * tremor.ring,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.5F + random.nextFloat() * 0.2F);
        return tremor.ring >= tremor.reach || !tremor.owner.isAlive();
    }

    private static int shatter(Tremor tremor, int x, int z, int debris) {
        ServerLevel level = tremor.level;
        BlockPos surface = GroundBlocks.findSurface(level, tremor.center.offset(x, 0, z));
        if (surface == null || !GroundBlocks.canBreak(level, tremor.instance, tremor.owner, surface)) {
            return debris;
        }

        RandomSource random = level.random;
        BlockState state = level.getBlockState(surface);
        if (random.nextFloat() < TREMOR_BREAK_CHANCE) {
            level.removeBlock(surface, false);
            GroundBlocks.griefed(level, tremor.instance, tremor.owner, surface);
            if (debris < DEBRIS_PER_TICK) {
                Vec3 out = new Vec3(x, 0.0D, z).normalize().scale(0.1D + random.nextDouble() * 0.25D);
                GroundBlocks.throwBlock(level, surface, state, out.add(0.0D, 0.45D + random.nextDouble() * 0.45D, 0.0D));
                return debris + 1;
            }

            if (random.nextInt(4) == 0) {
                level.levelEvent(2001, surface, Block.getId(state));
            }
            return debris;
        }

        Block cracked = CRACKED.get(state.getBlock());
        if (cracked != null) {
            level.setBlock(surface, cracked.defaultBlockState(), Block.UPDATE_ALL);
        }

        if (random.nextInt(3) == 0) {
            GroundBlocks.dust(level, surface, state, 3);
        }

        return debris;
    }


    private static void targetedTremor(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!hasDetection(entity)) {
            return;
        }

        LivingEntity target = VibrationDetectionQuirk.getSensedTarget(entity);
        if (target == null) {
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, TARGETED_TREMOR)) {
            return;
        }

        SEEKERS.add(new Seeker(level, instance, entity, target, entity.position()));
        shakeGround(level, entity.position(), 2.0D, 6);
        level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(), entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0F, 0.5F);
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.setCoolDown(CONFIG.targetedTremorCooldown, TARGETED_TREMOR);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickSeeker(Seeker seeker) {
        LivingEntity target = seeker.target;
        if (!seeker.owner.isAlive() || !target.isAlive() || target.isRemoved() || target.level() != seeker.level || ++seeker.age > SEEK_TICKS) {
            return true;
        }

        Vec3 flat = new Vec3(target.getX() - seeker.head.x, 0.0D, target.getZ() - seeker.head.z);
        double distance = flat.length();
        if (distance <= SEEK_HIT_REACH + target.getBbWidth() * 0.5D && Math.abs(target.getY() - seeker.head.y) <= SEEK_HIT_HEIGHT) {
            erupt(seeker);
            return true;
        }

        Vec3 step = flat.scale(Math.min(CONFIG.targetedTremorSpeed, distance) / distance);
        Vec3 next = seeker.head.add(step);
        BlockPos surface = GroundBlocks.findSurface(seeker.level, BlockPos.containing(next.x, seeker.head.y, next.z));
        seeker.head = surface == null ? next : new Vec3(next.x, surface.getY() + 1.0D, next.z);
        if (surface != null) {
            Vec3 side = new Vec3(-step.z, 0.0D, step.x).normalize();
            crack(seeker, surface, side.scale(0.15D), SEEK_BREAK_CHANCE);
            crack(seeker, BlockPos.containing(surface.getX() + side.x + 0.5D, surface.getY(), surface.getZ() + side.z + 0.5D), side.scale(0.25D), SEEK_BREAK_CHANCE * 0.5F);
            crack(seeker, BlockPos.containing(surface.getX() - side.x + 0.5D, surface.getY(), surface.getZ() - side.z + 0.5D), side.scale(-0.25D), SEEK_BREAK_CHANCE * 0.5F);
        }

        if (seeker.age % 2 == 0) {
            seeker.level.gameEvent(GameEvent.HIT_GROUND, seeker.head, GameEvent.Context.of(seeker.level.getBlockState(BlockPos.containing(seeker.head).below())));
        }

        if (seeker.age % 3 == 0) {
            seeker.level.playSound(null, seeker.head.x, seeker.head.y, seeker.head.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
        }
        return false;
    }

    private static void crack(Seeker seeker, BlockPos surface, Vec3 out, float breakChance) {
        ServerLevel level = seeker.level;
        BlockState state = level.getBlockState(surface);
        if (state.isAir() || !GroundBlocks.canBreak(level, seeker.instance, seeker.owner, surface)) {
            return;
        }

        RandomSource random = level.random;
        if (CONFIG.tremorBreaksBlocks && random.nextFloat() < breakChance) {
            level.removeBlock(surface, false);
            GroundBlocks.griefed(level, seeker.instance, seeker.owner, surface);
            GroundBlocks.throwBlock(level, surface, state, out.add(0.0D, 0.4D + random.nextDouble() * 0.4D, 0.0D));
            return;
        }

        Block cracked = CRACKED.get(state.getBlock());
        if (CONFIG.tremorBreaksBlocks && cracked != null) {
            level.setBlock(surface, cracked.defaultBlockState(), Block.UPDATE_ALL);
        }

        GroundBlocks.dust(level, surface, state, 4);
    }

    private static void erupt(Seeker seeker) {
        ServerLevel level = seeker.level;
        LivingEntity target = seeker.target;
        boolean mastered = seeker.instance.isMastered(seeker.owner);
        earthHit(seeker.instance, seeker.owner, target, (float) (mastered ? CONFIG.targetedTremorDamageMastered : CONFIG.targetedTremorDamage), TARGETED_TREMOR);
        target.push(0.0D, 0.9D, 0.0D);
        target.hurtMarked = true;

        Vec3 point = new Vec3(target.getX(), seeker.head.y, target.getZ());
        BlockPos middle = BlockPos.containing(point).below();
        RandomSource random = level.random;
        int reach = Mth.ceil(ERUPT_RADIUS);
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (x * x + z * z > ERUPT_RADIUS * ERUPT_RADIUS) {
                    continue;
                }

                BlockPos surface = GroundBlocks.findSurface(level, middle.offset(x, 1, z));
                if (surface != null) {
                    Vec3 out = x == 0 && z == 0 ? Vec3.ZERO : new Vec3(x, 0.0D, z).normalize().scale(0.15D + random.nextDouble() * 0.2D);
                    crack(seeker, surface, out.add(0.0D, 0.2D, 0.0D), 0.5F);
                }
            }
        }

        showEffect(level, EFFECT_TREMOR, point, (float) ERUPT_RADIUS, target.getId(), 20);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, point.x, point.y + 0.1D, point.z, 0, 0.0D, 1.0D, 0.0D, ERUPT_RADIUS / 1.5D);
        level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y + 0.5D, point.z, 2, 0.5D, 0.3D, 0.5D, 0.0D);
        BlockState ground = level.getBlockState(middle);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), point.x, point.y + 0.3D, point.z, 40, 1.0D, 0.6D, 1.0D, 0.25D);
        }

        level.playSound(null, point.x, point.y, point.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 3.0F, 0.5F);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
    }

    private static void vibrate(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.vibrateRange + TARGET_RAY_OFFSET, false, true);
        if (target == null || target.distanceTo(entity) > CONFIG.vibrateRange + target.getBbWidth()) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, VIBRATE)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double health = target.getMaxHealth() * (mastered ? CONFIG.vibrateMaxHealthDamageMastered : CONFIG.vibrateMaxHealthDamage);
        int duration = CONFIG.vibrateSeconds * 20;
        hit(instance, entity, target, (float) (mastered ? CONFIG.vibrateDamageMastered : CONFIG.vibrateDamage), VIBRATE);
        SHAKING.add(new Shaking(instance, entity, target, (float) (health / VIBRATE_PULSES), level.getGameTime(), duration));
        showEffect(level, EFFECT_VIBRATE, target.position(), target.getBbWidth(), target.getId(), duration);
        entity.swing(InteractionHand.MAIN_HAND, true);

        Vec3 hand = entity.getEyePosition().add(entity.getLookAngle().scale(0.6D)).subtract(0.0D, 0.4D, 0.0D);
        level.sendParticles(new VibrationParticleOption(new EntityPositionSource(target, target.getBbHeight() * 0.5F), 4), hand.x, hand.y, hand.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 2.0F);
        instance.setCoolDown(CONFIG.vibrateCooldown, VIBRATE);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickShaking(Shaking shaking) {
        LivingEntity target = shaking.target;
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
            return true;
        }

        long elapsed = level.getGameTime() - shaking.start;
        if (elapsed % VIBRATION_INTERVAL == 0) {
            level.gameEvent(target, GameEvent.HIT_GROUND, target.position());
        }

        int interval = Math.max(1, shaking.duration / VIBRATE_PULSES);
        if (elapsed > 0 && elapsed % interval == 0 && shaking.pulses < VIBRATE_PULSES) {
            shaking.pulses++;
            hit(shaking.instance, shaking.owner, target, shaking.pulseDamage, VIBRATE);
            pulse(level, shaking, target);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.6F + shaking.pulses * 0.1F);
        }

        return elapsed >= shaking.duration && shaking.pulses >= VIBRATE_PULSES;
    }

    private static void pulse(ServerLevel level, Shaking shaking, LivingEntity target) {
        RandomSource random = level.random;
        BlockPos feet = target.blockPosition();
        BlockState ground = level.getBlockState(feet.below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), target.getX(), target.getY() + 0.1D, target.getZ(), 16, target.getBbWidth() * 0.6D, 0.05D, target.getBbWidth() * 0.6D, 0.15D);
        }

        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 1.5D + random.nextDouble() * 1.5D;
            BlockPos surface = GroundBlocks.findSurface(level, BlockPos.containing(target.getX() + Math.cos(angle) * distance, target.getY(), target.getZ() + Math.sin(angle) * distance));
            if (surface == null) {
                continue;
            }

            level.sendParticles(new VibrationParticleOption(new BlockPositionSource(surface.above()), 6), target.getX(), target.getY(0.5D), target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (!GroundBlocks.lift(level, shaking.instance, shaking.owner, surface, 0.18D + random.nextDouble() * 0.08D)) {
                GroundBlocks.dust(level, surface, level.getBlockState(surface), 2);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SHAKING.isEmpty()) {
            SHAKING.removeIf(VibrateQuirk::tickShaking);
        }

        if (!QUAKES.isEmpty()) {
            QUAKES.removeIf(VibrateQuirk::tickQuake);
        }

        if (!TREMORS.isEmpty()) {
            TREMORS.removeIf(VibrateQuirk::tickTremor);
        }

        if (!SEEKERS.isEmpty()) {
            SEEKERS.removeIf(VibrateQuirk::tickSeeker);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QUAKES.clear();
        TREMORS.clear();
        SEEKERS.clear();
        SHAKING.clear();
        IMMUNE.clear();
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

    // Seen on the client, used by VibrateClient
    public record Effect(int kind, Vec3 pos, float radius, int entityId, int duration, long start, long seed) {}

    private static final List<Effect> CLIENT_EFFECTS = new ArrayList<>();

    public static List<Effect> getClientEffects() {
        return CLIENT_EFFECTS;
    }

    public record EffectPayload(int kind, Vec3 pos, float radius, int entityId, int duration) implements CustomPacketPayload {
        public static final Type<EffectPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "vibrate_effect"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> POS_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x,
                ByteBufCodecs.DOUBLE, Vec3::y,
                ByteBufCodecs.DOUBLE, Vec3::z,
                Vec3::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, EffectPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, EffectPayload::kind,
                POS_CODEC, EffectPayload::pos,
                ByteBufCodecs.FLOAT, EffectPayload::radius,
                ByteBufCodecs.VAR_INT, EffectPayload::entityId,
                ByteBufCodecs.VAR_INT, EffectPayload::duration,
                EffectPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(EffectPayload.TYPE, EffectPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            long time = context.player().level().getGameTime();
            CLIENT_EFFECTS.add(new Effect(payload.kind(), payload.pos(), payload.radius(), payload.entityId(), payload.duration(), time, context.player().getRandom().nextLong()));
        }));
    }

}

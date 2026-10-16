package com.radient.tensuraacadamia.ability.unique.quirks.attraction;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AttractionBlackHole {

    private static final QuirkSkillsConfig.Attraction CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Attraction;

    private static final int VISUAL_PER_TICK = 18;
    private static final double PULL_REACH = 1.5D;
    private static final double PULL_SPEED = 0.35D;
    private static final double CORE_SHARE = 0.12D;
    private static final double MIN_CORE = 0.6D;
    private static final int CONJURE_TICKS = 10;
    private static final double ORB_HIT = 0.4D;

    private static final Map<Integer, int[]> SPHERES = new HashMap<>();
    private static int nextId;

    private static final class Hole {
        private final ServerLevel level;
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final int id;
        private final Vec3 center;
        private final BlockPos middle;
        private final double radius;
        private final float damage;
        private final int absorbTicks;
        private final int[] sphere;
        private int done;
        private int age;

        private Hole(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode, int id, Vec3 center, double radius, float damage, int absorbTicks) {
            this.level = level;
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
            this.id = id;
            this.center = center;
            this.middle = BlockPos.containing(center);
            this.radius = radius;
            this.damage = damage;
            this.absorbTicks = Math.max(1, absorbTicks);
            this.sphere = getSphere(radius);
        }
    }

    private static final class Orb {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final int mode;
        private final int id;
        private Vec3 pos = Vec3.ZERO;
        private Vec3 direction = Vec3.ZERO;
        private double travelled;
        private int age;

        private Orb(ManasSkillInstance instance, LivingEntity owner, int mode, int id) {
            this.instance = instance;
            this.owner = owner;
            this.mode = mode;
            this.id = id;
        }
    }

    private static final List<Hole> HOLES = new ArrayList<>();
    private static final List<Orb> ORBS = new ArrayList<>();

    private AttractionBlackHole() {
    }

    static int nextId() {
        return ++nextId;
    }

    private static int[] getSphere(double radius) {
        int key = (int) Math.round(radius * 2.0D);
        return SPHERES.computeIfAbsent(key, diameter -> {
            double r = diameter * 0.5D;
            int reach = Mth.ceil(r);
            List<int[]> points = new ArrayList<>();
            for (int x = -reach; x <= reach; x++) {
                for (int y = -reach; y <= reach; y++) {
                    for (int z = -reach; z <= reach; z++) {
                        if (x * x + y * y + z * z <= r * r) {
                            points.add(new int[]{x, y, z, x * x + y * y + z * z});
                        }
                    }
                }
            }
            points.sort((a, b) -> Integer.compare(a[3], b[3]));
            int[] packed = new int[points.size() * 3];
            for (int i = 0; i < points.size(); i++) {
                int[] point = points.get(i);
                packed[i * 3] = point[0];
                packed[i * 3 + 1] = point[1];
                packed[i * 3 + 2] = point[2];
            }
            return packed;
        });
    }

    public static double getCore(double radius) {
        return Math.max(MIN_CORE, radius * CORE_SHARE);
    }

    static void open(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode, int id, Vec3 center, double size, double damage, int seconds) {
        double radius = size * 0.5D;
        Hole hole = new Hole(level, instance, owner, mode, id, center, radius, (float) damage, seconds * 20);
        HOLES.add(hole);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, Math.max(96.0D, radius * 4.0D),
                new AttractionPayloads.BlackHolePayload(id, center, (float) radius, (float) getCore(radius), hole.absorbTicks));
        playSound(level, center, SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
        playSound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, 2.5F, 0.5F);
    }

    private static boolean tickHole(Hole hole) {
        ServerLevel level = hole.level;
        int age = hole.age++;
        if (age >= hole.absorbTicks) {
            explode(hole);
            return true;
        }

        swallow(hole, age);
        pull(hole);
        if (age % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                Vec3 out = new Vec3(level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D, level.random.nextDouble() - 0.5D).normalize().scale(hole.radius * (0.5D + level.random.nextDouble() * 0.5D));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, hole.center.x + out.x, hole.center.y + out.y, hole.center.z + out.z, 0, -out.x, -out.y, -out.z, 0.05D);
            }
        }
        if (age % 20 == 0) {
            playSound(level, hole.center, SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.4F);
            playSound(level, hole.center, SoundEvents.PORTAL_AMBIENT, 2.0F, 0.4F);
        }
        return false;
    }

    private static void swallow(Hole hole, int age) {
        ServerLevel level = hole.level;
        int total = hole.sphere.length / 3;
        int until = Math.min(total, Mth.ceil(total * (age + 1.0D) / hole.absorbTicks));
        if (!GroundBlocks.canGrief(level, hole.owner)) {
            hole.done = until;
            return;
        }

        int[] states = new int[VISUAL_PER_TICK];
        long[] positions = new long[VISUAL_PER_TICK];
        int shown = 0;
        int swallowed = 0;
        int every = Math.max(1, (until - hole.done) / VISUAL_PER_TICK / 2);
        for (int i = hole.done; i < until; i++) {
            BlockPos pos = hole.middle.offset(hole.sphere[i * 3], hole.sphere[i * 3 + 1], hole.sphere[i * 3 + 2]);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0.0F || !GroundBlocks.canBreak(level, hole.instance, hole.owner, pos)) {
                continue;
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            GroundBlocks.griefed(level, hole.instance, hole.owner, pos);
            if (shown < VISUAL_PER_TICK && swallowed++ % every == 0 && state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty()) {
                states[shown] = Block.getId(state);
                positions[shown] = pos.asLong();
                shown++;
            }
        }
        hole.done = until;

        if (shown > 0) {
            PacketDistributor.sendToPlayersNear(level, null, hole.center.x, hole.center.y, hole.center.z, Math.max(96.0D, hole.radius * 4.0D),
                    new AttractionPayloads.SwallowPayload(hole.id, Arrays.copyOf(states, shown), Arrays.copyOf(positions, shown)));
        }
    }

    private static void pull(Hole hole) {
        double reach = hole.radius * PULL_REACH;
        AABB area = new AABB(hole.center, hole.center).inflate(reach);
        for (Entity entity : hole.level.getEntities((Entity) null, area, entity -> entity != hole.owner && !entity.isSpectator() && (entity instanceof LivingEntity || entity instanceof ItemEntity))) {
            Vec3 offset = hole.center.subtract(entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D));
            double distance = offset.length();
            if (distance > reach || distance < 0.3D) {
                continue;
            }

            Vec3 pull = offset.scale(Math.min(distance, PULL_SPEED * (1.0D + (1.0D - distance / reach) * 2.0D)) / distance);
            if (entity instanceof LivingEntity living) {
                Changeable<Vec3> moved = Changeable.of(living.getDeltaMovement().scale(0.5D).add(pull));
                if (living.getType().is(TensuraEntityTags.NO_FORCED_MOVE) || TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(living, hole.owner, hole.instance, moved).isFalse() || moved.get() == null) {
                    continue;
                }
                living.setDeltaMovement(moved.get());
                living.hurtMarked = true;
                living.resetFallDistance();
            } else {
                entity.setDeltaMovement(pull);
                entity.hasImpulse = true;
            }
        }
    }

    private static void explode(Hole hole) {
        ServerLevel level = hole.level;
        DamageSource source = ((Skill) hole.instance.getSkill()).createSource(hole.instance, hole.owner, TensuraDamageTypes.GRAVITY_ELEMENTAL, hole.mode);
        AABB area = new AABB(hole.center, hole.center).inflate(hole.radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != hole.owner && target.isAlive() && !target.isSpectator()
                && target.distanceToSqr(hole.center) <= hole.radius * hole.radius * 1.2D && HitRules.canHit(hole.owner, target))) {
            target.invulnerableTime = 0;
            target.hurt(source, hole.damage);
            Vec3 away = target.position().subtract(hole.center);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 1.0D, 0.0D) : away.normalize();
            Changeable<Vec3> push = Changeable.of(away.scale(1.6D).add(0.0D, 0.5D, 0.0D));
            if (!target.getType().is(TensuraEntityTags.NO_FORCED_MOVE) && !TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, hole.owner, hole.instance, push).isFalse() && push.get() != null) {
                target.setDeltaMovement(push.get());
                target.hurtMarked = true;
            }
        }

        Vec3 at = hole.center;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, Math.max(1, (int) (hole.radius / 6.0D)), hole.radius * 0.2D, hole.radius * 0.2D, hole.radius * 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 3, hole.radius * 0.2D, hole.radius * 0.2D, hole.radius * 0.2D, 0.0D);
        float volume = (float) Math.max(3.0D, hole.radius * 0.4D);
        playSound(level, at, SoundEvents.GENERIC_EXPLODE.value(), volume, 0.5F);
        playSound(level, at, SoundEvents.WARDEN_SONIC_BOOM, volume, 0.5F);
        playSound(level, at, SoundEvents.BEACON_DEACTIVATE, volume, 0.5F);
    }

    public static boolean throwOrb(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode) {
        if (ORBS.stream().anyMatch(orb -> orb.owner == owner) || EnergyHelper.isOutOfEnergy(owner, instance, mode)) {
            return false;
        }

        Orb orb = new Orb(instance, owner, mode, nextId());
        orb.pos = getHands(owner);
        ORBS.add(orb);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new AttractionPayloads.OrbPayload(orb.id, owner.getId(), AttractionPayloads.OrbPayload.CONJURING, orb.pos, Vec3.ZERO, 0.0F));
        owner.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, orb.pos, SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.4F);
        playSound(level, orb.pos, SoundEvents.PORTAL_TRIGGER, 0.6F, 2.0F);
        return true;
    }

    private static Vec3 getHands(LivingEntity owner) {
        return owner.getEyePosition().add(owner.getViewVector(1.0F).scale(0.9D * owner.getScale())).subtract(0.0D, 0.35D * owner.getScale(), 0.0D);
    }

    private static boolean tickOrb(Orb orb) {
        LivingEntity owner = orb.owner;
        if (!(owner.level() instanceof ServerLevel level) || owner.isRemoved() || !owner.isAlive() && orb.age < CONJURE_TICKS) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new AttractionPayloads.OrbPayload(orb.id, owner.getId(), AttractionPayloads.OrbPayload.GONE, orb.pos, Vec3.ZERO, 0.0F));
            return true;
        }

        int age = orb.age++;
        if (age < CONJURE_TICKS) {
            orb.pos = getHands(owner);
            if (age % 2 == 0) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, orb.pos.x, orb.pos.y, orb.pos.z, 3, 0.4D, 0.4D, 0.4D, 0.02D);
            }
            return false;
        }

        double speed = CONFIG.blackHoleSpeed / 20.0D;
        if (age == CONJURE_TICKS) {
            orb.pos = getHands(owner);
            orb.direction = owner.getViewVector(1.0F);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, new AttractionPayloads.OrbPayload(orb.id, owner.getId(), AttractionPayloads.OrbPayload.FLYING, orb.pos, orb.direction, (float) speed));
            playSound(level, orb.pos, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.6F);
            playSound(level, orb.pos, SoundEvents.ENDER_EYE_LAUNCH, 1.5F, 0.5F);
        }

        Vec3 next = orb.pos.add(orb.direction.scale(speed));
        BlockHitResult block = level.clip(new ClipContext(orb.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, owner, orb.pos, end, new AABB(orb.pos, end).inflate(ORB_HIT + 1.0D),
                target -> target instanceof LivingEntity living && living != owner && living.isAlive() && !living.isSpectator() && HitRules.canHit(owner, living), (float) ORB_HIT);
        orb.travelled += orb.pos.distanceTo(end);
        if (entity != null || block.getType() != HitResult.Type.MISS || orb.travelled >= CONFIG.blackHoleRange) {
            Vec3 at = entity != null ? entity.getEntity().getBoundingBox().getCenter() : block.getType() != HitResult.Type.MISS ? end.subtract(orb.direction.scale(0.3D)) : end;
            open(level, orb.instance, owner, orb.mode, orb.id, at, CONFIG.blackHoleSize, CONFIG.blackHoleDamage, CONFIG.blackHoleSeconds);
            return true;
        }

        orb.pos = end;
        return false;
    }

    public static void forget(LivingEntity owner) {
        ORBS.removeIf(orb -> orb.owner == owner);
    }

    private static void playSound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ORBS.isEmpty()) {
            ORBS.removeIf(AttractionBlackHole::tickOrb);
        }
        if (!HOLES.isEmpty()) {
            HOLES.removeIf(AttractionBlackHole::tickHole);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ORBS.clear();
        HOLES.clear();
    }

}

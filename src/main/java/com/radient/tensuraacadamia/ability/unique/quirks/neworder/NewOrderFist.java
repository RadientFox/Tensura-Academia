package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Become My Fist
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderFist {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    private static final double FIST_SIDE = 1.6D;
    private static final double FIST_BACK = 0.6D;
    private static final double FIST_DROP = 5.0D;
    private static final double FIST_RAISE = 2.2D;
    private static final double WIND_UP = 1.0D;
    private static final double MAX_REACH = 7.0D;

    // Tick stuff
    private static final int RISE = 8;
    private static final int DRAWN = 11;
    public static final int PUNCH = 14;
    private static final int HOLD = 18;
    public static final int RETURN = 30;

    private NewOrderFist() {
    }

    private static final double[][] SHAPE = {
            {0.0D, -0.5D, -0.5D}, {0.0D, 0.5D, -0.5D}, {0.0D, -0.5D, 0.5D}, {0.0D, 0.5D, 0.5D},
            {-1.0D, 0.0D, 0.0D}, {-2.0D, 0.0D, 0.0D}
    };

    private static final int[][] TAKEN = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}};

    private static final class Piece {
        private final TelekinesisBlockEntity block;
        private final BlockState state;
        private final @Nullable BlockPos origin;
        private final Vec3 home;
        private final double[] spot;
        private final int delay;

        private Piece(TelekinesisBlockEntity block, BlockState state, @Nullable BlockPos origin, Vec3 home, double[] spot, int delay) {
            this.block = block;
            this.state = state;
            this.origin = origin;
            this.home = home;
            this.spot = spot;
            this.delay = delay;
        }
    }

    private static final class Fist {
        private final ServerLevel level;
        private final ManasSkillInstance instance;
        private final LivingEntity user;
        private final LivingEntity target;
        private final Vec3 base;
        private final List<Piece> pieces = new ArrayList<>();
        private final float damage;
        private final long start;
        private Vec3 aim;

        private Fist(ServerLevel level, ManasSkillInstance instance, LivingEntity user, LivingEntity target, Vec3 base, float damage, long start) {
            this.level = level;
            this.instance = instance;
            this.user = user;
            this.target = target;
            this.base = base;
            this.damage = damage;
            this.start = start;
            this.aim = target.getBoundingBox().getCenter();
        }
    }

    private static final List<Fist> FISTS = new ArrayList<>();

    private static boolean isFistBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty();
    }

    private static boolean canLift(ServerLevel level, ManasSkillInstance instance, LivingEntity user, BlockPos pos, BlockState state) {
        return isFistBlock(level, pos, state) && GroundBlocks.canBreak(level, instance, user, pos) && level.getEntities((Entity) null, new AABB(pos.above())).isEmpty();
    }

    private static BlockState getFistBlock(BlockState ground) {
        return ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.DIRT_PATH) ? Blocks.DIRT.defaultBlockState() : ground;
    }

    static void fists(ServerLevel level, ManasSkillInstance instance, LivingEntity user, LivingEntity target, BlockState named) {
        long time = level.getGameTime();
        if (instance.onCoolDown(NewOrderQuirk.FIST_HIT) || FISTS.stream().anyMatch(fist -> fist.user == user && fist.target == target)) {
            return;
        }

        Vec3 toward = target.position().subtract(user.position()).multiply(1.0D, 0.0D, 1.0D);
        if (toward.lengthSqr() < 1.0E-4D) {
            toward = user.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D);
        }
        toward = toward.normalize();
        Vec3 side = new Vec3(-toward.z, 0.0D, toward.x);
        float damage = NewOrderUtil.meleeDamage(user, CONFIG.fistDamageMultiplier);
        int raised = 0;
        for (int sign = -1; sign <= 1; sign += 2) {
            Vec3 spot = target.position().add(side.scale(sign * (FIST_SIDE + target.getBbWidth() * 0.5D))).subtract(toward.scale(FIST_BACK));
            BlockPos surface = GroundBlocks.findSurface(level, BlockPos.containing(spot.x, target.getY(), spot.z));
            if (surface == null || target.getY() - (surface.getY() + 1.0D) > FIST_DROP) {
                continue;
            }

            Vec3 base = new Vec3(spot.x, surface.getY() + 1.0D, spot.z);
            BlockState ground = level.getBlockState(surface);
            Fist fist = new Fist(level, instance, user, target, base, damage, time);
            liftPieces(fist, surface, isFistBlock(level, surface, ground) ? getFistBlock(ground) : named);
            FISTS.add(fist);
            raised++;
            level.playSound(null, base.x, base.y, base.z, ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.2F, 0.6F);
            NewOrderUtil.playSound(level, base, TensuraSoundEvents.CAST_EARTH.get(), 0.7F, 1.2F);
        }

        if (raised > 0) {
            instance.setCoolDown(CONFIG.fistCooldown, NewOrderQuirk.FIST_HIT);
            if (level.random.nextInt(3) == 0) {
                instance.addMasteryPoint(user);
            }
        }
    }

    private static void liftPieces(Fist fist, BlockPos surface, BlockState fallback) {
        ServerLevel level = fist.level;
        for (int i = 0; i < SHAPE.length; i++) {
            BlockPos ground = GroundBlocks.findSurface(level, surface.offset(TAKEN[i][0], 0, TAKEN[i][1]));
            BlockState state = ground == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(ground);
            BlockPos origin = null;
            BlockState shown = fallback;
            if (ground != null && Math.abs(ground.getY() - surface.getY()) <= 1 && canLift(level, fist.instance, fist.user, ground, state)) {
                origin = ground.immutable();
                shown = getFistBlock(state);
                level.setBlock(ground, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                GroundBlocks.griefed(level, fist.instance, fist.user, ground);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), ground.getX() + 0.5D, ground.getY() + 0.9D, ground.getZ() + 0.5D, 8, 0.3D, 0.1D, 0.3D, 0.1D);
            }

            Vec3 home = origin != null ? Vec3.atCenterOf(origin) : fist.base.add(TAKEN[i][0], -0.5D, TAKEN[i][1]);
            TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, shown, 1.0F, home);
            level.addFreshEntity(block);
            fist.pieces.add(new Piece(block, origin != null ? state : shown, origin, home, SHAPE[i], i));
        }
    }

    private static float easeOut(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static float easeIn(float t) {
        return t * t;
    }

    private static float progress(long age, int from, int to) {
        return Mth.clamp((float) (age - from) / (to - from), 0.0F, 1.0F);
    }

    private static Vec3 getAnchor(Fist fist, long age, Vec3 forward) {
        Vec3 raised = fist.base.add(0.0D, FIST_RAISE, 0.0D);
        if (age < DRAWN) {
            return raised.subtract(forward.scale(WIND_UP * easeOut(progress(age, RISE, DRAWN))));
        }

        Vec3 drawn = raised.subtract(forward.scale(WIND_UP));
        Vec3 struck = fist.aim.subtract(forward.scale(fist.target.getBbWidth() * 0.5D + 0.6D));
        if (age < PUNCH) {
            return drawn.lerp(struck, easeIn(progress(age, DRAWN, PUNCH)));
        }
        return struck.lerp(drawn, 0.25D * progress(age, PUNCH, HOLD));
    }

    private static Vec3 getSpot(Vec3 anchor, Vec3 forward, double[] spot) {
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 up = side.cross(forward).normalize();
        return anchor.add(forward.scale(spot[0])).add(side.scale(spot[1])).add(up.scale(spot[2]));
    }

    // true once every block is back in the ground
    private static boolean tickFist(Fist fist, long time) {
        long age = time - fist.start;
        if (age < PUNCH && fist.target.isAlive() && fist.target.level() == fist.level) {
            fist.aim = fist.target.getBoundingBox().getCenter();
        }

        Vec3 forward = fist.aim.subtract(fist.base.add(0.0D, FIST_RAISE, 0.0D));
        forward = forward.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : forward.normalize();
        Vec3 anchor = getAnchor(fist, age, forward);
        if (age == PUNCH) {
            punch(fist);
        }

        for (Piece piece : fist.pieces) {
            Vec3 formed = getSpot(anchor, forward, piece.spot);
            Vec3 at;
            if (age < RISE + piece.delay) {
                at = piece.home.lerp(formed, easeOut(progress(age, piece.delay, RISE + piece.delay)));
            } else if (age < HOLD) {
                at = formed;
            } else {
                at = formed.lerp(piece.home, easeIn(progress(age, HOLD + piece.delay, RETURN)));
            }
            piece.block.keepAlive();
            piece.block.setDeltaMovement(Vec3.ZERO);
            piece.block.setPos(at.x, at.y - piece.block.getBbHeight() * 0.5D, at.z);
        }

        if (age >= RETURN) {
            settle(fist);
            return true;
        }
        return false;
    }

    private static void punch(Fist fist) {
        ServerLevel level = fist.level;
        LivingEntity target = fist.target;
        if (!target.isAlive() || target.level() != level || target.getBoundingBox().getCenter().distanceTo(fist.base) > MAX_REACH) {
            return;
        }

        DamageSource source = ((Skill) fist.instance.getSkill()).createSource(fist.instance, fist.user, TensuraDamageTypes.EARTH_ELEMENTAL, NewOrderQuirk.RULES);
        ((TensuraDamageSource) source).tensura$setElement(Element.EARTH);
        target.invulnerableTime = 0;
        if (target.hurt(source, fist.damage)) {
            Vec3 push = target.position().subtract(fist.base);
            SkillHelper.knockBack(target, fist.user, fist.instance, new Vec3(push.x, 0.0D, push.z), 0.5D, 0.0D, 0.15D);
        }

        Vec3 hit = target.getBoundingBox().getCenter();
        BlockState state = fist.pieces.isEmpty() ? Blocks.DIRT.defaultBlockState() : fist.pieces.get(0).block.getBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), hit.x, hit.y, hit.z, 24, 0.4D, 0.4D, 0.4D, 0.2D);
        level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 8, 0.3D, 0.3D, 0.3D, 0.3D);
        NewOrderUtil.playSound(level, hit, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        level.playSound(null, hit.x, hit.y, hit.z, state.getSoundType().getHitSound(), SoundSource.PLAYERS, 1.5F, 0.5F + level.random.nextFloat() * 0.2F);
    }

    private static void settle(Fist fist) {
        ServerLevel level = fist.level;
        for (Piece piece : fist.pieces) {
            piece.block.discard();
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, piece.state);
            if (piece.origin == null) {
                level.sendParticles(dust, piece.home.x, piece.home.y, piece.home.z, 6, 0.3D, 0.3D, 0.3D, 0.05D);
            } else if (level.getBlockState(piece.origin).canBeReplaced()) {
                level.setBlock(piece.origin, piece.state, Block.UPDATE_ALL);
                level.sendParticles(dust, piece.home.x, piece.home.y + 0.5D, piece.home.z, 6, 0.3D, 0.1D, 0.3D, 0.05D);
            } else {
                GroundBlocks.dropBlock(level, piece.origin.above(), piece.state, Vec3.ZERO);
            }
        }
        if (!fist.pieces.isEmpty()) {
            level.playSound(null, fist.base.x, fist.base.y, fist.base.z, fist.pieces.get(0).state.getSoundType().getPlaceSound(), SoundSource.PLAYERS, 1.2F, 0.7F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FISTS.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            FISTS.removeIf(fist -> tickFist(fist, time));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FISTS.forEach(NewOrderFist::settle);
        FISTS.clear();
    }

}

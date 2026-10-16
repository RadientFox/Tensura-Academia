package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ImpactRecoilQuirk;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Body of Air's moves
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderBodyMoves {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    // Fist Bump To The Earth info
    public static final int BUMP_WIND = 14;
    public static final int BUMP_HIT = 18;
    public static final int BUMP_END = 32;
    private static final int CRATER_TICKS = 6;
    public static final double BUMP_REACH = 0.9D;
    private static final double BUMP_FLOOR = 8.0D;
    private static final double BUMP_DOWN = 1.6D;

    // Air Spear info
    public static final int SPEAR_FORM = 10;
    public static final int SPEAR_RAISE = 16;
    public static final int SPEAR_HIT = 19;
    public static final int SPEAR_END = 34;
    public static final double SPEAR_CLOSE = 0.6D;
    private static final double SPEAR_PIN = 0.6D;
    private static final double SPEAR_WAVE_HEIGHT = 3.0D;
    private static final double SPEAR_WAVE_PUSH = 0.9D;
    private static final double SPEAR_WAVE_LIFT = 0.35D;
    private static final double BASE_COPY_SIZE = 4.0D;

    // Wind Clap info
    public static final int CLAP_SPREAD = 10;
    public static final int CLAP_HIT = 13;
    public static final int CLAP_END = 22;
    private static final double CLAP_LIFT = 0.6D;

    private NewOrderBodyMoves() {
    }

    private static final class Move {
        private final int action;
        private final ManasSkillInstance instance;
        private final LivingEntity user;
        private final @Nullable LivingEntity target;
        private final long start;
        private Vec3 point = Vec3.ZERO;
        private final List<List<BlockPos>> crater = new ArrayList<>();

        private Move(int action, ManasSkillInstance instance, LivingEntity user, @Nullable LivingEntity target, long start) {
            this.action = action;
            this.instance = instance;
            this.user = user;
            this.target = target;
            this.start = start;
        }
    }

    private static final List<Move> MOVES = new ArrayList<>();

    static boolean isMoving(LivingEntity entity) {
        return MOVES.stream().anyMatch(move -> move.user == entity);
    }

    private static boolean canMove(LivingEntity entity) {
        if (!NewOrderBody.isInside(entity) || !NewOrderRules.hasRule(entity, NewOrderRule.HARDEN)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_body"));
            return false;
        }
        if (isMoving(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.copy_busy"));
            return false;
        }
        return true;
    }

    private static void start(Move move, int charge, int ticks) {
        MOVES.add(move);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(move.user, new NewOrderPayloads.CopyActionPayload(move.user.getId(), move.action, charge, ticks,
                move.target == null ? -1 : move.target.getId(), move.point));
    }

    static void fistBump(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!canMove(entity) || EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.FIST_BUMP)) {
            return;
        }

        instance.setCoolDown(CONFIG.fistBumpCooldown, NewOrderQuirk.FIST_BUMP);
        instance.addMasteryPoint(entity);
        start(new Move(NewOrderCopy.ACTION_FIST_BUMP, instance, entity, null, level.getGameTime()), BUMP_WIND, BUMP_END);
        NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.CAST_WIND.get(), 1.2F, 0.6F);
        NewOrderUtil.playSound(level, NewOrderCopy.getDrawnHand(entity), SoundEvents.BREEZE_INHALE, 1.5F, 0.5F);
    }

    static void airSpear(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!canMove(entity) || EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.AIR_SPEAR)) {
            return;
        }

        instance.setCoolDown(CONFIG.spearCooldown, NewOrderQuirk.AIR_SPEAR);
        instance.addMasteryPoint(entity);
        Move move = new Move(NewOrderCopy.ACTION_SPEAR, instance, entity, null, level.getGameTime());
        move.point = getSpearSpot(level, entity);
        start(move, SPEAR_FORM, SPEAR_END);
        Vec3 front = NewOrderCopy.getBeamOrigin(entity);
        NewOrderUtil.playSound(level, front, TensuraSoundEvents.CAST_WIND.get(), 1.0F, 1.2F);
        NewOrderUtil.playSound(level, front, SoundEvents.BREEZE_CHARGE, 1.2F, 0.7F);
    }

    static void windClap(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!canMove(entity)) {
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.clapRange * entity.getScale(), false, true);
        if (target == null || target == entity) {
            NewOrderUtil.fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.WIND_CLAP)) {
            return;
        }

        instance.setCoolDown(CONFIG.clapCooldown, NewOrderQuirk.FIST_BUMP);
        instance.addMasteryPoint(entity);
        start(new Move(NewOrderCopy.ACTION_CLAP, instance, entity, target, level.getGameTime()), CLAP_SPREAD, CLAP_END);
        NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.CAST_WIND.get(), 1.0F, 0.8F);
        NewOrderUtil.playSound(level, entity.getEyePosition(), SoundEvents.BREEZE_CHARGE, 1.5F, 0.6F);
    }

    private static boolean tickMove(Move move, long time) {
        LivingEntity user = move.user;
        if (!(user.level() instanceof ServerLevel level) || user.isRemoved() || !user.isAlive() || !NewOrderBody.isInside(user)) {
            return true;
        }

        long age = time - move.start;
        return switch (move.action) {
            case NewOrderCopy.ACTION_FIST_BUMP -> tickBump(level, move, age);
            case NewOrderCopy.ACTION_SPEAR -> tickSpear(level, move, age);
            default -> tickClap(level, move, age);
        };
    }

    // Fist Bump To The Earth
    private static boolean tickBump(ServerLevel level, Move move, long age) {
        LivingEntity user = move.user;
        if (age == BUMP_WIND - 4) {
            NewOrderUtil.playSound(level, NewOrderCopy.getDrawnHand(user), SoundEvents.BREEZE_CHARGE, 1.5F, 0.5F);
        } else if (age == BUMP_WIND) {
            NewOrderUtil.playSound(level, NewOrderCopy.getDrawnHand(user), SoundEvents.BREEZE_SHOOT, 2.0F, 0.5F);
        } else if (age == BUMP_HIT) {
            bump(level, move);
        } else if (age > BUMP_HIT && age - BUMP_HIT <= CRATER_TICKS) {
            carve(level, move, (int) (age - BUMP_HIT - 1));
        }
        return age >= BUMP_END;
    }

    static Vec3 getImpact(Level level, LivingEntity user) {
        Vec3 spot = user.position().add(NewOrderCopy.getForward(user).scale(BUMP_REACH * NewOrderCopy.getCopyUnit(user))).add(0.0D, 1.0D, 0.0D);
        return getGround(level, user, spot);
    }

    private static Vec3 getGround(Level level, LivingEntity user, Vec3 spot) {
        BlockHitResult floor = level.clip(new ClipContext(spot, spot.subtract(0.0D, BUMP_FLOOR + 1.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        return floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : spot.subtract(0.0D, 1.0D, 0.0D);
    }

    private static boolean inBlast(Vec3 offset, double width, double height) {
        double x = offset.x / (width * 0.5D);
        double y = offset.y / (height * 0.5D);
        double z = offset.z / (width * 0.5D);
        return x * x + y * y + z * z <= 1.0D;
    }

    private static void bump(ServerLevel level, Move move) {
        LivingEntity user = move.user;
        Vec3 impact = getImpact(level, user);
        double width = CONFIG.fistBumpSize;
        double height = CONFIG.fistBumpHeight;
        DamageSource source = NewOrderUtil.windSource(move.instance, user, NewOrderQuirk.FIST_BUMP);
        float damage = NewOrderUtil.meleeDamage(user, CONFIG.fistBumpDamagePercent);
        AABB area = new AABB(impact, impact).inflate(width * 0.5D, height * 0.5D, width * 0.5D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target.isAlive() && !target.isSpectator() && HitRules.canHit(user, target)
                && inBlast(target.getBoundingBox().getCenter().subtract(impact), width + target.getBbWidth(), height + target.getBbHeight()))) {
            NewOrderUtil.hit(target, source, damage);
            NewOrderUtil.fling(target, user, move.instance, new Vec3(0.0D, -BUMP_DOWN, 0.0D));
        }

        if (CONFIG.fistBumpBreaksBlocks && GroundBlocks.canGrief(level, user)) {
            planCrater(level, move, impact, width, height);
        }

        NewOrderUtil.impact(level, impact, NewOrderPayloads.ImpactPayload.FIST_BUMP, (float) (width * 0.5D));
        level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y + 0.8D, impact.z, 4, width * 0.06D, 0.4D, width * 0.06D, 0.0D);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, impact.x, impact.y + 1.0D, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, impact.x, impact.y + 0.1D, impact.z, 0, 0.0D, 1.0D, 0.0D, width / 3.0D);
        NewOrderUtil.playSound(level, impact, SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.6F);
        NewOrderUtil.playSound(level, impact, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.5F, 0.5F);
        NewOrderUtil.playSound(level, impact, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.5F);
    }

    private static void planCrater(ServerLevel level, Move move, Vec3 impact, double width, double height) {
        for (int band = 0; band < CRATER_TICKS; band++) {
            move.crater.add(new ArrayList<>());
        }

        int across = Mth.ceil(width * 0.5D);
        int up = Mth.ceil(height * 0.5D);
        BlockPos middle = BlockPos.containing(impact.x, impact.y - 0.5D, impact.z);
        for (int x = -across; x <= across; x++) {
            for (int z = -across; z <= across; z++) {
                for (int y = -up; y <= up; y++) {
                    double reach = Math.sqrt(sq(x / (width * 0.5D)) + sq(y / (height * 0.5D)) + sq(z / (width * 0.5D)));
                    BlockPos pos = middle.offset(x, y, z);
                    if (reach <= 1.0D && !level.getBlockState(pos).isAir()) {
                        move.crater.get(Math.min(CRATER_TICKS - 1, (int) (reach * CRATER_TICKS))).add(pos);
                    }
                }
            }
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    private static void carve(ServerLevel level, Move move, int band) {
        if (band < 0 || band >= move.crater.size()) {
            return;
        }

        for (BlockPos pos : move.crater.get(band)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !GroundBlocks.canBreak(level, move.instance, move.user, pos)) {
                continue;
            }

            level.setBlock(pos, state.getFluidState().isEmpty() ? Blocks.AIR.defaultBlockState() : state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            GroundBlocks.griefed(level, move.instance, move.user, pos);
        }
        move.crater.get(band).clear();
    }

    // Air Spear
    private static Vec3 getSpearGrip(LivingEntity user) {
        double unit = NewOrderCopy.getCopyUnit(user);
        return NewOrderCopy.getShoulder(user, false).add(NewOrderCopy.getForward(user).scale(NewOrderCopy.ARM_REACH * unit * 0.6D)).subtract(0.0D, NewOrderCopy.ARM_REACH * unit, 0.0D);
    }

    private static Vec3 getSpearSpot(ServerLevel level, LivingEntity user) {
        Vec3 grip = getSpearGrip(user);
        return getGround(level, user, new Vec3(grip.x, user.getY() + 1.0D, grip.z));
    }

    public static double getSpearScale(LivingEntity user) {
        return NewOrderCopy.getCopyUnit(user) / BASE_COPY_SIZE;
    }

    private static boolean tickSpear(ServerLevel level, Move move, long age) {
        LivingEntity user = move.user;
        if (age == SPEAR_FORM) {
            NewOrderUtil.playSound(level, NewOrderCopy.getBeamOrigin(user), SoundEvents.TRIDENT_RETURN, 1.5F, 0.5F);
        } else if (age == SPEAR_RAISE) {
            NewOrderUtil.playSound(level, NewOrderCopy.getGather(user), SoundEvents.BREEZE_SHOOT, 1.5F, 0.6F);
        } else if (age == SPEAR_HIT) {
            pierce(level, move);
        } else if (age == SPEAR_END - 6) {
            NewOrderUtil.playSound(level, move.point, SoundEvents.BREEZE_IDLE_AIR, 1.2F, 0.8F);
        }
        return age >= SPEAR_END;
    }

    private static void pierce(ServerLevel level, Move move) {
        LivingEntity user = move.user;
        boolean mastered = move.instance.isMastered(user);
        double scale = getSpearScale(user);
        Vec3 tip = getSpearSpot(level, user);
        move.point = tip;
        Vec3 grip = getSpearGrip(user);
        double half = (mastered ? CONFIG.spearSizeMastered : CONFIG.spearSize) * 0.5D * scale;
        double waveHalf = (mastered ? CONFIG.spearShockwaveSizeMastered : CONFIG.spearShockwaveSize) * 0.5D * scale;
        DamageSource source = NewOrderUtil.windSource(move.instance, user, NewOrderQuirk.AIR_SPEAR);
        float damage = NewOrderUtil.ruleDamage(user, CONFIG.spearDamage);
        AABB spear = new AABB(tip.x - half, tip.y - 1.0D, tip.z - half, tip.x + half, Math.max(grip.y, tip.y + 2.0D), tip.z + half);
        Set<LivingEntity> struck = new HashSet<>();
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, spear, other -> other != user && other.isAlive() && !other.isSpectator() && HitRules.canHit(user, other))) {
            NewOrderUtil.hit(hit, source, damage);
            NewOrderUtil.fling(hit, user, move.instance, new Vec3(0.0D, -SPEAR_PIN, 0.0D));
            struck.add(hit);
        }

        AABB wave = new AABB(tip.x - waveHalf, tip.y - 1.0D, tip.z - waveHalf, tip.x + waveHalf, tip.y + SPEAR_WAVE_HEIGHT * scale, tip.z + waveHalf);
        float waveDamage = (float) (damage * CONFIG.spearShockwaveDamagePercent);
        for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, wave, other -> other != user && other.isAlive() && !other.isSpectator() && !struck.contains(other)
                && HitRules.canHit(user, other))) {
            NewOrderUtil.hit(hit, source, waveDamage);
            Vec3 away = hit.position().subtract(tip).multiply(1.0D, 0.0D, 1.0D);
            away = away.lengthSqr() < 1.0E-4D ? NewOrderCopy.getForward(user) : away.normalize();
            NewOrderUtil.fling(hit, user, move.instance, away.scale(SPEAR_WAVE_PUSH).add(0.0D, SPEAR_WAVE_LIFT, 0.0D));
        }

        BlockState ground = level.getBlockState(BlockPos.containing(tip.x, tip.y - 0.3D, tip.z));
        NewOrderUtil.impact(level, tip, NewOrderPayloads.ImpactPayload.SPEAR, (float) waveHalf);
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, tip.x, tip.y + 0.5D, tip.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, tip.x, tip.y + 0.1D, tip.z, 0, 0.0D, 1.0D, 0.0D, half * 1.5D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, tip.x, tip.y + 0.15D, tip.z, 0, 0.0D, 1.0D, 0.0D, waveHalf * 2.0D / 3.0D);
        if (!ground.isAir()) {
            NewOrderUtil.playSound(level, tip, ground.getSoundType().getBreakSound(), 2.0F, 0.5F);
        }
        NewOrderUtil.playSound(level, tip, SoundEvents.TRIDENT_HIT_GROUND, 2.5F, 0.5F);
        NewOrderUtil.playSound(level, tip, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.6F);
        NewOrderUtil.playSound(level, tip, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.6F);
    }

    // Wind Clap
    private static boolean tickClap(ServerLevel level, Move move, long age) {
        if (age == CLAP_SPREAD) {
            for (Vec3 hand : NewOrderCopy.getSpreadHands(move.user)) {
                NewOrderUtil.playSound(level, hand, SoundEvents.BREEZE_INHALE, 1.0F, 0.7F);
            }
        } else if (age == CLAP_HIT) {
            clap(level, move);
        }
        return age >= CLAP_END;
    }

    private static void clap(ServerLevel level, Move move) {
        LivingEntity user = move.user;
        LivingEntity target = move.target;
        if (target == null || !target.isAlive() || target.level() != level || target.distanceTo(user) > CONFIG.clapRange * user.getScale() * 1.5D) {
            NewOrderUtil.playSound(level, user.getEyePosition(), SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 1.4F);
            return;
        }

        Vec3 at = target.getBoundingBox().getCenter();
        NewOrderUtil.hit(target, NewOrderUtil.windSource(move.instance, user, NewOrderQuirk.WIND_CLAP), NewOrderUtil.ruleDamage(user, CONFIG.clapDamage));
        Vec3 away = target.position().subtract(user.position()).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? NewOrderCopy.getForward(user) : away.normalize();
        NewOrderUtil.fling(target, user, move.instance, launch(away, CONFIG.clapKnockback));

        NewOrderUtil.impact(level, at, NewOrderPayloads.ImpactPayload.CLAP, Math.max(target.getBbWidth(), target.getBbHeight()));
        level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, at.x, at.y, at.z, 0, away.x, 0.0D, away.z, 1.5D);
        NewOrderUtil.playSound(level, at, SoundEvents.WIND_CHARGE_BURST.value(), 2.5F, 0.7F);
        NewOrderUtil.playSound(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.0F, 0.5F);
        NewOrderUtil.playSound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.6F);
    }
//Im deadass so sick and tired of this skill but its so cool

    static Vec3 launch(Vec3 direction, double blocks) {
        double y = 0.0D;
        double up = CLAP_LIFT;
        int ticks = 0;
        while (ticks < 200 && (ticks == 0 || y > 0.0D)) {
            y += up;
            up = (up - 0.08D) * 0.98D;
            ticks++;
        }

        double carried = 0.0D;
        double drag = 1.0D;
        for (int tick = 0; tick < ticks; tick++) {
            carried += drag;
            drag *= 0.91D;
        }
        return direction.scale(blocks / carried).add(0.0D, CLAP_LIFT, 0.0D);
    }

    static void forget(LivingEntity entity) {
        MOVES.removeIf(move -> move.user == entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MOVES.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            List.copyOf(MOVES).stream().filter(move -> tickMove(move, time)).forEach(MOVES::remove);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MOVES.clear();
    }

}

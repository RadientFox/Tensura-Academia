package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
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
public class TrajectQuirk extends Skill {

    private static final QuirkSkillsConfig.Traject CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Traject;

    private static final int ATTACK_REDIRECT = 0;
    private static final int FORCED_VECTOR = 1;
    private static final int RICOCHET_SHOT = 2;
    private static final int TRAJECTORY_CHANGE = 3;
    private static final int MODES = 4;

    public static final String REDIRECT_TAG = "redirectUntil";
    private static final String RICOCHET_TAG = "ricochetArmed";
    private static final String RICOCHET_PROJECTILE_TAG = "tracadamia_ricochet";
    private static final double MIN_REDIRECT_SPEED = 0.8D;
    private static final double TURN = 0.35D;
    private static final double SNAP_TICKS = 2.0D;
    private static final double STUCK = 0.05D;
    private static final double BOUNCED = 0.25D;
    private static final int MARKER_TICKS = 5;
    private static final DustParticleOptions MARKER = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.2F);
    private static final int HURL_TICKS = 60;
    private static final int HURL_TAKEOFF = 3;
    private static final double HURL_LIFT = 0.25D;
    private static final double HURL_STOPPED = 0.08D;
    private static final int RICOCHET_TICKS = 200;

    private record Mark(ManasSkillInstance instance, LivingEntity owner, LivingEntity target) {}

    private static final class Homing {
        private final Projectile projectile;
        private final LivingEntity owner;
        private final double speed;
        private Vec3 lastPos;

        private Homing(Projectile projectile, LivingEntity owner) {
            this.projectile = projectile;
            this.owner = owner;
            this.speed = projectile.getDeltaMovement().length();
            this.lastPos = projectile.position();
        }
    }

    private static final class Hurled {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final long start;
        private Vec3 lastPos;
        private double speed;

        private Hurled(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, long start, double speed) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.start = start;
            this.lastPos = target.position();
            this.speed = speed;
        }
    }

    private static final class Ricochet {
        private final LivingEntity owner;
        private final double range;
        private final double speed;
        private final long start;
        private final Set<Integer> hit = new HashSet<>();
        private Projectile projectile;
        private @Nullable LivingEntity struck;
        private @Nullable CompoundTag snapshot;

        private Ricochet(Projectile projectile, LivingEntity owner, double range, long start) {
            this.projectile = projectile;
            this.owner = owner;
            this.range = range;
            this.speed = Math.max(MIN_REDIRECT_SPEED, projectile.getDeltaMovement().length());
            this.start = start;
        }
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static final List<Homing> HOMING = new ArrayList<>();
    private static final List<LivingEntity> REDIRECTING = new ArrayList<>();
    private static final List<Hurled> HURLED = new ArrayList<>();
    private static final List<Ricochet> RICOCHETS = new ArrayList<>();

    public TrajectQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ATTACK_REDIRECT -> CONFIG.redirectAuraCost;
            case FORCED_VECTOR -> CONFIG.forcedVectorAuraCost;
            case RICOCHET_SHOT -> CONFIG.ricochetAuraCost;
            case TRAJECTORY_CHANGE -> CONFIG.markAuraCost;
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
            case ATTACK_REDIRECT -> "traject.attack_redirect";
            case FORCED_VECTOR -> "traject.forced_vector";
            case RICOCHET_SHOT -> "traject.ricochet_shot";
            case TRAJECTORY_CHANGE -> "traject.trajectory_change";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getTraject(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TRAJECT.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        MARKS.removeIf(mark -> mark.owner() == entity);
        HOMING.removeIf(homing -> homing.owner == entity);
        REDIRECTING.remove(entity);
        HURLED.removeIf(hurled -> hurled.owner == entity);
        RICOCHETS.removeIf(ricochet -> ricochet.owner == entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case ATTACK_REDIRECT -> redirect(level, instance, entity);
            case FORCED_VECTOR -> forcedVector(level, instance, entity);
            case RICOCHET_SHOT -> armRicochet(level, instance, entity);
            case TRAJECTORY_CHANGE -> mark(level, instance, entity);
        }
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }


    public static boolean isRedirecting(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(REDIRECT_TAG) > entity.level().getGameTime();
    }

    private static void redirect(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, ATTACK_REDIRECT)) {
            return;
        }

        int seconds = instance.isMastered(entity) ? CONFIG.redirectSecondsMastered : CONFIG.redirectSeconds;
        instance.getOrCreateTag().putLong(REDIRECT_TAG, level.getGameTime() + seconds * 20L);
        instance.markDirty();
        instance.setCoolDown(CONFIG.redirectCooldown, ATTACK_REDIRECT);
        instance.addMasteryPoint(entity);
        if (!REDIRECTING.contains(entity)) {
            REDIRECTING.add(entity);
        }

        playSound(level, entity, TensuraSoundEvents.DEFENCE_ACTIVATE.get(), 1.0F, 1.2F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.traject.redirecting", seconds), true);
        }
    }

    private static boolean isProjectileDamage(DamageSource source) {
        return source.getDirectEntity() instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        return !isRedirecting(instance, entity) || source.getEntity() == entity || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isProjectileDamage(source);
    }

    @Override
    public void onProjectileHit(ManasSkillInstance instance, LivingEntity entity, EntityHitResult hitResult, Projectile projectile,
                                Changeable<ProjectileDeflection> deflection, Changeable<EntityEvents.ProjectileHitResult> result) {
        if (!(entity.level() instanceof ServerLevel level) || projectile.getOwner() == entity) {
            return;
        }

        if (isRedirecting(instance, entity)) {
            Entity attacker = projectile.getOwner();
            projectile.deflect((deflected, hit, random) -> aimAt(deflected, attacker, random), entity, entity, entity instanceof Player);
            playSound(level, entity, TensuraSoundEvents.REFLECTION.get(), 1.0F, 1.0F);
        } else if (instance.isToggled()) {
            projectile.deflect((deflected, hit, random) -> shoveOff(deflected, entity, random), entity, entity, entity instanceof Player);
            playSound(level, entity, SoundEvents.SHIELD_BLOCK, 0.8F, 1.4F);
        } else {
            return;
        }

        result.set(EntityEvents.ProjectileHitResult.PASS);
        Vec3 pos = projectile.position();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 8, 0.2D, 0.2D, 0.2D, 0.2D);
        instance.addMasteryPoint(entity);
    }

    private static void aimAt(Projectile projectile, @Nullable Entity attacker, RandomSource random) {
        if (attacker == null || !attacker.isAlive() || attacker.level() != projectile.level()) {
            ProjectileDeflection.REVERSE.deflect(projectile, null, random);
            return;
        }

        aimAt(projectile, attacker, Math.max(MIN_REDIRECT_SPEED, projectile.getDeltaMovement().length()));
    }

    private static void aimAt(Projectile projectile, Entity target, double speed) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(projectile.position());
        double ticks = to.length() / speed;
        projectile.shoot(to.x, to.y + 0.5D * projectile.getGravity() * ticks * ticks, to.z, (float) speed, 0.0F);
        projectile.hasImpulse = true;
    }

    public static void shoveOff(Projectile projectile, LivingEntity entity, RandomSource random) {
        double speed = Math.max(MIN_REDIRECT_SPEED, projectile.getDeltaMovement().length());
        Vec3 away = projectile.position().subtract(entity.getBoundingBox().getCenter());
        Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D + 0.2D, random.nextGaussian());
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = away;
        }

        direction = direction.normalize();
        if (away.lengthSqr() > 1.0E-6D && direction.dot(away) < 0.0D) {
            Vec3 normal = away.normalize();
            direction = direction.subtract(normal.scale(2.0D * direction.dot(normal)));
        }

        projectile.setDeltaMovement(direction.scale(speed));
        projectile.hasImpulse = true;
    }

    private static boolean tickRedirecting(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)
                || getTraject(entity).filter(instance -> isRedirecting(instance, entity)).isEmpty()) {
            return true;
        }

        if (level.getGameTime() % 4L == 0L) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 2,
                    entity.getBbWidth() * 0.6D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.6D, 0.0D);
        }
        return false;
    }


    private static void forcedVector(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.forcedVectorRange);
        if (target == null) {
            fail(level, entity, "tracadamia.skill.traject.no_target");
            return;
        }

        if (target.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
            fail(level, entity, "tracadamia.skill.traject.cant_move");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, FORCED_VECTOR)) {
            return;
        }

        Vec3 look = entity.getLookAngle();
        Vec3 velocity = look.scale(CONFIG.forcedVectorSpeed);
        if (target.onGround() && velocity.y < HURL_LIFT) {
            velocity = new Vec3(velocity.x, HURL_LIFT, velocity.z);
        }

        Changeable<Vec3> motion = Changeable.of(velocity);
        if (TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(target, entity, instance, motion).isFalse()) {
            fail(level, entity, "tracadamia.skill.traject.cant_move");
            return;
        }

        if (target.isPassenger()) {
            target.stopRiding();
        }

        target.setDeltaMovement(motion.get());
        target.setOnGround(false);
        target.hasImpulse = true;
        target.hurtMarked = true;
        target.resetFallDistance();
        HURLED.removeIf(hurled -> hurled.target == target);
        HURLED.add(new Hurled(instance, entity, target, level.getGameTime(), motion.get().length()));

        level.sendParticles(ParticleTypes.GUST, target.getX(), target.getY(0.5D), target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        playSound(level, target, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0F, 0.7F);
        instance.setCoolDown(CONFIG.forcedVectorCooldown, FORCED_VECTOR);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickHurled(Hurled hurled) {
        LivingEntity target = hurled.target;
        if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level)) {
            return true;
        }

        long age = level.getGameTime() - hurled.start;
        Vec3 pos = target.position();
        double moved = pos.distanceTo(hurled.lastPos);
        Vec3 heading = pos.subtract(hurled.lastPos);
        hurled.lastPos = pos;
        if (age > HURL_TAKEOFF) {
            hurled.speed = Math.max(moved, hurled.speed * 0.9D);
        }

        boolean hitWall = target.horizontalCollision || target.verticalCollision && age > HURL_TAKEOFF;
        if (hitWall) {
            impact(level, hurled, heading);
            return true;
        }

        if (age > HURL_TICKS || age > HURL_TAKEOFF && moved < HURL_STOPPED) {
            return true;
        }

        if (age % 2L == 0L) {
            level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.5D), target.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
        return false;
    }

    private static void impact(ServerLevel level, Hurled hurled, Vec3 heading) {
        LivingEntity target = hurled.target;
        BlockPos pos = getStruckBlock(level, target, heading);
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            hardness = (float) CONFIG.forcedVectorUnbreakableHardness;
        }

        float damage = (float) (hardness * hurled.speed * 20.0D * CONFIG.forcedVectorDamageScale);
        if (damage > 0.0F) {
            DamageSource source = ((Skill) hurled.instance.getSkill()).createSource(hurled.instance, hurled.owner, DamageTypes.FLY_INTO_WALL, FORCED_VECTOR);
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        }

        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), target.getX(), target.getY(0.5D), target.getZ(), 24, 0.4D, 0.4D, 0.4D, 0.15D);
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.4F, 0.6F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    private static BlockPos getStruckBlock(ServerLevel level, LivingEntity target, Vec3 heading) {
        Vec3 center = target.getBoundingBox().getCenter();
        if (heading.lengthSqr() > 1.0E-6D) {
            double reach = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.5D + 0.6D;
            BlockHitResult hit = level.clip(new ClipContext(center, center.add(heading.normalize().scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit.getBlockPos();
            }
        }

        BlockPos below = target.getOnPos();
        return target.verticalCollision && !level.getBlockState(below).isAir() ? below : BlockPos.containing(center);
    }


    private static void armRicochet(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, RICOCHET_SHOT)) {
            return;
        }

        instance.getOrCreateTag().putBoolean(RICOCHET_TAG, true);
        instance.markDirty();
        instance.setCoolDown(CONFIG.ricochetCooldown, RICOCHET_SHOT);
        instance.addMasteryPoint(entity);
        playSound(level, entity, TensuraSoundEvents.GENERIC_CAST.get(), 1.0F, 1.6F);
        message(entity, Component.translatable("tracadamia.skill.traject.ricochet_ready"));
    }

    private static boolean isRicochetArmed(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getBoolean(RICOCHET_TAG);
    }

    private static @Nullable Ricochet getRicochet(Projectile projectile) {
        for (Ricochet ricochet : RICOCHETS) {
            if (ricochet.projectile == projectile) {
                return ricochet;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Ricochet ricochet = RICOCHETS.isEmpty() ? null : getRicochet(event.getProjectile());
        if (ricochet == null || event.getProjectile().level().isClientSide) {
            return;
        }

        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            ricochet.struck = null;
            return;
        }

        Entity entity = hit.getEntity();
        if (entity == ricochet.owner || ricochet.hit.contains(entity.getId())) {
            event.setCanceled(true);
            return;
        }

        if (entity instanceof LivingEntity living) {
            ricochet.hit.add(living.getId());
            ricochet.struck = living;
            ricochet.snapshot = event.getProjectile().saveWithoutId(new CompoundTag());
        }
    }

    private static @Nullable LivingEntity findNext(ServerLevel level, Ricochet ricochet, LivingEntity from) {
        Vec3 center = from.getBoundingBox().getCenter();
        return level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(ricochet.range),
                        target -> target != ricochet.owner && target.isAlive() && !target.isSpectator() && !ricochet.hit.contains(target.getId())
                                && HitRules.canHit(ricochet.owner, target) && target.getBoundingBox().getCenter().distanceTo(center) <= ricochet.range)
                .stream()
                .filter(target -> level.clip(new ClipContext(center, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS)
                .min(Comparator.comparingDouble(target -> target.distanceToSqr(from)))
                .orElse(null);
    }

    private static boolean tickRicochet(Ricochet ricochet) {
        if (!(ricochet.projectile.level() instanceof ServerLevel level) || level.getGameTime() - ricochet.start > RICOCHET_TICKS) {
            return true;
        }

        LivingEntity struck = ricochet.struck;
        if (struck == null) {
            return ricochet.projectile.isRemoved();
        }

        ricochet.struck = null;
        LivingEntity next = findNext(level, ricochet, struck);
        if (next == null) {
            return true;
        }

        Projectile projectile = ricochet.projectile;
        if (projectile.isRemoved()) {
            projectile = copy(level, projectile, ricochet.snapshot);
            if (projectile == null) {
                return true;
            }
            ricochet.projectile = projectile;
        }

        Vec3 from = struck.getBoundingBox().getCenter();
        Vec3 toward = next.getBoundingBox().getCenter().subtract(from).normalize();
        Vec3 start = from.add(toward.scale(struck.getBbWidth() * 0.5D + 0.4D));
        projectile.moveTo(start.x, start.y, start.z, projectile.getYRot(), projectile.getXRot());
        aimAt(projectile, next, ricochet.speed);

        if (!projectile.isAddedToLevel()) {
            level.addFreshEntity(projectile);
        }

        level.sendParticles(ParticleTypes.CRIT, start.x, start.y, start.z, 8, 0.1D, 0.1D, 0.1D, 0.3D);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ARROW_HIT, SoundSource.PLAYERS, 0.8F, 1.6F);
        return false;
    }

    private static @Nullable Projectile copy(ServerLevel level, Projectile original, @Nullable CompoundTag snapshot) {
        if (snapshot == null || original instanceof ThrownTrident || !(original.getType().create(level) instanceof Projectile copy)) {
            return null;
        }

        copy.load(snapshot);
        copy.setUUID(Mth.createInsecureUUID(level.random));
        copy.getPersistentData().putBoolean(RICOCHET_PROJECTILE_TAG, true);
        if (copy instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        return copy;
    }


    private static @Nullable Mark getMark(LivingEntity owner) {
        for (Mark mark : MARKS) {
            if (mark.owner() == owner) {
                return mark;
            }
        }
        return null;
    }

    private static void mark(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.markRange);
        Mark current = getMark(entity);
        if (current != null && (target == null || target == current.target())) {
            MARKS.remove(current);
            message(entity, Component.translatable("tracadamia.skill.traject.mark_cleared").withStyle(ChatFormatting.GRAY));
            playSound(level, entity, TensuraSoundEvents.GENERIC_CAST.get(), 0.8F, 0.8F);
            return;
        }

        if (target == null) {
            fail(level, entity, "tracadamia.skill.traject.no_target");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, TRAJECTORY_CHANGE)) {
            return;
        }

        if (current != null) {
            MARKS.remove(current);
        }

        MARKS.add(new Mark(instance, entity, target));
        instance.setCoolDown(CONFIG.markCooldown, TRAJECTORY_CHANGE);
        instance.addMasteryPoint(entity);
        playSound(level, entity, TensuraSoundEvents.GENERIC_CAST.get(), 1.0F, 1.4F);
        message(entity, Component.translatable("tracadamia.skill.traject.marked", target.getDisplayName()));
    }

    private static boolean tickMark(Mark mark) {
        LivingEntity owner = mark.owner();
        LivingEntity target = mark.target();
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        if (!target.isAlive() || target.isRemoved() || target.level() != level || owner.distanceTo(target) > CONFIG.markRange) {
            message(owner, Component.translatable("tracadamia.skill.traject.mark_lost", target.getDisplayName()).withStyle(ChatFormatting.GRAY));
            return true;
        }

        if (owner instanceof ServerPlayer player && level.getGameTime() % MARKER_TICKS == 0L) {
            level.sendParticles(player, MARKER, true, target.getX(), target.getY() + target.getBbHeight() + 0.4D, target.getZ(), 4,
                    target.getBbWidth() * 0.3D, 0.05D, target.getBbWidth() * 0.3D, 0.0D);
        }
        return false;
    }

    private static boolean canHome(Projectile projectile) {
        return !(projectile instanceof TensuraProjectile) && !(projectile instanceof FishingHook)
                && !(projectile instanceof FireworkRocketEntity firework && !firework.isShotAtAngle());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)
                || projectile.getPersistentData().getBoolean(RICOCHET_PROJECTILE_TAG) || !(projectile.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        ManasSkillInstance traject = getTraject(shooter).filter(TrajectQuirk::isRicochetArmed).orElse(null);
        if (traject != null && !(projectile instanceof FishingHook)) {
            traject.getOrCreateTag().remove(RICOCHET_TAG);
            traject.markDirty();
            projectile.getPersistentData().putBoolean(RICOCHET_PROJECTILE_TAG, true);
            double range = traject.isMastered(shooter) ? CONFIG.ricochetRangeMastered : CONFIG.ricochetRange;
            RICOCHETS.add(new Ricochet(projectile, shooter, range, event.getLevel().getGameTime()));
            return;
        }

        Mark mark = MARKS.isEmpty() || !canHome(projectile) ? null : getMark(shooter);
        if (mark != null) {
            HOMING.add(new Homing(projectile, mark.owner()));
        }
    }

    private static boolean tickHoming(Homing homing) {
        Projectile projectile = homing.projectile;
        Mark mark = getMark(homing.owner);
        if (projectile.isRemoved() || mark == null || projectile.level() != mark.target().level() || homing.speed < 1.0E-3D) {
            return true;
        }

        Vec3 pos = projectile.position();
        Vec3 velocity = projectile.getDeltaMovement();
        if (projectile.tickCount > 1 && (pos.distanceToSqr(homing.lastPos) < STUCK * STUCK || velocity.length() < homing.speed * BOUNCED)) {
            return true;
        }

        homing.lastPos = pos;
        Vec3 to = mark.target().getBoundingBox().getCenter().subtract(pos);
        if (to.lengthSqr() < 1.0E-4D) {
            return false;
        }

        Vec3 toward = to.normalize();
        Vec3 heading = velocity.lengthSqr() < 1.0E-6D ? toward : velocity.normalize();
        Vec3 direction = to.length() < homing.speed * SNAP_TICKS ? toward : heading.scale(1.0D - TURN).add(toward.scale(TURN)).normalize();
        projectile.setDeltaMovement(direction.scale(homing.speed));
        projectile.hasImpulse = true;
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!MARKS.isEmpty()) {
            MARKS.removeIf(TrajectQuirk::tickMark);
        }

        if (!HOMING.isEmpty()) {
            HOMING.removeIf(TrajectQuirk::tickHoming);
        }

        if (!REDIRECTING.isEmpty()) {
            REDIRECTING.removeIf(TrajectQuirk::tickRedirecting);
        }

        if (!HURLED.isEmpty()) {
            HURLED.removeIf(TrajectQuirk::tickHurled);
        }

        if (!RICOCHETS.isEmpty()) {
            RICOCHETS.removeIf(TrajectQuirk::tickRicochet);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MARKS.clear();
        HOMING.clear();
        REDIRECTING.clear();
        HURLED.clear();
        RICOCHETS.clear();
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message, true);
        }
    }

    private static void fail(ServerLevel level, LivingEntity entity, String key) {
        playSound(level, entity, TensuraSoundEvents.GENERIC_CAST_FAIL.get(), 1.0F, 1.0F);
        message(entity, Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    private static void playSound(ServerLevel level, LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

}

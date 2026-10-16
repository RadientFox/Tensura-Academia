package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class BeamsFromHisEyesQuirk extends Skill {
    private static final int BEAM = 0;
    private static final int EVERY_LAST_DROP = 1;
    private static final int MAX_MASTERY = 5_000;
    private static final int BASIC_DURATION_TICKS = 10;
    private static final int ULTIMATE_DURATION_TICKS = 60;
    private static final int ULTIMATE_CHARGE_TICKS = 40;
    private static final int BASIC_COOLDOWN_SECONDS = 5;
    private static final int ULTIMATE_COOLDOWN_SECONDS = 300;
    private static final double BASIC_AURA_COST = 500.0D;
    private static final double BASIC_LENGTH = 100.0D;
    private static final double BASIC_WIDTH = 1.0D;
    private static final double ULTIMATE_LENGTH = 200.0D;
    private static final double ULTIMATE_WIDTH = 15.0D;
    private static final float BASIC_DAMAGE_PER_TICK = 50.0F;
    private static final ResourceLocation ULTIMATE_MOVEMENT_LOCK =
            ResourceLocation.fromNamespaceAndPath("tracadamia", "every_last_drop_movement_lock");
    private static final String TAG_NOT_ENOUGH_AURA = "tracadamia.skill.beams_from_his_eyes.not_enough_aura";
    private static final Map<UUID, ActiveBeam> ACTIVE_BEAMS = new HashMap<>();
    private static final class ActiveBeam {
        private final LivingEntity owner;
        private final ManasSkillInstance instance;
        private final int mode;
        private Vec3 direction;
        private final double length;
        private final double width;
        private final float damagePerTick;
        private final boolean mastered;
        private final boolean locksMovement;
        private final boolean originalNoGravity;
        private final SimpleParticleType particle;
        private final double clashPower;
        private final boolean testBeam;
        private int ticksRemaining;

        private ActiveBeam(LivingEntity owner, ManasSkillInstance instance, int mode, Vec3 direction,
                           double length, double width, float damagePerTick,
                           boolean mastered, boolean locksMovement, boolean originalNoGravity,
                           SimpleParticleType particle, double clashPower, boolean testBeam, int ticksRemaining) {
            this.owner = owner;
            this.instance = instance;
            this.mode = mode;
            this.direction = direction;
            this.length = length;
            this.width = width;
            this.damagePerTick = damagePerTick;
            this.mastered = mastered;
            this.locksMovement = locksMovement;
            this.originalNoGravity = originalNoGravity;
            this.particle = particle;
            this.clashPower = clashPower;
            this.testBeam = testBeam;
            this.ticksRemaining = ticksRemaining;
        }
    }

    public BeamsFromHisEyesQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/beamsfromeyesicon.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= MAX_MASTERY ? 2 : 1;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BEAM -> "beams_from_his_eyes.beam";
            case EVERY_LAST_DROP -> "beams_from_his_eyes.every_last_drop";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BEAM -> BASIC_AURA_COST;
            case EVERY_LAST_DROP -> TensuraStorages.getExistenceFrom(entity).getAura();
            default -> 0.0D;
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode < BEAM || mode > EVERY_LAST_DROP) {
            return;
        }

        if (mode == EVERY_LAST_DROP && !instance.isMastered(player)) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.beams_from_his_eyes.mastery_locked"), true);
            return;
        }
        if (mode >= getModes(instance) || instance.onCoolDown(mode)
                || ACTIVE_BEAMS.containsKey(player.getUUID())) {
            return;
        }

        if (mode == EVERY_LAST_DROP) {
            if (TensuraStorages.getExistenceFrom(player).getAura() <= 0.0D) {
                player.displayClientMessage(Component.translatable(TAG_NOT_ENOUGH_AURA), true);
            }
            return;
        }

        if (TensuraStorages.getExistenceFrom(player).getAura() < BASIC_AURA_COST) {
            player.displayClientMessage(Component.translatable(TAG_NOT_ENOUGH_AURA), true);
            return;
        }
        fireBeam(instance, player, BEAM, null, false);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != EVERY_LAST_DROP) {
            return false;
        }
        if (entity.level().isClientSide) {
            return true;
        }
        if (!(entity instanceof ServerPlayer player) || !instance.isMastered(player)
                || mode >= getModes(instance) || instance.onCoolDown(mode)
                || ACTIVE_BEAMS.containsKey(player.getUUID())) {
            return false;
        }
        if (heldTicks < ULTIMATE_CHARGE_TICKS) {
            return true;
        }
        return fireBeam(instance, player, mode, null, false);
    }

    public static boolean startTestEveryLastDrop(LivingEntity owner, Vec3 direction) {
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(owner)
                .getSkill(QuirkSkills.BEAMS_FROM_HIS_EYES.get()).orElse(null);
        if (instance == null) return false;
        return fireBeam(instance, owner, EVERY_LAST_DROP, direction, true);
    }

    public static boolean isBeamActive(UUID ownerId) {
        return ACTIVE_BEAMS.containsKey(ownerId);
    }

    public static void updateTestBeamAim(UUID ownerId, Vec3 direction) {
        ActiveBeam beam = ACTIVE_BEAMS.get(ownerId);
        if (beam != null && beam.testBeam && direction.lengthSqr() > 1.0E-6D) {
            beam.direction = direction.normalize();
        }
    }

    private static boolean fireBeam(ManasSkillInstance instance, LivingEntity owner, int mode,
                                    @Nullable Vec3 requestedDirection, boolean testBeam) {
        if (mode < BEAM || mode > EVERY_LAST_DROP
                || (mode == EVERY_LAST_DROP && !instance.isMastered(owner))
                || (!testBeam && instance.onCoolDown(mode)) || ACTIVE_BEAMS.containsKey(owner.getUUID())
                || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }

        var existence = TensuraStorages.getExistenceFrom(owner);
        double currentAura = Math.max(0.0D, existence.getAura());
        if (mode == BEAM) {
            if (currentAura < BASIC_AURA_COST) {
                if (owner instanceof Player player)
                    player.displayClientMessage(Component.translatable(TAG_NOT_ENOUGH_AURA), true);
                return false;
            }
            existence.setAura(currentAura - BASIC_AURA_COST);
        } else {
            if (currentAura <= 0.0D) {
                if (owner instanceof Player player)
                    player.displayClientMessage(Component.translatable(TAG_NOT_ENOUGH_AURA), true);
                return false;
            }
            existence.setAura(0.0D);
        }
        existence.markDirty();
        instance.addMasteryPoint(owner);
        boolean mastered = instance.isMastered(owner);

        Vec3 direction = requestedDirection == null ? owner.getLookAngle().normalize()
                : requestedDirection.normalize();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = Vec3.directionFromRotation(owner.getXRot(), owner.getYRot());
        }

        double length = mode == BEAM ? BASIC_LENGTH : ULTIMATE_LENGTH;
        double width = mode == BEAM ? BASIC_WIDTH : ULTIMATE_WIDTH;
        float damagePerTick = mode == BEAM
                ? BASIC_DAMAGE_PER_TICK
                : (float) Math.max(10.0D, currentAura / 10_000.0D);
        int duration = mode == BEAM ? BASIC_DURATION_TICKS : ULTIMATE_DURATION_TICKS;
        SimpleParticleType particle = mode == BEAM ? MHAParticles.BLAZE_ROD_BEAM.get()
                : MHAParticles.BLAZE_ROD_BEAM_WIDE.get();
        boolean locksMovement = mode == EVERY_LAST_DROP;
        boolean originalNoGravity = owner.isNoGravity();
        if (locksMovement) {
            setMovementLocked(owner, true);
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
        }

        ACTIVE_BEAMS.put(owner.getUUID(), new ActiveBeam(owner, instance, mode, direction,
                length, width, damagePerTick, mastered, locksMovement, originalNoGravity,
                particle, mode == EVERY_LAST_DROP ? currentAura : 0.0D, testBeam, duration));
        if (mode == EVERY_LAST_DROP) {
            Vec3 origin = owner.getEyePosition();
            Vec3 baseEnd = origin.add(direction.scale(length));
            BlockHitResult hit = level.clip(new ClipContext(origin, baseEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 visibleEnd = hit.getType() == BlockHitResult.Type.MISS ? baseEnd : hit.getLocation();
            visibleEnd = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, visibleEnd, width, owner);
            BeamClashManager.publish(owner, instance, BeamClashManager.BeamType.EVERY_LAST_DROP,
                    mode, origin, visibleEnd, width, currentAura);
        }
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.3F, mode == BEAM ? 0.8F : 0.6F);
        if (!testBeam) instance.setCoolDown(mode == BEAM ? BASIC_COOLDOWN_SECONDS : ULTIMATE_COOLDOWN_SECONDS, mode);
        return true;
    }

    @SubscribeEvent
    public static void tickBeams(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ActiveBeam>> iterator = ACTIVE_BEAMS.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveBeam beam = iterator.next().getValue();
            LivingEntity owner = beam.owner;
            if (!owner.isAlive() || owner.isRemoved()
                    || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.BEAMS_FROM_HIS_EYES.get()).orElse(null) != beam.instance) {
                clearBeamMovementLock(beam);
                BeamClashManager.onBeamStopped(owner.getUUID());
                iterator.remove();
                continue;
            }

            BeamClashManager.Control control = BeamClashManager.getControl(owner.getUUID());
            if (control != null && (control.phase() == BeamClashManager.Phase.FIZZLED
                    || (control.phase() == BeamClashManager.Phase.WINNER && control.isLoser(owner.getUUID())))) {
                clearBeamMovementLock(beam);
                iterator.remove();
                continue;
            }

            ServerLevel level = (ServerLevel) owner.level();
            Vec3 origin = owner.getEyePosition();
            Vec3 baseEnd = origin.add(beam.direction.scale(beam.length));
            BlockHitResult baseBlockHit = level.clip(new ClipContext(origin, baseEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 baseVisibleEnd = baseBlockHit.getType() == BlockHitResult.Type.MISS
                    ? baseEnd : baseBlockHit.getLocation();
            baseVisibleEnd = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, baseVisibleEnd, beam.width, owner);
            if (beam.mode == EVERY_LAST_DROP) {
                BeamClashManager.publish(owner, beam.instance, BeamClashManager.BeamType.EVERY_LAST_DROP,
                        beam.mode, origin, baseVisibleEnd, beam.width, beam.clashPower);
                control = BeamClashManager.getControl(owner.getUUID());
                if (control != null && (control.phase() == BeamClashManager.Phase.FIZZLED
                        || (control.phase() == BeamClashManager.Phase.WINNER && control.isLoser(owner.getUUID())))) {
                    clearBeamMovementLock(beam);
                    iterator.remove();
                    continue;
                }
            }

            boolean pushing = control != null && control.phase() == BeamClashManager.Phase.PUSHING;
            boolean overpowering = control != null && control.phase() == BeamClashManager.Phase.WINNER
                    && control.isWinner(owner.getUUID());
            if (beam.locksMovement) {
                setMovementLocked(owner, !pushing);
                if (!pushing) {
                    owner.setDeltaMovement(Vec3.ZERO);
                    owner.hurtMarked = true;
                }
            }

            Vec3 direction = beam.direction;
            Vec3 requestedEnd = baseEnd;
            LivingEntity clashTarget = null;
            if (pushing) {
                requestedEnd = control.clashPoint();
                direction = requestedEnd.subtract(origin).normalize();
            } else if (overpowering) {
                Entity target = level.getEntity(control.loser());
                clashTarget = target instanceof LivingEntity living ? living : null;
                if (clashTarget == null || !clashTarget.isAlive() || clashTarget.level() != level) {
                    BeamClashManager.onBeamStopped(owner.getUUID());
                    control = null;
                    overpowering = false;
                } else {
                    requestedEnd = clashTarget.getEyePosition();
                    direction = requestedEnd.subtract(origin).normalize();
                }
            }
            if (direction.lengthSqr() < 1.0E-6D) direction = beam.direction;
            BlockHitResult blockHit = pushing || requestedEnd.equals(baseEnd) ? baseBlockHit
                    : level.clip(new ClipContext(origin, requestedEnd,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 end = pushing ? control.clashPoint()
                    : blockHit.getType() == BlockHitResult.Type.MISS ? requestedEnd : blockHit.getLocation();
            double sizeMultiplier = overpowering ? control.sizeMultiplier() : 1.0D;
            double effectiveWidth = beam.width * sizeMultiplier;
            end = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, end, effectiveWidth, owner,
                    pushing ? null : beamSource(owner, beam), beam.damagePerTick * (overpowering ? 5.0F : 1.0F));
            double beamLength = origin.distanceTo(end);
            WhirlwindQuirk.igniteBeam(level, origin, direction, beamLength, effectiveWidth);
            Vec3 right = BeamGeometry.perpendicular(direction);
            Vec3 up = right.cross(direction).normalize();
            AABB candidates = new AABB(origin, end).inflate(effectiveWidth * 0.5D + 1.0D);
            boolean clashTargetHit = false;

            if (!pushing) {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, candidates,
                        candidate -> candidate != owner && !(candidate instanceof com.radient.tensuraacadamia.entity.MoltenShield)
                                && candidate.isAlive() && !candidate.isRemoved())) {
                    boolean lockedClashTarget = overpowering && target == clashTarget;
                    if (!lockedClashTarget && !BeamGeometry.intersects(target.getBoundingBox(), origin,
                            direction, right, up, beamLength, effectiveWidth, effectiveWidth)) {
                        continue;
                    }
                    damageTarget(owner, beam, target, overpowering ? 5.0F : 1.0F);
                    if (lockedClashTarget) clashTargetHit = true;
                }
                if (overpowering && !clashTargetHit && clashTarget != null
                        && blockHit.getType() == BlockHitResult.Type.MISS && end.equals(requestedEnd)) {
                    damageTarget(owner, beam, clashTarget, 5.0F);
                }
            }

            if (owner.tickCount % 2 == 0) {
                double renderLength = overpowering || beam.mode == EVERY_LAST_DROP ? 240.0D : beam.length;
                double particleRange = beamLength / renderLength;
                SimpleParticleType particle = overpowering
                        ? MHAParticles.BLAZE_ROD_BEAM_OVERPOWER.get() : beam.particle;
                level.sendParticles(particle, origin.x, origin.y, origin.z, 0,
                        direction.x * particleRange, direction.y * particleRange,
                        direction.z * particleRange, 1.0D);
            }

            if (!(pushing && beam.mode == EVERY_LAST_DROP) && --beam.ticksRemaining <= 0) {
                clearBeamMovementLock(beam);
                BeamClashManager.onBeamStopped(owner.getUUID());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActiveBeam beam = ACTIVE_BEAMS.remove(event.getEntity().getUUID());
        BeamClashManager.onBeamStopped(event.getEntity().getUUID());
        if (beam != null) {
            clearBeamMovementLock(beam);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_BEAMS.values().forEach(BeamsFromHisEyesQuirk::clearBeamMovementLock);
        ACTIVE_BEAMS.clear();
    }

    private static void setMovementLocked(LivingEntity player, boolean locked) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        if (locked) {
            movement.addOrUpdateTransientModifier(new AttributeModifier(ULTIMATE_MOVEMENT_LOCK,
                    -10.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            movement.removeModifier(ULTIMATE_MOVEMENT_LOCK);
        }
    }

    private static void clearBeamMovementLock(ActiveBeam beam) {
        if (!beam.locksMovement) {
            return;
        }
        setMovementLocked(beam.owner, false);
        beam.owner.setNoGravity(beam.originalNoGravity);
    }

    private static void damageTarget(LivingEntity owner, ActiveBeam beam, LivingEntity target,
                                     float damageMultiplier) {
        target.invulnerableTime = 0;
        target.hurt(beamSource(owner, beam), beam.damagePerTick * damageMultiplier);
    }

    private static DamageSource beamSource(LivingEntity owner, ActiveBeam beam) {
        DamageSource source = owner.damageSources().source(TensuraDamageTypes.HEAT_WAVE, owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);
        tensuraSource.tensura$setAbilityInstance(beam.instance);
        tensuraSource.tensura$setAbilityMode(beam.mode);
        tensuraSource.tensura$setElement(Element.FLAME);
        if (beam.mastered) {
            tensuraSource.tensura$setResistanceBypassLevel(1.0F);
        }
        return source;
    }

}

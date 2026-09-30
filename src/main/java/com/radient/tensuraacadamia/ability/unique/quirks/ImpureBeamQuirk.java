package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.extra.SpatialDominationSkill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class ImpureBeamQuirk extends Skill {
    private static final int BEAM = 0;
    private static final int FULL_POWER = 1;
    private static final int MAX_MASTERY = 5_000;
    private static final double MAX_BEAM_AP = 2_000_000.0D;
    private static final double MAX_RENDERED_BEAM_RANGE = 240.0D;
    private static final double BEAM_HITBOX = 1.05D;
    private static final double NORMAL_AP_PER_SECOND = 10_000.0D;
    private static final double LIGHT_DAMAGE_PER_TICK = 10.0D;
    private static final double HOLY_DAMAGE_PER_TICK = 5.0D;
    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_BEAM_CHARGES = 10;
    private static final String NOT_ENOUGH_AP = "tracadamia.skill.impure_beam.not_enough_ap";
    private static final Map<UUID, ActiveBeam> ACTIVE_BEAMS = new HashMap<>();
    private static final Map<UUID, BeamCharge> BEAM_CHARGES = new HashMap<>();

    private static final class BeamCharge {
        private final ManasSkillInstance instance;
        private int charges;
        private int paidTicks;

        private BeamCharge(ManasSkillInstance instance) {
            this.instance = instance;
        }
    }

    private static final class ActiveBeam {
        private final ServerPlayer owner;
        private final ManasSkillInstance instance;
        private final int mode;
        private final double apCostPerTick;
        private final double damageMultiplier;
        private final boolean resistanceBypass;
        private final double clashPower;
        private int ticksRemaining;

        private ActiveBeam(ServerPlayer owner, ManasSkillInstance instance, int mode,
                           double apCostPerTick, double damageMultiplier,
                           boolean resistanceBypass, double clashPower, int ticksRemaining) {
            this.owner = owner;
            this.instance = instance;
            this.mode = mode;
            this.apCostPerTick = apCostPerTick;
            this.damageMultiplier = damageMultiplier;
            this.resistanceBypass = resistanceBypass;
            this.clashPower = clashPower;
            this.ticksRemaining = ticksRemaining;
        }
    }

    public ImpureBeamQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/impurebeamicon.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 45_000.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case BEAM -> "impure_beam.beam";
            case FULL_POWER -> "impure_beam.full_power";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return 0.0D;
    }

    @Override
    public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner,
                                                       int keyNumber, int mode) {
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode < BEAM || mode > FULL_POWER
                || ACTIVE_BEAMS.containsKey(player.getUUID())) {
            return;
        }

        if (mode == BEAM) {
            double currentAp = Math.max(0.0D, TensuraStorages.getExistenceFrom(player).getAura());
            if (currentAp < NORMAL_AP_PER_SECOND / TICKS_PER_SECOND) {
                player.displayClientMessage(Component.translatable(NOT_ENOUGH_AP), true);
                return;
            }
            BEAM_CHARGES.put(player.getUUID(), new BeamCharge(instance));
            return;
        }

        startBeam(instance, player, mode, 1);
    }

    private static boolean startBeam(ManasSkillInstance instance, ServerPlayer player, int mode, int chargeCount) {
        double maxAp = Math.max(0.0D, EnergyHelper.getMaxAura(player));
        double currentAp = Math.max(0.0D, TensuraStorages.getExistenceFrom(player).getAura());
        double apCostPerTick = mode == BEAM
                ? 0.0D
                : Math.max(1.0D, maxAp * 0.05D) / TICKS_PER_SECOND;
        double apRequiredToStart = mode == BEAM ? 0.0D : apCostPerTick;
        if (currentAp < apRequiredToStart) {
            player.displayClientMessage(Component.translatable(NOT_ENOUGH_AP), true);
            return false;
        }

        boolean combinedWithOtherQuirks = hasOtherQuirk(player);
        double damageMultiplier = mode == FULL_POWER
                ? 1.0D + 0.5D * Math.min(20.0D, Math.floor(maxAp / 100_000.0D))
                : 1.0D;
        if (mode == BEAM) {
            damageMultiplier *= Math.max(1, Math.min(MAX_BEAM_CHARGES, chargeCount));
        }
        if (combinedWithOtherQuirks) {
            damageMultiplier *= 2.0D;
            applyRecoil(instance, player, mode == FULL_POWER ? 0.50D : 0.25D);
            if (!player.isAlive()) return false;
        }

        boolean fullPowerAtCap = mode == FULL_POWER && maxAp >= MAX_BEAM_AP;
        ACTIVE_BEAMS.put(player.getUUID(), new ActiveBeam(player, instance, mode,
                apCostPerTick, damageMultiplier, combinedWithOtherQuirks || fullPowerAtCap,
                currentAp, mode == BEAM ? TICKS_PER_SECOND : -1));
        if (mode == FULL_POWER && player.level() instanceof ServerLevel level) {
            Vec3 direction = player.getLookAngle().normalize();
            Vec3 origin = beamOrigin(player);
            Vec3 baseEnd = origin.add(direction.scale(Math.max(1.0D, SpatialDominationSkill.CONFIG.rayRange * 2.0D)));
            BlockHitResult hit = level.clip(new ClipContext(origin, baseEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 visibleEnd = hit.getType() == BlockHitResult.Type.MISS ? baseEnd : hit.getLocation();
            visibleEnd = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, visibleEnd, BEAM_HITBOX, player);
            BeamClashManager.publish(player, instance, BeamClashManager.BeamType.IMPURE_FULL_POWER,
                    mode, origin, visibleEnd, BEAM_HITBOX, currentAp);
        }
        if (mode == BEAM) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.0F, 1.3F);
        }
        return true;
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode < BEAM || mode > FULL_POWER) return false;
        if (entity.level().isClientSide) return true;
        if (!(entity instanceof ServerPlayer player)) return false;
        if (mode == BEAM) {
            BeamCharge charge = BEAM_CHARGES.get(player.getUUID());
            if (charge == null || charge.instance != instance) return false;
            boolean fullyProcessed = processChargeTicks(player, charge, heldTicks);
            if (!fullyProcessed || charge.charges >= MAX_BEAM_CHARGES) {
                BEAM_CHARGES.remove(player.getUUID());
                if (charge.charges > 0) {
                    startBeam(instance, player, BEAM, charge.charges);
                }
                return false;
            }
            return true;
        }
        ActiveBeam beam = ACTIVE_BEAMS.get(player.getUUID());
        return beam != null && beam.instance == instance && beam.mode == mode;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == BEAM && entity instanceof ServerPlayer player) {
            BeamCharge charge = BEAM_CHARGES.remove(player.getUUID());
            if (charge == null || charge.instance != instance) return;
            processChargeTicks(player, charge, heldTicks);
            if (charge.charges > 0) startBeam(instance, player, BEAM, charge.charges);
            return;
        }
        BeamClashManager.onBeamStopped(entity.getUUID());
        ACTIVE_BEAMS.computeIfPresent(entity.getUUID(), (uuid, beam) ->
                beam.instance == instance ? null : beam);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        BeamClashManager.onBeamStopped(entity.getUUID());
        BeamCharge charge = BEAM_CHARGES.get(entity.getUUID());
        if (charge != null && charge.instance == instance) BEAM_CHARGES.remove(entity.getUUID());
        ACTIVE_BEAMS.computeIfPresent(entity.getUUID(), (uuid, beam) -> beam.instance == instance ? null : beam);
        super.onForgetSkill(instance, entity);
    }

    private static boolean processChargeTicks(ServerPlayer player, BeamCharge charge, int heldTicks) {
        int targetTicks = Math.min(MAX_BEAM_CHARGES * TICKS_PER_SECOND, Math.max(0, heldTicks));
        int ticksToPay = Math.max(0, targetTicks - charge.paidTicks);
        if (ticksToPay == 0) return true;

        var existence = TensuraStorages.getExistenceFrom(player);
        double currentAp = Math.max(0.0D, existence.getAura());
        int affordableTicks = (int) Math.floor((currentAp + 1.0E-6D)
                / (NORMAL_AP_PER_SECOND / TICKS_PER_SECOND));
        int paidTicks = Math.min(ticksToPay, affordableTicks);
        if (paidTicks > 0) {
            existence.setAura(Math.max(0.0D, currentAp
                    - paidTicks * (NORMAL_AP_PER_SECOND / TICKS_PER_SECOND)));
            existence.markDirty();
            charge.paidTicks += paidTicks;
            int charges = charge.paidTicks / TICKS_PER_SECOND;
            if (charges > charge.charges) {
                charge.charges = charges;
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.65F, 0.85F + charges * 0.07F);
                player.displayClientMessage(Component.literal("Impure Beam charge: " + charges + "/"
                        + MAX_BEAM_CHARGES), true);
            }
        }

        if (charge.paidTicks < targetTicks) {
            player.displayClientMessage(Component.translatable(NOT_ENOUGH_AP), true);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void tickBeams(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ActiveBeam>> iterator = ACTIVE_BEAMS.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveBeam beam = iterator.next().getValue();
            ServerPlayer owner = beam.owner;
            if (!owner.isAlive() || owner.isRemoved()
                    || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.IMPURE_BEAM.get()).orElse(null) != beam.instance) {
                BeamClashManager.onBeamStopped(owner.getUUID());
                iterator.remove();
                continue;
            }

            BeamClashManager.Control control = BeamClashManager.getControl(owner.getUUID());
            if (control != null && (control.phase() == BeamClashManager.Phase.FIZZLED
                    || (control.phase() == BeamClashManager.Phase.WINNER && control.isLoser(owner.getUUID())))) {
                iterator.remove();
                continue;
            }
            boolean pushing = control != null && control.phase() == BeamClashManager.Phase.PUSHING;
            boolean overpowering = control != null && control.phase() == BeamClashManager.Phase.WINNER
                    && control.isWinner(owner.getUUID());

            if (beam.apCostPerTick > 0.0D) {
                var existence = TensuraStorages.getExistenceFrom(owner);
                double currentAp = Math.max(0.0D, existence.getAura());
                if (currentAp < beam.apCostPerTick) {
                    owner.displayClientMessage(Component.translatable(NOT_ENOUGH_AP), true);
                    BeamClashManager.onBeamStopped(owner.getUUID());
                    iterator.remove();
                    continue;
                }
                existence.setAura(currentAp - beam.apCostPerTick);
                existence.markDirty();
            }

            ServerLevel level = owner.serverLevel();
            double beamRange = Math.max(1.0D, SpatialDominationSkill.CONFIG.rayRange * 2.0D);
            Vec3 direction = owner.getLookAngle().normalize();
            if (direction.lengthSqr() < 1.0E-6D) {
                direction = Vec3.directionFromRotation(0.0F, owner.getYRot());
            }
            Vec3 origin = beamOrigin(owner);
            Vec3 baseEnd = origin.add(direction.scale(beamRange));
            BlockHitResult baseBlockHit = level.clip(new ClipContext(origin, baseEnd,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 baseVisibleEnd = baseBlockHit.getType() == BlockHitResult.Type.MISS
                    ? baseEnd : baseBlockHit.getLocation();
            baseVisibleEnd = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, baseVisibleEnd, BEAM_HITBOX, owner);
            if (beam.mode == FULL_POWER) {
                BeamClashManager.publish(owner, beam.instance, BeamClashManager.BeamType.IMPURE_FULL_POWER,
                        beam.mode, origin, baseVisibleEnd, BEAM_HITBOX, beam.clashPower);
                control = BeamClashManager.getControl(owner.getUUID());
                if (control != null && (control.phase() == BeamClashManager.Phase.FIZZLED
                        || (control.phase() == BeamClashManager.Phase.WINNER && control.isLoser(owner.getUUID())))) {
                    iterator.remove();
                    continue;
                }
                pushing = control != null && control.phase() == BeamClashManager.Phase.PUSHING;
                overpowering = control != null && control.phase() == BeamClashManager.Phase.WINNER
                        && control.isWinner(owner.getUUID());
            }

            LivingEntity clashTarget = null;
            Vec3 requestedEnd = baseEnd;
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
            if (direction.lengthSqr() < 1.0E-6D) direction = owner.getLookAngle().normalize();
            BlockHitResult blockHit = pushing || requestedEnd.equals(baseEnd) ? baseBlockHit
                    : level.clip(new ClipContext(origin, requestedEnd,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
            Vec3 end = pushing ? control.clashPoint()
                    : blockHit.getType() == BlockHitResult.Type.MISS ? requestedEnd : blockHit.getLocation();
            double sizeMultiplier = overpowering ? control.sizeMultiplier() : 1.0D;
            double effectiveHitbox = BEAM_HITBOX * sizeMultiplier;
            double clashDamageMultiplier = beam.damageMultiplier * (overpowering ? 5.0D : 1.0D);
            end = com.radient.tensuraacadamia.entity.MoltenShield.clipBeam(level, origin, end, effectiveHitbox, owner,
                    pushing ? null : beamSource(owner, beam, TensuraDamageTypes.LIGHT_ELEMENTAL, Element.LIGHT),
                    (float) ((LIGHT_DAMAGE_PER_TICK + HOLY_DAMAGE_PER_TICK) * clashDamageMultiplier));
            double visibleLength = origin.distanceTo(end);
            Vec3 right = BeamGeometry.perpendicular(direction);
            Vec3 up = right.cross(direction).normalize();
            AABB candidates = new AABB(origin, end).inflate(effectiveHitbox);
            boolean clashTargetHit = false;

            if (!pushing) {
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, candidates,
                        candidate -> candidate != owner && !(candidate instanceof com.radient.tensuraacadamia.entity.MoltenShield)
                                && candidate.isAlive() && !candidate.isRemoved())) {
                    boolean lockedClashTarget = overpowering && target == clashTarget;
                    if (!lockedClashTarget && !BeamGeometry.intersects(target.getBoundingBox(), origin, direction,
                            right, up, visibleLength, effectiveHitbox, effectiveHitbox)) continue;
                    damageTarget(owner, beam, target, TensuraDamageTypes.LIGHT_ELEMENTAL,
                            Element.LIGHT, LIGHT_DAMAGE_PER_TICK * clashDamageMultiplier);
                    damageTarget(owner, beam, target, TensuraDamageTypes.HOLY_DAMAGE,
                            Element.HOLY, HOLY_DAMAGE_PER_TICK * clashDamageMultiplier);
                    if (lockedClashTarget) clashTargetHit = true;
                }
                if (overpowering && !clashTargetHit && clashTarget != null
                        && blockHit.getType() == BlockHitResult.Type.MISS && end.equals(requestedEnd)) {
                    damageTarget(owner, beam, clashTarget, TensuraDamageTypes.LIGHT_ELEMENTAL,
                            Element.LIGHT, LIGHT_DAMAGE_PER_TICK * clashDamageMultiplier);
                    damageTarget(owner, beam, clashTarget, TensuraDamageTypes.HOLY_DAMAGE,
                            Element.HOLY, HOLY_DAMAGE_PER_TICK * clashDamageMultiplier);
                }
            }

            if (owner.tickCount % 2 == 0) {
                double particleRange = visibleLength / MAX_RENDERED_BEAM_RANGE;
                SimpleParticleType particle = overpowering
                        ? MHAParticles.IMPURE_BEAM_OVERPOWER.get() : MHAParticles.IMPURE_BEAM.get();
                level.sendParticles(particle, origin.x, origin.y, origin.z, 0,
                        direction.x * particleRange, direction.y * particleRange,
                        direction.z * particleRange, 1.0D);
            }

            if (beam.mode == BEAM && --beam.ticksRemaining <= 0) {
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BeamClashManager.onBeamStopped(event.getEntity().getUUID());
        BEAM_CHARGES.remove(event.getEntity().getUUID());
        ACTIVE_BEAMS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_BEAMS.keySet().forEach(BeamClashManager::onBeamStopped);
        ACTIVE_BEAMS.clear();
        BEAM_CHARGES.clear();
    }

    private static boolean hasOtherQuirk(ServerPlayer player) {
        ResourceLocation ownId = QuirkSkills.IMPURE_BEAM.get().getRegistryName();
        return SkillAPI.getSkillsFrom(player).getLearnedSkills().stream()
                .filter(skill -> !skill.isTemporarySkill())
                .anyMatch(skill -> !ownId.equals(skill.getSkillId())
                        && skill.getSkill() instanceof Skill unique
                        && unique.getType() == SkillType.UNIQUE
                        && "tracadamia".equals(skill.getSkillId().getNamespace()));
    }

    private static void applyRecoil(ManasSkillInstance instance, ServerPlayer player, double healthFraction) {
        float recoilDamage = (float) (player.getMaxHealth() * healthFraction);
        DamageSource source = player.serverLevel().damageSources().source(DamageTypes.MAGIC, player);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);
        tensuraSource.tensura$setAbilityInstance(instance);
        tensuraSource.tensura$setResistanceBypassLevel(2.0F);
        player.invulnerableTime = 0;
        player.hurt(source, recoilDamage);
    }

    private static void damageTarget(ServerPlayer owner, ActiveBeam beam, LivingEntity target,
                                     net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> damageType,
                                     Element element, double damage) {
        target.invulnerableTime = 0;
        target.hurt(beamSource(owner, beam, damageType, element), (float) damage);
    }

    private static DamageSource beamSource(ServerPlayer owner, ActiveBeam beam,
                                          net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> damageType,
                                          Element element) {
        DamageSource source = owner.serverLevel().damageSources().source(damageType, owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);
        tensuraSource.tensura$setAbilityInstance(beam.instance);
        tensuraSource.tensura$setAbilityMode(beam.mode);
        tensuraSource.tensura$setElement(element);
        if (beam.resistanceBypass) tensuraSource.tensura$setResistanceBypassLevel(2.0F);
        return source;
    }

    private static Vec3 beamOrigin(LivingEntity owner) {
        return owner.position().add(0.0D, owner.getBbHeight() * 0.58D, 0.0D);
    }

}

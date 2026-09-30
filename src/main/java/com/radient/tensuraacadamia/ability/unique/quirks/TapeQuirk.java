package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.mixin.FallingBlockEntityInvoker;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tape constructs are temporary server-side effects; the source blocks used by Trident are real. */
public final class TapeQuirk extends Skill {
    private static final int MAX_MASTERY = 2500;
    private static final int GRAPPLE_TICKS = 200;
    private static final int BIND_TICKS = 300;
    private static final int SWING_TICKS = 100;
    private static final int BARRICADE_TICKS = 600;
    private static final double GRAPPLE_TAPE_SPEED = 5.2;
    private static final double BIND_TAPE_SPEED = 5.0;
    private static final double BARRICADE_EXTENSION_TICKS = 4.0;
    private static final int BLOCK_STAGE_TICKS = 18;
    private static final int BLOCK_FLIGHT_TICKS = 80;
    private static final String TRIDENT_BLOCK = "TracadamiaTapeTridentBlock";
    private static final List<Grapple> GRAPPLES = new ArrayList<>();
    private static final List<Binding> BINDINGS = new ArrayList<>();
    private static final List<Strand> STRANDS = new ArrayList<>();
    private static final List<FlyingBlock> BLOCKS = new ArrayList<>();

    public TapeQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return MAX_MASTERY; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/tapeicon.png"); }
    @Override public int getModes(ManasSkillInstance instance) {
        double mastery = instance.getMastery();
        return mastery >= 1875 ? 4 : mastery >= 1250 ? 3 : mastery >= 500 ? 2 : 1;
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "tape.bind";
            case 2 -> "tape.barricade";
            case 3 -> "tape.trident";
            default -> "tape.tape";
        };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode >= getModes(instance)) return;
        boolean used = switch (mode) {
            case 0 -> grapple(level, owner, instance);
            case 1 -> bind(level, owner);
            case 2 -> barricade(level, owner, instance);
            case 3 -> trident(level, owner, instance);
            default -> false;
        };
        if (used) {
            instance.addMasteryPoint(owner);
            instance.markDirty();
            level.playSound(null, owner.blockPosition(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    private static boolean grapple(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        Hit hit = aim(level, owner, 40, false);
        if (hit == null) {
            List<Grapple> detached = GRAPPLES.stream().filter(g -> g.owner == owner).toList();
            detached.forEach(Grapple::discard);
            GRAPPLES.removeAll(detached);
            return !detached.isEmpty();
        }
        if (hit.entity instanceof LivingEntity target && instance.getMastery() >= 1000) {
            Binding bound = BINDINGS.stream().filter(b -> b.target == target && b.owner == owner && b.latched && !b.swinging).findFirst().orElse(null);
            if (bound != null) {
                return bound.startSwing(level);
            }
        }
        Vec3 endpoint = hit.entity == null ? hit.position : hit.entity.getBoundingBox().getCenter();
        List<Grapple> owned = GRAPPLES.stream().filter(g -> g.owner == owner).toList();
        if (owned.size() >= 2) {
            Grapple oldest = owned.getFirst();
            oldest.discard();
            GRAPPLES.remove(oldest);
        }
        boolean right = GRAPPLES.stream().anyMatch(g -> g.owner == owner && !g.right);
        Vec3 from = elbow(owner, right);
        TapeRibbon ribbon = TapeRibbon.create(level, from);
        if (ribbon == null) return false;
        GRAPPLES.add(new Grapple(owner, hit.entity, endpoint, ribbon, right,
                Math.max(2, owner.getEyePosition().distanceTo(endpoint) * 0.97), level.getGameTime() + GRAPPLE_TICKS));
        return true;
    }

    private static boolean bind(ServerLevel level, LivingEntity owner) {
        Hit hit = aim(level, owner, 30, true);
        if (hit == null || !(hit.entity instanceof LivingEntity target) || !enemy(owner, target)) return false;
        BINDINGS.removeIf(binding -> {
            if (binding.target != target) return false;
            binding.discard();
            return true;
        });
        Binding binding = new Binding(owner, target, level.getGameTime() + BIND_TICKS);
        if (!binding.start(level)) return false;
        BINDINGS.add(binding);
        return true;
    }

    private static boolean barricade(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        List<Vec3> anchors = new ArrayList<>();
        BlockPos center = owner.blockPosition();
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            if (x * x + z * z > 81) continue;
            BlockPos column = center.offset(x, 0, z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos support = GroundBlocks.findSurface(level, column);
            if (support != null && level.hasChunkAt(support) && !level.getBlockState(support).isAir())
                anchors.add(Vec3.atBottomCenterOf(support.above()).add(0, 0.7, 0));
        }
        if (anchors.size() < 2) return false;
        int made = 0;
        for (int attempt = 0; attempt < 120 && made < 20; attempt++) {
            Vec3 a = anchors.get(level.random.nextInt(anchors.size()));
            Vec3 b = anchors.get(level.random.nextInt(anchors.size()));
            if (a.distanceToSqr(b) < 9 || a.distanceToSqr(b) > 144) continue;
            TapeRibbon ribbon = TapeRibbon.create(level, a);
            if (ribbon == null) continue;
            STRANDS.add(new Strand(level, owner.getUUID(), a, b, level.getGameTime() + BARRICADE_TICKS,
                    instance.getMastery() >= MAX_MASTERY ? 100 : 40, ribbon,
                    level.getGameTime() + made / 2));
            made++;
        }
        return made > 0;
    }

    private static boolean trident(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        Hit hit = aim(level, owner, 40, true);
        if (hit == null || !(hit.entity instanceof LivingEntity target) || !enemy(owner, target)) return false;
        Vec3 targetPoint = target.getBoundingBox().getCenter();
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = owner.blockPosition();
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-7, -3, -7), center.offset(7, 4, 7))) {
            BlockPos pos = scan.immutable();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                    || state.getRenderShape() != RenderShape.MODEL || !state.isCollisionShapeFullBlock(level, pos)
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    || !GroundBlocks.canBreak(level, instance, owner, pos)) continue;
            candidates.add(pos);
        }
        if (candidates.isEmpty()) return false;
        java.util.Collections.shuffle(candidates, new java.util.Random(level.random.nextLong()));
        int fired = 0;
        for (BlockPos pos : candidates) {
            if (fired >= 30) break;
            BlockState state = level.getBlockState(pos);
            FallingBlockEntity block = FallingBlockEntityInvoker.tracadamia$create(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, state);
            block.dropItem = false;
            block.disableDrop();
            block.setNoGravity(true);
            block.getPersistentData().putBoolean(TRIDENT_BLOCK, true);
            TapeRibbon ribbon = TapeRibbon.create(level, elbow(owner, fired % 2 == 1));
            if (ribbon == null) continue;
            FlyingBlock flight = new FlyingBlock(level, owner, block, ribbon, targetPoint,
                    Math.clamp(state.getDestroySpeed(level, pos), 0, 30), level.getGameTime(), fired);
            BLOCKS.add(flight);
            if (!level.addFreshEntity(block)) { BLOCKS.remove(flight); ribbon.discard(); continue; }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            fired++;
        }
        return fired > 0;
    }

    private static Hit aim(ServerLevel level, LivingEntity owner, double range, boolean livingEnemyOnly) {
        Vec3 start = owner.getEyePosition(), end = start.add(owner.getLookAngle().scale(range));
        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 stop = block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(owner, start, stop, new AABB(start, stop).inflate(1),
                candidate -> candidate instanceof LivingEntity living && living != owner && living.isAlive()
                        && (!livingEnemyOnly || enemy(owner, living)),
                start.distanceToSqr(stop));
        if (entity != null) return new Hit(entity.getEntity(), entity.getLocation());
        return block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? new Hit(null, stop) : null;
    }

    private static boolean enemy(LivingEntity owner, LivingEntity target) {
        return target != owner && target.isAlive() && HomingQuirk.isEnemy(owner, target);
    }

    private static Vec3 elbow(LivingEntity owner, boolean right) {
        Vec3 lateral = owner.getLookAngle().cross(new Vec3(0, 1, 0)).normalize();
        if (lateral.lengthSqr() < 0.001) lateral = new Vec3(1, 0, 0);
        return owner.getEyePosition().add(lateral.scale(right ? -0.38 : 0.38)).add(0, -0.7, 0);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        GRAPPLES.removeIf(g -> { if (g.tick()) return false; g.discard(); return true; });
        // A newly fired line may travel while the old line holds the player, but
        // its latch replaces the older attachment rather than tethering both.
        java.util.Set<UUID> attachedOwners = new java.util.HashSet<>();
        for (int i = GRAPPLES.size() - 1; i >= 0; i--) {
            Grapple grapple = GRAPPLES.get(i);
            if (grapple.latched && !attachedOwners.add(grapple.owner.getUUID())) {
                grapple.discard();
                GRAPPLES.remove(i);
            }
        }
        BINDINGS.removeIf(b -> { if (b.tick()) return false; b.discard(); return true; });
        STRANDS.removeIf(s -> { if (s.tick()) return false; s.discard(); return true; });
        BLOCKS.removeIf(b -> { if (b.tick()) return false; b.discard(); return true; });
    }

    @SubscribeEvent public static void blockMelee(AttackEntityEvent event) {
        if (BINDINGS.stream().anyMatch(binding -> binding.latched && binding.target == event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void blockMobMelee(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && BINDINGS.stream().anyMatch(binding -> binding.latched && binding.target == attacker)
                && event.getSource().getDirectEntity() == attacker
                && (event.getSource().is(DamageTypes.PLAYER_ATTACK)
                || event.getSource().is(DamageTypes.MOB_ATTACK)
                || event.getSource().is(DamageTypes.MOB_ATTACK_NO_AGGRO))) event.setCanceled(true);
    }
    @SubscribeEvent public static void clear(ServerStoppingEvent event) {
        GRAPPLES.forEach(Grapple::discard);
        BINDINGS.forEach(Binding::discard);
        STRANDS.forEach(Strand::discard);
        BLOCKS.forEach(FlyingBlock::discard);
        GRAPPLES.clear(); BINDINGS.clear(); STRANDS.clear(); BLOCKS.clear();
        TapeRibbon.clear();
    }
    @SubscribeEvent public static void discardOrphanedBlock(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof FallingBlockEntity block
                && block.getPersistentData().getBoolean(TRIDENT_BLOCK)
                && BLOCKS.stream().noneMatch(flight -> flight.block == block)) block.discard();
        if (event.getEntity() instanceof Display.BlockDisplay display
                && display.getPersistentData().getBoolean(TapeRibbon.TAG)
                && !TapeRibbon.active(display)) display.discard();
    }

    private record Hit(Entity entity, Vec3 position) {}

    private static final class Grapple {
        final ServerLevel level;
        final LivingEntity owner;
        final Entity anchorEntity;
        final Vec3 anchorPoint;
        final TapeRibbon ribbon;
        final boolean right;
        final long end;
        double ropeLength;
        int flightTicks;
        boolean latched;
        Grapple(LivingEntity owner, Entity anchorEntity, Vec3 anchorPoint, TapeRibbon ribbon,
                boolean right, double ropeLength, long end) {
            this.level = (ServerLevel) owner.level();
            this.owner = owner; this.anchorEntity = anchorEntity; this.anchorPoint = anchorPoint;
            this.ribbon = ribbon; this.right = right; this.ropeLength = ropeLength; this.end = end;
        }
        boolean tick() {
            if (!owner.isAlive() || owner.level() != level || level.getGameTime() >= end
                    || anchorEntity != null && (!anchorEntity.isAlive() || anchorEntity.level() != owner.level())) return false;
            Vec3 anchor = anchorEntity == null ? anchorPoint : anchorEntity.getBoundingBox().getCenter();
            Vec3 from = elbow(owner, right);
            if (!latched) {
                flightTicks++;
                Vec3 travel = anchor.subtract(from);
                double distance = travel.length();
                ribbon.set(from, from.add(travel.scale(Math.min(1, flightTicks * GRAPPLE_TAPE_SPEED / Math.max(0.01, distance)))), 0.17F);
                if (flightTicks * GRAPPLE_TAPE_SPEED >= distance) {
                    latched = true;
                    ropeLength = Math.max(2, owner.getEyePosition().distanceTo(anchor) * 0.98);
                    ((ServerLevel) owner.level()).playSound(null, owner.blockPosition(), SoundEvents.LEASH_KNOT_PLACE,
                            SoundSource.PLAYERS, 0.6F, 1.4F);
                }
                return true;
            }
            ribbon.set(from, anchor, 0.17F);
            Vec3 offset = anchor.subtract(owner.getEyePosition());
            double distance = offset.length();
            if (distance < 0.01 || distance > 80) return false;
            Vec3 direction = offset.scale(1 / distance);
            boolean retract = owner.isShiftKeyDown() || anchorEntity != null;
            if (retract) ropeLength = Math.max(1.5, ropeLength - 0.75);
            Vec3 velocity = owner.getDeltaMovement();
            if (retract) {
                velocity = velocity.add(direction.scale(0.3));
            } else if (distance >= ropeLength - 0.5) {
                // Preserve motion tangent to the rope. Looking along the swing pumps
                // that tangent to overcome Minecraft's strong midair velocity drag.
                Vec3 tangent = owner.getLookAngle().subtract(direction.scale(owner.getLookAngle().dot(direction)));
                if (tangent.lengthSqr() < 0.01)
                    tangent = velocity.subtract(direction.scale(velocity.dot(direction)));
                if (tangent.lengthSqr() > 0.01 && velocity.lengthSqr() < 7.0)
                    velocity = velocity.add(tangent.normalize().scale(0.095));
                if (distance < ropeLength) {
                    owner.setDeltaMovement(velocity);
                    owner.fallDistance = 0;
                    owner.hurtMarked = true;
                    return true;
                }
                double outward = velocity.dot(direction);
                if (outward < 0) velocity = velocity.subtract(direction.scale(outward));
                velocity = velocity.add(direction.scale(Math.min(0.32, (distance - ropeLength) * 0.22 + 0.02)));
            }
            owner.setDeltaMovement(velocity);
            owner.fallDistance = 0;
            owner.hurtMarked = true;
            return true;
        }
        void discard() { ribbon.discard(); }
    }

    private static final class Binding {
        final ServerLevel level;
        final LivingEntity owner, target;
        final List<TapeRibbon> launch = new ArrayList<>();
        final List<TapeRibbon> cocoon = new ArrayList<>();
        Vec3 position;
        long end;
        TapeRibbon swingLine;
        boolean latched;
        boolean swinging;
        long swingEnd;
        int flightTicks;
        Binding(LivingEntity owner, LivingEntity target, long end) {
            this.level = (ServerLevel) owner.level();
            this.owner = owner; this.target = target; this.end = end;
        }
        boolean start(ServerLevel level) {
            for (int i = 0; i < 3; i++) {
                TapeRibbon strand = TapeRibbon.create(level, elbow(owner, i == 1));
                if (strand == null) { discard(); return false; }
                launch.add(strand);
            }
            return true;
        }
        boolean startSwing(ServerLevel level) {
            swingLine = TapeRibbon.create(level, elbow(owner, false));
            if (swingLine == null) return false;
            swinging = true;
            swingEnd = level.getGameTime() + SWING_TICKS;
            return true;
        }
        void wrap(ServerLevel level) {
            for (TapeRibbon strand : launch) strand.discard();
            launch.clear();
            latched = true;
            position = target.position();
            end = level.getGameTime() + BIND_TICKS;
            for (int i = 0; i < 16; i++) {
                TapeRibbon strand = TapeRibbon.create(level, target.getBoundingBox().getCenter());
                if (strand != null) cocoon.add(strand);
            }
            updateCocoon();
            level.playSound(null, target.blockPosition(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1, 0.8F);
        }
        void updateCocoon() {
            double radius = Math.max(0.42, target.getBbWidth() * 0.7);
            double height = Math.max(0.8, target.getBbHeight());
            Vec3 center = target.position().add(0, 0, 0);
            for (int i = 0; i < cocoon.size(); i++) {
                if (i >= 12) {
                    double a = Math.PI * (i - 12) / 2;
                    double b = a + Math.PI / 2;
                    cocoon.get(i).set(center.add(Math.cos(a) * radius, height * 0.15, Math.sin(a) * radius),
                            center.add(Math.cos(b) * radius, height * 0.85, Math.sin(b) * radius), 0.2F);
                    continue;
                }
                int ring = i / 4, edge = i % 4;
                double y = height * (0.23 + ring * 0.26);
                double a = Math.PI * (edge * 0.5 + ring * 0.125);
                double b = a + Math.PI / 2;
                cocoon.get(i).set(center.add(Math.cos(a) * radius, y, Math.sin(a) * radius),
                        center.add(Math.cos(b) * radius, y + (edge % 2 == 0 ? 0.12 : -0.12), Math.sin(b) * radius),
                        0.23F);
            }
        }
        boolean tick() {
            if (!target.isAlive() || target.level() != level || owner.level() != level) return false;
            if (!latched) {
                if (!owner.isAlive() || level.getGameTime() >= end) return false;
                flightTicks++;
                Vec3 center = target.getBoundingBox().getCenter();
                for (int i = 0; i < launch.size(); i++) {
                    Vec3 from = elbow(owner, i == 1);
                    Vec3 to = center.add((i - 1) * 0.22, (i - 1) * 0.3, 0);
                    double fraction = Math.min(1, flightTicks * BIND_TAPE_SPEED / Math.max(0.01, from.distanceTo(to)));
                    launch.get(i).set(from, from.lerp(to, fraction), 0.17F);
                }
                if (flightTicks * BIND_TAPE_SPEED >= owner.getEyePosition().distanceTo(center)) wrap(level);
                return true;
            }
            if (!swinging && level.getGameTime() >= end) return false;
            if (swinging) {
                if (!owner.isAlive()) return false;
                Vec3 from = owner.getEyePosition(), to = target.getBoundingBox().getCenter();
                Vec3 offset = to.subtract(from);
                Vec3 outward = offset.lengthSqr() < 0.01 ? owner.getLookAngle() : offset.normalize();
                Vec3 steer = owner.getLookAngle().subtract(outward.scale(owner.getLookAngle().dot(outward)));
                if (steer.lengthSqr() < 0.01) steer = owner.getLookAngle().cross(new Vec3(0, 1, 0));
                Vec3 velocity = target.getDeltaMovement().scale(0.93)
                        .add(steer.normalize().scale(0.2)).add(0, -0.025, 0);
                if (offset.length() > 4.2) {
                    double radial = velocity.dot(outward);
                    if (radial > 0) velocity = velocity.subtract(outward.scale(radial));
                    velocity = velocity.subtract(outward.scale(Math.min(0.8, (offset.length() - 4.2) * 0.25)));
                }
                target.setDeltaMovement(velocity);
                target.hurtMarked = true;
                if (swingLine != null) swingLine.set(elbow(owner, false), to, 0.19F);
                if (level.getGameTime() >= swingEnd) {
                    Vec3 launchVelocity = velocity.lengthSqr() < 0.2 ? owner.getLookAngle().scale(1.7)
                            : velocity.scale(1.8);
                    target.setDeltaMovement(launchVelocity);
                    target.hurtMarked = true;
                    return false;
                }
            } else {
                target.teleportTo(position.x, position.y, position.z);
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
            }
            if (swinging) updateCocoon();
            return true;
        }
        void discard() {
            launch.forEach(TapeRibbon::discard);
            cocoon.forEach(TapeRibbon::discard);
            if (swingLine != null) swingLine.discard();
        }
    }

    private static final class Strand {
        final ServerLevel level;
        final UUID owner;
        final Vec3 a, b;
        final long end, ready;
        final int fixTicks;
        final TapeRibbon ribbon;
        LivingEntity captured;
        Vec3 fixation;
        long release;
        Strand(ServerLevel level, UUID owner, Vec3 a, Vec3 b, long end, int fixTicks,
               TapeRibbon ribbon, long ready) {
            this.level = level; this.owner = owner; this.a = a; this.b = b;
            this.end = end; this.fixTicks = fixTicks; this.ribbon = ribbon; this.ready = ready;
        }
        boolean tick() {
            long now = level.getGameTime();
            if (now >= end) return false;
            if (!level.hasChunkAt(BlockPos.containing(a)) || !level.hasChunkAt(BlockPos.containing(b))) return true;
            double progress = Math.clamp((now - ready) / BARRICADE_EXTENSION_TICKS, 0, 1);
            ribbon.set(a, a.lerp(b, progress), 0.17F);
            if (progress < 1) return true;
            if (captured != null) {
                if (!captured.isAlive() || captured.level() != level || now >= release) return false;
                captured.teleportTo(fixation.x, fixation.y, fixation.z);
                captured.setDeltaMovement(Vec3.ZERO);
                captured.hurtMarked = true;
                return true;
            }
            LivingEntity creator = level.getPlayerByUUID(owner);
            if (creator == null) return true;
            AABB bounds = new AABB(a, b).inflate(0.9);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                    candidate -> enemy(creator, candidate))) {
                Vec3 closest = a.add(b.subtract(a).scale(Math.clamp(target.getBoundingBox().getCenter().subtract(a)
                        .dot(b.subtract(a)) / b.distanceToSqr(a), 0, 1)));
                if (target.getBoundingBox().getCenter().distanceToSqr(closest) <= 1.2) {
                    captured = target;
                    fixation = target.position();
                    release = now + fixTicks;
                    level.playSound(null, target.blockPosition(), SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 0.5F, 1.5F);
                    break;
                }
            }
            return true;
        }
        void discard() { ribbon.discard(); }
    }

    private static final class FlyingBlock {
        final ServerLevel level;
        final LivingEntity owner;
        final FallingBlockEntity block;
        final TapeRibbon ribbon;
        final Vec3 targetPoint;
        final float hardness;
        final long born;
        final int index;
        Vec3 flightVelocity;
        FlyingBlock(ServerLevel level, LivingEntity owner, FallingBlockEntity block, TapeRibbon ribbon,
                    Vec3 targetPoint, float hardness, long born, int index) {
            this.level = level; this.owner = owner; this.block = block; this.ribbon = ribbon;
            this.targetPoint = targetPoint; this.hardness = hardness; this.born = born; this.index = index;
        }
        boolean tick() {
            if (block.isRemoved()) return false;
            long age = level.getGameTime() - born;
            if (!owner.isAlive() || owner.level() != level
                    || age >= BLOCK_STAGE_TICKS + BLOCK_FLIGHT_TICKS + index / 5) return false;
            Vec3 velocity;
            if (age < BLOCK_STAGE_TICKS + index / 5) {
                double angle = index * Math.PI * (3 - Math.sqrt(5));
                Vec3 above = owner.getEyePosition().add(Math.cos(angle) * (1.5 + index % 3 * 0.6),
                        2.8 + index / 10.0, Math.sin(angle) * (1.5 + index % 3 * 0.6));
                velocity = above.subtract(block.position()).scale(0.38);
                block.setDeltaMovement(velocity);
                block.hurtMarked = true;
                ribbon.set(elbow(owner, index % 2 == 1), block.position(), 0.14F);
                return true;
            }
            if (flightVelocity == null) {
                Vec3 toTarget = targetPoint.subtract(block.position());
                if (toTarget.lengthSqr() < 0.01) return false;
                flightVelocity = toTarget.normalize().scale(2.1 + (index % 3) * 0.2);
                level.playSound(null, block.blockPosition(), SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.5F, 0.8F);
            }
            velocity = flightVelocity;
            Vec3 next = block.position().add(velocity);
            ribbon.set(elbow(owner, index % 2 == 1), block.position(), 0.12F);
            BlockHitResult wall = level.clip(new ClipContext(block.position(), next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, block));
            if (wall.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
            for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class,
                    block.getBoundingBox().expandTowards(velocity).inflate(0.3), candidate -> enemy(owner, candidate))) {
                if (hit.hurt(owner.damageSources().mobAttack(owner), (float) ((4 + hardness * 2) * velocity.length()))) {
                    return false;
                }
            }
            block.setDeltaMovement(velocity);
            block.hurtMarked = true;
            if (block.position().distanceToSqr(targetPoint) < 1.5) return false;
            return true;
        }
        void discard() { ribbon.discard(); block.discard(); }
    }
}

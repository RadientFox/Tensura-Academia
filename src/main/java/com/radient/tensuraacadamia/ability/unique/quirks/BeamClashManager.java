package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class BeamClashManager {
    public enum BeamType {
        IMPURE_FULL_POWER,
        EVERY_LAST_DROP
    }

    public enum Phase {
        PUSHING,
        WINNER,
        FIZZLED
    }

    public record Control(Phase phase, UUID winner, UUID loser, UUID opponent,
                          Vec3 clashPoint, double sizeMultiplier) {
        public boolean isWinner(UUID id) { return id.equals(winner); }
        public boolean isLoser(UUID id) { return id.equals(loser); }
    }

    private static final double EQUAL_POWER_WINDOW = 25_000.0D;
    private static final int WINNER_CLASH_TICKS = 20;
    private static final int EQUAL_CLASH_TICKS = 60;
    private static final int CLASH_COOLDOWN_SECONDS = 5;
    private static final ResourceLocation MOVEMENT_LOCK =
            ResourceLocation.fromNamespaceAndPath("tracadamia", "beam_clash_movement_lock");
    private static final Map<UUID, BeamSnapshot> BEAMS = new HashMap<>();
    private static final Map<UUID, Clash> CLASHES = new HashMap<>();

    private record BeamSnapshot(LivingEntity owner, ManasSkillInstance instance, BeamType type, int mode,
                                Vec3 start, Vec3 end, double width, double power, long gameTime) {
    }

    private static final class Clash {
        private final BeamSnapshot first;
        private final BeamSnapshot second;
        private final Vec3 clashPoint;
        private final boolean equalPower;
        private final long startedAt;
        private Phase phase = Phase.PUSHING;
        private UUID winner;
        private UUID loser;
        private double sizeMultiplier = 1.0D;

        private Clash(BeamSnapshot first, BeamSnapshot second, Vec3 clashPoint,
                      boolean equalPower, long startedAt) {
            this.first = first;
            this.second = second;
            this.clashPoint = clashPoint;
            this.equalPower = equalPower;
            this.startedAt = startedAt;
        }

        private UUID firstId() { return first.owner.getUUID(); }
        private UUID secondId() { return second.owner.getUUID(); }

        private LivingEntity owner(UUID id) {
            return firstId().equals(id) ? first.owner : second.owner;
        }

    }

    private BeamClashManager() {
    }

    public static void publish(LivingEntity owner, ManasSkillInstance instance, BeamType type, int mode,
                               Vec3 start, Vec3 end, double width, double power) {
        BeamSnapshot beam = new BeamSnapshot(owner, instance, type, mode,
                start, end, width, Math.max(0.0D, power), serverLevel(owner).getGameTime());
        BEAMS.put(owner.getUUID(), beam);
        findNewClash(beam);
    }

    public static Control getControl(UUID ownerId) {
        Clash clash = CLASHES.get(ownerId);
        if (clash == null) return null;
        UUID opponent = clash.firstId().equals(ownerId) ? clash.secondId() : clash.firstId();
        return new Control(clash.phase, clash.winner, clash.loser, opponent,
                clash.clashPoint, clash.sizeMultiplier);
    }

    public static void onBeamStopped(UUID ownerId) {
        BEAMS.remove(ownerId);
        Clash clash = CLASHES.get(ownerId);
        if (clash == null || clash.phase == Phase.FIZZLED) return;
        if (clash.phase == Phase.PUSHING || ownerId.equals(clash.winner)) {
            endClash(clash);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tickClashes(ServerTickEvent.Post event) {
        removeStaleBeams();
        Set<Clash> uniqueClashes = new HashSet<>(CLASHES.values());
        for (Clash clash : uniqueClashes) {
            advanceClash(clash);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        onBeamStopped(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Clash clash : new HashSet<>(CLASHES.values())) {
            unlockLoser(clash);
        }
        CLASHES.clear();
        BEAMS.clear();
    }

    private static void removeStaleBeams() {
        BEAMS.entrySet().removeIf(entry -> {
            BeamSnapshot beam = entry.getValue();
            return !beam.owner.isAlive() || beam.owner.isRemoved()
                    || serverLevel(beam.owner).getGameTime() - beam.gameTime > 2L;
        });
    }

    private static void findNewClash(BeamSnapshot first) {
        if (CLASHES.containsKey(first.owner.getUUID()) || !isFresh(first)) return;
        for (BeamSnapshot second : BEAMS.values()) {
            if (CLASHES.containsKey(second.owner.getUUID()) || !isFresh(second)
                    || first.type == second.type || first.owner == second.owner
                    || first.owner.level() != second.owner.level()
                    || Math.abs(first.gameTime - second.gameTime) > 2L) {
                continue;
            }

            Vec3 firstDirection = first.end.subtract(first.start).normalize();
            Vec3 secondDirection = second.end.subtract(second.start).normalize();
            if (firstDirection.dot(secondDirection) > -0.65D) continue;

            SegmentClosest closest = closestPoints(first.start, first.end, second.start, second.end);
            double collisionRadius = (first.width + second.width) * 0.5D + 0.25D;
            if (closest.first.distanceToSqr(closest.second) > collisionRadius * collisionRadius) continue;

            Vec3 point = clashPoint(first, second, closest);
            boolean equalPower = Math.abs(first.power - second.power) <= EQUAL_POWER_WINDOW;
            Clash clash = new Clash(first, second, point, equalPower, first.gameTime);
            CLASHES.put(first.owner.getUUID(), clash);
            CLASHES.put(second.owner.getUUID(), clash);
            break;
        }
    }

    private static void advanceClash(Clash clash) {
        long now = serverLevel(clash.first.owner).getGameTime();
        if (clash.phase == Phase.FIZZLED) {
            if (now - clash.startedAt >= EQUAL_CLASH_TICKS + 2L) endClash(clash);
            return;
        }

        if (clash.phase == Phase.PUSHING) {
            BeamSnapshot firstBeam = BEAMS.get(clash.firstId());
            BeamSnapshot secondBeam = BEAMS.get(clash.secondId());
            if (firstBeam == null || secondBeam == null
                    || !isFresh(firstBeam) || !isFresh(secondBeam)) {
                endClash(clash);
                return;
            }
            pushFromClash(clash.first.owner, clash.clashPoint);
            pushFromClash(clash.second.owner, clash.clashPoint);
            ServerLevel level = serverLevel(clash.first.owner);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, clash.clashPoint.x, clash.clashPoint.y,
                    clash.clashPoint.z, 12, 0.32D, 0.32D, 0.32D, 0.03D);
            level.sendParticles(ParticleTypes.END_ROD, clash.clashPoint.x, clash.clashPoint.y,
                    clash.clashPoint.z, 6, 0.24D, 0.24D, 0.24D, 0.01D);

            long duration = clash.equalPower ? EQUAL_CLASH_TICKS : WINNER_CLASH_TICKS;
            if (now - clash.startedAt >= duration) {
                if (clash.equalPower) {
                    fizzle(clash);
                } else {
                    resolveWinner(clash);
                }
            }
            return;
        }

        if (clash.phase == Phase.WINNER) {
            BeamSnapshot winnerBeam = BEAMS.get(clash.winner);
            LivingEntity loser = clash.owner(clash.loser);
            if (winnerBeam == null || !isFresh(winnerBeam) || !loser.isAlive()
                    || loser.isRemoved() || loser.level() != winnerBeam.owner.level()) {
                endClash(clash);
                return;
            }
            lock(loser);
            if (clash.first.owner.tickCount % 2 == 0) {
                Vec3 target = loser.getEyePosition();
                serverLevel(winnerBeam.owner).sendParticles(ParticleTypes.END_ROD,
                        target.x, target.y, target.z, 3, 0.15D, 0.15D, 0.15D, 0.0D);
            }
        }
    }

    private static void fizzle(Clash clash) {
        clash.phase = Phase.FIZZLED;
        clash.first.instance.setCoolDown(CLASH_COOLDOWN_SECONDS, clash.first.mode);
        clash.second.instance.setCoolDown(CLASH_COOLDOWN_SECONDS, clash.second.mode);
        clash.first.instance.markDirty();
        clash.second.instance.markDirty();
        if (clash.first.owner instanceof Player player)
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The beam clash fizzles out."), true);
        if (clash.second.owner instanceof Player player)
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("The beam clash fizzles out."), true);
        unlockLoser(clash);
    }

    private static void resolveWinner(Clash clash) {
        BeamSnapshot winner = clash.first.power > clash.second.power ? clash.first : clash.second;
        BeamSnapshot loser = winner == clash.first ? clash.second : clash.first;
        clash.phase = Phase.WINNER;
        clash.winner = winner.owner.getUUID();
        clash.loser = loser.owner.getUUID();
        clash.sizeMultiplier = winner.type == BeamType.IMPURE_FULL_POWER ? 3.0D : 2.0D;
        lock(loser.owner);
        String winningBeam = winner.type == BeamType.IMPURE_FULL_POWER
                ? "Impure Beam Full Power" : "Every Last Drop";
        if (winner.owner instanceof Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Your " + winningBeam + " wins the beam clash."), true);
        }
        if (loser.owner instanceof Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "Your beam loses the clash to " + winningBeam + "."), true);
        }
    }

    private static void pushFromClash(LivingEntity player, Vec3 point) {
        Vec3 away = player.getEyePosition().subtract(point).normalize();
        if (away.lengthSqr() < 1.0E-6D) return;
        player.setDeltaMovement(player.getDeltaMovement().add(away.scale(0.025D)));
        player.hurtMarked = true;
    }

    private static void lock(LivingEntity player) {
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.addOrUpdateTransientModifier(new AttributeModifier(MOVEMENT_LOCK,
                    -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    private static void unlockLoser(Clash clash) {
        if (clash.loser == null) return;
        LivingEntity loser = clash.owner(clash.loser);
        AttributeInstance movement = loser.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(MOVEMENT_LOCK);
    }

    private static boolean isFresh(BeamSnapshot beam) {
        return beam.owner.isAlive() && !beam.owner.isRemoved()
                && serverLevel(beam.owner).getGameTime() - beam.gameTime <= 2L;
    }

    private static void endClash(Clash clash) {
        unlockLoser(clash);
        CLASHES.remove(clash.firstId(), clash);
        CLASHES.remove(clash.secondId(), clash);
        BEAMS.remove(clash.firstId());
        BEAMS.remove(clash.secondId());
    }

    private static SegmentClosest closestPoints(Vec3 firstStart, Vec3 firstEnd,
                                                Vec3 secondStart, Vec3 secondEnd) {
        Vec3 firstDirection = firstEnd.subtract(firstStart);
        Vec3 secondDirection = secondEnd.subtract(secondStart);
        Vec3 betweenStarts = firstStart.subtract(secondStart);
        double a = firstDirection.dot(firstDirection);
        double b = firstDirection.dot(secondDirection);
        double c = secondDirection.dot(secondDirection);
        double d = firstDirection.dot(betweenStarts);
        double e = secondDirection.dot(betweenStarts);
        double denominator = a * c - b * b;
        double firstParameter = denominator < 1.0E-8D ? 0.0D
                : clamp((b * e - c * d) / denominator, 0.0D, 1.0D);
        double secondParameter = c < 1.0E-8D ? 0.0D
                : clamp((b * firstParameter + e) / c, 0.0D, 1.0D);
        firstParameter = a < 1.0E-8D ? 0.0D
                : clamp((b * secondParameter - d) / a, 0.0D, 1.0D);
        secondParameter = c < 1.0E-8D ? 0.0D
                : clamp((b * firstParameter + e) / c, 0.0D, 1.0D);
        return new SegmentClosest(firstStart.add(firstDirection.scale(firstParameter)),
                secondStart.add(secondDirection.scale(secondParameter)));
    }

    private static Vec3 clashPoint(BeamSnapshot first, BeamSnapshot second, SegmentClosest closest) {
        Vec3 firstLine = first.end.subtract(first.start);
        Vec3 secondLine = second.end.subtract(second.start);
        double firstLength = firstLine.length();
        double secondLength = secondLine.length();
        if (firstLength > 1.0E-6D && secondLength > 1.0E-6D
                && firstLine.normalize().dot(secondLine.normalize()) < -0.95D) {
            Vec3 axis = firstLine.scale(1.0D / firstLength);
            double secondStartOnFirst = second.start.subtract(first.start).dot(axis);
            double secondEndOnFirst = second.end.subtract(first.start).dot(axis);
            double overlapStart = Math.max(0.0D, Math.min(secondStartOnFirst, secondEndOnFirst));
            double overlapEnd = Math.min(firstLength, Math.max(secondStartOnFirst, secondEndOnFirst));
            if (overlapEnd >= overlapStart) {
                Vec3 pointOnFirst = first.start.add(axis.scale((overlapStart + overlapEnd) * 0.5D));
                Vec3 secondAxis = secondLine.scale(1.0D / secondLength);
                double alongSecond = clamp(pointOnFirst.subtract(second.start).dot(secondAxis), 0.0D, secondLength);
                Vec3 pointOnSecond = second.start.add(secondAxis.scale(alongSecond));
                return pointOnFirst.add(pointOnSecond).scale(0.5D);
            }
        }
        return closest.first.add(closest.second).scale(0.5D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ServerLevel serverLevel(LivingEntity entity) {
        return (ServerLevel) entity.level();
    }

    private record SegmentClosest(Vec3 first, Vec3 second) {
    }
}

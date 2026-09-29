package com.radient.tensuraacadamia.ability.unique.quirks;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class HomingSteering {
    private static final String DETOUR = "HomingDetour";

    private HomingSteering() { }

    public record Result(Vec3 velocity, boolean avoiding) { }

    public static Vec3 motion(Projectile shot) {
        Vec3 velocity = shot.getDeltaMovement();
        var tag = shot.getPersistentData().getCompound(DETOUR);
        return velocity.lengthSqr() > 0.0001 || tag.isEmpty() ? velocity
                : new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")).scale(tag.getDouble("Speed"));
    }

    public static void clear(Projectile shot) { shot.getPersistentData().remove(DETOUR); }

    public static Result steer(Projectile shot, Vec3 desired, Vec3 target) {
        double speed = desired.length();
        if (speed < 0.0001) return new Result(desired, false);
        Vec3 forward = desired.normalize();
        double lookAhead = Math.min(12, Math.max(3, speed * 3));
        // Do not look beyond the target and mistake the wall behind it for an obstruction.
        lookAhead = Math.min(lookAhead, target.distanceTo(shot.position()));
        if (clearPath(shot, forward, lookAhead)) {
            clear(shot);
            return new Result(desired, false);
        }
        var memory = shot.getPersistentData().getCompound(DETOUR);
        Vec3 previous = new Vec3(memory.getDouble("X"), memory.getDouble("Y"), memory.getDouble("Z"));
        if (memory.getInt("Until") >= shot.tickCount && previous.lengthSqr() > 0.5
                && clearPath(shot, previous, lookAhead)) return new Result(previous.scale(speed), true);

        Vec3 right = forward.cross(Math.abs(forward.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 heading = motion(shot).normalize(), aim = target.subtract(shot.position()).normalize();
        Vec3 best = null;
        double bestScore = -Double.MAX_VALUE;
        // A small fan of side/up/down routes, including turns away from a dead end.
        for (int turn = 1; turn <= 3; turn++) {
            double angle = turn * Math.PI / 4;
            for (int side = 0; side < 8; side++) {
                double around = side * Math.PI / 4;
                Vec3 direction = forward.scale(Math.cos(angle)).add(
                        right.scale(Math.sin(angle) * Math.cos(around))).add(up.scale(Math.sin(angle) * Math.sin(around)));
                double score = direction.dot(aim) * 2 + direction.dot(heading) * 0.5;
                if (score > bestScore && clearPath(shot, direction, lookAhead)) {
                    best = direction;
                    bestScore = score;
                }
            }
        }
        // If enclosed, wait and retry rather than steering through the wall.
        Vec3 remembered = best == null ? forward : best;
        var saved = new net.minecraft.nbt.CompoundTag();
        saved.putDouble("X", remembered.x);
        saved.putDouble("Y", remembered.y);
        saved.putDouble("Z", remembered.z);
        saved.putDouble("Speed", speed);
        saved.putInt("Until", shot.tickCount + 8);
        shot.getPersistentData().put(DETOUR, saved);
        return new Result(best == null ? Vec3.ZERO : best.scale(speed), true);
    }

    private static boolean clearPath(Projectile shot, Vec3 direction, double distance) {
        // Swept collision boxes include the projectile's width, not just its center ray.
        int steps = Math.max(1, (int) Math.ceil(distance / 0.5));
        Vec3 step = direction.scale(distance / steps);
        var box = shot.getBoundingBox().deflate(0.01);
        for (int i = 0; i < steps; i++) {
            if (shot.level().getBlockCollisions(shot, box.expandTowards(step)).iterator().hasNext()) return false;
            box = box.move(step);
        }
        return true;
    }
}

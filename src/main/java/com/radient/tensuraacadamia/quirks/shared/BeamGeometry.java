package com.radient.tensuraacadamia.quirks.shared;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.SimpleParticleType;

public final class BeamGeometry {
    private BeamGeometry() {
    }

    public static boolean intersects(AABB bounds, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
                              double length, double width, double height) {
        Vec3 relative = bounds.getCenter().subtract(origin);
        double forwardDistance = relative.dot(forward);
        double sideDistance = Math.abs(relative.dot(right));
        double upDistance = Math.abs(relative.dot(up));
        double forwardExtent = projectedExtent(bounds, forward);
        double sideExtent = projectedExtent(bounds, right);
        double upExtent = projectedExtent(bounds, up);
        return forwardDistance + forwardExtent >= 0.0D && forwardDistance <= length
                && forwardDistance - forwardExtent <= length
                && sideDistance <= width * 0.5D + sideExtent
                && upDistance <= height * 0.5D + upExtent;
    }

    public static Vec3 perpendicular(Vec3 direction) {
        Vec3 right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-5D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
    }
    public static void sendVisual(ServerLevel level, SimpleParticleType particle, Vec3 origin, Vec3 direction,
                           double length, double renderLength) {
        Vec3 end = origin.add(direction.scale(length)), delta = end.subtract(origin);
        for (var viewer : level.players()) {
            double fraction = delta.lengthSqr() < 1e-8 ? 0
                    : Math.clamp(viewer.getEyePosition().subtract(origin).dot(delta) / delta.lengthSqr(), 0, 1);
            if (viewer.getEyePosition().distanceToSqr(origin.add(delta.scale(fraction))) > 128 * 128) continue;
            double scale = length / renderLength;
            level.sendParticles(viewer, particle, true, origin.x, origin.y, origin.z, 0,
                    direction.x * scale, direction.y * scale, direction.z * scale, 1);
        }
    }

    private static double projectedExtent(AABB bounds, Vec3 axis) {
        return Math.abs(axis.x) * bounds.getXsize() * 0.5D
                + Math.abs(axis.y) * bounds.getYsize() * 0.5D
                + Math.abs(axis.z) * bounds.getZsize() * 0.5D;
    }
}

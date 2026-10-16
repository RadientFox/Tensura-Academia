package com.radient.tensuraacadamia.ability.unique.quirks;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class BeamGeometry {
    private BeamGeometry() {
    }

    static boolean intersects(AABB bounds, Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
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

    static Vec3 perpendicular(Vec3 direction) {
        Vec3 right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        return right.lengthSqr() < 1.0E-5D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
    }

    private static double projectedExtent(AABB bounds, Vec3 axis) {
        return Math.abs(axis.x) * bounds.getXsize() * 0.5D
                + Math.abs(axis.y) * bounds.getYsize() * 0.5D
                + Math.abs(axis.z) * bounds.getZsize() * 0.5D;
    }
}

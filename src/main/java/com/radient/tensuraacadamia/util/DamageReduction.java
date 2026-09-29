package com.radient.tensuraacadamia.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

public final class DamageReduction {

    private record Hit(DamageSource source, long time, double total) {}

    private static final double MAX_REDUCTION = 0.99D;

    private static final Map<LivingEntity, Hit> HITS = new WeakHashMap<>();

    private DamageReduction() {
    }

    public static float reduce(LivingEntity entity, DamageSource source, float amount, double reduction) {
        if (reduction <= 0.0D) {
            return amount;
        }

        long time = entity.level().getGameTime();
        Hit hit = HITS.get(entity);
        double before = hit != null && hit.source() == source && hit.time() == time ? hit.total() : 0.0D;
        double capped = Math.min(reduction, MAX_REDUCTION);
        double after = before + capped / (1.0D - capped);
        HITS.put(entity, new Hit(source, time, after));
        return (float) (amount * (1.0D + before) / (1.0D + after));
    }

}

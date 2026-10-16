package com.radient.tensuraacadamia.ability.unique.quirks;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Mob;

public final class DoubleMobHealthData {
    private static EntityDataAccessor<Float> reducedMaxHealth;

    private DoubleMobHealthData() {
    }

    public static EntityDataAccessor<Float> createAccessor() {
        if (reducedMaxHealth == null) {
            reducedMaxHealth = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.FLOAT);
        }
        return reducedMaxHealth;
    }

    public static float get(Mob mob) {
        return mob.getEntityData().get(createAccessor());
    }

    public static void set(Mob mob, float health) {
        if (Float.isFinite(health) && health > 0.0F) {
            mob.getEntityData().set(createAccessor(), health);
        }
    }
}

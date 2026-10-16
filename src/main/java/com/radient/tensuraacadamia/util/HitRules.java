package com.radient.tensuraacadamia.util;

import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jetbrains.annotations.Nullable;

public final class HitRules {

    private HitRules() {
    }

    public static boolean canHit(@Nullable Entity attacker, Entity target) {
        if (attacker == null) {
            return true;
        }
        if (target == attacker) {
            return false;
        }
        if (attacker instanceof LivingEntity user && target instanceof LivingEntity living && isOwnSubordinate(user, living)) {
            return true;
        }
        return !target.isAlliedTo(attacker) && !attacker.isAlliedTo(target);
    }

    public static boolean isOwnSubordinate(LivingEntity owner, LivingEntity target) {
        return target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()) || SubordinateHelper.isSubordinate(owner, target);
    }

}

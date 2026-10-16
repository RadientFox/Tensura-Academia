package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.ability.unique.quirks.WhaleQuirk;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class WhaleClient {

    private static final float STRIKE_TICKS = 12.0F;
    private static final float GROW_TICKS = 2.0F;
    private static final float SHRINK_TICKS = 5.0F;
    private static final float HEAD_WIDTH = 2.6F;
    private static final float HEAD_LENGTH = 3.4F;

    private static boolean scaled;

    private WhaleClient() {
    }

    // Head grows larger for the headbutt then shrinks back
    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        float size = 1.0F;
        long start = ageInTicks == 0.0F ? Long.MIN_VALUE : WhaleQuirk.getWhale(entity).map(WhaleQuirk::getStrikeStart).orElse(Long.MIN_VALUE);
        if (start != Long.MIN_VALUE) {
            float age = entity.level().getGameTime() - start + ageInTicks - entity.tickCount;
            if (age >= 0.0F && age <= STRIKE_TICKS) {
                size = Math.min(Mth.clamp(age / GROW_TICKS, 0.0F, 1.0F), Mth.clamp((STRIKE_TICKS - age) / SHRINK_TICKS, 0.0F, 1.0F));
                size = 1.0F + size * size * (3.0F - 2.0F * size) * (HEAD_WIDTH - 1.0F);
            }
        }

        if (size == 1.0F && !scaled) {
            return;
        }

        scaled = size != 1.0F;
        float length = 1.0F + (size - 1.0F) * (HEAD_LENGTH - 1.0F) / (HEAD_WIDTH - 1.0F);
        scale(model.head, size, length);
        scale(model.hat, size, length);
    }

    private static void scale(ModelPart part, float size, float length) {
        part.xScale = size;
        part.yScale = size;
        part.zScale = length;
    }

}

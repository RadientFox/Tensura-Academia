package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.GigantificationQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.Input;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class GigantificationClient {

    private static final float KICK_BACK = 0.9F;
    private static final float KICK_FORWARD = -1.5F;
    private static final float STOMP_RAISE = -1.4F;
    private static final float MIN_FOLLOW_TICKS = 6.0F;

    private GigantificationClient() {
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (ageInTicks == 0.0F) {
            return;
        }

        ManasSkillInstance instance = GigantificationQuirk.getGigantification(entity).orElse(null);
        if (instance == null) {
            return;
        }

        // Right arm held out with whatever's in hand
        if (GigantificationQuirk.isHandFull(instance)) {
            model.rightArm.xRot = -Mth.HALF_PI;
            model.rightArm.yRot = 0.0F;
            model.rightArm.zRot = 0.0F;
            model.rightSleeve.copyFrom(model.rightArm);
        }

        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(GigantificationQuirk.KICK_TAG)) {
            return;
        }

        float windup = Math.max(1, tag.getInt(GigantificationQuirk.KICK_TICKS_TAG));
        float follow = Math.max(MIN_FOLLOW_TICKS, windup * 0.5F);
        float age = entity.level().getGameTime() - tag.getLong(GigantificationQuirk.KICK_TAG) + ageInTicks - entity.tickCount;
        if (age < 0.0F || age > windup + follow) {
            return;
        }

        // Slow windup
        float charge = ease(Mth.clamp(age / windup, 0.0F, 1.0F));
        float swing = Mth.clamp((age - windup) / follow, 0.0F, 1.0F);
        float legRot;
        if (tag.getBoolean(GigantificationQuirk.STOMP_TAG)) {
            legRot = age < windup ? STOMP_RAISE * charge : STOMP_RAISE * (1.0F - ease(Mth.clamp(swing * 4.0F, 0.0F, 1.0F)));
        } else if (age < windup) {
            legRot = KICK_BACK * charge;
        } else {
            float snap = ease(Mth.clamp(swing * 3.0F, 0.0F, 1.0F));
            float back = ease(Mth.clamp((swing - 0.4F) / 0.6F, 0.0F, 1.0F));
            legRot = Mth.lerp(snap, KICK_BACK, KICK_FORWARD) * (1.0F - back);
        }

        poseKick(model, legRot);
    }

    // Right leg kick
    public static void poseKick(PlayerModel<?> model, float legRot) {
        model.rightLeg.xRot = legRot;
        model.rightLeg.zRot = 0.0F;
        model.rightPants.copyFrom(model.rightLeg);
        model.rightArm.xRot -= legRot * 0.4F;
        model.leftArm.xRot += legRot * 0.4F;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Player player = event.getEntity();
        if (GigantificationQuirk.getGigantification(player).filter(instance -> GigantificationQuirk.isKicking(instance, player)).isEmpty()) {
            return;
        }

        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
    }

    private static float ease(float progress) {
        return progress * progress * (3.0F - 2.0F * progress);
    }

}

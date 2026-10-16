package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.MuscleExpansionQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;

//BIG BOY ARMS
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class MuscleExpansionClient {

    private static final float GROW_PER_TICK = 0.05F;
    private static final float SLAM_DROP_TICKS = 4.0F;
    private static final float OVERHEAD = -2.9F;
    private static final float SLAMMED = -0.7F;
    private static final float ARM_INNER = 1.0F;
    private static final float ARM_TOP = 2.0F;

    private static final Map<Integer, float[]> SIZES = new HashMap<>();

    private MuscleExpansionClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SIZES.clear();
            return;
        }

        for (AbstractClientPlayer player : level.players()) {
            float target = MuscleExpansionQuirk.getMuscleExpansion(player).map(MuscleExpansionQuirk::getArmSize).orElse(1.0F);
            float[] size = SIZES.get(player.getId());
            if (size == null) {
                if (target == 1.0F) {
                    continue;
                }

                size = new float[]{1.0F, 1.0F};
                SIZES.put(player.getId(), size);
            }

            size[1] = size[0];
            size[0] = size[0] < target ? Math.min(target, size[0] + GROW_PER_TICK) : Math.max(target, size[0] - GROW_PER_TICK);
        }

        SIZES.entrySet().removeIf(entry -> !(level.getEntity(entry.getKey()) instanceof Player) || entry.getValue()[0] == 1.0F && entry.getValue()[1] == 1.0F);
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        ManasSkillInstance instance = entity instanceof Player ? MuscleExpansionQuirk.getMuscleExpansion(entity).orElse(null) : null;
        if (instance == null) {
            return;
        }

        float[] size = SIZES.get(entity.getId());
        if (size != null) {
            float partialTick = ageInTicks - entity.tickCount;
            float grown = Mth.lerp(partialTick, size[1], size[0]);
            grow(model.rightArm, model.rightSleeve, grown, -1.0F);
            grow(model.leftArm, model.leftSleeve, grown, 1.0F);
        }

        if (MuscleExpansionQuirk.isChoking(instance, entity)) {
            model.leftArm.xRot = -1.45F + model.head.xRot * 0.5F;
            model.leftArm.yRot = 0.15F;
            model.leftArm.zRot = 0.0F;
            model.leftSleeve.copyFrom(model.leftArm);
        }

        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(MuscleExpansionQuirk.SLAM_TAG)) {
            float age = entity.level().getGameTime() - tag.getLong(MuscleExpansionQuirk.SLAM_TAG) + ageInTicks - entity.tickCount;
            float lift = MuscleExpansionQuirk.SLAM_LIFT_TICKS;
            if (age >= 0.0F && age <= lift + SLAM_DROP_TICKS) {
                float pitch = age < lift ? OVERHEAD : Mth.lerp(Mth.clamp((age - lift) / SLAM_DROP_TICKS, 0.0F, 1.0F), OVERHEAD, SLAMMED);
                raise(model.rightArm, model.rightSleeve, pitch, -0.15F);
                raise(model.leftArm, model.leftSleeve, pitch, 0.15F);
            }
        }
    }

    private static void grow(ModelPart arm, ModelPart sleeve, float size, float side) {
        arm.xScale *= size;
        arm.yScale *= size;
        arm.zScale *= size;
        arm.x += side * (size - 1.0F) * ARM_INNER;
        arm.y += (size - 1.0F) * ARM_TOP;
        sleeve.copyFrom(arm);
    }

    private static void raise(ModelPart arm, ModelPart sleeve, float pitch, float roll) {
        arm.xRot = pitch;
        arm.yRot = 0.0F;
        arm.zRot = roll;
        sleeve.copyFrom(arm);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SIZES.clear();
    }

}

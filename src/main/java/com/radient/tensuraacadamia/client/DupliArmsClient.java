package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.DupliArmsQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.MultiArms;
import com.radient.tensuraacadamia.mixin.client.LevelRendererAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class DupliArmsClient {

    // Octoblow, three arms out of its hand, then four out of each of those
    private static final int PRIMARIES = 3;
    private static final int SECONDARIES = 4;
    private static final int[] PUNCH_ORDER = {0, 5, 9, 13, 2, 6, 10, 14, 1, 7, 11, 15, 3, 4, 8, 12};
    private static final int[] PUNCH_SLOT = new int[DupliArmsQuirk.OCTOBLOW_ARMS];

    static {
        for (int slot = 0; slot < PUNCH_ORDER.length; slot++) {
            PUNCH_SLOT[PUNCH_ORDER[slot]] = slot;
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new DupliArmsLayer(renderer, event.getContext().getItemInHandRenderer()));
            }
        }
    }

    private static Optional<ManasSkillInstance> getDupliArms(AbstractClientPlayer player) {
        return SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.DUPLI_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static float getAge(long start, float now) {
        return start == Long.MIN_VALUE ? -1.0F : now - start;
    }

    // Each arm thrusts out around the tick it lands its hit
    private static float getPunch(int arm, float age, int hits) {
        float punch = 0.0F;
        for (int hit = PUNCH_SLOT[arm]; hit < hits; hit += DupliArmsQuirk.OCTOBLOW_ARMS) {
            punch = Math.max(punch, 1.0F - Math.abs(age - hit) / 2.5F);
        }

        return punch;
    }

    public static boolean poseMainArm(PlayerModel<?> model, AbstractClientPlayer player, float ageInTicks, float netHeadYaw, float headPitch) {
        Optional<ManasSkillInstance> dupliArms = getDupliArms(player);
        if (dupliArms.isEmpty()) {
            return false;
        }

        ManasSkillInstance instance = dupliArms.get();
        float now = player.level().getGameTime() + (ageInTicks - player.tickCount);
        float aim = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD;
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        ModelPart arm = model.rightArm;

        float octoblow = getAge(DupliArmsQuirk.getOctoblowStart(instance), now);
        int hits = DupliArmsQuirk.getOctoblowHits(instance);
        if (octoblow >= 0.0F && octoblow < hits + 6.0F) {
            float jab = getPunch(0, octoblow, hits);
            arm.xRot = aim + Mth.sin(octoblow * 2.3F) * 0.12F - jab * 0.2F;
            arm.yRot = yaw - 0.1F;
            arm.zRot = 0.0F;
            arm.z -= jab * 2.5F;
            return true;
        }

        float octospansion = getAge(DupliArmsQuirk.getOctospansionStart(instance), now);
        if (octospansion >= 0.0F && octospansion < DupliArmsQuirk.OCTOSPANSION_TICKS) {
            float thrust = Mth.clamp((octospansion - DupliArmsQuirk.OCTOSPANSION_WINDUP + 2.0F) / 2.0F, 0.0F, 1.0F);
            float settle = Mth.clamp((DupliArmsQuirk.OCTOSPANSION_TICKS - octospansion) / 6.0F, 0.0F, 1.0F);
            arm.xRot = Mth.lerp(settle, arm.xRot, Mth.lerp(thrust, 0.9F, aim));
            arm.yRot = Mth.lerp(settle, arm.yRot, yaw + Mth.lerp(thrust, 0.5F, 0.0F));
            arm.zRot = Mth.lerp(settle, arm.zRot, 0.0F);
            arm.z -= thrust * settle * 2.0F;
            return true;
        }

        return false;
    }

    // The block this player is breaking
    private static @Nullable BlockState getMinedBlock(AbstractClientPlayer player) {
        BlockDestructionProgress progress = ((LevelRendererAccessor) Minecraft.getInstance().levelRenderer).tracadamia$getDestroyingBlocks().get(player.getId());
        return progress == null ? null : player.level().getBlockState(progress.getPos());
    }

    private static class DupliArmsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        // Grown arms come out of the back in pairs
        private static final float[][] SHOULDERS = {{4.2F, 2.5F, 2.2F}, {4.2F, 2.5F, 2.2F}, {3.8F, 6.0F, 2.4F}, {3.8F, 6.0F, 2.4F}};
        private static final float[] TILTS = {0.55F, 0.55F, 0.95F, 0.95F};
        private static final float ARM_WIDTH = 0.85F;
        private static final float DIG_TICKS = 6.0F;

        private final ItemInHandRenderer itemInHandRenderer;

        DupliArmsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible()) {
                return;
            }

            Optional<ManasSkillInstance> dupliArms = getDupliArms(player);
            if (dupliArms.isEmpty()) {
                return;
            }

            ManasSkillInstance instance = dupliArms.get();
            float now = player.level().getGameTime() + partialTick;
            int arms = Math.min(MultiArms.SLOTS, DupliArmsQuirk.getArms(instance));
            float octoblowAge = getAge(DupliArmsQuirk.getOctoblowStart(instance), now);
            int octoblowHits = DupliArmsQuirk.getOctoblowHits(instance);
            boolean octoblow = octoblowAge >= 0.0F && octoblowAge < octoblowHits + 6.0F;
            float octospansionAge = getAge(DupliArmsQuirk.getOctospansionStart(instance), now);
            boolean octospansion = octospansionAge >= 0.0F && octospansionAge < DupliArmsQuirk.OCTOSPANSION_TICKS;
            if (arms <= 0 && !octoblow && !octospansion) {
                return;
            }

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            PartPose main = model.rightArm.storePose();
            PartPose[] rest = MultiArmsClient.getRestPoses(model, player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            MultiArmsClient.ArmContext context = MultiArmsClient.ArmContext.of(model, player, this.itemInHandRenderer, poseStack, buffer, packedLight);

            renderGrownArms(context, instance, rest, arms, partialTick, ageInTicks);
            if (octoblow) {
                renderOctoblow(context, main, octoblowAge, octoblowHits);
            }

            if (octospansion) {
                renderOctospansion(context, main, octospansionAge, DupliArmsQuirk.getOctospansionArms(instance));
            }
        }

        private static void renderGrownArms(MultiArmsClient.ArmContext context, ManasSkillInstance instance, PartPose[] rest, int arms, float partialTick, float ageInTicks) {
            ModelPart body = context.model().body;
            boolean carrying = DupliArmsQuirk.isCarrying(instance);
            boolean guarding = DupliArmsQuirk.isGuarding(instance);
            int free = DupliArmsQuirk.getFreeArms(instance);
            BlockState mined = instance.isToggled() ? getMinedBlock(context.player()) : null;

            for (int index = 0; index < arms; index++) {
                boolean left = index % 2 == 0;
                float side = left ? 1.0F : -1.0F;
                float[] shoulder = SHOULDERS[index];
                Vector3f pivot = MultiArmsClient.onBody(body, side * shoulder[0] * body.xScale, shoulder[1], shoulder[2]);
                PartPose restPose = left ? rest[1] : rest[0];
                ItemStack stack = instance.isToggled() && index < free ? MultiArms.getExtraOffhand(context.player(), index) : ItemStack.EMPTY;

                float xRot;
                float yRot;
                float zRot;
                if (index >= free && carrying && index < free + 2) {
                    // Reaching back over the shoulders to hold the rider
                    xRot = 2.1F;
                    yRot = body.yRot;
                    zRot = -side * 0.35F;
                } else if (index >= free && guarding) {
                    // Crossed over the front like a shield
                    xRot = left ? -1.45F : -1.2F;
                    yRot = body.yRot + side * 0.65F;
                    zRot = 0.0F;
                } else {
                    // Tools that help break the block being mined dig along, weapons swing on their follow up hits
                    boolean digging = mined != null && stack.getDestroySpeed(mined) > 1.0F;
                    float swing = digging
                            ? ((ageInTicks + index * DIG_TICKS * 0.5F) % DIG_TICKS) / DIG_TICKS
                            : MultiArmsClient.getSwing(context.player(), DupliArmsQuirk.getSwingTime(instance, index), partialTick);
                    xRot = (stack.isEmpty() ? restPose.xRot : restPose.xRot * 0.5F - (float) Math.PI / 10.0F) + 0.2F + MultiArmsClient.getSwingXRot(context.model(), swing);
                    yRot = restPose.yRot + body.yRot;
                    zRot = restPose.zRot - side * TILTS[index] + MultiArmsClient.getSwingZRot(swing);
                }

                MultiArmsClient.renderArm(context, left, PartPose.offsetAndRotation(pivot.x(), pivot.y(), pivot.z(), xRot, yRot, zRot), ARM_WIDTH, 1.0F, stack);
            }
        }

        // Arms branch out of the main arm
        private static void renderOctoblow(MultiArmsClient.ArmContext context, PartPose main, float age, int hits) {
            float grow = Mth.clamp(age / 3.0F, 0.0F, 1.0F) * Mth.clamp((hits + 6.0F - age) / 4.0F, 0.0F, 1.0F);
            if (grow <= 0.0F) {
                return;
            }

            PoseStack poseStack = context.poseStack();
            for (int primary = 0; primary < PRIMARIES; primary++) {
                float fan = primary - (PRIMARIES - 1) * 0.5F;
                float length = grow * (0.75F + 0.5F * getPunch(1 + primary, age, hits));
                PartPose primaryPose = PartPose.rotation(-0.1F + Math.abs(fan) * 0.15F, fan * 0.6F, fan * 0.25F);

                poseStack.pushPose();
                MultiArmsClient.toHand(poseStack, main, 1.0F);
                MultiArmsClient.renderArm(context, false, primaryPose, 0.8F, length, ItemStack.EMPTY);

                for (int secondary = 0; secondary < SECONDARIES; secondary++) {
                    float spread = secondary - (SECONDARIES - 1) * 0.5F;
                    int arm = 1 + PRIMARIES + primary * SECONDARIES + secondary;
                    float childLength = grow * (0.6F + 0.6F * getPunch(arm, age, hits));
                    poseStack.pushPose();
                    MultiArmsClient.toHand(poseStack, primaryPose, length);
                    MultiArmsClient.renderArm(context, false, PartPose.rotation(secondary % 2 == 0 ? -0.25F : 0.25F, spread * 0.35F, 0.0F), 0.7F, childLength, ItemStack.EMPTY);
                    poseStack.popPose();
                }

                poseStack.popPose();
            }
        }

        private static void renderOctospansion(MultiArmsClient.ArmContext context, PartPose main, float age, int arms) {
            float gather = Mth.clamp(age / (DupliArmsQuirk.OCTOSPANSION_WINDUP - 2.0F), 0.0F, 1.0F);
            float thrust = Mth.clamp((age - DupliArmsQuirk.OCTOSPANSION_WINDUP + 2.0F) / 2.0F, 0.0F, 1.0F);
            float fade = Mth.clamp((DupliArmsQuirk.OCTOSPANSION_TICKS - age) / 6.0F, 0.0F, 1.0F);
            float size = (0.35F + 0.65F * gather) * fade;
            if (size <= 0.0F) {
                return;
            }

            float length = size * (1.0F + 0.5F * thrust);
            PoseStack poseStack = context.poseStack();
            poseStack.pushPose();
            MultiArmsClient.transform(poseStack, main, 1.0F, 1.0F);
            poseStack.translate(MultiArmsClient.getArmCenter(false, context.slim()) / 16.0F, 0.0F, 0.0F);

            int inner = Math.min(arms, 7);
            renderRing(context, inner, 3.0F * size, length, 0.0F);
            renderRing(context, arms - inner, 5.8F * size, length * 0.9F, 0.4F);
            poseStack.popPose();
        }

        private static void renderRing(MultiArmsClient.ArmContext context, int count, float radius, float length, float twist) {
            PoseStack poseStack = context.poseStack();
            float center = MultiArmsClient.getArmCenter(false, context.slim());
            for (int i = 0; i < count; i++) {
                float angle = (float) (Math.PI * 2.0D * i / count) + twist;
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotation(angle));
                poseStack.translate(radius / 16.0F, 0.0F, 0.0F);
                MultiArmsClient.renderArm(context, false, PartPose.offsetAndRotation(-center, 0.0F, 0.0F, 0.0F, 0.0F, 0.06F), 1.0F, length, ItemStack.EMPTY);
                poseStack.popPose();
            }
        }

    }

}

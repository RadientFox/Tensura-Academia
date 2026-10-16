package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBarrage;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBarrier;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AirRulesRenderer {

    private static final int FADE = 3;
    private static final int FLASH_TICKS = 6;
    private static final int FORM_TICKS = 7;
    private static final int POP_TICKS = 7;
    private static final float ARM_SCALE = 1.6F;
    private static final double ARM_REST = 0.15D;
    private static final double ARM_REACH = 2.2D;
    private static final double ARM_AIM = 3.5D;
    private static final float LEFT_WIND_SHIFT = 0.5F;
    private static final float OWN_FADE = 0.7F;
    private static final float OWN_BUBBLE_FADE = 0.45F;
    private static final float BUBBLE_U = 0.12F;
    private static final float BUBBLE_V = 0.5F;
    private static final double BUBBLE_EDGE = 0.045D;
    private static final double BUBBLE_CHARGE = 0.9D;
    private static final int EDGE_COLOR = FastColor.ARGB32.color(115, 215, 240, 255);
    private static final int SKIN_COLOR = FastColor.ARGB32.color(80, 215, 240, 255);

    private record Barrage(long start, int arms, int punches) {}

    private static final class Bubble {
        private long start;
        private long flash = Long.MIN_VALUE;
        private float health;
        private AABB box;

        private Bubble(long start, float health, AABB box) {
            this.start = start;
            this.health = health;
            this.box = box;
        }
    }

    private record Pop(long start, AABB box) {}

    private static final Map<Integer, Barrage> BARRAGES = new HashMap<>();
    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
    private static final Map<Integer, Pop> POPS = new HashMap<>();

    private AirRulesRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long time = level.getGameTime();
        for (NewOrderPayloads.BarragePayload barrage : NewOrderPayloads.getClientBarrages()) {
            BARRAGES.put(barrage.userId(), new Barrage(time, barrage.arms(), barrage.punches()));
        }
        NewOrderPayloads.getClientBarrages().clear();
        BARRAGES.values().removeIf(barrage -> time - barrage.start() > NewOrderBarrage.getTicks(barrage.arms(), barrage.punches()) + FADE || barrage.start() > time);

        for (Player player : level.players()) {
            NewOrderBarrier.BarrierHealth health = NewOrderBarrier.getBarrier(player);
            Bubble bubble = BUBBLES.get(player.getId());
            if (health == null) {
                if (bubble != null) {
                    BUBBLES.remove(player.getId());
                    POPS.put(player.getId(), new Pop(time, bubble.box));
                }
                continue;
            }

            AABB box = NewOrderBarrier.getBox(player);
            if (bubble == null) {
                BUBBLES.put(player.getId(), new Bubble(time, health.health(), box));
                POPS.remove(player.getId());
                continue;
            }
            if (health.health() > bubble.health) {
                bubble.start = time;
            } else if (health.health() < bubble.health) {
                bubble.flash = time;
            }
            bubble.health = health.health();
            bubble.box = box;
        }
        BUBBLES.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
        POPS.values().removeIf(pop -> time - pop.start() > POP_TICKS || pop.start() > time);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null || BARRAGES.isEmpty() && BUBBLES.isEmpty() && POPS.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float now = level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        if (!BUBBLES.isEmpty() || !POPS.isEmpty()) {
            RenderType[] types = {AirShapes.core(), AirShapes.wind(now, 0.03F, 0.0F), AirShapes.wind(now, -0.02F, LEFT_WIND_SHIFT), RenderType.breezeWind(AirShapes.WIND, now * 0.045F % 1.0F, now * 0.01F % 1.0F),
                    AirShapes.charge(now)};
            for (int pass = 0; pass < types.length; pass++) {
                VertexConsumer consumer = buffers.getBuffer(types[pass]);
                for (Map.Entry<Integer, Bubble> entry : BUBBLES.entrySet()) {
                    if (level.getEntity(entry.getKey()) instanceof Player player) {
                        Bubble bubble = entry.getValue();
                        float age = now - bubble.start;
                        float form = Mth.clamp(age / FORM_TICKS, 0.0F, 1.0F);
                        float flash = 1.0F - Mth.clamp((now - bubble.flash) / FLASH_TICKS, 0.0F, 1.0F);
                        float grow = (1.0F + 0.6F * (1.0F - form) * (1.0F - form)) * (1.0F + 0.012F * Mth.sin(age * 0.2F)) * (1.0F + 0.06F * flash);
                        float alpha = form * (1.0F + 0.7F * flash) * (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() ? OWN_BUBBLE_FADE : 1.0F);
                        AABB box = NewOrderBarrier.getBox(player).move(player.getPosition(partialTick).subtract(player.position()));
                        renderBubble(consumer, poseStack, box, grow, alpha, pass, camera, now, getLight(player, box.getCenter()));
                    }
                }
                for (Pop pop : POPS.values()) {
                    float burst = Mth.clamp((now - pop.start()) / POP_TICKS, 0.0F, 1.0F);
                    renderBubble(consumer, poseStack, pop.box(), 1.0F + 0.5F * burst, (1.0F - burst) * (1.0F - burst), pass, camera, now, LightTexture.FULL_BRIGHT);
                }
                buffers.endBatch(types[pass]);
            }
        }

        for (Map.Entry<Integer, Barrage> entry : BARRAGES.entrySet()) {
            if (level.getEntity(entry.getKey()) instanceof AbstractClientPlayer player && !player.isInvisible()
                    && minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
                boolean own = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
                RenderType[] types = {RenderType.entityTranslucent(player.getSkin().texture()), RenderType.breezeWind(AirShapes.WIND, now * 0.02F % 1.0F, 0.0F),
                        RenderType.breezeWind(AirShapes.WIND, now * 0.02F % 1.0F, LEFT_WIND_SHIFT)};
                for (int pass = 0; pass < types.length; pass++) {
                    renderArms(buffers.getBuffer(types[pass]), poseStack, renderer.getModel(), player, entry.getValue(), now, partialTick, own, pass);
                    buffers.endBatch(types[pass]);
                }
            }
        }
        poseStack.popPose();
    }

    private static int getLight(Entity entity, Vec3 at) {
        return LevelRenderer.getLightColor(entity.level(), BlockPos.containing(at));
    }

    // pass 0 is the skin on every arm
    // 1 the wind on right arms
    // 2 the wind on left arms
    private static void renderArms(VertexConsumer consumer, PoseStack poseStack, PlayerModel<AbstractClientPlayer> model, Player player, Barrage barrage, float now, float partialTick, boolean own, int pass) {
        float age = now - barrage.start();
        int ticks = NewOrderBarrage.getTicks(barrage.arms(), barrage.punches());
        float scale = player.getScale();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 aim = eye.add(look.scale(ARM_AIM * scale));
        int light = getLight(player, eye);
        for (int arm = 0; arm < barrage.arms(); arm++) {
            boolean rightSide = arm % 2 == 0;
            float into = age - arm * NewOrderBarrage.STAGGER;
            if (pass == 1 && !rightSide || pass == 2 && rightSide || into < 0.0F) {
                continue;
            }

            int punch = (int) (into / NewOrderBarrage.PERIOD);
            float out = punch < barrage.punches() ? Mth.sin(Mth.PI * (into % NewOrderBarrage.PERIOD) / NewOrderBarrage.PERIOD) : 0.0F;
            float alpha = Mth.clamp(Math.min(into / FADE, (ticks + FADE - age) / FADE), 0.0F, 1.0F) * (own ? OWN_FADE : 1.0F);
            if (alpha <= 0.01F) {
                continue;
            }

            Vec3 base = NewOrderBarrage.getArmBase(eye, look, NewOrderBarrage.getArmOffset(arm, barrage.arms()), scale);
            Vec3 forward = aim.subtract(base).normalize();
            Vec3 at = base.add(forward.scale((ARM_REST + out * ARM_REACH) * scale));
            int color = pass == 0 ? SKIN_COLOR : AirShapes.WIND_COLOR;
            drawArm(poseStack, rightSide ? model.rightArm : model.leftArm, rightSide ? model.rightSleeve : model.leftSleeve, at, forward, ARM_SCALE * scale, consumer, light,
                    AirShapes.fade(color, alpha));
        }
    }

    private static void drawArm(PoseStack poseStack, ModelPart arm, ModelPart sleeve, Vec3 at, Vec3 forward, float scale, VertexConsumer consumer, int light, int color) {
        PartPose armPose = arm.storePose();
        PartPose sleevePose = sleeve.storePose();
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) forward.x, (float) forward.y, (float) forward.z));
        poseStack.scale(scale, scale, scale);
        for (ModelPart part : new ModelPart[]{arm, sleeve}) {
            part.setPos(0.0F, 0.0F, 0.0F);
            part.setRotation(0.0F, 0.0F, 0.0F);
            part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, color);
        }
        poseStack.popPose();
        arm.loadPose(armPose);
        sleeve.loadPose(sleevePose);
    }

    // pass 0 is the core and the edges
    // 1 to 3 the wind
    // 4 a wind charge spinning on each corner
    private static void renderBubble(VertexConsumer consumer, PoseStack poseStack, AABB box, float grow, float alpha, int pass, Vec3 camera, float now, int light) {
        PoseStack.Pose pose = poseStack.last();
        if (alpha <= 0.01F) {
            return;
        }

        Vec3 center = box.getCenter();
        double layer = pass == 2 ? 1.04D : pass == 3 ? 0.96D : 1.0D;
        double half = box.getXsize() * 0.5D * grow * layer;
        double halfY = box.getYsize() * 0.5D * grow * layer;
        if (pass == 4) {
            for (int corner = 0; corner < 8; corner++) {
                Vec3 at = center.add((corner & 1) == 0 ? -half : half, (corner & 2) == 0 ? -halfY : halfY, (corner & 4) == 0 ? -half : half);
                AirShapes.charge(poseStack, consumer, at, (float) (half * BUBBLE_CHARGE), now + corner * 7.0F, alpha, light);
            }
            return;
        }
        if (pass == 0) {
            AirShapes.shell(consumer, pose, center, half, halfY, camera, BUBBLE_U, BUBBLE_V, 0.0F, true, AirShapes.fade(AirShapes.CORE_COLOR, alpha * 0.45F), light);
            AirShapes.edges(consumer, pose, center, half, halfY, half * BUBBLE_EDGE, true, AirShapes.fade(EDGE_COLOR, alpha), light);
            return;
        }

        int color = AirShapes.fade(AirShapes.WIND_COLOR, alpha * (pass == 1 ? 1.0F : 0.75F));
        AirShapes.shell(consumer, pose, center, half, halfY, camera, BUBBLE_U, pass == 2 ? BUBBLE_V * 0.85F : pass == 3 ? BUBBLE_V * 1.2F : BUBBLE_V, pass * 0.25F, false, color, light);
        if (pass == 1) {
            AirShapes.edges(consumer, pose, center, half, halfY, half * BUBBLE_EDGE * 1.3D, false, color, light);
        }
    }

    static void clear() {
        BARRAGES.clear();
        BUBBLES.clear();
        POPS.clear();
    }

}

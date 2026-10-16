package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBarrier;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBody;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderGround;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderSelf;
import com.radient.tensuraacadamia.mixin.client.BossHealthOverlayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class NewOrderClient {

    private static final int CALLOUT_TICKS = 40;
    private static final int CALLOUT_FADE = 12;
    private static final float CALLOUT_RISE = 0.6F;
    private static final float CALLOUT_SCALE = 0.025F;
    private static final double WATER_RISE = 0.15D;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final ResourceLocation BODY_LAYER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body");
    private static final ResourceLocation BODY_EDGE_LAYER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body_edge");
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("boss_bar/white_background");
    private static final ResourceLocation BAR_PROGRESS = ResourceLocation.withDefaultNamespace("boss_bar/white_progress");
    private static final int EDGE = 48;
    private static final int EDGE_COLOR = 0xD8F3FF;
    private static final int BAR_WIDTH = 182;
    private static final int BOSS_BAR_STEP = 19;

    private record Callout(Vec3 pos, int entityId, double lift, Component text, int color, long time) {}

    private record Arc(Vec3 from, Vec3 to, long time, int ticks, long seed) {}

    private static final List<Callout> CALLOUTS = new ArrayList<>();
    private static final List<Arc> ARCS = new ArrayList<>();

    private NewOrderClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        long time = level.getGameTime();
        NewOrderPayloads.RulesPayload rules = NewOrderPayloads.takeClientRules();
        if (rules != null && minecraft.player != null) {
            if (rules.open()) {
                minecraft.setScreen(new NewOrderWheelScreen(rules));
            } else if (minecraft.screen instanceof NewOrderWheelScreen screen) {
                screen.update(rules);
            }
        }

        for (NewOrderPayloads.CalloutPayload callout : NewOrderPayloads.getClientCallouts()) {
            Entity followed = callout.entityId() >= 0 ? level.getEntity(callout.entityId()) : null;
            double lift = followed == null ? 0.5D : callout.pos().y - followed.getY() - followed.getBbHeight();
            CALLOUTS.add(new Callout(callout.pos(), callout.entityId(), lift, callout.text(), callout.color(), time));
        }
        NewOrderPayloads.getClientCallouts().clear();
        CALLOUTS.removeIf(callout -> time - callout.time() > CALLOUT_TICKS || callout.time() > time);
        for (NewOrderPayloads.ArcPayload arc : NewOrderPayloads.getClientArcs()) {
            ARCS.add(new Arc(arc.from(), arc.to(), time, arc.ticks(), level.random.nextLong()));
        }
        NewOrderPayloads.getClientArcs().clear();
        ARCS.removeIf(arc -> time - arc.time() > arc.ticks() || arc.time() > time);
        NewOrderGround.updateClientFields(level.players());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        if (player.isInWater() && !player.isShiftKeyDown() && !player.isPassenger() && NewOrderSelf.canWalkOnWater(player)) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.max(motion.y, WATER_RISE), motion.z);
        }
        if ((NewOrderPayloads.getClientHeld() & NewOrderPayloads.HeldPayload.CAGED) != 0) {
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        int held = NewOrderPayloads.getClientHeld();
        Player player = event.getEntity();
        boolean flying = player.getAbilities().flying || player.isFallFlying();
        if ((held & NewOrderPayloads.HeldPayload.CAGED) == 0 && ((held & NewOrderPayloads.HeldPayload.ROOTED) == 0 || flying)) {
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
        player.setSprinting(false);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CALLOUTS.isEmpty() && ARCS.isEmpty() || level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;
        long time = level.getGameTime();
        if (!ARCS.isEmpty()) {
            renderArcs(poseStack, buffers, camera, time, partialTick);
        }
        for (Callout callout : CALLOUTS) {
            float age = time - callout.time() + partialTick;
            float alpha = age < CALLOUT_TICKS - CALLOUT_FADE ? 1.0F : (CALLOUT_TICKS - age) / CALLOUT_FADE;
            int opacity = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
            if (opacity < 8) {
                continue;
            }

            Vec3 at = callout.pos();
            Entity followed = callout.entityId() >= 0 ? level.getEntity(callout.entityId()) : null;
            if (followed != null) {
                at = followed.getPosition(partialTick).add(0.0D, followed.getBbHeight() + callout.lift(), 0.0D);
            }

            float pop = age < 4.0F ? 1.0F + (4.0F - age) * 0.06F : 1.0F;
            float scale = CALLOUT_SCALE * pop;
            Component text = callout.text().copy().withStyle(ChatFormatting.BOLD);
            poseStack.pushPose();
            poseStack.translate(at.x - camera.x, at.y + age / CALLOUT_TICKS * CALLOUT_RISE - camera.y, at.z - camera.z);
            poseStack.mulPose(event.getCamera().rotation());
            poseStack.scale(scale, -scale, scale);
            font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, opacity << 24 | callout.color() & 0xFFFFFF, true, poseStack.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    private static void renderArcs(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera, long time, float partialTick) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        for (Arc arc : ARCS) {
            float age = time - arc.time() + partialTick;
            float fade = 1.0F - Mth.clamp((age - arc.ticks() * 0.5F) / (arc.ticks() * 0.5F), 0.0F, 1.0F);
            if (fade > 0.0F) {
                RandomSource random = RandomSource.create(arc.seed() * 31L + (time - arc.time()));
                drawBolt(consumer, matrix, arc.from().subtract(camera), arc.to().subtract(camera), random, fade, true);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void drawBolt(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, RandomSource random, float fade, boolean main) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1.0E-3D) {
            return;
        }

        Vec3 direction = path.scale(1.0D / length);
        Vec3 side = direction.cross(UP);
        if (side.lengthSqr() < 1.0E-4D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = side.cross(direction).normalize();
        int segments = Math.max(3, (int) (length / 0.6D));
        double sway = Math.min(1.2D, length * 0.12D) * (main ? 1.0D : 0.6D);
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 point = from.add(path.scale(t));
            if (i < segments) {
                double bulge = Math.sin(t * Math.PI) * sway;
                point = point.add(side.scale((random.nextDouble() - 0.5D) * 2.0D * bulge)).add(up.scale((random.nextDouble() - 0.5D) * 2.0D * bulge));
            }

            segment(consumer, matrix, previous, point, side, up, main ? 0.32F : 0.16F, 0.45F, 0.5F, 0.95F, 0.3F * fade);
            segment(consumer, matrix, previous, point, side, up, main ? 0.12F : 0.06F, 0.65F, 0.7F, 1.0F, 0.6F * fade);
            segment(consumer, matrix, previous, point, side, up, main ? 0.04F : 0.02F, 0.95F, 0.95F, 1.0F, 1.0F * fade);
            if (main && i < segments && random.nextFloat() < 0.18F) {
                double reach = length * 0.25D;
                Vec3 fork = point.add(direction.scale(length * 0.15D)).add(side.scale((random.nextDouble() - 0.5D) * reach)).add(up.scale((random.nextDouble() - 0.5D) * reach));
                drawBolt(consumer, matrix, point, fork, random, fade * 0.7F, false);
            }
            previous = point;
        }
    }

    private static void segment(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 side, Vec3 up, float width, float red, float green, float blue, float alpha) {
        for (Vec3 across : new Vec3[]{side.scale(width), up.scale(width)}) {
            Vec3 a1 = a.subtract(across);
            Vec3 a2 = a.add(across);
            Vec3 b1 = b.subtract(across);
            Vec3 b2 = b.add(across);
            vertex(consumer, matrix, a1, red, green, blue, alpha);
            vertex(consumer, matrix, a2, red, green, blue, alpha);
            vertex(consumer, matrix, b2, red, green, blue, alpha);
            vertex(consumer, matrix, b1, red, green, blue, alpha);
            vertex(consumer, matrix, b1, red, green, blue, alpha);
            vertex(consumer, matrix, b2, red, green, blue, alpha);
            vertex(consumer, matrix, a2, red, green, blue, alpha);
            vertex(consumer, matrix, a1, red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 at, float red, float green, float blue, float alpha) {
        consumer.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(red, green, blue, alpha);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, BODY_EDGE_LAYER, NewOrderClient::renderBodyEdge);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, BODY_LAYER, NewOrderClient::renderBodyBar);
    }

    private static void renderBodyBar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        int bosses = ((BossHealthOverlayAccessor) minecraft.gui.getBossOverlay()).tracadamia$getEvents().size();
        int y = 12 + bosses * BOSS_BAR_STEP;
        NewOrderBody.BodyHealth body = NewOrderBody.getBodyHealth(player);
        if (body != null && body.max() > 0.0F) {
            renderBar(graphics, minecraft, Component.translatable("tracadamia.skill.new_order.body_title", Mth.ceil(body.health()), Mth.ceil(body.max())), body.health() / body.max(), y);
            y += BOSS_BAR_STEP;
        }

        NewOrderBarrier.BarrierHealth barrier = NewOrderBarrier.getBarrier(player);
        if (barrier != null && barrier.max() > 0.0F) {
            renderBar(graphics, minecraft, Component.translatable("tracadamia.skill.new_order.barrier_title", Mth.ceil(barrier.health()), Mth.ceil(barrier.max())), barrier.health() / barrier.max(), y);
        }
    }

    private static void renderBar(GuiGraphics graphics, Minecraft minecraft, Component title, float share, int y) {
        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        graphics.drawString(minecraft.font, title, graphics.guiWidth() / 2 - minecraft.font.width(title) / 2, y - 9, 0xD8F3FF);
        graphics.blitSprite(BAR_BACKGROUND, x, y, BAR_WIDTH, 5);
        int filled = Mth.lerpDiscrete(Mth.clamp(share, 0.0F, 1.0F), 0, BAR_WIDTH);
        if (filled > 0) {
            graphics.blitSprite(BAR_PROGRESS, BAR_WIDTH, 5, 0, 0, x, y, filled, 5);
        }
    }

    private static void renderBodyEdge(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }

        float inside = AirCopyRenderer.getInside(player, deltaTracker.getGameTimeDeltaPartialTick(false));
        if (inside < 0.01F) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int strong = Math.round(70 * inside) << 24 | EDGE_COLOR;
        int clear = EDGE_COLOR;
        graphics.fillGradient(0, 0, width, EDGE, strong, clear);
        graphics.fillGradient(0, height - EDGE, width, height, clear, strong);
        for (int x = 0; x < EDGE; x += 2) {
            int alpha = Math.round(70 * inside * (1.0F - (float) x / EDGE));
            graphics.fill(x, 0, x + 2, height, alpha << 24 | EDGE_COLOR);
            graphics.fill(width - x - 2, 0, width - x, height, alpha << 24 | EDGE_COLOR);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NewOrderPayloads.clearClient();
        NewOrderBody.clearClient();
        NewOrderGround.clearClient();
        CALLOUTS.clear();
        AirCopyRenderer.clear();
        AirRulesRenderer.clear();
        AirRestrainRenderer.clear();
        AirImpacts.clear();
        ARCS.clear();
    }

}

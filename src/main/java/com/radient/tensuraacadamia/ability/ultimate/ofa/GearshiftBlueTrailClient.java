package com.radient.tensuraacadamia.ability.ultimate.ofa;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class GearshiftBlueTrailClient {
    private static final Map<Integer, ActiveTrail> ACTIVE = new ConcurrentHashMap<>();
    private static final int MAX_NODES = 64;
    private static final int MAX_GHOSTS = 9;
    private static final int MAX_GHOST_AGE = 44;
    private static final double MAX_GHOST_CHAIN_DISTANCE = 3.25D;
    private static float time;

    private GearshiftBlueTrailClient() {
    }

    public static void enable(int entityId, int duration, int gear) {
        ActiveTrail trail = ACTIVE.computeIfAbsent(entityId, id -> new ActiveTrail());
        trail.life = Math.max(trail.life, duration);
        trail.gear = Mth.clamp(gear, 0, 5);
    }

    public static void disable(int entityId) {
        ACTIVE.remove(entityId);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        time += 0.13F;

        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }

        Iterator<Map.Entry<Integer, ActiveTrail>> iterator = ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveTrail> entry = iterator.next();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            ActiveTrail trail = entry.getValue();

            trail.life--;

            if (trail.life <= 0 || !(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }

            trail.tickNodes();

            for (TrailPart part : TrailPart.values()) {
                ArrayDeque<TrailNode> nodes = trail.nodes.get(part);
                while (nodes.size() > getMaxNodes(trail.gear)) {
                    nodes.removeLast();
                }
            }

            if (trail.gear < 5) {
                trail.ghosts.clear();
                trail.ghostSampleDelay = 0;
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 1, 1, 0);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        for (Map.Entry<Integer, ActiveTrail> entry : ACTIVE.entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());

            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            ActiveTrail trail = entry.getValue();
            sampleEntity(living, trail, partialTick);
            sampleGhost(living, trail, partialTick);
            trimTrail(trail);
            renderTrails(matrix, cam, trail, getGearIntensity(trail.gear), trail.gear);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        renderGhosts(minecraft, poseStack, cam, partialTick);
    }

    private static void sampleEntity(LivingEntity entity, ActiveTrail trail, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        Vec3 base = new Vec3(x, y, z);

        float height = entity.getBbHeight();
        float width = entity.getBbWidth();

        float yaw = (float) Math.toRadians(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()));
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));

        if (forward.lengthSqr() < 0.00001D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }

        forward = forward.normalize();

        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        Vec3 motion = entity.getDeltaMovement();
        Vec3 horizontalMotion = new Vec3(motion.x, 0.0D, motion.z);
        double speed = horizontalMotion.length();
        double speedPush = Mth.clamp((float) speed * 3.35F, 0.0F, 0.48F);
        double gearPower = trail.gear / 5.0D;

        Vec3 movementBack = Vec3.ZERO;

        if (horizontalMotion.lengthSqr() > 0.000001D) {
            movementBack = horizontalMotion.normalize().scale(0.050D + speedPush * 0.135D + gearPower * 0.030D);
        }

        double walk = Math.sin((entity.tickCount + partialTick + time) * 0.74D) * (0.080D + speedPush * 0.27D + gearPower * 0.018D);
        double armSwing = Math.sin((entity.tickCount + partialTick + time) * 0.62D) * (0.024D + speedPush * 0.080D + gearPower * 0.014D);

        Vec3 movementForward = Vec3.ZERO;

        if (horizontalMotion.lengthSqr() > 0.000001D) {
            movementForward = horizontalMotion.normalize().scale(0.018D + speedPush * 0.035D);
        }

        Vec3 chest = base.add(0.0D, height * 0.655D, 0.0D)
                .subtract(forward.scale(0.034D + gearPower * 0.014D))
                .subtract(movementBack)
                .add(movementForward);

        Vec3 hip = base.add(0.0D, height * 0.31D, 0.0D)
                .subtract(forward.scale(0.026D))
                .subtract(movementBack.scale(0.60D))
                .add(movementForward.scale(0.85D));

        double armSide = width * (0.620D + gearPower * 0.030D);
        double armForward = -0.042D + armSwing;
        double armDown = height * 0.118D;

        Vec3 leftArm = chest.subtract(right.scale(armSide))
                .add(forward.scale(armForward))
                .subtract(up.scale(armDown));

        Vec3 rightArm = chest.add(right.scale(armSide))
                .add(forward.scale(-0.042D - armSwing))
                .subtract(up.scale(armDown));

        double legSide = width * (0.292D + gearPower * 0.016D);
        double legForward = walk * (0.54D + gearPower * 0.07D);

        Vec3 leftLeg = hip.subtract(right.scale(legSide))
                .add(forward.scale(legForward + 0.018D))
                .subtract(up.scale(height * 0.245D));

        Vec3 rightLeg = hip.add(right.scale(legSide))
                .add(forward.scale(-legForward + 0.018D))
                .subtract(up.scale(height * 0.245D));

        addNode(trail, TrailPart.LEFT_ARM, leftArm);
        addNode(trail, TrailPart.RIGHT_ARM, rightArm);
        addNode(trail, TrailPart.LEFT_LEG, leftLeg);
        addNode(trail, TrailPart.RIGHT_LEG, rightLeg);
    }

    private static void sampleGhost(LivingEntity entity, ActiveTrail trail, float partialTick) {
        if (trail.gear < 5) {
            return;
        }

        if (trail.ghostSampleDelay > 0) {
            trail.ghostSampleDelay--;
            return;
        }

        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        Vec3 position = new Vec3(x, y, z);

        GhostFrame first = trail.ghosts.peekFirst();

        Vec3 motion = entity.getDeltaMovement();
        Vec3 horizontalMotion = new Vec3(motion.x, 0.0D, motion.z);
        double speed = horizontalMotion.length();
        double neededDistance = speed > 0.55D ? 0.130D : speed > 0.25D ? 0.095D : 0.070D;

        if (first == null || first.position.distanceToSqr(position) > neededDistance * neededDistance) {
            trail.ghosts.addFirst(new GhostFrame(
                    position,
                    Mth.lerp(partialTick, entity.yRotO, entity.getYRot()),
                    Mth.lerp(partialTick, entity.xRotO, entity.getXRot()),
                    Mth.lerp(partialTick, entity.yBodyRotO, entity.yBodyRot),
                    Mth.lerp(partialTick, entity.yHeadRotO, entity.yHeadRot),
                    entity.tickCount,
                    captureWalkAnimation(entity)
            ));

            trail.ghostSampleDelay = speed > 0.55D ? 5 : speed > 0.25D ? 6 : 7;
        }

        trimGhostChain(trail);
    }

    private static void trimGhostChain(ActiveTrail trail) {
        while (trail.ghosts.size() > MAX_GHOSTS) {
            trail.ghosts.removeLast();
        }

        while (getGhostChainDistance(trail.ghosts) > MAX_GHOST_CHAIN_DISTANCE && trail.ghosts.size() > 1) {
            trail.ghosts.removeLast();
        }
    }

    private static double getGhostChainDistance(ArrayDeque<GhostFrame> ghosts) {
        if (ghosts.size() < 2) {
            return 0.0D;
        }

        Iterator<GhostFrame> iterator = ghosts.iterator();
        GhostFrame previous = iterator.next();
        double distance = 0.0D;

        while (iterator.hasNext()) {
            GhostFrame current = iterator.next();
            distance += previous.position.distanceTo(current.position);
            previous = current;
        }

        return distance;
    }

    private static void renderGhosts(Minecraft minecraft, PoseStack poseStack, Vec3 cam, float partialTick) {
        if (minecraft.level == null) {
            return;
        }

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(770, 1, 1, 0);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        for (Map.Entry<Integer, ActiveTrail> entry : ACTIVE.entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());

            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            ActiveTrail trail = entry.getValue();

            if (trail.gear < 5 || trail.ghosts.isEmpty()) {
                continue;
            }

            double oldX = living.getX();
            double oldY = living.getY();
            double oldZ = living.getZ();
            double oldXOld = living.xOld;
            double oldYOld = living.yOld;
            double oldZOld = living.zOld;
            float oldYRot = living.getYRot();
            float oldXRot = living.getXRot();
            float oldYRotO = living.yRotO;
            float oldXRotO = living.xRotO;
            float oldYBodyRot = living.yBodyRot;
            float oldYBodyRotO = living.yBodyRotO;
            float oldYHeadRot = living.yHeadRot;
            float oldYHeadRotO = living.yHeadRotO;
            int oldTickCount = living.tickCount;
            WalkAnimationSnapshot oldWalk = captureWalkAnimation(living);

            int index = 0;

            for (GhostFrame ghost : trail.ghosts) {
                float chain = 1.0F - index / (float) Math.max(1, trail.ghosts.size() - 1);
                float age = 1.0F - ghost.age / (float) MAX_GHOST_AGE;
                float pulse = 0.97F + 0.03F * Mth.sin(time * 1.8F + index * 0.75F);
                float alpha = Mth.clamp((0.36F + chain * 0.64F) * age * pulse * 0.72F, 0.0F, 0.72F);

                if (alpha > 0.020F) {
                    living.setPos(ghost.position.x, ghost.position.y, ghost.position.z);
                    living.xOld = ghost.position.x;
                    living.yOld = ghost.position.y;
                    living.zOld = ghost.position.z;
                    living.setYRot(ghost.yRot);
                    living.setXRot(ghost.xRot);
                    living.yRotO = ghost.yRot;
                    living.xRotO = ghost.xRot;
                    living.yBodyRot = ghost.bodyRot;
                    living.yBodyRotO = ghost.bodyRot;
                    living.yHeadRot = ghost.headRot;
                    living.yHeadRotO = ghost.headRot;
                    living.tickCount = ghost.tickCount;
                    applyWalkAnimation(living, ghost.walkAnimation);

                    double renderX = ghost.position.x - cam.x;
                    double renderY = ghost.position.y - cam.y;
                    double renderZ = ghost.position.z - cam.z;

                    RenderSystem.setShaderColor(
                            0.48F + alpha * 0.42F,
                            0.82F + alpha * 0.18F,
                            1.0F,
                            alpha
                    );

                    dispatcher.render(living, renderX, renderY, renderZ, ghost.yRot, 0.0F, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
                }

                index++;
            }

            living.setPos(oldX, oldY, oldZ);
            living.xOld = oldXOld;
            living.yOld = oldYOld;
            living.zOld = oldZOld;
            living.setYRot(oldYRot);
            living.setXRot(oldXRot);
            living.yRotO = oldYRotO;
            living.xRotO = oldXRotO;
            living.yBodyRot = oldYBodyRot;
            living.yBodyRotO = oldYBodyRotO;
            living.yHeadRot = oldYHeadRot;
            living.yHeadRotO = oldYHeadRotO;
            living.tickCount = oldTickCount;
            applyWalkAnimation(living, oldWalk);
        }

        bufferSource.endBatch();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static WalkAnimationSnapshot captureWalkAnimation(LivingEntity entity) {
        WalkAnimationSnapshot snapshot = new WalkAnimationSnapshot();
        WalkAnimationState state = entity.walkAnimation;

        snapshot.speedOld = getWalkAnimationFloat(state, "speedOld");
        snapshot.speed = getWalkAnimationFloat(state, "speed");
        snapshot.position = getWalkAnimationFloat(state, "position");

        return snapshot;
    }

    private static void applyWalkAnimation(LivingEntity entity, WalkAnimationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        WalkAnimationState state = entity.walkAnimation;
        setWalkAnimationFloat(state, "speedOld", snapshot.speedOld);
        setWalkAnimationFloat(state, "speed", snapshot.speed);
        setWalkAnimationFloat(state, "position", snapshot.position);
    }

    private static float getWalkAnimationFloat(WalkAnimationState state, String fieldName) {
        try {
            Field field = WalkAnimationState.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getFloat(state);
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    private static void setWalkAnimationFloat(WalkAnimationState state, String fieldName, float value) {
        try {
            Field field = WalkAnimationState.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setFloat(state, value);
        } catch (Throwable ignored) {
        }
    }

    private static void trimTrail(ActiveTrail trail) {
        for (TrailPart part : TrailPart.values()) {
            ArrayDeque<TrailNode> nodes = trail.nodes.get(part);

            while (nodes.size() > getMaxNodes(trail.gear)) {
                nodes.removeLast();
            }
        }
    }

    private static void addNode(ActiveTrail trail, TrailPart part, Vec3 position) {
        ArrayDeque<TrailNode> nodes = trail.nodes.get(part);
        TrailNode first = nodes.peekFirst();

        if (first == null || first.position.distanceToSqr(position) > 0.000018D) {
            nodes.addFirst(new TrailNode(position));
        }
    }

    private static void renderTrails(Matrix4f matrix, Vec3 cam, ActiveTrail trail, float intensity, int gear) {
        float gearScale = getGearScale(gear);
        float overdrive = gear == 5 ? 1.08F : 1.0F;

        BufferBuilder outer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean drewOuter = false;

        for (TrailPart part : TrailPart.values()) {
            drewOuter |= addTrailLayer(outer, matrix, cam, trail.nodes.get(part), 0.145F * intensity * gearScale, 0.00F, 0.12F, 1.0F, 0.30F + gear * 0.024F, gear, 0);
        }

        if (drewOuter) {
            BufferUploader.drawWithShader(outer.buildOrThrow());
        }

        BufferBuilder middle = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean drewMiddle = false;

        for (TrailPart part : TrailPart.values()) {
            drewMiddle |= addTrailLayer(middle, matrix, cam, trail.nodes.get(part), 0.088F * intensity * gearScale, 0.03F, 0.48F, 1.0F, 0.52F + gear * 0.034F, gear, 1);
        }

        if (drewMiddle) {
            BufferUploader.drawWithShader(middle.buildOrThrow());
        }

        BufferBuilder core = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean drewCore = false;

        for (TrailPart part : TrailPart.values()) {
            drewCore |= addTrailLayer(core, matrix, cam, trail.nodes.get(part), 0.037F * intensity * overdrive, 0.70F, 0.98F, 1.0F, 0.88F + gear * 0.038F, gear, 2);
        }

        if (drewCore) {
            BufferUploader.drawWithShader(core.buildOrThrow());
        }

        if (gear >= 2) {
            BufferBuilder streaks = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            boolean drewStreaks = false;

            for (TrailPart part : TrailPart.values()) {
                drewStreaks |= addTrailStreaks(streaks, matrix, cam, trail.nodes.get(part), intensity, gear);
            }

            if (drewStreaks) {
                BufferUploader.drawWithShader(streaks.buildOrThrow());
            }
        }
    }

    private static boolean addTrailLayer(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, ArrayDeque<TrailNode> nodes, float width, float red, float green, float blue, float alphaScale, int gear, int layer) {
        if (nodes.size() < 2) {
            return false;
        }

        List<TrailNode> list = new ArrayList<>(nodes);
        boolean drew = false;
        int size = list.size();
        int maxAge = getMaxAge(gear);
        int smoothness = getSmoothness(gear);

        for (int i = 1; i < size; i++) {
            TrailNode p0 = list.get(Math.max(0, i - 2));
            TrailNode p1 = list.get(i - 1);
            TrailNode p2 = list.get(i);
            TrailNode p3 = list.get(Math.min(size - 1, i + 1));

            Vec3 last = p1.position;

            for (int s = 1; s <= smoothness; s++) {
                float step = s / (float) smoothness;
                float progress = (i - 1 + step) / (float) Math.max(1, size - 1);
                float chain = 1.0F - progress;
                float nodeAge = Mth.lerp(step, p1.age, p2.age);
                float age = 1.0F - nodeAge / (float) maxAge;
                float speed = 4.8F + gear * 1.55F;
                float gearPulse = 0.90F + 0.10F * Mth.sin(time * (8.8F + gear * 1.85F) + progress * 11.0F + layer);
                float ripple = 0.90F + 0.10F * Mth.sin(time * speed + progress * 8.0F + layer * 1.7F);
                float alpha = Mth.clamp(chain * age * alphaScale * gearPulse, 0.0F, 1.0F);
                float localWidth = width * (0.30F + chain * 0.70F) * ripple;

                if (layer == 2) {
                    localWidth *= gear >= 4 ? 0.86F : 0.82F;
                    alpha *= gear >= 4 ? 1.12F : 1.08F;
                }

                if (gear == 0) {
                    alpha *= 1.04F;
                    localWidth *= 1.10F;
                }

                if (gear == 1) {
                    alpha *= 1.08F;
                    localWidth *= 1.08F;
                }

                if (gear == 2) {
                    alpha *= 1.10F;
                    localWidth *= 1.06F;
                }

                if (gear == 4) {
                    alpha *= 1.08F;
                    localWidth *= 0.90F;
                }

                if (gear == 5) {
                    alpha *= 1.12F;
                    localWidth *= 0.84F;
                }

                Vec3 current = catmullRom(p0.position, p1.position, p2.position, p3.position, step);

                if (alpha > 0.001F && localWidth > 0.001F) {
                    drew |= addRibbon(buffer, matrix, cam, last, current, localWidth, red, green, blue, alpha);
                }

                last = current;
            }
        }

        return drew;
    }

    private static boolean addTrailStreaks(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, ArrayDeque<TrailNode> nodes, float intensity, int gear) {
        if (nodes.size() < 4) {
            return false;
        }

        List<TrailNode> list = new ArrayList<>(nodes);
        boolean drew = false;
        int maxAge = getMaxAge(gear);
        int size = list.size();

        for (int i = 1; i < size; i += 2) {
            TrailNode previous = list.get(i - 1);
            TrailNode current = list.get(i);
            float flash = hash(Mth.floor(time * (8.5F + gear * 1.7F)) + i * 13.37F + gear * 9.0F);

            if (gear >= 5 || flash > 0.36F) {
                float chain = 1.0F - i / (float) Math.max(1, size - 1);
                float age = 1.0F - current.age / (float) maxAge;
                float alpha = chain * age * intensity * (gear >= 5 ? 0.58F : gear >= 4 ? 0.46F : gear >= 3 ? 0.36F : 0.22F) * (0.45F + flash * 0.55F);
                float width = (gear >= 5 ? 0.014F : gear >= 4 ? 0.012F : gear >= 3 ? 0.010F : 0.008F) * intensity;

                drew |= addRibbon(buffer, matrix, cam, previous.position, current.position, width, 0.82F, 0.98F, 1.0F, alpha);
            }
        }

        return drew;
    }

    private static boolean addRibbon(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float blue, float alpha) {
        Vec3 dir = b.subtract(a);

        if (dir.lengthSqr() < 0.0000004D) {
            return false;
        }

        Vec3 mid = a.add(b).scale(0.5D);
        Vec3 toCam = cam.subtract(mid);

        if (toCam.lengthSqr() < 0.000001D) {
            return false;
        }

        Vec3 side = dir.normalize().cross(toCam.normalize());

        if (side.lengthSqr() < 0.000001D) {
            side = new Vec3(0.0D, 1.0D, 0.0D);
        }

        side = side.normalize().scale(width);

        Vec3 a0 = a.subtract(side).subtract(cam);
        Vec3 a1 = a.add(side).subtract(cam);
        Vec3 b1 = b.add(side).subtract(cam);
        Vec3 b0 = b.subtract(side).subtract(cam);

        buffer.addVertex(matrix, (float) a0.x, (float) a0.y, (float) a0.z).setColor(r, g, blue, alpha);
        buffer.addVertex(matrix, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, blue, alpha);
        buffer.addVertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).setColor(r, g, blue, alpha * 0.64F);
        buffer.addVertex(matrix, (float) b0.x, (float) b0.y, (float) b0.z).setColor(r, g, blue, alpha * 0.64F);

        return true;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5D * ((2.0D * p1.x) + (-p0.x + p2.x) * t + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2 + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3);
        double y = 0.5D * ((2.0D * p1.y) + (-p0.y + p2.y) * t + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2 + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3);
        double z = 0.5D * ((2.0D * p1.z) + (-p0.z + p2.z) * t + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2 + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3);

        return new Vec3(x, y, z);
    }

    private static float getGearIntensity(int gear) {
        return switch (gear) {
            case 0 -> 0.72F;
            case 1 -> 0.90F;
            case 2 -> 1.10F;
            case 3 -> 1.22F;
            case 4 -> 1.34F;
            case 5 -> 1.46F;
            default -> 0.90F;
        };
    }

    private static float getGearScale(int gear) {
        return switch (gear) {
            case 0 -> 0.92F;
            case 1 -> 1.00F;
            case 2 -> 1.08F;
            case 3 -> 1.13F;
            case 4 -> 1.10F;
            case 5 -> 1.04F;
            default -> 1.0F;
        };
    }

    private static int getMaxAge(int gear) {
        return switch (gear) {
            case 0 -> 24;
            case 1 -> 28;
            case 2 -> 32;
            case 3 -> 36;
            case 4 -> 40;
            case 5 -> 44;
            default -> 28;
        };
    }

    private static int getMaxNodes(int gear) {
        return switch (gear) {
            case 0 -> 34;
            case 1 -> 40;
            case 2 -> 46;
            case 3 -> 52;
            case 4 -> 58;
            case 5 -> 64;
            default -> MAX_NODES;
        };
    }

    private static int getSmoothness(int gear) {
        return switch (gear) {
            case 0 -> 3;
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 4;
            case 4 -> 5;
            case 5 -> 6;
            default -> 3;
        };
    }

    private static float hash(float value) {
        return Mth.frac(Mth.sin(value * 12.9898F) * 43758.5453F);
    }

    private enum TrailPart {
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG
    }

    private static final class ActiveTrail {
        private final EnumMap<TrailPart, ArrayDeque<TrailNode>> nodes = new EnumMap<>(TrailPart.class);
        private final ArrayDeque<GhostFrame> ghosts = new ArrayDeque<>();
        private int life;
        private int gear;
        private int ghostSampleDelay;

        private ActiveTrail() {
            for (TrailPart part : TrailPart.values()) {
                nodes.put(part, new ArrayDeque<>());
            }
        }

        private void tickNodes() {
            for (ArrayDeque<TrailNode> list : nodes.values()) {
                Iterator<TrailNode> iterator = list.iterator();

                while (iterator.hasNext()) {
                    TrailNode node = iterator.next();

                    if (node.age > getMaxAge(gear)) {
                        iterator.remove();
                    } else {
                        node.age++;
                    }
                }
            }

            Iterator<GhostFrame> iterator = ghosts.iterator();

            while (iterator.hasNext()) {
                GhostFrame ghost = iterator.next();

                if (ghost.age > MAX_GHOST_AGE) {
                    iterator.remove();
                } else {
                    ghost.age++;
                }
            }
        }
    }

    private static final class TrailNode {
        private final Vec3 position;
        private int age;

        private TrailNode(Vec3 position) {
            this.position = position;
        }
    }

    private static final class GhostFrame {
        private final Vec3 position;
        private final float yRot;
        private final float xRot;
        private final float bodyRot;
        private final float headRot;
        private final int tickCount;
        private final WalkAnimationSnapshot walkAnimation;
        private int age;

        private GhostFrame(Vec3 position, float yRot, float xRot, float bodyRot, float headRot, int tickCount, WalkAnimationSnapshot walkAnimation) {
            this.position = position;
            this.yRot = yRot;
            this.xRot = xRot;
            this.bodyRot = bodyRot;
            this.headRot = headRot;
            this.tickCount = tickCount;
            this.walkAnimation = walkAnimation;
        }
    }

    private static final class WalkAnimationSnapshot {
        private float speedOld;
        private float speed;
        private float position;
    }
}

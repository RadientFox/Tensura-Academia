package com.radient.tensuraacadamia.ability.ultimate.ofa;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FullCowlLightningClient {
    private static final Map<Integer, ActiveFullCowl> ACTIVE = new ConcurrentHashMap<>();
    private static float time;

    private FullCowlLightningClient() {
    }

    public static void enable(int entityId, int duration, int percent, boolean instantLightning, boolean hundredPercentUnlocked) {
        ActiveFullCowl cowl = ACTIVE.computeIfAbsent(entityId, id -> new ActiveFullCowl());
        int visualDuration = Math.max(1, duration);
        cowl.life = visualDuration;
        cowl.maxLife = visualDuration;
        cowl.percent = Mth.clamp(percent, 1, 100);
        cowl.age = 0;
        cowl.seed = hash(entityId * 932.77F + duration * 0.13F + percent * 7.13F + time * 11.0F);
    }

    public static void disable(int entityId) {
        ACTIVE.remove(entityId);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        time += 0.12F;

        if (minecraft.level == null) {
            ACTIVE.clear();
            return;
        }

        Iterator<Map.Entry<Integer, ActiveFullCowl>> iterator = ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveFullCowl> entry = iterator.next();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            ActiveFullCowl cowl = entry.getValue();

            cowl.life--;
            cowl.age++;

            if (cowl.life <= 0 || !(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
            }
        }
    }

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

        for (Map.Entry<Integer, ActiveFullCowl> entry : ACTIVE.entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());

            if (entity instanceof LivingEntity living && living.isAlive()) {
                renderFullCowl(matrix, cam, living, entry.getValue(), partialTick);
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderFullCowl(Matrix4f matrix, Vec3 cam, LivingEntity entity, ActiveFullCowl cowl, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        Vec3 base = new Vec3(x, y, z);

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
        float headPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        Rig rig = buildRig(entity, base, bodyYaw, headYaw, headPitch, partialTick);
        float progress = 1.0F - cowl.life / (float) Math.max(1, cowl.maxLife);
        float grow = Mth.clamp(progress * 1.45F, 0.0F, 1.0F);
        float fade = cowl.life <= 8 ? cowl.life / 8.0F : 1.0F;
        float hold = Mth.clamp(fade, 0.0F, 1.0F);

        renderRedWeb(matrix, cam, rig, cowl, grow, hold, true);
        renderRedWeb(matrix, cam, rig, cowl, grow, hold, false);
    }

    private static void renderRedWeb(Matrix4f matrix, Vec3 cam, Rig rig, ActiveFullCowl cowl, float grow, float hold, boolean glow) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean drew = false;

        float power = getVisualPower(cowl.percent);
        float baseWidth = glow ? 0.023F + power * 0.007F : 0.0048F + power * 0.0019F;
        double lift = glow ? 0.026D : 0.017D;

        drew |= renderPartWeb(buffer, matrix, cam, rig.head, cowl, PartId.HEAD, 17, grow, hold, glow, baseWidth * 0.88F, lift);
        drew |= renderPartWeb(buffer, matrix, cam, rig.torso, cowl, PartId.TORSO, 34, grow, hold, glow, baseWidth * 1.08F, lift);
        drew |= renderPartWeb(buffer, matrix, cam, rig.leftArm, cowl, PartId.LEFT_ARM, 18, grow, hold, glow, baseWidth * 0.82F, lift);
        drew |= renderPartWeb(buffer, matrix, cam, rig.rightArm, cowl, PartId.RIGHT_ARM, 18, grow, hold, glow, baseWidth * 0.82F, lift);
        drew |= renderPartWeb(buffer, matrix, cam, rig.leftLeg, cowl, PartId.LEFT_LEG, 20, grow, hold, glow, baseWidth * 0.90F, lift);
        drew |= renderPartWeb(buffer, matrix, cam, rig.rightLeg, cowl, PartId.RIGHT_LEG, 20, grow, hold, glow, baseWidth * 0.90F, lift);
        drew |= renderWebConnectors(buffer, matrix, cam, rig, cowl, grow, hold, glow, baseWidth, lift);

        drawBuiltBuffer(buffer, drew);
    }

    private static boolean renderPartWeb(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, PartPose pose, ActiveFullCowl cowl, PartId part, int count, float grow, float hold, boolean glow, float width, double lift) {
        boolean drew = false;

        for (int i = 0; i < count; i++) {
            float seed = hash(cowl.seed * 137.0F + part.ordinal() * 71.0F + i * 19.0F);
            float order = getPartOrder(part) + hash(seed * 43.0F + i) * 0.42F;
            float appear = Mth.clamp(grow * 1.72F - order * 0.78F, 0.0F, 1.0F);

            if (appear <= 0.0F) {
                continue;
            }

            SurfaceAnchor a = randomSurfaceAnchor(seed, i, false);
            SurfaceAnchor b = webTargetAnchor(a, seed, i, part);
            ResolvedAnchor ra = resolveAnchor(pose, a);
            ResolvedAnchor rb = resolveAnchor(pose, b);
            Vec3 grownEnd = ra.position.lerp(rb.position, easeOut(appear));
            ResolvedAnchor grown = new ResolvedAnchor(grownEnd, ra.normal.lerp(rb.normal, appear).normalize());

            float pulse = 0.72F + 0.28F * hash(seed * 89.0F + time * 8.0F);
            float alpha = Mth.clamp(appear * hold * pulse * (glow ? 0.34F : 1.0F), 0.0F, glow ? 0.42F : 1.0F);
            float lineWidth = width * (0.66F + hash(seed * 109.0F) * 0.72F);

            if (glow) {
                drew |= addAttachedWebArc(buffer, matrix, cam, ra, grown, 5, lineWidth, 1.0F, 0.012F, 0.008F, alpha, seed, lift);
            } else {
                drew |= addAttachedWebArc(buffer, matrix, cam, ra, grown, 5, lineWidth, 1.0F, 0.046F, 0.020F, alpha, seed + 5.0F, lift);
            }

            if (i % 3 == 0) {
                SurfaceAnchor branch = branchAnchor(a, b, seed, i);
                ResolvedAnchor rc = resolveAnchor(pose, branch);
                Vec3 branchEnd = ra.position.lerp(rc.position, easeOut(appear * 0.86F));
                ResolvedAnchor grownBranch = new ResolvedAnchor(branchEnd, ra.normal.lerp(rc.normal, appear).normalize());
                float branchAlpha = alpha * (glow ? 0.82F : 0.72F);
                float branchWidth = lineWidth * 0.58F;

                if (glow) {
                    drew |= addAttachedWebArc(buffer, matrix, cam, ra, grownBranch, 4, branchWidth, 1.0F, 0.010F, 0.007F, branchAlpha, seed + 33.0F, lift);
                } else {
                    drew |= addAttachedWebArc(buffer, matrix, cam, ra, grownBranch, 4, branchWidth, 1.0F, 0.040F, 0.018F, branchAlpha, seed + 39.0F, lift);
                }
            }
        }

        return drew;
    }

    private static boolean renderWebConnectors(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, Rig rig, ActiveFullCowl cowl, float grow, float hold, boolean glow, float width, double lift) {
        boolean drew = false;
        drew |= addCrossWeb(buffer, matrix, cam, rig.head, s(0.00D, -1.00D, 0.82D), rig.torso, s(0.00D, 1.00D, 0.90D), 0.07F, grow, hold, glow, width, lift, cowl.seed + 201.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(-1.00D, 0.78D, 0.54D), rig.leftArm, s(1.00D, 0.90D, 0.72D), 0.14F, grow, hold, glow, width * 0.82F, lift, cowl.seed + 202.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(1.00D, 0.78D, 0.54D), rig.rightArm, s(-1.00D, 0.90D, 0.72D), 0.14F, grow, hold, glow, width * 0.82F, lift, cowl.seed + 203.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(-0.42D, -1.00D, 0.78D), rig.leftLeg, s(0.00D, 1.00D, 0.94D), 0.22F, grow, hold, glow, width * 0.88F, lift, cowl.seed + 204.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(0.42D, -1.00D, 0.78D), rig.rightLeg, s(0.00D, 1.00D, 0.94D), 0.22F, grow, hold, glow, width * 0.88F, lift, cowl.seed + 205.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(-1.00D, 0.72D, -0.58D), rig.leftArm, s(1.00D, 0.72D, -0.88D), 0.32F, grow, hold, glow, width * 0.68F, lift, cowl.seed + 206.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(1.00D, 0.72D, -0.58D), rig.rightArm, s(-1.00D, 0.72D, -0.88D), 0.32F, grow, hold, glow, width * 0.68F, lift, cowl.seed + 207.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(-0.36D, -1.00D, -0.78D), rig.leftLeg, s(0.00D, 1.00D, -0.94D), 0.42F, grow, hold, glow, width * 0.66F, lift, cowl.seed + 208.0F);
        drew |= addCrossWeb(buffer, matrix, cam, rig.torso, s(0.36D, -1.00D, -0.78D), rig.rightLeg, s(0.00D, 1.00D, -0.94D), 0.42F, grow, hold, glow, width * 0.66F, lift, cowl.seed + 209.0F);
        return drew;
    }

    private static boolean addCrossWeb(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, PartPose poseA, SurfaceAnchor from, PartPose poseB, SurfaceAnchor to, float order, float grow, float hold, boolean glow, float width, double lift, float seed) {
        float appear = Mth.clamp(grow * 1.64F - order * 0.72F, 0.0F, 1.0F);

        if (appear <= 0.0F) {
            return false;
        }

        ResolvedAnchor a = resolveAnchor(poseA, from);
        ResolvedAnchor b = resolveAnchor(poseB, to);
        Vec3 grownEnd = a.position.lerp(b.position, easeOut(appear));
        ResolvedAnchor grown = new ResolvedAnchor(grownEnd, a.normal.lerp(b.normal, appear).normalize());
        float pulse = 0.78F + hash(seed * 17.0F + time * 7.0F) * 0.22F;
        float alpha = Mth.clamp(appear * hold * pulse * (glow ? 0.30F : 0.92F), 0.0F, glow ? 0.36F : 0.96F);

        if (glow) {
            return addAttachedWebArc(buffer, matrix, cam, a, grown, 6, width, 1.0F, 0.010F, 0.007F, alpha, seed, lift);
        }

        return addAttachedWebArc(buffer, matrix, cam, a, grown, 6, width, 1.0F, 0.046F, 0.020F, alpha, seed + 5.0F, lift);
    }

    private static Rig buildRig(LivingEntity entity, Vec3 base, float bodyYaw, float headYaw, float headPitch, float partialTick) {
        float height = entity.getBbHeight();
        float width = entity.getBbWidth();

        double legHeight = height * 0.43D;
        double torsoHeight = height * 0.35D;
        double headHeight = height * 0.23D;
        double armHeight = height * 0.35D;

        double torsoWidth = width * 0.94D;
        double torsoDepth = width * 0.64D;
        double headWidth = width * 0.82D;
        double headDepth = width * 0.82D;
        double armWidth = width * 0.36D;
        double armDepth = width * 0.36D;
        double legWidth = width * 0.36D;
        double legDepth = width * 0.36D;

        float walkPos = entity.walkAnimation.position(partialTick);
        float walkSpeed = entity.walkAnimation.speed(partialTick);
        float walkAmount = Mth.clamp(walkSpeed * 1.6F, 0.0F, 1.0F);

        float armSwing = Mth.sin(walkPos * 0.6662F) * 30.0F * walkAmount;
        float legSwing = Mth.sin(walkPos * 0.6662F) * 28.0F * walkAmount;

        Basis torsoBasis = getBasis(bodyYaw);
        Basis headBasis = rotateAroundRight(getBasis(headYaw), headPitch * 0.75F);
        Basis leftArmBasis = rotateAroundRight(torsoBasis, -armSwing);
        Basis rightArmBasis = rotateAroundRight(torsoBasis, armSwing);
        Basis leftLegBasis = rotateAroundRight(torsoBasis, legSwing);
        Basis rightLegBasis = rotateAroundRight(torsoBasis, -legSwing);

        Vec3 torsoCenter = base.add(0.0D, legHeight + torsoHeight * 0.5D, 0.0D);
        Vec3 headCenter = torsoCenter.add(torsoBasis.up.scale(torsoHeight * 0.58D + headHeight * 0.56D));

        Vec3 leftArmCenter = torsoCenter.add(torsoBasis.right.scale(-(torsoWidth * 0.5D + armWidth * 0.58D))).add(torsoBasis.up.scale(torsoHeight * 0.22D));
        Vec3 rightArmCenter = torsoCenter.add(torsoBasis.right.scale(torsoWidth * 0.5D + armWidth * 0.58D)).add(torsoBasis.up.scale(torsoHeight * 0.22D));
        Vec3 leftLegCenter = base.add(torsoBasis.right.scale(-legWidth * 0.72D)).add(0.0D, legHeight * 0.5D, 0.0D);
        Vec3 rightLegCenter = base.add(torsoBasis.right.scale(legWidth * 0.72D)).add(0.0D, legHeight * 0.5D, 0.0D);

        return new Rig(
                new PartPose(torsoCenter, torsoBasis, torsoWidth * 0.5D, torsoHeight * 0.5D, torsoDepth * 0.5D),
                new PartPose(headCenter, headBasis, headWidth * 0.5D, headHeight * 0.5D, headDepth * 0.5D),
                new PartPose(leftArmCenter, leftArmBasis, armWidth * 0.5D, armHeight * 0.5D, armDepth * 0.5D),
                new PartPose(rightArmCenter, rightArmBasis, armWidth * 0.5D, armHeight * 0.5D, armDepth * 0.5D),
                new PartPose(leftLegCenter, leftLegBasis, legWidth * 0.5D, legHeight * 0.5D, legDepth * 0.5D),
                new PartPose(rightLegCenter, rightLegBasis, legWidth * 0.5D, legHeight * 0.5D, legDepth * 0.5D)
        );
    }

    private static ResolvedAnchor resolveAnchor(PartPose pose, SurfaceAnchor anchor) {
        Vec3 pos = pose.origin.add(pose.basis.right.scale(anchor.x * pose.halfX)).add(pose.basis.up.scale(anchor.y * pose.halfY)).add(pose.basis.forward.scale(anchor.z * pose.halfZ));

        double ax = Math.abs(anchor.x);
        double ay = Math.abs(anchor.y);
        double az = Math.abs(anchor.z);

        Vec3 normal;

        if (ax >= ay && ax >= az) {
            normal = pose.basis.right.scale(Math.signum(anchor.x));
        } else if (ay >= ax && ay >= az) {
            normal = pose.basis.up.scale(Math.signum(anchor.y));
        } else {
            normal = pose.basis.forward.scale(Math.signum(anchor.z));
        }

        if (normal.lengthSqr() < 0.000001D) {
            normal = pose.basis.forward;
        }

        return new ResolvedAnchor(pos, normal.normalize());
    }

    private static boolean addAttachedWebArc(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, ResolvedAnchor a, ResolvedAnchor b, int bends, float width, float r, float g, float blue, float alpha, float seed, double lift) {
        if (alpha <= 0.001F || width <= 0.001F) {
            return false;
        }

        Vec3 startPos = a.position.add(a.normal.scale(lift));
        Vec3 endPos = b.position.add(b.normal.scale(lift));
        Vec3 main = endPos.subtract(startPos);

        if (main.lengthSqr() < 0.000001D) {
            return false;
        }

        boolean drew = false;
        Vec3 previous = startPos;

        for (int i = 1; i <= bends; i++) {
            float t = i / (float) bends;
            Vec3 pos = a.position.lerp(b.position, t);
            Vec3 normal = a.normal.lerp(b.normal, t);

            if (normal.lengthSqr() < 0.000001D) {
                normal = a.normal;
            }

            normal = normal.normalize();

            Vec3 tangent = main.normalize().cross(normal);

            if (tangent.lengthSqr() < 0.000001D) {
                tangent = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
            }

            if (tangent.lengthSqr() < 0.000001D) {
                tangent = new Vec3(1.0D, 0.0D, 0.0D);
            }

            tangent = tangent.normalize();

            if (i < bends) {
                float strength = Mth.sin(t * (float) Math.PI);
                double tangentOffset = (hash(seed * 17.0F + i * 31.0F + time * 1.4F) - 0.5D) * 0.045D * strength;
                double liftOffset = (hash(seed * 23.0F + i * 37.0F + time * 2.0F) - 0.5D) * 0.010D * strength;
                double crawl = Math.sin(time * 3.4D + seed * 13.0D + i) * 0.010D * strength;
                pos = pos.add(tangent.scale(tangentOffset + crawl)).add(normal.scale(lift + liftOffset));
            } else {
                pos = pos.add(normal.scale(lift));
            }

            drew |= addRibbon(buffer, matrix, cam, previous, pos, width * (1.0F - t * 0.06F), r, g, blue, alpha * (1.0F - t * 0.08F));
            previous = pos;
        }

        return drew;
    }

    private static boolean addRibbon(BufferBuilder buffer, Matrix4f matrix, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float blue, float alpha) {
        if (alpha <= 0.001F || width <= 0.001F) {
            return false;
        }

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
        buffer.addVertex(matrix, (float) b1.x, (float) b1.y, (float) b1.z).setColor(r, g, blue, alpha * 0.76F);
        buffer.addVertex(matrix, (float) b0.x, (float) b0.y, (float) b0.z).setColor(r, g, blue, alpha * 0.76F);

        return true;
    }

    private static SurfaceAnchor randomSurfaceAnchor(float seed, int index, boolean alternate) {
        int face = Math.floorMod(Mth.floor(hash(seed * 11.0F + index * 3.0F + (alternate ? 7.0F : 0.0F)) * 6.0F), 6);
        double u = hash(seed * 17.0F + index * 5.0F) * 1.84D - 0.92D;
        double v = hash(seed * 23.0F + index * 7.0F) * 1.84D - 0.92D;

        return anchorFromFace(face, u, v);
    }

    private static SurfaceAnchor webTargetAnchor(SurfaceAnchor from, float seed, int index, PartId part) {
        int face = faceOf(from);
        int nextFace = face;

        if (hash(seed * 31.0F + index) > 0.52F) {
            nextFace = Math.floorMod(face + (hash(seed * 37.0F) > 0.5F ? 1 : -1), 6);
        }

        double u = coordinateU(from, face) + (hash(seed * 41.0F + index) - 0.5D) * (part == PartId.TORSO ? 1.35D : 1.15D);
        double v = coordinateV(from, face) + (hash(seed * 43.0F + index) - 0.5D) * (part == PartId.TORSO ? 1.35D : 1.20D);

        u = Mth.clamp((float) u, -0.96F, 0.96F);
        v = Mth.clamp((float) v, -0.96F, 0.96F);

        return anchorFromFace(nextFace, u, v);
    }

    private static SurfaceAnchor branchAnchor(SurfaceAnchor a, SurfaceAnchor b, float seed, int index) {
        int face = hash(seed * 53.0F + index) > 0.38F ? faceOf(a) : faceOf(b);
        double u = (coordinateU(a, faceOf(a)) + coordinateU(b, faceOf(b))) * 0.5D + (hash(seed * 59.0F) - 0.5D) * 0.92D;
        double v = (coordinateV(a, faceOf(a)) + coordinateV(b, faceOf(b))) * 0.5D + (hash(seed * 61.0F) - 0.5D) * 0.92D;

        u = Mth.clamp((float) u, -0.96F, 0.96F);
        v = Mth.clamp((float) v, -0.96F, 0.96F);

        return anchorFromFace(face, u, v);
    }

    private static int faceOf(SurfaceAnchor anchor) {
        double ax = Math.abs(anchor.x);
        double ay = Math.abs(anchor.y);
        double az = Math.abs(anchor.z);

        if (az >= ax && az >= ay) {
            return anchor.z >= 0.0D ? 0 : 1;
        }

        if (ax >= ay) {
            return anchor.x >= 0.0D ? 2 : 3;
        }

        return anchor.y >= 0.0D ? 4 : 5;
    }

    private static double coordinateU(SurfaceAnchor anchor, int face) {
        return switch (face) {
            case 0, 1, 4, 5 -> anchor.x;
            case 2, 3 -> anchor.z;
            default -> anchor.x;
        };
    }

    private static double coordinateV(SurfaceAnchor anchor, int face) {
        return switch (face) {
            case 0, 1, 2, 3 -> anchor.y;
            case 4, 5 -> anchor.z;
            default -> anchor.y;
        };
    }

    private static SurfaceAnchor anchorFromFace(int face, double u, double v) {
        return switch (face) {
            case 0 -> s(u, v, 1.0D);
            case 1 -> s(u, v, -1.0D);
            case 2 -> s(1.0D, v, u);
            case 3 -> s(-1.0D, v, u);
            case 4 -> s(u, 1.0D, v);
            default -> s(u, -1.0D, v);
        };
    }

    private static SurfaceAnchor s(double x, double y, double z) {
        return new SurfaceAnchor(x, y, z);
    }

    private static Basis getBasis(float yawDegrees) {
        float yaw = (float) Math.toRadians(yawDegrees);
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));

        if (forward.lengthSqr() < 0.00001D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }

        forward = forward.normalize();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x).normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        return new Basis(right, up, forward);
    }

    private static Basis rotateAroundRight(Basis basis, float pitchDegrees) {
        float pitch = (float) Math.toRadians(pitchDegrees);
        float cos = Mth.cos(pitch);
        float sin = Mth.sin(pitch);

        Vec3 up = basis.up.scale(cos).add(basis.forward.scale(sin));
        Vec3 forward = basis.forward.scale(cos).subtract(basis.up.scale(sin));

        if (up.lengthSqr() < 0.000001D) {
            up = new Vec3(0.0D, 1.0D, 0.0D);
        }

        if (forward.lengthSqr() < 0.000001D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        }

        return new Basis(basis.right.normalize(), up.normalize(), forward.normalize());
    }

    private static void drawBuiltBuffer(BufferBuilder buffer, boolean drewAny) {
        if (drewAny) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
    }

    private static float getPartOrder(PartId part) {
        return switch (part) {
            case HEAD -> 0.00F;
            case TORSO -> 0.08F;
            case LEFT_ARM, RIGHT_ARM -> 0.18F;
            case LEFT_LEG, RIGHT_LEG -> 0.26F;
        };
    }

    private static float getVisualPower(int percent) {
        float normalized = Mth.clamp(percent / 100.0F, 0.01F, 1.0F);
        return 0.20F + Mth.sqrt(normalized) * 0.80F;
    }

    private static float easeOut(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - value) * (1.0F - value);
    }

    private static float hash(float value) {
        return Mth.frac(Mth.sin(value * 12.9898F) * 43758.5453F);
    }

    private enum PartId {
        HEAD,
        TORSO,
        LEFT_ARM,
        RIGHT_ARM,
        LEFT_LEG,
        RIGHT_LEG
    }

    private record Basis(Vec3 right, Vec3 up, Vec3 forward) {
    }

    private record SurfaceAnchor(double x, double y, double z) {
    }

    private record ResolvedAnchor(Vec3 position, Vec3 normal) {
    }

    private record PartPose(Vec3 origin, Basis basis, double halfX, double halfY, double halfZ) {
    }

    private record Rig(PartPose torso, PartPose head, PartPose leftArm, PartPose rightArm, PartPose leftLeg, PartPose rightLeg) {
    }

    private static final class ActiveFullCowl {
        private int life;
        private int maxLife;
        private int percent;
        private int age;
        private float seed;
    }
}
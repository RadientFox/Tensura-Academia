package com.radient.tensuraacadamia.quirks.dark_shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class DarkShadowModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("tracadamia", "dark_shadow"), "main");
    private final ModelPart root, body, head, leftArm, rightArm, leftForearm, rightForearm, face, eyes, beak, markings;
    public final ModelPart segment;

    public DarkShadowModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body"); head = body.getChild("head");
        leftArm = body.getChild("left_arm"); rightArm = body.getChild("right_arm");
        leftForearm = leftArm.getChild("forearm"); rightForearm = rightArm.getChild("forearm");
        face = head.getChild("face"); eyes = face.getChild("eyes"); beak = face.getChild("beak");
        markings = body.getChild("markings"); segment = root.getChild("segment");
        segment.visible = false;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .addBox(-3, -5, -2.5F, 6, 3, 5)
                .addBox(-6, -3, -3, 12, 8, 6)
                .addBox(-4, 5, -2.5F, 8, 6, 5)
                .addBox(-2.5F, 11, -1.5F, 5, 4, 3), PartPose.offset(0, 9, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .addBox(-4, -7, -4, 8, 7, 7)
                .addBox(-3, -8, -2, 6, 2, 5), PartPose.offset(0, -4, -0.5F));
        for (int side = -1; side <= 1; side += 2) {
            head.addOrReplaceChild("crest" + side, CubeListBuilder.create().addBox(-0.8F, -3.5F, -1, 1.6F, 3.5F, 3),
                    PartPose.offsetAndRotation(side * 2.8F, -6, 1.5F, -0.65F, 0, side * 0.25F));
            for (int feather = 0; feather < 3; feather++) body.addOrReplaceChild("ruff" + side + feather,
                    CubeListBuilder.create().addBox(-1, -1, -0.6F, 2, 5 - feather, 1.2F),
                    PartPose.offsetAndRotation(side * (4.2F + feather * 0.8F), -2 + feather, 2,
                            -0.35F, side * 0.3F, -side * (0.55F + feather * 0.16F)));
            PartDefinition arm = body.addOrReplaceChild(side < 0 ? "right_arm" : "left_arm",
                    CubeListBuilder.create().addBox(-2, -1, -2, 4, 7, 4),
                    PartPose.offsetAndRotation(side * 7, -1, 0, -0.12F, 0, -side * 0.18F));
            PartDefinition forearm = arm.addOrReplaceChild("forearm", CubeListBuilder.create()
                    .addBox(-2.2F, 0, -2.2F, 4.4F, 6, 4.4F)
                    .addBox(-2.8F, 5, -2.8F, 5.6F, 3, 5.6F), PartPose.offsetAndRotation(0, 6, 0, -0.22F, 0, 0));
            for (int finger = -1; finger <= 1; finger++) {
                PartDefinition claw = forearm.addOrReplaceChild("claw" + finger,
                        CubeListBuilder.create().addBox(-0.5F, 0, -0.5F, 1, 3, 1),
                        PartPose.offsetAndRotation(finger * 1.7F, 7, -2, -0.45F, 0, -finger * 0.12F));
                claw.addOrReplaceChild("tip", CubeListBuilder.create().addBox(-0.35F, 0, -0.35F, 0.7F, 2, 0.7F),
                        PartPose.offsetAndRotation(0, 2.5F, 0, -0.8F, 0, 0));
            }
        }
        PartDefinition face = head.addOrReplaceChild("face", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition eyes = face.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.ZERO);
        for (int side = -1; side <= 1; side += 2) eyes.addOrReplaceChild("eye" + side,
                CubeListBuilder.create().addBox(-1.2F, -0.4F, -0.2F, 2.4F, 0.8F, 0.4F),
                PartPose.offsetAndRotation(side * 2.2F, -4.6F, -4.05F, 0, side * 0.12F, -side * 0.2F));
        PartDefinition beak = face.addOrReplaceChild("beak", CubeListBuilder.create()
                .addBox(-2, -0.8F, -4.5F, 4, 1.6F, 4.5F),
                PartPose.offsetAndRotation(0, -3.2F, -3.8F, 0.18F, 0, 0));
        beak.addOrReplaceChild("hook", CubeListBuilder.create().addBox(-1, 0, -1, 2, 2.5F, 2),
                PartPose.offsetAndRotation(0, 0, -3.8F, 0.3F, 0, 0));
        beak.addOrReplaceChild("jaw", CubeListBuilder.create().addBox(-1.5F, 0, -3.5F, 3, 0.7F, 3.5F),
                PartPose.offset(0, 1.5F, 0));
        PartDefinition marks = body.addOrReplaceChild("markings", CubeListBuilder.create()
                .addBox(-0.5F, -2, -3.08F, 1, 6, 0.16F)
                .addBox(-0.4F, 5, -2.58F, 0.8F, 5, 0.16F), PartPose.ZERO);
        for (int side = -1; side <= 1; side += 2) marks.addOrReplaceChild("streak" + side,
                CubeListBuilder.create().addBox(-0.5F, 0, -0.08F, 1, 5, 0.16F),
                PartPose.offsetAndRotation(side * 4, -2, -3.08F, 0, 0, side * 0.55F));
        root.addOrReplaceChild("segment", CubeListBuilder.create().addBox(-2, 0, -2, 4, 12, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    public void animate(DarkShadow shadow, float partialTick, float yaw) {
        int action = shadow.action();
        float headYaw = Mth.wrapDegrees(shadow.getYHeadRot() - yaw) * Mth.DEG_TO_RAD;
        float pitch = shadow.getXRot() * Mth.DEG_TO_RAD;
        if (action >= 0) {
            Vec3 direction = shadow.armEnd().subtract(shadow.position().add(0, shadow.getBbHeight() * .56, 0));
            headYaw = 0;
            pitch = (float) -Math.atan2(direction.y, direction.horizontalDistance());
        }
        animate(shadow.tickCount + partialTick, shadow.walkAnimation.speed(partialTick),
                shadow.walkAnimation.position(partialTick), headYaw, pitch, action, attackPose(shadow, partialTick),
                shadow.berserk(), shadow.command());
        if (shadow.hurtTime > 0) {
            float flinch = Mth.sin(Mth.clamp((shadow.hurtTime - partialTick) / 10, 0, 1) * Mth.PI);
            body.xRot -= flinch * .2F; body.yRot += shadow.attackSide() * flinch * .12F;
            head.xRot -= flinch * .15F;
        }
        if (shadow.deathTime > 0) {
            float collapse = Mth.clamp((shadow.deathTime + partialTick) / 20, 0, 1);
            body.y += collapse * 8; body.zRot += collapse * 1.1F;
            body.xRot += collapse * .6F; head.yRot -= collapse * .4F;
        }
    }

    public record AttackPose(float windup, float extension, float recovery, float impact, int side) { }

    public static AttackPose attackPose(DarkShadow shadow, float partialTick) {
        float elapsed = shadow.visualStrikeAt() == 0 ? -1
                : (float) (shadow.level().getGameTime() - shadow.visualStrikeAt()) + partialTick;
        return attackPose(shadow.action(), shadow.armProgress(), elapsed, shadow.attackSide());
    }

    public static AttackPose attackPose(int action, float progress, float elapsed, int side) {
        if (action < 0) return new AttackPose(0, 0, 0, 0, 1);
        float recovery = elapsed < 0 ? 0 : Mth.clamp((elapsed - 2) / 8, 0, 1);
        float windup = Mth.clamp(progress / .65F, 0, 1) * (1 - recovery);
        windup = (float) Math.floor(windup * 4) / 4;
        float strike = Mth.clamp((progress - .65F) / .35F, 0, 1);
        if (elapsed >= 0) strike = 1;
        if (action == DarkShadowQuirk.ARMS) strike = Mth.clamp(progress, 0, 1);
        float extension = (1 - (float) Math.pow(1 - strike, 3)) * (1 - recovery * recovery);
        float impact = elapsed >= 0 && elapsed < 4 ? 1 - elapsed / 4 : 0;
        return new AttackPose(windup, extension, recovery, impact,
                action == DarkShadowQuirk.COMMAND && side < 0 ? -1 : 1);
    }

    public void animate(float age, float movement, float walk, float headYaw, float pitch,
                        int action, AttackPose attack, boolean berserk, int command) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float beat = (float) Math.floor(age / 2) * 2;
        float swing = Mth.cos(walk * .6662F) * movement * .4F;
        body.y += Mth.sin(beat * .09F) * (.35F + movement * .45F);
        body.xRot = -movement * .2F;
        body.zRot = Mth.sin(walk * .6662F) * movement * .07F;
        head.yRot = Mth.clamp(headYaw, -1.1F, 1.1F);
        head.xRot = Mth.clamp(pitch, -.65F, .65F) + Mth.sin(beat * .06F) * .035F;
        leftArm.xRot += swing; rightArm.xRot -= swing;
        float sway = Mth.sin(beat * .08F) * .07F;
        leftArm.zRot -= sway; rightArm.zRot += sway;
        leftForearm.xRot -= movement * .25F; rightForearm.xRot -= movement * .25F;
        float guard = berserk ? .8F : command == 2 ? 1 : command >= 0 ? .35F : 0;
        leftArm.xRot -= guard * .45F; rightArm.xRot -= guard * .45F;
        leftForearm.xRot -= guard * .8F; rightForearm.xRot -= guard * .8F;
        if (berserk) {
            body.xRot -= .18F; head.xRot -= .12F;
            body.zRot += Mth.sin(beat * .6F) * .045F;
            leftArm.zRot -= .2F; rightArm.zRot += .2F;
        }
        beak.getChild("jaw").xRot = berserk ? .45F : action >= 0 ? .15F + attack.extension * .3F : .02F;
        boolean extending = action >= 0 && action != DarkShadowQuirk.WOMB && action != DarkShadowQuirk.ANGEL;
        boolean single = action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.FLEETING
                || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR;
        rightArm.visible = !extending || single && attack.side < 0;
        leftArm.visible = !extending || single && attack.side > 0;
        float power = action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR ? 1.5F : 1;
        if (extending) {
            body.y += attack.windup * power * 1.5F - attack.extension * 1.5F;
            body.z += attack.windup * power * 1.3F - attack.extension * 2;
            body.xRot += attack.windup * .12F * power - attack.extension * .22F;
            body.yRot = single ? attack.side * (.35F * attack.windup - .42F * attack.extension) : 0;
            body.zRot += single ? attack.side * (.06F * attack.windup - .1F * attack.extension) : 0;
            head.yRot -= body.yRot * .7F;
            leftArm.xRot -= .45F * (1 - attack.recovery); rightArm.xRot -= .45F * (1 - attack.recovery);
            leftForearm.xRot -= .9F * (1 - attack.recovery); rightForearm.xRot -= .9F * (1 - attack.recovery);
        }
        if (action == DarkShadowQuirk.WOMB) {
            leftArm.xRot = rightArm.xRot = -.95F;
            leftArm.yRot = .35F; rightArm.yRot = -.35F;
            leftForearm.xRot = rightForearm.xRot = -.8F;
        } else if (action == DarkShadowQuirk.ANGEL) {
            leftArm.zRot = -1.1F * attack.extension; rightArm.zRot = 1.1F * attack.extension;
            leftForearm.xRot = rightForearm.xRot = -.4F;
        } else if (action == DarkShadowQuirk.BARRAGE) {
            body.xRot = -.22F; body.yRot = Mth.sin(beat * 1.5F) * .12F;
            head.yRot -= body.yRot;
        }
        if (attack.impact > 0) {
            body.z += attack.impact * .8F;
            head.xRot -= attack.impact * .1F;
        }
        for (int side = -1; side <= 1; side += 2) {
            head.getChild("crest" + side).xRot -= movement * .25F + attack.extension * .2F;
            for (int feather = 0; feather < 3; feather++) body.getChild("ruff" + side + feather).xRot
                    += Mth.sin(beat * .12F + feather) * .08F - movement * .2F - attack.extension * .2F;
            ModelPart forearm = side < 0 ? rightForearm : leftForearm;
            for (int finger = -1; finger <= 1; finger++) forearm.getChild("claw" + finger).xRot
                    -= guard * .3F + attack.windup * .45F;
        }
    }

    public Vec3 shoulder(int side, float yaw, float scale) {
        return joint(side, yaw, scale, false);
    }

    public Vec3 hand(int side, float yaw, float scale) {
        return joint(side, yaw, scale, true);
    }

    private Vec3 joint(int side, float yaw, float scale, boolean hand) {
        PoseStack poses = new PoseStack();
        poses.mulPose(new org.joml.Quaternionf().rotationY((180 - yaw) * Mth.DEG_TO_RAD));
        poses.scale(-scale, -scale, scale); poses.translate(0, -1.501, 0);
        body.translateAndRotate(poses);
        ModelPart arm = side > 0 ? rightArm : leftArm;
        Vector3f point;
        if (hand) {
            arm.translateAndRotate(poses); arm.getChild("forearm").translateAndRotate(poses);
            point = poses.last().pose().transformPosition(0, 6.5F / 16, 0, new Vector3f());
        } else point = poses.last().pose().transformPosition(arm.x / 16, arm.y / 16, arm.z / 16, new Vector3f());
        return new Vec3(point);
    }

    public void renderBody(PoseStack poses, VertexConsumer vertices, int light) {
        face.visible = false; markings.visible = false; segment.visible = false;
        root.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, 0xFF0A0811);
        markings.visible = true;
        poses.pushPose(); body.translateAndRotate(poses);
        markings.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, 0xFF30203E);
        head.translateAndRotate(poses); face.translateAndRotate(poses);
        beak.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, 0xFF382545);
        poses.popPose();
    }

    public void renderEyes(PoseStack poses, VertexConsumer vertices, int light) {
        poses.pushPose(); body.translateAndRotate(poses); head.translateAndRotate(poses); face.translateAndRotate(poses);
        eyes.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, 0xFFFFDC58);
        poses.popPose();
    }
}

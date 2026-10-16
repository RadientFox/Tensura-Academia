package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk;
import com.radient.tensuraacadamia.entity.DarkShadow;
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
                .addBox(-6, -3, -3, 12, 8, 6)
                .addBox(-4, 5, -2.5F, 8, 6, 5)
                .addBox(-2.5F, 11, -1.5F, 5, 4, 3), PartPose.offset(0, 9, 0));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .addBox(-4, -7, -4, 8, 7, 7)
                .addBox(-3, -8, -2, 6, 2, 5), PartPose.offset(0, -4, -0.5F));
        for (int side = -1; side <= 1; side += 2) {
            head.addOrReplaceChild("crest" + side, CubeListBuilder.create().addBox(-0.8F, -3.5F, -1, 1.6F, 3.5F, 3),
                    PartPose.offsetAndRotation(side * 2.8F, -6, 1.5F, -0.65F, 0, side * 0.25F));
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
        root.getAllParts().forEach(ModelPart::resetPose);
        float age = shadow.tickCount + partialTick;
        float movement = shadow.walkAnimation.speed(partialTick);
        float swing = Mth.cos(shadow.walkAnimation.position(partialTick) * 0.6662F) * movement * 0.55F;
        body.y += Mth.sin(age * 0.09F) * (0.25F + movement * 0.25F);
        body.xRot = -movement * 0.06F;
        head.yRot = Mth.wrapDegrees(shadow.getYHeadRot() - yaw) * Mth.DEG_TO_RAD;
        head.xRot = shadow.getXRot() * Mth.DEG_TO_RAD + Mth.sin(age * 0.06F) * 0.025F;
        leftArm.xRot += swing; rightArm.xRot -= swing;
        float sway = Mth.sin(age * 0.08F) * 0.04F;
        leftArm.zRot -= sway; rightArm.zRot += sway;
        leftForearm.xRot -= movement * 0.15F; rightForearm.xRot -= movement * 0.15F;
        beak.getChild("jaw").xRot = shadow.action() >= 0 ? 0.12F + shadow.armProgress() * 0.18F : 0;
        int action = shadow.action();
        boolean extending = action >= 0 && action != DarkShadowQuirk.WOMB && action != DarkShadowQuirk.ANGEL;
        rightArm.visible = !extending;
        leftArm.visible = !extending || action == DarkShadowQuirk.COMMAND || action == DarkShadowQuirk.FLEETING
                || action == DarkShadowQuirk.RAGNAROK || action == DarkShadowQuirk.BALDUR;
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

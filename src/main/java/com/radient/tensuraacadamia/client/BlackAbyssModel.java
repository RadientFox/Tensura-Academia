package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.ability.unique.quirks.DarkShadowQuirk;
import com.radient.tensuraacadamia.entity.DarkShadow;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

public final class BlackAbyssModel {
    public static final ModelLayerLocation NORMAL = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("tracadamia", "black_abyss"), "normal");
    public static final ModelLayerLocation SLIM = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("tracadamia", "black_abyss"), "slim");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");
    private final ModelPart root;

    public BlackAbyssModel(ModelPart root) { this.root = root; }

    public static LayerDefinition createLayer(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = shell(root, "head", CubeListBuilder.create()
                .addBox(-5.5F, -10.8F, -5, 11, 2.3F, 11)
                .addBox(-5.5F, -8.5F, 3.8F, 11, 9, 2.2F)
                .addBox(-5.5F, -8.5F, -5, 1.1F, 8, 8.8F)
                .addBox(4.4F, -8.5F, -5, 1.1F, 8, 8.8F));
        PartDefinition face = head.getChild("markings");
        face.addOrReplaceChild("beak", CubeListBuilder.create()
                .addBox(-2.2F, -10.4F, -8.3F, 4.4F, 1.5F, 3.4F)
                .addBox(-1.1F, -9.2F, -8.3F, 2.2F, 0.6F, 1.4F), PartPose.ZERO);
        PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.ZERO);
        for (int side = -1; side <= 1; side += 2) {
            eyes.addOrReplaceChild("eye" + side, CubeListBuilder.create().addBox(-1.1F, -0.35F, -0.12F, 2.2F, 0.7F, 0.24F),
                    PartPose.offsetAndRotation(side * 3.2F, -9.4F, -5.05F, 0, 0, -side * 0.2F));
            face.addOrReplaceChild("temple" + side, CubeListBuilder.create().addBox(-0.35F, 0, -0.12F, 0.7F, 3.2F, 0.24F),
                    PartPose.offsetAndRotation(side * 4.7F, -10.5F, -5.05F, 0, 0, side * 0.2F));
            for (int feather = 0; feather < 3; feather++) head.addOrReplaceChild("ruff" + side + "_" + feather,
                    CubeListBuilder.create().addBox(-0.65F, 0, -0.9F, 1.3F, 3.5F, 1.8F),
                    PartPose.offsetAndRotation(side * 5, -4 + feather * 1.8F, 1 + feather * 0.5F,
                            0.5F, 0, -side * 0.4F));
        }
        for (int feather = -1; feather <= 1; feather++) head.addOrReplaceChild("crest" + feather,
                CubeListBuilder.create().addBox(-0.9F, -3.2F, -1.2F, 1.8F, 3.2F, 3.5F),
                PartPose.offsetAndRotation(feather * 3, -10, 4.4F, -1.15F, 0, feather * 0.15F));
        PartDefinition body = shell(root, "body", CubeListBuilder.create()
                .addBox(-5.8F, -0.7F, 1.8F, 11.6F, 6.8F, 2.6F)
                .addBox(-4.8F, 5, 2, 9.6F, 3.5F, 2.2F)
                .addBox(-5.3F, -0.7F, -2.8F, 10.6F, 2.2F, 5.6F));
        for (int feather = -3; feather <= 3; feather++) {
            PartPose pose = PartPose.offsetAndRotation(feather * 1.6F, 4.5F, 3.6F,
                    0.15F + Math.abs(feather) * 0.06F, 0, -feather * 0.12F);
            body.addOrReplaceChild("plume" + feather, CubeListBuilder.create()
                    .addBox(-1.3F, 0, -0.8F, 2.6F, 4.5F, 1.6F)
                    .addBox(-0.9F, 4, -0.6F, 1.8F, 2.5F, 1.2F)
                    .addBox(-0.4F, 6, -0.35F, 0.8F, 1.6F, 0.7F), pose);
            body.getChild("markings").addOrReplaceChild("plume" + feather, CubeListBuilder.create()
                    .addBox(-0.25F, 0.5F, 0.7F, 0.5F, 3.5F, 0.2F)
                    .addBox(-0.18F, 4, 0.5F, 0.36F, 2, 0.2F), pose);
        }
        for (int side = -1; side <= 1; side += 2) {
            String prefix = side < 0 ? "right" : "left";
            float center = side * (slim ? 0.5F : 1);
            float upperWidth = slim ? 4.1F : 5.1F;
            PartDefinition arm = shell(root, prefix + "_arm", CubeListBuilder.create()
                    .addBox(center - upperWidth / 2, -2.55F, -2.55F, upperWidth, 6.5F, 5.1F)
                    .addBox(center - 2.8F, 3.5F, -2.8F, 5.6F, 5.2F, 5.6F)
                    .addBox(center - 3.2F, 8, -3.2F, 6.4F, 2.8F, 6.4F));
            for (int feather = 0; feather < 3; feather++) {
                arm.addOrReplaceChild("ruff" + feather, CubeListBuilder.create()
                        .addBox(-0.8F, 0, -1, 1.6F, 4 - feather * 0.5F, 2),
                        PartPose.offsetAndRotation(center + side * 2.4F, -1 + feather * 2.5F, 2,
                                0.6F, 0, -side * 0.35F));
                arm.getChild("markings").addOrReplaceChild("streak" + feather, CubeListBuilder.create()
                        .addBox(-0.3F, 0, -0.15F, 0.6F, 2.8F, 0.3F),
                        PartPose.offsetAndRotation(center - side * 0.6F, -1 + feather * 3.2F,
                                feather == 0 ? -2.6F : -2.85F, 0, 0, side * 0.3F));
            }
            for (int finger = -1; finger <= 1; finger++) {
                PartDefinition claw = arm.addOrReplaceChild("claw" + finger, CubeListBuilder.create()
                        .addBox(-0.6F, 0, -0.6F, 1.2F, 3.6F, 1.2F),
                        PartPose.offsetAndRotation(center + finger * 1.75F, 10.2F, -2.4F, -0.3F, 0, -finger * 0.12F));
                claw.addOrReplaceChild("hook", CubeListBuilder.create().addBox(-0.35F, 0, -0.35F, 0.7F, 1.8F, 0.7F),
                        PartPose.offsetAndRotation(0, 3.1F, 0, -0.8F, 0, 0));
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static PartDefinition shell(PartDefinition root, String name, CubeListBuilder cubes) {
        PartDefinition shell = root.addOrReplaceChild(name, cubes, PartPose.ZERO);
        shell.addOrReplaceChild("markings", CubeListBuilder.create(), PartPose.ZERO);
        return shell;
    }

    public void render(PoseStack poses, MultiBufferSource buffers, int light, PlayerModel<?> player, DarkShadow shadow, float partialTick) {
        animate(shadow.tickCount + partialTick,
                shadow.creator() instanceof net.minecraft.world.entity.LivingEntity owner ? owner.walkAnimation.speed(partialTick) : 0);
        fitted(poses, buffers, light, "head", player.head, 1);
        fitted(poses, buffers, light, "body", player.body, 1);
        float charge = shadow.action() == DarkShadowQuirk.FLEETING || shadow.action() == DarkShadowQuirk.BALDUR
                ? 1 + shadow.armProgress() * (shadow.action() == DarkShadowQuirk.BALDUR ? 0.6F : 0.3F) : 1;
        fitted(poses, buffers, light, "right_arm", player.rightArm, charge);
        fitted(poses, buffers, light, "left_arm", player.leftArm, 1);
    }

    public void animate(float age, float movement) {
        root.getAllParts().forEach(ModelPart::resetPose);
        ModelPart body = root.getChild("body");
        for (int feather = -3; feather <= 3; feather++) {
            float flutter = net.minecraft.util.Mth.sin(age * 0.12F + feather * 0.8F) * (0.04F + movement * 0.1F);
            body.getChild("plume" + feather).xRot += flutter;
            body.getChild("markings").getChild("plume" + feather).xRot += flutter;
        }
        for (String arm : new String[]{"right_arm", "left_arm"}) for (int feather = 0; feather < 3; feather++)
            root.getChild(arm).getChild("ruff" + feather).xRot += net.minecraft.util.Mth.sin(age * 0.12F + feather) * 0.035F;
    }

    private void fitted(PoseStack poses, MultiBufferSource buffers, int light, String name, ModelPart playerPart, float width) {
        if (!playerPart.visible) return;
        poses.pushPose();
        playerPart.translateAndRotate(poses);
        poses.scale(width, 1, width);
        renderPart(poses, buffers, light, name, 255);
        poses.popPose();
    }

    public void renderFirstPersonArm(PoseStack poses, MultiBufferSource buffers, int light, ModelPart playerArm, HumanoidArm side) {
        PartPose rest = playerArm.getInitialPose();
        poses.pushPose();
        poses.translate(rest.x / 16, rest.y / 16, rest.z / 16);
        poses.mulPose(new org.joml.Quaternionf().rotationZ(side == HumanoidArm.RIGHT ? 0.1F : -0.1F));
        renderPart(poses, buffers, light, side == HumanoidArm.RIGHT ? "right_arm" : "left_arm", 153);
        poses.popPose();
    }

    private void renderPart(PoseStack poses, MultiBufferSource buffers, int light, String name, int alpha) {
        ModelPart part = root.getChild(name), markings = part.getChild("markings");
        ModelPart eyes = part.hasChild("eyes") ? part.getChild("eyes") : null;
        var vertices = buffers.getBuffer(alpha < 255 ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE));
        markings.visible = false;
        if (eyes != null) eyes.visible = false;
        part.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0x0A0811);
        markings.visible = true;
        markings.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0x30203E);
        if (eyes != null) {
            eyes.visible = true;
            eyes.render(poses, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFDC58);
        }
    }
}

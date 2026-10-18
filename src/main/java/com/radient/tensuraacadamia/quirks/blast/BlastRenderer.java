package com.radient.tensuraacadamia.quirks.blast;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class BlastRenderer extends EntityRenderer<BlastProjectile> {
    public BlastRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlastProjectile entity) { return ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"); }
    @Override public void render(BlastProjectile shot, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(shot.getPosition(partial));
        Vec3 direction = shot.getDeltaMovement().normalize(); if (direction.lengthSqr() < 1e-8) direction = shot.getLookAngle();
        float time = shot.tickCount + partial;
        renderShot(poses, buffers, cam, direction, shot.getSize(), time);
        super.render(shot, yaw, partial, poses, buffers, light);
    }
    static void renderShot(PoseStack poses, MultiBufferSource buffers, Vec3 cam, Vec3 direction, float size, float time) {
        boolean large = size >= 4;
        double radius = large ? 2 : size <= .16F ? .09 : .18;
        SkillVfx.disc(poses, buffers, cam, Vec3.ZERO, radius * 1.4, 0xffca15, .5f, true);
        SkillVfx.disc(poses, buffers, cam, Vec3.ZERO, radius * .65, 0xffef97, 1, true);
        Vec3 tail = direction.scale(-(large ? 5 : .8));
        SkillVfx.ribbon(poses, buffers, cam, Vec3.ZERO, tail, radius * 1.4, 0xffc51d, .35f, true);
        SkillVfx.ribbon(poses, buffers, cam, Vec3.ZERO, tail.scale(.65), radius * .3, 0xfff0b0, .9f, true);
        SkillVfx.ring(poses, buffers, cam, Vec3.ZERO, direction, radius, radius * .12, 0xffd82e, .8f, time * .6);
        if (large) {
            SkillVfx.ring(poses, buffers, cam, tail.scale(.25), direction, radius * .7, .08, 0xffe381, .7f, -time * .4);
            SkillVfx.ring(poses, buffers, cam, tail.scale(.6), direction, radius * .4, .06, 0xffcb20, .6f, time * .5);
        }
    }
}

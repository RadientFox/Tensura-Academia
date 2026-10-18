package com.radient.tensuraacadamia.quirks.acid;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AcidRenderer extends EntityRenderer<AcidProjectile> {
    private static final int LIQUID = 0xB0B1BE, OUTLINE = 0x404350, HIGHLIGHT = 0xF3F4FA;

    public AcidRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AcidEntities.ACID.get(), AcidRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(AcidProjectile acid) {
        return ResourceLocation.withDefaultNamespace("textures/block/slime_block.png");
    }

    @Override
    public void render(AcidProjectile acid, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(acid.getPosition(partial));
        float age = acid.tickCount + partial;
        if (acid.form() == AcidProjectile.MARK) {
            float fade = Math.clamp((60 - age) / 15F, 0, 1);
            patch(poses, buffers, acid.facing(), .61, fade * .8F, OUTLINE);
            patch(poses, buffers, acid.facing(), .55, fade * .9F, LIQUID);
            Vec3 right = AcidProjectile.right(acid.facing());
            SkillVfx.ribbon(poses, buffers, camera, right.scale(-.25), right.scale(.12).add(acid.facing().scale(.008)), .035, HIGHLIGHT, fade * .65F, false);
        } else if (acid.form() == AcidProjectile.VEIL) veil(poses, buffers, camera, acid.facing(), age);
        else {
            Vec3 direction = acid.facing();
            Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction.toVector3f());
            Vec3 localCamera = new Vec3(camera.toVector3f().rotate(new Quaternionf(rotation).conjugate()));
            poses.pushPose();
            poses.mulPose(rotation);
            double size = acid.form() == AcidProjectile.ALMA ? .7 + acid.stacks() / 100.0 : .23;
            int detail = camera.lengthSqr() > 1024 ? 6 : 10;
            double stretch = 1.35 + Math.min(.5, acid.getDeltaMovement().length() * .15);
            blob(poses, buffers, Vec3.ZERO, size, stretch, age, 1, detail);
            // trail axis
            for (int i = 0; i < 4; i++) {
                double a = age * .12 + i * 2.4, distance = size * (1.1 + i * .55);
                Vec3 offset = new Vec3(Math.cos(a) * size * .2, -distance, Math.sin(a) * size * .2);
                blob(poses, buffers, offset, size * (.3 - i * .045), 2 - i * .15, age + i, .9F - i * .15F, 6);
            }
            Vec3 shine = localCamera.normalize().scale(size * .88).add(-size * .12, size * .1, 0);
            SkillVfx.ribbon(poses, buffers, localCamera, shine, shine.add(0, size * .3, 0), size * .1, HIGHLIGHT, .75F, false);
            poses.popPose();
        }
        super.render(acid, yaw, partial, poses, buffers, light);
    }

    private static void veil(PoseStack poses, MultiBufferSource buffers, Vec3 camera, Vec3 normal, float age) {
        Vec3 right = AcidProjectile.right(normal), up = AcidProjectile.up(normal);
        float alpha = Math.clamp(age / 4F, .15F, 1) * Math.clamp((60 - age) / 8F, 0, 1);
        var vertices = buffers.getBuffer(SkillVfx.HAZE);
        for (int x = 0; x < 10; x++)
            for (int y = 0; y < 6; y++) {
                double left = -1.5 + x * .3, bottom = -1.5 + y * .5;
                int tint = y % 3 == 0 ? 0xBDBDCC : LIQUID;
                quad(vertices, poses, sheet(right, up, normal, left, bottom, age), sheet(right, up, normal, left + .3, bottom, age),
                        sheet(right, up, normal, left + .3, bottom + .5, age), sheet(right, up, normal, left, bottom + .5, age), tint, alpha * .9F);
            }
        for (int i = 0; i < 10; i++) {
            double x = -1.35 + i * .3;
            Vec3 top = right.scale(x).add(up.scale(1.5)), bottom = right.scale(x).add(up.scale(-1.5));
            double flow = (age * .075 + i * .43) % 3;
            Vec3 gleam = top.subtract(up.scale(flow)).add(normal.scale(.065));
            SkillVfx.ribbon(poses, buffers, camera, gleam, gleam.subtract(up.scale(Math.min(.45, 3 - flow))), .025, HIGHLIGHT, alpha * .65F, false);
            blob(poses, buffers, bottom.subtract(up.scale((age * .035 + i * .15) % .4)), .045, 1.7, age + i, alpha, 6);
        }
        for (int side : new int[]{-1, 1}) {
            SkillVfx.ribbon(poses, buffers, camera, right.scale(side * 1.5).add(up.scale(-1.5)),
                    right.scale(side * 1.5).add(up.scale(1.5)), .045, OUTLINE, alpha, false);
            SkillVfx.ribbon(poses, buffers, camera, up.scale(side * 1.5).add(right.scale(-1.5)),
                    up.scale(side * 1.5).add(right.scale(1.5)), .05, OUTLINE, alpha, false);
        }
    }

    private static Vec3 sheet(Vec3 right, Vec3 up, Vec3 normal, double x, double y, float age) {
        return right.scale(x).add(up.scale(y)).add(normal.scale(Math.sin(x * 3 + y * 2 - age * .13) * .04));
    }

    private static void patch(PoseStack poses, MultiBufferSource buffers, Vec3 normal, double radius, float alpha, int color) {
        Vec3 right = AcidProjectile.right(normal), up = AcidProjectile.up(normal), center = normal.scale(color == LIQUID ? .004 : 0);
        var vertices = buffers.getBuffer(SkillVfx.HAZE);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, b = (i + 1) * Math.PI / 6;
            Vec3 start = center.add(right.scale(Math.cos(a) * radius * (i % 3 == 0 ? .8 : 1))).add(up.scale(Math.sin(a) * radius));
            Vec3 end = center.add(right.scale(Math.cos(b) * radius * ((i + 1) % 3 == 0 ? .8 : 1))).add(up.scale(Math.sin(b) * radius));
            quad(vertices, poses, center, start, end, center, color, alpha);
        }
    }

    static void blob(PoseStack poses, MultiBufferSource buffers, Vec3 center, double radius, double height, float time, float alpha, int sides) {
        shell(poses, buffers, center, radius * 1.035, height, time, alpha, sides, OUTLINE);
        shell(poses, buffers, center, radius, height, time, alpha, sides, LIQUID);
    }

    private static void shell(PoseStack poses, MultiBufferSource buffers, Vec3 center, double radius, double height, float time, float alpha, int sides, int tint) {
        var vertices = buffers.getBuffer(SkillVfx.HAZE);
        for (int ring = 0; ring < 4; ring++)
            for (int i = 0; i < sides; i++) {
                double a = i * Math.PI * 2 / sides, b = (i + 1) * Math.PI * 2 / sides;
                double low = -Math.PI / 2 + ring * Math.PI / 4, high = low + Math.PI / 4;
                float shade = (float) (.83 + .1 * Math.sin(high) + .07 * Math.cos(a - .8));
                int color = ((int) (((tint >> 16) & 255) * shade) << 16) | ((int) (((tint >> 8) & 255) * shade) << 8) | (int) ((tint & 255) * shade);
                quad(vertices, poses, surface(center, radius, height, low, a, time), surface(center, radius, height, low, b, time),
                        surface(center, radius, height, high, b, time), surface(center, radius, height, high, a, time), color, alpha);
            }
    }

    private static Vec3 surface(Vec3 center, double radius, double height, double latitude, double longitude, float time) {
        double pulse = 1 + Math.sin(longitude * 3 + latitude * 2 + time * .12) * .09
                + Math.sin(longitude * 5 - latitude * 3 - time * .18) * .035;
        return center.add(Math.cos(latitude) * Math.cos(longitude) * radius * pulse,
                Math.sin(latitude) * radius * height, Math.cos(latitude) * Math.sin(longitude) * radius * pulse);
    }

    private static void quad(VertexConsumer vertices, PoseStack poses, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {
        vertex(vertices, poses, a, color, alpha);
        vertex(vertices, poses, b, color, alpha);
        vertex(vertices, poses, c, color, alpha);
        vertex(vertices, poses, d, color, alpha);
    }

    private static void vertex(VertexConsumer vertices, PoseStack poses, Vec3 point, int color, float alpha) {
        vertices.addVertex(poses.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, (int) (255 * Math.clamp(alpha, 0, 1)));
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        var added = new java.util.HashSet<EntityRenderer<?>>();
        for (var skin : event.getSkins()) addCoating(event.getSkin(skin), added);
        for (var type : event.getEntityTypes()) addCoating(event.getRenderer(type), added);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addCoating(EntityRenderer<?> renderer, java.util.Set<EntityRenderer<?>> added) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel && added.add(renderer))
            living.addLayer(new Coating((RenderLayerParent) living));
    }

    private static final class Coating<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
        private Coating(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, T owner, float walk, float swing,
                           float partial, float age, float yaw, float pitch) {
            var minecraft = Minecraft.getInstance();
            if (!owner.isAlive() || !owner.hasEffect(MHAEffects.ACIDMAN) || owner.isInvisible()
                    || owner == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson())
                return;
            var model = getParentModel();
            coatPart(model.body, poses, buffers, new Vec3(0, .36, 0), .37, 1.35, age);
            coatPart(model.rightArm, poses, buffers, new Vec3(-.04, .23, 0), .2, 1.85, age + 4);
            coatPart(model.leftArm, poses, buffers, new Vec3(.04, .23, 0), .2, 1.85, age + 8);
            coatPart(model.rightLeg, poses, buffers, new Vec3(0, .35, 0), .21, 1.9, age + 12);
            coatPart(model.leftLeg, poses, buffers, new Vec3(0, .35, 0), .21, 1.9, age + 16);
            if (model.head.visible) {
                coverPart(model.head, poses, buffers);
                poses.pushPose();
                model.head.translateAndRotate(poses);
                blob(poses, buffers, new Vec3(0, -.25, 0), .36, .98, age, .92F, 10);
                for (int side : new int[]{-1, 1}) {
                    Vec3 center = new Vec3(side * .12, -.29, -.365);
                    eye(poses, buffers, center, .086, .059, OUTLINE);
                    eye(poses, buffers, center.add(0, 0, -.004), .065, .039, HIGHLIGHT);
                }
                poses.popPose();
            }
        }

        private static void coatPart(ModelPart part, PoseStack poses, MultiBufferSource buffers, Vec3 center, double radius, double length, float age) {
            if (!part.visible) return;
            coverPart(part, poses, buffers);
            poses.pushPose();
            part.translateAndRotate(poses);
            blob(poses, buffers, center, radius, length, age, .88F, 8);
            for (int run = 0; run < 2; run++) {
                double travel = (age * .015 + run * .5) % 1;
                Vec3 drop = center.add(run == 0 ? radius * .3 : -radius * .35,
                        (travel - .5) * radius * length, -radius * .99);
                SkillVfx.ribbon(poses, buffers, new Vec3(0, 0, -4), drop, drop.add(.015, radius * .3, 0),
                        .018, HIGHLIGHT, .65F, false);
            }
            poses.popPose();
        }

        private static void coverPart(ModelPart part, PoseStack poses, MultiBufferSource buffers) {
            // undercoat
            var vertices = buffers.getBuffer(RenderType.debugQuads());
            part.visit(poses, (pose, path, index, cube) -> {
                double pad = .075;
                double x = cube.minX / 16.0 - pad, y = cube.minY / 16.0 - pad, z = cube.minZ / 16.0 - pad;
                double X = cube.maxX / 16.0 + pad, Y = cube.maxY / 16.0 + pad, Z = cube.maxZ / 16.0 + pad;
                Vec3 a = new Vec3(x, y, z), b = new Vec3(X, y, z), c = new Vec3(X, Y, z), d = new Vec3(x, Y, z);
                Vec3 e = new Vec3(x, y, Z), f = new Vec3(X, y, Z), g = new Vec3(X, Y, Z), h = new Vec3(x, Y, Z);
                quad(vertices, poses, a, b, c, d, LIQUID, 1);
                quad(vertices, poses, f, e, h, g, 0xA3A5B3, 1);
                quad(vertices, poses, e, a, d, h, 0x999BAA, 1);
                quad(vertices, poses, b, f, g, c, 0x999BAA, 1);
                quad(vertices, poses, e, f, b, a, 0xBFC0CD, 1);
                quad(vertices, poses, d, c, g, h, 0x9798A5, 1);
            });
        }

        private static void eye(PoseStack poses, MultiBufferSource buffers, Vec3 center, double width, double height, int color) {
            var vertices = buffers.getBuffer(SkillVfx.HAZE);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, b = (i + 1) * Math.PI / 4;
                quad(vertices, poses, center, center.add(Math.cos(a) * width, Math.sin(a) * height, 0),
                        center.add(Math.cos(b) * width, Math.sin(b) * height, 0), center, color, 1);
            }
        }
    }
}

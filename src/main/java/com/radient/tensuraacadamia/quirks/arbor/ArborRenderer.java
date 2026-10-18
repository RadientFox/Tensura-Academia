package com.radient.tensuraacadamia.quirks.arbor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArborRenderer extends EntityRenderer<ArborConstruct> {
    private static final RenderType BARK = RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/block/oak_log.png"));
    private static final RenderType END = RenderType.entityCutoutNoCull(ResourceLocation.withDefaultNamespace("textures/block/oak_log_top.png"));

    public ArborRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArborEntities.CONSTRUCT.get(), ArborRenderer::new);
    }

    @SubscribeEvent
    public static void buffers(RegisterRenderBuffersEvent event) {
        event.registerRenderBuffer(BARK);
        event.registerRenderBuffer(END);
    }

    @Override
    public boolean shouldRender(ArborConstruct wood, Frustum frustum, double x, double y, double z) {
        return wood.shouldRender(x, y, z);
    }

    @Override
    public void render(ArborConstruct wood, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        if (wood.form() == ArborConstruct.TREE) {
            float growth = Math.clamp((wood.age() + partial) / 20F, .03F, 1);
            poses.pushPose();
            poses.scale(growth, growth, growth);
            for (int tier = 0; tier < 5; tier++) {
                float width = 30 - tier * 3;
                log(poses, buffers, light, new Vec3(-width / 2, tier * 6, -width / 2), width, 6.1F, width);
            }
            for (int root = 0; root < 8; root++) {
                double angle = root * Math.PI / 4;
                Vec3 axis = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                limb(poses, buffers, light, axis.scale(10).add(0, 9, 0), axis.scale(16).add(0, 2, 0), 3);
                limb(poses, buffers, light, axis.scale(16).add(0, 2, 0), axis.scale(24).add(0, .25, 0), 1.5F);
                Vec3 branch = axis.scale(10).add(0, 21 + root % 2, 0);
                Vec3 bend = axis.scale(17).add(0, 25, 0);
                limb(poses, buffers, light, branch, bend, 2.5F);
                limb(poses, buffers, light, bend, axis.scale(24).add(0, 29, 0), 1.2F);
                limb(poses, buffers, light, bend, axis.scale(19).add(0, 30, 4 * Math.sin(angle)), .75F);
                poses.pushPose();
                Vec3 crown = axis.scale(24).add(0, 28, 0);
                poses.translate(crown.x - 2, crown.y, crown.z - 2);
                poses.scale(4, 2.5F, 4);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.OAK_LEAVES.defaultBlockState(), poses, buffers, light, OverlayTexture.NO_OVERLAY);
                poses.popPose();
            }
            poses.popPose();
        } else {
            var root = wood.form() == ArborConstruct.TREE_BRANCH ? wood.visualRoot() : wood.visualOwner();
            if (root != null) {
                Vec3 start = root.getPosition(partial).add(0, wood.form() == ArborConstruct.TREE_BRANCH ? 12 : root.getBbHeight() * .65, 0)
                        .subtract(wood.getPosition(partial));
                if (root == Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson())
                    start = start.add(root.getViewVector(partial).cross(new Vec3(0, 1, 0)).scale(.55));
                Vec3 end = new Vec3(0, 1, 0);
                Vec3 previous = start;
                for (int segment = 1; segment <= 5; segment++) {
                    double progress = segment / 5.0;
                    double growth = Math.min(1, (wood.age() + partial) / 5.0);
                    Vec3 next = start.lerp(end, progress * growth).add(0, Math.sin(progress * Math.PI) * .35, 0);
                    limb(poses, buffers, light, previous, next, .75F - segment * .055F);
                    previous = next;
                }
            }
            log(poses, buffers, light, new Vec3(-.6, 0, -.6), 1.2F, 1.5F, 1.2F);
        }
        super.render(wood, yaw, partial, poses, buffers, light);
    }

    private static void limb(PoseStack poses, MultiBufferSource buffers, int light, Vec3 from, Vec3 to, float width) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < .001) return;
        poses.pushPose();
        poses.translate(from.x, from.y, from.z);
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize()));
        log(poses, buffers, light, new Vec3(-width / 2, 0, -width / 2), width, (float) delta.length(), width);
        poses.popPose();
    }

    private static void log(PoseStack poses, MultiBufferSource buffers, int light, Vec3 point, float width, float height, float depth) {
        poses.pushPose();
        poses.translate(point.x, point.y, point.z);
        Vec3 a = new Vec3(0, 0, 0), b = new Vec3(width, 0, 0), c = new Vec3(width, height, 0), d = new Vec3(0, height, 0);
        Vec3 e = new Vec3(0, 0, depth), f = new Vec3(width, 0, depth), g = new Vec3(width, height, depth), h = new Vec3(0, height, depth);
        face(poses, buffers, BARK, light, a, b, c, d, width, height, new Vec3(0, 0, -1));
        face(poses, buffers, BARK, light, f, e, h, g, width, height, new Vec3(0, 0, 1));
        face(poses, buffers, BARK, light, e, a, d, h, depth, height, new Vec3(-1, 0, 0));
        face(poses, buffers, BARK, light, b, f, g, c, depth, height, new Vec3(1, 0, 0));
        face(poses, buffers, END, light, d, c, g, h, width, depth, new Vec3(0, 1, 0));
        face(poses, buffers, END, light, e, f, b, a, width, depth, new Vec3(0, -1, 0));
        poses.popPose();
    }

    private static void face(PoseStack poses, MultiBufferSource buffers, RenderType type, int light,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float width, float height, Vec3 normal) {
        var vertices = buffers.getBuffer(type);
        Vec3[] points = {a, b, c, d};
        for (int i = 0; i < 4; i++)
            vertices.addVertex(poses.last().pose(), (float) points[i].x, (float) points[i].y, (float) points[i].z)
                    .setColor(255, 255, 255, 255).setUv(i == 1 || i == 2 ? width : 0, i >= 2 ? 0 : height)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(poses.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    public static void pose(PlayerModel<?> model, LivingEntity owner, float ticks) {
        var tree = owner.level().getEntitiesOfClass(ArborConstruct.class, owner.getBoundingBox().inflate(55),
                wood -> wood.form() == ArborConstruct.TREE && wood.visualOwner() == owner && wood.age() < 20).stream().findFirst().orElse(null);
        if (tree == null) return;
        float progress = Math.clamp((tree.age() + ticks % 1) / 20F, 0, 1);
        float bend = (float) Math.sin(progress * Math.PI);
        model.body.xRot = .7F * bend;
        model.rightArm.xRot = -1.8F * bend;
        model.leftArm.xRot = -.3F * bend;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.jacket.copyFrom(model.body);
    }

    @Override
    public ResourceLocation getTextureLocation(ArborConstruct wood) {
        return ResourceLocation.withDefaultNamespace("textures/block/oak_log.png");
    }
}

package com.radient.tensuraacadamia.quirks.acid_sweat;

import com.radient.tensuraacadamia.quirks.shared.SkillVfx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.quirks.acid.AcidProjectile;
import com.radient.tensuraacadamia.quirks.acid.AcidEntities;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class AcidSweatClient extends EntityRenderer<AcidSweatAction> {
    private final ModelPart arm;

    public AcidSweatClient(EntityRendererProvider.Context context) {
        super(context);
        arm = context.bakeLayer(ModelLayers.PLAYER).getChild("right_arm");
        arm.x = arm.y = arm.z = arm.xRot = arm.yRot = arm.zRot = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(AcidSweatAction action) {
        var owner = action.creator();
        return owner == null ? ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")
                : Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(owner).getTextureLocation(owner);
    }

    @Override
    public void render(AcidSweatAction action, float yaw, float partial, PoseStack poses, MultiBufferSource buffers, int light) {
        var owner = action.creator();
        if (owner == null || owner.isInvisible()) return;
        float age = action.age() + partial;
        boolean firstPerson = owner == Minecraft.getInstance().getCameraEntity() && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        Vec3 forward = owner.getViewVector(partial).normalize(), right = AcidProjectile.right(forward), up = AcidProjectile.up(forward);
        Vec3 origin = owner.getEyePosition(partial).subtract(action.getPosition(partial)).add(0, -.35, 0);
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(action)));
        if (action.mode() == 0) {
            if (!firstPerson && owner instanceof Player) return;
            double progress = Math.clamp(age / AcidSweatQuirk.CLAP_STARTUP, 0, 1);
            double spread = age < AcidSweatQuirk.CLAP_STARTUP ? .7 * (1 - progress * progress) + .1 : .1 + (age - AcidSweatQuirk.CLAP_STARTUP) * .045;
            for (int side : new int[]{-1, 1})
                segment(poses, vertices, origin.add(right.scale(side * .4)),
                        origin.add(forward.scale(.75)).add(right.scale(side * spread)), .25F, light, 230);
        } else {
            if (age < AcidSweatQuirk.BARRAGE_STARTUP) {
                if (firstPerson) for (int side : new int[]{-1, 1})
                    segment(poses, vertices, origin.add(right.scale(side * .4)),
                            origin.add(forward.scale(.6)).add(right.scale(side * .28)), .24F, light, 185);
                return;
            }
            for (int trail = 2; trail >= 0; trail--)
                for (int side : new int[]{-1, 1}) {
                    double time = age - AcidSweatQuirk.BARRAGE_STARTUP - trail * .5;
                    double phase = time / (AcidSweatQuirk.PUNCH_INTERVAL * 2) + (side == 1 ? .5 : 0);
                    phase -= Math.floor(phase);
                    double extension = phase < .3 ? phase / .3 : (1 - phase) / .7;
                    Vec3 shoulder = origin.add(right.scale(side * .4));
                    Vec3 fist = origin.add(forward.scale(.5 + extension * (AcidSweatQuirk.PUNCH_REACH - .5)))
                            .add(right.scale(side * .2)).add(up.scale(Math.sin(Math.floor(time / 4) * 2.1 + side) * .22));
                    segment(poses, vertices, shoulder, fist, .24F, light, trail == 0 ? firstPerson ? 185 : 255 : 50 / trail);
                }
        }
    }

    private void segment(PoseStack poses, VertexConsumer vertices, Vec3 from, Vec3 to, float width, int light, int alpha) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < .001) return;
        poses.pushPose();
        poses.translate(from.x, from.y, from.z);
        poses.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), delta.toVector3f().normalize()));
        poses.scale(width / .25F, (float) delta.length() / .75F, width / .25F);
        poses.translate(.0625, .125, 0);
        arm.render(poses, vertices, light, OverlayTexture.NO_OVERLAY, alpha << 24 | 0xFFFFFF);
        poses.popPose();
    }

    private static AcidSweatAction action(LivingEntity owner) {
        return owner.level().getEntitiesOfClass(AcidSweatAction.class, owner.getBoundingBox().inflate(4), cast -> cast.creator() == owner).stream().findFirst().orElse(null);
    }

    public static void pose(PlayerModel<?> model, LivingEntity owner, float age) {
        var action = action(owner);
        if (action == null) return;
        float time = action.age() + age % 1;
        model.rightArm.xRot = model.leftArm.xRot = -1.35F;
        if (action.mode() == 0) {
            float progress = Math.clamp(time / AcidSweatQuirk.CLAP_STARTUP, 0, 1);
            model.rightArm.yRot = .9F - progress * 1.45F;
            model.leftArm.yRot = -model.rightArm.yRot;
            model.rightArm.zRot = .15F;
            model.leftArm.zRot = -.15F;
        } else {
            model.rightArm.yRot = -.25F;
            model.leftArm.yRot = .25F;
            model.body.yRot += (float) Math.sin(time * Math.PI / 2) * .06F;
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AcidEntities.SWEAT_ACTION.get(), AcidSweatClient::new);
        }
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
    public static final class Hands {
        @SubscribeEvent
        public static void render(RenderHandEvent event) {
            var player = Minecraft.getInstance().player;
            if (player != null && action(player) != null) event.setCanceled(true);
        }
    }

    private static final class Blast extends Particle {
        private final double size, phase;

        private Blast(ClientLevel level, double x, double y, double z, double size) {
            super(level, x, y, z);
            this.size = Math.clamp(size, .2, 5);
            phase = random.nextDouble() * Math.PI;
            lifetime = this.size <= 1 ? 5 : 9;
            hasPhysics = false;
            xd = yd = zd = 0;
            setSize((float) this.size * 3, (float) this.size * 3);
        }

        @Override
        public void render(VertexConsumer ignored, Camera camera, float partial) {
            Vec3 origin = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z));
            Vec3 view = camera.getPosition().subtract(origin);
            if (view.lengthSqr() > 9216) return;
            float time = age + partial, fade = Math.max(0, 1 - time / lifetime);
            var poses = new PoseStack();
            var relative = origin.subtract(camera.getPosition());
            poses.translate(relative.x, relative.y, relative.z);
            var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
            // vfx ik its ass
            double radius = size * (.35 + Math.min(1, time / 3) * .65);
            SkillVfx.flare(poses, buffers, view, radius, 0xFF8E21, fade, (long) (phase * 100));
            SkillVfx.disc(poses, buffers, view, Vec3.ZERO, radius * .45, 0xFFF0B6, fade * Math.max(0, 1 - time / 5), true);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.CUSTOM;
        }
    }

    public static final class BlastProvider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new Blast(level, x, y, z, vx);
        }
    }
}

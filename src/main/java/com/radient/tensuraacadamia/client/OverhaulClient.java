package com.radient.tensuraacadamia.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.OverhaulQuirk;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class OverhaulClient {
    private static final Deque<UUID> ROOT_TRANSFORMS = new ArrayDeque<>();
    private static final Map<ResourceLocation, Entity> SOURCE_MODELS = new HashMap<>();
    private static final Map<PlayerModel<?>, PartPose[]> BASE_POSES = new java.util.WeakHashMap<>();
    private OverhaulClient() { }

    private static ModelPart[] bones(PlayerModel<?> model) {
        return new ModelPart[]{model.body, model.head, model.hat, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg, model.rightSleeve, model.leftSleeve,
                model.rightPants, model.leftPants, model.jacket};
    }
    public static void restorePose(PlayerModel<?> model) {
        OverhaulLegs.reset(model);
        PartPose[] saved = BASE_POSES.remove(model);
        if (saved == null) return;
        ModelPart[] parts = bones(model);
        for (int i = 0; i < parts.length; i++) parts[i].loadPose(saved[i]);
    }

    public static void receive(int id, CompoundTag state) {
        var level = Minecraft.getInstance().level;
        if (level != null && state != null && level.getEntity(id) instanceof LivingEntity entity) {
            int oldMask = mask(entity);
            entity.getPersistentData().put(OverhaulQuirk.DATA, state.copy());
            if (entity instanceof net.minecraft.world.entity.player.Player player) {
                if ((mask(entity) & 12) == 12) player.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
                else if ((oldMask & 12) == 12 && player.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING) player.setForcedPose(null);
            }
        }
    }
    public static CompoundTag data(Entity entity) { return OverhaulQuirk.data(entity); }
    public static int mask(Entity entity) { return data(entity).getInt("Mask"); }
    private static float age(Entity entity, float partial) { return entity.level().getGameTime() - data(entity).getLong("Start") + partial; }
    public static boolean animating(Entity entity) {
        return data(entity).getInt("Animation") != 0 && age(entity, 0) < data(entity).getInt("Duration");
    }
    public static boolean missingArm(Entity entity, boolean left) {
        return (mask(entity) & (left ? OverhaulQuirk.LEFT_ARM : OverhaulQuirk.RIGHT_ARM)) != 0;
    }

    public static void posePlayer(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!animating(entity)) {
            if ((mask(entity) & 12) == 12) {
                float stride = Mth.sin(ageInTicks * 0.3F) * Math.min(0.4F, (float) entity.getDeltaMovement().horizontalDistance() * 4);
                model.rightArm.xRot = -1.4F + stride;
                model.leftArm.xRot = -1.4F - stride;
                model.head.xRot = -0.2F;
                model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
            }
            hideClothes(model, entity);
            return;
        }
        float partial = ageInTicks - entity.tickCount;
        float time = age(entity, partial);
        int animation = data(entity).getInt("Animation");
        var clip = OverhaulAnimations.clip(animation);
        if (clip == null) return;
        ModelPart[] parts = bones(model);
        PartPose[] original = new PartPose[parts.length];
        for (int i = 0; i < parts.length; i++) original[i] = parts[i].storePose();
        BASE_POSES.put(model, original);
        rotation(model.body, OverhaulAnimations.values(clip, "body", time, false));
        rotation(model.head, OverhaulAnimations.values(clip, "head", time, false));
        rotation(model.rightLeg, OverhaulAnimations.values(clip, "rightLeg", time, false));
        rotation(model.leftLeg, OverhaulAnimations.values(clip, "leftLeg", time, false));
        rotation(model.rightArm, armPose(entity, false, time));
        rotation(model.leftArm, armPose(entity, true, time));
        float cos = Mth.cos(model.body.xRot), sin = Mth.sin(model.body.xRot);
        model.body.y += 12 * (1 - cos); model.body.z -= 12 * sin;
        model.head.y += 12 * (1 - cos); model.head.z -= 12 * sin;
        model.head.xRot += model.body.xRot;
        for (ModelPart arm : new ModelPart[]{model.rightArm, model.leftArm}) {
            arm.y += 10 * (1 - cos); arm.z -= 10 * sin;
            arm.xRot += model.body.xRot;
        }
        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm); model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg); model.leftPants.copyFrom(model.leftLeg); model.jacket.copyFrom(model.body);
        hideClothes(model, entity);
        if (animation == OverhaulQuirk.SLAM || animation == OverhaulQuirk.REASSEMBLE) OverhaulLegs.pose(model, clip, time);
    }
    private static void rotation(ModelPart part, float[] pose) {
        part.xRot = pose[0]; part.yRot = pose[1]; part.zRot = pose[2];
    }
    private static float[] armPose(LivingEntity entity, boolean left, float time) {
        int animation = data(entity).getInt("Animation");
        boolean mirror = missingArm(entity, false)
                && (animation == OverhaulQuirk.RECOVER_SELF || animation == OverhaulQuirk.ERADICATE);
        var clip = OverhaulAnimations.clip(animation);
        float[] pose = OverhaulAnimations.values(clip, left != mirror ? "leftArm" : "rightArm", time, false);
        if (mirror) { pose[1] = -pose[1]; pose[2] = -pose[2]; }
        if (animation == OverhaulQuirk.RECOVER_OTHER || animation == OverhaulQuirk.FUSE
                || animation == OverhaulQuirk.ERADICATE && left == mirror) {
            float reach = ease(time / 10) * (1 - ease((time - data(entity).getInt("Duration") + 10) / 10));
            pose[0] += (reachPitch(entity) + Mth.HALF_PI) * reach;
        }
        return pose;
    }
    private static float reachPitch(LivingEntity entity) {
        var target = entity.level().getEntity(data(entity).getInt("Target"));
        if (target == entity || target == null && data(entity).getInt("Animation") != OverhaulQuirk.FUSE) return -1.45F;
        var state = data(entity);
        var point = target == null ? new net.minecraft.world.phys.Vec3(state.getDouble("X"), state.getDouble("Y"), state.getDouble("Z")) : target.getEyePosition();
        double dy = point.y - (entity.getY() + entity.getBbHeight() * 0.75);
        double distance = Math.max(0.3, point.subtract(entity.position()).horizontalDistance());
        return (float) (-Math.PI / 2 - Math.atan2(dy, distance));
    }
    private static void hideClothes(PlayerModel<?> model, Entity entity) {
        model.leftSleeve.visible = model.leftArm.visible;
        model.rightSleeve.visible = model.rightArm.visible;
        model.leftPants.visible = model.leftLeg.visible;
        model.rightPants.visible = model.rightLeg.visible;
    }
    public static void poseLimbs(HumanoidModel<?> model, LivingEntity entity, float time) {
        ModelPart[] parts = {model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        int mask = mask(entity);
        if (!(entity instanceof net.minecraft.world.entity.player.Player) && (mask & 12) == 12) {
            float stride = Mth.sin(time * 0.3F) * Math.min(0.4F, (float) entity.getDeltaMovement().horizontalDistance() * 4);
            model.rightArm.xRot = -1.4F + stride; model.leftArm.xRot = -1.4F - stride;
        }
        var state = data(entity);
        int swelling = state.getInt("Swelling") - 1;
        float progress = (float) (entity.level().getGameTime() - state.getLong("SwellStart")) + time - entity.tickCount;
        for (int i = 0; i < parts.length; i++) {
            if ((mask & (1 << i)) != 0) { parts[i].visible = false; parts[i].xScale = parts[i].yScale = parts[i].zScale = 0; }
            else if (swelling == i && progress >= 0 && progress < 12) {
                float amount = 1 + ease(progress / 12) * 2;
                parts[i].xScale *= amount; parts[i].zScale *= amount;
                parts[i].yScale *= 1 + ease(progress / 12) * 0.2F;
                parts[i].zRot += Mth.sin(progress * 2.5F) * 0.08F;
            }
        }
    }
    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static float lower(Entity entity, float partial) {
        var clip = OverhaulAnimations.clip(data(entity).getInt("Animation"));
        if (data(entity).getInt("Animation") == OverhaulQuirk.SLAM || data(entity).getInt("Animation") == OverhaulQuirk.REASSEMBLE)
            return OverhaulAnimations.kneelingDrop(clip, age(entity, partial)) / 16;
        return -OverhaulAnimations.values(clip, "root", age(entity, partial), true)[1] / 16;

    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void lowerBody(RenderPlayerEvent.Pre event) {
        if (event.isCanceled() || !animating(event.getEntity())) return;
        event.getPoseStack().pushPose(); ROOT_TRANSFORMS.push(event.getEntity().getUUID());
        event.getPoseStack().translate(0, -lower(event.getEntity(), event.getPartialTick()), 0);
    }
    @SubscribeEvent public static void restoreBody(RenderPlayerEvent.Post event) {
        if (!ROOT_TRANSFORMS.isEmpty() && ROOT_TRANSFORMS.peek().equals(event.getEntity().getUUID())) {
            ROOT_TRANSFORMS.pop(); event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void crawlingMob(RenderLivingEvent.Pre<?, ?> event) {
        var entity = event.getEntity();
        if (event.isCanceled() || entity instanceof net.minecraft.world.entity.player.Player || (mask(entity) & 12) != 12
                || !(event.getRenderer().getModel() instanceof HumanoidModel<?>)) return;
        var poses = event.getPoseStack(); poses.pushPose(); ROOT_TRANSFORMS.push(entity.getUUID());
        float yaw = Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot);
        poses.translate(0, 0.3, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        poses.mulPose(Axis.XP.rotationDegrees(-90));
        poses.mulPose(Axis.YP.rotationDegrees(yaw - 180));
    }
    @SubscribeEvent public static void restoreMob(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)
                && !ROOT_TRANSFORMS.isEmpty() && ROOT_TRANSFORMS.peek().equals(event.getEntity().getUUID())) {
            ROOT_TRANSFORMS.pop(); event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void hands(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int bit = event.getHand() == InteractionHand.MAIN_HAND ? OverhaulQuirk.RIGHT_ARM : OverhaulQuirk.LEFT_ARM;
        if ((mask(player) & bit) != 0) { event.setCanceled(true); return; }
        if (!animating(player)) return;
        event.setCanceled(true);
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        boolean left = event.getHand() == InteractionHand.OFF_HAND;
        ModelPart arm = left ? model.leftArm : model.rightArm, sleeve = left ? model.leftSleeve : model.rightSleeve;
        PartPose oldArm = arm.storePose(), oldSleeve = sleeve.storePose();
        boolean armVisible = arm.visible, sleeveVisible = sleeve.visible;
        float ax = arm.xScale, ay = arm.yScale, az = arm.zScale, sx = sleeve.xScale, sy = sleeve.yScale, sz = sleeve.zScale;
        arm.visible = sleeve.visible = true;
        arm.xScale = arm.yScale = arm.zScale = 1;
        float time = age(player, event.getPartialTick());
        rotation(arm, armPose(player, left, time));
        arm.x = arm.y = arm.z = 0;
        sleeve.copyFrom(arm);
        PoseStack poses = event.getPoseStack(); poses.pushPose();
        poses.translate(left ? -0.72 : 0.72, -0.6, -0.35); poses.scale(1, -1, 1);
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        arm.render(poses, buffer, event.getPackedLight(), 0); sleeve.render(poses, buffer, event.getPackedLight(), 0);
        poses.popPose(); arm.loadPose(oldArm); sleeve.loadPose(oldSleeve);
        arm.visible = armVisible; sleeve.visible = sleeveVisible;
        arm.xScale = ax; arm.yScale = ay; arm.zScale = az; sleeve.xScale = sx; sleeve.yScale = sy; sleeve.zScale = sz;
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) if (event.getSkin(skin) instanceof PlayerRenderer renderer)
            renderer.addLayer(new FusionArms(renderer));
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        SOURCE_MODELS.clear(); ROOT_TRANSFORMS.clear(); BASE_POSES.clear(); OverhaulLegs.clear();
    }
    private static final class FusionArms extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        FusionArms(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }
        @Override public void render(PoseStack poses, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                                     float swing, float amount, float partial, float ticks, float yaw, float pitch) {
            if (player.isInvisible()) return;
            for (String list : List.of("Fusions", "DetachedArms")) {
                boolean detached = list.equals("DetachedArms");
                float fall = detached ? Math.max(0, player.level().getGameTime() - data(player).getLong("DetachStart") + partial) : 0;
                if (detached && fall >= OverhaulQuirk.DETACH_TICKS) continue;
                var fusions = data(player).getList(list, Tag.TAG_COMPOUND);
                for (int index = 0; index < Math.min(2, fusions.size()); index++) {
                    CompoundTag source = fusions.getCompound(index);
                    ResourceLocation texture = player.getSkin().texture();
                    HumanoidModel<?> sourceModel = getParentModel();
                    if (source.hasUUID("UUID") && source.getString("EntityType").equals("minecraft:player")) {
                        GameProfile profile = source.contains("Profile") ? ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, source.get("Profile"))
                                .result().map(ResolvableProfile::gameProfile).orElse(null) : null;
                        if (profile == null) profile = new GameProfile(source.getUUID("UUID"), source.getString("Name"));
                        texture = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile).texture();
                    } else {
                        ResourceLocation type = ResourceLocation.tryParse(source.getString("EntityType"));
                        if (type != null) {
                            Entity example = SOURCE_MODELS.compute(type, (key, prior) -> prior != null && prior.level() == player.level()
                                    ? prior : BuiltInRegistries.ENTITY_TYPE.get(key).create(player.level()));
                            if (example != null) {
                                var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(example);
                                texture = renderer.getTextureLocation(example);
                                if (renderer instanceof LivingEntityRenderer<?, ?> living && living.getModel() instanceof HumanoidModel<?> humanoid)
                                    sourceModel = humanoid;
                            }
                        }
                    }
                    float grow = !detached && data(player).getInt("Animation") == OverhaulQuirk.FUSE && index == fusions.size() - 1
                            ? ease((age(player, partial) - 28) / 16) : 1;
                    if (grow <= 0) continue;
                    for (int side = 0; side < 2; side++) {
                        ModelPart arm = side == 0 ? sourceModel.leftArm : sourceModel.rightArm;
                        PartPose old = arm.storePose(); boolean visible = arm.visible;
                        float sx = arm.xScale, sy = arm.yScale, sz = arm.zScale;
                        arm.visible = true; arm.xScale = arm.zScale = grow; arm.yScale = grow;
                        arm.loadPose(PartPose.offsetAndRotation(side == 0 ? 6 : -6, 5 + index * 4, 2.5F,
                                0.25F + Mth.sin(ticks * 0.1F + side) * 0.08F, 0, side == 0 ? -0.3F : 0.3F));
                        poses.pushPose(); getParentModel().body.translateAndRotate(poses);
                        float opacity = 1;
                        if (detached) {
                            poses.translate(arm.x / 16, arm.y / 16, arm.z / 16);
                            arm.x = arm.y = arm.z = 0;
                            poses.translate((side == 0 ? 1 : -1) * fall * 0.015, Math.min(0.95, fall * fall * 0.002), 0);
                            poses.mulPose(Axis.XP.rotationDegrees(fall * 9));
                            poses.mulPose(Axis.ZP.rotationDegrees((side == 0 ? 1 : -1) * fall * 5));
                            opacity = 1 - ease((fall - 20) / 10);
                        }
                        arm.render(poses, buffer.getBuffer(detached ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture)), light,
                                LivingEntityRenderer.getOverlayCoords(player, 0), (Math.round(opacity * 255) << 24) | 0xFFFFFF);
                        poses.popPose(); arm.loadPose(old); arm.visible = visible;
                        arm.xScale = sx; arm.yScale = sy; arm.zScale = sz;
                    }
                }
            }
        }
    }
}

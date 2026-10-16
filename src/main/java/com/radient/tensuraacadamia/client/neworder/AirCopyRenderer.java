package com.radient.tensuraacadamia.client.neworder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBody;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderBodyMoves;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderCopy;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderPayloads;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderRule;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderRules;
import com.radient.tensuraacadamia.ability.unique.quirks.neworder.NewOrderStrikes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Debug;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public final class AirCopyRenderer {

    private static final int STRIKE_TICKS = 8;

    //gets this before configs?
//    private static final float NewOrderCopy.getCopyScale() = NewOrderCopy.getCopyScale();

    private static final float COPY_BACK = NewOrderCopy.COPY_BACK;
    private static final float COPY_LUNGE = 0.6F;
    private static final float COPY_LIFT = NewOrderCopy.COPY_LIFT;
    private static final float FEET = 1.501F;
    private static final float HIPS = 0.75F;
    private static final float LEG = 0.75F;
    private static final float HAND = 10.0F / 16.0F;
    private static final float INSIDE_EASE = 0.2F;
    private static final float MODEL_SCALE = 0.9375F;
    private static final int DISPERSE_TICKS = 16;
    private static final int REFORM_PAUSE = 10;
    private static final int BUILD_TICKS = 30;
    private static final double BUILD_REACH = 2.2D;
    private static final float LEFT_WIND_SHIFT = 0.5F;
    private static final int WIND_COLOR = FastColor.ARGB32.color(150, 225, 245, 255);
    private static final int SKIN_COLOR = FastColor.ARGB32.color(80, 215, 240, 255);
    private static final float OWN_ARMS = 0.5F;

    private static final float SPEAR_LENGTH = 1.9F;
    private static final float SPEAR_HELD = 0.55F;
    private static final float SPEAR_HALF = 0.08F;
    private static final float SPEAR_HEAD = 0.45F;
    private static final float SPEAR_HEAD_HALF = 0.19F;
    private static final int SPEAR_PIECES = 7;
    private static final int SPEAR_CORE = FastColor.ARGB32.color(150, 210, 238, 255);
    private static final float SPEAR_SINK = 0.15F;
    private static final Vec3 SPEAR_FORWARD = new Vec3(0.0D, 0.2D, -1.0D).normalize();
    private static final Vec3 DOWN = new Vec3(0.0D, 1.0D, 0.0D);

    private static final float BALL_SIZE = 1.1F;

    // Formation and other info
    private static final class Copy {
        private long start;
        private long gone = -1L;
        private float inside;
        private float insideO;
        private int action = -1;
        private long actionStart;
        private int charge;
        private int ticks;
        private Vec3 point = Vec3.ZERO;
        private @Nullable Matrix4f torso;
        private @Nullable Matrix4f applied;
        private @Nullable Vec3 rightHand;
        private @Nullable Vec3 leftHand;
        private @Nullable Vec3 grip;
        private Motion motion = Motion.NONE;

        private Copy(long start) {
            this.start = start;
        }
    }

    private record Motion(float weight, float twist, float lean, float front, float back) {
        private static final Motion NONE = new Motion(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private static final Map<Integer, Long> STRIKES = new HashMap<>();
    private static final Map<Integer, Copy> COPIES = new HashMap<>();
    private static final Set<Integer> LIFTED = new HashSet<>();

    private static boolean inLevel;

    private static boolean posingCopy;

    private AirCopyRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        long time = level.getGameTime();
        for (NewOrderPayloads.GhostStrikePayload strike : NewOrderPayloads.getClientStrikes()) {
            STRIKES.put(strike.userId(), time);
        }
        NewOrderPayloads.getClientStrikes().clear();
        STRIKES.values().removeIf(start -> time - start > STRIKE_TICKS || start > time);

        for (Player player : level.players()) {
            boolean harden = NewOrderRules.hasRule(player, NewOrderRule.HARDEN);
            boolean inside = harden && NewOrderBody.isInside(player);
            NewOrderBody.setBody(player, inside);
            Copy copy = COPIES.get(player.getId());
            if (!harden) {
                if (copy != null) {
                    copy.insideO = copy.inside;
                    copy.inside += -copy.inside * INSIDE_EASE;
                    if (copy.start != Long.MAX_VALUE) {
                        disperse(level, player, copy, time);
                        copy.start = Long.MAX_VALUE;
                    }
                }
                continue;
            }

            if (copy == null || copy.start == Long.MAX_VALUE) {
                copy = new Copy(copy == null && player.tickCount < 5 ? time - BUILD_TICKS : time);
                COPIES.put(player.getId(), copy);
            }
            if (time == copy.start) {
                formSound(level, player, copy);
            }

            copy.insideO = copy.inside;
            copy.inside += ((inside ? 1.0F : 0.0F) - copy.inside) * INSIDE_EASE;
            if (copy.action >= 0 && time - copy.actionStart > copy.ticks) {
                copy.action = -1;
            }
            if (!player.isInvisible() && time >= copy.start) {
                tickCopy(level, player, copy, time - copy.start, time);
                if (copy.action >= 0) {
                    gatherAir(level, player, copy, time - copy.actionStart);
                }
            }
        }
        COPIES.values().removeIf(copy -> copy.start == Long.MAX_VALUE && time - copy.gone > DISPERSE_TICKS);

        for (NewOrderPayloads.CopyActionPayload action : NewOrderPayloads.getClientActions()) {
            Copy copy = COPIES.get(action.userId());
            if (copy == null) {
                continue;
            }

            if (action.action() == NewOrderCopy.ACTION_REFORM) {
                if (level.getEntity(action.userId()) instanceof Player player) {
                    disperse(level, player, copy, time);
                }
                copy.start = time + DISPERSE_TICKS + REFORM_PAUSE;
                copy.action = -1;
            } else {
                copy.action = action.action();
                copy.actionStart = time;
                copy.charge = action.charge();
                copy.ticks = action.ticks();
                copy.point = action.point();
                copy.rightHand = null;
                copy.leftHand = null;
                copy.grip = null;
            }
        }
        NewOrderPayloads.getClientActions().clear();
        COPIES.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
    }

    private static void gatherAir(ClientLevel level, Player player, Copy copy, long age) {
        double unit = NewOrderCopy.getCopyScale() * MODEL_SCALE * player.getScale();
        switch (copy.action) {
            case NewOrderCopy.ACTION_FIST_BUMP -> {
                if (age < NewOrderBodyMoves.BUMP_WIND && copy.rightHand != null) {
                    AirImpacts.gather(level, copy.rightHand, unit * 0.9D, 2, (float) (unit * 0.12D));
                }
            }
            case NewOrderCopy.ACTION_SPEAR -> {
                if (age < NewOrderBodyMoves.SPEAR_FORM && copy.grip != null) {
                    AirImpacts.gather(level, copy.grip, unit * 0.8D, 3, (float) (unit * 0.1D));
                }
            }
            case NewOrderCopy.ACTION_CLAP -> {
                if (age < NewOrderBodyMoves.CLAP_HIT) {
                    for (Vec3 hand : new Vec3[]{copy.rightHand, copy.leftHand}) {
                        if (hand != null) {
                            AirImpacts.gather(level, hand, unit * 0.7D, 1, (float) (unit * 0.1D));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    // Blow apart effect
    private static void disperse(ClientLevel level, Player player, Copy copy, long time) {
        copy.gone = time;
        copy.action = -1;
        if (player.isInvisible()) {
            return;
        }

        Vec3 center = getCopyCenter(player, copy);
        double size = player.getBbHeight() * NewOrderCopy.getCopyScale();
        double width = player.getBbWidth() * NewOrderCopy.getCopyScale();
        level.addParticle(ParticleTypes.GUST, center.x, center.y, center.z, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 40; i++) {
            Vec3 at = center.add((level.random.nextDouble() - 0.5D) * width, (level.random.nextDouble() - 0.5D) * size, (level.random.nextDouble() - 0.5D) * width);
            Vec3 drift = at.subtract(center).normalize().scale(0.12D + level.random.nextDouble() * 0.12D).add(0.0D, 0.05D, 0.0D);
            level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, drift.x, drift.y, drift.z);
        }
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 1.2F, 0.6F, false);
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.5F, 1.3F, false);
    }

    private static void formSound(ClientLevel level, Player player, Copy copy) {
        Vec3 center = getCopyCenter(player, copy);
        level.playLocalSound(center.x, center.y, center.z, SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.0F, 0.6F, false);
    }

    private static Vec3 getCopyCenter(Player player, Copy copy) {
        return NewOrderCopy.getCopyFeet(player, copy.inside).add(0.0D, player.getBbHeight() * NewOrderCopy.getCopyScale() * 0.5D, 0.0D);
    }
    // Creates an effect like air being pulled in during the formation
    private static void tickCopy(ClientLevel level, Player player, Copy copy, long age, long time) {
        Vec3 center = getCopyCenter(player, copy);
        double size = player.getBbHeight() * NewOrderCopy.getCopyScale();
        if (age < BUILD_TICKS) {
            double reach = BUILD_REACH * player.getScale();
            for (int i = 0; i < 4; i++) {
                double angle = level.random.nextDouble() * Mth.TWO_PI;
                double height = (level.random.nextDouble() - 0.5D) * size;
                Vec3 from = center.add(Math.cos(angle) * reach, height, Math.sin(angle) * reach);
                Vec3 pull = center.add(0.0D, height * 0.5D, 0.0D).subtract(from).scale(0.1D);
                level.addParticle(ParticleTypes.CLOUD, from.x, from.y, from.z, pull.x - Math.sin(angle) * 0.08D, pull.y, pull.z + Math.cos(angle) * 0.08D);
            }
            return;
        }

        if (age == BUILD_TICKS) {
            level.addParticle(ParticleTypes.GUST, center.x, center.y, center.z, 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < 16; i++) {
                double angle = i * Mth.TWO_PI / 16.0D;
                level.addParticle(ParticleTypes.CLOUD, center.x, center.y - size * 0.3D, center.z, Math.cos(angle) * 0.25D, 0.02D, Math.sin(angle) * 0.25D);
            }
            level.playLocalSound(center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.7F, 0.8F, false);
        }

        if (time % 3L == 0L) {
            double width = player.getBbWidth() * NewOrderCopy.getCopyScale();
            Vec3 at = center.add((level.random.nextDouble() - 0.5D) * width, (level.random.nextDouble() - 0.5D) * size + 0.3D, (level.random.nextDouble() - 0.5D) * width);
            level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, 0.0D, 0.01D, 0.0D);
        }
    }

    private static float getBuild(Player player, @Nullable Copy copy, float partialTick) {
        if (copy == null || player.level() == null) {
            return 1.0F;
        }
        return Mth.clamp((player.level().getGameTime() - copy.start + partialTick) / BUILD_TICKS, 0.0F, 1.0F);
    }

    private static float smooth(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float strike(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return clamped * clamped;
    }

    // Action posing hold time
    // ts was so annoying
    private static float hold(float age, Copy copy, float in, float out) {
        return smooth(age / in) * (1.0F - smooth((age - (copy.ticks - out)) / out));
    }

    private static void turn(ModelPart part, float weight, float x, float y, float z) {
        part.xRot = Mth.lerp(weight, part.xRot, x);
        part.yRot = Mth.lerp(weight, part.yRot, y);
        part.zRot = Mth.lerp(weight, part.zRot, z);
    }

    private static float swing(float wind, float hit, float rest, float wound, float struck) {
        return Mth.lerp(hit, Mth.lerp(wind, rest, wound), struck);
    }

    private static void copyArms(PlayerModel<?> model) {
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.hat.copyFrom(model.head);
    }

    // Thunder Strike poses
    private static Motion poseThunder(PlayerModel<?> model, float age, Copy copy, float headPitch) {
        float weight = smooth(age / NewOrderStrikes.THUNDER_GRAB) * (1.0F - smooth((age - (copy.ticks - 4)) / 4.0F));
        float hurl = smooth((age - copy.charge) / 3.0F);
        float x = Mth.lerp(hurl, -Mth.PI * 0.95F, -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD + 0.15F);
        turn(model.rightArm, weight, x, model.head.yRot * hurl, 0.15F * (1.0F - hurl));
        copyArms(model);
        return Motion.NONE;
    }

    // Solar Beam poses
    private static Motion poseSolar(PlayerModel<?> model, float age, Copy copy, float headPitch) {
        float weight = smooth(age / 6.0F) * (1.0F - smooth((age - (copy.ticks - 8)) / 8.0F));
        float fire = smooth((age - copy.charge) / 3.0F);
        float x = Mth.lerp(fire, -Mth.PI * 0.92F, -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD);
        float spread = 0.3F * (1.0F - fire);
        float inward = 0.12F * fire;
        float aim = model.head.yRot * fire;
        turn(model.rightArm, weight, x, aim - inward, spread);
        turn(model.leftArm, weight, x, aim + inward, -spread);
        copyArms(model);
        return Motion.NONE;
    }

    // Fist Bump To The Earth poses
    private static Motion poseFistBump(PlayerModel<?> model, float age, Copy copy) {
        float weight = hold(age, copy, 3.0F, 8.0F);
        float wind = smooth(age / NewOrderBodyMoves.BUMP_WIND);
        float hit = strike((age - NewOrderBodyMoves.BUMP_WIND) / (NewOrderBodyMoves.BUMP_HIT - NewOrderBodyMoves.BUMP_WIND));
        float shudder = age > NewOrderBodyMoves.BUMP_HIT ? Mth.sin((age - NewOrderBodyMoves.BUMP_HIT) * 2.6F) * 0.05F * (1.0F - smooth((age - NewOrderBodyMoves.BUMP_HIT) / 6.0F)) : 0.0F;
        turn(model.rightArm, weight, swing(wind, hit, 0.0F, 1.35F, -0.95F + shudder), swing(wind, hit, 0.0F, 0.35F, -0.1F), swing(wind, hit, 0.0F, 0.7F, 0.15F));
        turn(model.leftArm, weight, swing(wind, hit, 0.0F, -1.4F, 0.75F), swing(wind, hit, 0.0F, 0.25F, 0.0F), swing(wind, hit, 0.0F, -0.1F, -0.4F));
        turn(model.head, weight, swing(wind, hit, model.head.xRot, 0.05F, 0.5F), swing(wind, hit, model.head.yRot, -0.35F, 0.25F), 0.0F);
        float stretch = 1.0F + 0.5F * hit * weight;
        float swell = 1.0F + (0.15F * wind + 0.15F * hit) * weight;
        model.rightArm.xScale = swell;
        model.rightArm.zScale = swell;
        model.rightArm.yScale = stretch;
        copyArms(model);
        return new Motion(weight, swing(wind, hit, 0.0F, 0.6F, -0.35F), swing(wind, hit, 0.0F, -0.15F, 0.5F) + shudder, swing(wind, hit, 0.0F, 0.2F, 0.9F),
                swing(wind, hit, 0.0F, 0.15F, 0.65F));
    }

    // Air Spear poses
    // god knows this took me so long
    // learning poses will be good tho
    private static Motion poseSpear(PlayerModel<?> model, float age, Copy copy) {
        float weight = hold(age, copy, 3.0F, 8.0F);
        float raise = smooth((age - NewOrderBodyMoves.SPEAR_FORM) / (NewOrderBodyMoves.SPEAR_RAISE - NewOrderBodyMoves.SPEAR_FORM));
        float plunge = strike((age - NewOrderBodyMoves.SPEAR_RAISE) / (NewOrderBodyMoves.SPEAR_HIT - NewOrderBodyMoves.SPEAR_RAISE));
        float x = swing(raise, plunge, -1.35F, -2.85F, -0.7F);
        float y = swing(raise, plunge, -0.5F, -0.1F, -0.75F);
        float z = swing(raise, plunge, 0.0F, 0.5F, 0.0F);
        turn(model.rightArm, weight, x, y, z);
        turn(model.leftArm, weight, x, -y, -z);
        turn(model.head, weight, swing(raise, plunge, 0.15F, -0.3F, 0.6F), 0.0F, 0.0F);
        copyArms(model);
        return new Motion(weight, 0.0F, swing(raise, plunge, 0.08F, -0.15F, 0.4F), 0.5F * plunge, 0.35F * plunge);
    }

    // Wind Clap
    // Couldnt find a way to do from above so side to side it is
    private static Motion poseClap(PlayerModel<?> model, float age, Copy copy, float headPitch) {
        float weight = hold(age, copy, 4.0F, 6.0F);
        float clap = strike((age - NewOrderBodyMoves.CLAP_SPREAD) / (NewOrderBodyMoves.CLAP_HIT - NewOrderBodyMoves.CLAP_SPREAD));
        float open = smooth(age / NewOrderBodyMoves.CLAP_SPREAD);
        float x = -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD * 0.5F;
        float spread = Mth.lerp(clap, 1.3F * open, -0.3F);
        turn(model.rightArm, weight, x, model.head.yRot + spread, 0.0F);
        turn(model.leftArm, weight, x, model.head.yRot - spread, 0.0F);
        copyArms(model);
        return new Motion(weight, 0.0F, Mth.lerp(clap, -0.12F * open, 0.2F), 0.35F * clap, 0.25F * clap);
    }

    private static Motion pose(PlayerModel<?> model, Copy copy, float age, float headPitch) {
        return switch (copy.action) {
            case NewOrderCopy.ACTION_THUNDER -> poseThunder(model, age, copy, headPitch);
            case NewOrderCopy.ACTION_SOLAR -> poseSolar(model, age, copy, headPitch);
            case NewOrderCopy.ACTION_FIST_BUMP -> poseFistBump(model, age, copy);
            case NewOrderCopy.ACTION_SPEAR -> poseSpear(model, age, copy);
            case NewOrderCopy.ACTION_CLAP -> poseClap(model, age, copy, headPitch);
            default -> Motion.NONE;
        };
    }

    // Leg movement
    private static void stance(PlayerModel<?> model, Motion motion) {
        float weight = motion.weight();
        float lean = motion.lean() * weight;
        model.leftLeg.xRot = Mth.lerp(weight, model.leftLeg.xRot, -lean - motion.front() * weight);
        model.rightLeg.xRot = Mth.lerp(weight, model.rightLeg.xRot, -lean + motion.back() * weight);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    private static void move(PoseStack poseStack, PlayerModel<?> model, Motion motion) {
        if (motion.weight() <= 0.0F) {
            return;
        }

        float weight = motion.weight();
        float lean = motion.lean() * weight;
        float front = motion.front() * weight;
        float back = motion.back() * weight;
        stance(model, motion);
        poseStack.translate(0.0F, HIPS + LEG * (1.0F - Mth.cos(Math.max(front, back))), 0.0F);
        poseStack.mulPose(Axis.YP.rotation(motion.twist() * weight));
        poseStack.mulPose(Axis.XP.rotation(lean));
        poseStack.translate(0.0F, -HIPS, 0.0F);
    }

    private static boolean isBodyMove(int action) {
        return action == NewOrderCopy.ACTION_FIST_BUMP || action == NewOrderCopy.ACTION_SPEAR || action == NewOrderCopy.ACTION_CLAP;
    }

    private static @Nullable Copy getBodyMove(Player player, float partialTick) {
        Copy copy = COPIES.get(player.getId());
        return copy != null && isBodyMove(copy.action) && Mth.lerp(partialTick, copy.insideO, copy.inside) >= 0.5F ? copy : null;
    }

    public static void poseModel(PlayerModel<?> model, LivingEntity entity, float ageInTicks, float headPitch) {
        if (posingCopy || !(entity instanceof Player player) || player.level() == null) {
            return;
        }

        float partialTick = Mth.frac(ageInTicks);
        Copy copy = getBodyMove(player, partialTick);
        if (copy == null) {
            return;
        }

        stance(model, pose(model, copy, player.level().getGameTime() - copy.actionStart + partialTick, headPitch));
        for (ModelPart arm : new ModelPart[]{model.rightArm, model.rightSleeve, model.leftArm, model.leftSleeve}) {
            arm.xScale = 1.0F;
            arm.yScale = 1.0F;
            arm.zScale = 1.0F;
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (Minecraft.getInstance().player instanceof Player player && getBodyMove(player, event.getPartialTick()) != null) {
            event.setCanceled(true);
        }
    }

    private static Matrix4f getModelToPlayer(Player player, float partialTick, float inside) {
        float scale = player.getScale();
        return new Matrix4f().translate(0.0F, NewOrderBody.getBodyRide() * scale * inside, 0.0F).scale(scale)
                .rotateY((180.0F - Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot)) * Mth.DEG_TO_RAD)
                .scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE).translate(0.0F, -FEET, 0.0F);
    }

    private record Space(Matrix4f toPlayer, Vec3 origin) {
        private Vec3 toWorld(Vec3 local) {
            Vector3f at = this.toPlayer.transformPosition((float) local.x, (float) local.y, (float) local.z, new Vector3f());
            return this.origin.add(at.x, at.y, at.z);
        }

        private Vec3 toLocal(Vec3 world) {
            Vec3 relative = world.subtract(this.origin);
            Vector3f at = new Matrix4f(this.toPlayer).invert().transformPosition((float) relative.x, (float) relative.y, (float) relative.z, new Vector3f());
            return new Vec3(at.x, at.y, at.z);
        }
    }

    private static Matrix4f getChest(Matrix4f start, PoseStack poseStack, ModelPart body)
    {
        Matrix4f copyMove = new Matrix4f(start).invert().mul(poseStack.last().pose());
        PoseStack chest = new PoseStack();

        chest.mulPose(copyMove);
        body.translateAndRotate(chest);
        return chest.last().pose();
    }

    // helps with the crouch fix on body of air
    private static Matrix4f getRestTorso(float inside)
    {
       float up = FEET - COPY_LIFT + NewOrderBody.getBodyRide() * inside / MODEL_SCALE;
       float back = Mth.lerp(inside, COPY_BACK, 0.0F);

       return new
               Matrix4f().translate(0.0F, up, back).scale(NewOrderCopy.getCopyScale()).translate(0.0F, -FEET, 0.0F);
    }

    private static void renderCopy(PoseStack poseStack, MultiBufferSource buffers, int light, Player player, PlayerModel<AbstractClientPlayer> model, PlayerSkin skin, Copy copy,
                                   float partialTick, float ageInTicks, float headPitch, float offset, float inside, float build, float disperse, boolean armsOnly) {
        float age = copy.action >= 0 ? player.level().getGameTime() - copy.actionStart + partialTick : 0.0F;
        Motion motion = copy.action >= 0 && disperse < 0.0F ? pose(model, copy, age, headPitch) : Motion.NONE;
        copy.motion = motion;
        Matrix4f entry = new Matrix4f(poseStack.last().pose());
        poseStack.pushPose();
        poseStack.translate(0.0F, FEET - COPY_LIFT + Mth.sin(ageInTicks * 0.1F) * 0.05F + NewOrderBody.getBodyRide() * inside / MODEL_SCALE, offset);
        poseStack.scale(NewOrderCopy.getCopyScale(), NewOrderCopy.getCopyScale(), NewOrderCopy.getCopyScale());
        poseStack.translate(0.0F, -FEET, 0.0F);
        move(poseStack, model, motion);

        copy.torso = getChest(entry, poseStack, model.body);

        List<Piece> right = armsOnly ? List.of(new Piece(model.rightArm, 0.4F, 0.8F), new Piece(model.rightSleeve, 0.4F, 0.8F))
                : List.of(new Piece(model.rightLeg, 0.0F, 0.4F), new Piece(model.rightPants, 0.0F, 0.4F), new Piece(model.body, 0.2F, 0.6F),
                new Piece(model.jacket, 0.2F, 0.6F), new Piece(model.rightArm, 0.4F, 0.8F), new Piece(model.rightSleeve, 0.4F, 0.8F),
                new Piece(model.head, 0.6F, 1.0F), new Piece(model.hat, 0.6F, 1.0F));
        List<Piece> left = armsOnly ? List.of(new Piece(model.leftArm, 0.4F, 0.8F), new Piece(model.leftSleeve, 0.4F, 0.8F))
                : List.of(new Piece(model.leftLeg, 0.0F, 0.4F), new Piece(model.leftPants, 0.0F, 0.4F), new Piece(model.leftArm, 0.4F, 0.8F),
                new Piece(model.leftSleeve, 0.4F, 0.8F));
        float scroll = ageInTicks * 0.02F % 1.0F;
        int windColor = armsOnly ? AirShapes.fade(WIND_COLOR, OWN_ARMS) : WIND_COLOR;
        int skinColor = armsOnly ? AirShapes.fade(SKIN_COLOR, OWN_ARMS * 0.6F) : SKIN_COLOR;
        renderPieces(right, build, disperse, poseStack, buffers.getBuffer(RenderType.breezeWind(AirShapes.WIND, scroll, 0.0F)), light, windColor);
        renderPieces(left, build, disperse, poseStack, buffers.getBuffer(RenderType.breezeWind(AirShapes.WIND, scroll, LEFT_WIND_SHIFT)), light, windColor);
        VertexConsumer shape = buffers.getBuffer(RenderType.entityTranslucent(skin.texture()));
        renderPieces(right, build, disperse, poseStack, shape, light, skinColor);
        renderPieces(left, build, disperse, poseStack, shape, light, skinColor);

        if (copy.action >= 0 && disperse < 0.0F) {
            Matrix4f inCopy = new Matrix4f(entry).invert().mul(poseStack.last().pose());
            Space space = new Space(getModelToPlayer(player, partialTick, inside).mul(inCopy), player.getPosition(partialTick));
            renderHeld(poseStack, buffers, light, player, model, copy, age, partialTick, ageInTicks, space);
        }
        poseStack.popPose();

        for (ModelPart arm : new ModelPart[]{model.rightArm, model.rightSleeve}) {
            arm.xScale = 1.0F;
            arm.yScale = 1.0F;
            arm.zScale = 1.0F;
        }
    }

    // Where the end of an arm is
    private static Vec3 getHand(ModelPart arm, float side) {
        PoseStack scratch = new PoseStack();
        arm.translateAndRotate(scratch);
        Vector3f at = scratch.last().pose().transformPosition(side / 16.0F, HAND, 0.0F, new Vector3f());
        return new Vec3(at.x, at.y, at.z);
    }

    private static void renderHeld(PoseStack poseStack, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, Copy copy, float age, float partialTick,
                                   float ageInTicks, Space space) {
        Vec3 right = getHand(model.rightArm, -1.0F);
        Vec3 left = getHand(model.leftArm, 1.0F);
        if (inLevel) {
            copy.rightHand = space.toWorld(right);
            copy.leftHand = space.toWorld(left);
        }
        switch (copy.action) {
            case NewOrderCopy.ACTION_FIST_BUMP -> {
                float grow = smooth(age / NewOrderBodyMoves.BUMP_WIND);
                float burst = 1.0F - smooth((age - NewOrderBodyMoves.BUMP_HIT) / 2.0F);
                renderBall(poseStack, buffers, right, BALL_SIZE * (0.35F + 0.65F * grow) * (1.0F + (1.0F - burst) * 0.8F), ageInTicks * 2.0F, smooth(age / 3.0F) * burst, light);
            }
            case NewOrderCopy.ACTION_CLAP -> {
                float grow = smooth(age / NewOrderBodyMoves.CLAP_SPREAD);
                float burst = 1.0F - smooth((age - NewOrderBodyMoves.CLAP_HIT) / 2.0F);
                for (Vec3 hand : new Vec3[]{right, left}) {
                    renderBall(poseStack, buffers, hand, BALL_SIZE * 0.8F * (0.3F + 0.7F * grow), ageInTicks * 2.0F, smooth(age / 3.0F) * burst, light);
                }
            }
            case NewOrderCopy.ACTION_SPEAR -> renderSpear(poseStack, buffers, light, player, copy, age, partialTick, ageInTicks, right.add(left).scale(0.5D), space);
            default -> {
            }
        }
    }

    private static void renderBall(PoseStack poseStack, MultiBufferSource buffers, Vec3 at, float size, float ageInTicks, float alpha, int light) {
        if (alpha <= 0.02F || size <= 0.01F) {
            return;
        }

        AirShapes.charge(poseStack, buffers.getBuffer(AirShapes.charge(ageInTicks)), at, size, ageInTicks, alpha, light);
    }

    private static Vec3 turnTo(Vec3 from, Vec3 to, float t) {
        Vec3 between = from.lerp(to, t);
        return between.lengthSqr() < 1.0E-4D ? to : between.normalize();
    }

    private static void renderSpear(PoseStack poseStack, MultiBufferSource buffers, int light, Player player, Copy copy, float age, float partialTick, float ageInTicks,
                                    Vec3 grip, Space space) {
        float form = smooth(age / NewOrderBodyMoves.SPEAR_FORM);
        float alpha = form * (1.0F - smooth((age - (copy.ticks - 8)) / 8.0F));
        if (inLevel) {
            copy.grip = space.toWorld(grip);
        }
        if (alpha <= 0.01F) {
            return;
        }

        float raise = smooth((age - NewOrderBodyMoves.SPEAR_FORM) / (NewOrderBodyMoves.SPEAR_RAISE - NewOrderBodyMoves.SPEAR_FORM));
        float plunge = strike((age - NewOrderBodyMoves.SPEAR_RAISE) / (NewOrderBodyMoves.SPEAR_HIT - NewOrderBodyMoves.SPEAR_RAISE));
        Vec3 gripWorld = space.toWorld(grip);
        Vec3 target = space.toLocal(new Vec3(gripWorld.x, copy.point.y, gripWorld.z)).subtract(grip);
        Vec3 direction = turnTo(turnTo(SPEAR_FORWARD, DOWN, raise), target.normalize(), plunge);
        double below = Mth.lerp(plunge, SPEAR_LENGTH - SPEAR_HELD, target.length() + SPEAR_SINK) * form;
        double above = SPEAR_HELD * form * (1.0F - smooth((age - (copy.ticks - 8)) / 8.0F));
        Vec3 tail = grip.subtract(direction.scale(above));
        Vec3 point = grip.add(direction.scale(below));
        double length = tail.distanceTo(point);
        if (length < 0.05D) {
            return;
        }

        double head = Math.min(SPEAR_HEAD, length * 0.4D);
        Vec3 base = point.subtract(direction.scale(head));
        double shaft = tail.distanceTo(base);
        Vec3[] frame = AirShapes.frame(direction);
        PoseStack.Pose pose = poseStack.last();
        double piece = shaft / SPEAR_PIECES;
        double held = Math.min(1.0D, (grip.subtract(tail)).dot(direction) / Math.max(shaft, 1.0E-3D));
        VertexConsumer consumer = buffers.getBuffer(AirShapes.core());
        for (int i = 0; i < SPEAR_PIECES; i++) {
            double along = (i + 0.5D) / SPEAR_PIECES;
            float show = smooth(form * 2.2F - (float) Math.abs(along - held) * 2.0F);
            if (show <= 0.01F) {
                continue;
            }

            double angle = i * 0.9D;
            Vec3 side = frame[0].scale(Math.cos(angle)).add(frame[1].scale(Math.sin(angle)));
            Vec3 up = frame[1].scale(Math.cos(angle)).subtract(frame[0].scale(Math.sin(angle)));
            double half = SPEAR_HALF * (1.0D + 0.18D * ((i * 7) % 3)) * show;
            AirShapes.coreBox(consumer, pose, tail.add(direction.scale(piece * (i + 0.5D))), side, up, direction, half, half, piece * 0.62D * show, i * 0.13F, 0.0F,
                    AirShapes.fade(SPEAR_CORE, alpha), light);
        }
        float headShow = smooth(form * 2.2F - (float) Math.abs(1.0D - held) * 2.0F);
        if (headShow > 0.01F) {
            AirShapes.coreBox(consumer, pose, base, frame[0], frame[1], direction, SPEAR_HEAD_HALF * 0.75D * headShow, SPEAR_HEAD_HALF * 0.75D * headShow, 0.06D, 0.4F, 0.0F,
                    AirShapes.fade(SPEAR_CORE, alpha), light);
            AirShapes.tip(consumer, pose, base, base.add(direction.scale(head * headShow)), frame[0], frame[1], SPEAR_HEAD_HALF * headShow, AirShapes.fade(SPEAR_CORE, alpha), light);
        }

        consumer = buffers.getBuffer(RenderType.breezeWind(AirShapes.WIND, ageInTicks * 0.06F % 1.0F, 0.0F));
        int wind = AirShapes.fade(WIND_COLOR, alpha * form);
        Vec3 middle = tail.lerp(base, 0.5D);
        AirShapes.box(consumer, pose, middle, frame[0], frame[1], direction, SPEAR_HALF * 1.7D, SPEAR_HALF * 1.7D, shaft * 0.5D * form, wind, light);
        if (headShow > 0.01F) {
            AirShapes.tip(consumer, pose, base, base.add(direction.scale((head + 0.03D) * headShow)), frame[0], frame[1], SPEAR_HEAD_HALF * 1.25D * headShow, wind, light);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY || event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            inLevel = event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !(minecraft.player instanceof AbstractClientPlayer player) || minecraft.getCameraEntity() != player
                || !minecraft.options.getCameraType().isFirstPerson() || player.isInvisible()) {
            return;
        }

        Copy copy = COPIES.get(player.getId());
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float inside = copy == null ? 0.0F : Mth.lerp(partialTick, copy.insideO, copy.inside);
        if (copy == null || copy.action < 0 || inside < 0.5F || !(minecraft.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        float bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float ageInTicks = player.tickCount + partialTick;
        float limbSpeed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
        float limbPosition = player.walkAnimation.position(partialTick);
        model.attackTime = 0.0F;
        model.crouching = false;
        model.riding = false;
        model.young = false;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.prepareMobModel(player, limbPosition, limbSpeed, partialTick);
        posingCopy = true;
        model.setupAnim(player, limbPosition, limbSpeed, ageInTicks, Mth.wrapDegrees(Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyRot), headPitch);
        posingCopy = false;

        Vec3 camera = event.getCamera().getPosition();
        Vec3 at = player.getPosition(partialTick).subtract(camera);
        float scale = player.getScale();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(at.x, at.y + NewOrderBody.getBodyRide() * scale * inside, at.z);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
        poseStack.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0F, -FEET, 0.0F);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(player.level(), BlockPos.containing(player.getEyePosition(partialTick)));
        renderCopy(poseStack, buffers, light, player, model, player.getSkin(), copy, partialTick, ageInTicks, headPitch, 0.0F, inside, 1.0F, -1.0F, true);
        buffers.endBatch();
        poseStack.popPose();
    }

    // Body of air positioning
    @SubscribeEvent
    @Debug
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Copy copy = COPIES.get(player.getId());
        float inside = copy == null ? 0.0F : Mth.lerp(event.getPartialTick(), copy.insideO, copy.inside);

//        if (inside > 0.001F) {
        if (inside <= 0.001F){
            return;
        }

        Matrix4f follow = null;
        copy.applied = null;
        if (inside > 0.5F && copy.torso != null)
        {
            Matrix4f moved = new Matrix4f(copy.torso).mul(getRestTorso(inside).invert());
            Matrix4f toPlayer = getModelToPlayer(player, event.getPartialTick(), inside);
            follow = new Matrix4f(toPlayer).mul(moved).mul(new Matrix4f(toPlayer).invert());

            copy.applied = moved;
        }
//            }

            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();

            if (follow != null) {
                poseStack.mulPose(follow);
            }

//            poseStack.popPose();
            poseStack.translate(0.0F, NewOrderBody.getBodyRide() * player.getScale() * inside, 0.0F);
            LIFTED.add(player.getId());
//            Motion motion = copy.action >= 0 ? copy.motion : Motion.NONE;
//            if (motion.weight() > 0.0F) {
//                float weight = motion.weight();
//                float unit = NewOrderCopy.getCopyScale() * MODEL_SCALE * scale;
//                float hips = (COPY_LIFT - NewOrderBody.getBodyRide() * inside / MODEL_SCALE + NewOrderCopy.getCopyScale() * (FEET - HIPS)) * MODEL_SCALE * scale;
//                float drop = LEG * (1.0F - Mth.cos(Math.max(motion.front(), motion.back()) * weight)) * unit;
//                float facing = 180.0F - Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
//                poseStack.translate(0.0F, hips - drop, 0.0F);
//                poseStack.mulPose(Axis.YP.rotationDegrees(facing));
//                poseStack.mulPose(Axis.YP.rotation(-motion.twist() * weight));
//                poseStack.mulPose(Axis.XP.rotation(-motion.lean() * weight));
//                poseStack.mulPose(Axis.YP.rotationDegrees(-facing));
//                poseStack.translate(0.0F, -hips, 0.0F);
//
//            }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (LIFTED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new AirCopyLayer(renderer));
            }
        }
    }

    // 0 to 1 through the strike
    // -1 when not striking
    private static float getStrike(Player player, float partialTick) {
        Long start = STRIKES.get(player.getId());
        if (start == null || player.level() == null) {
            return -1.0F;
        }

        float age = player.level().getGameTime() - start + partialTick;
        return age < 0.0F || age > STRIKE_TICKS ? -1.0F : age / STRIKE_TICKS;
    }

    private record Piece(ModelPart part, float from, float to) {}

    // Each part of the body fades in individually
    private static void renderPieces(List<Piece> pieces, float build, float disperse, PoseStack poseStack, VertexConsumer consumer, int light, int color) {
        for (Piece piece : pieces) {
            float reveal = disperse >= 0.0F ? 1.0F - Mth.clamp((disperse - (1.0F - piece.to())) / (piece.to() - piece.from()), 0.0F, 1.0F)
                    : Mth.clamp((build - piece.from()) / (piece.to() - piece.from()), 0.0F, 1.0F);
            if (reveal <= 0.0F) {
                continue;
            }

            ModelPart part = piece.part();
            float x = part.xScale;
            float y = part.yScale;
            float z = part.zScale;
            float grow = disperse >= 0.0F ? 1.0F + 0.4F * (1.0F - reveal) : 0.4F + 0.6F * reveal;
            part.xScale = x * grow;
            part.yScale = y * grow;
            part.zScale = z * grow;
            poseStack.pushPose();
            if (disperse >= 0.0F) {
                poseStack.translate(0.0F, -0.3F * (1.0F - reveal), 0.0F);
            }
            part.render(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(Math.round(FastColor.ARGB32.alpha(color) * reveal), color));
            poseStack.popPose();
            part.xScale = x;
            part.yScale = y;
            part.zScale = z;
        }
    }

    // This if for Harden Into My Shape
    private static final class AirCopyLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private AirCopyLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Copy copy = COPIES.get(player.getId());
            if (player.isInvisible() || copy == null || player.level() == null) {
                return;
            }

            float now = player.level().getGameTime() + partialTick;
            float disperse = copy.gone >= 0L && now - copy.gone < DISPERSE_TICKS ? (now - copy.gone) / DISPERSE_TICKS : -1.0F;
            if (disperse < 0.0F && now < copy.start) {
                return;
            }

            PlayerModel<AbstractClientPlayer> model = getParentModel();
            float attackTime = model.attackTime;
            float strike = getStrike(player, partialTick);
            float inside = Mth.lerp(partialTick, copy.insideO, copy.inside);
            float back = Mth.lerp(inside, COPY_BACK, 0.0F);
            float offset = back;
            float swing = 0.0F;
            if (strike >= 0.0F) {
                float out = strike < 0.5F ? strike / 0.5F : 1.0F - (strike - 0.5F) / 0.5F;
                offset = Mth.lerp(Mth.sin(out * Mth.HALF_PI), back, -COPY_LUNGE);
                swing = Mth.clamp((strike - 0.2F) / 0.6F, 0.0F, 1.0F);
            }

            model.attackTime = copy.action >= 0 ? 0.0F : inside > 0.5F ? attackTime : swing;
            posingCopy = true;
            model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            posingCopy = false;
            poseStack.pushPose();
            if (copy.applied != null) {
                poseStack.mulPose(new Matrix4f(copy.applied).invert());
            }
            renderCopy(poseStack, buffers, light, player, model, player.getSkin(), copy, partialTick, ageInTicks, headPitch, offset, inside, getBuild(player, copy, partialTick), disperse, false);
            poseStack.popPose();
            model.attackTime = attackTime;
            model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    public static float getInside(Player player, float partialTick) {
        Copy copy = COPIES.get(player.getId());
        return copy == null ? 0.0F : Mth.lerp(partialTick, copy.insideO, copy.inside);
    }

    static void clear() {
        STRIKES.clear();
        COPIES.clear();
        LIFTED.clear();
    }

}

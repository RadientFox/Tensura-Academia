package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// Harden Into My Shape
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderCopy {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    // The construct's size
//    public static final float COPY_SCALE = 3.0F;

    //changes it to be farther behind you the larger it scales
    public static final float COPY_BACK = 1.6F / 3.0F * getCopyScale();

    public static final float COPY_LIFT = 0.15F;
    private static final double SHOULDER_HEIGHT = 22.0D / 16.0D * 0.9375D;
    private static final double SHOULDER_SIDE = 5.0D / 16.0D * 0.9375D;
    static final double ARM_REACH = 10.0D / 16.0D * 0.9375D;
    public static final int ACTION_THUNDER = 0;
    public static final int ACTION_SOLAR = 1;
    public static final int ACTION_REFORM = 2;
    public static final int ACTION_FIST_BUMP = 3;
    public static final int ACTION_SPEAR = 4;
    public static final int ACTION_CLAP = 5;
    private static final double STRIKE_RANGE = 8.0D;
    private static final int STRIKE_LEAD = 5;

    private NewOrderCopy() {
    }

    static final class Echo {
        private final ManasSkillInstance instance;
        private final LivingEntity user;
        private final LivingEntity target;
        private final long time;
        private boolean shown;

        private Echo(ManasSkillInstance instance, LivingEntity user, LivingEntity target, long time) {
            this.instance = instance;
            this.user = user;
            this.target = target;
            this.time = time;
        }
    }

    static final List<Echo> ECHOES = new ArrayList<>();
    private static boolean echoing;

    //swapped to this so its config based
    public static float getCopyScale()
    {
        return (float) CONFIG.hardenSize;
    }

    static Vec3 getForward(LivingEntity user) {
        return Vec3.directionFromRotation(0.0F, user.yBodyRot);
    }

    static Vec3 getRight(LivingEntity user) {
        Vec3 forward = getForward(user);
        return new Vec3(-forward.z, 0.0D, forward.x);
    }

    // inside is how far the copy has closed around the user, 0 to 1
    public static Vec3 getCopyFeet(LivingEntity user, float inside) {
        double scale = user.getScale();
        return user.position().add(getForward(user).scale(-COPY_BACK * scale * (1.0F - inside))).add(0.0D, COPY_LIFT * scale, 0.0D);
    }

    static double getCopyUnit(LivingEntity user) {
        return getCopyScale() * user.getScale();
    }

    static Vec3 getShoulder(LivingEntity user, boolean right) {
        Vec3 shoulders = getCopyFeet(user, NewOrderBody.isInside(user) ? 1.0F : 0.0F).add(0.0D, SHOULDER_HEIGHT * getCopyUnit(user), 0.0D);
        return right ? shoulders.add(getRight(user).scale(SHOULDER_SIDE * getCopyUnit(user))) : shoulders;
    }

    static Vec3 getRaisedHand(LivingEntity user) {
        return getShoulder(user, true).add(0.0D, ARM_REACH * getCopyUnit(user), 0.0D);
    }

    static Vec3 getReachingHand(LivingEntity user, Vec3 toward) {
        Vec3 shoulder = getShoulder(user, true);
        Vec3 direction = toward.subtract(shoulder);
        return direction.lengthSqr() < 1.0E-6D ? shoulder : shoulder.add(direction.normalize().scale(ARM_REACH * getCopyUnit(user)));
    }

    // Get the right hand back behind the player
    static Vec3 getDrawnHand(LivingEntity user) {
        return getShoulder(user, true).subtract(getForward(user).scale(ARM_REACH * getCopyUnit(user)));
    }

    //I couldnt get it like the anime to be up and down so I just spread out like a hug
    static Vec3[] getSpreadHands(LivingEntity user) {
        Vec3 shoulders = getShoulder(user, false).add(getForward(user).scale(ARM_REACH * getCopyUnit(user) * 0.4D));
        Vec3 side = getRight(user).scale((SHOULDER_SIDE + ARM_REACH) * getCopyUnit(user));
        return new Vec3[]{shoulders.add(side), shoulders.subtract(side)};
    }

    public static Vec3 getReachingHand(LivingEntity user, Vec3 toward, float inside, float partialTick) {
        double unit = getCopyUnit(user);
        Vec3 forward = Vec3.directionFromRotation(0.0F, Mth.rotLerp(partialTick, user.yBodyRotO, user.yBodyRot));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 feet = user.getPosition(partialTick).add(forward.scale(-COPY_BACK * user.getScale() * (1.0F - inside))).add(0.0D, COPY_LIFT * user.getScale(), 0.0D);
        Vec3 shoulder = feet.add(0.0D, SHOULDER_HEIGHT * unit, 0.0D).add(right.scale(SHOULDER_SIDE * unit));
        Vec3 direction = toward.subtract(shoulder);
        return direction.lengthSqr() < 1.0E-6D ? shoulder : shoulder.add(direction.normalize().scale(ARM_REACH * unit));
    }

    // This is the over the head
    static Vec3 getGather(LivingEntity user) {
        return getShoulder(user, false).add(0.0D, (ARM_REACH + 0.2D) * getCopyUnit(user), 0.0D);
    }

    // for the solar beam, holds ut arms in front/at target you're looking at
    static Vec3 getBeamOrigin(LivingEntity user) {
        return getShoulder(user, false).add(user.getViewVector(1.0F).scale(ARM_REACH * getCopyUnit(user)));
    }

    static boolean isMelee(DamageSource source, LivingEntity user) {
        return source.getDirectEntity() == user && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
                && ((TensuraDamageSource) source).tensura$getAbilityInstance() == null;
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (echoing || event.getNewDamage() <= 0.0F || !(event.getSource().getEntity() instanceof LivingEntity user) || !(user.level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity target = event.getEntity();
        if (target == user || !isMelee(event.getSource(), user) || !HitRules.canHit(user, target)) {
            return;
        }

        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(user).orElse(null);
        if (instance == null || NewOrderSeals.isSealed((Skill) instance.getSkill(), user)) {
            return;
        }

        CompoundTag fist = NewOrderRules.getEntry(instance, NewOrderRule.FIST);
        if (fist != null) {
            NewOrderFist.fists(level, instance, user, target, NewOrderShield.getWallBlock(fist));
        }

        if (!NewOrderRules.hasRule(instance, NewOrderRule.HARDEN) || NewOrderBody.isInside(instance) || ECHOES.stream().anyMatch(echo -> echo.user == user && echo.target == target)) {
            return;
        }

        ECHOES.add(new Echo(instance, user, target, level.getGameTime() + Math.max(1, CONFIG.hardenDelayTicks)));
    }

    private static void showStrike(Echo echo) {
        echo.shown = true;
        if (echo.user.isAlive() && echo.target.isAlive()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(echo.user, new NewOrderPayloads.GhostStrikePayload(echo.user.getId(), echo.target.getId()));
        }
    }

    private static void strike(Echo echo) {
        LivingEntity user = echo.user;
        LivingEntity target = echo.target;
        if (!user.isAlive() || !target.isAlive() || user.level() != target.level() || user.distanceTo(target) > STRIKE_RANGE || !(user.level() instanceof ServerLevel level)) {
            return;
        }

        DamageSource source = ((Skill) echo.instance.getSkill()).createSource(echo.instance, user, TensuraDamageTypes.WIND_ELEMENTAL, NewOrderQuirk.RULES);
        ((TensuraDamageSource) source).tensura$setElement(Element.WIND);
        float damage = NewOrderUtil.meleeDamage(user, CONFIG.hardenDamageMultiplier);
        echoing = true;
        try {
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        } finally {
            echoing = false;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
        NewOrderUtil.playSound(level, center, SoundEvents.PLAYER_ATTACK_STRONG, 0.9F, 1.3F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.5F, 1.4F);
        if (level.random.nextInt(4) == 0) {
            echo.instance.addMasteryPoint(user);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ECHOES.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            ECHOES.stream().filter(echo -> !echo.shown && time >= echo.time - STRIKE_LEAD).toList().forEach(NewOrderCopy::showStrike);
            List<Echo> ready = ECHOES.stream().filter(echo -> time >= echo.time).toList();
            ECHOES.removeAll(ready);
            ready.forEach(NewOrderCopy::strike);
        }
    }

    static void forget(LivingEntity entity) {
        ECHOES.removeIf(echo -> echo.user == entity);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ECHOES.clear();
    }

}

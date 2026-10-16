package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// The Air Is My Weapon
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderBarrage {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    public static final int PERIOD = 6;
    public static final int LAND = 3;
    public static final int STAGGER = 1;
    private static final double ARM_SIDE = 1.0D;
    private static final double ARM_SPREAD = 0.3D;
    private static final double ARM_TOP = 0.15D;
    private static final double ARM_STEP = 0.45D;
    private static final double ARM_BACK = 0.35D;
    private static final double PUNCH_WIDTH = 0.45D;

    private record Barrage(ManasSkillInstance instance, LivingEntity user, long start, int arms, int punches) {}

    private static final List<Barrage> BARRAGES = new ArrayList<>();

    private NewOrderBarrage() {
    }

    public static int getTicks(int arms, int punches) {
        return (arms - 1) * STAGGER + punches * PERIOD;
    }

    // Arm position
    public static Vec3 getArmOffset(int arm, int arms) {
        int row = arm / 2;
        double side = (arm % 2 == 0 ? 1.0D : -1.0D) * (ARM_SIDE + (row % 2) * ARM_SPREAD);
        return new Vec3(side, ARM_TOP - row * ARM_STEP, -ARM_BACK);
    }

    public static Vec3 getArmBase(Vec3 eye, Vec3 look, Vec3 offset, float scale) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        return eye.add(right.scale(offset.x * scale)).add(up.scale(offset.y * scale)).add(look.scale(offset.z * scale));
    }

    static boolean start(ServerLevel level, LivingEntity player, ManasSkillInstance instance, int mode) {
        if (BARRAGES.stream().anyMatch(barrage -> barrage.user() == player)) {
            NewOrderUtil.fail(player, Component.translatable("tracadamia.skill.new_order.copy_busy"));
            return false;
        }
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) {
            return false;
        }

        int arms = Math.max(1, CONFIG.weaponArms);
        int punches = Math.max(1, CONFIG.weaponPunches);
        BARRAGES.add(new Barrage(instance, player, level.getGameTime(), arms, punches));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new NewOrderPayloads.BarragePayload(player.getId(), arms, punches));
        instance.addMasteryPoint(player);

        NewOrderUtil.playSound(level, player.getEyePosition(), TensuraSoundEvents.CAST_WIND.get(), 1.0F, 1.1F);
        NewOrderUtil.playSound(level, player.getEyePosition(), SoundEvents.BREEZE_SHOOT, 1.2F, 0.7F);
        NewOrderUtil.callout(level, new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.8D, player.getZ()), player, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.AIR]);
        return true;
    }

    // true once it's over
    private static boolean tickBarrage(Barrage barrage, long time) {
        LivingEntity user = barrage.user();
        if (!(user.level() instanceof ServerLevel level) || user.isRemoved() || !user.isAlive() || !NewOrderRules.hasRule(user, NewOrderRule.AIR_CONTROL)) {
            return true;
        }

        long age = time - barrage.start();
        for (int arm = 0; arm < barrage.arms(); arm++) {
            long into = age - arm * STAGGER - LAND;
            if (into >= 0L && into % PERIOD == 0L && into / PERIOD < barrage.punches()) {
                punch(level, barrage, arm);
            }
        }
        return age >= getTicks(barrage.arms(), barrage.punches());
    }

    private static void punch(ServerLevel level, Barrage barrage, int arm) {
        LivingEntity user = barrage.user();
        double range = CONFIG.weaponRange * user.getScale();
        Vec3 base = getArmBase(user.getEyePosition(), user.getViewVector(1.0F), getArmOffset(arm, barrage.arms()), user.getScale());
        Vec3 end = base.add(NewOrderUtil.aim(user, range).point().subtract(base).normalize().scale(range));
        BlockHitResult wall = level.clip(new ClipContext(base, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }

        Vec3 from = base;
        Vec3 to = end;
        DamageSource source = NewOrderUtil.windSource(barrage.instance(), user, NewOrderQuirk.WEAPON);
        float damage = NewOrderUtil.meleeDamage(user, CONFIG.weaponDamagePercent);
        boolean landed = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(PUNCH_WIDTH), target -> target.isAlive() && !target.isSpectator()
                && HitRules.canHit(user, target) && target.getBoundingBox().inflate(PUNCH_WIDTH).clip(from, to).isPresent())) {
            if (NewOrderUtil.hit(target, source, damage)) {
                landed = true;
                Vec3 at = target.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 3, 0.2D, 0.2D, 0.2D, 0.05D);
                if (level.random.nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.3D, 0.3D, 0.3D, 0.2D);
                }
            }
        }

        if (landed) {
            NewOrderUtil.playSound(level, end, SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.8F + level.random.nextFloat() * 0.4F);
        } else if (arm % 2 == 0) {
            NewOrderUtil.playSound(level, end, SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.4F + level.random.nextFloat() * 0.3F);
        }
        if (arm == 0) {
            NewOrderUtil.playSound(level, user.getEyePosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.4F, 1.6F);
        }
    }

    static void forget(LivingEntity entity) {
        BARRAGES.removeIf(barrage -> barrage.user() == entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!BARRAGES.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            BARRAGES.removeIf(barrage -> tickBarrage(barrage, time));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BARRAGES.clear();
    }

}

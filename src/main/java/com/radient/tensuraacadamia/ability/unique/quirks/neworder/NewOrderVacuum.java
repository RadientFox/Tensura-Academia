package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.Alignment;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

// No Longer Exist
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderVacuum {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private static final double VACUUM_BASE_RADIUS = 3.5D;

    private NewOrderVacuum() {
    }

    static final class Vacuum {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final ServerLevel level;
        private final Vec3 center;
        private final double radius;
        private final long start;
        private final long end;

        private Vacuum(ManasSkillInstance instance, LivingEntity owner, ServerLevel level, Vec3 center, double radius, long start, long end) {
            this.instance = instance;
            this.owner = owner;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.start = start;
            this.end = end;
        }
    }

    static final List<Vacuum> VACUUMS = new ArrayList<>();

    static boolean placeVacuum(ServerLevel level, LivingEntity player, ManasSkillInstance instance, int mode) {
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) {
            return false;
        }

        long time = level.getGameTime();
        double radius = CONFIG.vacuumRadius;
        NewOrderUtil.Aim aim = NewOrderUtil.aim(player, CONFIG.vacuumRange);
        Vec3 center = aim.block() != null ? aim.point().add(Vec3.atLowerCornerOf(aim.block().getDirection().getNormal()).scale(radius * 0.5D)) : aim.point();

        VACUUMS.removeIf(vacuum -> vacuum.owner == player && endVacuum(vacuum));
        VACUUMS.add(new Vacuum(instance, player, level, center, radius, time, time + CONFIG.vacuumSeconds * 20L));
        instance.addMasteryPoint(player);

        player.swing(InteractionHand.MAIN_HAND, true);
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_INHALE, 1.5F, 0.5F);
        NewOrderUtil.playSound(level, center, TensuraSoundEvents.CAST_WIND.get(), 1.0F, 0.7F);
        NewOrderUtil.playSound(level, player.getEyePosition(), TensuraSoundEvents.COERCION.get(), 0.6F, 1.3F);
        rushIn(level, center, radius, 40 * getDensity(radius), 0.5D);
        NewOrderUtil.callout(level, new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.8D, player.getZ()), player, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.AIR]);
        return true;
    }

    private static Vec3 randomDirection(RandomSource random) {
        double y = random.nextDouble() * 2.0D - 1.0D;
        double angle = random.nextDouble() * Mth.TWO_PI;
        double flat = Math.sqrt(1.0D - y * y);
        return new Vec3(Math.cos(angle) * flat, y, Math.sin(angle) * flat);
    }

    private static int getDensity(double radius) {
        return Math.max(1, (int) Math.round(radius / VACUUM_BASE_RADIUS));
    }

    private static void rushIn(ServerLevel level, Vec3 center, double radius, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3 direction = randomDirection(level.random);
            Vec3 at = center.add(direction.scale(radius));
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, -direction.x, -direction.y, -direction.z, speed * radius * 0.1D);
        }
    }

    private static boolean tickVacuum(Vacuum vacuum, long time) {
        LivingEntity owner = vacuum.owner;
        if (owner.isRemoved() || !owner.isAlive() || time >= vacuum.end || !NewOrderRules.hasRule(owner, NewOrderRule.AIR_CONTROL)) {
            endVacuum(vacuum);
            return false;
        }

        ServerLevel level = vacuum.level;
        Vec3 center = vacuum.center;
        double radius = vacuum.radius;
        long age = time - vacuum.start;
        int density = getDensity(radius);
        rushIn(level, center, radius, 6 * density, 0.35D);
        if (age % 3L == 0L) {
            level.sendParticles(ParticleTypes.WHITE_ASH, center.x, center.y, center.z, 8 * density * density, radius * 0.4D, radius * 0.4D, radius * 0.4D, 0.0D);
        }
        if (age % 30L == 0L) {
            NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_IDLE_AIR, 1.2F * density, 0.5F);
        }

        boolean hurting = age % Math.max(1, CONFIG.vacuumDamageInterval) == 0L;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius + 1.0D),
                target -> target.isAlive() && !target.isSpectator() && HitRules.canHit(owner, target) && Alignment.shouldConsumeAir(target)
                        && target.getBoundingBox().getCenter().distanceTo(center) <= radius + target.getBbWidth() * 0.5D)) {
            target.clearFire();
            int air = Math.max(-20, target.getAirSupply() - CONFIG.vacuumAirDrain);
            target.setAirSupply(air);
            if (air <= 0 && hurting) {
                DamageSource source = TensuraDamageTypes.getEntityDamageSource(level, TensuraDamageTypes.SUFFOCATE, owner);
                ((TensuraDamageSource) source).tensura$setAbilityInstance(vacuum.instance);
                target.invulnerableTime = 0;
                target.hurt(source, NewOrderUtil.ruleDamage(owner, CONFIG.vacuumDamage + target.getMaxHealth() * CONFIG.vacuumHealthDamage));
                level.sendParticles(ParticleTypes.POOF, target.getX(), target.getEyeY(), target.getZ(), 4, 0.15D, 0.1D, 0.15D, 0.02D);
            }
        }
        return true;
    }

    static boolean endVacuum(Vacuum vacuum) {
        Vec3 center = vacuum.center;
        int density = getDensity(vacuum.radius);
        for (int i = 0; i < 30 * density; i++) {
            Vec3 direction = randomDirection(vacuum.level.random);
            vacuum.level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 0, direction.x, direction.y, direction.z, 0.4D * density);
        }
        vacuum.level.playSound(null, center.x, center.y, center.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F * density, 0.7F);
        return true;
    }

    static void forget(LivingEntity entity) {
        VACUUMS.removeIf(vacuum -> vacuum.owner == entity && endVacuum(vacuum));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!VACUUMS.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            VACUUMS.removeIf(vacuum -> !tickVacuum(vacuum, time));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VACUUMS.clear();
    }

}

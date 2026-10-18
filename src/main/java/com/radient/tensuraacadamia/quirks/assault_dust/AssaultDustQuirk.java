package com.radient.tensuraacadamia.quirks.assault_dust;

import com.radient.tensuraacadamia.quirks.shared.BeamGeometry;
import com.radient.tensuraacadamia.quirks.shared.QuirkCastCosts;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AssaultDustQuirk extends Skill {
    public static final int DUSTBLOWER = 0, DUSTSTORM = 1, RETURN_TO_DUST = 2;
    public static final int COMPRESSION_TICKS = 100;
    public static final double RUSH_DISTANCE = 10, STORM_DISTANCE = 40, STORM_SIZE = 50;
    private static final QuirkSkillsConfig.AssaultDust CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).AssaultDust;
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(0.64F, 0.52F, 0.36F), 3);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.6F, 0.05F, 0.04F), 1);
    private static final BlockParticleOption GRAINS = new BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
    private static final String FOOTSTEP = "AssaultDustFootstep";
    private static final List<Storm> STORMS = new ArrayList<>();
    private static final List<Rush> RUSHES = new ArrayList<>();
    private static final List<Capture> CAPTURES = new ArrayList<>();

    private static final class Rush {
        final ManasSkillInstance instance;
        final LivingEntity owner;
        final ServerLevel level;
        final Vec3 origin, direction;
        Vec3 previous;
        int age;
        boolean interrupted;

        Rush(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
            this.instance = instance;
            this.owner = owner;
            this.level = level;
            origin = owner.position();
            previous = owner.getBoundingBox().getCenter();
            direction = owner.getLookAngle().normalize();
        }
    }

    private static final class Capture {
        final ManasSkillInstance instance;
        final LivingEntity owner, target;
        final ServerLevel level;
        int age;
        boolean interrupted;

        Capture(Rush rush, LivingEntity target) {
            instance = rush.instance;
            owner = rush.owner;
            level = rush.level;
            this.target = target;
            owner.setDeltaMovement(Vec3.ZERO);
            target.setDeltaMovement(Vec3.ZERO);
            owner.hurtMarked = true;
            target.hurtMarked = true;
            owner.addEffect(new MobEffectInstance(MHAEffects.COMPRESSED_DUST, COMPRESSION_TICKS + 2, 0, false, false, true));
            target.addEffect(new MobEffectInstance(MHAEffects.COMPRESSED_DUST, COMPRESSION_TICKS + 2, 0, false, false, true));
        }

        void release() {
            owner.removeEffect(MHAEffects.COMPRESSED_DUST);
            target.removeEffect(MHAEffects.COMPRESSED_DUST);
        }
    }

    private static final class Storm {
        final ManasSkillInstance instance;
        final LivingEntity owner;
        final ServerLevel level;
        final Vec3 origin, forward, right;
        final int lifetime;
        final Set<UUID> hit = new HashSet<>();
        int age;

        Storm(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
            this.instance = instance;
            this.owner = owner;
            this.level = level;
            forward = Vec3.directionFromRotation(0, owner.getYRot());
            right = new Vec3(forward.z, 0, -forward.x);
            origin = owner.position().subtract(forward.scale(8));
            lifetime = Math.max(1, CONFIG.duststormTravelSeconds) * 20;
        }

        boolean tick() {
            if (invalid(owner, level)) return true;
            double progress = age / (double) lifetime;
            double height = Math.max(3, STORM_SIZE * (1 - progress));
            Vec3 center = origin.add(forward.scale(STORM_DISTANCE * progress));
            AABB area = new AABB(center.x, center.y, center.z, center.x, center.y + height, center.z).inflate(STORM_SIZE / 2, 0, STORM_SIZE / 2);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> enemy(owner, target))) {
                Vec3 offset = target.getBoundingBox().getCenter().subtract(center);
                if (Math.abs(offset.dot(forward)) > 3 + target.getBbWidth() / 2
                        || Math.abs(offset.dot(right)) > STORM_SIZE / 2 + target.getBbWidth() / 2 || !hit.add(target.getUUID()))
                    continue;
                int duration = Math.max(1, CONFIG.duststormDebuffSeconds) * 20;
                target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), duration, 2));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 2));
                earthHit(instance, owner, target, DUSTSTORM, 50, false);
            }
            if (age % 2 == 0) {
                level.sendParticles(com.radient.tensuraacadamia.regestry.MHAParticles.DUST_WAVE.get(), center.x, center.y, center.z,
                        0, STORM_SIZE, height, Math.atan2(forward.x, forward.z), 1);
            }
            if (age % 20 == 0) sound(level, center, SoundEvents.SAND_BREAK, 2, 0.5F);
            return ++age > lifetime;
        }
    }

    public AssaultDustQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/assaultdusticon.png");
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.assault_dust.description");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() ? 3 : instance.getMastery() >= getMaxMastery() * 0.5 ? 2 : 1;
    }

    @Override
    public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case DUSTSTORM -> "assault_dust.duststorm";
            case RETURN_TO_DUST -> "assault_dust.return_to_dust";
            default -> "assault_dust.dustblower";
        };
    }

    @Override
    public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode == DUSTSTORM ? CONFIG.duststormAura : mode == RETURN_TO_DUST ? CONFIG.compressionAura : CONFIG.dustblowerAura;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || instance.getMastery() < 0 || mode < 0 || mode >= getModes(instance)
                || instance.onCoolDown(mode) || isCompressed(owner) || RUSHES.stream().anyMatch(rush -> rush.owner == owner))
            return;
        double cost = Math.max(0, getAuraCost(owner, instance, mode));
        if (!QuirkCastCosts.hasAura(owner, cost)) return;
        int cooldown;
        switch (mode) {
            case DUSTBLOWER -> {
                dustblower(owner, level);
                cooldown = CONFIG.dustblowerCooldown;
            }
            case DUSTSTORM -> {
                STORMS.add(new Storm(instance, owner, level));
                cooldown = CONFIG.duststormCooldown;
            }
            case RETURN_TO_DUST -> {
                RUSHES.add(new Rush(instance, owner, level));
                cooldown = CONFIG.compressionCooldown;
            }
            default -> {
                return;
            }
        }
        QuirkCastCosts.spendAura(owner, cost);
        QuirkCastCosts.cooldown(instance, mode, Math.max(0, cooldown), 3);
        instance.addMasteryPoint(owner);
    }

    private static void dustblower(LivingEntity owner, ServerLevel level) {
        Vec3 origin = owner.getEyePosition(), forward = owner.getLookAngle().normalize();
        double range = Math.max(1, CONFIG.dustblowerRange);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range), target -> enemy(owner, target))) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(origin);
            if (offset.length() > range || offset.normalize().dot(forward) < Math.cos(Math.toRadians(30)) || !owner.hasLineOfSight(target))
                continue;
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
            target.addEffect(new MobEffectInstance(MHAEffects.DUSTY, Math.max(1, CONFIG.dustySeconds) * 20));
        }
        Vec3 right = BeamGeometry.perpendicular(forward), up = right.cross(forward).normalize();
        for (int i = 0; i < 180; i++) {
            double distance = level.random.nextDouble() * range;
            double angle = level.random.nextDouble() * Math.PI * 2, spread = Math.sqrt(level.random.nextDouble()) * distance * 0.577;
            Vec3 point = origin.add(forward.scale(distance)).add(right.scale(Math.cos(angle) * spread)).add(up.scale(Math.sin(angle) * spread));
            level.sendParticles(DUST, point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0);
        }
        sound(level, origin, SoundEvents.SAND_BREAK, 1, 0.8F);
    }

    private static boolean tickRush(Rush rush) {
        LivingEntity owner = rush.owner;
        if (rush.interrupted || invalid(owner, rush.level) || ++rush.age > 40) {
            stop(owner);
            return true;
        }
        double remaining = RUSH_DISTANCE - owner.position().distanceTo(rush.origin);
        if (remaining <= 0.05) {
            stop(owner);
            return true;
        }
        Vec3 center = owner.getBoundingBox().getCenter(), end = center.add(rush.direction.scale(Math.min(1, remaining)));
        var block = rush.level.clip(new ClipContext(center, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (block.getType() == HitResult.Type.BLOCK) end = block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(rush.level, owner, rush.previous, end,
                owner.getBoundingBox().expandTowards(end.subtract(center)).inflate(1), entity -> entity instanceof LivingEntity target
                        && enemy(owner, target) && !isCompressed(target));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            CAPTURES.add(new Capture(rush, target));
            return true;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            stop(owner);
            return true;
        }
        owner.setDeltaMovement(end.subtract(center));
        owner.hurtMarked = true;
        owner.resetFallDistance();
        rush.previous = center;
        Vec3 point = owner.position();
        rush.level.sendParticles(GRAINS, point.x, point.y + 0.1, point.z, 12, 0.3, 0.1, 0.3, 0.03);
        return false;
    }

    private static boolean tickCapture(Capture capture) {
        if (capture.interrupted || invalid(capture.owner, capture.level) || invalid(capture.target, capture.level)
                || capture.age >= COMPRESSION_TICKS || !isCompressed(capture.owner) || !isCompressed(capture.target)) {
            capture.release();
            return true;
        }
        stop(capture.owner);
        stop(capture.target);
        earthHit(capture.instance, capture.owner, capture.target, RETURN_TO_DUST, compressionDamage(capture.target), true);
        Vec3 center = capture.target.getBoundingBox().getCenter();
        double radius = Math.max(0.65, capture.target.getBbWidth() * 0.7);
        for (int i = 0; i < 48; i++) {
            double y = (i % 12) / 11.0 * capture.target.getBbHeight();
            double angle = capture.age * 0.8 + i * 1.9;
            Vec3 point = new Vec3(center.x + Math.cos(angle) * radius, capture.target.getY() + y, center.z + Math.sin(angle) * radius);
            capture.level.sendParticles(DUST, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 6 == 0) capture.level.sendParticles(GRAINS, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.05);
        }
        if (capture.age % 4 == 0)
            capture.level.sendParticles(BLOOD, center.x, center.y, center.z, 4, radius * 0.5, capture.target.getBbHeight() * 0.3, radius * 0.5, 0);
        if (capture.age % 10 == 0) sound(capture.level, center, SoundEvents.SAND_BREAK, 1, 0.5F);
        capture.age++;
        if (!capture.target.isAlive()) {
            capture.release();
            return true;
        }
        return false;
    }

    public static float compressionDamage(LivingEntity target) {
        double resistance = target.getAttributeValue(TensuraAttributes.EARTH_RESISTANCE);
        if (resistance >= 2 || SkillUtils.isSkillToggled(target, ResistanceSkills.EARTH_ATTACK_NULLIFICATION.get()))
            return 20;
        if (resistance > 0 || SkillUtils.isSkillToggled(target, ResistanceSkills.EARTH_ATTACK_RESISTANCE.get()))
            return 50;
        return 100;
    }

    private static void earthHit(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, int mode, float damage, boolean bypass) {
        DamageSource source = QuirkSkills.ASSAULT_DUST.get().createSource(instance, owner, TensuraDamageTypes.EARTH_ELEMENTAL, mode);
        TensuraDamageSource tensura = (TensuraDamageSource) source;
        tensura.tensura$setElement(Element.EARTH);
        if (bypass) tensura.tensura$setResistanceBypassLevel(2);
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    public static boolean isCompressed(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(MHAEffects.COMPRESSED_DUST);
    }

    private static boolean enemy(LivingEntity owner, LivingEntity target) {
        return target != owner && target.isAlive() && !target.isSpectator() && !owner.isAlliedTo(target) && !SubordinateHelper.isAlly(owner, target);
    }

    private static boolean invalid(LivingEntity entity, ServerLevel level) {
        return !entity.isAlive() || entity.isRemoved() || entity.level() != level;
    }

    private static void stop(LivingEntity entity) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        entity.resetFallDistance();
    }

    private static void sound(ServerLevel level, Vec3 point, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        level.playSound(null, point.x, point.y, point.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        STORMS.removeIf(Storm::tick);
        RUSHES.removeIf(AssaultDustQuirk::tickRush);
        CAPTURES.removeIf(AssaultDustQuirk::tickCapture);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        RUSHES.stream().filter(rush -> rush.owner == event.getEntity()).forEach(rush -> rush.interrupted = true);
        CAPTURES.stream().filter(capture -> capture.owner == event.getEntity()).forEach(capture -> capture.interrupted = true);
    }

    @SubscribeEvent
    public static void onFootsteps(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity target) || !(target.level() instanceof ServerLevel level))
            return;
        CompoundTag data = target.getPersistentData();
        if (!target.hasEffect(MHAEffects.DUSTY)) {
            data.remove(FOOTSTEP);
            data.remove(FOOTSTEP + "X");
            data.remove(FOOTSTEP + "Y");
            data.remove(FOOTSTEP + "Z");
            return;
        }
        if (!target.onGround() || target.tickCount % 4 != 0) return;
        Vec3 old = data.contains(FOOTSTEP) ? new Vec3(data.getDouble(FOOTSTEP + "X"), data.getDouble(FOOTSTEP + "Y"), data.getDouble(FOOTSTEP + "Z")) : target.position();
        if (!data.contains(FOOTSTEP) || target.position().distanceToSqr(old) > 0.09) {
            level.sendParticles(GRAINS, target.getX(), target.getY() + 0.05, target.getZ(), 8, target.getBbWidth() * 0.3, 0.02, target.getBbWidth() * 0.3, 0.01);
            data.putBoolean(FOOTSTEP, true);
            data.putDouble(FOOTSTEP + "X", target.getX());
            data.putDouble(FOOTSTEP + "Y", target.getY());
            data.putDouble(FOOTSTEP + "Z", target.getZ());
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof LivingEntity living && isCompressed(living)
                && CAPTURES.stream().noneMatch(capture -> capture.owner == living || capture.target == living))
            living.removeEffect(MHAEffects.COMPRESSED_DUST);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        super.onForgetSkill(instance, owner);
        STORMS.removeIf(storm -> storm.owner == owner);
        RUSHES.removeIf(rush -> {
            if (rush.owner != owner) return false;
            stop(owner);
            return true;
        });
        CAPTURES.removeIf(capture -> {
            if (capture.owner != owner) return false;
            capture.release();
            return true;
        });
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        CAPTURES.forEach(Capture::release);
        RUSHES.forEach(rush -> stop(rush.owner));
        STORMS.clear();
        RUSHES.clear();
        CAPTURES.clear();
    }
}

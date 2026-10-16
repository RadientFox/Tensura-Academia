package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.effects.TailwindEffect;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WhirlwindQuirk extends Skill {
    public static final int WHIRLWIND_TICKS = 200, TORNADO_TICKS = 140, TAILWIND_TICKS = 900;
    public static final int SLOW_FALLING_TICKS = 300, FLIGHT_TICKS = 200;
    private static final List<WindField> ACTIVE = new ArrayList<>();
    private static final String SAVED_GRAVITY = "TracadamiaWindOriginalGravity";
    private static final DustParticleOptions WIND = new DustParticleOptions(new Vector3f(0.92F, 0.96F, 1), 1.2F);
    private static final DustParticleOptions FIRE = new DustParticleOptions(new Vector3f(1, 0.4F, 0.05F), 1.2F);
    private static final DustParticleOptions WATER = new DustParticleOptions(new Vector3f(0.08F, 0.2F, 0.8F), 1.2F);
    private static final DustParticleOptions ICE = new DustParticleOptions(new Vector3f(0.6F, 0.88F, 1), 1.2F);
    private enum Infusion { NONE, FIRE, WATER, ICE }

    public WhirlwindQuirk() { super(SkillType.UNIQUE); }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/whirlwindicon.png");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (mode == 2 && entity.hasEffect(MHAEffects.WIND_FLIGHT)) return 0;
        return mode == 1 ? 750 : mode == 2 ? 250 : 500;
    }
    @Override public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return mode == 2 && owner.hasEffect(MHAEffects.WIND_FLIGHT);
    }
    @Override public int getModes(ManasSkillInstance instance) { return 3; }
    @Override public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "whirlwind.tornado";
            case 2 -> "whirlwind.tailwind";
            default -> "whirlwind.whirlwind";
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int keyNumber, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode > 2) return;
        boolean stopping = mode == 2 && owner.hasEffect(MHAEffects.WIND_FLIGHT);
        double cost = getAuraCost(owner, instance, mode);
        if (!stopping && (instance.onCoolDown(mode) || !QuirkCastCosts.hasAura(owner, cost))) return;
        if (mode == 2) {
            if (owner.hasEffect(MHAEffects.WIND_FLIGHT)) {
                owner.removeEffect(MHAEffects.WIND_FLIGHT);
                owner.getPersistentData().remove(TailwindEffect.BOOST_AT);
                return;
            }
            MobEffectInstance tailwind = owner.getEffect(MHAEffects.TAILWIND);
            if (tailwind == null || owner.onGround() || owner.isInWaterOrBubble() || owner.isPassenger()) {
                if (owner instanceof Player player) player.displayClientMessage(
                        Component.translatable("tracadamia.skill.whirlwind.tailwind_required"), true);
                return;
            }
            owner.addEffect(new MobEffectInstance(MHAEffects.WIND_FLIGHT,
                    Math.min(FLIGHT_TICKS, tailwind.getDuration()), 0, false, false, true));
            owner.getPersistentData().putLong(TailwindEffect.BOOST_AT, level.getGameTime() + 40);
            owner.setDeltaMovement(owner.getLookAngle().scale(TailwindEffect.BOOST_STRENGTH));
            owner.hurtMarked = true;
        } else {
            if (ACTIVE.stream().anyMatch(field -> field.owner == owner && field.mode == mode)) return;
            Vec3 center = owner.position();
            if (mode == 1) {
                Vec3 eye = owner.getEyePosition();
                center = level.clip(new ClipContext(eye, eye.add(owner.getLookAngle().scale(20)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
                if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(center))) return;
            }
            ACTIVE.add(new WindField(level, owner, instance, mode, center));
            level.playSound(null, owner.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                    SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        QuirkCastCosts.spendAura(owner, cost);
        QuirkCastCosts.cooldown(instance, mode, mode == 1 ? 5 : mode == 2 ? 20 : 10, 3);
        instance.addMasteryPoint(owner);
        instance.markDirty();
    }

    public static void registerSkillEvents() {
        EntityEvents.PROJECTILE_HIT.register((hit, projectile, deflection, result) -> {
            if (isCaptured(projectile)) result.set(EntityEvents.ProjectileHitResult.PASS);
        });
    }

    private static boolean isCaptured(Entity entity) {
        return ACTIVE.stream().anyMatch(field -> field.captured.containsKey(entity.getUUID()));
    }

    private record Captured(Entity entity, boolean noGravity, int projectileAge) { }

    private static final class WindField {
        final ServerLevel level;
        final LivingEntity owner;
        final ManasSkillInstance instance;
        final int mode;
        final Map<UUID, Captured> captured = new HashMap<>();
        Vec3 center;
        int age;
        boolean launchedOwner;
        Infusion infusion = Infusion.NONE;

        WindField(ServerLevel level, LivingEntity owner, ManasSkillInstance instance, int mode, Vec3 center) {
            this.level = level; this.owner = owner; this.instance = instance; this.mode = mode; this.center = center;
        }

        AABB bounds() {
            double half = mode == 0 ? 15 : 2.5, height = mode == 0 ? 50 : 7;
            return new AABB(center.x - half, center.y, center.z - half,
                    center.x + half, center.y + height, center.z + half);
        }

        void tick() {
            age++;
            if (mode == 0) center = owner.position();
            AABB box = bounds();
            if (mode == 1 && !launchedOwner && box.intersects(owner.getBoundingBox())) {
                launchedOwner = true;
                owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALLING_TICKS));
                owner.addEffect(new MobEffectInstance(MHAEffects.TAILWIND, TAILWIND_TICKS));
                owner.setDeltaMovement(new Vec3(owner.getDeltaMovement().x, 3.0, owner.getDeltaMovement().z));
                owner.fallDistance = 0;
                owner.hurtMarked = true;
            }
            for (Entity entity : level.getEntities(owner, box, entity -> !entity.isRemoved() && !entity.isSpectator())) {
                if (entity instanceof Projectile projectile) {
                    Infusion incoming = projectile.isOnFire() || projectile instanceof Fireball ? Infusion.FIRE
                            : projectile instanceof TensuraProjectile magic ? classify(magic.getDamageSource()) : Infusion.NONE;
                    if (incoming != Infusion.NONE && !captured.containsKey(entity.getUUID())) infusion = incoming;
                } else if (!(entity instanceof LivingEntity living) || !living.isAlive()
                        || mode == 1 && entity instanceof Player
                        || entity instanceof Player player && player.getAbilities().invulnerable
                        || owner.isAlliedTo(entity) || SubordinateHelper.isAlly(owner, living)) continue;
                if (ACTIVE.stream().anyMatch(field -> field != this && field.captured.containsKey(entity.getUUID()))) continue;
                captured.computeIfAbsent(entity.getUUID(), ignored -> {
                    entity.getPersistentData().putBoolean(SAVED_GRAVITY, entity.isNoGravity());
                    return new Captured(entity, entity.isNoGravity(), entity instanceof TensuraProjectile magic ? magic.getAge() : 0);
                });
            }
            captured.values().removeIf(entry -> {
                if (entry.entity.isRemoved() || entry.entity.level() != level) {
                    entry.entity.setNoGravity(entry.noGravity);
                    entry.entity.getPersistentData().remove(SAVED_GRAVITY);
                    return true;
                }
                return false;
            });
            for (Captured entry : captured.values()) {
                Entity entity = entry.entity;
                entity.setNoGravity(true);
                Vec3 offset = entity.position().subtract(center);
                double angle = age * 0.16 + entity.getId() * 1.7;
                double radius = mode == 0 ? 8.25 : 1.4;
                double height = Math.clamp(offset.y + 0.05, 0.5, mode == 0 ? 48 : 5.5);
                Vec3 destination = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
                Vec3 movement = destination.subtract(entity.position());
                if (movement.lengthSqr() > 0.64) movement = movement.normalize().scale(0.8);
                if (entity instanceof Projectile) {
                    if (entity instanceof TensuraProjectile magic) magic.setAge(entry.projectileAge);
                    entity.move(MoverType.SELF, movement);
                    entity.setDeltaMovement(Vec3.ZERO);
                    entity.hasImpulse = true;
                } else {
                    entity.setDeltaMovement(movement);
                    entity.hurtMarked = true;
                }
                entity.fallDistance = 0;
            }
            if (age % 2 == 0) particles();
        }

        void particles() {
            DustParticleOptions particle = switch (infusion) {
                case FIRE -> FIRE;
                case WATER -> WATER;
                case ICE -> ICE;
                default -> WIND;
            };
            double height = mode == 0 ? 50 : 7;
            int layers = mode == 0 ? 20 : 7;
            for (int row = 0; row < layers; row++) {
                double y = height * row / layers;
                double radius = mode == 0 ? 15 : 0.6 + 1.8 * row / layers;
                int points = mode == 0 ? 18 : 12;
                for (int point = 0; point < points; point++) {
                    double angle = point * Math.PI * 2 / points + age * 0.16 + row * 0.55;
                    level.sendParticles(particle, center.x + Math.cos(angle) * radius,
                            center.y + y, center.z + Math.sin(angle) * radius, 2, 0.12, 0.12, 0.12, 0);
                }
            }
        }

        void release(boolean attack) {
            for (Captured entry : captured.values()) {
                Entity entity = entry.entity;
                entity.setNoGravity(entry.noGravity);
                entity.getPersistentData().remove(SAVED_GRAVITY);
                if (!attack || entity.isRemoved() || entity.level() != level) continue;
                double angle = level.random.nextDouble() * Math.PI * 2;
                entity.setDeltaMovement(new Vec3(Math.cos(angle) * 2.5, 1.1 + level.random.nextDouble(), Math.sin(angle) * 2.5));
                entity.hurtMarked = true;
                entity.hasImpulse = true;
                if (entity instanceof LivingEntity living) {
                    float damage = mode == 0 ? 20 : 15;
                    hurt(living, damage, Infusion.NONE);
                    if (infusion != Infusion.NONE && living.isAlive()) {
                        living.invulnerableTime = 0;
                        hurt(living, damage, infusion);
                    }
                }
            }
            captured.clear();
        }

        void hurt(LivingEntity target, float amount, Infusion type) {
            DamageSource source = level.damageSources().source(switch (type) {
                case FIRE -> TensuraDamageTypes.HEAT_WAVE;
                case WATER -> TensuraDamageTypes.WATER_ELEMENTAL;
                case ICE -> TensuraDamageTypes.ICE_ELEMENTAL;
                default -> TensuraDamageTypes.WIND_ELEMENTAL;
            }, owner);
            TensuraDamageSource typed = (TensuraDamageSource) source;
            typed.tensura$setSkillType(SkillType.UNIQUE);
            typed.tensura$setAbilityInstance(instance);
            typed.tensura$setAbilityMode(mode);
            typed.tensura$setElement(type == Infusion.NONE ? Element.WIND : type == Infusion.FIRE ? Element.FLAME : Element.WATER);
            target.hurt(source, amount);
        }
    }

    private static Infusion classify(DamageSource source) {
        if (source.is(TensuraDamageTypes.ICE_ELEMENTAL) || source.is(TensuraDamageTypes.ICE_BREATH)
                || source.is(DamageTypes.FREEZE)) return Infusion.ICE;
        Element element = ((TensuraDamageSource) source).tensura$getElement();
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(TensuraDamageTypes.HEAT_WAVE)
                || element == Element.FLAME) return Infusion.FIRE;
        if (source.is(TensuraDamageTypes.WATER_ELEMENTAL) || source.is(TensuraDamageTypes.WATER_BLADE)
                || source.is(TensuraDamageTypes.WATER_BREATH) || element == Element.WATER) return Infusion.WATER;
        return Infusion.NONE;
    }

    public static void igniteBeam(ServerLevel level, Vec3 origin, Vec3 direction, double length, double width) {
        Vec3 right = BeamGeometry.perpendicular(direction), up = right.cross(direction).normalize();
        for (WindField field : ACTIVE) {
            if (field.level == level && BeamGeometry.intersects(field.bounds(), origin, direction, right, up,
                    length, width, width)) field.infusion = Infusion.FIRE;
        }
    }

    @SubscribeEvent
    public static void onElementalDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Infusion incoming = classify(event.getSource());
        if (incoming == Infusion.NONE) return;
        for (WindField field : ACTIVE) {
            if (field.level == event.getEntity().level() && field.bounds().intersects(event.getEntity().getBoundingBox()))
                field.infusion = incoming;
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (isCaptured(event.getProjectile())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            WindField field = iterator.next();
            if (!field.owner.isAlive() || field.owner.isRemoved() || field.owner.level() != field.level) {
                field.release(false); iterator.remove(); continue;
            }
            field.tick();
            if (field.age >= (field.mode == 0 ? WHIRLWIND_TICKS : TORNADO_TICKS)) {
                field.release(true); iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.forEach(field -> field.release(false));
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onEntityLoaded(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (entity.getPersistentData().contains(SAVED_GRAVITY)) {
            entity.setNoGravity(entity.getPersistentData().getBoolean(SAVED_GRAVITY));
            entity.getPersistentData().remove(SAVED_GRAVITY);
        }
    }
}

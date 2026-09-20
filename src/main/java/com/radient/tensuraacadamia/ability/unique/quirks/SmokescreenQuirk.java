package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SmokescreenQuirk extends Skill {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/smokescreenicon.png");
    private static final ResourceLocation GEARSHIFT_MOVEMENT = ResourceLocation.fromNamespaceAndPath("tracadamia", "gearshift_movement");
    private static final Set<String> TENSURA_SENSES = Set.of("all_seeing_eye", "danger_sense", "godwolf_sense",
            "heavenly_eye", "magic_sense", "sense_heat_source", "sense_soundwave", "snake_eye",
            "universal_perception", "dragon_eye", "eye_of_truth");
    private static final Map<UUID, Cloud> CLOUDS = new HashMap<>();
    private static Map<UUID, LivingEntity> OBSCURED = new HashMap<>();
    private static final Map<UUID, Set<ManasSkillInstance>> PAUSED_SENSES = new HashMap<>();
    private static final Map<UUID, Integer> UNSLOTTED_TICKS = new HashMap<>();
    private static final Map<UUID, Long> DISABLED_UNTIL = new HashMap<>();
    private static final double DOME_HEIGHT_RATIO = 0.7;
    private static final int AURA_DRAIN_INTERVAL = 60;

    private static final class Cloud {
        final LivingEntity owner;
        final ServerLevel level;
        final double radius;
        final double radiusSquared;
        final double height;
        final int particleCount;
        Vec3 center;
        boolean holding = true;
        int heldTicks;
        long expiresAt = Long.MAX_VALUE;

        Cloud(LivingEntity owner, ServerLevel level, boolean mastered) {
            this.owner = owner;
            this.level = level;
            this.radius = mastered ? 25.0 : 15.0;
            this.radiusSquared = radius * radius;
            this.height = radius * DOME_HEIGHT_RATIO;
            this.particleCount = (int) Math.round(28.0 * radius / 15.0);
            this.center = owner.position();
        }
    }

    public SmokescreenQuirk() {
        super(SkillType.UNIQUE);
    }

    public static void registerSkillEvents() {
        SkillEvents.ACTIVATE_SKILL.register((change, entity, key, mode) -> blockSense(change.get(), entity));
        SkillEvents.TOGGLE_SKILL.register((change, entity) -> blockSense(change.get(), entity));
        SkillEvents.SKILL_PRE_TICK.register(SmokescreenQuirk::blockSense);
        EntityEvents.LIVING_CHANGE_TARGET.register((entity, target) ->
                !CLOUDS.isEmpty() && entity instanceof Mob && target.get() != null
                        && crossesSmoke(entity.getEyePosition(), target.get().getEyePosition(), entity.level())
                        ? EventResult.interruptFalse() : EventResult.pass());
        EntityEvents.LIVING_POST_TICK.register(entity -> {
            if (!CLOUDS.isEmpty() && entity instanceof Mob mob && mob.getTarget() != null
                    && crossesSmoke(mob.getEyePosition(), mob.getTarget().getEyePosition(), mob.level()))
                mob.setTarget(null);
        });
    }

    private static EventResult blockSense(ManasSkillInstance instance, LivingEntity entity) {
        return instance != null && isSense(instance) && isInSmoke(entity)
                ? EventResult.interruptFalse() : EventResult.pass();
    }

    private static boolean isSense(ManasSkillInstance instance) {
        ResourceLocation id = instance.getSkillId();
        return "tensura".equals(id.getNamespace()) && TENSURA_SENSES.contains(id.getPath())
                || "tracadamia".equals(id.getNamespace()) && "dangersense".equals(id.getPath());
    }

    private static boolean isInSmoke(LivingEntity entity) {
        if (entity.level().isClientSide || CLOUDS.isEmpty()) return false;
        Vec3 eye = entity.getEyePosition();
        for (Cloud cloud : CLOUDS.values()) {
            if (cloud.level == entity.level() && insideDome(cloud, eye))
                return true;
        }
        return false;
    }

    private static boolean insideDome(Cloud cloud, Vec3 point) {
        double dy = point.y - cloud.center.y;
        if (dy < 0 || dy > cloud.height) return false;
        double dx = point.x - cloud.center.x;
        double dz = point.z - cloud.center.z;
        double horizontal = (dx * dx + dz * dz) / cloud.radiusSquared;
        return horizontal + dy * dy / (cloud.height * cloud.height) <= 1.0;
    }

    private static boolean crossesSmoke(Vec3 from, Vec3 to, net.minecraft.world.level.Level level) {
        if (CLOUDS.isEmpty()) return false;
        for (Cloud cloud : CLOUDS.values()) {
            if (cloud.level != level) continue;
            Vec3 start = new Vec3((from.x - cloud.center.x) / cloud.radius,
                    (from.y - cloud.center.y) / cloud.height, (from.z - cloud.center.z) / cloud.radius);
            Vec3 end = new Vec3((to.x - cloud.center.x) / cloud.radius,
                    (to.y - cloud.center.y) / cloud.height, (to.z - cloud.center.z) / cloud.radius);
            if (start.y < 0 && end.y < 0) continue;
            double first = start.y < 0 ? -start.y / (end.y - start.y) : 0.0;
            double last = end.y < 0 ? start.y / (start.y - end.y) : 1.0;
            Vec3 lowerBound = start.lerp(end, first);
            Vec3 upperBound = start.lerp(end, last);
            Vec3 segment = upperBound.subtract(lowerBound);
            double lengthSquared = segment.lengthSqr();
            double t = lengthSquared == 0 ? 0 : Math.clamp(-lowerBound.dot(segment) / lengthSquared, 0, 1);
            if (lowerBound.add(segment.scale(t)).lengthSqr() <= 1.0) return true;
        }
        return false;
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ICON;
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 600_000.0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false; // Command-granted until the One For All user requirements are implemented.
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if (!(entity instanceof ServerPlayer)) {
            boolean slotted = TensuraStorages.getAbilityFrom(entity).isAbilityInActivePreset(this);
            if (slotted && !entity.hasEffect(MHAEffects.ASPERSION))
                entity.addEffect(new MobEffectInstance(MHAEffects.ASPERSION, -1, 0, false, false, true));
            else if (!slotted) entity.removeEffect(MHAEffects.ASPERSION);
        }
        Cloud cloud = CLOUDS.get(entity.getUUID());
        if (cloud != null && cloud.owner == entity && cloud.holding && entity.tickCount % 20 == 0)
            instance.addMasteryPoint(entity);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 0 ? "smokescreen.smoke_release" : super.getModeId(instance, mode);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != 0 || !(entity.level() instanceof ServerLevel level)) return;
        long disabledUntil = DISABLED_UNTIL.getOrDefault(entity.getUUID(), 0L);
        if (level.getGameTime() < disabledUntil) {
            if (entity instanceof Player player)
                player.displayClientMessage(Component.literal("Smokescreen has been dispersed."), true);
            return;
        }
        CLOUDS.put(entity.getUUID(), new Cloud(entity, level, instance.isMastered(entity)));
    }

    public static void disableInArea(ServerLevel level, Vec3 center, double radius, int durationTicks) {
        Iterator<Cloud> iterator = CLOUDS.values().iterator();
        while (iterator.hasNext()) {
            Cloud cloud = iterator.next();
            double combinedRadius = radius + cloud.radius;
            if (cloud.level != level || cloud.center.distanceToSqr(center) > combinedRadius * combinedRadius) continue;
            DISABLED_UNTIL.put(cloud.owner.getUUID(), level.getGameTime() + durationTicks);
            iterator.remove();
        }
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode, int heldTicks) {
        if (mode != 0 || entity.level().isClientSide) return;
        Cloud cloud = CLOUDS.get(entity.getUUID());
        if (cloud != null && cloud.owner == entity && cloud.holding) {
            finishHolding(cloud);
        }
    }

    private static void finishHolding(Cloud cloud) {
        cloud.holding = false;
        cloud.expiresAt = cloud.level.getGameTime() + smokeDuration(gearOf(cloud.owner));
    }

    private static boolean consumeAura(LivingEntity entity) {
        double cost = Math.max(1.0, Math.ceil(EnergyHelper.getMaxAura(entity) * 0.01));
        var existence = TensuraStorages.getExistenceFrom(entity);
        double aura = existence.getAura();
        if (aura < cost) {
            if (entity instanceof ServerPlayer player)
                player.displayClientMessage(Component.literal("Not enough AP to sustain Smokescreen."), true);
            return false;
        }
        existence.setAura(aura - cost);
        existence.markDirty();
        return true;
    }

    private static int gearOf(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier applied = speed == null ? null : speed.getModifier(GEARSHIFT_MOVEMENT);
        if (applied != null) {
            double amount = applied.amount();
            if (amount < -0.7) return 0;
            if (amount > 0.7) return 4;
            if (amount > 0.5) return 3;
            if (amount > 0.3) return 2;
        }
        ManasSkillInstance gearshift = SkillAPI.getSkillsFrom(entity)
                .getSkill(QuirkSkills.GEARSHIFT.get().getRegistryName()).orElse(null);
        CompoundTag tag = gearshift == null ? null : gearshift.getTag();
        return tag != null && tag.getBoolean("tracadamia_gearshift_active")
                ? Math.clamp(tag.getInt("tracadamia_gearshift_gear"), 0, 4) : 1;
    }

    private static int smokeDuration(int gear) {
        return switch (gear) {
            case 0 -> 400; // Low: 20 seconds
            case 2 -> 300; // Second: 15 seconds
            case 3 -> 200; // Third: 10 seconds
            case 4 -> 100; // Top: 5 seconds
            default -> 200; // No Gearshift or base gear: 10 seconds
        };
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) updateAspersion(player);
        if (CLOUDS.isEmpty() && OBSCURED.isEmpty()) return;
        Map<UUID, LivingEntity> nowObscured = new HashMap<>();
        Iterator<Cloud> iterator = CLOUDS.values().iterator();
        while (iterator.hasNext()) {
            Cloud cloud = iterator.next();
            if (cloud.holding) {
                if (cloud.owner.isAlive() && cloud.owner.level() == cloud.level) {
                    cloud.center = cloud.owner.position();
                    if (++cloud.heldTicks % AURA_DRAIN_INTERVAL == 0 && !consumeAura(cloud.owner))
                        finishHolding(cloud);
                } else {
                    finishHolding(cloud);
                }
            }
            if (cloud.level.getGameTime() >= cloud.expiresAt) {
                iterator.remove();
                continue;
            }
            emitSmoke(cloud);
            AABB bounds = new AABB(cloud.center.x - cloud.radius, cloud.center.y,
                    cloud.center.z - cloud.radius, cloud.center.x + cloud.radius,
                    cloud.center.y + cloud.height, cloud.center.z + cloud.radius);
            for (LivingEntity target : cloud.level.getEntitiesOfClass(LivingEntity.class, bounds,
                    entity -> entity.isAlive() && insideDome(cloud, entity.getEyePosition()))) {
                if (nowObscured.putIfAbsent(target.getUUID(), target) != null) continue;
                pauseSenses(target);
                MobEffectInstance effect = target.getEffect(MHAEffects.SMOKESCREEN_OBSCURED);
                if (effect == null || effect.getDuration() <= 5)
                    target.addEffect(new MobEffectInstance(MHAEffects.SMOKESCREEN_OBSCURED, 10, 0, false, false, true));
            }
        }
        for (Map.Entry<UUID, LivingEntity> prior : OBSCURED.entrySet()) {
            if (nowObscured.containsKey(prior.getKey())) continue;
            LivingEntity entity = prior.getValue();
            if (entity.isAlive()) {
                entity.removeEffect(MHAEffects.SMOKESCREEN_OBSCURED);
                resumeSenses(entity, PAUSED_SENSES.remove(prior.getKey()));
            } else {
                PAUSED_SENSES.remove(prior.getKey());
            }
        }
        OBSCURED = nowObscured;
    }

    private static void updateAspersion(ServerPlayer player) {
        boolean slotted = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.SMOKESCREEN.get().getRegistryName()).isPresent()
                && TensuraStorages.getAbilityFrom(player).isAbilityInActivePreset(QuirkSkills.SMOKESCREEN.get());
        if (slotted) {
            UNSLOTTED_TICKS.remove(player.getUUID());
            if (!player.hasEffect(MHAEffects.ASPERSION))
                player.addEffect(new MobEffectInstance(MHAEffects.ASPERSION, -1, 0, false, false, true));
        } else if (player.hasEffect(MHAEffects.ASPERSION)
                && UNSLOTTED_TICKS.merge(player.getUUID(), 1, Integer::sum) >= 20) {
            player.removeEffect(MHAEffects.ASPERSION);
            UNSLOTTED_TICKS.remove(player.getUUID());
        }
    }

    private static void pauseSenses(LivingEntity entity) {
        for (ManasSkillInstance skill : SkillAPI.getSkillsFrom(entity).getLearnedSkills()) {
            if (!skill.isToggled() || !isSense(skill)) continue;
            PAUSED_SENSES.computeIfAbsent(entity.getUUID(), ignored -> new HashSet<>()).add(skill);
            skill.setToggled(false);
            skill.onToggleOff(entity);
        }
    }

    private static void resumeSenses(LivingEntity entity, Set<ManasSkillInstance> paused) {
        if (paused == null) return;
        var learned = SkillAPI.getSkillsFrom(entity).getLearnedSkills();
        for (ManasSkillInstance skill : paused) {
            if (!learned.contains(skill)) continue;
            skill.setToggled(true);
            skill.onToggleOn(entity);
        }
    }

    private static void emitSmoke(Cloud cloud) {
        if (cloud.level.getGameTime() % 2 != 0) return;
        var viewers = cloud.level.players();
        var ordinarySmoke = MHAParticles.SMOKESCREEN.get();
        var casterSmoke = MHAParticles.SMOKESCREEN_SELF.get();
        for (int i = 0; i < cloud.particleCount; i++) {
            double angle = cloud.level.random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(cloud.level.random.nextDouble()) * cloud.radius;
            double x = cloud.center.x + Math.cos(angle) * distance;
            double z = cloud.center.z + Math.sin(angle) * distance;
            double domeY = cloud.height * Math.sqrt(Math.max(0, 1.0 - distance * distance / cloud.radiusSquared));
            double y = cloud.center.y + domeY * (i < cloud.particleCount / 2 ? 0.9 : cloud.level.random.nextDouble());
            for (ServerPlayer viewer : viewers) {
                cloud.level.sendParticles(viewer,
                        viewer == cloud.owner ? casterSmoke : ordinarySmoke, false,
                        x, y, z, 1, 0.02, 0.015, 0.02, 0.008);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CLOUDS.clear();
        OBSCURED.clear();
        PAUSED_SENSES.clear();
        UNSLOTTED_TICKS.clear();
    }
}

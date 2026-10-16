package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.SolidAirWall;
import com.radient.tensuraacadamia.regestry.SolidAirEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;

public final class SolidAirQuirk extends Skill {
    public static final int LIFETIME = 1200;
    public static final double TARGET_RANGE = 20;
    private static final String READY = "SolidAirReady";
    private static final String COOLDOWN_ACTIVE = "SolidAirCooldownActive";

    public SolidAirQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/solidairicon.png");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return switch (mode) { case 0 -> 100; case 1 -> 1000; case 2 -> 10000; default -> 0; };
    }
    public int cooldownTicks(ManasSkillInstance instance, int mode) {
        boolean mastered = instance.getMastery() >= getMaxMastery();
        return switch (mode) {
            case 0 -> mastered ? 10 : 20;
            case 1 -> mastered ? 60 : 100;
            case 2 -> mastered ? 100 : 200;
            default -> 0;
        };
    }
    @Override public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() ? 3 : instance.getMastery() >= getMaxMastery() / 2 ? 2 : 1;
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "solid_air.air_prison";
            case 2 -> "solid_air.air_maiden";
            default -> "solid_air.air_wall";
        };
    }
    public float wallHealth(ManasSkillInstance instance) {
        return 40 + 360 * Math.clamp((float) instance.getMastery() / getMaxMastery(), 0, 1);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int keyNumber, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode >= getModes(instance)) return;
        long ready = instance.getOrCreateTag().getLong(READY + mode);
        if (ready > level.getGameTime() || (ready == 0 && instance.onCoolDown(mode))) return;
        double cost = getAuraCost(owner, instance, mode);
        var existence = TensuraStorages.getExistenceFrom(owner);
        if (existence.getAura() < cost) {
            if (owner instanceof Player player) player.displayClientMessage(
                    Component.translatable("tracadamia.skill.solid_air.not_enough_aura"), true);
            return;
        }
        boolean mastered = instance.getMastery() >= getMaxMastery();
        List<SolidAirWall> walls = new ArrayList<>();
        if (mode == 0) {
            Direction normal = Direction.getNearest(owner.getLookAngle().x, owner.getLookAngle().y, owner.getLookAngle().z);
            float span = mastered ? 10 : 5;
            Vec3 center = owner.getEyePosition().add(Vec3.atLowerCornerOf(normal.getNormal()).scale(3));
            if (normal.getAxis() != Direction.Axis.Y) center = new Vec3(center.x, owner.getY() + span / 2, center.z);
            walls.add(wall(level, owner, instance, center, normal, span, 1, false, null, 0, 1));
        } else {
            Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(TARGET_RANGE));
            var hit = ProjectileUtil.getEntityHitResult(owner, eye, end, new AABB(eye, end).inflate(1),
                    target -> target instanceof LivingEntity living && !(living instanceof SolidAirWall)
                            && HomingQuirk.isEnemy(owner, living), TARGET_RANGE * TARGET_RANGE);
            if (hit == null || !owner.hasLineOfSight(hit.getEntity())) {
                if (owner instanceof Player player) player.displayClientMessage(
                        Component.translatable("tracadamia.skill.solid_air.no_target"), true);
                return;
            }
            Vec3 center = hit.getEntity().getBoundingBox().getCenter();
            float span = mode == 2 ? 15 : 5;
            int layers = mode == 2 ? 3 : mastered ? 2 : 1;
            for (Direction normal : Direction.values()) for (int layer = 0; layer < layers; layer++) {
                Vec3 face = center.add(Vec3.atLowerCornerOf(normal.getNormal()).scale(span / 2 - (layer + 0.5) / layers));
                walls.add(wall(level, owner, instance, face, normal, span, 1F / layers, mode == 2, center, layer, layers));
            }
        }
        if (walls.stream().anyMatch(wall -> wall == null || !level.hasChunkAt(wall.blockPosition()))) return;
        List<SolidAirWall> spawned = new ArrayList<>();
        for (SolidAirWall wall : walls) {
            if (!level.addFreshEntity(wall)) {
                spawned.forEach(SolidAirWall::discard);
                return;
            }
            spawned.add(wall);
        }
        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        instance.getOrCreateTag().putLong(READY + mode, level.getGameTime() + cooldownTicks(instance, mode));
        owner.getPersistentData().putBoolean(COOLDOWN_ACTIVE, true);
        synchronizeCooldowns(instance, level.getGameTime());
        level.playSound(null, owner.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
        instance.addMasteryPoint(owner);
        instance.markDirty();
    }

    private static boolean synchronizeCooldowns(ManasSkillInstance instance, long now) {
        var cooldowns = new ArrayList<>(instance.getCooldownList());
        while (cooldowns.size() < 3) cooldowns.add(0);
        boolean active = false;
        for (int mode = 0; mode < 3; mode++) {
            long ready = instance.getOrCreateTag().getLong(READY + mode);
            if (ready == 0) continue;
            long remaining = Math.max(0, ready - now);
            cooldowns.set(mode, (int) ((remaining + 19) / 20));
            if (remaining == 0) {
                instance.getOrCreateTag().remove(READY + mode);
                instance.markDirty();
            }
            else active = true;
        }
        if (!cooldowns.equals(instance.getCooldownList())) {
            instance.setCoolDownList(cooldowns);
            instance.markDirty();
        }
        return active;
    }

    @SubscribeEvent
    public static void onCooldownTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || owner.level().isClientSide
                || !owner.getPersistentData().getBoolean(COOLDOWN_ACTIVE)) return;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.SOLID_AIR.get()).orElse(null);
        if (instance == null || !synchronizeCooldowns(instance, owner.level().getGameTime()))
            owner.getPersistentData().remove(COOLDOWN_ACTIVE);
    }

    private SolidAirWall wall(ServerLevel level, LivingEntity owner, ManasSkillInstance instance, Vec3 center,
                              Direction normal, float span, float thickness, boolean spiked, Vec3 prisonCenter,
                              int layer, int layers) {
        SolidAirWall wall = SolidAirEntities.WALL.get().create(level);
        if (wall != null) wall.configure(owner, center, normal, span, thickness, wallHealth(instance), spiked, prisonCenter, layer, layers);
        return wall;
    }
}

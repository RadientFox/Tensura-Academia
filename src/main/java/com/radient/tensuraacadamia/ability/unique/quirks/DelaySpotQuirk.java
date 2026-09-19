package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DelaySpotQuirk extends Skill {

    private static final ResourceLocation DELAY_SPOT_SLOWNESS = ResourceLocation.fromNamespaceAndPath("tracadamia", "delay_spot_slowness");

    private static final double RADIUS = 20.0D;

    private final Map<UUID, Set<UUID>> affectedEntities = new HashMap<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/delayspot.png");
    }

    public DelaySpotQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.literal("Flowers bloom in your heart.");
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "delayspot.delay_spot";
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != 0) {
            return false;
        }

        if (entity.level().isClientSide()) {
            return true;
        }

        UUID userUUID = entity.getUUID();

        Set<UUID> affected = affectedEntities.computeIfAbsent(userUUID, uuid -> new HashSet<>());

        for (LivingEntity target : entity.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(RADIUS), target -> target != entity && target.isAlive() && !target.isAlliedTo(entity))) {
            slowEntity(target);
            affected.add(target.getUUID());
        }

        spawnDomeParticles((ServerLevel) entity.level(), entity);

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != 0) {
            return;
        }

        if (entity.level().isClientSide()) {
            return;
        }

        removeAllSlowness((ServerLevel) entity.level(), entity.getUUID());
    }

    private void slowEntity(LivingEntity target) {
        AttributeInstance movementSpeed = target.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movementSpeed == null) {
            return;
        }

        if (movementSpeed.getModifier(DELAY_SPOT_SLOWNESS) != null) {
            return;
        }

        movementSpeed.addTransientModifier(new AttributeModifier(DELAY_SPOT_SLOWNESS, -0.99D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void removeAllSlowness(ServerLevel currentLevel, UUID userUUID) {
        Set<UUID> affected = affectedEntities.remove(userUUID);

        if (affected == null || affected.isEmpty()) {
            return;
        }

        MinecraftServer server = currentLevel.getServer();

        for (UUID targetUUID : affected) {

            for (ServerLevel level : server.getAllLevels()) {

                Entity entity = level.getEntity(targetUUID);

                if (!(entity instanceof LivingEntity target)) {
                    continue;
                }

                AttributeInstance movementSpeed = target.getAttribute(Attributes.MOVEMENT_SPEED);

                if (movementSpeed == null) {
                    continue;
                }

                AttributeModifier modifier = movementSpeed.getModifier(DELAY_SPOT_SLOWNESS);

                if (modifier != null) {
                    movementSpeed.removeModifier(modifier);
                }

                break;
            }
        }
    }

    private void spawnDomeParticles(ServerLevel level, LivingEntity entity) {
        double centerX = entity.getX();
        double centerY = entity.getY();
        double centerZ = entity.getZ();

        for (int i = 0; i < 140; i++) {
            double phi = level.random.nextDouble() * (Math.PI / 2.0D);

            double theta = level.random.nextDouble() * Math.PI * 2.0D;

            double x = Math.sin(phi) * Math.cos(theta) * RADIUS;

            double y = Math.cos(phi) * RADIUS;

            double z = Math.sin(phi) * Math.sin(theta) * RADIUS;

            level.sendParticles(ParticleTypes.CHERRY_LEAVES, centerX + x, centerY + y, centerZ + z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        for (int i = 0; i < 25; i++) {
            double phi = level.random.nextDouble() * (Math.PI / 2.0D);

            double theta = level.random.nextDouble() * Math.PI * 2.0D;

            double distance = RADIUS * (0.4D + level.random.nextDouble() * 0.6D);

            double x = Math.sin(phi) * Math.cos(theta) * distance;

            double y = Math.cos(phi) * distance;

            double z = Math.sin(phi) * Math.sin(theta) * distance;

            level.sendParticles(ParticleTypes.CLOUD, centerX + x, centerY + y, centerZ + z, 3, 0.0D, -0.02D, 0.0D, 0.0D);
        }

        for (int i = 0; i < 50; i++) {
            double angle = (Math.PI * 2.0D * i) / 50.0D;

            double x = Math.cos(angle) * RADIUS;

            double z = Math.sin(angle) * RADIUS;

            level.sendParticles(ParticleTypes.CHERRY_LEAVES, centerX + x, centerY + 0.1D, centerZ + z, 3, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
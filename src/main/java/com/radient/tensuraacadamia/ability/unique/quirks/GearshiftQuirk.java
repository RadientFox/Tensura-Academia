package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.ThrowerSkill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GearshiftQuirk extends Skill {

    private static final int MAX_GEARSHIFT_TIME = 20 * 60 * 5;

    private static final float LOW_SPEED = -0.80F;
    private static final float SECOND_SPEED = 0.40F;
    private static final float THIRD_SPEED = 0.60F;
    private static final float TOP_SPEED = 0.80F;

    private static final float SECOND_ATTACK_SPEED = 0.40F;
    private static final float THIRD_ATTACK_SPEED = 0.60F;
    private static final float TOP_ATTACK_SPEED = 0.80F;

    private static final float LOW_DAMAGE_MULT = 0.50F;
    private static final float BASE_DAMAGE_MULT = 1.00F;
    private static final float SECOND_DAMAGE_MULT = 1.50F;
    private static final float THIRD_DAMAGE_MULT = 2.00F;
    private static final float TOP_DAMAGE_MULT = 2.50F;

    private static final double LAUNCH_AURA_COST = 25_000.0D;

    private static final String TARGET_UUID_TAG = "tracadamia_gearshift_target";

    private static final ResourceLocation MOVEMENT_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "gearshift_movement");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "gearshift_attack_speed");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "gearshift_attack_damage");

    private static final String ACTIVE_TAG = "tracadamia_gearshift_active";
    private static final String GEAR_TAG = "tracadamia_gearshift_gear";
    private static final String TIME_TAG = "tracadamia_gearshift_time";

    private static final int GEARSHIFT_PENALTY_DURATION = 20 * 60 * 5;


    public GearshiftQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    private static CompoundTag getData(ManasSkillInstance instance) {
        return instance.getOrCreateTag();
    }

    private static float getGearDamageMultiplier(int gear) {
        return switch (gear) {
            case 0 -> LOW_DAMAGE_MULT;
            case 1 -> BASE_DAMAGE_MULT;
            case 2 -> SECOND_DAMAGE_MULT;
            case 3 -> THIRD_DAMAGE_MULT;
            case 4 -> TOP_DAMAGE_MULT;
            default -> BASE_DAMAGE_MULT;
        };
    }

    private static int getGear(ManasSkillInstance instance) {
        CompoundTag tag = getData(instance);

        if (!tag.contains(GEAR_TAG)) {
            tag.putInt(GEAR_TAG, 1);
            instance.markDirty();
        }

        return MthClampGear(tag.getInt(GEAR_TAG));
    }

    private static void setGear(ManasSkillInstance instance, int gear) {
        gear = MthClampGear(gear);

        CompoundTag tag = getData(instance);
        tag.putInt(GEAR_TAG, gear);

        instance.markDirty();
    }

    private static boolean isActive(ManasSkillInstance instance) {
        return getData(instance).getBoolean(ACTIVE_TAG);
    }

    private static void setActive(ManasSkillInstance instance, boolean active) {
        CompoundTag tag = getData(instance);
        tag.putBoolean(ACTIVE_TAG, active);

        instance.markDirty();
    }

    private static int getRemainingTime(ManasSkillInstance instance) {
        return getData(instance).getInt(TIME_TAG);
    }

    private static void setRemainingTime(ManasSkillInstance instance, int time) {
        CompoundTag tag = getData(instance);
        tag.putInt(TIME_TAG, Math.max(0, time));

        instance.markDirty();
    }

    private static int MthClampGear(int gear) {
        if (gear < 0) {
            return 0;
        }

        if (gear > 4) {
            return 4;
        }

        return gear;
    }

    private static void applyGearshiftPenalty(LivingEntity entity, int gear) {
        if (gear < 0) {
            return;
        }

        int amplifier = gear;

        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GEARSHIFT_PENALTY_DURATION, amplifier, false, true, true));

        entity.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FRAGILITY), GEARSHIFT_PENALTY_DURATION, amplifier, false, true, true));
    }

    private static void applyGear(LivingEntity entity, int gear) {
        removeGearshiftModifiers(entity);

        switch (gear) {

            // the loweest gear
            case 0 -> {
                addMovementModifier(entity, LOW_SPEED);
                addAttackDamageModifier(entity, LOW_DAMAGE_MULT);
            }

            // normal speeds
            case 1 -> {
            }

            // second gear
            case 2 -> {
                addMovementModifier(entity, SECOND_SPEED);
                addAttackSpeedModifier(entity, SECOND_ATTACK_SPEED);
                addAttackDamageModifier(entity, SECOND_DAMAGE_MULT);
            }

            // third gear
            case 3 -> {
                addMovementModifier(entity, THIRD_SPEED);
                addAttackSpeedModifier(entity, THIRD_ATTACK_SPEED);
                addAttackDamageModifier(entity, THIRD_DAMAGE_MULT);
            }

            // max gear
            case 4 -> {
                addMovementModifier(entity, TOP_SPEED);
                addAttackSpeedModifier(entity, TOP_ATTACK_SPEED);
                addAttackDamageModifier(entity, TOP_DAMAGE_MULT);
            }

            default -> {
            }
        }
    }

    private static void addMovementModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(MOVEMENT_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void addAttackSpeedModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_SPEED);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_SPEED_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void addAttackDamageModifier(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attributes.ATTACK_DAMAGE);

        if (attribute == null) {
            return;
        }

        attribute.addOrUpdateTransientModifier(new AttributeModifier(ATTACK_DAMAGE_MODIFIER, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeGearshiftModifiers(LivingEntity entity) {
        AttributeInstance movement = entity.getAttribute(Attributes.MOVEMENT_SPEED);

        if (movement != null) {
            movement.removeModifier(MOVEMENT_MODIFIER);
        }

        AttributeInstance attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);

        if (attackSpeed != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER);
        }
    }

    private static void deactivateGearshift(ManasSkillInstance instance, LivingEntity entity) {
        int gear = getGear(instance);

        setActive(instance, false);
        setRemainingTime(instance, 0);

        removeGearshiftModifiers(entity);

        entity.removeEffect(MobEffects.GLOWING);

        MobEffectInstance effect = entity.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (effect != null && effect.getAmplifier() == 1 && effect.getDuration() <= 40) {
            entity.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        }

        applyGearshiftPenalty(entity, gear);

        instance.setCoolDown(300, 0);
    }

    private static @Nullable LivingEntity getLookedAtEntity(Player player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(range));

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox, entity -> entity != player && entity.isAlive() && !entity.isSpectator());

        LivingEntity closest = null;
        double closestDistance = range * range;

        for (LivingEntity entity : entities) {

            AABB box = entity.getBoundingBox().inflate(0.3D);

            var hit = box.clip(start, end);

            if (hit.isPresent()) {

                double distance = start.distanceToSqr(hit.get());

                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = entity;
                }
            }
        }

        return closest;
    }

    private static void sendGearMessage(ServerPlayer player, int gear) {
        Component message = Component.literal("Gearshift: " + getGearName(gear)).withStyle(ChatFormatting.AQUA);

        player.displayClientMessage(message, true);
    }

    private static void spawnGearTransformationParticles(LivingEntity entity, int gear) {
        double x = entity.getX();
        double y = entity.getY() + entity.getBbHeight() * 0.5D;
        double z = entity.getZ();

        switch (gear) {
            case 0 ->
                    TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleTypes.PURPLE_LIGHTNING_SPARK.get(), x, y, z, 15, 0.1D, 0.1D, 0.1D, 0.1D, true);

            case 1 ->
                    TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleTypes.YELLOW_LIGHTNING_SPARK.get(), x, y, z, 15, 0.1D, 0.1D, 0.1D, 0.1D, true);

            case 2 ->
                    TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleTypes.PURPLE_LIGHTNING_SPARK.get(), x, y, z, 25, 0.15D, 0.15D, 0.15D, 0.15D, true);

            case 3 ->
                    TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleTypes.PURPLE_LIGHTNING_SPARK.get(), x, y, z, 35, 0.2D, 0.2D, 0.2D, 0.2D, true);

            case 4 -> {
                TensuraParticleHelper.addServerParticlesAroundSelf(entity, TensuraParticleTypes.YELLOW_LIGHTNING_SPARK.get(), 3.0D);

                TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleTypes.PURPLE_LIGHTNING_SPARK.get(), x, y, z, 25, 0.08D, 0.08D, 0.08D, 0.2D, true);

                TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleUtils.getGoldWave(0.9F, entity.getBbWidth() * 6.0F, 0.1F, true), x, entity.getY() + entity.getBbHeight() * 0.66D, z);

                TensuraParticleHelper.spawnServerParticles(entity.level(), TensuraParticleUtils.getPurpleWave(0.75F, entity.getBbWidth() * 7.0F, 0.1F, true), x, entity.getY() + entity.getBbHeight() * 0.33D, z);
            }
        }
    }

    private static String getGearName(int gear) {
        return switch (gear) {
            case 0 -> "Low Gear";
            case 1 -> "Base Gear";
            case 2 -> "Second Gear";
            case 3 -> "Third Gear";
            case 4 -> "Top Gear";
            default -> "Base Gear";
        };
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/gearshift.png");
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return true;
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        deactivateGearshift(instance, entity);
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        if (!isActive(instance)) {
            return;
        }

        int remaining = getRemainingTime(instance);

        if (remaining <= 0) {
            deactivateGearshift(instance, entity);
            return;
        }

        setRemainingTime(instance, remaining - 1);

        int gear = getGear(instance);

        applyGear(entity, gear);

        if (gear == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false, true));
        }

        if (remaining <= 1) {
            deactivateGearshift(instance, entity);
        }
    }

    @Override
    public boolean canScroll(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 0;
    }

    @Override
    public void onScroll(ManasSkillInstance instance, LivingEntity entity, double delta, int mode) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (mode != 0) {
            return;
        }

        if (delta == 0) {
            return;
        }

        int gear = getGear(instance);

        if (delta > 0) {
            gear++;

            if (gear > 4) {
                gear = 0;
            }
        } else {
            gear--;

            if (gear < 0) {
                gear = 4;
            }
        }

        setGear(instance, gear);

        if (isActive(instance)) {
            applyGear(player, gear);
            spawnGearTransformationParticles(player, gear);
        }

        sendGearMessage(player, gear);
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "gearshift.transmission";
            case 1 -> "gearshift.launch";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == 0 ? 1 : 0;
        }

        return mode == 1 ? 0 : 1;
    }


    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        switch (mode) {
            case 0 -> {
                if (isActive(instance)) {
                    deactivateGearshift(instance, player);
                    return;
                }

                int gear = getGear(instance);

                LivingEntity target = player;

                if (player.isCrouching()) {
                    LivingEntity lookedAt = getLookedAtEntity(player, 16.0D);

                    if (lookedAt != null && lookedAt != player) {
                        target = lookedAt;
                    }
                }

                if (target != player) {
                    getData(instance).putUUID(TARGET_UUID_TAG, target.getUUID());
                } else {
                    getData(instance).remove(TARGET_UUID_TAG);
                }

                setActive(instance, true);
                setRemainingTime(instance, MAX_GEARSHIFT_TIME);

                applyGear(target, gear);

                spawnGearTransformationParticles(target, gear);


                if (gear != 1) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 6000, 0, false, false, false));
                }

                instance.addMasteryPoint(player);
                sendGearMessage(player, gear);
            }

            case 1 -> throwGearshiftItem(instance, player);
        }
    }

    private void throwGearshiftItem(ManasSkillInstance instance, ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();

        if (stack.isEmpty()) {
            return;
        }

        double launchCost = LAUNCH_AURA_COST;

        if (EnergyHelper.isOutOfEnergy(player, launchCost, 0.0D)) {
            return;
        }

        Level level = player.level();

        int gear = getGear(instance);
        float damageMultiplier = getGearDamageMultiplier(gear);

        Projectile projectile = ThrowerSkill.getProjectile(level, player, stack.copy(), instance);

        CompoundTag projectileData = projectile.getPersistentData();
        projectileData.putFloat("GearshiftDamageMultiplier", damageMultiplier);

        Vec3 direction = player.getViewVector(1.0F);

        projectile.shoot(direction.x(), direction.y(), direction.z(), 2.0F, 0.0F);

        level.addFreshEntity(projectile);

        player.swing(player.getUsedItemHand(), true);

        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }

        instance.addMasteryPoint(player);
    }

}
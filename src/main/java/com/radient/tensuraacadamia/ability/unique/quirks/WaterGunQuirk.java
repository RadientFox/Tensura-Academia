package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WaterGunQuirk extends Skill {

    private static final QuirkSkillsConfig.WaterGun CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WaterGun;


    private static final int SPRAY = 0;
    private static final int WATER_BULLET = 1;
    private static final int WATER_JET = 2;
    private static final int MODES = 3;

    public static final String JETTING_TAG = "jetting";
    public static final String JET_LIFT_TAG = "jetLift";
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "water_gun_water_boost");
    private static final float BULLET_SIZE = 0.5F;
    private static final float BULLET_SPEED = 3.0F;
    private static final double JET_ACCELERATION = 0.15D;
    private static final double JET_FALL = 0.08D;

    private record Charge(ManasSkillInstance instance, LivingEntity owner, long fireAt) {}

    private static final List<Charge> CHARGES = new ArrayList<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/watergun.png");
    }

    public WaterGunQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SPRAY -> CONFIG.sprayAuraCost;
            case WATER_BULLET -> CONFIG.bulletAuraCost;
            case WATER_JET -> CONFIG.jetAuraCost;
            default -> 0.0D;
        };
    }


    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SPRAY -> "water_gun.spray";
            case WATER_BULLET -> "water_gun.water_bullet";
            case WATER_JET -> "water_gun.water_jet";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getWaterGun(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WATER_GUN.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Water Affinity

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, instance.isMastered(entity) ? CONFIG.waterBoostMastered : CONFIG.waterBoost);
        CompoundTag tag = instance.getOrCreateTag();
        if (tag.getDouble(JET_LIFT_TAG) != CONFIG.jetLift) {
            tag.putDouble(JET_LIFT_TAG, CONFIG.jetLift);
            instance.markDirty();
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        onTick(instance, entity);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, 0.0D);
        CHARGES.removeIf(charge -> charge.owner() == entity);
    }

    // Spray and Water Jet

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode == WATER_BULLET || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            setJetting(instance, false);
            return false;
        }

        if (mode == SPRAY) {
            boolean hit = heldTicks % Math.max(1, CONFIG.sprayHitTicks) == 0;
            WaterPumpQuirk.waterStream(level, instance, entity, SPRAY, CONFIG.sprayRange, hit ? (float) CONFIG.sprayDamage : 0.0F, false);
            if (heldTicks % 5 == 0) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.9F);
            }
        } else {
            waterJet(level, instance, entity, heldTicks);
        }

        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == WATER_JET) {
            setJetting(instance, false);
        }
    }

    // water jet lifts you off the ground/slow falling
    private static void waterJet(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        setJetting(instance, true);
        entity.resetFallDistance();
        if (!(entity instanceof Player)) {
            entity.setDeltaMovement(lift(entity.getDeltaMovement(), CONFIG.jetLift / 20.0D));
            entity.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.SPLASH, entity.getX(), entity.getY() - 0.1D, entity.getZ(), 6, entity.getBbWidth() * 0.3D, 0.05D, entity.getBbWidth() * 0.3D, 0.1D);
        level.sendParticles(ParticleTypes.FALLING_WATER, entity.getX(), entity.getY() + 0.2D, entity.getZ(), 4, entity.getBbWidth() * 0.4D, 0.1D, entity.getBbWidth() * 0.4D, 0.0D);
        if (heldTicks % 6 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    //this sis the slow falling part
    public static Vec3 lift(Vec3 motion, double maxLift) {
        double y = motion.y < 0.0D ? Math.min(0.0D, motion.y + JET_FALL + JET_ACCELERATION) : Math.min(maxLift, motion.y + JET_ACCELERATION);
        return new Vec3(motion.x, y, motion.z);
    }

    private static void setJetting(ManasSkillInstance instance, boolean jetting) {
        if (instance.getOrCreateTag().getBoolean(JETTING_TAG) != jetting) {
            instance.getOrCreateTag().putBoolean(JETTING_TAG, jetting);
            instance.markDirty();
        }
    }

    // Water Bullet

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != WATER_BULLET || !(entity.level() instanceof ServerLevel level) || CHARGES.stream().anyMatch(charge -> charge.owner() == entity)
                || EnergyHelper.isOutOfEnergy(entity, instance, WATER_BULLET)) {
            return;
        }

        CHARGES.add(new Charge(instance, entity, level.getGameTime() + Math.max(0, CONFIG.bulletChargeTicks)));
        entity.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.8F, 1.4F);
        instance.setCoolDown(CONFIG.bulletCooldown, WATER_BULLET);
        instance.addMasteryPoint(entity);
    }

    private static boolean tickCharge(Charge charge) {
        LivingEntity owner = charge.owner();
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return false;
        }

        Vec3 hands = owner.getEyePosition().add(0.0D, -0.4D, 0.0D).add(owner.getLookAngle().scale(0.5D));
        if (level.getGameTime() < charge.fireAt()) {
            level.sendParticles(ParticleTypes.SPLASH, hands.x, hands.y, hands.z, 3, 0.1D, 0.1D, 0.1D, 0.0D);
            level.sendParticles(ParticleTypes.BUBBLE_POP, hands.x, hands.y, hands.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
            return true;
        }

        WaterPumpQuirk.shootWaterBall(level, charge.instance(), owner, WATER_BULLET, (float) CONFIG.bulletDamage, BULLET_SIZE, BULLET_SPEED);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.8F, 1.8F);
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CHARGES.isEmpty()) {
            CHARGES.removeIf(charge -> !tickCharge(charge));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CHARGES.clear();
    }

}

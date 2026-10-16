package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.projectile.magic.WaterBallProjectile;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;

public class WaterPumpQuirk extends Skill {

    private static final QuirkSkillsConfig.WaterPump CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WaterPump;

    private static final int WATER_BEAM = 0;
    private static final int HYDRO_CANNON = 1;
    private static final int MODES = 2;
    private static final int LEND_SECONDS = 10;

    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "water_pump_water_boost");
    private static final DustParticleOptions STREAM = new DustParticleOptions(new Vector3f(0.25F, 0.55F, 1.0F), 1.0F);
    private static final double STREAM_WIDTH = 0.4D;
    private static final double HAND_SIDE = 0.35D;

    public WaterPumpQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case WATER_BEAM -> CONFIG.beamAuraCost;
            case HYDRO_CANNON -> CONFIG.cannonAuraCost;
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
            case WATER_BEAM -> "water_pump.water_beam";
            case HYDRO_CANNON -> "water_pump.hydro_cannon";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getWaterPump(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WATER_PUMP.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

// more temp skill lending for water breathing and such
    private static void lendSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        ManasSkillInstance owned = skills.getSkill(skill).orElse(null);
        if (owned == null) {
            TensuraSkillInstance lent = skill.createLearningInstance(entity);
            lent.setMastery(0.0D);
            lent.setRemoveTime(LEND_SECONDS);
            if (SkillHelper.learnSkill(entity, lent, LEND_SECONDS)) {
                skills.getSkill(skill).filter(learned -> !learned.isToggled()).ifPresent(learned -> {
                    learned.setToggled(true);
                    learned.onToggleOn(entity);
                    skills.markDirty();
                });
            }
        } else if (owned.isTemporarySkill() && owned.getRemoveTime() < LEND_SECONDS) {
            owned.setRemoveTime(LEND_SECONDS);
            skills.markDirty();
        }
    }

    private static void takeSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        if (skills.getSkill(skill).filter(ManasSkillInstance::isTemporarySkill).isPresent()) {
            skills.forgetSkill(skill);
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            lendSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
        }
    }

    // Water Affinity

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        updateWater(instance, entity);
        if (!instance.isTemporarySkill()) {
            lendSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
            if (instance.isMastered(entity)) {
                lendSkill(entity, ResistanceSkills.WATER_ATTACK_RESISTANCE.get());
            }
        }

        if (instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, 0.0D);
        takeSkill(entity, IntrinsicSkills.WATER_BREATHING.get());
        takeSkill(entity, ResistanceSkills.WATER_ATTACK_RESISTANCE.get());
    }

    private static void updateWater(ManasSkillInstance instance, LivingEntity entity) {
        double boost = instance.isMastered(entity) ? CONFIG.waterBoostMastered : CONFIG.waterBoost;
        AdditiveBoost.apply(entity, TensuraAttributes.WATER_BOOST, WATER, instance.isToggled() ? boost : 0.0D);
    }

    // Water Beam

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != WATER_BEAM || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, WATER_BEAM)) {
            return false;
        }

        boolean hit = heldTicks % Math.max(1, CONFIG.beamHitTicks) == 0;
        waterStream(level, instance, entity, WATER_BEAM, CONFIG.beamRange, hit ? (float) CONFIG.beamDamage : 0.0F, true);
        if (heldTicks % 4 == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WATER_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.6F);
        }

        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    // This puts out fire
    // top 3 water quirks in the mod (theres only 3)
    public static void waterStream(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode, double range, float damage, boolean bothHands) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }

        Vec3 from = eye;
        Vec3 to = end;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(STREAM_WIDTH),
                target -> target != owner && target.isAlive() && !target.isSpectator() && HitRules.canHit(owner, target) && target.getBoundingBox().inflate(STREAM_WIDTH).clip(from, to).isPresent())) {
            target.clearFire();
            if (damage > 0.0F) {
                target.invulnerableTime = 0;
                target.hurt(waterSource(instance, owner, mode), damage);
                SkillHelper.knockBack(target, owner, instance, new Vec3(look.x, 0.0D, look.z), 0.15D, 0.0D, 0.02D);
                target.hurtMarked = true;
            }
        }

        douse(level, instance, owner, end);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 hands = eye.add(0.0D, -0.4D, 0.0D).add(look.scale(0.4D));
        for (int side = bothHands ? -1 : 1; side <= 1; side += 2) {
            Vec3 hand = hands.add(right.scale(side * HAND_SIDE));
            double length = hand.distanceTo(end);
            Vec3 step = end.subtract(hand).normalize();
            for (double along = 0.0D; along <= length; along += 0.5D) {
                Vec3 spot = hand.add(step.scale(along));
                level.sendParticles(STREAM, spot.x, spot.y, spot.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
                if (owner.getRandom().nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.SPLASH, spot.x, spot.y, spot.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
                }
            }
        }

        level.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 6, 0.2D, 0.2D, 0.2D, 0.1D);
    }

    public static void douse(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 spot) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(spot).offset(-1, -1, -1), BlockPos.containing(spot).offset(1, 1, 1))) {
            if (!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock) || owner instanceof Player player && !level.mayInteract(player, pos)
                    || TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(instance, level, owner, pos.getX(), pos.getY(), pos.getZ()).isFalse()) {
                continue;
            }

            level.removeBlock(pos, false);
            GroundBlocks.griefed(level, instance, owner, pos);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
    }

    public static DamageSource waterSource(ManasSkillInstance instance, LivingEntity owner, int mode) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.WATER_ELEMENTAL, mode);
        ((TensuraDamageSource) source).tensura$setElement(Element.WATER);
        return source;
    }

    // Hydro Cannon

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != HYDRO_CANNON || !(entity.level() instanceof ServerLevel level) || EnergyHelper.isOutOfEnergy(entity, instance, HYDRO_CANNON)) {
            return;
        }

        shootWaterBall(level, instance, entity, HYDRO_CANNON, (float) CONFIG.cannonDamage, (float) CONFIG.cannonSize, 1.2F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2F, 0.6F);
        instance.setCoolDown(CONFIG.cannonCooldown, HYDRO_CANNON);
        instance.addMasteryPoint(entity);
    }

    // Tensura's own water ball, fired the way its water magic does
    public static WaterBallProjectile shootWaterBall(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, int mode, float damage, float size, float speed) {
        WaterBallProjectile ball = new WaterBallProjectile(level, owner);
        ball.setSpeed(speed);
        ball.setDamage(damage);
        ball.setKnockForce(Math.max(1.0F, size * 0.5F));
        ball.setSize(size);
        ball.setVisualSize(size);
        ball.setSkill(owner, instance, (TensuraSkill) instance.getSkill(), mode);
        ball.setPosAndShoot(owner);
        level.addFreshEntity(ball);
        return ball;
    }

}

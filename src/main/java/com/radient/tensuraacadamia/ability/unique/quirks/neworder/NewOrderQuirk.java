package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.magic.barrier.BarrierEntity;
import io.github.manasmods.tensura.entity.magic.beam.BeamProjectile;
import io.github.manasmods.tensura.entity.magic.spike.SpikeEntity;
import io.github.manasmods.tensura.entity.template.subclass.ILivingPartEntity;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class NewOrderQuirk extends Skill {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    static final int SAY_MY_NAME = 0;
    static final int RULES = 1;
    static final int THUNDER_STRIKE = 2;
    static final int SOLAR_BEAM = 3;
    static final int BODY_OF_AIR = 4;
    static final int FIST_BUMP = 5;
    static final int AIR_SPEAR = 6;
    static final int WIND_CLAP = 7;
    static final int VACUUM = 8;
    static final int RESTRAIN = 9;
    static final int EXPLODE = 10;
    static final int PROTECT = 11;
    static final int WEAPON = 12;
    static final int SPIKY = 13;
    static final int FIST_HIT = 14;
    static final int MODES = 15;

    // The order the skill bar cycles through, Body of Air before what the copy does from behind
    private static final int[] ORDER = {SAY_MY_NAME, RULES, BODY_OF_AIR, THUNDER_STRIKE, SOLAR_BEAM, FIST_BUMP, AIR_SPEAR, VACUUM, RESTRAIN, EXPLODE, PROTECT, WEAPON, SPIKY, WIND_CLAP, FIST_HIT};
    private static final double MOB_ENTITY_REACH = 3.0D;
    private static final double MOB_BLOCK_REACH = 4.5D;
    static final double AIR_TOUCH = 1.5D;
    public static final int NAME_COLOR = 0xFFFFFF;
    private record Touch(int kind, @Nullable LivingEntity entity, @Nullable BlockState state, Vec3 point) {}

    public NewOrderQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SAY_MY_NAME -> CONFIG.nameAuraCost;
            case RULES -> CONFIG.ruleAuraCost;
            case THUNDER_STRIKE -> CONFIG.thunderAuraCost;
            case SOLAR_BEAM -> CONFIG.solarAuraCost;
            case BODY_OF_AIR -> CONFIG.bodyAuraCost;
            case FIST_BUMP -> CONFIG.fistBumpAuraCost;
            case AIR_SPEAR -> CONFIG.spearAuraCost;
            case WIND_CLAP -> CONFIG.clapAuraCost;
            case VACUUM -> CONFIG.vacuumAuraCost;
            case RESTRAIN -> CONFIG.restrainAuraCost;
            case EXPLODE -> CONFIG.explodeAuraCost;
            case PROTECT -> CONFIG.protectAuraCost;
            case WEAPON -> CONFIG.weaponAuraCost;
            case SPIKY -> CONFIG.spikyAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    // Modes only show once they can be used, just fixes clutter
    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        int at = 0;
        while (at < ORDER.length && ORDER[at] != mode) {
            at++;
        }
        for (int step = 1; step <= ORDER.length; step++) {
            int next = ORDER[Math.floorMod(at + (reverse ? -step : step), ORDER.length)];
            if (isModeAvailable(instance, next)) {
                return next;
            }
        }
        return mode;
    }

    static boolean isModeAvailable(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case THUNDER_STRIKE, SOLAR_BEAM -> !NewOrderBody.isInside(instance) && NewOrderRules.hasRule(instance, NewOrderRule.HARDEN);
            case FIST_BUMP, AIR_SPEAR -> NewOrderBody.isInside(instance);
            case BODY_OF_AIR -> NewOrderBody.isInside(instance) || NewOrderRules.hasRule(instance, NewOrderRule.HARDEN);
            case WIND_CLAP, FIST_HIT -> false;
            case VACUUM, RESTRAIN, EXPLODE, PROTECT, WEAPON, SPIKY -> NewOrderRules.hasRule(instance, getModeRule(mode));
            default -> true;
        };
    }

    // The rules that give New Order a skill of their own
    static @Nullable NewOrderRule getModeRule(int mode) {
        return switch (mode) {
            case VACUUM, RESTRAIN, EXPLODE, PROTECT, WEAPON -> NewOrderRule.AIR_CONTROL;
            case SPIKY -> NewOrderRule.SPIKY;
            default -> null;
        };
    }

    // Swaps the mode when they cant be used
    // basically just stops you from being on a skill you cant use after a rule change or no longer inside the construct
    static int getStandIn(ManasSkillInstance instance, int mode) {
        int paired = switch (mode) {
            case THUNDER_STRIKE -> FIST_BUMP;
            case SOLAR_BEAM -> AIR_SPEAR;
            case FIST_BUMP, WIND_CLAP -> THUNDER_STRIKE;
            case AIR_SPEAR -> SOLAR_BEAM;
            case VACUUM, RESTRAIN, EXPLODE, PROTECT, WEAPON, SPIKY -> RULES;
            default -> BODY_OF_AIR;
        };
        if (isModeAvailable(instance, paired)) {
            return paired;
        }
        return isModeAvailable(instance, BODY_OF_AIR) ? BODY_OF_AIR : RULES;
    }

    // Fixed cooldowns that used to be on right click that changed to skil modes from air control
    static void fixCooldowns(ManasSkillInstance instance) {
        List<Integer> cooldowns = instance.getCooldownList();
        if (cooldowns.size() < MODES) {
            List<Integer> longer = new ArrayList<>(cooldowns);
            while (longer.size() < MODES) {
                longer.add(0);
            }
            instance.setCoolDownList(longer);
            instance.markDirty();
        }
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SAY_MY_NAME -> "new_order.say_my_name";
            case RULES -> "new_order.rules";
            case THUNDER_STRIKE -> "new_order.thunder_strike";
            case SOLAR_BEAM -> "new_order.solar_beam";
            case BODY_OF_AIR -> "new_order.body_of_air";
            case FIST_BUMP -> "new_order.fist_bump";
            case AIR_SPEAR -> "new_order.air_spear";
            case WIND_CLAP -> "new_order.wind_clap";
            case VACUUM -> "new_order.no_longer_exist";
            case RESTRAIN -> "new_order.restrain";
            case EXPLODE -> "new_order.explode";
            case PROTECT -> "new_order.protect";
            case WEAPON -> "new_order.weapon";
            case SPIKY -> "new_order.spiky";
            default -> super.getModeId(instance, mode);
        };
    }

    static Component getCalloutName(ManasSkillInstance instance, int mode) {
        return ((Skill) instance.getSkill()).getModeName(instance, mode);
    }

    public static Optional<ManasSkillInstance> getNewOrder(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.NEW_ORDER.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == SAY_MY_NAME) {
            sayMyName(level, instance, entity);
        } else if (mode == RULES && entity instanceof ServerPlayer player) {
            if (NewOrderRules.getNamed(instance, level.getGameTime()) == null && NewOrderRules.getRuleList(instance).isEmpty()) {
                NewOrderUtil.fail(player, Component.translatable("tracadamia.skill.new_order.no_name"));
            } else {
                NewOrderRules.sendRules(player, instance, true);
            }
        } else if (mode == THUNDER_STRIKE) {
            NewOrderStrikes.thunderStrike(level, instance, entity);
        } else if (mode == SOLAR_BEAM) {
            NewOrderStrikes.solarBeam(level, instance, entity);
        } else if (mode == BODY_OF_AIR) {
            NewOrderBody.bodyOfAir(level, instance, entity);
        } else if (mode == FIST_BUMP) {
            if (entity.isShiftKeyDown()) {
                NewOrderBodyMoves.windClap(level, instance, entity);
            } else {
                NewOrderBodyMoves.fistBump(level, instance, entity);
            }
        } else if (mode == AIR_SPEAR) {
            NewOrderBodyMoves.airSpear(level, instance, entity);
        } else if (getModeRule(mode) != null) {
            NewOrderAirSkills.use(level, instance, entity, mode);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        NewOrderSelf.applySelf(entity, instance, EnumSet.noneOf(NewOrderRule.class));
        NewOrderSeals.clearSeals(entity.getUUID());
        NewOrderCopy.forget(entity);
        NewOrderVacuum.forget(entity);
        NewOrderStrikes.forget(entity);
        NewOrderBodyMoves.forget(entity);
        NewOrderBody.forget(entity, instance);
        NewOrderAirSkills.forget(entity, instance);
        NewOrderGround.forget(entity);
    }

    private static Touch touch(ServerLevel level, LivingEntity entity) {
        double entityReach = entity instanceof Player player ? player.entityInteractionRange() : MOB_ENTITY_REACH;
        double blockReach = entity instanceof Player player ? player.blockInteractionRange() : MOB_BLOCK_REACH;
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getViewVector(1.0F);
        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(look.scale(blockReach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        double blockDistance = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceTo(block.getLocation());
        double reach = Math.min(entityReach, blockDistance);
        Vec3 end = eye.add(look.scale(reach));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(entity, eye, end, entity.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D),
                other -> other instanceof LivingEntity living && living.isAlive() && !other.isSpectator() && other.isPickable(), reach * reach);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return new Touch(NewOrderRule.ENTITY, ILivingPartEntity.checkForHead(living), null, hit.getLocation());
        }

        if (block.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(block.getBlockPos());
            if (!state.isAir()) {
                return new Touch(NewOrderRule.GROUND, null, state, block.getLocation());
            }
        }

        return new Touch(NewOrderRule.AIR, null, null, eye.add(look.scale(Math.min(blockReach, AIR_TOUCH))));
    }

    private static void sayMyName(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        Touch touch = entity.isShiftKeyDown() ? new Touch(NewOrderRule.USER, entity, null, entity.getBoundingBox().getCenter()) : touch(level, entity);
        if (EnergyHelper.isOutOfEnergy(entity, instance, SAY_MY_NAME)) {
            return;
        }

        Component name = switch (touch.kind()) {
            case NewOrderRule.USER, NewOrderRule.ENTITY -> touch.entity() == null ? Component.empty() : touch.entity().getName();
            case NewOrderRule.GROUND -> touch.state() == null ? Component.empty() : touch.state().getBlock().getName();
            default -> Component.translatable("tracadamia.skill.new_order.kind.air");
        };

        CompoundTag named = new CompoundTag();
        named.putInt("kind", touch.kind());
        if (touch.entity() != null) {
            named.putUUID("id", touch.entity().getUUID());
        }
        named.putString("name", Component.Serializer.toJson(name, level.registryAccess()));
        if (touch.state() != null) {
            named.putString("block", BuiltInRegistries.BLOCK.getKey(touch.state().getBlock()).toString());
        }
        named.putLong("time", level.getGameTime());
        instance.getOrCreateTag().put(NewOrderRules.NAMED_TAG, named);
        instance.setCoolDown(CONFIG.nameCooldown, SAY_MY_NAME);
        instance.addMasteryPoint(entity);
        NewOrderUtil.dirty(instance, entity);

        entity.swing(InteractionHand.MAIN_HAND, true);
        NewOrderUtil.playSound(level, touch.point(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        Vec3 point = touch.point();
        level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 10, 0.2D, 0.2D, 0.2D, 0.03D);
        if (touch.entity() != null) {
            LivingEntity called = touch.entity();
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, called.getX(), called.getY(0.5D), called.getZ(), 12, called.getBbWidth() * 0.5D, called.getBbHeight() * 0.4D, called.getBbWidth() * 0.5D, 0.05D);
            NewOrderUtil.callout(level, new Vec3(called.getX(), called.getY() + called.getBbHeight() + 0.4D, called.getZ()), called, Component.translatable("tracadamia.skill.new_order.callout", name), NAME_COLOR);

        } else {
            NewOrderUtil.callout(level, point.add(0.0D, touch.kind() == NewOrderRule.GROUND ? 0.6D : 0.3D, 0.0D), null, Component.translatable("tracadamia.skill.new_order.callout", name), NAME_COLOR);
        }

    }

    @Override
    public void onProjectileHit(ManasSkillInstance instance, LivingEntity entity, EntityHitResult hitResult, Projectile projectile, Changeable<ProjectileDeflection> deflection, Changeable<EntityEvents.ProjectileHitResult> result) {
        if (!(entity.level() instanceof ServerLevel level) || !NewOrderRules.hasRule(instance, NewOrderRule.REFLECT) || projectile instanceof BarrierEntity || projectile instanceof SpikeEntity || projectile instanceof BeamProjectile || projectile instanceof FishingHook) {
            return;
        }

        Entity shooter = projectile.getOwner();
        if (shooter == entity || shooter instanceof LivingEntity living && !HitRules.canHit(living, entity)) {
            return;
        }

        Vec3 at = hitResult.getLocation();
        double speed = Math.max(0.5D, projectile.getDeltaMovement().length());
        Vec3 back = shooter != null && shooter.isAlive() && shooter.level() == level ? shooter.getBoundingBox().getCenter().subtract(at) : projectile.getDeltaMovement().reverse();
        if (back.lengthSqr() < 1.0E-6D) {
            back = entity.getViewVector(1.0F);
        }
        back = back.normalize();

        projectile.setPos(at.add(back.scale(entity.getBbWidth() * 0.5D + 0.3D)));
        projectile.shoot(back.x, back.y, back.z, (float) speed, 0.0F);
        projectile.setOwner(entity);
        projectile.hasImpulse = true;
        result.set(EntityEvents.ProjectileHitResult.PASS);

        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.1D, 0.1D, 0.1D, 0.12D);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.2D, 0.2D, 0.2D, 0.2D);
        NewOrderUtil.playSound(level, at, TensuraSoundEvents.REFLECTION.get(), 1.0F, 1.2F);
        instance.addMasteryPoint(entity);
    }

    // Slight change for beams since they're kinda weird
    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (!(source.getDirectEntity() instanceof BeamProjectile beam) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !NewOrderRules.hasRule(instance, NewOrderRule.REFLECT)
                || !(entity.level() instanceof ServerLevel level)) {
            return true;
        }

        Entity caster = beam.getOwner();
        if (caster == entity || caster instanceof LivingEntity living && !HitRules.canHit(living, entity)) {
            return true;
        }

        if (caster instanceof LivingEntity living && living.isAlive() && living.level() == level) {
            DamageSource reflected = TensuraDamageTypes.getEntityDamageSource(level, TensuraDamageTypes.REFLECTED, entity);
            ((TensuraDamageSource) reflected).tensura$setAbilityInstance(instance);
            living.hurt(reflected, amount);
            if (level.getGameTime() % 4L == 0L) {
                ray(level, entity.getBoundingBox().getCenter(), living.getBoundingBox().getCenter());
                NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.REFLECTION.get(), 0.6F, 1.4F);
            }
        }
        return false;
    }

    private static void ray(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 3.0D));
        for (int point = 0; point <= points; point++) {
            Vec3 at = from.add(step.scale((double) point / points));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.HomingBarrageProjectile;
import com.radient.tensuraacadamia.mixin.HomingArrowAccessor;
import com.radient.tensuraacadamia.regestry.HomingEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Comparator;

public final class HomingQuirk extends Skill {
    public static final double LOCK_RANGE = 600, GUIDANCE_RANGE = 200;
    private static final String LOCK = "HomingTarget", SNIPE = "TracadamiaSnipe", GUIDED = "TracadamiaHomingTarget";

    public HomingQuirk() { super(SkillType.UNIQUE); }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/homingicon.png");
    }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) { return 0; }
    @Override public int getModes(ManasSkillInstance instance) { return instance.getMastery() >= getMaxMastery() ? 2 : 1; }
    @Override public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 1 ? "homing.barrage" : "homing.lock_on";
    }

    public static boolean isEnemy(LivingEntity owner, LivingEntity target) {
        return owner != target && target.isAlive() && !target.isSpectator()
                && !(target instanceof Player player && player.getAbilities().invulnerable)
                && !owner.isAlliedTo(target) && !SubordinateHelper.isAlly(owner, target);
    }

    public static LivingEntity lockedTarget(LivingEntity owner, ManasSkillInstance instance, double range) {
        var tag = instance.getOrCreateTag();
        if (!(owner.level() instanceof ServerLevel level) || !tag.hasUUID(LOCK)) return null;
        Entity entity = level.getEntity(tag.getUUID(LOCK));
        return entity instanceof LivingEntity target && isEnemy(owner, target)
                && owner.distanceToSqr(target) <= range * range ? target : null;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int keyNumber, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode > 1) return;
        if (mode == 0) {
            Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(LOCK_RANGE));
            var hit = ProjectileUtil.getEntityHitResult(owner, eye, end, new AABB(eye, end).inflate(1),
                    entity -> entity instanceof LivingEntity living && isEnemy(owner, living), LOCK_RANGE * LOCK_RANGE);
            if (hit == null || !(hit.getEntity() instanceof LivingEntity target) || !owner.hasLineOfSight(target)) {
                message(owner, "no_target"); return;
            }
            instance.getOrCreateTag().putUUID(LOCK, target.getUUID());
            if (owner instanceof Player player) player.displayClientMessage(
                    Component.translatable("tracadamia.skill.homing.locked", target.getDisplayName()), true);
        } else {
            if (!instance.isMastered(owner)) { message(owner, "mastery_required"); return; }
            LivingEntity target = lockedTarget(owner, instance, GUIDANCE_RANGE);
            if (target == null) { message(owner, "lock_required"); return; }
            for (int i = 0; i < 30; i++) {
                HomingBarrageProjectile shot = HomingEntities.BARRAGE.get().create(level);
                if (shot == null) continue;
                shot.setOwner(owner);
                shot.setSkill(owner, instance, this, 1);
                shot.setElementType(level.random.nextInt(5));
                shot.setTarget(target);
                shot.setPos(owner.getEyePosition());
                Vec3 direction = owner.getLookAngle();
                shot.shoot(direction.x, direction.y, direction.z, 1.0F, 25.0F);
                shot.setDeltaMovement(shot.getDeltaMovement().normalize());
                level.addFreshEntity(shot);
            }
        }
        instance.addMasteryPoint(owner);
        instance.markDirty();
    }

    private static void message(LivingEntity owner, String key) {
        if (owner instanceof Player player) player.displayClientMessage(
                Component.translatable("tracadamia.skill.homing." + key), true);
    }

    @SubscribeEvent
    public static void onProjectileTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile projectile) || !(projectile.level() instanceof ServerLevel level)
                || !(projectile.getOwner() instanceof LivingEntity owner) || projectile instanceof HomingBarrageProjectile) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.HOMING.get()).orElse(null);
        if (instance == null) { clearGuidance(projectile); return; }
        boolean bow = projectile instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                && arrow.getWeaponItem().getItem() instanceof BowItem;
        if (bow) projectile.getPersistentData().putBoolean(SNIPE, true);
        if (projectile instanceof AbstractArrow arrow && ((HomingArrowAccessor) arrow).tracadamia$isInGround()) return;
        Vec3 movement = HomingSteering.motion(projectile);
        if (movement.lengthSqr() < 0.0001) return;
        LivingEntity target = lockedTarget(owner, instance, GUIDANCE_RANGE);
        boolean locked = target != null;
        if (target == null && bow) {
            Vec3 ahead = movement.normalize().scale(Math.min(12, Math.max(4, movement.length() * 3)));
            Vec3 start = projectile.position(), end = start.add(ahead);
            target = level.getEntitiesOfClass(LivingEntity.class, projectile.getBoundingBox().expandTowards(ahead).inflate(2),
                    enemy -> isEnemy(owner, enemy) && (enemy.getBoundingBox().distanceToSqr(start) <= 4
                            || enemy.getBoundingBox().inflate(2).clip(start, end).isPresent())
                            && level.clip(new ClipContext(projectile.position(), enemy.getBoundingBox().getCenter(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile)).getType() == HitResult.Type.MISS)
                    .stream().min(Comparator.comparingDouble(enemy -> enemy.distanceToSqr(projectile))).orElse(null);
        }
        if (target == null) { clearGuidance(projectile); return; }
        Vec3 aim = target.getBoundingBox().getCenter().subtract(projectile.position()).normalize();
        Vec3 steered = movement.normalize().lerp(aim, locked ? 0.35 : 0.15);
        if (steered.lengthSqr() < 0.0001) steered = aim;
        var route = HomingSteering.steer(projectile, steered.normalize().scale(movement.length()), target.getBoundingBox().getCenter());
        projectile.setDeltaMovement(route.velocity());
        if (projectile instanceof TensuraFlyingProjectile magic) {
            magic.setHomingTarget(route.avoiding() ? null : target);
            projectile.getPersistentData().putUUID(GUIDED, target.getUUID());
        }
        projectile.hasImpulse = true;
    }

    private static void clearGuidance(Projectile projectile) {
        var tag = projectile.getPersistentData();
        if (projectile instanceof TensuraFlyingProjectile magic && tag.hasUUID(GUIDED)
                && magic.getHomingTarget() != null && magic.getHomingTarget().getUUID().equals(tag.getUUID(GUIDED)))
            magic.setHomingTarget(null);
        tag.remove(GUIDED);
        HomingSteering.clear(projectile);
    }

    @SubscribeEvent
    public static void onBowDamage(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide && event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getPersistentData().getBoolean(SNIPE)) event.setAmount(event.getAmount() * 2);
    }
}

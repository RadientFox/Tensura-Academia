package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.HomingSteering;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.projectile.magic.AuraBulletProjectile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class HomingBarrageProjectile extends AuraBulletProjectile {
    private static final int HOMING_DELAY_TICKS = 20;
    private static final int[] COLORS = {0xFF6518, 0xA6E4FF, 0x164BD9, 0xEAF6FF, 0xC66AFF};
    private int elementType;
    private UUID targetId;

    public HomingBarrageProjectile(EntityType<? extends HomingBarrageProjectile> type, Level level) {
        super(type, level);
        setDamage(20);
        setLife(600);
        setSpeed(1);
        setSize(0.35F);
        setVisualSize(0.35F);
        setExplosionRadius(0);
        setHitRadius(0);
        setIgnoreInvulnerabilityOnHit(true);
        setNoGravity(true);
    }

    public void setElementType(int type) {
        elementType = Math.floorMod(type, 5);
        setColor(COLORS[elementType]);
        setElementalAttack(elementType != 4);
        setElement(elementType == 0 ? Element.FLAME : elementType == 1 || elementType == 2 ? Element.WATER : Element.WIND);
    }

    public void setTarget(LivingEntity target) { targetId = target.getUUID(); }

    @Override public ResourceKey<DamageType> getDamageType() {
        return switch (elementType) {
            case 0 -> TensuraDamageTypes.HEAT_WAVE;
            case 1 -> TensuraDamageTypes.ICE_ELEMENTAL;
            case 2 -> TensuraDamageTypes.WATER_ELEMENTAL;
            case 3 -> TensuraDamageTypes.WIND_ELEMENTAL;
            default -> TensuraDamageTypes.MAGIC_GENERIC;
        };
    }

    @Override public void tick() {
        if (level() instanceof ServerLevel server) {
            Entity owner = getOwner();
            Entity target = targetId == null ? null : server.getEntity(targetId);
            LivingEntity enemy = target instanceof LivingEntity living && living.isAlive()
                    && (owner == null || owner.distanceToSqr(target) <= HomingQuirk.GUIDANCE_RANGE * HomingQuirk.GUIDANCE_RANGE)
                    ? living : null;
            boolean homing = enemy != null && getAge() >= HOMING_DELAY_TICKS;
            setHomingTarget(homing ? enemy : null);
            if (enemy != null) {
                Vec3 goal = homing ? enemy.getBoundingBox().getCenter()
                        : position().add(HomingSteering.motion(this).normalize().scale(20));
                Vec3 aim = goal.subtract(position()).normalize();
                Vec3 movement = HomingSteering.motion(this).normalize().lerp(aim, homing ? 0.35 : 0).normalize();
                if (movement.lengthSqr() < 0.0001) movement = aim;
                var route = HomingSteering.steer(this, movement, goal);
                setDeltaMovement(route.velocity());
                if (route.avoiding()) setHomingTarget(null);
            } else HomingSteering.clear(this);
        }
        super.tick();
    }

    @Override public void updateMovement() {
        super.updateMovement();
        if (getDeltaMovement().lengthSqr() > 0.0001) setDeltaMovement(getDeltaMovement().normalize());
    }

    @Override protected boolean shouldRemove() { return getAge() >= 600; }
    @Override protected void onHitBlock(BlockHitResult hit) { }

    @Override protected void onHitEntity(EntityHitResult hit, EntityEvents.ProjectileHitResult result) {
        if (result == EntityEvents.ProjectileHitResult.PASS) return;
        super.onHitEntity(hit, result);
        if (!level().isClientSide) discard();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HomingElement", elementType);
        if (targetId != null) tag.putUUID("HomingTarget", targetId);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setElementType(tag.getInt("HomingElement"));
        targetId = tag.hasUUID("HomingTarget") ? tag.getUUID("HomingTarget") : null;
    }
}

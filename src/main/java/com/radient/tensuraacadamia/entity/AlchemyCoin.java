package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.AlchemyQuirk;
import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class AlchemyCoin extends ThrowableItemProjectile {
    private boolean mastered;
    public AlchemyCoin(EntityType<? extends AlchemyCoin> type, Level level) { super(type, level); }
    @Override protected Item getDefaultItem() { return Items.GOLD_NUGGET; }
    public void configure(LivingEntity owner, boolean mastered) {
        setOwner(owner); this.mastered = mastered; setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.4)));
        shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0, 1.8F, 0.5F);
    }
    @Override protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity target && getOwner() instanceof LivingEntity owner && HomingQuirk.isEnemy(owner, target);
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit); if (level().isClientSide) return;
        if (hit instanceof EntityHitResult impact && impact.getEntity() instanceof LivingEntity target) {
            AlchemyQuirk.lockTarget(target, mastered);
            level().playSound(null, target.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1, 0.8F);
        }
        discard();
    }
    @Override public void tick() { super.tick(); if (!level().isClientSide && tickCount >= 100) discard(); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("AlchemyMastered", mastered); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); mastered = tag.getBoolean("AlchemyMastered"); }
}

package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.regestry.GreyTornadoEntities;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.projectile.magic.WindTornadoProjectile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GreyTornadoBlade extends WindTornadoProjectile {
    public static final ResourceLocation GREY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "tracadamia", "textures/entity/grey_tornado_blade.png");

    public GreyTornadoBlade(EntityType<? extends GreyTornadoBlade> type, Level level) {
        super(type, level);
        setElementalAttack(false);
    }

    public GreyTornadoBlade(Level level, LivingEntity owner) {
        this(GreyTornadoEntities.TORNADO_BLADE.get(), level);
        setOwner(owner);
        setLife(400);
        setDamage(10);
        setSecondaryDamage(10);
        setNoGravity(true);
        setPos(owner.position().add(0, 0.4, 0));
    }

    @Override
    public ResourceLocation getTexture() {
        return GREY_TEXTURE;
    }

    @Override
    public ResourceKey<DamageType> getDamageType() {
        return DamageTypes.MOB_ATTACK;
    }

    @Override
    public ResourceKey<DamageType> getSecondaryDamageType() {
        return TensuraDamageTypes.WIND_ELEMENTAL;
    }

    @Override
    public void tickHandler() {
        if (level().isClientSide) return;
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || owner.level() != level()) {
            discard();
            return;
        }

        Vec3 forward = owner.getLookAngle();
        Vec3 horizontal = new Vec3(forward.x, 0, forward.z).normalize().scale(1.8);
        setPos(owner.getX() + horizontal.x, owner.getY() + 0.3, owner.getZ() + horizontal.z);
        setDeltaMovement(Vec3.ZERO);

        if (getAge() % 10 == 0) {
            for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(1.2),
                    target -> target != owner && target.isAlive() && !target.isAlliedTo(owner))) {
                dealDamage(target);
            }
        }
    }
}

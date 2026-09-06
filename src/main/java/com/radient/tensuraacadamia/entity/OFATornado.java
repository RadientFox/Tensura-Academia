package com.radient.tensuraacadamia.entity;
/*
import com.radient.tensuraacadamia.config.skills.OFAConfig;
import com.radient.tensuraacadamia.regestry.OFAEntitys;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.magic.aspectual.wind.TornadoBladeMagic;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.entity.projectile.TensuraFlyingProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindBladeProjectile;
import io.github.manasmods.tensura.entity.projectile.magic.WindTornadoProjectile;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.entity.ProjectileEntityTypes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.processing.Generated;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class OFATornado extends TensuraFlyingProjectile implements GeoEntity {
    protected static final EntityDataAccessor<Integer> BURST_DELAY;
    protected static final EntityDataAccessor<Boolean> BURSTING;
    protected float bladeDamage;
    protected float pullForce;
    private final AnimatableInstanceCache cache;

    public OFATornado(EntityType<? extends OFATornado> entityType, Level level) {
        super(entityType, level);
        this.bladeDamage = 0.0F;
        this.pullForce = 0.5F;
        this.cache = GeckoLibUtil.createInstanceCache(this);
        this.setElementalAttack(true);
        this.setElement(Element.WIND);
    }

    public OFATornado(Level levelIn, LivingEntity shooter) {
        this((EntityType) OFAEntitys.OFA_TORNADO.get(), levelIn);
        this.setOwner(shooter);
    }




    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BURST_DELAY, 40);
        builder.define(BURSTING, false);
    }

    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Bursting", this.isBursting());
        compound.putInt("BurstDelay", this.getBurstDelay());
        compound.putFloat("PullForce", this.getPullForce());
    }

    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setBursting(compound.getBoolean("Bursting"));
        this.setBurstDelay(compound.getInt("BurstDelay"));
        this.setPullForce(compound.getFloat("PullForce"));
    }

    public int getBurstDelay() {
        return (Integer)this.entityData.get(BURST_DELAY);
    }

    public void setBurstDelay(int delay) {
        this.entityData.set(BURST_DELAY, delay);
    }

    public boolean isBursting() {
        return (Boolean)this.entityData.get(BURSTING);
    }

    public void setBursting(boolean bursting) {
        this.entityData.set(BURSTING, bursting);
    }

    public ResourceKey<DamageType> getDamageType() {
        return TensuraDamageTypes.WIND_ELEMENTAL;
    }

    public boolean shouldDiscardInWater() {
        return false;
    }

    public ResourceLocation getTexture() {
        return ResourceLocation.fromNamespaceAndPath("tensura", "textures/entity/misc/wind_tornado.png");
    }

    public void tick() {
        super.tick();
        if (this.isBursting()) {
            this.setBurstDelay(this.getBurstDelay() - 1);
            if (this.getBurstDelay() == 13) {
                if (this.getHitRadius() > 0.0F) {
                    List<Entity> list = this.level().getEntitiesOfClass(Entity.class, this.getBoundingBox().inflate((double)this.getHitRadius()), (x$0) -> {
                        return this.canHitEntity(x$0);
                    });

                    Entity target;
                    for(Iterator var2 = list.iterator(); var2.hasNext(); this.hitEntity(target, EntityEvents.ProjectileHitResult.DEFAULT)) {
                        target = (Entity)var2.next();
                        if (this.isIgnoreInvulnerabilityOnHit() && (this.getDamage() > 0.0F || this.getSecondaryDamage() > 0.0F)) {
                            target.invulnerableTime = 0;
                        }
                    }
                }


                this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), SoundEvents.WIND_CHARGE_BURST, SoundSource.NEUTRAL, 3.0F, 1.0F);
                this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), (SoundEvent) TensuraSoundEvents.CAST_WIND.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
                this.onExplosion(this.getX(), this.getY(), this.getZ());
            } else if (this.getBurstDelay() > 13) {
                this.applyPull(this.getPullForce());
            }
        }

    }

    protected void applyBlockHitPre(BlockHitResult pResult) {
    }

    protected void applyBlockHitPost(BlockHitResult pResult) {
        if (!this.isPiercingBlock()) {
            Vec3 vec3 = pResult.getLocation().subtract(this.getX(), this.getY(), this.getZ());
            this.setDeltaMovement(vec3);
            this.setBursting(true);
            this.setAge(this.getLife() - this.getBurstDelay());
            Vec3 vec32 = vec3.normalize().scale(0.05000000074505806);
            this.setPosRaw(this.getX() - vec32.x, this.getY() - vec32.y, this.getZ() - vec32.z);
        }

    }

    protected void applyHitEntity(Entity entity, EntityHitResult result, EntityEvents.ProjectileHitResult customResult) {
        if (!this.isPiercingEntity()) {
            Vec3 vec3 = this.position().subtract(this.getX(), this.getY(), this.getZ());
            this.setDeltaMovement(vec3);
            this.setBursting(true);
            this.setAge(this.getLife() - this.getBurstDelay());
            Vec3 vec32 = vec3.normalize().scale(0.05000000074505806);
            this.setPosRaw(this.getX() - vec32.x, this.getY() - vec32.y, this.getZ() - vec32.z);
        }

    }

    private void applyPull(float pullMultiplier) {
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate((double)(this.getHitRadius() * 2.0F)), (targetx) -> {
            return (this.getOwner() == null || !targetx.isAlliedTo(this.getOwner()) && !targetx.is(this.getOwner())) && !targetx.getType().is(TensuraEntityTags.FULL_GRAVITY_CONTROL) && !targetx.getType().is(TensuraEntityTags.NO_FORCED_MOVE);
        });
        if (!list.isEmpty()) {
            Iterator var3 = list.iterator();

            while(var3.hasNext()) {
                LivingEntity target = (LivingEntity)var3.next();
                if (!SkillUtils.isSkillToggled(target, (ManasSkill) ExtraSkills.GRAVITY_DOMINATION.get())) {
                    Vec3 vec3 = (new Vec3(this.getX() - target.getX(), this.getY() - target.getY(), this.getZ() - target.getZ())).normalize();
                    Changeable<Vec3> changeable = Changeable.of(target.getDeltaMovement().add(vec3.scale((double)pullMultiplier)));
                    if (!((TensuraEntityEvents.ForceMovementEvent)TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker()).move(target, this.getOwner(), this.getSkill(), changeable).isFalse()) {
                        target.setDeltaMovement(vec3);
                    }
                }
            }

        }
    }


    public Optional<SoundEvent> hitSound() {
        return Optional.of((SoundEvent)SoundEvents.WIND_CHARGE_BURST.value());
    }

    public void hitParticles(double x, double y, double z) {
    }

    public void flyingParticles() {
    }

    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController(this, "controller", 0, (event) -> {
            if (this.getAge() < 6) {
                return event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.magic_tornado.start"));
            } else {
                return this.getLife() - this.getAge() < 31 ? event.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation.magic_tornado.stop")) : event.setAndContinue(RawAnimation.begin().thenLoop("animation.magic_tornado.loop"));
            }
        }));
    }

    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }



    public float getPullForce() {
        return this.pullForce;
    }

    public void setPullForce(float pullForce) {
        this.pullForce = pullForce;
    }

    static {
        BURST_DELAY = SynchedEntityData.defineId(WindTornadoProjectile.class, EntityDataSerializers.INT);
        BURSTING = SynchedEntityData.defineId(WindTornadoProjectile.class, EntityDataSerializers.BOOLEAN);
    }

}


 */
package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.RuptureQuirk;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class RuptureArmEntity extends Projectile {

    public static final DeferredHolder<EntityType<?>, EntityType<RuptureArmEntity>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "rupture_arm"));

    private static final EntityDataAccessor<Boolean> DATA_RIGHT = SynchedEntityData.defineId(RuptureArmEntity.class, EntityDataSerializers.BOOLEAN);

    private @Nullable ManasSkillInstance instance;
    private float damage;
    private float radius;
    private int life;
    private int age;
    private boolean ruptured;

    public RuptureArmEntity(EntityType<? extends RuptureArmEntity> type, Level level) {
        super(type, level);
    }

    public static RuptureArmEntity create(Level level, LivingEntity owner, ManasSkillInstance instance, boolean right, float damage, float radius, int life) {
        RuptureArmEntity arm = new RuptureArmEntity(TYPE.get(), level);
        arm.setOwner(owner);
        arm.instance = instance;
        arm.entityData.set(DATA_RIGHT, right);
        arm.damage = damage;
        arm.radius = radius;
        arm.life = life;
        return arm;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<RuptureArmEntity>of(RuptureArmEntity::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(8)
                .updateInterval(1)
                .noSummon()
                .build(TYPE.getId().toString()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RIGHT, false);
    }

    public boolean isRightArm() {
        return this.entityData.get(DATA_RIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            this.hitTargetOrDeflectSelf(hit);
        }

        if (this.isRemoved()) {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.updateRotation();
        if (this.level().isClientSide) {
            this.level().addParticle(RuptureQuirk.BLOOD, this.getX(), this.getY(0.5D), this.getZ(), 0.0D, 0.0D, 0.0D);
            this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(0.5D), this.getZ(), 0.0D, 0.02D, 0.0D);
        } else if (++this.age > this.life) {
            rupture(this.position());
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && HitRules.canHit(this.getOwner(), target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        rupture(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        rupture(result.getLocation());
    }

    private void rupture(Vec3 pos) {
        if (this.ruptured || !(this.level() instanceof ServerLevel level)) {
            return;
        }

        this.ruptured = true;
        RuptureQuirk.ruptureArm(level, this.instance, this.getOwner() instanceof LivingEntity owner ? owner : null, pos, this.damage, this.radius);
        this.discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("RightArm", this.isRightArm());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_RIGHT, tag.getBoolean("RightArm"));
    }

}

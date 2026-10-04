package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class GravityClusterEntity extends Entity implements IEntityWithComplexSpawn {

    public static final DeferredHolder<EntityType<?>, EntityType<GravityClusterEntity>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "gravity_cluster"));

    private static final EntityDataAccessor<Float> DATA_SPIN = SynchedEntityData.defineId(GravityClusterEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_FORMED = SynchedEntityData.defineId(GravityClusterEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int MAX_IDLE_TICKS = 100;
    private static final double RENDER_DISTANCE = 512.0D;

    public record Part(BlockState state, Vec3 offset, Vec3 origin) {}

    private List<Part> parts = List.of();
    private long gatherStart;
    private float gatherSpeed;
    private int idleTicks;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public GravityClusterEntity(EntityType<? extends GravityClusterEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static GravityClusterEntity create(Level level, Vec3 pos, List<Part> parts) {
        GravityClusterEntity cluster = new GravityClusterEntity(TYPE.get(), level);
        cluster.parts = List.copyOf(parts);
        cluster.moveTo(pos.x, pos.y, pos.z);
        return cluster;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<GravityClusterEntity>of(GravityClusterEntity::new, MobCategory.MISC)
                .sized(1.0F, 1.0F)
                .clientTrackingRange(16)
                .updateInterval(1)
                .noSummon()
                .fireImmune()
                .build(TYPE.getId().toString()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SPIN, 0.0F);
        builder.define(DATA_FORMED, true);
    }

    public List<Part> getBlocks() {
        return this.parts;
    }

    public void setGather(long start, float speed) {
        this.gatherStart = start;
        this.gatherSpeed = speed;
        this.entityData.set(DATA_FORMED, false);
    }

    public void setFormed() {
        this.entityData.set(DATA_FORMED, true);
    }

    public boolean isFormed() {
        return this.entityData.get(DATA_FORMED);
    }

    public long getGatherStart() {
        return this.gatherStart;
    }

    public float getGatherSpeed() {
        return this.gatherSpeed;
    }

    public void setSpin(float degrees) {
        this.entityData.set(DATA_SPIN, degrees);
    }

    public float getSpin(float partialTick) {
        return this.entityData.get(DATA_SPIN) * (this.tickCount + partialTick);
    }

    public void keepAlive() {
        this.idleTicks = 0;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            if (this.lerpSteps > 0) {
                double step = 1.0D / this.lerpSteps;
                setPos(getX() + (this.lerpX - getX()) * step, getY() + (this.lerpY - getY()) * step, getZ() + (this.lerpZ - getZ()) * step);
                this.lerpSteps--;
            }
            return;
        }

        if (++this.idleTicks > MAX_IDLE_TICKS || getY() < level().getMinBuildHeight() - 64) {
            discard();
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : getZ();
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.parts.size());
        for (Part part : this.parts) {
            buffer.writeVarInt(Block.getId(part.state()));
            buffer.writeFloat((float) part.offset().x);
            buffer.writeFloat((float) part.offset().y);
            buffer.writeFloat((float) part.offset().z);
            buffer.writeDouble(part.origin().x);
            buffer.writeDouble(part.origin().y);
            buffer.writeDouble(part.origin().z);
        }

        buffer.writeLong(this.gatherStart);
        buffer.writeFloat(this.gatherSpeed);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Part> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockState state = Block.stateById(buffer.readVarInt());
            Vec3 offset = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            Vec3 origin = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            read.add(new Part(state, offset, origin));
        }

        this.parts = List.copyOf(read);
        this.gatherStart = buffer.readLong();
        this.gatherSpeed = buffer.readFloat();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

}

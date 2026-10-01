package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TelekinesisBlockEntity extends Entity {

    public static final DeferredHolder<EntityType<?>, EntityType<TelekinesisBlockEntity>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "telekinesis_block"));

    private static final EntityDataAccessor<BlockState> DATA_BLOCK = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOLD = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOT = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLOTS = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PHASE = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_PUNCHABLE = SynchedEntityData.defineId(TelekinesisBlockEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int HOLD_NONE = 0;
    public static final int HOLD_SHOULDER = 1;
    public static final int HOLD_ORBIT = 2;
    public static final int HOLD_BOULDER = 3;
    public static final int HOLD_WALL = 4;
    public static final int HOLD_ARMS = 5;

    public static final double ARMS_HEIGHT = 1.12D;
    public static final double ARMS_BACK = 0.065D;

    public static final double ORBIT_SPEED = 0.1D;
    private static final double ORBIT_HEIGHT = 0.55D;
    private static final double ORBIT_RADIUS = 1.3D;
    private static final double ORBIT_GAP = 0.25D;
    private static final double BOULDER_HEIGHT = 2.2D;
    private static final double SHOULDER_GAP = 0.45D;
    private static final double SHOULDER_FORWARD = 0.25D;
    private static final double SHOULDER_HEIGHT = 0.85D;
    private static final double WALL_DISTANCE = 1.6D;
    private static final int WALL_WIDTH = 3;
    private static final double GATHER_SPEED = 1.5D;
    private static final double HOLD_DELAY = 0.4D;
    private static final int MAX_IDLE_TICKS = 100;
    private static final double RENDER_DISTANCE = 256.0D;
    private static final double STILL_SPEED = 1.0E-7D;

    public static final List<Vec3> BOULDER_SHAPE = buildBoulderShape();

    public record Punch(LivingEntity attacker, Vec3 direction, float amount) {}

    private boolean shield;
    private @Nullable Punch punch;
    private boolean settled;
    private int idleTicks;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public TelekinesisBlockEntity(EntityType<? extends TelekinesisBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static TelekinesisBlockEntity create(Level level, BlockState state, float scale, Vec3 center) {
        TelekinesisBlockEntity block = new TelekinesisBlockEntity(TYPE.get(), level);
        block.setBlockState(state);
        block.setBlockScale(scale);
        block.moveTo(center.x, center.y - block.getBbHeight() * 0.5D, center.z);
        return block;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<TelekinesisBlockEntity>of(TelekinesisBlockEntity::new, MobCategory.MISC)
                .sized(0.98F, 0.98F)
                .clientTrackingRange(10)
                .updateInterval(1)
                .noSummon()
                .build(TYPE.getId().toString()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BLOCK, Blocks.STONE.defaultBlockState());
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_HOLD, HOLD_NONE);
        builder.define(DATA_SLOT, 0);
        builder.define(DATA_SLOTS, 1);
        builder.define(DATA_PHASE, 0.0F);
        builder.define(DATA_PUNCHABLE, false);
    }

    private static List<Vec3> buildBoulderShape() {
        List<Vec3> shape = new ArrayList<>(List.of(
                new Vec3(-0.5D, -0.5D, -0.5D), new Vec3(0.5D, -0.5D, 0.5D), new Vec3(0.5D, 0.5D, -0.5D), new Vec3(-0.5D, 0.5D, 0.5D),
                new Vec3(0.5D, -0.5D, -0.5D), new Vec3(-0.5D, 0.5D, -0.5D), new Vec3(-0.5D, -0.5D, 0.5D), new Vec3(0.5D, 0.5D, 0.5D),
                new Vec3(1.5D, -0.5D, 0.5D), new Vec3(-1.5D, 0.5D, -0.5D), new Vec3(0.5D, 0.5D, 1.5D), new Vec3(-0.5D, -0.5D, -1.5D)));

        List<Vec3> outer = new ArrayList<>();
        for (double x = -1.5D; x <= 1.5D; x++) {
            for (double y = -1.5D; y <= 1.5D; y++) {
                for (double z = -1.5D; z <= 1.5D; z++) {
                    Vec3 spot = new Vec3(x, y, z);
                    if (!shape.contains(spot)) {
                        outer.add(spot);
                    }
                }
            }
        }

        outer.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        shape.addAll(outer);
        return List.copyOf(shape);
    }

    public static Vec3 getHoldSpot(Vec3 ownerPos, float ownerYaw, float ownerWidth, float ownerHeight, int hold, int slot, int slots, double phase, double time, float blockWidth, float blockHeight) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, ownerYaw);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return switch (hold) {
            case HOLD_SHOULDER -> ownerPos
                    .add(right.scale(ownerWidth * 0.5D + SHOULDER_GAP))
                    .add(forward.scale(SHOULDER_FORWARD))
                    .add(0.0D, ownerHeight * SHOULDER_HEIGHT + Math.sin(time * 0.15D) * 0.05D - blockHeight * 0.5D, 0.0D);
            case HOLD_BOULDER -> ownerPos
                    .add(0.0D, ownerHeight + BOULDER_HEIGHT + Math.sin(time * 0.1D) * 0.1D - blockHeight * 0.5D, 0.0D)
                    .add(BOULDER_SHAPE.get(Mth.clamp(slot, 0, BOULDER_SHAPE.size() - 1)));
            case HOLD_WALL -> ownerPos
                    .add(forward.scale(WALL_DISTANCE))
                    .add(right.scale(slot % WALL_WIDTH - WALL_WIDTH / 2))
                    .add(0.0D, slot / WALL_WIDTH, 0.0D);
            case HOLD_ARMS -> ownerPos
                    .add(forward.scale(-ownerHeight * ARMS_BACK))
                    .add(0.0D, ownerHeight * ARMS_HEIGHT + 1.0D - blockHeight * 0.5D, 0.0D)
                    .add(BOULDER_SHAPE.get(Mth.clamp(slot, 0, BOULDER_SHAPE.size() - 1)));
            default -> {
                double angle = phase + time * ORBIT_SPEED + slot * (Math.PI * 2.0D / Math.max(1, slots));
                double radius = Math.max(ORBIT_RADIUS, slots * (blockWidth + ORBIT_GAP) / (Math.PI * 2.0D));
                double bob = Math.sin(time * 0.15D + slot) * 0.08D;
                yield ownerPos.add(Math.cos(angle) * radius, ownerHeight * ORBIT_HEIGHT - blockHeight * 0.5D + bob, Math.sin(angle) * radius);
            }
        };
    }

    public Vec3 getHoldSpot(LivingEntity owner, double time) {
        float yaw = getHold() == HOLD_ARMS ? owner.yBodyRot : owner.getYRot();
        return getHoldSpot(owner.position(), yaw, owner.getBbWidth(), owner.getBbHeight(), getHold(), this.entityData.get(DATA_SLOT), this.entityData.get(DATA_SLOTS), this.entityData.get(DATA_PHASE), time, getBbWidth(), getBbHeight());
    }

    public void hold(LivingEntity owner, int hold, int slot, int slots, double phase) {
        this.entityData.set(DATA_OWNER, owner.getId());
        this.entityData.set(DATA_HOLD, hold);
        this.entityData.set(DATA_SLOT, slot);
        this.entityData.set(DATA_SLOTS, slots);
        this.entityData.set(DATA_PHASE, (float) phase);
    }

    public void release() {
        this.entityData.set(DATA_HOLD, HOLD_NONE);
    }

    public int getHold() {
        return this.entityData.get(DATA_HOLD);
    }

    public int getOwnerId() {
        return this.entityData.get(DATA_OWNER);
    }

    public void keepAlive() {
        this.idleTicks = 0;
    }

    private boolean isDelayed() {
        int hold = getHold();
        return hold == HOLD_SHOULDER || hold == HOLD_BOULDER;
    }

    public @Nullable LivingEntity getHoldOwner() {
        return getHold() != HOLD_NONE && level().getEntity(this.entityData.get(DATA_OWNER)) instanceof LivingEntity owner ? owner : null;
    }

    public Vec3 getHoldRenderOffset(float partialTick) {
        LivingEntity owner = getHoldOwner();
        if (!this.settled || owner == null || isDelayed()) {
            return Vec3.ZERO;
        }

        float yaw = getHold() == HOLD_ARMS ? Mth.rotLerp(partialTick, owner.yBodyRotO, owner.yBodyRot) : Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot());
        double time = level().getGameTime() - 1.0D + partialTick;
        Vec3 spot = getHoldSpot(owner.getPosition(partialTick), yaw, owner.getBbWidth(), owner.getBbHeight(), getHold(), this.entityData.get(DATA_SLOT), this.entityData.get(DATA_SLOTS), this.entityData.get(DATA_PHASE), time, getBbWidth(), getBbHeight());
        return spot.subtract(getPosition(partialTick));
    }

    public BlockState getBlockState() {
        return this.entityData.get(DATA_BLOCK);
    }

    public void setBlockState(BlockState state) {
        this.entityData.set(DATA_BLOCK, state);
    }

    public float getBlockScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setBlockScale(float scale) {
        this.entityData.set(DATA_SCALE, scale);
        refreshDimensions();
    }

    public void setShield(boolean shield) {
        this.shield = shield;
    }

    public void setPunchable(boolean punchable) {
        this.entityData.set(DATA_PUNCHABLE, punchable);
    }

    public @Nullable Punch takePunch() {
        Punch taken = this.punch;
        this.punch = null;
        return taken;
    }

    @Override
    public boolean isPickable() {
        return (this.shield || this.entityData.get(DATA_PUNCHABLE)) && !isRemoved();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < RENDER_DISTANCE * RENDER_DISTANCE;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SCALE.equals(accessor)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getBlockScale());
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            LivingEntity owner = getHoldOwner();
            if (owner != null) {
                Vec3 offset = getHoldSpot(owner, level().getGameTime()).subtract(position());
                if (isDelayed()) {
                    this.settled = false;
                    offset = offset.scale(HOLD_DELAY);
                    if (offset.length() > GATHER_SPEED) {
                        offset = offset.normalize().scale(GATHER_SPEED);
                    }
                } else if (this.settled || offset.lengthSqr() < 0.01D) {
                    this.settled = true;
                } else {
                    offset = offset.scale(0.5D);
                    if (offset.length() > GATHER_SPEED) {
                        offset = offset.normalize().scale(GATHER_SPEED);
                    }
                }

                setPos(position().add(offset));
                this.lerpSteps = 0;
                return;
            }

            this.settled = false;
            if (this.lerpSteps > 0) {
                double step = 1.0D / this.lerpSteps;
                setPos(getX() + (this.lerpX - getX()) * step, getY() + (this.lerpY - getY()) * step, getZ() + (this.lerpZ - getZ()) * step);
                this.lerpSteps--;
            }

            return;
        }

        if (getY() < level().getMinBuildHeight() - 64) {
            discard();
            return;
        }

        if (++this.idleTicks > MAX_IDLE_TICKS) {
            level().levelEvent(2001, blockPosition(), Block.getId(getBlockState()));
            discard();
            return;
        }

        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() > STILL_SPEED) {
            move(MoverType.SELF, motion);
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
    public boolean hurt(DamageSource source, float amount) {
        if (!this.entityData.get(DATA_PUNCHABLE) || level().isClientSide || isRemoved() || !(source.getEntity() instanceof LivingEntity attacker)) {
            return false;
        }

        Entity direct = source.getDirectEntity();
        Vec3 direction = direct == null || direct == attacker ? attacker.getLookAngle() : direct.getDeltaMovement().normalize();
        this.punch = new Punch(attacker, direction, amount);
        return true;
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
        setBlockState(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
        setBlockScale(tag.contains("Scale") ? tag.getFloat("Scale") : 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(getBlockState()));
        tag.putFloat("Scale", getBlockScale());
    }

}

package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TornadoEntity extends Entity implements GeoEntity {

    public static final DeferredHolder<EntityType<?>, EntityType<TornadoEntity>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tornado"));

    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    private static final SoundSource ABILITY_SOUND = Arrays.stream(SoundSource.values()).filter(source -> source.getName().equals("ability")).findFirst().orElse(SoundSource.PLAYERS);
    private static final RawAnimation START = RawAnimation.begin().thenPlayAndHold("animation.death_tornado.start");
    private static final RawAnimation LOOP = RawAnimation.begin().thenLoop("animation.death_tornado.loop");
    private static final RawAnimation STOP = RawAnimation.begin().thenPlayAndHold("animation.death_tornado.stop");

    private static final int FORM_TICKS = 30;
    private static final int FADE_TICKS = 30;
    private static final int ANIMATION_TICKS = 40;
    private static final int DAMAGE_INTERVAL = 20;
    private static final double MODEL_HEIGHT = 28.0D;
    private static final double MODEL_RADIUS = 8.5D;
    private static final double CORE_LIFT = 0.12D;
    private static final double EDGE_LIFT = 0.04D;
    private static final double SWIRL = 0.8D;
    private static final double WOBBLE = 0.08D;
    private static final int GROUND_SEARCH = 6;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable LivingEntity owner;
    private @Nullable ManasSkillInstance instance;
    private int mode;
    private Vec3 drift = Vec3.ZERO;
    private float damage;
    private double pullRadius;
    private double coreRadius;
    private double pull;
    private double wobblePhase;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static TornadoEntity create(Level level, LivingEntity owner, ManasSkillInstance instance, int mode, Vec3 pos, Vec3 drift, int life, float size) {
        TornadoEntity tornado = new TornadoEntity(TYPE.get(), level);
        tornado.owner = owner;
        tornado.instance = instance;
        tornado.mode = mode;
        tornado.drift = drift;
        tornado.wobblePhase = level.random.nextDouble() * Math.PI * 2.0D;
        tornado.entityData.set(DATA_LIFE, life);
        tornado.entityData.set(DATA_SIZE, size);
        tornado.moveTo(pos.x, pos.y, pos.z);
        return tornado;
    }

    public void setHarm(float damage, double pullRadius, double coreRadius, double pull) {
        this.damage = damage;
        this.pullRadius = pullRadius;
        this.coreRadius = coreRadius;
        this.pull = pull;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<TornadoEntity>of(TornadoEntity::new, MobCategory.MISC)
                .sized(2.0F, 4.0F)
                .clientTrackingRange(10)
                .updateInterval(2)
                .noSummon()
                .fireImmune()
                .build(TYPE.getId().toString()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE, 200);
        builder.define(DATA_SIZE, 1.0F);
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public float getVisualSize() {
        return this.entityData.get(DATA_SIZE);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double radius = MODEL_RADIUS * getVisualSize();
        return new AABB(getX() - radius, getY(), getZ() - radius, getX() + radius, getY() + MODEL_HEIGHT * getVisualSize(), getZ() + radius);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 128.0D * getViewScale();
        return distance < range * range;
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            return;
        }

        if (this.owner == null || this.instance == null || !this.owner.isAlive() || this.tickCount > getLife() || !(level() instanceof ServerLevel level)) {
            discard();
            return;
        }

        drift(level);
        if (this.tickCount >= FORM_TICKS && getLife() - this.tickCount >= FADE_TICKS) {
            swirl(level);
        }

        if (this.tickCount % 4 == 0) {
            double radius = this.coreRadius;
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 6, radius * 0.6D, 0.1D, radius * 0.6D, 0.08D);
        }

        if (this.tickCount % 20 == 0) {
            level.playSound(null, getX(), getY(), getZ(), TensuraSoundEvents.WIND_BLOW.get(), ABILITY_SOUND, 3.0F, 0.8F + this.random.nextFloat() * 0.2F);
        }
    }

    private void drift(ServerLevel level) {
        double wobble = Math.sin(this.tickCount * 0.15D + this.wobblePhase) * WOBBLE;
        Vec3 side = new Vec3(-this.drift.z, 0.0D, this.drift.x).normalize().scale(wobble);
        Vec3 next = position().add(this.drift).add(side);
        BlockPos column = BlockPos.containing(next.x, getY() + GROUND_SEARCH, next.z);
        double ground = next.y;
        for (int down = 0; down <= GROUND_SEARCH * 2; down++) {
            BlockPos pos = column.below(down);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                ground = pos.getY() + 1.0D;
                break;
            }
        }

        setPos(next.x, ground, next.z);
    }

    private void swirl(ServerLevel level) {
        Vec3 center = position();
        double height = MODEL_HEIGHT * getVisualSize();
        AABB area = new AABB(center.x - this.pullRadius, center.y - 2.0D, center.z - this.pullRadius, center.x + this.pullRadius, center.y + height, center.z + this.pullRadius);
        boolean hurting = this.tickCount % DAMAGE_INTERVAL == 0;
        for (Entity entity : level.getEntities(this, area, this::canSwirl)) {
            Vec3 toward = new Vec3(center.x - entity.getX(), 0.0D, center.z - entity.getZ());
            double distance = toward.length();
            if (distance > this.pullRadius) {
                continue;
            }

            boolean core = distance <= this.coreRadius + entity.getBbWidth() * 0.5D;
            if (!entity.getType().is(TensuraEntityTags.NO_FORCED_MOVE)) {
                double strength = this.pull * Mth.clamp(1.0D - distance / this.pullRadius, 0.25D, 1.0D);
                Vec3 inward = distance > 1.0E-3D ? toward.scale(1.0D / distance) : Vec3.ZERO;
                Vec3 around = new Vec3(-inward.z, 0.0D, inward.x);
                double lift = entity.getY() - center.y > height * 0.6D ? 0.0D : core ? CORE_LIFT : EDGE_LIFT;
                Vec3 motion = entity.getDeltaMovement().add(inward.scale(strength)).add(around.scale(strength * SWIRL)).add(0.0D, lift, 0.0D);
                Changeable<Vec3> moved = Changeable.of(motion);
                if (!TensuraEntityEvents.FORCE_MOVEMENT_EVENT.invoker().move(entity, this.owner, this.instance, moved).isFalse()) {
                    entity.setDeltaMovement(moved.get());
                    entity.hurtMarked = true;
                    entity.hasImpulse = true;
                    entity.resetFallDistance();
                }
            }

            if (hurting && core && entity instanceof LivingEntity living) {
                DamageSource source = ((Skill) this.instance.getSkill()).createSource(this.instance, this.owner, TensuraDamageTypes.WIND_ELEMENTAL, this.mode);
                ((TensuraDamageSource) source).tensura$setElement(Element.WIND);
                living.invulnerableTime = 0;
                living.hurt(source, this.damage);
            }
        }
    }

    private boolean canSwirl(Entity entity) {
        if (entity == this.owner || entity instanceof TornadoEntity || entity.isSpectator() || !(entity instanceof LivingEntity || entity instanceof ItemEntity)) {
            return false;
        }

        if (entity instanceof Player player && (player.isCreative() || this.owner != null && player.isAlliedTo(this.owner))) {
            return false;
        }

        return this.owner == null || !this.owner.isAlliedTo(entity);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tornado", 3, state -> {
            if (this.tickCount < ANIMATION_TICKS) {
                return state.setAndContinue(START);
            }

            return state.setAndContinue(getLife() - this.tickCount < ANIMATION_TICKS ? STOP : LOOP);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
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

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.datafixers.util.Pair;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Extra offhand slots
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class MultiArms {

    public static final int SLOTS = 4;

    // Slot positions in the survival inventory and the creative inventory
    private static final int[][] INVENTORY_POSITIONS = {{77, 44}, {77, 26}, {77, 8}, {152, 62}};
    private static final int[][] CREATIVE_POSITIONS = {{15, 6}, {15, 33}, {127, 6}, {127, 33}};

    private static final String HELD_BY_TAG = "tracadamia_quad_held_by";
    private static final String HELD_UNTIL_TAG = "tracadamia_quad_held_until";
    private static final double HOLD_BREAK_DISTANCE = 64.0D;

    public static final int HOLD_BODY = 0;
    public static final int HOLD_LOOK = 1;

    private record Hold(LivingEntity holder, Vec3 offset, int frame, long time, long start, boolean ownRules) {}

    private static final QuirkSkillsConfig.Grabbing GRAB_CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Grabbing;

    public record ClientHold(int holderId, Vec3 offset, int frame) {}

    private static final Map<Integer, Hold> HOLDS = new HashMap<>();
    private static final Map<Integer, ClientHold> CLIENT_HOLDS = new HashMap<>();

    private static final ResourceLocation OFFHANDS_ID = ResourceLocation.fromNamespaceAndPath("tracadamia", "extra_offhands");
    private static final AttachmentType<OffhandStorage> OFFHANDS = AttachmentType.serializable(OffhandStorage::new).copyOnDeath().build();

    private static final List<PendingImpact> PENDING_IMPACTS = new ArrayList<>();
    private static boolean armHitting = false;

    private record PendingImpact(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, long hitTime, int delay, String swingTag) {}

    private MultiArms() {
    }

    @SubscribeEvent
    public static void registerOffhands(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OFFHANDS_ID, () -> OFFHANDS);
    }

    // Extra offhands the entity's arms can have
    public static int getSlotCount(LivingEntity entity) {
        int slots = Math.max(QuadArmsQuirk.getArmSlots(entity), DupliArmsQuirk.getArmSlots(entity));
        return Math.min(SLOTS, slots);
    }

    // Other arm hits
    public static boolean isArmHitting() {
        return armHitting;
    }

    public static boolean hasPendingImpact(ManasSkillInstance instance, LivingEntity owner) {
        long time = owner.level().getGameTime();
        for (PendingImpact impact : PENDING_IMPACTS) {
            if (impact.owner() == owner && impact.instance().getSkill() == instance.getSkill() && impact.hitTime() == time) {
                return true;
            }
        }

        return false;
    }

    public static void queueImpact(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int delay, String swingTag) {
        PENDING_IMPACTS.add(new PendingImpact(instance, owner, target, damage, owner.level().getGameTime(), delay, swingTag));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!HOLDS.isEmpty()) {
            releaseStaleHolds();
        }

        if (!FLUNG.isEmpty()) {
            FLUNG.removeIf(MultiArms::tickFlung);
        }

        if (PENDING_IMPACTS.isEmpty()) {
            return;
        }

        List<PendingImpact> ready = PENDING_IMPACTS.stream()
                .filter(impact -> !impact.owner().isAlive() || impact.owner().level().getGameTime() - impact.hitTime() >= impact.delay())
                .toList();

        PENDING_IMPACTS.removeAll(ready);
        ready.forEach(MultiArms::hitWithArm);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_IMPACTS.clear();
        HOLDS.clear();
        FLUNG.clear();
    }

    private static void hitWithArm(PendingImpact impact) {
        LivingEntity owner = impact.owner();
        LivingEntity target = impact.target();
        if (!owner.isAlive() || !target.isAlive() || owner.isRemoved() || target.isRemoved() || owner.level() != target.level()) {
            return;
        }

        DamageSource source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);

        // Swing for the arm that hits
        impact.instance().getOrCreateTag().putLong(impact.swingTag(), owner.level().getGameTime());
        impact.instance().markDirty();
        SkillAPI.getSkillsFrom(owner).markDirty();

        // Keep the first hit's iframes
        int invulnerableTime = target.invulnerableTime;
        armHitting = true;
        try {
            target.invulnerableTime = 0;
            if (target.hurt(source, impact.damage()) && owner.getRandom().nextBoolean()) {
                impact.instance().addMasteryPoint(owner);
            }
        } finally {
            armHitting = false;
            target.invulnerableTime = invulnerableTime;
        }
    }

    public static long getSwingTime(ManasSkillInstance instance, String key) {
        CompoundTag tag = instance.getTag();
        return tag == null || !tag.contains(key) ? Long.MIN_VALUE : tag.getLong(key);
    }

    public static double getArmDamage(LivingEntity owner, ItemStack held) {
        AttributeInstance attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 0.0D;
        }

        Set<ResourceLocation> heldWeapon = getAttackModifiers(owner.getMainHandItem()).stream().map(AttributeModifier::id).collect(Collectors.toSet());
        List<AttributeModifier> modifiers = new ArrayList<>(attack.getModifiers().stream().filter(modifier -> !heldWeapon.contains(modifier.id())).toList());
        modifiers.addAll(getAttackModifiers(held));

        double base = attack.getBaseValue();
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                base += modifier.amount();
            }
        }

        double total = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                total += base * modifier.amount();
            }
        }

        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                total *= 1.0D + modifier.amount();
            }
        }

        return attack.getAttribute().value().sanitizeValue(total);
    }

    private static List<AttributeModifier> getAttackModifiers(ItemStack stack) {
        List<AttributeModifier> modifiers = new ArrayList<>();
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                modifiers.add(modifier);
            }
        });

        return modifiers;
    }

    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && !getAttackModifiers(stack).isEmpty() && !stack.is(ItemTags.PICKAXES) && !stack.is(ItemTags.SHOVELS) && !stack.is(ItemTags.HOES);
    }


    private static final double TARGET_RAY_OFFSET = 1.0D;
    private static final double SIZE_TOLERANCE = 1.25D;
    private static final double NEARBY_AIM = 0.9D;

    public static final double THROW_GRAVITY = 0.08D;

    private static final class Flung {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final LivingEntity target;
        private final float damage;
        private final int mode;
        private final boolean ground;
        private final long start;
        private final long until;
        private @Nullable Vec3 velocity;

        private Flung(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode, boolean ground, long start, long until) {
            this.instance = instance;
            this.owner = owner;
            this.target = target;
            this.damage = damage;
            this.mode = mode;
            this.ground = ground;
            this.start = start;
            this.until = until;
        }
    }

    private static final List<Flung> FLUNG = new ArrayList<>();

    public static @Nullable LivingEntity getTarget(LivingEntity entity, double range) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, range + TARGET_RAY_OFFSET, false, true);
        return target == null || target == entity || !target.isAlive() || target.distanceTo(entity) > range + target.getBbWidth() ? null : target;
    }

    public static @Nullable LivingEntity getNearbyTarget(LivingEntity entity, double range) {
        LivingEntity target = getTarget(entity, range);
        if (target != null) {
            return target;
        }

        Vec3 eye = entity.getEyePosition();
//        Vec3 look = entity.getLookAngle();
        Vec3 look = Vec3.directionFromRotation(0.0F, entity.getYHeadRot()); // look

        double best = NEARBY_AIM;

        for (LivingEntity candidate : entity.level().getEntitiesOfClass
                (LivingEntity.class, entity.getBoundingBox().inflate(range + 1.0D), candidate -> candidate != entity && candidate.isAlive() && !candidate.isSpectator()))

        {
            if (candidate.distanceTo(entity) > range + candidate.getBbWidth() + entity.getBbWidth() * 0.5D) {
                continue;
            }

//            double aim = toward.normalize().dot(look);
//            if (aim > best && entity.hasLineOfSight(candidate)) {
//                best = aim;
//                target = candidate;
//            }

            Vec3 toward = candidate.position().subtract(entity.position()); // fixes large sizes breaking grabs
            Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);

            double aim = flat.lengthSqr() < 1.0E-4D ? 1.0D : flat.normalize().dot(look); // test
            if (aim > best && entity.hasLineOfSight(candidate))
            {
                best = aim;
                target = candidate;
            }
        }

        return target;
    }


    public static boolean canGrab(LivingEntity target) {
        return !target.getType().is(Tags.EntityTypes.BOSSES) && !target.getType().is(TensuraEntityTags.HERO_BOSS);
    }

    public static void fling(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode, boolean ground, int ticks) {
        startFling(instance, owner, target, damage, mode, ground, ticks);
    }

    public static void throwAt(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, Vec3 velocity, float damage, int mode, int ticks) {
        startFling(instance, owner, target, damage, mode, true, ticks).velocity = velocity;
        flyThrown(target, velocity);
    }

    private static Flung startFling(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, int mode, boolean ground, int ticks) {
        long time = target.level().getGameTime();
        Flung flung = new Flung(instance, owner, target, damage, mode, ground, time, time + ticks);
        FLUNG.removeIf(other -> other.target == target);
        FLUNG.add(flung);
        return flung;
    }

    private static boolean isWeightless(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && ZeroGravityQuirk.isFloating(living);
    }

    private static void flyThrown(LivingEntity target, Vec3 velocity) {
        target.setDeltaMovement(velocity.x, isWeightless(target) ? velocity.y : velocity.y - THROW_GRAVITY * 0.5D, velocity.z);
        target.resetFallDistance();
        target.hurtMarked = true;
    }

    private static boolean tickFlung(Flung flung) {
        LivingEntity target = flung.target;
        if (!target.isAlive() || !(target.level() instanceof ServerLevel level) || level.getGameTime() > flung.until) {
            return true;
        }

        boolean landed = flung.ground && level.getGameTime() - flung.start > 2L && target.onGround();
        boolean ceiling = flung.velocity != null && target.verticalCollision && !target.onGround();
        if (!target.horizontalCollision && !landed && !ceiling) {
            if (flung.velocity != null) {
                if (!isWeightless(target)) {
                    flung.velocity = flung.velocity.subtract(0.0D, THROW_GRAVITY, 0.0D);
                }
                flyThrown(target, flung.velocity);
            }
            return false;
        }

        target.invulnerableTime = 0;
        target.hurt(((Skill) flung.instance.getSkill()).createSource(flung.instance, flung.owner, DamageTypes.FLY_INTO_WALL, flung.mode), flung.damage);
        BlockState state = landed ? target.getBlockStateOn() : getWall(level, target);
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), target.getX(), target.getY(0.5D), target.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.15D);
        }

        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.2F, 0.7F);
        return true;
    }

    private static BlockState getWall(ServerLevel level, LivingEntity target) {
        BlockPos middle = BlockPos.containing(target.getX(), target.getY(0.5D), target.getZ());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState state = level.getBlockState(middle.relative(direction));
            if (!state.getCollisionShape(level, middle.relative(direction)).isEmpty()) {
                return state;
            }
        }

        return level.getBlockState(middle);
    }

    public static boolean isNoLarger(LivingEntity target, LivingEntity entity) {
        double targetSize = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
        double size = entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight();
        return targetSize <= size * SIZE_TOLERANCE;
    }

    // Same front half a shield covers
    public static boolean isInFront(LivingEntity entity, Vec3 from) {
        Vec3 view = Vec3.directionFromRotation(0.0F, entity.getYHeadRot());
        Vec3 toward = from.vectorTo(entity.position());
        return new Vec3(toward.x, 0.0D, toward.z).normalize().dot(view) < 0.0D;
    }

    public static Vec3 getThrowVelocity(LivingEntity thrower, @Nullable Entity thrown, Vec3 from, double range, double speed) {
        Vec3 offset = getThrowAim(thrower, thrown, range).subtract(from);
        double distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        if (distance < 0.1D) {
            return thrower.getLookAngle().scale(speed);
        }

        if (isWeightless(thrown)) {
            return offset.normalize().scale(speed);
        }

        double height = offset.y;
        double reach = THROW_GRAVITY * (height + Math.sqrt(height * height + distance * distance));
        double square = Math.max(speed * speed, reach);
        double root = Math.sqrt(Math.max(0.0D, square * square - THROW_GRAVITY * (THROW_GRAVITY * distance * distance + 2.0D * height * square)));
        double angle = Math.atan((square - root) / (THROW_GRAVITY * distance));
        double flat = Math.cos(angle) * Math.sqrt(square) / distance;
        return new Vec3(offset.x * flat, Math.sin(angle) * Math.sqrt(square), offset.z * flat);
    }

    private static Vec3 getThrowAim(LivingEntity thrower, @Nullable Entity thrown, double range) {
        Vec3 eye = thrower.getEyePosition();
        Vec3 end = eye.add(thrower.getLookAngle().scale(range));
        BlockHitResult block = thrower.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, thrower));
        Vec3 reach = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(thrower, eye, reach, thrower.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0D),
                candidate -> candidate != thrown && candidate instanceof LivingEntity && !candidate.isSpectator() && candidate.isPickable(), eye.distanceToSqr(reach));
        return entity != null ? entity.getEntity().getBoundingBox().getCenter() : reach;
    }

    public static Vec3 getLaunchVelocity(Vec3 direction, double distance) {
        Vec3 unit = direction.normalize();
        double low = 0.0D;
        double high = 12.0D;
        for (int i = 0; i < 24; i++) {
            double speed = (low + high) * 0.5D;
            if (getFlightDistance(unit.scale(speed)) < distance) {
                low = speed;
            } else {
                high = speed;
            }
        }

        return unit.scale(high);
    }

    private static double getFlightDistance(Vec3 velocity) {
        Vec3 pos = Vec3.ZERO;
        Vec3 motion = velocity;
        double farthest = 0.0D;
        for (int tick = 0; tick < 100; tick++) {
            pos = pos.add(motion);
            if (pos.y < 0.0D) {
                break;
            }

            farthest = Math.max(farthest, pos.length());
            motion = new Vec3(motion.x * 0.91D, (motion.y - 0.08D) * 0.98D, motion.z * 0.91D);
        }

        return farthest;
    }

    // Holding

    public static Vec3 getHoldPos(LivingEntity holder, Vec3 offset, int frame, float partialTick) {
        if (frame == HOLD_LOOK) {
            Vec3 look = holder.getViewVector(partialTick);
            Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
            return holder.getEyePosition(partialTick).add(look.scale(offset.z)).add(right.scale(offset.x)).add(0.0D, offset.y, 0.0D);
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, Mth.rotLerp(partialTick, holder.yBodyRotO, holder.yBodyRot));
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return holder.getPosition(partialTick).add(forward.scale(offset.z)).add(right.scale(offset.x)).add(0.0D, offset.y, 0.0D);
    }

    public static void holdAt(LivingEntity holder, LivingEntity target, Vec3 offset, int frame) {
        holdAt(holder, target, offset, frame, false);
    }

    public static void holdAt(LivingEntity holder, LivingEntity target, Vec3 offset, int frame, boolean ownRules) {
        Vec3 pos = getHoldPos(holder, offset, frame, 1.0F);
        if (target.position().distanceToSqr(pos) > 0.0025D) {
            target.teleportTo(pos.x, pos.y, pos.z);
        }

        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
        target.getPersistentData().putUUID(HELD_BY_TAG, holder.getUUID());
        target.getPersistentData().putLong(HELD_UNTIL_TAG, target.level().getGameTime() + 2L);

        Hold last = HOLDS.get(target.getId());
        if (last == null || last.holder() != holder || !last.offset().equals(offset) || last.frame() != frame) {
            syncHold(holder, target, new HoldPayload(target.getId(), holder.getId(), offset, frame));
        }

        long time = target.level().getGameTime();
        HOLDS.put(target.getId(), new Hold(holder, offset, frame, time, last != null && last.holder() == holder ? last.start() : time, ownRules));
    }

    private static void syncHold(LivingEntity holder, LivingEntity target, HoldPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(holder, payload);
    }

    private static void releaseStaleHolds() {
        HOLDS.entrySet().removeIf(entry -> {
            Hold hold = entry.getValue();
            if (hold.holder().level().getGameTime() - hold.time() <= 1L && !hold.holder().isRemoved()) {
                return false;
            }

            if (hold.holder().level().getEntity(entry.getKey()) instanceof LivingEntity target) {
                syncHold(hold.holder(), target, new HoldPayload(entry.getKey(), -1, Vec3.ZERO, HOLD_BODY));
            }
            return true;
        });
    }

    public static boolean isHoldPair(Entity first, Entity second) {
        return isHolding(first, second) || isHolding(second, first);
    }

    private static boolean isHolding(Entity holder, Entity target) {
        if (target.level().isClientSide) {
            ClientHold hold = CLIENT_HOLDS.get(target.getId());
            return hold != null && hold.holderId() == holder.getId();
        }

        Hold hold = HOLDS.get(target.getId());
        return hold != null && hold.holder() == holder;
    }

    // used by MultiArmsClient
    public static Map<Integer, ClientHold> getClientHolds() {
        return CLIENT_HOLDS;
    }

    public record HoldPayload(int targetId, int holderId, Vec3 offset, int frame) implements CustomPacketPayload {
        public static final Type<HoldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "arms_hold"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> OFFSET_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Vec3::x,
                ByteBufCodecs.DOUBLE, Vec3::y,
                ByteBufCodecs.DOUBLE, Vec3::z,
                Vec3::new
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, HoldPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HoldPayload::targetId,
                ByteBufCodecs.VAR_INT, HoldPayload::holderId,
                OFFSET_CODEC, HoldPayload::offset,
                ByteBufCodecs.VAR_INT, HoldPayload::frame,
                HoldPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    //HELD TARGET MY BELOVED
    public static @Nullable LivingEntity getHeldTarget(ManasSkillInstance instance, LivingEntity entity, String key) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(key) || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }

        double scale = Math.max(1.0D, entity.getScale());

        if (!(level.getEntity(tag.getUUID(key)) instanceof LivingEntity target) || !target.isAlive() || target.distanceToSqr(entity) > HOLD_BREAK_DISTANCE * scale * scale) { // scale fixes distance check
            return null;
        }

        Hold hold = HOLDS.get(target.getId());
        if (hold != null && hold.holder() == entity && !hold.ownRules() && (target instanceof Player && target.isShiftKeyDown()
                || isHostile(target, entity) && level.getGameTime() - hold.start() > GRAB_CONFIG.hostileHoldSeconds * 20L)) {
            return null;
        }

        return target;
    }

    private static boolean isHostile(LivingEntity target, LivingEntity holder) {
        return target instanceof Enemy || target instanceof NeutralMob neutral && neutral.isAngryAt(holder) || target instanceof Mob mob && mob.getTarget() == holder;
    }

    public static void clearHeld(ManasSkillInstance instance, LivingEntity entity, String key) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(key)) {
            return;
        }

        LivingEntity target = getHeldTarget(instance, entity, key);
        if (target != null) {
            target.getPersistentData().remove(HELD_BY_TAG);
            target.getPersistentData().remove(HELD_UNTIL_TAG);
        }

        tag.remove(key);
        instance.markDirty();
    }

    private static boolean isHeldBy(LivingEntity target, LivingEntity holder) {
        CompoundTag data = target.getPersistentData();
        return data.hasUUID(HELD_BY_TAG) && data.getUUID(HELD_BY_TAG).equals(holder.getUUID()) && data.getLong(HELD_UNTIL_TAG) >= target.level().getGameTime();
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isHeldBy(attacker, event.getEntity())) {
            event.setCanceled(true);
        }

        if (event.getSource().is(DamageTypes.IN_WALL) && HOLDS.containsKey(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    // Extra offhands

    public static class OffhandStorage implements INBTSerializable<CompoundTag> {
        private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        private final ItemStack[] synced = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            return ContainerHelper.saveAllItems(new CompoundTag(), this.items, provider);
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            this.items.clear();
            ContainerHelper.loadAllItems(tag, this.items, provider);
        }
    }

    public static ItemStack getExtraOffhand(LivingEntity entity, int slot) {
        return slot >= 0 && slot < SLOTS && entity.hasData(OFFHANDS) ? entity.getData(OFFHANDS).items.get(slot) : ItemStack.EMPTY;
    }

    public static void setExtraOffhand(LivingEntity entity, int slot, ItemStack stack) {
        entity.getData(OFFHANDS).items.set(slot, stack);
    }

    // Items in slots the arms can't hold go back to the inventory
    private static void returnInactiveOffhands(Player player) {
        for (int slot = getSlotCount(player); slot < SLOTS; slot++) {
            ItemStack stack = getExtraOffhand(player, slot);
            if (!stack.isEmpty()) {
                setExtraOffhand(player, slot, ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    // used by InventoryMenuMixin
    public static List<Slot> createSlots(Player owner) {
        OffhandContainer container = new OffhandContainer(owner);
        List<Slot> slots = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.add(new OffhandSlot(container, owner, slot, INVENTORY_POSITIONS[slot][0], INVENTORY_POSITIONS[slot][1]));
        }

        return slots;
    }

    // used by CreativeInventoryMixin
    public static int getCreativeX(Slot slot, int x) {
        return slot instanceof OffhandSlot ? CREATIVE_POSITIONS[slot.getContainerSlot()][0] : x;
    }

    public static int getCreativeY(Slot slot, int y) {
        return slot instanceof OffhandSlot ? CREATIVE_POSITIONS[slot.getContainerSlot()][1] : y;
    }

    // used by ServerGamePacketListenerMixin
    public static boolean handleCreativeSlot(ServerPlayer player, int slotNum, ItemStack stack) {
        if (!player.gameMode.isCreative() || slotNum < 0 || slotNum >= player.inventoryMenu.slots.size()) {
            return false;
        }

        if (!(player.inventoryMenu.getSlot(slotNum) instanceof OffhandSlot slot)) {
            return false;
        }

        if (slot.isActive() && (stack.isEmpty() || stack.getCount() <= stack.getMaxStackSize())) {
            slot.setByPlayer(stack);
            player.inventoryMenu.broadcastChanges();
        }

        return true;
    }

    // Reads the player's storage
    public static class OffhandContainer implements Container {
        private final Player player;

        public OffhandContainer(Player player) {
            this.player = player;
        }

        private NonNullList<ItemStack> items() {
            return this.player.getData(OFFHANDS).items;
        }

        @Override
        public int getContainerSize() {
            return SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return this.items().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.items().get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ContainerHelper.removeItem(this.items(), slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(this.items(), slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.items().set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            this.items().clear();
        }
    }

    public static class OffhandSlot extends Slot {
        private final Player owner;

        public OffhandSlot(Container container, Player owner, int slot, int x, int y) {
            super(container, slot, x, y);
            this.owner = owner;
        }

        @Override
        public boolean isActive() {
            return getContainerSlot() < getSlotCount(this.owner);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.isActive();
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
        }
    }

    // Drop the items on death
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(OFFHANDS) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getExtraOffhand(player, slot);
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3D, player.getZ(), stack.copy());
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                setExtraOffhand(player, slot, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.hasData(OFFHANDS)) {
            return;
        }

        if (player.isAlive()) {
            returnInactiveOffhands(player);
        }

        syncExtraOffhands(player);
    }

    // Send the items to nearby players so they show in the extra hands
    private static void syncExtraOffhands(Player player) {
        OffhandStorage storage = player.getData(OFFHANDS);
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!ItemStack.matches(storage.items.get(slot), storage.synced[slot])) {
                storage.synced[slot] = storage.items.get(slot).copy();
                changed = true;
            }
        }

        if (changed) {
            PacketDistributor.sendToPlayersTrackingEntity(player, OffhandSyncPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && target.hasData(OFFHANDS) && event.getEntity() instanceof ServerPlayer tracker) {
            PacketDistributor.sendToPlayer(tracker, OffhandSyncPayload.of(target));
        }
    }

    public record OffhandSyncPayload(int entityId, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<OffhandSyncPayload> TYPE = new Type<>(OFFHANDS_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, OffhandSyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OffhandSyncPayload::entityId,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, OffhandSyncPayload::items,
                OffhandSyncPayload::new
        );

        public static OffhandSyncPayload of(Player player) {
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < SLOTS; slot++) {
                items.add(getExtraOffhand(player, slot).copy());
            }

            return new OffhandSyncPayload(player.getId(), items);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(HoldPayload.TYPE, HoldPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.holderId() < 0) {
                CLIENT_HOLDS.remove(payload.targetId());
            } else {
                CLIENT_HOLDS.put(payload.targetId(), new ClientHold(payload.holderId(), payload.offset(), payload.frame()));
            }
        }));

        event.registrar("1").playToClient(OffhandSyncPayload.TYPE, OffhandSyncPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof LivingEntity living) {
                for (int slot = 0; slot < SLOTS && slot < payload.items().size(); slot++) {
                    setExtraOffhand(living, slot, payload.items().get(slot));
                }
            }
        }));
    }

}

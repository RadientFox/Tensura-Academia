package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.network.OverhaulStatePayload;
import com.radient.tensuraacadamia.regestry.OverhaulEarth;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.unique.CookSkill;
import io.github.manasmods.tensura.entity.human.golem.TrainingDummyEntity;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.CommonSkills;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.ability.SkillHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class OverhaulQuirk extends Skill {
    public static final String DATA = "TracadamiaOverhaul";
    public static final int SLAM = 1, REASSEMBLE = 2, RECOVER_SELF = 3, RECOVER_OTHER = 4, FUSE = 5, ERADICATE = 6;
    public static final int LEFT_ARM = 1, RIGHT_ARM = 2, LEFT_LEG = 4, RIGHT_LEG = 8;
    public static final int SLAM_TICKS = 80, REASSEMBLE_TICKS = 40, RECOVERY_TICKS = 24, FUSION_TICKS = 50, ERADICATE_TICKS = 36;
    public static final int DETACH_TICKS = 30;
    public static final int[] IMPACT_TICKS = {0, 16, 12, 10, 10, 28, 24};
    public static final int[] AURA_COSTS = {1000, 1500, 2500, 10000};
    public static final int[] COOLDOWNS = {10, 5, 15, 30, 60};
    public static final int DECAY_DEPTH = 3;
    public static final int TARGET_RANGE = 20;
    public static final String BORROWED = "OverhaulBorrowed";
    private static final ResourceLocation DECAY = id("overhaul_decay"), LIMBS = id("overhaul_limbs"), SPEED = id("overhaul_missing_legs");
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75F, 0.015F, 0.015F), 1.3F);
    private static final List<Holder<Attribute>> FUSION_ATTRIBUTES = List.of(Attributes.MAX_HEALTH,
            TensuraAttributes.MAX_SPIRITUAL_HEALTH, TensuraAttributes.MAX_AURA, TensuraAttributes.MAX_MAGICULE,
            TensuraAttributes.LIMITED_SPIRITUAL_MAX_AURA, TensuraAttributes.LIMITED_SPIRITUAL_MAX_MAGICULE,
            Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS,
            Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED, Attributes.KNOCKBACK_RESISTANCE);

    public OverhaulQuirk() { super(SkillType.UNIQUE); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("tracadamia", path); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.withDefaultNamespace("textures/item/iron_pickaxe.png"); }
    @Override public MutableComponent getSkillDescription() { return Component.translatable("tracadamia.skill.overhaul.description"); }
    @Override public int getModes(ManasSkillInstance instance) { return instance.getMastery() >= getMaxMastery() ? 5 : 4; }
    @Override public List<Integer> getModeLearningList(ManasSkillInstance instance) { return List.of(2, 3); }
    @Override public double getLearningPointRequirement(ManasSkillInstance instance, LivingEntity owner, int mode) { return 100; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        int next = Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
        if (next == 3 && instance.getMastery() < getMaxMastery() / 2.0) next = reverse ? 2 : 0;
        return next;
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return "overhaul." + switch (mode) {
            case 1 -> "reassemble"; case 2 -> "recovery"; case 3 -> "fusion"; case 4 -> "eradicate"; default -> "disassemble";
        };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }
    @Override public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return detaching(owner, mode);
    }
    private static boolean detaching(LivingEntity owner, int mode) {
        return mode == 3 && owner.isShiftKeyDown() && !data(owner).getList("Fusions", Tag.TAG_COMPOUND).isEmpty();
    }
    public static CompoundTag data(Entity entity) { return entity.getPersistentData().getCompound(DATA); }
    private static CompoundTag mutable(Entity entity) {
        if (!entity.getPersistentData().contains(DATA, Tag.TAG_COMPOUND)) entity.getPersistentData().put(DATA, new CompoundTag());
        return data(entity);
    }
    public static boolean animating(Entity entity) {
        var tag = data(entity);
        return tag.getInt("Animation") != 0 && entity.level().getGameTime() < tag.getLong("Start") + tag.getInt("Duration");
    }
    public static boolean blocksMovement(Entity entity) {
        return animating(entity) || data(entity).getLong("LockedUntil") > entity.level().getGameTime();
    }
    public static boolean missingHand(LivingEntity entity, InteractionHand hand) {
        int mask = data(entity).getInt("Mask");
        return (mask & (hand == InteractionHand.MAIN_HAND ? RIGHT_ARM : LEFT_ARM)) != 0;
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || mode < 0 || mode >= getModes(instance)
                || animating(owner) || !isInSlot(owner, instance)) return;
        if (mode == 3 && owner.isShiftKeyDown()) {
            if (detaching(owner, mode)) {
                separateFusion(owner, true);
                message(owner, "Fusion separated. Borrowed skills and stat bonuses removed.");
            } else message(owner, "You have no active fusion to separate.");
            return;
        }
        if (instance.onCoolDown(mode)) return;
        if (instance.getCooldownList().size() < getModes(instance)) {
            var cooldowns = new ArrayList<>(instance.getCooldownList());
            while (cooldowns.size() < getModes(instance)) cooldowns.add(0);
            instance.setCoolDownList(cooldowns);
        }
        if (mode == 3 && instance.getMastery() < getMaxMastery() / 2.0) { message(owner, "Fusion requires 50% mastery."); return; }
        if ((mode == 2 || mode == 3) && learnMode(instance, owner, mode)) { instance.markDirty(); return; }
        var tag = mutable(owner);
        LivingEntity target = null;
        int animation, duration;
        double cost;
        switch (mode) {
            case 0 -> {
                if (!owner.onGround() || !owner.getMainHandItem().isEmpty() || missingHand(owner, InteractionHand.MAIN_HAND)) {
                    message(owner, "Disassemble requires an empty, usable main hand and solid ground."); return;
                }
                animation = SLAM; duration = SLAM_TICKS; cost = AURA_COSTS[0];
                tag.putLong("Ground", owner.blockPosition().below().asLong());
                tag.putInt("Radius", instance.isMastered(owner) ? 50 : 30);
                tag.putInt("LastRing", 1);
            }
            case 1 -> {
                if (!owner.onGround() || missingHand(owner, InteractionHand.MAIN_HAND)) { message(owner, "Reassemble requires a usable hand and solid ground."); return; }
                boolean wall = owner.isShiftKeyDown();
                if (!wall && (target = target(owner, TARGET_RANGE, true)) == null) { message(owner, "Look at an enemy within 20 blocks."); return; }
                animation = REASSEMBLE; duration = REASSEMBLE_TICKS; cost = AURA_COSTS[1];
                tag.putBoolean("Wall", wall);
                putPoint(tag, wall ? owner.position().add(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(3)) : target.position());
            }
            case 2 -> {
                target = owner.isShiftKeyDown() ? target(owner, 4, false) : owner;
                if (target == null) { message(owner, "Look at someone within four blocks to recover them."); return; }
                if (missingHand(owner, InteractionHand.MAIN_HAND) && missingHand(owner, InteractionHand.OFF_HAND)) {
                    message(owner, "Recovery requires a usable hand."); return;
                }
                animation = target == owner ? RECOVER_SELF : RECOVER_OTHER;
                duration = RECOVERY_TICKS; cost = AURA_COSTS[2];
                tag.putBoolean("Restore", data(target).getBoolean("RecoveryPending"));
            }
            case 3 -> {
                var remains = tag.getCompound("RecentKill");
                if (remains.isEmpty() || remains.getLong("Expires") < level.getGameTime()) { message(owner, "Fuse within three seconds of your kill."); return; }
                int limit = instance.isMastered(owner) ? 2 : 1;
                if (tag.getList("Fusions", Tag.TAG_COMPOUND).size() >= limit) { message(owner, "Fusion limit reached. Shift-use Fusion to separate."); return; }
                if (missingHand(owner, InteractionHand.MAIN_HAND) || missingHand(owner, InteractionHand.OFF_HAND)) {
                    message(owner, "Fusion requires both arms."); return;
                }
                Vec3 point = point(remains);
                if (point.distanceToSqr(owner.position()) > 36) { message(owner, "Move within six blocks of the remains."); return; }
                animation = FUSE; duration = FUSION_TICKS; cost = AURA_COSTS[3];
                tag.put("PendingFusion", remains.copy());
                putPoint(tag, point.add(0, remains.getDouble("Height") * 0.75, 0));
            }
            case 4 -> {
                target = target(owner, 4, true);
                if (target == null || !owner.getBoundingBox().inflate(1).intersects(target.getBoundingBox())) {
                    message(owner, "Eradicate requires an enemy within one block."); return;
                }
                if (missingHand(owner, InteractionHand.MAIN_HAND) && missingHand(owner, InteractionHand.OFF_HAND)) {
                    message(owner, "Eradicate requires a usable hand."); return;
                }
                animation = ERADICATE; duration = ERADICATE_TICKS; cost = target.getMaxHealth() * 100.0;
                List<Integer> remaining = new ArrayList<>();
                for (int i = 0; i < 4; i++) if ((data(target).getInt("Mask") & (1 << i)) == 0) remaining.add(i);
                tag.putInt("ChosenLimb", remaining.isEmpty() ? -1 : remaining.get(level.random.nextInt(remaining.size())));
            }
            default -> { return; }
        }
        if (!spend(owner, cost)) { tag.remove("PendingFusion"); return; }
        tag.putInt("Animation", animation); tag.putLong("Start", level.getGameTime()); tag.putInt("Duration", duration);
        tag.putInt("Mode", mode); tag.putUUID("Actor", owner.getUUID());
        tag.putInt("Target", target == null ? -1 : target.getId());
        if (target != null) tag.putUUID("TargetUUID", target.getUUID()); else tag.remove("TargetUUID");
        tag.putBoolean("Hit", false); tag.putDouble("Refund", cost);
        tag.putBoolean("Mastered", instance.isMastered(owner));
        if (mode == 3) tag.remove("RecentKill");
        if (mode != 2 || tag.getBoolean("Restore")) instance.setCoolDown(COOLDOWNS[mode], mode);
        if (animation == ERADICATE && target != null) {
            var victim = mutable(target);
            victim.putLong("LockedUntil", level.getGameTime() + duration);
            victim.putInt("Swelling", tag.getInt("ChosenLimb") + 1);
            victim.putLong("SwellStart", level.getGameTime() + 12);
            sync(target);
        }
        if (target != null && target != owner) owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        else if (animation == FUSE) owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, point(tag));
        instance.markDirty(); sync(owner);
        level.playSound(null, owner.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 0.8F, 0.65F);
    }

    private static LivingEntity target(LivingEntity owner, double range, boolean enemy) {
        Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(range));
        var hit = ProjectileUtil.getEntityHitResult(owner, eye, end, new AABB(eye, end).inflate(0.5),
                entity -> entity instanceof LivingEntity living && living != owner && living.isAlive() && !living.isSpectator()
                        && (!enemy || HomingQuirk.isEnemy(owner, living)), range * range);
        return hit != null && owner.hasLineOfSight(hit.getEntity()) ? (LivingEntity) hit.getEntity() : null;
    }
    private static boolean spend(LivingEntity owner, double cost) {
        var energy = TensuraStorages.getExistenceFrom(owner);
        if (!Double.isFinite(cost) || energy.getAura() < cost) { message(owner, "Not enough aura (" + (long) cost + " required)."); return false; }
        energy.setAura(energy.getAura() - cost); energy.markDirty(); return true;
    }
    private static void message(LivingEntity owner, String text) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(text), true);
    }
    private static void putPoint(CompoundTag tag, Vec3 point) { tag.putDouble("X", point.x); tag.putDouble("Y", point.y); tag.putDouble("Z", point.z); }
    private static Vec3 point(CompoundTag tag) { return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")); }

    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || !(owner.level() instanceof ServerLevel level)
                || !owner.getPersistentData().contains(DATA)) return;
        var tag = data(owner);
        if (tag.contains("DetachedArms") && level.getGameTime() >= tag.getLong("DetachStart") + DETACH_TICKS) {
            tag.remove("DetachedArms"); tag.remove("DetachStart"); sync(owner);
        }
        if (tag.getInt("Mask") != 0) {
            if (owner.isUsingItem() && missingHand(owner, owner.getUsedItemHand())) owner.stopUsingItem();
            if (owner.tickCount % 20 == 0) regenerateLimbs(owner);
        }
        int animation = tag.getInt("Animation");
        if (animation == 0) return;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.OVERHAUL.get()).orElse(null);
        if (!owner.isAlive() || instance == null || !QuirkSkills.OVERHAUL.get().isInSlot(owner, instance)) { finish(owner, true); return; }
        int age = (int) (level.getGameTime() - tag.getLong("Start"));
        owner.setDeltaMovement(0, owner.getDeltaMovement().y, 0); owner.hurtMarked = true;
        LivingEntity target = tag.hasUUID("TargetUUID") && level.getEntity(tag.getUUID("TargetUUID")) instanceof LivingEntity living ? living : null;
        if (!tag.getBoolean("Hit") && age >= IMPACT_TICKS[animation]) {
            if ((animation == ERADICATE || animation == RECOVER_OTHER) && (target == null || !target.isAlive()
                    || target.distanceToSqr(owner) > 36)) { finish(owner, true); return; }
            tag.putBoolean("Hit", true);
            switch (animation) {
                case REASSEMBLE -> reassemble(owner, target, tag);
                case RECOVER_SELF, RECOVER_OTHER -> recover(animation == RECOVER_SELF ? owner : target, tag.getBoolean("Restore"));
                case FUSE -> fuse(owner, tag.getCompound("PendingFusion"));
                case ERADICATE -> eradicate(owner, target, tag.getInt("ChosenLimb"));
                default -> { }
            }
            instance.addMasteryPoint(owner); instance.markDirty();
            level.playSound(null, owner.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.5F);
        }
        if (animation == SLAM && age >= 16 && age <= 36) {
            int radius = Math.min(tag.getInt("Radius"), (age - 15) * tag.getInt("Radius") / 20);
            decayGround(owner, BlockPos.of(tag.getLong("Ground")), tag.getInt("LastRing"), radius);
            tag.putInt("LastRing", radius);
        }
        if (age >= tag.getInt("Duration")) finish(owner, false);
    }

    private static void finish(LivingEntity owner, boolean failed) {
        var tag = data(owner);
        if (failed && !tag.getBoolean("Hit")) {
            var energy = TensuraStorages.getExistenceFrom(owner);
            energy.setAura(Math.min(EnergyHelper.getMaxAura(owner), energy.getAura() + tag.getDouble("Refund"))); energy.markDirty();
            if (owner.level() instanceof ServerLevel level && tag.hasUUID("TargetUUID")
                    && level.getEntity(tag.getUUID("TargetUUID")) instanceof LivingEntity target) {
                data(target).remove("LockedUntil"); data(target).remove("Swelling"); sync(target);
            }
        }
        tag.remove("Animation"); tag.remove("PendingFusion"); tag.remove("TargetUUID"); tag.remove("Refund");
        sync(owner);
    }

    private static void decayGround(LivingEntity owner, BlockPos center, int previous, int radius) {
        ServerLevel level = (ServerLevel) owner.level();
        for (int r = Math.max(2, previous + 1); r <= radius; r++) for (int x = -r; x <= r; x++) {
            int z = r - Math.abs(x);
            decayColumn(level, owner, center.offset(x, 0, z));
            if (z != 0) decayColumn(level, owner, center.offset(x, 0, -z));
        }
    }
    private static void decayColumn(ServerLevel level, LivingEntity owner, BlockPos surface) {
        if (!level.hasChunkAt(surface) || !level.getWorldBorder().isWithinBounds(surface)) return;
        for (int depth = 0; depth < DECAY_DEPTH; depth++) {
            BlockPos pos = surface.below(depth);
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || state.hasBlockEntity()) continue;
            level.destroyBlock(pos, false, owner);
        }
        if (level.random.nextInt(8) == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.STONE.defaultBlockState()), surface.getX() + 0.5, surface.getY() + 1, surface.getZ() + 0.5,
                3, 0.15, 0.2, 0.15, 0.04);
    }
    private static void reassemble(LivingEntity owner, LivingEntity target, CompoundTag tag) {
        ServerLevel level = (ServerLevel) owner.level();
        BlockPos center = BlockPos.containing(point(tag));
        boolean mastered = tag.getBoolean("Mastered");
        if (tag.getBoolean("Wall")) {
            Direction forward = owner.getDirection(), side = forward.getClockWise();
            int half = mastered ? 4 : 2, height = mastered ? 6 : 4;
            for (int x = -half; x <= half; x++) for (int y = 0; y < height; y++)
                OverhaulEarth.place(level, center.relative(side, x).above(y), 600);
        } else {
            if (target != null && target.isAlive()) {
                target.hurt(QuirkSkills.OVERHAUL.get().createSource(SkillAPI.getSkillsFrom(owner)
                        .getSkill(QuirkSkills.OVERHAUL.get()).orElseThrow(), owner, DamageTypes.FALLING_BLOCK, 1), mastered ? 150 : 100);
                target.teleportTo(target.getX(), Math.max(target.getY(), center.getY() + 4), target.getZ());
                target.setDeltaMovement(0, 0.8, 0); target.hurtMarked = true;
            }
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if ((Math.abs(x) + Math.abs(z)) % 2 != 0) continue;
                int height = Math.max(1, 4 - Math.abs(x) - Math.abs(z));
                for (int y = 0; y < height; y++) OverhaulEarth.place(level, center.offset(x, y, z), 200, y == height - 1);
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                center.getX(), center.getY() + 1, center.getZ(), 70, 2, 0.5, 2, 0.12);
    }

    static void recover(LivingEntity target, boolean restore) {
        var tag = mutable(target);
        if (!restore) { target.setHealth(1); tag.putBoolean("RecoveryPending", true); return; }
        CookSkill.removeCookedHP(target);
        var hp = target.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.removeModifier(DECAY);
        for (var effect : new ArrayList<>(target.getActiveEffects()))
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) target.removeEffect(effect.getEffect());
        tag.remove("Mask"); tag.remove("Swelling"); tag.remove("LockedUntil"); tag.remove("RecoveryPending");
        for (int i = 0; i < 4; i++) { tag.remove("Lost" + i); tag.remove("Regrow" + i); }
        applyLimbs(target);
        target.setHealth(target.getMaxHealth());
        var energy = TensuraStorages.getExistenceFrom(target);
        energy.setSpiritualHealth(target.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH)); energy.markDirty();
        if (target.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
        sync(target);
    }

    @SubscribeEvent public static void deconstruct(LivingDamageEvent.Post event) {
        var target = event.getEntity();
        if (target.level().isClientSide || !Float.isFinite(event.getNewDamage()) || event.getNewDamage() <= 0 || !target.isAlive()
                || !(event.getSource().getEntity() instanceof LivingEntity owner)
                || event.getSource().getDirectEntity() != owner || !melee(event.getSource())
                || !owner.getMainHandItem().isEmpty() || missingHand(owner, InteractionHand.MAIN_HAND)) return;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.OVERHAUL.get()).orElse(null);
        if (instance == null || !TensuraStorages.getAbilityFrom(owner).getAbilitySlots().stream().anyMatch(slot ->
                slot.getSkill() == instance.getSkill() && (slot.getMode() == 0
                        || slot.getMode() == 3 && instance.getMastery() >= 1250
                        && instance.getOrCreateTag().getDouble("overhaul.fusion") >= 100))) return;
        destroyHealth(owner, target, event.getNewDamage());
        instance.addMasteryPoint(owner); instance.markDirty();
    }
    static void destroyHealth(LivingEntity owner, LivingEntity target, float damage) {
        if (!Float.isFinite(damage) || damage <= 0 || target.isRemoved()) return;
        repairHealth(target);
        var hp = target.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        mutable(target);
        var prior = hp.getModifier(DECAY);
        double loss = damage + (prior == null ? 0 : -prior.amount());
        hp.addOrReplacePermanentModifier(new AttributeModifier(DECAY, -loss, AttributeModifier.Operation.ADD_VALUE));
        if (remainingHealth(target) <= 0) execute(owner, target);
        else target.setHealth(Math.min(target.getHealth(), target.getMaxHealth()));
    }

    private static void execute(LivingEntity owner, LivingEntity target) {
        if (target.isRemoved()) return;
        var source = target.damageSources().source(DamageTypes.GENERIC_KILL, owner);
        target.setLastHurtByMob(owner);
        if (owner instanceof Player player) target.setLastHurtByPlayer(player);
        if (target instanceof TrainingDummyEntity dummy) {
            if (!net.neoforged.neoforge.common.CommonHooks.onLivingDeath(dummy, source)) {
                if (dummy.level().getBlockState(dummy.blockPosition()).is(
                        io.github.manasmods.tensura.registry.block.TensuraBlocks.TRAINING_DUMMY.get()))
                    dummy.level().destroyBlock(dummy.blockPosition(), false);
                if (DoubleCloneManager.isDoubleDuplicate(dummy) && dummy.inventory != null) dummy.inventory.clearContent();
                dummy.kill();
            }
        } else {
            target.setHealth(0);
            target.die(source);
        }
    }

    static void repairHealth(LivingEntity target) {
        var hp = target.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        var decay = hp.getModifier(DECAY);
        if (decay == null && !target.getPersistentData().contains(DATA)) return;
        boolean brokenModifier = decay != null && !Double.isFinite(decay.amount());
        if (brokenModifier) hp.removeModifier(DECAY);
        float maximum = target.getMaxHealth();
        if (Float.isFinite(maximum) && maximum > 0 && !Float.isFinite(target.getHealth()))
            target.setHealth(brokenModifier ? maximum : 1);
        var energy = TensuraStorages.getExistenceFrom(target);
        if (!Double.isFinite(energy.getSpiritualHealth())) { energy.setSpiritualHealth(0); energy.markDirty(); }
        if (target instanceof TrainingDummyEntity dummy && (!Float.isFinite(dummy.damageTotal) || !Float.isFinite(dummy.lastDamageTaken))) {
            dummy.lastDamageTaken = dummy.damageTotal = 0;
            dummy.damageNumber = 0;
        }
    }

    public static float duplicateHealth(LivingEntity entity, float maximum) {
        var hp = entity.getAttribute(Attributes.MAX_HEALTH);
        var decay = hp == null ? null : hp.getModifier(DECAY);
        double loss = decay != null && Double.isFinite(decay.amount()) ? -decay.amount() : 0;
        return (float) Math.max(0, (maximum - loss) * (1 - Integer.bitCount(data(entity).getInt("Mask") & 15) / 8.0));
    }

    private static double remainingHealth(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.Mob mob) {
            float fixed = DoubleMobHealthData.get(mob);
            if (Float.isFinite(fixed) && fixed > 0) return duplicateHealth(entity, fixed);
        }
        var hp = entity.getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return entity.getMaxHealth();
        double base = hp.getBaseValue();
        for (var modifier : hp.getModifiers()) if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
        double value = base;
        for (var modifier : hp.getModifiers()) if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += base * modifier.amount();
        for (var modifier : hp.getModifiers()) if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1 + modifier.amount();
        return value;
    }
    private static boolean melee(net.minecraft.world.damagesource.DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    static void eradicate(LivingEntity owner, LivingEntity target, int limb) {
        var tag = mutable(target);
        repairHealth(target);
        if (limb < 0) { execute(owner, target); return; }
        tag.putInt("Mask", tag.getInt("Mask") | 1 << limb);
        long now = target.level().getGameTime();
        tag.putLong("Lost" + limb, now);
        tag.putLong("Regrow" + limb, regenerationTicks(target) == Long.MAX_VALUE ? Long.MAX_VALUE : now + regenerationTicks(target));
        tag.remove("Swelling");
        applyLimbs(target); sync(target);
        ServerLevel level = (ServerLevel) target.level();
        double height = limb < 2 ? target.getBbHeight() * 0.65 : target.getBbHeight() * 0.25;
        Vec3 right = target.getLookAngle().cross(new Vec3(0, 1, 0)).normalize().scale((limb % 2 == 0 ? -1 : 1) * 0.3);
        level.sendParticles(BLOOD, target.getX() + right.x, target.getY() + height, target.getZ() + right.z,
                85, 0.22, 0.3, 0.22, 0.12);
    }
    private static long regenerationTicks(LivingEntity entity) {
        var skills = SkillAPI.getSkillsFrom(entity);
        if (skills.getSkill(ExtraSkills.INFINITE_REGENERATION.get()).isPresent()) return 600;
        if (skills.getSkill(ExtraSkills.ULTRASPEED_REGENERATION.get()).isPresent()) return 3000;
        if (skills.getSkill(CommonSkills.SELF_REGENERATION.get()).isPresent()) return 6000;
        return Long.MAX_VALUE;
    }
    static void regenerateLimbs(LivingEntity entity) {
        var tag = data(entity);
        int mask = tag.getInt("Mask"), before = mask;
        long now = entity.level().getGameTime(), duration = regenerationTicks(entity);
        for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0) {
            long deadline = tag.getLong("Regrow" + i);
            if (duration != Long.MAX_VALUE) deadline = Math.min(deadline, tag.getLong("Lost" + i) + duration);
            if (now >= deadline) { mask &= ~(1 << i); tag.remove("Lost" + i); tag.remove("Regrow" + i); }
            else tag.putLong("Regrow" + i, deadline);
        }
        if (before != mask) { tag.putInt("Mask", mask); applyLimbs(entity); sync(entity); }
    }
    private static void applyLimbs(LivingEntity entity) {
        int mask = data(entity).getInt("Mask"), count = Integer.bitCount(mask & 15);
        var hp = entity.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(LIMBS);
            if (count != 0) hp.addPermanentModifier(new AttributeModifier(LIMBS, -count / 8.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED);
            int legs = Integer.bitCount(mask & (LEFT_LEG | RIGHT_LEG));
            if (legs > 0) speed.addPermanentModifier(new AttributeModifier(SPEED, legs == 1 ? -0.5 : -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (entity instanceof Player player) {
            if ((mask & (LEFT_LEG | RIGHT_LEG)) == (LEFT_LEG | RIGHT_LEG)) {
                player.setForcedPose(Pose.SWIMMING); mutable(entity).putBoolean("Crawling", true);
            } else if (data(entity).getBoolean("Crawling")) {
                if (player.getForcedPose() == Pose.SWIMMING) player.setForcedPose(null);
                data(entity).remove("Crawling");
            }
        }
        entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth())); entity.refreshDimensions();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity owner && owner != victim
                && SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.OVERHAUL.get()).isPresent()) {
            CompoundTag remains = capture(victim);
            remains.putLong("Expires", level.getGameTime() + 60); putPoint(remains, victim.position());
            mutable(owner).put("RecentKill", remains);
        }
        if (victim.getPersistentData().contains(DATA)) clearOnDeath(victim);
    }
    static CompoundTag capture(LivingEntity victim) {
        CompoundTag snapshot = new CompoundTag(), attributes = new CompoundTag();
        snapshot.putString("EntityType", BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString());
        snapshot.putUUID("UUID", victim.getUUID()); snapshot.putString("Name", victim.getName().getString());
        snapshot.putDouble("Height", victim.getBbHeight());
        if (victim instanceof Player player) ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE,
                new ResolvableProfile(player.getGameProfile())).result().ifPresent(profile -> snapshot.put("Profile", profile));
        for (var holder : FUSION_ATTRIBUTES) if (victim.getAttribute(holder) != null) {
            double value = victim.getAttributeValue(holder);
            if (Double.isFinite(value) && value > 0) attributes.putDouble(BuiltInRegistries.ATTRIBUTE.getKey(holder.value()).toString(), value);
        }
        snapshot.put("Attributes", attributes);
        ListTag skills = new ListTag();
        for (var source : SkillAPI.getSkillsFrom(victim).getLearnedSkills())
            if (source.getSkill() instanceof Skill skill && skill.getType() != SkillType.ULTIMATE && !source.isSubInstance()) skills.add(source.toNBT().copy());
        snapshot.put("Skills", skills); return snapshot;
    }
    static void fuse(LivingEntity owner, CompoundTag snapshot) {
        var tag = mutable(owner);
        ListTag fusions = tag.getList("Fusions", Tag.TAG_COMPOUND);
        int index = fusions.size();
        var attributes = snapshot.getCompound("Attributes");
        for (var holder : FUSION_ATTRIBUTES) {
            var attribute = owner.getAttribute(holder);
            String key = BuiltInRegistries.ATTRIBUTE.getKey(holder.value()).toString();
            double value = attributes.getDouble(key);
            if (holder == TensuraAttributes.LIMITED_SPIRITUAL_MAX_AURA || holder == TensuraAttributes.LIMITED_SPIRITUAL_MAX_MAGICULE) {
                var maximum = holder == TensuraAttributes.LIMITED_SPIRITUAL_MAX_AURA ? TensuraAttributes.MAX_AURA : TensuraAttributes.MAX_MAGICULE;
                value = owner.getAttributeValue(holder) > 0 ? attributes.getDouble(BuiltInRegistries.ATTRIBUTE.getKey(maximum.value()).toString()) : 0;
            }
            if (attribute != null && value > 0 && Double.isFinite(value)) attribute.addPermanentModifier(
                    new AttributeModifier(id("overhaul_fusion_" + index), value, AttributeModifier.Operation.ADD_VALUE));
        }
        var storage = SkillAPI.getSkillsFrom(owner);
        for (var entry : snapshot.getList("Skills", Tag.TAG_COMPOUND)) {
            var source = ManasSkillInstance.fromNBT(((CompoundTag) entry).copy());
            if (storage.getSkill(source.getSkill()).isPresent()) continue;
            var copy = source.getSkill().createDefaultInstance(); copy.setMastery(source.getMastery());
            if (source.getSkill() instanceof Skill skill) for (int mode : skill.getModeLearningList(source)) {
                String modeId = skill.getModeId(source, mode);
                copy.getOrCreateTag().putDouble(modeId, source.getOrCreateTag().getDouble(modeId));
            }
            copy.getOrCreateTag().putBoolean("NoMagiculeCost", true); copy.getOrCreateTag().putBoolean(BORROWED, true);
            copy.setRemoveTime(Integer.MAX_VALUE); SkillHelper.learnSkill(owner, copy);
        }
        CompoundTag visual = snapshot.copy(); visual.remove("Skills"); visual.remove("Expires");
        fusions.add(visual); tag.put("Fusions", fusions);
        owner.setHealth((float) Math.min(owner.getMaxHealth(), owner.getHealth()
                + attributes.getDouble(BuiltInRegistries.ATTRIBUTE.getKey(Attributes.MAX_HEALTH.value()).toString())));
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setSpiritualHealth(Math.min(owner.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH),
                energy.getSpiritualHealth() + attributes.getDouble(BuiltInRegistries.ATTRIBUTE.getKey(TensuraAttributes.MAX_SPIRITUAL_HEALTH.value()).toString())));
        energy.markDirty(); sync(owner);
    }
    static void clearOnDeath(LivingEntity owner) {
        if (owner instanceof Player player && data(owner).getBoolean("Crawling") && player.getForcedPose() == Pose.SWIMMING)
            player.setForcedPose(null);
        separateFusion(owner, false);
        var hp = owner.getAttribute(Attributes.MAX_HEALTH); if (hp != null) hp.removeModifier(DECAY);
        owner.getPersistentData().remove(DATA); applyLimbs(owner); sync(owner);
    }
    static void separateFusion(LivingEntity owner, boolean fallingArms) {
        var tag = mutable(owner);
        ListTag fusions = tag.getList("Fusions", Tag.TAG_COMPOUND).copy();
        for (var holder : FUSION_ATTRIBUTES) {
            var attribute = owner.getAttribute(holder);
            if (attribute != null) for (int i = 0; i < 2; i++) attribute.removeModifier(id("overhaul_fusion_" + i));
        }
        var storage = SkillAPI.getSkillsFrom(owner);
        var ability = TensuraStorages.getAbilityFrom(owner);
        for (var skill : new ArrayList<>(storage.getLearnedSkills()))
            if (skill.isTemporarySkill() && skill.getOrCreateTag().getBoolean(BORROWED)) {
                ability.removeSkillFromPresets(skill.getSkill());
                storage.forgetSkill(skill.getSkillId());
            }
        storage.markDirty(); ability.markDirty();
        tag.remove("Fusions"); tag.remove("PendingFusion");
        if (fallingArms && !fusions.isEmpty()) {
            tag.put("DetachedArms", fusions); tag.putLong("DetachStart", owner.level().getGameTime());
            owner.level().playSound(null, owner.blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.7F);
        } else { tag.remove("DetachedArms"); tag.remove("DetachStart"); }
        owner.setHealth(Math.min(owner.getHealth(), owner.getMaxHealth()));
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setSpiritualHealth(Math.min(energy.getSpiritualHealth(), owner.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH)));
        energy.setAura(Math.min(energy.getAura(), EnergyHelper.getMaxAura(owner)));
        energy.setMagicule(Math.min(energy.getMagicule(), EnergyHelper.getMaxMagicule(owner))); energy.markDirty();
        sync(owner);
    }

    public static OverhaulStatePayload visual(Entity entity) {
        var tag = data(entity); CompoundTag visible = new CompoundTag();
        for (String key : List.of("Animation", "Start", "Duration", "Target", "Mask", "Swelling", "SwellStart", "LockedUntil", "X", "Y", "Z", "DetachStart"))
            if (tag.contains(key)) visible.put(key, tag.get(key).copy());
        for (String list : List.of("Fusions", "DetachedArms")) {
            ListTag fusions = new ListTag();
            for (var entry : tag.getList(list, Tag.TAG_COMPOUND)) {
                var source = (CompoundTag) entry; CompoundTag body = new CompoundTag();
                for (String key : List.of("EntityType", "UUID", "Name", "Profile")) if (source.contains(key)) body.put(key, source.get(key).copy());
                fusions.add(body);
            }
            visible.put(list, fusions);
        }
        return new OverhaulStatePayload(entity.getId(), visible);
    }
    private static void sync(Entity entity) {
        if (entity.level() instanceof ServerLevel) PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, visual(entity));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget().getPersistentData().contains(DATA))
            PacketDistributor.sendToPlayer(player, visual(event.getTarget()));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(DATA)) PacketDistributor.sendToPlayer(player, visual(player));
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity living)) return;
        repairHealth(living);
        var hp = living.getAttribute(Attributes.MAX_HEALTH);
        if (!living.getPersistentData().contains(DATA)) {
            if (hp == null || hp.getModifier(DECAY) == null) return;
            mutable(living);
        }
        if (hp != null && hp.getModifier(DECAY) != null && remainingHealth(living) <= 0) {
            execute(living, living); return;
        }
        applyLimbs(living);
        if (data(living).contains("Animation")) finish(living, true);
    }
    @SubscribeEvent public static void cloned(PlayerEvent.Clone event) {
        if (event.isWasDeath()) clearOnDeath(event.getEntity());
        else if (event.getOriginal().getPersistentData().contains(DATA)) event.getEntity().getPersistentData().put(DATA, data(event.getOriginal()).copy());
    }
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent event) { sync(event.getEntity()); }
    @SubscribeEvent public static void noMelee(AttackEntityEvent event) {
        if (missingHand(event.getEntity(), InteractionHand.MAIN_HAND) || animating(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGH) public static void noMobMelee(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getSource().getDirectEntity() == attacker
                && melee(event.getSource()) && missingHand(attacker, InteractionHand.MAIN_HAND)) event.setCanceled(true);
    }
    @SubscribeEvent public static void noItem(PlayerInteractEvent.RightClickItem event) { if (missingHand(event.getEntity(), event.getHand())) event.setCanceled(true); }
    @SubscribeEvent public static void noBlock(PlayerInteractEvent.RightClickBlock event) { if (missingHand(event.getEntity(), event.getHand())) event.setCanceled(true); }
    @SubscribeEvent public static void noEntity(PlayerInteractEvent.EntityInteract event) { if (missingHand(event.getEntity(), event.getHand())) event.setCanceled(true); }
    @SubscribeEvent public static void noSpecificEntity(PlayerInteractEvent.EntityInteractSpecific event) { if (missingHand(event.getEntity(), event.getHand())) event.setCanceled(true); }
    @SubscribeEvent public static void noMining(PlayerInteractEvent.LeftClickBlock event) { if (missingHand(event.getEntity(), InteractionHand.MAIN_HAND)) event.setCanceled(true); }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.entity.human.CloneEntity;
import io.github.manasmods.tensura.registry.entity.HumanEntityTypes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import io.github.manasmods.tensura.entity.template.subclass.ISubordinate;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.EnumSet;

public final class DoubleCloneManager {
    static final String TAG_DOUBLE = "TracadamiaDouble";
    static final String TAG_COMMITTED = "TracadamiaDoubleCommitted";
    static final String TAG_CREATOR = "TracadamiaDoubleCreator";
    static final String TAG_SELF = "TracadamiaDoubleSelf";
    static final String TAG_DEADLINE = "TracadamiaDoubleDeadline";
    static final String TAG_HOSTILE = "TracadamiaDoubleHostile";
    private static final String TAG_HOSTILE_TARGET = "TracadamiaDoubleHostileTarget";
    static final String TAG_PARADE = "TracadamiaDoubleParade";
    static final String TAG_SOURCE = "TracadamiaDoubleSource";
    static final String TAG_CREATOR_DEATH_GENERATION = "TracadamiaDoubleCreatorDeathGeneration";
    static final String TAG_CREATOR_TEAM = "TracadamiaDoubleCreatorTeam";
    static final String TAG_REDUCED_MAX_HEALTH = "TracadamiaDoubleMaxHealth";
    private static final ResourceLocation SENTIENT_SKILL_ID =
            ResourceLocation.fromNamespaceAndPath("nightmareutils", "sentient");
    private static final ResourceLocation HEALTH_FRACTION_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "double_health_fraction");
    private static final int NORMAL_BETRAYAL_TICKS = 6_000;
    private static final int PARADE_BETRAYAL_TICKS = 600;
    private static final int AI_TARGET_SCAN_TICKS = 10;
    private static final double HOSTILE_CLONE_TARGET_CHANCE = 0.75D;
    private static final double HOSTILE_CLONE_TARGET_RANGE = 20.0D;
    private static final double SUBORDINATE_MOVEMENT_SPEED = 1.5D;
    private static final Map<UUID, Mob> LOADED_CLONES = new HashMap<>();

    private DoubleCloneManager() {
    }

    public static int countNonSelf(ServerPlayer creator) {
        return DoubleCloneLedger.get(creator.serverLevel().getServer()).countNonSelf(creator.getUUID());
    }

    public static boolean hasSentientSkill() {
        return sentientSkill() != null;
    }

    private static ManasSkill sentientSkill() {
        return SkillAPI.getSkillRegistry().get(SENTIENT_SKILL_ID);
    }

    public static double auraCost(ServerPlayer creator, double fraction) {
        double maximum = EnergyHelper.getMaxAura(creator);
        return Double.isFinite(maximum) ? Math.max(0.0D, maximum * fraction) : Double.POSITIVE_INFINITY;
    }

    public static boolean summonOne(ServerPlayer creator, CompoundTag snapshot, UUID sourceId,
                                    boolean self, double auraCost) {
        if (!self && countNonSelf(creator) >= DoubleQuirk.duplicateLimit(creator)) {
            creator.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "tracadamia.skill.double.clone_limit", DoubleQuirk.duplicateLimit(creator)), true);
            return false;
        }
        if (!hasSentientSkill()) {
            message(creator, "tracadamia.skill.double.sentient_missing");
            return false;
        }
        if (TensuraStorages.getExistenceFrom(creator).getAura() < auraCost) {
            message(creator, "tracadamia.skill.double.need_aura");
            return false;
        }

        ServerLevel level = creator.serverLevel();
        double healthFraction = DoubleQuirk.hasMastery(creator)
                ? DoubleQuirk.MASTERED_HEALTH_FRACTION : DoubleQuirk.NORMAL_HEALTH_FRACTION;
        Mob clone = createDuplicate(creator, snapshot, sourceId, healthFraction);
        if (clone == null) return false;
        Vec3 spawn = creator.position().add(-creator.getLookAngle().z * 1.25D, 0.0D,
                creator.getLookAngle().x * 1.25D);
        position(clone, spawn);
        prepareMetadata(clone, creator, sourceId, self, level.getServer().overworld().getGameTime()
                + NORMAL_BETRAYAL_TICKS, false);
        if (!level.addFreshEntity(clone)) {
            clone.discard();
            return false;
        }

        commit(clone, creator, !self);
        chargeAura(creator, auraCost);
        creator.serverLevel().playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    public static boolean summonParade(ServerPlayer creator, double auraCost) {
        if (!DoubleQuirk.hasMastery(creator)) {
            message(creator, "tracadamia.skill.double.need_mastery");
            return false;
        }
        if (!hasSentientSkill()) {
            message(creator, "tracadamia.skill.double.sentient_missing");
            return false;
        }
        if (TensuraStorages.getExistenceFrom(creator).getAura() < auraCost) {
            message(creator, "tracadamia.skill.double.need_aura");
            return false;
        }

        CompoundTag snapshot = DoubleResearchData.capture(creator);
        ServerLevel level = creator.serverLevel();
        List<Mob> prepared = new ArrayList<>(DoubleQuirk.PARADE_CLONES);
        for (int i = 0; i < DoubleQuirk.PARADE_CLONES; i++) {
            Mob clone = createDuplicate(creator, snapshot, creator.getUUID(), DoubleQuirk.PARADE_HEALTH_FRACTION);
            if (clone == null) {
                prepared.forEach(Entity::discard);
                return false;
            }
            prepared.add(clone);
        }

        long deadline = level.getServer().overworld().getGameTime() + PARADE_BETRAYAL_TICKS;
        for (int i = 0; i < prepared.size(); i++) {
            Mob clone = prepared.get(i);
            position(clone, paradePosition(creator, i));
            prepareMetadata(clone, creator, creator.getUUID(), true, deadline, true);
        }
        LivingEntity paradeTarget = findNearestEnemy(creator.getUUID(), creator, prepared.getFirst());
        if (paradeTarget != null) prepared.forEach(clone -> setDuplicateTarget(clone, paradeTarget));

        List<Mob> spawned = new ArrayList<>(prepared.size());
        for (Mob clone : prepared) {
            if (!level.addFreshEntity(clone)) {
                for (Mob partial : spawned) partial.discard();
                for (Mob pending : prepared) if (!spawned.contains(pending)) pending.discard();
                return false;
            }
            spawned.add(clone);
        }

        for (Mob clone : spawned) commit(clone, creator, false);
        chargeAura(creator, auraCost);
        return true;
    }

    private static Mob createDuplicate(ServerPlayer creator, CompoundTag snapshot, UUID sourceId,
                                       double healthFraction) {
        ServerLevel level = creator.serverLevel();
        Mob clone = null;
        if (!snapshot.contains("Profile")) {
            ResourceLocation typeId = ResourceLocation.tryParse(snapshot.getString("EntityType"));
            if (typeId != null) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
                Entity entity = type.create(level);
                if (entity instanceof Mob mob && !(entity instanceof Player)) clone = mob;
                else if (entity != null) entity.discard();
            }
        }
        if (clone == null) clone = HumanEntityTypes.CLONE.get().create(level);
        if (clone == null) return null;

        if (clone instanceof CloneEntity tensuraClone) {
            tensuraClone.setLife(-1);
            tensuraClone.setChunkLoader(false);
            tensuraClone.setIllusion(false);
            tensuraClone.setStatic(false);
            tensuraClone.tame(creator);
            if (snapshot.contains("Profile")) {
                ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, snapshot.get("Profile")).result()
                        .ifPresent(tensuraClone::setProfile);
            }
        }
        clone.setPersistenceRequired();
        clone.setCanPickUpLoot(false);
        clone.setCustomName(net.minecraft.network.chat.Component.literal(snapshot.getString("Name")));
        clone.setCustomNameVisible(false);
        clone.setTarget(null);
        copyEquipment(clone, snapshot, level);
        applyCurios(clone, snapshot, level);
        copyPlayerSkillsAndSlots(clone, snapshot);

        ManasSkill sentient = sentientSkill();
        if (sentient == null) {
            clone.discard();
            return null;
        }
        if (SkillAPI.getSkillsFrom(clone).getSkill(SENTIENT_SKILL_ID).isEmpty()) {
            ManasSkillInstance sentientInstance = new TensuraSkillInstance(sentient);
            sentientInstance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
            if (!SkillHelper.learnSkill(clone, sentientInstance)) {
                clone.discard();
                return null;
            }
        }

        applyEquipmentAttributes(clone);
        copyAttributes(clone, snapshot);
        copyTensuraResources(clone, snapshot);
        double reducedMaxHealth = applyHealthFraction(clone, healthFraction);

        clone.getPersistentData().putString(TAG_SOURCE, sourceId.toString());
        clone.getPersistentData().putDouble(TAG_REDUCED_MAX_HEALTH, reducedMaxHealth);
        DoubleMobHealthData.set(clone, (float) reducedMaxHealth);
        clone.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        clone.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        for (EquipmentSlot slot : EquipmentSlot.values()) clone.setDropChance(slot, 0.0F);
        clone.setHealth(clone.getMaxHealth());
        return clone;
    }

    private static void copyPlayerSkillsAndSlots(Mob clone, CompoundTag snapshot) {
        ListTag skills = snapshot.getList("Skills", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < skills.size(); i++) {
            ManasSkillInstance skill;
            try {
                skill = TensuraSkillInstance.fromNBT(skills.getCompound(i).copy());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (skill == null || skill.getSkillId().equals(SENTIENT_SKILL_ID)
                    || SkillAPI.getSkillsFrom(clone).getSkill(skill.getSkillId()).isPresent()) continue;
            skill.getOrCreateTag().putBoolean("NoMagiculeCost", true);
            boolean wasToggled = skill.isToggled();
            if (SkillHelper.learnSkill(clone, skill) && wasToggled) skill.onToggleOn(clone);
        }

        ListTag presets = snapshot.getList("AbilityPresets", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (presets.isEmpty()) return;
        int activePreset = Math.max(0, snapshot.getInt("ActivePreset"));
        var ability = TensuraStorages.getAbilityFrom(clone);
        int presetCount = Math.min(presets.size(), ability.getPresets().size());
        for (int presetIndex = 0; presetIndex < presetCount; presetIndex++) {
            CompoundTag preset = presets.getCompound(presetIndex);
            ListTag savedSlots = preset.getList("Slots", net.minecraft.nbt.Tag.TAG_COMPOUND);
            List<AbilitySlot> destinationSlots = ability.getAbilitySlots(presetIndex);
            ability.getPresets().get(presetIndex).setName(preset.getString("Name"));
            for (int slotIndex = 0; slotIndex < Math.min(savedSlots.size(), destinationSlots.size()); slotIndex++) {
                ability.setAbilitySlot(presetIndex, slotIndex,
                        AbilitySlot.fromNBT(savedSlots.getCompound(slotIndex).copy()));
            }
        }
        ability.setActivePreset(Math.min(activePreset, ability.getPresets().size() - 1));
        ability.markDirty();
    }

    private static void copyTensuraResources(Mob clone, CompoundTag snapshot) {
        if (snapshot.contains("MaxAura")) {
            AttributeInstance maxAura = clone.getAttribute(TensuraAttributes.MAX_AURA);
            if (maxAura != null) setEffectiveValue(maxAura, snapshot.getDouble("MaxAura"));
        }
        if (snapshot.contains("MaxMagicule")) {
            AttributeInstance maxMagicule = clone.getAttribute(TensuraAttributes.MAX_MAGICULE);
            if (maxMagicule != null) setEffectiveValue(maxMagicule, snapshot.getDouble("MaxMagicule"));
        }

        var existence = TensuraStorages.getExistenceFrom(clone);
        double maximumAura = Math.max(0.0D, EnergyHelper.getMaxAura(clone));
        double maximumMagicule = Math.max(0.0D, EnergyHelper.getMaxMagicule(clone));
        double aura = snapshot.contains("Aura") ? snapshot.getDouble("Aura") : existence.getAura();
        double magicule = snapshot.contains("Magicule") ? snapshot.getDouble("Magicule") : existence.getMagicule();
        existence.setAura(clampResource(aura, maximumAura));
        existence.setMagicule(clampResource(magicule, maximumMagicule));
        existence.markDirty();
    }

    private static double clampResource(double value, double maximum) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(maximum, value)) : 0.0D;
    }

    private static void copyEquipment(Mob clone, CompoundTag snapshot, ServerLevel level) {
        CompoundTag equipment = snapshot.getCompound("Equipment");
        if (clone instanceof CloneEntity tensuraClone) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                int slotId = tensuraClone.getSlotId(slot);
                if (slotId >= 0) tensuraClone.inventory.setItem(slotId, ItemStack.EMPTY);
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                int slotId = tensuraClone.getSlotId(slot);
                if (slotId < 0 || !equipment.contains(slot.getName())) continue;
                ItemStack.parse(level.registryAccess(), equipment.get(slot.getName()))
                        .ifPresent(stack -> tensuraClone.inventory.setItem(slotId, stack.copy()));
            }
            tensuraClone.updateContainerEquipment();
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.setItemSlot(slot, ItemStack.EMPTY);
            if (!equipment.contains(slot.getName())) continue;
            ItemStack.parse(level.registryAccess(), equipment.get(slot.getName()))
                    .ifPresent(stack -> clone.setItemSlot(slot, stack.copy()));
        }
    }

    private static void applyCurios(Mob clone, CompoundTag snapshot, ServerLevel level) {
        CompoundTag curios = snapshot.getCompound("Curios");
        if (curios.isEmpty()) return;
        CuriosApi.getCuriosInventory(clone).ifPresent(destination -> {
            for (String slotId : curios.getAllKeys()) {
                ICurioStacksHandler handler = destination.getCurios().get(slotId);
                if (handler == null) continue;
                ListTag stacks = curios.getList(slotId, net.minecraft.nbt.Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(stacks.size(), handler.getStacks().getSlots()); i++) {
                    CompoundTag stackTag = stacks.getCompound(i);
                    ItemStack stack = stackTag.isEmpty() ? ItemStack.EMPTY
                            : ItemStack.parse(level.registryAccess(), stackTag).orElse(ItemStack.EMPTY);
                    destination.setEquippedCurio(slotId, i, stack.copy());
                }
            }
        });
    }

    private static void copyAttributes(Mob clone, CompoundTag snapshot) {
        ListTag attributes = snapshot.getList("Attributes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            CompoundTag saved = attributes.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(saved.getString("Id"));
            if (id == null) continue;
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder = BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceKey.create(Registries.ATTRIBUTE, id)).orElse(null);
            if (holder == null) continue;
            AttributeInstance destination = clone.getAttribute(holder);
            if (destination != null) setEffectiveValue(destination, saved.getDouble("Value"));
        }
    }

    private static void applyEquipmentAttributes(Mob clone) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.getItemBySlot(slot).forEachModifier(slot, (holder, modifier) -> {
                AttributeInstance attribute = clone.getAttribute(holder);
                if (attribute == null) return;
                attribute.removeModifier(modifier.id());
                attribute.addTransientModifier(modifier);
            });
        }
    }

    private static void setEffectiveValue(AttributeInstance attribute, double desired) {
        if (!Double.isFinite(desired)) return;
        double added = 0.0D;
        double multipliedBase = 1.0D;
        double multipliedTotal = 1.0D;
        for (AttributeModifier modifier : attribute.getModifiers()) {
            switch (modifier.operation()) {
                case ADD_VALUE -> added += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1.0D + modifier.amount();
            }
        }
        double multiplier = multipliedBase * multipliedTotal;
        if (!Double.isFinite(multiplier) || Math.abs(multiplier) < 1.0E-9D) return;
        double base = desired / multiplier - added;
        if (Double.isFinite(base)) attribute.setBaseValue(base);
    }

    private static double applyHealthFraction(Mob clone, double fraction) {
        double originalMaxHealth = clone.getMaxHealth();
        double reducedMaxHealth = Math.max(DoubleQuirk.MINIMUM_DUPLICATE_HEALTH, originalMaxHealth * fraction);
        AttributeInstance maxHealth = clone.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(HEALTH_FRACTION_MODIFIER);
            maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_FRACTION_MODIFIER,
                    reducedMaxHealth / Math.max(1.0D, originalMaxHealth) - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        return reducedMaxHealth;
    }

    private static void prepareMetadata(Mob clone, ServerPlayer creator, UUID sourceId, boolean self,
                                        long deadline, boolean parade) {
        CompoundTag data = clone.getPersistentData();
        data.putBoolean(TAG_DOUBLE, true);
        data.putBoolean(TAG_COMMITTED, false);
        data.putUUID(TAG_CREATOR, creator.getUUID());
        data.putBoolean(TAG_SELF, self);
        data.putLong(TAG_DEADLINE, deadline);
        data.putBoolean(TAG_HOSTILE, false);
        data.putBoolean(TAG_PARADE, parade);
        data.putString(TAG_SOURCE, sourceId.toString());
        data.putString(TAG_CREATOR_TEAM, creator.getTeam() == null ? "" : creator.getTeam().getName());
        data.putLong(TAG_CREATOR_DEATH_GENERATION,
                DoubleCloneLedger.get(creator.server).creatorDeathGeneration(creator.getUUID()));
        TensuraStorages.getExistenceFrom(clone).setSkippingEPDrop(true);
        TensuraStorages.getExistenceFrom(clone).markDirty();
    }

    private static void commit(Mob clone, ServerPlayer creator, boolean countsTowardLimit) {
        clone.getPersistentData().putBoolean(TAG_COMMITTED, true);
        ensureBowAmmo(clone);
        ensureMeleeGoal(clone);
        assignOwnership(clone, creator.getUUID());
        DoubleCloneLedger.get(creator.server).register(clone.getUUID(), creator.getUUID(), countsTowardLimit);
        LOADED_CLONES.put(clone.getUUID(), clone);
    }

    private static void ensureMeleeGoal(Mob clone) {
        if (!(clone instanceof PathfinderMob pathfinder)) return;
        boolean alreadyHasMeleeGoal = pathfinder.goalSelector.getAvailableGoals().stream()
                .anyMatch(prioritized -> prioritized.getGoal() instanceof MeleeAttackGoal);
        if (!alreadyHasMeleeGoal && clone.getAttribute(Attributes.ATTACK_DAMAGE) != null
                && !(clone instanceof CloneEntity)) {
            pathfinder.goalSelector.addGoal(1, new MeleeAttackGoal(pathfinder, SUBORDINATE_MOVEMENT_SPEED, true));
        }
        if (!(clone instanceof ISubordinate) && pathfinder.goalSelector.getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof FollowCreatorGoal)) {
            pathfinder.goalSelector.addGoal(2, new FollowCreatorGoal(pathfinder));
        }
    }

    private static void assignOwnership(Mob clone, UUID owner) {
        var existence = TensuraStorages.getExistenceFrom(clone);
        existence.setTemporaryOwner(null);
        existence.setPermanentOwner(null);
        existence.setPermanentOwner(owner);
        existence.markDirty();
        if (clone instanceof ISubordinate subordinate) {
            subordinate.resetOwner(owner);
            if (owner != null) {
                SubordinateHelper.setFollow(clone);
                if (clone.getPersistentData().getBoolean(TAG_PARADE)) SubordinateHelper.setAggressive(clone);
                else SubordinateHelper.setProtect(clone);
            } else SubordinateHelper.setAggressive(clone);
        } else if (clone instanceof TamableAnimal tameable) {
            tameable.setTame(owner != null, false);
            tameable.setOwnerUUID(owner);
            tameable.setOrderedToSit(false);
        }
    }

    private static final class FollowCreatorGoal extends Goal {
        private final PathfinderMob clone;
        private ServerPlayer owner;

        private FollowCreatorGoal(PathfinderMob clone) {
            this.clone = clone;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            UUID id = creatorId(clone);
            owner = id == null || clone.level().getServer() == null ? null
                    : clone.level().getServer().getPlayerList().getPlayer(id);
            return !clone.getPersistentData().getBoolean(TAG_HOSTILE) && clone.getTarget() == null
                    && owner != null && owner.isAlive() && owner.level() == clone.level()
                    && clone.distanceToSqr(owner) > 16.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            clone.getLookControl().setLookAt(owner, 10.0F, clone.getMaxHeadXRot());
            clone.getNavigation().moveTo(owner, SUBORDINATE_MOVEMENT_SPEED);
        }

        @Override
        public void stop() {
            clone.getNavigation().stop();
            owner = null;
        }
    }

    private static void chargeAura(ServerPlayer creator, double cost) {
        var existence = TensuraStorages.getExistenceFrom(creator);
        existence.setAura(Math.max(0.0D, existence.getAura() - cost));
        existence.markDirty();
    }

    private static Vec3 paradePosition(ServerPlayer creator, int index) {
        double radius = 1.25D + Math.sqrt(index + 1.0D) * 0.85D;
        double angle = index * 2.399963229728653D;
        return creator.position().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
    }

    private static void position(Mob mob, Vec3 position) {
        mob.moveTo(position.x, position.y, position.z, mob.getYRot(), mob.getXRot());
    }

    private static LivingEntity findNearestEnemy(UUID creatorId, ServerPlayer creator, Mob clone) {
        return clone.level().getEntitiesOfClass(LivingEntity.class, clone.getBoundingBox().inflate(20.0D),
                        entity -> validEnemy(creatorId, creator, clone, entity)).stream()
                .min(Comparator.comparingDouble(clone::distanceToSqr)).orElse(null);
    }

    private static boolean validEnemy(UUID creatorId, ServerPlayer creator, Mob clone, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target instanceof Player player && player.isCreative()
                || target == clone || target.getUUID().equals(creatorId)) return false;
        if (creator != null && (creator.isAlliedTo(target) || SubordinateHelper.isAlly(creator, target))
                || clone.isAlliedTo(target) || SubordinateHelper.isAlly(clone, target)) return false;
        String creatorTeam = clone.getPersistentData().getString(TAG_CREATOR_TEAM);
        if (!creatorTeam.isBlank() && target.getTeam() != null && creatorTeam.equals(target.getTeam().getName())) return false;
        return !isDoubleDuplicate(target) || !target.getPersistentData().hasUUID(TAG_CREATOR)
                || !target.getPersistentData().getUUID(TAG_CREATOR).equals(creatorId);
    }

    public static boolean isDoubleDuplicate(Entity entity) {
        return entity.getPersistentData().getBoolean(TAG_DOUBLE)
                && entity.getPersistentData().getBoolean(TAG_COMMITTED);
    }

    private static UUID creatorId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(TAG_CREATOR) ? data.getUUID(TAG_CREATOR) : null;
    }

    private static void ensureBowAmmo(Mob mob) {
        if (mob instanceof CloneEntity clone && clone.getMainHandItem().getItem() instanceof BowItem bow
                && bow.getAllSupportedProjectiles().test(Items.ARROW.getDefaultInstance())
                && clone.getProjectile(clone.getMainHandItem()).isEmpty()) {
            clone.inventory.addItem(Items.ARROW.getDefaultInstance());
        }
    }

    private static void killDuplicate(Mob clone) {
        clone.kill();
        if (clone.isAlive()) {
            clone.setHealth(0.0F);
            clone.die(clone.damageSources().genericKill());
        }
        clone.remove(Entity.RemovalReason.KILLED);
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof AbstractArrow arrow
                && arrow.getOwner() != null && isDoubleDuplicate(arrow.getOwner())) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        if (!(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)
                || !mob.getPersistentData().getBoolean(TAG_DOUBLE)) return;
        TensuraStorages.getExistenceFrom(mob).setSkippingEPDrop(true);
        TensuraStorages.getExistenceFrom(mob).markDirty();
        double reducedMaxHealth = mob.getPersistentData().getDouble(TAG_REDUCED_MAX_HEALTH);
        if (reducedMaxHealth > 0.0D && Double.isFinite(reducedMaxHealth)) {
            DoubleMobHealthData.set(mob, (float) reducedMaxHealth);
            mob.setHealth(Math.min(mob.getHealth(), (float) reducedMaxHealth));
        }
        if (!isDoubleDuplicate(mob)) return;
        if (mob.isDeadOrDying()) {
            DoubleCloneLedger.get(level.getServer()).remove(mob.getUUID());
            return;
        }
        UUID creator = creatorId(mob);
        if (creator != null && mob.getPersistentData().getLong(TAG_CREATOR_DEATH_GENERATION)
                < DoubleCloneLedger.get(level.getServer()).creatorDeathGeneration(creator)) {
            killDuplicate(mob);
            event.setCanceled(true);
            return;
        }
        ensureMeleeGoal(mob);
        ensureBowAmmo(mob);
        LOADED_CLONES.put(mob.getUUID(), mob);
        if (creator != null) {
            assignOwnership(mob, mob.getPersistentData().getBoolean(TAG_HOSTILE) ? null : creator);
            DoubleCloneLedger.get(level.getServer()).register(mob.getUUID(), creator,
                    !mob.getPersistentData().getBoolean(TAG_SELF));
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        LOADED_CLONES.remove(mob.getUUID(), mob);
        if (isDoubleDuplicate(mob) && mob.getRemovalReason() == Entity.RemovalReason.KILLED
                && event.getLevel() instanceof ServerLevel level) {
            DoubleCloneLedger.get(level.getServer()).remove(mob.getUUID());
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            DoubleCloneLedger.get(player.server).recordCreatorDeath(player.getUUID());
            for (Mob clone : List.copyOf(LOADED_CLONES.values())) {
                if (!clone.isRemoved() && player.getUUID().equals(creatorId(clone))) killDuplicate(clone);
            }
        }
        if (!isDoubleDuplicate(event.getEntity())) return;
        LOADED_CLONES.remove(event.getEntity().getUUID());
        if (event.getEntity().level() instanceof ServerLevel level) {
            DoubleCloneLedger.get(level.getServer()).remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (isDoubleDuplicate(event.getEntity())) event.getDrops().clear();
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isDoubleDuplicate(event.getEntity())) event.setDroppedExperience(0);
    }

    @SubscribeEvent
    public static void onInteraction(PlayerInteractEvent.EntityInteract event) {
        if (!isDoubleDuplicate(event.getTarget())) return;
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSpecificInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!isDoubleDuplicate(event.getTarget())) return;
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !isDoubleDuplicate(mob)
                || mob.getPersistentData().getBoolean(TAG_HOSTILE)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        UUID creatorId = creatorId(mob);
        if (target == null || creatorId == null) return;
        ServerPlayer creator = mob.level().getServer().getPlayerList().getPlayer(creatorId);
        if (!validEnemy(creatorId, creator, mob, target)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Mob>> iterator = LOADED_CLONES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Mob> entry = iterator.next();
            Mob clone = entry.getValue();
            if (clone.isRemoved() || !(clone.level() instanceof ServerLevel level)
                    || level.getEntity(entry.getKey()) != clone) {
                iterator.remove();
                continue;
            }
            tickDuplicate(server, clone, now);
        }
    }

    private static void tickDuplicate(MinecraftServer server, Mob clone, long now) {
        ensureBowAmmo(clone);
        CompoundTag data = clone.getPersistentData();
        UUID creatorId = creatorId(clone);
        if (creatorId == null) return;
        ServerPlayer creator = server.getPlayerList().getPlayer(creatorId);
        boolean hostile = data.getBoolean(TAG_HOSTILE);
        if (!hostile && now >= data.getLong(TAG_DEADLINE)) {
            data.putBoolean(TAG_HOSTILE, true);
            assignOwnership(clone, null);
            SubordinateHelper.removeTarget(clone);
            hostile = true;
        }

        if (hostile) {
            if (now % AI_TARGET_SCAN_TICKS == 0L) updateHostileTarget(clone, creator);
            return;
        }

        LivingEntity target = getDuplicateTarget(clone);
        if (target != null && !validEnemy(creatorId, creator, clone, target)) SubordinateHelper.removeTarget(clone);

        if (data.getBoolean(TAG_PARADE) && now % AI_TARGET_SCAN_TICKS == 0L
                && (clone.getTarget() == null || !clone.getTarget().isAlive())) {
            setDuplicateTarget(clone, findNearestEnemy(creatorId, creator, clone));
        } else if (!data.getBoolean(TAG_PARADE) && creator != null && now % AI_TARGET_SCAN_TICKS == 0L) {
            LivingEntity attacker = creator.getLastHurtByMob();
            LivingEntity victim = creator.getLastHurtMob();
            LivingEntity assist = creator.getLastHurtMobTimestamp() > creator.getLastHurtByMobTimestamp()
                    ? victim : attacker;
            if (assist != null && assist.level() == clone.level()
                    && validEnemy(creatorId, creator, clone, assist)) setDuplicateTarget(clone, assist);
        }
    }

    private static void updateHostileTarget(Mob clone, ServerPlayer creator) {
        CompoundTag data = clone.getPersistentData();
        LivingEntity target = null;
        if (data.hasUUID(TAG_HOSTILE_TARGET)
                && ((ServerLevel) clone.level()).getEntity(data.getUUID(TAG_HOSTILE_TARGET)) instanceof LivingEntity saved
                && validHostileTarget(clone, saved)
                && (isDoubleDuplicate(saved) || saved.getUUID().equals(creatorId(clone)))) {
            target = saved;
        }
        if (target == null) {
            Mob otherClone = LOADED_CLONES.values().stream()
                    .filter(other -> validHostileTarget(clone, other) && isDoubleDuplicate(other)
                            && clone.distanceToSqr(other) <= HOSTILE_CLONE_TARGET_RANGE * HOSTILE_CLONE_TARGET_RANGE)
                    .min(Comparator.comparingDouble(clone::distanceToSqr)).orElse(null);
            boolean canAttackCreator = validHostileTarget(clone, creator);
            target = otherClone != null && (!canAttackCreator || clone.getRandom().nextDouble() < HOSTILE_CLONE_TARGET_CHANCE)
                    ? otherClone : canAttackCreator ? creator : null;
            if (target == null) data.remove(TAG_HOSTILE_TARGET);
            else data.putUUID(TAG_HOSTILE_TARGET, target.getUUID());
        }
        setDuplicateTarget(clone, target);
    }

    private static boolean validHostileTarget(Mob clone, LivingEntity target) {
        return target != null && target != clone && target.isAlive() && !target.isRemoved()
                && target.level() == clone.level() && !target.isSpectator()
                && (!(target instanceof Player player) || !player.isCreative());
    }

    private static LivingEntity getDuplicateTarget(Mob clone) {
        var brain = clone.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            return brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(clone.getTarget());
        }
        return clone.getTarget();
    }

    private static void setDuplicateTarget(Mob clone, LivingEntity target) {
        clone.setTarget(target);
        var brain = clone.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            if (target == null) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            else brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED_CLONES.clear();
    }
}

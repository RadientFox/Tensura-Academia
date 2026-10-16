package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.datafixers.util.Pair;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ToolArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.ToolArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ToolArms;

    private static final int ARM_CREATION = 0;
    private static final int TOOL_SLOT = 1;
    private static final int MODES = 2;

    public static final int ARMS = 4;
    public static final String ARMS_TAG = "arms";
    public static final String PARRY_ARM_TAG = "parryArm";
    public static final String PARRY_TIME_TAG = "parryTime";
    public static final int PARRY_TICKS = 6;

    private static final ResourceLocation TOOL_ARMS_ID = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tool_arms");
    private static final ResourceLocation EMPTY_TOOL = ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe");
    private static final AttachmentType<ToolStorage> TOOLS = AttachmentType.serializable(ToolStorage::new).copyOnDeath().build();
    public static final MenuType<ToolArmsMenu> MENU = new MenuType<>(ToolArmsMenu::new, FeatureFlags.DEFAULT_FLAGS);

    private static boolean parrying = false;

    public ToolArmsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TOOL_ARMS_ID, () -> TOOLS);
        event.register(Registries.MENU, TOOL_ARMS_ID, () -> MENU);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == ARM_CREATION && !entity.isShiftKeyDown() ? CONFIG.armAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ARM_CREATION -> "tool_arms.arm_creation";
            case TOOL_SLOT -> "tool_arms.tool_slot";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == TOOL_SLOT || entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getToolArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TOOL_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static int getArms(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : Mth.clamp(tag.getInt(ARMS_TAG), 0, ARMS);
    }

    public static ItemStack getTool(LivingEntity entity, int slot) {
        return slot >= 0 && slot < ARMS && entity.hasData(TOOLS) ? entity.getData(TOOLS).items.get(slot) : ItemStack.EMPTY;
    }

    public static boolean isTool(ItemStack stack) {
        return stack.has(DataComponents.TOOL);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case ARM_CREATION -> {
                if (entity.isShiftKeyDown()) {
                    removeArm(level, instance, entity);
                } else {
                    createArm(level, instance, entity);
                }
            }
            case TOOL_SLOT -> {
                if (entity instanceof ServerPlayer player) {
                    player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new ToolArmsMenu(id, inventory, new ToolContainer(player)), Component.translatable("tracadamia.skill.tool_arms")));
                }
            }
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        returnTools(entity);
    }

    // Arm Creation

    private static void createArm(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int arms = getArms(instance);
        if (arms >= ARMS) {
            fail(entity, Component.translatable("tracadamia.skill.tool_arms.max_arms", ARMS));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ARM_CREATION)) {
            return;
        }

        setArms(instance, arms + 1);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        message(entity, arms + 1);
        instance.setCoolDown(CONFIG.armCooldown, ARM_CREATION);
        instance.addMasteryPoint(entity);
    }

    private static void removeArm(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        int arms = getArms(instance);
        if (arms <= 0) {
            fail(entity, Component.translatable("tracadamia.skill.tool_arms.no_arms"));
            return;
        }

        setArms(instance, arms - 1);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
        message(entity, arms - 1);
    }

    private static void setArms(ManasSkillInstance instance, int arms) {
        instance.getOrCreateTag().putInt(ARMS_TAG, arms);
        instance.markDirty();
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled() && getArms(instance) > 0) {
            instance.addMasteryPoint(entity);
        }
    }

    private static Optional<ManasSkillInstance> getMining(Player player) {
        return getToolArms(player).filter(instance -> instance.isToggled() && getArms(instance) > 0);
    }

    private static float getHandSpeed(Player player, BlockState state) {
        float speed = player.getInventory().getDestroySpeed(state);
        return speed > 1.0F ? speed + (float) player.getAttributeValue(Attributes.MINING_EFFICIENCY) : speed;
    }

    private static float getToolSpeed(Player player, ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        if (speed > 1.0F) {
            int efficiency = EnchantmentHelper.getItemEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), tool);
            speed += efficiency > 0 ? efficiency * efficiency + 1 : 0;
        }
        return speed;
    }

    private static int getHelpingArm(Player player, ManasSkillInstance instance, BlockState state) {
        boolean handHarvests = !state.requiresCorrectToolForDrops() || player.getMainHandItem().isCorrectToolForDrops(state);
        float handSpeed = getHandSpeed(player, state);
        int best = -1;
        boolean bestHarvests = false;
        float bestSpeed = 0.0F;
        for (int slot = 0; slot < getArms(instance); slot++) {
            ItemStack tool = getTool(player, slot);
            if (tool.isEmpty()) {
                continue;
            }

            boolean harvests = tool.isCorrectToolForDrops(state);
            float speed = getToolSpeed(player, tool, state);
            if (best < 0 || harvests && !bestHarvests || harvests == bestHarvests && speed > bestSpeed) {
                best = slot;
                bestHarvests = harvests;
                bestSpeed = speed;
            }
        }

        return best >= 0 && (bestSpeed > handSpeed || bestHarvests && !handHarvests) ? best : -1;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ManasSkillInstance instance = getMining(player).orElse(null);
        if (instance == null) {
            return;
        }

        BlockState state = event.getState();
        float speed = event.getNewSpeed();
        int arm = getHelpingArm(player, instance, state);
        if (arm >= 0) {
            float hand = getHandSpeed(player, state);
            speed = speed / Math.max(0.001F, hand) * Math.max(hand, getToolSpeed(player, getTool(player, arm), state));
        }

        double boost = instance.isMastered(player) ? CONFIG.miningBoostMastered : CONFIG.miningBoost;
        event.setNewSpeed((float) (speed * (1.0D + boost * getArms(instance))));
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (event.canHarvest()) {
            return;
        }

        getMining(player).ifPresent(instance -> {
            for (int slot = 0; slot < getArms(instance); slot++) {
                if (getTool(player, slot).isCorrectToolForDrops(event.getTargetBlock())) {
                    event.setCanHarvest(true);
                    return;
                }
            }
        });
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level) || event.getState().getDestroySpeed(level, event.getPos()) <= 0.0F) {
            return;
        }

        getMining(player).ifPresent(instance -> {
            int arm = getHelpingArm(player, instance, event.getState());
            if (arm < 0) {
                return;
            }

            ItemStack tool = getTool(player, arm);
            Tool mining = tool.get(DataComponents.TOOL);
            tool.hurtAndBreak(mining == null ? 1 : mining.damagePerBlock(), level, player, item -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F));
        });
    }


    private static int rollParry(ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isToggled()) {
            return -1;
        }

        double chance = instance.isMastered(entity) ? CONFIG.parryChanceMastered : CONFIG.parryChance;
        for (int arm = 0; arm < getArms(instance); arm++) {
            if (entity.getRandom().nextDouble() < chance) {
                return arm;
            }
        }
        return -1;
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity entity, DamageSource source, float amount) {
        if (parrying || amount <= 0.0F || !(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == entity
                || source.getDirectEntity() != attacker || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        int arm = rollParry(instance, entity);
        if (arm < 0) {
            return true;
        }

        parry(level, instance, entity, arm, attacker.getBoundingBox().getCenter());
        counter(level, instance, entity, attacker, arm);
        return false;
    }

    private static void counter(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, LivingEntity attacker, int arm) {
        ItemStack tool = getTool(owner, arm);
        DamageSource source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);
        float damage = EnchantmentHelper.modifyDamage(level, tool, attacker, source, (float) MultiArms.getArmDamage(owner, tool));
        parrying = true;
        try {
            attacker.invulnerableTime = 0;
            if (attacker.hurt(source, damage) && !tool.isEmpty()) {
                tool.hurtAndBreak(1, level, owner, item -> level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F));
            }
        } finally {
            parrying = false;
        }

        Vec3 hit = attacker.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 8, attacker.getBbWidth() * 0.4D, attacker.getBbHeight() * 0.3D, attacker.getBbWidth() * 0.4D, 0.2D);
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.1F);
    }

    @Override
    public void onProjectileHit(ManasSkillInstance instance, LivingEntity entity, EntityHitResult hitResult, Projectile projectile,
                                Changeable<ProjectileDeflection> deflection, Changeable<EntityEvents.ProjectileHitResult> result) {
        if (!(entity.level() instanceof ServerLevel level) || projectile.getOwner() == entity || result.get() == EntityEvents.ProjectileHitResult.PASS) {
            return;
        }

        int arm = rollParry(instance, entity);
        if (arm < 0) {
            return;
        }

        Vec3 pos = projectile.position();
        projectile.deflect((deflected, hit, random) -> TrajectQuirk.shoveOff(deflected, entity, random), entity, entity, entity instanceof Player);
        result.set(EntityEvents.ProjectileHitResult.PASS);
        parry(level, instance, entity, arm, pos);
    }

    private static void parry(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int arm, Vec3 at) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.putInt(PARRY_ARM_TAG, arm);
        tag.putLong(PARRY_TIME_TAG, level.getGameTime());
        instance.markDirty();
        SkillAPI.getSkillsFrom(entity).markDirty();

        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.2D, 0.2D, 0.2D, 0.3D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.4F);
        instance.addMasteryPoint(entity);
    }

    public static float getParrySwing(ManasSkillInstance instance, int arm, double time) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(PARRY_TIME_TAG) || tag.getInt(PARRY_ARM_TAG) != arm) {
            return -1.0F;
        }

        double progress = (time - tag.getLong(PARRY_TIME_TAG)) / PARRY_TICKS;
        return progress < 0.0D || progress >= 1.0D ? -1.0F : (float) progress;
    }


    private static void returnTools(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.hasData(TOOLS)) {
            return;
        }

        NonNullList<ItemStack> items = entity.getData(TOOLS).items;
        for (int slot = 0; slot < ARMS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            items.set(slot, ItemStack.EMPTY);
            if (entity instanceof Player player) {
                player.getInventory().placeItemBackInInventory(stack);
            } else {
                entity.spawnAtLocation(stack);
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.hasData(TOOLS) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        NonNullList<ItemStack> items = player.getData(TOOLS).items;
        for (int slot = 0; slot < ARMS; slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3D, player.getZ(), stack.copy());
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                items.set(slot, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.hasData(TOOLS)) {
            return;
        }

        ToolStorage storage = player.getData(TOOLS);
        boolean changed = false;
        for (int slot = 0; slot < ARMS; slot++) {
            if (!ItemStack.matches(storage.items.get(slot), storage.synced[slot])) {
                storage.synced[slot] = storage.items.get(slot).copy();
                changed = true;
            }
        }

        if (changed) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, ToolsPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && target.hasData(TOOLS) && event.getEntity() instanceof ServerPlayer tracker) {
            PacketDistributor.sendToPlayer(tracker, ToolsPayload.of(target));
        }
    }

    private static void message(LivingEntity entity, int arms) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.tool_arms.arms", arms, ARMS).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    public static class ToolStorage implements INBTSerializable<CompoundTag> {
        private final NonNullList<ItemStack> items = NonNullList.withSize(ARMS, ItemStack.EMPTY);
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

    public static class ToolContainer implements Container {
        private final Player player;

        public ToolContainer(Player player) {
            this.player = player;
        }

        private NonNullList<ItemStack> items() {
            return this.player.getData(TOOLS).items;
        }

        @Override
        public int getContainerSize() {
            return ARMS;
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
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isTool(stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return player == this.player && player.isAlive() && getToolArms(player).isPresent();
        }

        @Override
        public void clearContent() {
            this.items().clear();
        }
    }

    public static class ToolSlot extends Slot {
        public ToolSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isTool(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_TOOL);
        }
    }

    public static class ToolArmsMenu extends AbstractContainerMenu {
        public static final int SLOT_X = 53;
        public static final int SLOT_Y = 20;

        private final Container tools;

        public ToolArmsMenu(int containerId, Inventory inventory) {
            this(containerId, inventory, new SimpleContainer(ARMS));
        }

        public ToolArmsMenu(int containerId, Inventory inventory, Container tools) {
            super(MENU, containerId);
            checkContainerSize(tools, ARMS);
            this.tools = tools;
            for (int slot = 0; slot < ARMS; slot++) {
                addSlot(new ToolSlot(tools, slot, SLOT_X + slot * 18, SLOT_Y));
            }

            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, row * 18 + 51));
                }
            }

            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column, 8 + column * 18, 109));
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            Slot slot = this.slots.get(index);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = slot.getItem();
            ItemStack moved = stack.copy();
            if (index < ARMS) {
                if (!moveItemStackTo(stack, ARMS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!isTool(stack) || !moveItemStackTo(stack, 0, ARMS, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            return moved;
        }

        @Override
        public boolean stillValid(Player player) {
            return this.tools.stillValid(player);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            if (!player.level().isClientSide && getToolArms(player).isEmpty()) {
                returnTools(player);
            }
        }
    }

    public record ToolsPayload(int entityId, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<ToolsPayload> TYPE = new Type<>(TOOL_ARMS_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ToolsPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ToolsPayload::entityId,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, ToolsPayload::items,
                ToolsPayload::new
        );

        public static ToolsPayload of(Player player) {
            List<ItemStack> items = new ArrayList<>();
            for (int slot = 0; slot < ARMS; slot++) {
                items.add(getTool(player, slot).copy());
            }

            return new ToolsPayload(player.getId(), items);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ToolsPayload.TYPE, ToolsPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof LivingEntity living) {
                for (int slot = 0; slot < ARMS && slot < payload.items().size(); slot++) {
                    living.getData(TOOLS).items.set(slot, payload.items().get(slot));
                }
            }
        }));
    }

}

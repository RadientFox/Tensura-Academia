package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.datafixers.util.Pair;
import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class QuadArmsOffhands {

    public static final int SLOTS = 2;

    // Slot positions in the survival inventory and the creative inventory
    private static final int[][] INVENTORY_POSITIONS = {{77, 44}, {77, 26}};
    private static final int[][] CREATIVE_POSITIONS = {{15, 6}, {15, 33}};

    private static final ResourceLocation STORAGE_ID = ResourceLocation.fromNamespaceAndPath("tracadamia", "extra_offhands");

    public static final AttachmentType<Storage> STORAGE = AttachmentType.serializable(Storage::new).copyOnDeath().build();

    @SubscribeEvent
    public static void registerStorage(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, STORAGE_ID, () -> STORAGE);
    }

    public static class Storage implements INBTSerializable<CompoundTag> {
        private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        private final ItemStack[] synced = {ItemStack.EMPTY, ItemStack.EMPTY};

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

    public static ItemStack getItem(LivingEntity entity, int slot) {
        return slot >= 0 && slot < SLOTS && entity.hasData(STORAGE) ? entity.getData(STORAGE).items.get(slot) : ItemStack.EMPTY;
    }

    public static void setItem(LivingEntity entity, int slot, ItemStack stack) {
        entity.getData(STORAGE).items.set(slot, stack);
    }

    // Give the items back when Quad Arms is lost
    public static void returnItems(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide || !player.hasData(STORAGE)) {
            return;
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(player, slot);
            if (!stack.isEmpty()) {
                setItem(player, slot, ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    // used by InventoryMenuMixin
    public static List<Slot> createSlots(Player owner) {
        OffhandContainer container = new OffhandContainer(owner);
        return List.of(
                new OffhandSlot(container, owner, 0, INVENTORY_POSITIONS[0][0], INVENTORY_POSITIONS[0][1]),
                new OffhandSlot(container, owner, 1, INVENTORY_POSITIONS[1][0], INVENTORY_POSITIONS[1][1])
        );
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
            return this.player.getData(STORAGE).items;
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
            return QuadArmsQuirk.hasQuadArms(this.owner);
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
        if (!(event.getEntity() instanceof Player player) || !player.hasData(STORAGE) || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = getItem(player, slot);
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3D, player.getZ(), stack.copy());
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                setItem(player, slot, ItemStack.EMPTY);
            }
        }
    }

    // Send the items to nearby players so they show in the lower hands
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.hasData(STORAGE)) {
            return;
        }

        Storage storage = player.getData(STORAGE);
        boolean changed = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!ItemStack.matches(storage.items.get(slot), storage.synced[slot])) {
                storage.synced[slot] = storage.items.get(slot).copy();
                changed = true;
            }
        }

        if (changed) {
            PacketDistributor.sendToPlayersTrackingEntity(player, SyncPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && target.hasData(STORAGE) && event.getEntity() instanceof ServerPlayer tracker) {
            PacketDistributor.sendToPlayer(tracker, SyncPayload.of(target));
        }
    }

    public record SyncPayload(int entityId, ItemStack first, ItemStack second) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tracadamia", "extra_offhands"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::entityId,
                ItemStack.OPTIONAL_STREAM_CODEC, SyncPayload::first,
                ItemStack.OPTIONAL_STREAM_CODEC, SyncPayload::second,
                SyncPayload::new
        );

        public static SyncPayload of(Player player) {
            return new SyncPayload(player.getId(), getItem(player, 0).copy(), getItem(player, 1).copy());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            Entity entity = context.player().level().getEntity(payload.entityId());
            if (entity instanceof LivingEntity living) {
                setItem(living, 0, payload.first());
                setItem(living, 1, payload.second());
            }
        }));
    }

}

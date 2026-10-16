package com.radient.tensuraacadamia.util;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.registry.skill.CommonSkills;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Limbs lost to Burst and Rupture
// The limbs grow back over time
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class Limbs {

    private static final QuirkSkillsConfig.Burst BURST_CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Burst;
    private static final QuirkSkillsConfig.Rupture RUPTURE_CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Rupture;

    public static final int LEFT_ARM = 0;
    public static final int RIGHT_ARM = 1;
    public static final int LEFT_LEG = 2;
    public static final int RIGHT_LEG = 3;
    public static final int COUNT = 4;
    private static final String[] NAMES = {"left_arm", "right_arm", "left_leg", "right_leg"};

    // What took the limb, each grows back on its own timer
    public static final int BURST = 0;
    public static final int RUPTURE = 1;

    // Share of a standing body that's legs, the torso drops by this much with none
    public static final float LEG_SHARE = 0.39F;
    private static final int UPDATE_TICKS = 20;

    private static final ResourceLocation LIMBS_ID = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "limbs");
    private static final ResourceLocation LEGLESS = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "burst_legless");
    private static final AttachmentType<LimbStorage> LIMBS = AttachmentType.serializable(LimbStorage::new).build();

    // Players shorter than usual on each side, sizes aren't synced
    private static final Set<UUID> SHORT_SERVER = new HashSet<>();
    private static final Set<UUID> SHORT_CLIENT = new HashSet<>();

    private Limbs() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LIMBS_ID, () -> LIMBS);
    }

    public static class LimbStorage implements INBTSerializable<CompoundTag> {
        private final boolean[] growing = new boolean[COUNT];
        private final float[] base = new float[COUNT];
        private final long[] since = new long[COUNT];
        private final float[] rate = new float[COUNT];
        private final int[] cause = new int[COUNT];

        private boolean isEmpty() {
            for (boolean limb : this.growing) {
                if (limb) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            CompoundTag tag = new CompoundTag();
            for (int limb = 0; limb < COUNT; limb++) {
                if (this.growing[limb]) {
                    CompoundTag growing = new CompoundTag();
                    growing.putFloat("base", this.base[limb]);
                    growing.putLong("since", this.since[limb]);
                    growing.putFloat("rate", this.rate[limb]);
                    growing.putInt("cause", this.cause[limb]);
                    tag.put(String.valueOf(limb), growing);
                }
            }
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
            for (int limb = 0; limb < COUNT; limb++) {
                String key = String.valueOf(limb);
                this.growing[limb] = tag.contains(key);
                CompoundTag growing = tag.getCompound(key);
                this.base[limb] = growing.getFloat("base");
                this.since[limb] = growing.getLong("since");
                this.rate[limb] = growing.getFloat("rate");
                this.cause[limb] = growing.getInt("cause");
            }
        }
    }

    // 1 is a whole limb
    // anything less thab 1 means its still growing back
    public static float getLimb(LivingEntity entity, int limb, double time) {
        if (!entity.hasData(LIMBS)) {
            return 1.0F;
        }

        LimbStorage limbs = entity.getData(LIMBS);
        if (!limbs.growing[limb]) {
            return 1.0F;
        }
        return (float) Math.min(1.0D, Math.max(0.0D, limbs.base[limb] + (time - limbs.since[limb]) * limbs.rate[limb]));
    }

    // used by LimbsClient between ticks
    public static float getLimbRendered(LivingEntity entity, int limb, float partialTick) {
        return getLimb(entity, limb, entity.level().getGameTime() + (double) partialTick);
    }

    public static boolean hasLimb(LivingEntity entity, int limb) {
        return getLimb(entity, limb, (double) entity.level().getGameTime()) >= 1.0F;
    }

    public static int countLimbs(LivingEntity entity) {
        int limbs = 0;
        for (int limb = 0; limb < COUNT; limb++) {
            if (hasLimb(entity, limb)) {
                limbs++;
            }
        }
        return limbs;
    }

    public static boolean isLegless(LivingEntity entity) {
        return !hasLimb(entity, LEFT_LEG) && !hasLimb(entity, RIGHT_LEG);
    }

    public static boolean isArmless(LivingEntity entity) {
        return !hasLimb(entity, LEFT_ARM) && !hasLimb(entity, RIGHT_ARM);
    }

    public static float getLegs(LivingEntity entity, double time) {
        return Math.max(getLimb(entity, LEFT_LEG, time), getLimb(entity, RIGHT_LEG, time));
    }

    // Prioritizes the offhand arm first
    public static int getArm(LivingEntity entity, boolean mainHand) {
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        return mainHand == rightHanded ? RIGHT_ARM : LEFT_ARM;
    }

    private static boolean hasSkill(LivingEntity entity, ManasSkill skill, boolean toggled) {
        return SkillAPI.getSkillsFrom(entity).getSkill(skill).filter(instance -> instance.getMastery() >= 0.0D && (!toggled || instance.isToggled())).isPresent();
    }

    // Arm regenerating info for rupture and burst
    private static double getRegrowSeconds(LivingEntity entity, int cause) {
        boolean rupture = cause == RUPTURE;
        double seconds = rupture ? RUPTURE_CONFIG.armRegrowSeconds : BURST_CONFIG.regrowSeconds;
        if (hasSkill(entity, CommonSkills.SELF_REGENERATION.get(), false)) {
            seconds = Math.min(seconds, rupture ? RUPTURE_CONFIG.armRegrowSecondsSelfRegeneration : BURST_CONFIG.regrowSecondsSelfRegeneration);
        }
        if (hasSkill(entity, ExtraSkills.ULTRASPEED_REGENERATION.get(), true)) {
            seconds = Math.min(seconds, rupture ? RUPTURE_CONFIG.armRegrowSecondsUltraspeedRegeneration : BURST_CONFIG.regrowSecondsUltraspeedRegeneration);
        }
        if (hasSkill(entity, ExtraSkills.INFINITE_REGENERATION.get(), true)) {
            seconds = Math.min(seconds, rupture ? RUPTURE_CONFIG.armRegrowSecondsInfiniteRegeneration : BURST_CONFIG.regrowSecondsInfiniteRegeneration);
        }
        return Math.max(0.05D, seconds);
    }

    private static float getRate(LivingEntity entity, int cause) {
        return (float) (1.0D / (getRegrowSeconds(entity, cause) * 20.0D));
    }

    public static void loseLimb(LivingEntity entity, int limb, int cause) {
        LimbStorage limbs = entity.getData(LIMBS);
        limbs.growing[limb] = true;
        limbs.base[limb] = 0.0F;
        limbs.since[limb] = entity.level().getGameTime();
        limbs.rate[limb] = getRate(entity, cause);
        limbs.cause[limb] = cause;
        sync(entity);
    }

    public static void restore(LivingEntity entity) {
        if (entity.hasData(LIMBS)) {
            entity.removeData(LIMBS);
            Modifiers.set(entity, Attributes.MOVEMENT_SPEED, LEGLESS, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            sync(entity);
        }
    }

    // Update the regrowth so that its proper with the visual
    // Side note, might make it so losing limbs temporarily reduces max HP?
    private static void update(LivingEntity entity) {
        if (!entity.hasData(LIMBS)) {
            return;
        }

        LimbStorage limbs = entity.getData(LIMBS);
        long time = entity.level().getGameTime();
        boolean changed = false;
        for (int limb = 0; limb < COUNT; limb++) {
            if (!limbs.growing[limb]) {
                continue;
            }

            float rate = getRate(entity, limbs.cause[limb]);
            float grown = getLimb(entity, limb, (double) time);
            if (grown >= 1.0F) {
                limbs.growing[limb] = false;
                changed = true;
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.8F);
                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("tracadamia.skill.burst.limb_regrown", Component.translatable("tracadamia.skill.burst.limb." + NAMES[limb])).withStyle(ChatFormatting.GREEN), true);
                }
            } else if (Math.abs(limbs.rate[limb] - rate) > 1.0E-7F) {
                limbs.base[limb] = grown;
                limbs.since[limb] = time;
                limbs.rate[limb] = rate;
                changed = true;
            }
        }

        if (limbs.isEmpty()) {
            entity.removeData(LIMBS);
            Modifiers.set(entity, Attributes.MOVEMENT_SPEED, LEGLESS, 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            changed = true;
        }

        if (changed) {
            sync(entity);
        }
    }

    // Client syncing the limbs
    private static void sync(LivingEntity entity) {
        if (!entity.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, LimbsPayload.of(entity));
        }
    }

    // Prevents non-existent limbs from using things
    private static void freeHand(Player player, boolean mainHand) {
        Inventory inventory = player.getInventory();
        ItemStack stack = mainHand ? inventory.getSelected() : inventory.offhand.get(0);
        if (stack.isEmpty()) {
            return;
        }

        if (mainHand) {
            inventory.items.set(inventory.selected, ItemStack.EMPTY);
        } else {
            inventory.offhand.set(0, ItemStack.EMPTY);
        }

        int slot = -1;
        for (int i = Inventory.getSelectionSize(); i < inventory.items.size() && slot < 0; i++) {
            if (inventory.items.get(i).isEmpty()) {
                slot = i;
            }
        }
        for (int i = 0; i < Inventory.getSelectionSize() && slot < 0; i++) {
            if (i != inventory.selected && inventory.items.get(i).isEmpty()) {
                slot = i;
            }
        }

        if (slot >= 0) {
            inventory.items.set(slot, stack);
        } else {
            player.drop(stack, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.hasData(LIMBS)) {
            if (player.level().getGameTime() % UPDATE_TICKS == 0L) {
                update(player);
            }

            if (player.isAlive() && !player.isSpectator()) {
                if (!hasLimb(player, getArm(player, false))) {
                    freeHand(player, false);
                }
                if (!hasLimb(player, getArm(player, true))) {
                    freeHand(player, true);
                }
            }
        }

        updateSize(player);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player || entity.level().isClientSide || !entity.hasData(LIMBS)) {
            return;
        }

        if (entity.level().getGameTime() % UPDATE_TICKS == 0L) {
            update(entity);
        }

        if (entity.hasData(LIMBS)) {
            Modifiers.set(entity, Attributes.MOVEMENT_SPEED, LEGLESS, isLegless(entity) ? -1.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    private static float getHeightShare(LivingEntity entity) {
        return entity.hasData(LIMBS) ? 1.0F - LEG_SHARE * (1.0F - getLegs(entity, (double) entity.level().getGameTime())) : 1.0F;
    }

    private static void updateSize(Player player) {
        Set<UUID> shorter = player.level().isClientSide ? SHORT_CLIENT : SHORT_SERVER;
        float share = getHeightShare(player);
        if (share >= 1.0F && !shorter.contains(player.getUUID())) {
            return;
        }

        shorter.add(player.getUUID());
        float expected = player.getDimensions(player.getPose()).height() * share;
        if (Math.abs(player.getBbHeight() - expected) > 0.01F) {
            player.refreshDimensions();
        }

        if (share >= 1.0F) {
            shorter.remove(player.getUUID());
        }
    }

    // used by LimbsClient
    public static void clearClientSizes() {
        SHORT_CLIENT.clear();
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level().isClientSide ? SHORT_CLIENT : SHORT_SERVER).contains(player.getUUID())) {
            return;
        }

        float share = getHeightShare(player);
        if (share < 1.0F) {
            EntityDimensions size = event.getNewSize();
            event.setNewSize(size.scale(1.0F, share));
        }
    }

    private static void blockArmless(Player player, ICancellableEvent event) {
        if (player.hasData(LIMBS) && isArmless(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        blockArmless(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        blockArmless(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        blockArmless(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        blockArmless(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onBreakBlock(PlayerInteractEvent.LeftClickBlock event) {
        blockArmless(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity target && target.hasData(LIMBS) && event.getEntity() instanceof ServerPlayer tracker) {
            PacketDistributor.sendToPlayer(tracker, LimbsPayload.of(target));
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(LIMBS)) {
            PacketDistributor.sendToPlayer(player, LimbsPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(LIMBS)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, LimbsPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasData(LIMBS)) {
            PacketDistributor.sendToPlayer(player, LimbsPayload.of(player));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SHORT_SERVER.clear();
    }

    public record LimbsPayload(int entityId, CompoundTag limbs) implements CustomPacketPayload {
        public static final Type<LimbsPayload> TYPE = new Type<>(LIMBS_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, LimbsPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LimbsPayload::entityId,
                ByteBufCodecs.COMPOUND_TAG, LimbsPayload::limbs,
                LimbsPayload::new
        );

        public static LimbsPayload of(LivingEntity entity) {
            return new LimbsPayload(entity.getId(), entity.hasData(LIMBS) ? entity.getData(LIMBS).serializeNBT(entity.registryAccess()) : new CompoundTag());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(LimbsPayload.TYPE, LimbsPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player().level().getEntity(payload.entityId()) instanceof LivingEntity living) {
                if (payload.limbs().isEmpty()) {
                    living.removeData(LIMBS);
                } else {
                    living.getData(LIMBS).deserializeNBT(living.registryAccess(), payload.limbs());
                }
            }
        }));
    }

}

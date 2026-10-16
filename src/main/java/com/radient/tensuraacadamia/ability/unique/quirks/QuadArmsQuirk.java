package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.attribute.api.ManasCoreAttributes;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class QuadArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.QuadArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).QuadArms;

    private static final int HOLD = 0;
    private static final double TARGET_RAY_OFFSET = 1.0D;

    private static final int SLOTS = 2;
    private static final int LOWER_LEFT_SLOT = 0;
    private static final int LOWER_RIGHT_SLOT = 1;

    private static final String HOLD_TARGET_TAG = "holdTarget";
    private static final String QUAD_SHIELD_SLOT_TAG = "tracadamia_quad_shield_slot";
    private static final String IMPACT_SWING_TAG = "impactSwing";
    private static final String IMPACT_SWING_RIGHT_TAG = "impactSwingRight";

    private static final ResourceLocation MINING_EFFICIENCY = ResourceLocation.fromNamespaceAndPath("tracadamia", "quad_arms_mining_efficiency");
    private static final ResourceLocation SWIM_SPEED = ResourceLocation.fromNamespaceAndPath("tracadamia", "quad_arms_swim_speed");

    public QuadArmsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == HOLD ? "quad_arms.hold" : super.getModeId(instance, mode);
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    // Quad Arms

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
        if (isHolding(instance) && !TickingSkill.isTickingSkill(entity, this, HOLD)) {
            endHold(instance, entity);
        }

        if (!instance.isToggled()) {
            return;
        }

        applyMiningEfficiency(entity);

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        clearHold(instance, entity);
    }

    private static Optional<ManasSkillInstance> getQuadArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.QUAD_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static boolean hasQuadArms(LivingEntity entity) {
        return getQuadArms(entity).isPresent();
    }

    // used by MultiArms
    public static int getArmSlots(LivingEntity entity) {
        return hasQuadArms(entity) ? SLOTS : 0;
    }

    // Mining Efficiency

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        applyMiningEfficiency(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        AttributeInstance breakSpeed = entity.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeed != null) {
            breakSpeed.removeModifier(MINING_EFFICIENCY);
        }

        AttributeInstance swimSpeed = entity.getAttribute(ManasCoreAttributes.SWIM_SPEED_MULTIPLIER);
        if (swimSpeed != null) {
            swimSpeed.removeModifier(SWIM_SPEED);
        }
    }

    private static void applyMiningEfficiency(LivingEntity entity) {
        AttributeInstance breakSpeed = entity.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (breakSpeed != null && !breakSpeed.hasModifier(MINING_EFFICIENCY)) {
            breakSpeed.addOrReplacePermanentModifier(new AttributeModifier(MINING_EFFICIENCY, CONFIG.miningSpeedMultiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        AttributeInstance swimSpeed = entity.getAttribute(ManasCoreAttributes.SWIM_SPEED_MULTIPLIER);
        if (swimSpeed != null && !swimSpeed.hasModifier(SWIM_SPEED)) {
            swimSpeed.addOrReplacePermanentModifier(new AttributeModifier(SWIM_SPEED, CONFIG.swimSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // Double Impact

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || isHolding(instance) || MultiArms.isArmHitting() || owner.level().isClientSide) {
            return true;
        }

        if (source.getDirectEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        if (MultiArms.hasPendingImpact(instance, owner)) {
            return true;
        }

        float leftDamage = (float) MultiArms.getArmDamage(owner, MultiArms.getExtraOffhand(owner, LOWER_LEFT_SLOT));
        if (leftDamage > 0.0F) {
            MultiArms.queueImpact(instance, owner, target, leftDamage, CONFIG.doubleImpactDelay, IMPACT_SWING_TAG);
        }

        // Triple Impact
        float rightDamage = instance.isMastered(owner) ? (float) MultiArms.getArmDamage(owner, MultiArms.getExtraOffhand(owner, LOWER_RIGHT_SLOT)) : 0.0F;
        if (rightDamage > 0.0F) {
            MultiArms.queueImpact(instance, owner, target, rightDamage, CONFIG.doubleImpactDelay * 2, IMPACT_SWING_RIGHT_TAG);
        }

        return true;
    }

    // used by QuadArmsClient
    public static long getImpactSwingTime(ManasSkillInstance instance, boolean rightArm) {
        return MultiArms.getSwingTime(instance, rightArm ? IMPACT_SWING_RIGHT_TAG : IMPACT_SWING_TAG);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }

        returnQuadShield(player);
    }

    private static int findQuadShield(Player player) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = MultiArms.getExtraOffhand(player, slot);
            if (stack.canPerformAction(ItemAbilities.SHIELD_BLOCK) && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                return slot;
            }
        }

        return -1;
    }

    // used by QuadArmsClient
    public static boolean canUseQuadShield(Player player) {
        Optional<ManasSkillInstance> quadArms = getQuadArms(player);
        return quadArms.isPresent() && !isHolding(quadArms.get()) && !player.isUsingItem() && player.getOffhandItem().isEmpty() && findQuadShield(player) >= 0;
    }

    private static void useQuadShield(ServerPlayer player) {
        if (!canUseQuadShield(player)) {
            return;
        }

        int slot = findQuadShield(player);
        player.setItemInHand(InteractionHand.OFF_HAND, MultiArms.getExtraOffhand(player, slot).copy());
        MultiArms.setExtraOffhand(player, slot, ItemStack.EMPTY);
        player.getPersistentData().putInt(QUAD_SHIELD_SLOT_TAG, slot);
        player.startUsingItem(InteractionHand.OFF_HAND);
    }

    // Put the shield back once blocking stops
    private static void returnQuadShield(Player player) {
        if (!player.getPersistentData().contains(QUAD_SHIELD_SLOT_TAG)) {
            return;
        }

        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return;
        }

        int slot = player.getPersistentData().getInt(QUAD_SHIELD_SLOT_TAG);
        player.getPersistentData().remove(QUAD_SHIELD_SLOT_TAG);

        ItemStack shield = player.getOffhandItem();
        if (!shield.canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            return;
        }

        int target = slot < SLOTS && MultiArms.getExtraOffhand(player, slot).isEmpty() ? slot : -1;
        for (int other = 0; target < 0 && other < SLOTS; other++) {
            if (MultiArms.getExtraOffhand(player, other).isEmpty()) {
                target = other;
            }
        }

        if (target >= 0) {
            MultiArms.setExtraOffhand(player, target, shield.copy());
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    public record QuadShieldPayload() implements CustomPacketPayload {
        public static final Type<QuadShieldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("tracadamia", "quad_shield"));
        public static final StreamCodec<ByteBuf, QuadShieldPayload> STREAM_CODEC = StreamCodec.unit(new QuadShieldPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(QuadShieldPayload.TYPE, QuadShieldPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        useQuadShield(player);
                    }
                }));
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != HOLD) {
            return;
        }

        clearHold(instance, entity);

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.holdRange + TARGET_RAY_OFFSET, false, false);
        if (target == null || target == entity) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tensura.targeting.not_targeted").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (!MultiArms.canGrab(target)) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.arms.boss").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        instance.getOrCreateTag().putUUID(HOLD_TARGET_TAG, target.getUUID());
        instance.markDirty();
        entity.swing(InteractionHand.MAIN_HAND, true);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != HOLD) {
            return false;
        }

        LivingEntity target = getHoldTarget(instance, entity);
        if (target == null) {
            endHold(instance, entity);
            return false;
        }

        MultiArms.holdAt(entity, target, new Vec3(0.0D, -target.getBbHeight() * 0.5D, CONFIG.holdDistance + target.getBbWidth() * 0.5D), MultiArms.HOLD_LOOK);

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == HOLD) {
            endHold(instance, entity);
        }
    }

    private static void endHold(ManasSkillInstance instance, LivingEntity entity) {
        if (isHolding(instance)) {
            clearHold(instance, entity);
            instance.setCoolDown(CONFIG.holdCooldown, HOLD);
        }
    }

    public static double getHoldDistance() {
        return CONFIG.holdDistance;
    }

    // used by QuadArmsClient
    public static boolean isHolding(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(HOLD_TARGET_TAG);
    }

    private static @Nullable LivingEntity getHoldTarget(ManasSkillInstance instance, LivingEntity entity) {
        return MultiArms.getHeldTarget(instance, entity, HOLD_TARGET_TAG);
    }

    private static void clearHold(ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, HOLD_TARGET_TAG);
    }

}

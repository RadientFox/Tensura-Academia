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
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class QuadArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.QuadArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).QuadArms;

    private static final int HOLD = 0;

    private static final String HOLD_TARGET_TAG = "holdTarget";
    private static final String HELD_BY_TAG = "tracadamia_quad_held_by";
    private static final String HELD_UNTIL_TAG = "tracadamia_quad_held_until";
    private static final String QUAD_SHIELD_SLOT_TAG = "tracadamia_quad_shield_slot";
    private static final String IMPACT_SWING_TAG = "impactSwing";

    private static final ResourceLocation MINING_EFFICIENCY = ResourceLocation.fromNamespaceAndPath("tracadamia", "quad_arms_mining_efficiency");
    private static final ResourceLocation SWIM_SPEED = ResourceLocation.fromNamespaceAndPath("tracadamia", "quad_arms_swim_speed");

    private static final List<PendingImpact> PENDING_IMPACTS = new ArrayList<>();
    private static boolean otherArmHitting = false;

    private record PendingImpact(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, long hitTime) {}

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
            clearHold(instance, entity);
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
        QuadArmsOffhands.returnItems(entity);
    }

    private static Optional<ManasSkillInstance> getQuadArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.QUAD_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // used by QuadArmsOffhands
    public static boolean hasQuadArms(LivingEntity entity) {
        return getQuadArms(entity).isPresent();
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
        if (!instance.isToggled() || isHolding(instance) || otherArmHitting || owner.level().isClientSide) {
            return true;
        }

        if (source.getDirectEntity() != owner || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        long time = owner.level().getGameTime();
        for (PendingImpact impact : PENDING_IMPACTS) {
            if (impact.owner() == owner && impact.hitTime() == time) {
                return true;
            }
        }

        float damage = (float) getOtherArmDamage(owner);
        if (damage > 0.0F) {
            PENDING_IMPACTS.add(new PendingImpact(instance, owner, target, damage, time));
        }

        return true;
    }

    // Attack damage for the other arm
    private static double getOtherArmDamage(LivingEntity owner) {
        AttributeInstance attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 0.0D;
        }

        Set<ResourceLocation> heldWeapon = getAttackModifiers(owner.getMainHandItem()).stream().map(AttributeModifier::id).collect(Collectors.toSet());
        List<AttributeModifier> modifiers = new ArrayList<>(attack.getModifiers().stream().filter(modifier -> !heldWeapon.contains(modifier.id())).toList());
        modifiers.addAll(getQuadWeaponModifiers(owner));

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

    // Other arm follow up hits
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_IMPACTS.isEmpty()) {
            return;
        }

        List<PendingImpact> ready = PENDING_IMPACTS.stream()
                .filter(impact -> !impact.owner().isAlive() || impact.owner().level().getGameTime() - impact.hitTime() >= CONFIG.doubleImpactDelay)
                .toList();

        PENDING_IMPACTS.removeAll(ready);
        ready.forEach(QuadArmsQuirk::hitWithOtherArm);
    }

    private static void hitWithOtherArm(PendingImpact impact) {
        LivingEntity owner = impact.owner();
        LivingEntity target = impact.target();
        if (!owner.isAlive() || !target.isAlive() || owner.isRemoved() || target.isRemoved() || owner.level() != target.level()) {
            return;
        }

        DamageSource source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);

        // Lower left arm swing
        impact.instance().getOrCreateTag().putLong(IMPACT_SWING_TAG, owner.level().getGameTime());
        impact.instance().markDirty();
        SkillAPI.getSkillsFrom(owner).markDirty();

        // Keep the first hit's iframes
        int invulnerableTime = target.invulnerableTime;
        otherArmHitting = true;
        try {
            target.invulnerableTime = 0;
            if (target.hurt(source, impact.damage()) && owner.getRandom().nextBoolean()) {
                impact.instance().addMasteryPoint(owner);
            }
        } finally {
            otherArmHitting = false;
            target.invulnerableTime = invulnerableTime;
        }
    }


    // used by QuadArmsClient
    public static ItemStack getExtraOffhand(LivingEntity entity, int slot) {
        return QuadArmsOffhands.getItem(entity, slot);
    }

    // used by QuadArmsClient
    public static long getImpactSwingTime(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null || !tag.contains(IMPACT_SWING_TAG) ? Long.MIN_VALUE : tag.getLong(IMPACT_SWING_TAG);
    }

    private static List<AttributeModifier> getQuadWeaponModifiers(LivingEntity entity) {
        for (int slot = 0; slot < QuadArmsOffhands.SLOTS; slot++) {
            List<AttributeModifier> modifiers = getAttackModifiers(QuadArmsOffhands.getItem(entity, slot));
            if (!modifiers.isEmpty()) {
                return modifiers;
            }
        }

        return List.of();
    }

    private static int findQuadShield(Player player) {
        for (int slot = 0; slot < QuadArmsOffhands.SLOTS; slot++) {
            ItemStack stack = QuadArmsOffhands.getItem(player, slot);
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
        player.setItemInHand(InteractionHand.OFF_HAND, QuadArmsOffhands.getItem(player, slot).copy());
        QuadArmsOffhands.setItem(player, slot, ItemStack.EMPTY);
        player.getPersistentData().putInt(QUAD_SHIELD_SLOT_TAG, slot);
        player.startUsingItem(InteractionHand.OFF_HAND);
    }

    // Put the shield back once blocking stops
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.getPersistentData().contains(QUAD_SHIELD_SLOT_TAG)) {
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

        int target = slot < QuadArmsOffhands.SLOTS && QuadArmsOffhands.getItem(player, slot).isEmpty() ? slot : -1;
        for (int other = 0; target < 0 && other < QuadArmsOffhands.SLOTS; other++) {
            if (QuadArmsOffhands.getItem(player, other).isEmpty()) {
                target = other;
            }
        }

        if (target >= 0) {
            QuadArmsOffhands.setItem(player, target, shield.copy());
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

    @EventBusSubscriber(modid = TensuraAcadamia.MODID)
    public static class Network {
        @SubscribeEvent
        public static void registerPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(QuadShieldPayload.TYPE, QuadShieldPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    useQuadShield(player);
                }
            }));
        }
    }


    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != HOLD) {
            return;
        }

        clearHold(instance, entity);

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.holdRange, false, false);
        if (target == null || target == entity) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tensura.targeting.not_targeted").withStyle(ChatFormatting.RED), true);
            }
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
            clearHold(instance, entity);
            return false;
        }

        Vec3 look = entity.getViewVector(1.0F);
        Vec3 holdPos = entity.getEyePosition()
                .add(look.scale(CONFIG.holdDistance + target.getBbWidth() * 0.5D))
                .subtract(0.0D, target.getBbHeight() * 0.5D, 0.0D);

        if (target.position().distanceToSqr(holdPos) > 0.0025D) {
            target.teleportTo(holdPos.x, holdPos.y, holdPos.z);
        }

        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
        target.getPersistentData().putUUID(HELD_BY_TAG, entity.getUUID());
        target.getPersistentData().putLong(HELD_UNTIL_TAG, target.level().getGameTime() + 2L);

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == HOLD) {
            clearHold(instance, entity);
        }
    }

    // used by QuadArmsClient
    public static boolean isHolding(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(HOLD_TARGET_TAG);
    }

    private static @Nullable LivingEntity getHoldTarget(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(HOLD_TARGET_TAG) || !(entity.level() instanceof ServerLevel level)) {
            return null;
        }

        if (!(level.getEntity(tag.getUUID(HOLD_TARGET_TAG)) instanceof LivingEntity target) || !target.isAlive() || target.distanceToSqr(entity) > 64.0D) {
            return null;
        }

        return target;
    }

    private static void clearHold(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(HOLD_TARGET_TAG)) {
            return;
        }

        LivingEntity target = getHoldTarget(instance, entity);
        if (target != null) {
            target.getPersistentData().remove(HELD_BY_TAG);
            target.getPersistentData().remove(HELD_UNTIL_TAG);
        }

        tag.remove(HOLD_TARGET_TAG);
        instance.markDirty();
    }

    private static boolean isHeldBy(LivingEntity target, LivingEntity holder) {
        CompoundTag data = target.getPersistentData();
        return data.hasUUID(HELD_BY_TAG) && data.getUUID(HELD_BY_TAG).equals(holder.getUUID()) && data.getLong(HELD_UNTIL_TAG) >= target.level().getGameTime();
    }

    // Held entities cannot hit the holder
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isHeldBy(attacker, event.getEntity())) {
            event.setCanceled(true);
        }
    }

}

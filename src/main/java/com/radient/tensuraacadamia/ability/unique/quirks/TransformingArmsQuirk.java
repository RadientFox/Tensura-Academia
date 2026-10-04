package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TransformingArmsQuirk extends Skill {

    private static final QuirkSkillsConfig.TransformingArms CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).TransformingArms;

    private static final int TRANSFORM = 0;

    public static final int NONE = 0;
    public static final int CLAWS = 1;
    public static final int AXE = 2;
    public static final int SPEAR = 3;
    private static final String[] FORMS = {"none", "claws", "axe", "spear"};

    private static final String FORM_TAG = "form";
    private static final ResourceLocation ARMS = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "transforming_arms");

    private static boolean sweeping = false;

    public TransformingArmsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return entity.isShiftKeyDown() ? 0.0D : CONFIG.transformAuraCost;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == TRANSFORM ? "transforming_arms.transform" : super.getModeId(instance, mode);
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getTransformingArms(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TRANSFORMING_ARMS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static int getForm(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? NONE : Mth.clamp(tag.getInt(FORM_TAG), NONE, SPEAR);
    }

    public static int getForm(LivingEntity entity) {
        return getTransformingArms(entity).map(TransformingArmsQuirk::getForm).orElse(NONE);
    }

    private static void setForm(ManasSkillInstance instance, int form) {
        instance.getOrCreateTag().putInt(FORM_TAG, form);
        instance.markDirty();
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        int form = getForm(instance);
        boolean mastered = instance.isMastered(entity);
        double damage = switch (form) {
            case CLAWS -> mastered ? CONFIG.clawDamageMastered : CONFIG.clawDamage;
            case AXE -> mastered ? CONFIG.axeDamageMastered : CONFIG.axeDamage;
            case SPEAR -> mastered ? CONFIG.spearDamageMastered : CONFIG.spearDamage;
            default -> 0.0D;
        };

        AttributeInstance attackSpeed = entity.getAttribute(Attributes.ATTACK_SPEED);
        double axeSpeed = attackSpeed == null ? 0.0D : CONFIG.axeAttackSpeed - attackSpeed.getBaseValue();
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, ARMS, damage, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, TensuraAttributes.PHYSICAL_RESIST_DEGRADATION, ARMS, form == CLAWS ? 1.0D : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_SPEED, ARMS, form == AXE ? axeSpeed : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ENTITY_INTERACTION_RANGE, ARMS, form == SPEAR ? CONFIG.spearReach : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    // Transform | crouch to untransform
    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != TRANSFORM || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        int form = getForm(instance);
        if (entity.isShiftKeyDown()) {
            if (form != NONE) {
                setForm(instance, NONE);
                update(instance, entity);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, TRANSFORM)) {
            return;
        }

        int next = form == SPEAR ? CLAWS : form + 1;
        setForm(instance, next);
        update(instance, entity);
        if (entity instanceof Player player) {
            emptyHands(player);
            Component name = Component.translatable("tracadamia.skill.transforming_arms.form." + FORMS[next]);
            player.displayClientMessage(Component.translatable("tracadamia.skill.transforming_arms.transformed", name).withStyle(ChatFormatting.GOLD), true);
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
        instance.setCoolDown(CONFIG.transformCooldown, TRANSFORM);
        instance.addMasteryPoint(entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (getForm(instance) != NONE) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        setForm(instance, NONE);
        update(instance, entity);
    }

    // Transformed hands can't hold anything
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && getForm(player) != NONE) {
            emptyHands(player);
        }
    }

    private static void emptyHands(Player player) {
        stash(player, player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        stash(player, player.getOffhandItem());
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        for (int slot = 0; slot < MultiArms.getSlotCount(player); slot++) {
            ItemStack held = MultiArms.getExtraOffhand(player, slot);
            if (!held.isEmpty()) {
                MultiArms.setExtraOffhand(player, slot, ItemStack.EMPTY);
                stash(player, held);
            }
        }
    }

    private static void stash(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        Inventory inventory = player.getInventory();
        for (int slot = Inventory.getSelectionSize(); slot < inventory.items.size(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, stack.copy());
                return;
            }
        }

        player.drop(stack.copy(), false);
    }

    // Axe and spear sweep on melee hit
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMeleeHit(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (sweeping || target.level().isClientSide || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
                || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || ((TensuraDamageSource) source).tensura$getAbilityInstance() != null) {
            return;
        }

        int form = getForm(attacker);
        if (form != AXE && form != SPEAR || !(target.level() instanceof ServerLevel level)) {
            return;
        }

        float damage = (float) (event.getAmount() * CONFIG.sweepDamage);
        sweeping = true;
        try {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D),
                    other -> other != attacker && other != target && other.isAlive() && !other.isSpectator() && !attacker.isAlliedTo(other) && !MultiArms.isHoldPair(attacker, other))) {
                other.knockback(0.4D, Mth.sin(attacker.getYRot() * Mth.DEG_TO_RAD), -Mth.cos(attacker.getYRot() * Mth.DEG_TO_RAD));
                other.hurt(source, damage);
            }
        } finally {
            sweeping = false;
        }

        if (attacker instanceof Player player) {
            player.sweepAttack();
        }
        level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // Axe arms breaks shields
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getOriginalBlock()
                && event.getDamageSource().getDirectEntity() instanceof LivingEntity attacker && getForm(attacker) == AXE) {
            player.disableShield();
        }
    }

    // Axe arms chop logs like a diamond axe
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getState().is(BlockTags.LOGS) && getForm(event.getEntity()) == AXE) {
            event.setNewSpeed(event.getNewSpeed() * (float) CONFIG.axeBreakSpeed);
        }
    }

}

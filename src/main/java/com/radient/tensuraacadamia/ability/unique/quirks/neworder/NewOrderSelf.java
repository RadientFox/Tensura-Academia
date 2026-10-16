package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ZeroGravityQuirk;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.Modifiers;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// New Order's rules on the user
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderSelf {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private static final QuirkSkillsConfig.Wing WING_CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Wing;
    static final String FLIGHT_TAG = "grantedFlight";
    private static final String FLYING_TAG = "flying";
    private static final String FLY_SPEED_TAG = "flySpeed";
    private static final ResourceLocation ENHANCE_DAMAGE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_enhance_damage");
    private static final ResourceLocation ENHANCE_ARMOR = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_enhance_armor");
    private static final ResourceLocation ENHANCE_SCALE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_enhance_scale");
    private static final ResourceLocation UNSEEN = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_unseen");
    private static final ResourceLocation SEE_ALL = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_see_all");

    private NewOrderSelf() {
    }

    private static Set<NewOrderRule> getSelfRules(ManasSkillInstance instance) {
        Set<NewOrderRule> self = EnumSet.noneOf(NewOrderRule.class);
        for (Tag entry : NewOrderRules.getRuleList(instance)) {
            NewOrderRule rule = NewOrderRules.getRule((CompoundTag) entry);
            if (rule != null && rule.getKind() == NewOrderRule.USER) {
                self.add(rule);
            }
        }
        return self;
    }

    static void refreshSelf(LivingEntity entity, ManasSkillInstance instance) {
        applySelf(entity, instance, NewOrderSeals.isSealed((Skill) instance.getSkill(), entity) ? EnumSet.noneOf(NewOrderRule.class) : getSelfRules(instance));
    }

    static void applySelf(LivingEntity entity, ManasSkillInstance instance, Set<NewOrderRule> self) {
        enhance(entity, self.contains(NewOrderRule.ENHANCE));
        Modifiers.set(entity, TensuraAttributes.PRESENCE_CONCEALMENT, UNSEEN, self.contains(NewOrderRule.UNSEEN) ? CONFIG.unseenConcealment : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, TensuraAttributes.PRESENCE_SENSE, SEE_ALL, self.contains(NewOrderRule.SEE_ALL) ? CONFIG.seeAllPresenceSense : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        if (entity instanceof ServerPlayer player) {
            fly(player, instance, self.contains(NewOrderRule.FLY));
        }
        if (self.contains(NewOrderRule.GRAVITY)) {
            ZeroGravityQuirk.lendFloat(entity);
        }
        if (self.contains(NewOrderRule.REPEL)) {
            repel(entity);
        }
        if (self.contains(NewOrderRule.FLAMES) && entity.isOnFire()) {
            entity.clearFire();
        }
        NewOrderWill.heal(entity, self.contains(NewOrderRule.HEAL));
        if (self.contains(NewOrderRule.CLEAR_MIND)) {
            NewOrderWill.clearMind(entity);
        }
        if (self.contains(NewOrderRule.NO_AGGRO) && entity.tickCount % 20 == 0) {
            NewOrderWill.calm(entity);
        }
    }

    private static void enhance(LivingEntity entity, boolean on) {
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, ENHANCE_DAMAGE, on ? CONFIG.enhanceAttackDamage : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ARMOR, ENHANCE_ARMOR, on ? CONFIG.enhanceArmor : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.SCALE, ENHANCE_SCALE, on ? CONFIG.enhanceScale : 0.0D, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void fly(ServerPlayer player, ManasSkillInstance instance, boolean on) {
        CompoundTag tag = instance.getOrCreateTag();
        boolean changed = false;
        if (on && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            tag.putBoolean(FLIGHT_TAG, true);
            changed = true;
        } else if (!on && tag.getBoolean(FLIGHT_TAG)) {
            tag.remove(FLIGHT_TAG);
            changed = true;
            if (!SkillUtils.canFlyLegit(player)) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }

        boolean flying = on && player.getAbilities().flying;
        double speed = on ? WING_CONFIG.boostSpeed : 0.0D;
        if (tag.getBoolean(FLYING_TAG) != flying || tag.getDouble(FLY_SPEED_TAG) != speed) {
            tag.putBoolean(FLYING_TAG, flying);
            tag.putDouble(FLY_SPEED_TAG, speed);
            changed = true;
        }
        if (changed) {
            NewOrderUtil.dirty(instance, player);
        }
    }

    // used by WingQuirk and WingClient
    // Just grants wing flight
    public static boolean isFlyBoosting(Player player) {
        if (!player.isSprinting() || player.isPassenger() || !NewOrderRules.hasRule(player, NewOrderRule.FLY)) {
            return false;
        }
        if (player.level().isClientSide && !player.isLocalPlayer()) {
            CompoundTag tag = NewOrderQuirk.getNewOrder(player).map(ManasSkillInstance::getTag).orElse(null);
            return tag != null && tag.getBoolean(FLYING_TAG);
        }
        return player.getAbilities().flying;
    }

    public static double getFlySpeed(Player player) {
        CompoundTag tag = NewOrderQuirk.getNewOrder(player).map(ManasSkillInstance::getTag).orElse(null);
        return tag == null ? 0.0D : tag.getDouble(FLY_SPEED_TAG);
    }

    public static boolean canWalkOnWater(Player player) {
        return NewOrderRules.hasRule(player, NewOrderRule.WATER_WALK);
    }

    private static void repel(LivingEntity entity) {
        boolean cleansed = false;
        for (MobEffectInstance effect : List.copyOf(entity.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                entity.removeEffect(effect.getEffect());
                cleansed = true;
            }
        }
        if (cleansed && entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY(0.5D), entity.getZ(), 10, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.05D);
            NewOrderUtil.playSound(level, entity.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        }
    }

    @SubscribeEvent
    public static void onRepelEffect(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL && NewOrderRules.hasRule(entity, NewOrderRule.REPEL)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    static EventResult guardSoul(LivingEntity target, Changeable<Float> amount) {
        if (amount.get() > 0.0F && NewOrderRules.hasRule(target, NewOrderRule.SOUL)) {
            amount.set((float) (amount.get() * CONFIG.soulDamageMultiplier));
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.6D), target.getZ(), 4, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.02D);
            }
        }
        return EventResult.pass();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if ((source.is(DamageTypeTags.IS_FIRE) || TensuraDamageHelper.isFireDamage(source)) && !target.level().isClientSide && NewOrderRules.hasRule(target, NewOrderRule.FLAMES)) {
            target.clearFire();
            event.setCanceled(true);
            return;
        }

        if (source.getEntity() instanceof LivingEntity attacker && attacker != event.getEntity() && NewOrderRules.hasRule(attacker, NewOrderRule.DEGRADE)) {
            TensuraDamageSource tensura = (TensuraDamageSource) source;
            if (tensura.tensura$getResistanceBypassLevel() < 1.0F) {
                tensura.tensura$setResistanceBypassLevel(1.0F);
            }
        }
    }

}

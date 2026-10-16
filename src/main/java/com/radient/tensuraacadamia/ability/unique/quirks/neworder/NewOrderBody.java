package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ability.AbilitySlot;
import io.github.manasmods.tensura.storage.ability.IAbility;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderBody {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    public static final String BODY_TAG = "airBody";
    private static final String HEALTH_TAG = "airHealth";
    private static final String MAX_TAG = "airMax";

    //Body ride change
//    public static final float BODY_RIDE = 2.5F;
    private static final float EYE_HEIGHT = 1.62F;
    private static final double BODY_REACH = 4.0D;
    private static final double BODY_STEP = 0.5D;
    private static final ResourceLocation BODY_WIND = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body_wind");
    private static final ResourceLocation BODY_ENTITY_REACH = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body_entity_reach");
    private static final ResourceLocation BODY_BLOCK_REACH = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body_block_reach");
    private static final ResourceLocation BODY_STEP_HEIGHT = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_body_step");
    private static final int BODY_HIT_COOLDOWN = 10;
    private static final double EJECT_SPEED = 1.3D;
    private static final double EJECT_LIFT = 0.7D;
    private static final Map<UUID, NewOrderUtil.Frame> BODY_HITS = new HashMap<>();
    private static final int SLOT_CHECK = 10;

    // When a user last hit or got hit
    private static final Map<UUID, Long> COMBAT = new HashMap<>();

    // Size the player up to the construct
    private static final Set<Player> BODIES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<Player> CLIENT_BODIES = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private NewOrderBody() {
    }

    //Changed to function to fix scaling

    // size, height
    // subtracting by half of height should line you up near the middle
    public static float getBodyRide()
    {
        return NewOrderCopy.getCopyScale() * 1.8F * 0.63F - 0.9F;
    }

    public static boolean isInside(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.contains(BODY_TAG);
    }

    public static boolean isInside(LivingEntity entity) {
        return NewOrderQuirk.getNewOrder(entity).filter(NewOrderBody::isInside).isPresent();
    }

    public record BodyHealth(float health, float max) {}

    public static @Nullable BodyHealth getBodyHealth(LivingEntity entity) {
        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(entity).filter(NewOrderBody::isInside).orElse(null);
        if (instance == null) {
            return null;
        }

        CompoundTag tag = instance.getOrCreateTag();
        float max = tag.getFloat(MAX_TAG);
        return new BodyHealth(tag.contains(HEALTH_TAG) ? tag.getFloat(HEALTH_TAG) : max, max);
    }

    // Gotta make sure the health stays between uses
    private static float getMaxHealth(LivingEntity entity) {
        return (float) (entity.getMaxHealth() * CONFIG.bodyHealthMultiplier);
    }

    private static float getHealth(ManasSkillInstance instance, float max) {
        CompoundTag tag = instance.getOrCreateTag();
        return tag.contains(HEALTH_TAG) ? Math.min(max, tag.getFloat(HEALTH_TAG)) : max;
    }

    private static void setHealth(ManasSkillInstance instance, LivingEntity entity, float health, float max) {
        CompoundTag tag = instance.getOrCreateTag();
        tag.putFloat(HEALTH_TAG, Mth.clamp(health, 0.0F, max));
        tag.putFloat(MAX_TAG, max);
        NewOrderUtil.dirty(instance, entity);
    }

    private static void inCombat(LivingEntity entity) {
        COMBAT.put(entity.getUUID(), entity.level().getGameTime());
    }

    // Out of combat it starts to heal so it can be used again
    private static void regenerate(LivingEntity entity, ManasSkillInstance instance, long time) {
        int interval = Math.max(1, CONFIG.bodyRegenSeconds * 20);
        Long fought = COMBAT.get(entity.getUUID());
        if (time % interval != 0L || fought != null && time - fought < CONFIG.bodyCombatSeconds * 20L) {
            return;
        }

        float max = getMaxHealth(entity);
        float health = getHealth(instance, max);
        if (health < max) {
            setHealth(instance, entity, health + max * (float) CONFIG.bodyRegenPercent, max);
        }
    }

    @SubscribeEvent
    public static void onCombat(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) {
            return;
        }
        if (target instanceof Player && NewOrderQuirk.getNewOrder(target).isPresent()) {
            inCombat(target);
        }
        if (event.getSource().getEntity() instanceof Player attacker && attacker != target && NewOrderQuirk.getNewOrder(attacker).isPresent()) {
            inCombat(attacker);
        }
    }

    static void bodyOfAir(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isInside(instance)) {
            leave(level, instance, entity, Leave.STEP_OUT, null);
            return;
        }
        if (!NewOrderRules.hasRule(entity, NewOrderRule.HARDEN)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_harden"));
            return;
        }
        if (NewOrderStrikes.isCasting(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.copy_busy"));
            return;
        }
        float max = getMaxHealth(entity);
        float health = getHealth(instance, max);
        if (health <= 0.0F) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.body_reforming"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.BODY_OF_AIR)) {
            return;
        }

        instance.getOrCreateTag().putBoolean(BODY_TAG, true);
        setHealth(instance, entity, health, max);
        instance.addMasteryPoint(entity);
        BODY_HITS.remove(entity.getUUID());
        applyBody(entity, true);
        if (entity instanceof ServerPlayer player) {
            fixSlots(player, instance);
        }

        Vec3 center = entity.getBoundingBox().getCenter();
        double reach = NewOrderCopy.getCopyUnit(entity);
        for (int i = 0; i < 24; i++) {
            double angle = i * Mth.TWO_PI / 24.0D;
            Vec3 from = center.add(Math.cos(angle) * reach, (level.random.nextDouble() - 0.5D) * reach, Math.sin(angle) * reach);
            Vec3 pull = center.subtract(from).scale(0.12D);
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 0, pull.x, pull.y, pull.z, 1.0D);
        }
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_INHALE, 1.2F, 0.7F);
        NewOrderUtil.playSound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 0.6F);
    }

    private enum Leave {
        STEP_OUT,
        BROKEN,
        LOST
    }

    private static void leave(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, Leave reason, @Nullable Entity attacker) {
        instance.getOrCreateTag().remove(BODY_TAG);
        NewOrderUtil.dirty(instance, entity);
        BODY_HITS.remove(entity.getUUID());
        NewOrderBodyMoves.forget(entity);
        applyBody(entity, false);
        if (entity instanceof ServerPlayer player) {
            fixSlots(player, instance);
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        switch (reason) {
            case STEP_OUT -> {
                instance.setCoolDown(CONFIG.bodyExitCooldown, NewOrderQuirk.BODY_OF_AIR);
                level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 16, 0.6D, 0.8D, 0.6D, 0.05D);
                NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_IDLE_AIR, 1.0F, 1.2F);
            }
            case BROKEN -> {
                setHealth(instance, entity, 0.0F, getMaxHealth(entity));
                instance.setCoolDown(CONFIG.bodyBreakCooldown, NewOrderQuirk.BODY_OF_AIR);
                eject(entity, attacker);
                double reach = NewOrderCopy.getCopyUnit(entity) * 0.6D;
                level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y + reach * 0.5D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                for (int i = 0; i < 40; i++) {
                    double angle = level.random.nextDouble() * Mth.TWO_PI;
                    double rise = level.random.nextDouble() - 0.3D;
                    level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + level.random.nextDouble() * reach, center.z, 0, Math.cos(angle), rise, Math.sin(angle), 0.6D);
                }
                NewOrderUtil.playSound(level, center, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.5F);
                NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_DEATH, 1.2F, 0.7F);
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new NewOrderPayloads.CopyActionPayload(entity.getId(), NewOrderCopy.ACTION_REFORM, 0, 0));
                NewOrderUtil.message(entity, Component.translatable("tracadamia.skill.new_order.body_broken").withStyle(ChatFormatting.RED));
            }
            case LOST -> level.sendParticles(ParticleTypes.CLOUD, center.x, center.y, center.z, 10, 0.6D, 0.8D, 0.6D, 0.04D);
        }
    }

    // just ejects the player from the construct when broken
    private static void eject(LivingEntity entity, @Nullable Entity attacker) {
        Vec3 away = attacker != null && attacker != entity ? entity.position().subtract(attacker.position()) : NewOrderCopy.getForward(entity).reverse();
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4D) {
            away = NewOrderCopy.getForward(entity).reverse();
        }
        away = away.normalize().scale(EJECT_SPEED);
        entity.setDeltaMovement(away.x, EJECT_LIFT, away.z);
        entity.hurtMarked = true;
        entity.resetFallDistance();
    }

    // HIT DA CONSTRUCT
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        if (!(entity.level() instanceof ServerLevel level) || event.getAmount() <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(entity).filter(NewOrderBody::isInside).orElse(null);
        if (instance == null || !NewOrderRules.hasRule(entity, NewOrderRule.HARDEN)) {
            return;
        }

        event.setCanceled(true);
        inCombat(entity);
        float amount = NewOrderUtil.frame(BODY_HITS, entity.getUUID(), level.getGameTime(), event.getAmount(), BODY_HIT_COOLDOWN);
        if (amount <= 0.0F) {
            return;
        }
        if (NewOrderRules.hasRule(entity, NewOrderRule.ENHANCE)) {
            amount *= (float) (1.0D - CONFIG.enhanceBodyDamageReduction);
        }

        float max = getMaxHealth(entity);
        float health = getHealth(instance, max) - amount;
        if (health <= 0.0F) {
            leave(level, instance, entity, Leave.BROKEN, source.getEntity());
            return;
        }

        setHealth(instance, entity, health, max);
        Vec3 at = source.getSourcePosition() != null ? source.getSourcePosition() : entity.getBoundingBox().getCenter();
        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 hit = center.add(at.subtract(center).normalize().scale(entity.getBbWidth() * 0.5D * NewOrderCopy.getCopyScale()));
        level.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y, hit.z, 8, 0.3D, 0.3D, 0.3D, 0.05D);
        NewOrderUtil.playSound(level, hit, SoundEvents.BREEZE_DEFLECT, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
    }

    // Changes damage to wind
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMeleeFromInside(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof LivingEntity attacker && !attacker.level().isClientSide && NewOrderCopy.isMelee(source, attacker)
                && isInside(attacker) && NewOrderRules.hasRule(attacker, NewOrderRule.HARDEN)) {
            ((TensuraDamageSource) source).tensura$setElement(Element.WIND);
            ((TensuraDamageSource) source).tensura$setPhysicalConverted();
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && isInside(entity) && NewOrderRules.hasRule(entity, NewOrderRule.HARDEN)) {
            event.setCanceled(true);
        }
    }

    static void tickBody(ServerPlayer player, ManasSkillInstance instance) {
        boolean inside = isInside(instance);
        if (inside && (!player.isAlive() || !NewOrderRules.hasRule(player, NewOrderRule.HARDEN))) {
            leave(player.serverLevel(), instance, player, Leave.LOST, null);
            inside = false;
        }
        applyBody(player, inside);
        long time = player.level().getGameTime();
        regenerate(player, instance, time);
        if (inside && instance.getOrCreateTag().getFloat(MAX_TAG) != getMaxHealth(player)) {
            setHealth(instance, player, getHealth(instance, getMaxHealth(player)), getMaxHealth(player));
        }
        if (time % SLOT_CHECK == 0L) {
            fixSlots(player, instance);
        }
    }

    // Construct size, reach, and other stuff
    private static void applyBody(LivingEntity entity, boolean inside) {
        double scale = entity.getScale();
        AdditiveBoost.apply(entity, TensuraAttributes.WIND_BOOST, BODY_WIND, inside ? CONFIG.bodyWindBoost : 0.0D);
        Modifiers.set(entity, Attributes.ENTITY_INTERACTION_RANGE, BODY_ENTITY_REACH, inside ? BODY_REACH * scale : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.BLOCK_INTERACTION_RANGE, BODY_BLOCK_REACH, inside ? BODY_REACH * scale : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.STEP_HEIGHT, BODY_STEP_HEIGHT, inside ? BODY_STEP * scale : 0.0D, AttributeModifier.Operation.ADD_VALUE);
        if (entity instanceof Player player) {
            setBody(player, inside);
        }
    }

    private static void fixSlots(ServerPlayer player, ManasSkillInstance instance) {
        IAbility ability = TensuraStorages.getAbilityFrom(player);
        boolean changed = false;
        for (int preset = 0; preset < ability.getPresets().size(); preset++) {
            List<AbilitySlot> slots = ability.getAbilitySlots(preset);
            for (int slot = 0; slot < slots.size(); slot++) {
                AbilitySlot held = slots.get(slot);
                if (held.getSkill() == instance.getSkill() && !NewOrderQuirk.isModeAvailable(instance, held.getMode())) {
                    ability.setAbilitySlot(preset, slot, held.getSkill(), NewOrderQuirk.getStandIn(instance, held.getMode()));
                    changed = true;
                }
            }
        }
        if (changed) {
            ability.markDirty();
        }
    }

    public static void setBody(Player player, boolean inside) {
        Set<Player> bodies = player.level().isClientSide ? CLIENT_BODIES : BODIES;
        if (inside == bodies.contains(player)) {
            return;
        }

        if (inside) {
            bodies.add(player);
        } else {
            bodies.remove(player);
        }
        player.refreshDimensions();
    }

    public static boolean hasBody(Player player) {
        return (player.level().isClientSide ? CLIENT_BODIES : BODIES).contains(player);
    }

    // damn hitbox fix for flying
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player && hasBody(player)) {
            float scale = player.getScale();
            event.setNewSize(EntityDimensions.scalable(0.6F * NewOrderCopy.getCopyScale() * scale, 1.8F * NewOrderCopy.getCopyScale() * scale).withEyeHeight((getBodyRide() + EYE_HEIGHT) * scale));
        }
    }

    static void forget(LivingEntity entity, ManasSkillInstance instance) {
        if (isInside(instance) && entity.level() instanceof ServerLevel level) {
            leave(level, instance, entity, Leave.LOST, null);
        }
    }

    public static void clearClient() {
        CLIENT_BODIES.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 200 == 0 && (!BODY_HITS.isEmpty() || !COMBAT.isEmpty())) {
            long time = event.getServer().overworld().getGameTime();
            BODY_HITS.values().removeIf(hit -> time - hit.time() > BODY_HIT_COOLDOWN);
            COMBAT.values().removeIf(fought -> time - fought > CONFIG.bodyCombatSeconds * 20L);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BODY_HITS.clear();
        COMBAT.clear();
        BODIES.clear();
    }

}

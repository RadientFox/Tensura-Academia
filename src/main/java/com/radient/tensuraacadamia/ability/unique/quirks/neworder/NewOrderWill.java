package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

// My Body Will Not Move, My Body Will Heal, My Mind Will Remain Clear and I Will Not Raise Aggro
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderWill {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    private static final int GRANT_TICKS = 60;
    private static final int GRANT_REFRESH = 20;
    private static final double AGGRO_RANGE = 48.0D;

    private record Swap(LivingEntity entity, MobEffectInstance effect) {}

    private static final List<Swap> SWAPS = new ArrayList<>();
    private static final Set<LivingEntity> RAISED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean swapping;

    private NewOrderWill() {
    }


    public static boolean isImmovable(Entity target, @Nullable Entity cause) {
        return cause != target && target instanceof LivingEntity living && NewOrderRules.hasRule(living, NewOrderRule.IMMOVABLE);
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!event.getEntity().level().isClientSide && NewOrderRules.hasRule(event.getEntity(), NewOrderRule.IMMOVABLE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            TensuraEntityEvents.FORCE_MOVEMENT_EVENT.register((entity, cause, skill, push) -> isImmovable(entity, cause) ? EventResult.interruptFalse() : EventResult.pass());
            TensuraEntityEvents.INSTANT_TRANSMISSION_EVENT.register((entity, warper, position, type) -> warper != null && isImmovable(entity, warper) ? EventResult.interruptFalse() : EventResult.pass());
            TensuraEntityEvents.DIMENSION_TRAVEL_EVENT.register((entity, warper, dimension) -> warper != null && isImmovable(entity, warper) ? EventResult.interruptFalse() : EventResult.pass());
        });
    }

    // My Body Will Heal
    // Moves the regens up and then reduces cost of infinite regen

    private static boolean isHealing(LivingEntity entity) {
        return NewOrderRules.hasRule(entity, NewOrderRule.HEAL) && !NewOrderSeals.cannotRecover(entity);
    }

    private static Holder<MobEffect> getInstant() {
        return TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION);
    }

    private static Holder<MobEffect> getSelf() {
        return TensuraMobEffects.getReference(TensuraMobEffects.SELF_REGENERATION);
    }

    // Self Regeneration to Ultraspeed
    // Ultraspeed to Infinite
    // null when there's nothing higher
    private static @Nullable MobEffectInstance raise(MobEffectInstance effect) {
        MobEffect type = effect.getEffect().value();
        if (type == TensuraMobEffects.SELF_REGENERATION.get()) {
            return new MobEffectInstance(getInstant(), effect.getDuration(), 0, effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }
        if (type == TensuraMobEffects.INSTANT_REGENERATION.get() && effect.getAmplifier() == 0) {
            return new MobEffectInstance(getInstant(), effect.getDuration(), 1, effect.isAmbient(), effect.isVisible(), effect.showIcon());
        }
        return null;
    }

    private static void put(LivingEntity entity, MobEffectInstance effect) {
        swapping = true;
        try {
            TensuraMobEffect.addEffect(entity, new MobEffectInstance(effect.getEffect(), effect.getDuration(), effect.getAmplifier(), false, false, false), entity, QuirkSkills.NEW_ORDER.get());
        } finally {
            swapping = false;
        }
    }

    @SubscribeEvent
    public static void onRegeneration(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (swapping || entity.level().isClientSide || !isHealing(entity)) {
            return;
        }

        MobEffectInstance raised = raise(event.getEffectInstance());
        if (raised != null) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            SWAPS.add(new Swap(entity, raised));
        }
    }

    static void heal(LivingEntity entity, boolean on) {
        if (!on || NewOrderSeals.cannotRecover(entity)) {
            RAISED.remove(entity);
            return;
        }

        if (RAISED.add(entity)) {
            for (MobEffectInstance effect : List.copyOf(entity.getActiveEffects())) {
                MobEffectInstance raised = raise(effect);
                if (raised != null) {
                    entity.removeEffect(effect.getEffect());
                    put(entity, raised);
                }
            }
        }

        MobEffectInstance self = entity.getEffect(getSelf());
        if (!entity.hasEffect(getInstant()) && (self == null || self.getDuration() < GRANT_REFRESH)) {
            put(entity, new MobEffectInstance(getSelf(), GRANT_TICKS, 0));
        }
    }

    // Infinite Regeneration costs less, check the mixin
    public static double getRegenerationCostMultiplier(LivingEntity entity) {
        MobEffectInstance instant = entity.getEffect(getInstant());
        return instant != null && instant.getAmplifier() >= 1 && isHealing(entity) ? Math.max(0.0D, 1.0D - CONFIG.healCostReduction) : 1.0D;
    }

    // My Mind Will Remain Clear
    private static boolean isMental(Holder<MobEffect> effect) {
        MobEffect type = effect.value();
        return type == MobEffects.CONFUSION.value() || type == TensuraMobEffects.CONFUSION.get() || type == TensuraMobEffects.FEAR.get() || type == TensuraMobEffects.HYPNOSIS.get()
                || type == TensuraMobEffects.INSANITY.get() || type == TensuraMobEffects.MIND_CONTROL.get() || type == TensuraMobEffects.LUST_EMBRACEMENT.get();
    }

    @SubscribeEvent
    public static void onMindEffect(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && isMental(event.getEffectInstance().getEffect()) && NewOrderRules.hasRule(entity, NewOrderRule.CLEAR_MIND)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    static void clearMind(LivingEntity entity) {
        for (MobEffectInstance effect : List.copyOf(entity.getActiveEffects())) {
            if (isMental(effect.getEffect())) {
                entity.removeEffect(effect.getEffect());
            }
        }
    }

    // I Will Not Raise Aggro
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !target.level().isClientSide && NewOrderRules.hasRule(target, NewOrderRule.NO_AGGRO)) {
            event.setCanceled(true);
        }
    }

    static void calm(LivingEntity entity) {
        for (Mob mob : entity.level().getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(AGGRO_RANGE), mob -> mob != entity)) {
            if (mob.getTarget() == entity) {
                mob.setTarget(null);
            }
            if (mob instanceof NeutralMob neutral && entity.getUUID().equals(neutral.getPersistentAngerTarget())) {
                neutral.stopBeingAngry();
            }
            if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT) && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == entity) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SWAPS.isEmpty()) {
            List<Swap> pending = List.copyOf(SWAPS);
            SWAPS.clear();
            for (Swap swap : pending) {
                if (swap.entity().isAlive()) {
                    put(swap.entity(), swap.effect());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SWAPS.clear();
        RAISED.clear();
    }

}

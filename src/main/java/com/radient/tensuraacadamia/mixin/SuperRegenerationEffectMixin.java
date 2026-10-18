package com.radient.tensuraacadamia.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.radient.tensuraacadamia.quirks.super_regeneration.SuperRegenerationHandler;
import com.radient.tensuraacadamia.quirks.super_regeneration.SuperRegenerationQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.common.SelfRegenerationSkill;
import io.github.manasmods.tensura.effect.ability.InstantRegenerationEffect;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InstantRegenerationEffect.class, remap = false)
public abstract class SuperRegenerationEffectMixin {
    private static boolean slowed(LivingEntity owner) {
        return SuperRegenerationQuirk.ownsRecoveryEffect(owner) && SuperRegenerationHandler.weakened(owner);
    }

    @ModifyExpressionValue(method = "applyEffectTick", at = {
            @At(value = "FIELD", target = "Lio/github/manasmods/tensura/config/ability/skill/ExtraSkillConfig$InfiniteRegeneration;magiculeCost:D"),
            @At(value = "FIELD", target = "Lio/github/manasmods/tensura/config/ability/skill/ExtraSkillConfig$InfiniteRegeneration;magiculeCostMastered:D"),
            @At(value = "FIELD", target = "Lio/github/manasmods/tensura/config/ability/skill/ExtraSkillConfig$InfiniteRegeneration;shpMagiculeCost:D"),
            @At(value = "FIELD", target = "Lio/github/manasmods/tensura/config/ability/skill/ExtraSkillConfig$InfiniteRegeneration;shpMagiculeCostMastered:D")})
    private double tracadamia$recoveryCost(double cost, LivingEntity owner, int amplifier) {
        return SuperRegenerationQuirk.ownsRecoveryEffect(owner) ? cost * .1 : cost;
    }

    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void tracadamia$slowRecovery(LivingEntity owner, int amplifier, CallbackInfoReturnable<Boolean> result) {
        if (slowed(owner) && owner.tickCount % 20 != 0) result.setReturnValue(true);
    }

    private static int selfRegenerationLevel(LivingEntity owner) {
        var instance = SuperRegenerationQuirk.instance(owner);
        return instance != null && instance.isMastered(owner) ? SelfRegenerationSkill.CONFIG.regenLevelMastered : SelfRegenerationSkill.CONFIG.regenLevel;
    }

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lio/github/manasmods/tensura/storage/effect/EffectStorage;getSeveranceMaxHealth(Lnet/minecraft/world/entity/LivingEntity;)F"))
    private float tracadamia$slowPhysicalRecovery(LivingEntity owner, Operation<Float> original) {
        float maximum = original.call(owner);
        return slowed(owner) ? Math.min(maximum, owner.getHealth() + SelfRegenerationSkill.CONFIG.regenHP * selfRegenerationLevel(owner)) : maximum;
    }

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D", remap = true))
    private double tracadamia$slowSpiritualRecovery(LivingEntity owner, Holder<Attribute> attribute, Operation<Double> original) {
        double maximum = original.call(owner, attribute);
        if (attribute != TensuraAttributes.MAX_SPIRITUAL_HEALTH || !slowed(owner)) return maximum;
        int level = selfRegenerationLevel(owner);
        double amount = level > 1 ? SelfRegenerationSkill.CONFIG.regenSHP * level : 0;
        return Math.min(maximum, TensuraStorages.getExistenceFrom(owner).getSpiritualHealth() + amount);
    }

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lio/github/manasmods/manascore/skill/api/ManasSkillInstance;setToggled(Z)V"))
    private void tracadamia$preserveRevivalToggle(ManasSkillInstance instance, boolean toggled, Operation<Void> original, LivingEntity owner, int amplifier) {
        if (!toggled && instance.getSkill() == QuirkSkills.SUPER_REGENERATION.get()) SuperRegenerationQuirk.disableRecovery(instance, owner);
        else original.call(instance, toggled);
    }

    @WrapOperation(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", remap = true))
    private void tracadamia$recoveryWarning(LivingEntity owner, Component message, Operation<Void> original) {
        original.call(owner, SuperRegenerationQuirk.ownsRecoveryEffect(owner)
                ? Component.translatable("tracadamia.super_regeneration.no_magicules") : message);
    }
}

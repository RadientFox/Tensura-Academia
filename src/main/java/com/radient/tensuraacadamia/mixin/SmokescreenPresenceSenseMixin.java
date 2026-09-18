package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import io.github.manasmods.tensura.ability.SkillClientUtils;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.skill.IntrinsicSkills;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SkillClientUtils.class, remap = false)
public abstract class SmokescreenPresenceSenseMixin {
    @Redirect(method = "getGlowColor", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/LivingEntity;getAttributeValue(Lnet/minecraft/core/Holder;)D"),
            remap = false)
    private static double tracadamia$eyeOfTruthSeesThroughAspersion(LivingEntity target,
                                                                     Holder<Attribute> attribute,
                                                                     Player viewer, Entity seen) {
        double concealment = target.getAttributeValue(attribute);
        if (attribute == TensuraAttributes.PRESENCE_CONCEALMENT
                && target.hasEffect(MHAEffects.ASPERSION)
                && (viewer.getAttributeValue(TensuraAttributes.PRESENCE_SENSE) > 4.0
                || SkillUtils.isSkillToggled(viewer, IntrinsicSkills.EYE_OF_TRUTH.get()))) {
            return Math.max(0.0, concealment - 4.0);
        }
        return concealment;
    }
}

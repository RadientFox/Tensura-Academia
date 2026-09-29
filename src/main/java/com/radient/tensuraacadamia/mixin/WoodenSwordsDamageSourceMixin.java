package com.radient.tensuraacadamia.mixin;

import com.radient.tensuraacadamia.ability.unique.quirks.WoodenSwordsQuirk;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
public abstract class WoodenSwordsDamageSourceMixin {
    @Inject(method = "getWeaponItem", at = @At("HEAD"), cancellable = true)
    private void tracadamia$useMasteredEngravedSword(CallbackInfoReturnable<ItemStack> callback) {
        DamageSource source = (DamageSource) (Object) this;
        if (source.getDirectEntity() instanceof LivingEntity attacker
                && TensuraDamageHelper.isPhysicalAttack(source)
                && WoodenSwordsQuirk.hasMasteredIntangibility(attacker)) {
            callback.setReturnValue(WoodenSwordsQuirk.getIntangibilityWeaponStack(attacker));
        }
    }

    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void tracadamia$ignoreShieldsWithIntangibility(TagKey<DamageType> tag,
                                                           CallbackInfoReturnable<Boolean> callback) {
        if (!DamageTypeTags.BYPASSES_SHIELD.equals(tag)) {
            return;
        }

        DamageSource source = (DamageSource) (Object) this;
        if (source.getDirectEntity() instanceof LivingEntity attacker
                && TensuraDamageHelper.isPhysicalAttack(source)
                && WoodenSwordsQuirk.hasMasteredIntangibility(attacker)) {
            callback.setReturnValue(true);
        }
    }
}

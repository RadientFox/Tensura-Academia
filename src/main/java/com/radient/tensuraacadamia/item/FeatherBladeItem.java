package com.radient.tensuraacadamia.item;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.item.weapon.SimpleSwordItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Tier;

public class FeatherBladeItem extends SimpleSwordItem {

    private static final QuirkSkillsConfig.FierceWings CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).FierceWings;
    private static final double SWORD_ATTACK_SPEED = 1.6D;

    public FeatherBladeItem(Tier tier) {
        super(tier);
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        float bonus = super.getAttackDamageBonus(target, damage, source);
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return bonus;
        }

        AttributeInstance attackSpeed = attacker.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            return bonus;
        }

        double extra = Math.max(0.0D, attackSpeed.getValue() - SWORD_ATTACK_SPEED);
        return bonus + (float) (damage * extra * CONFIG.bladeAttackSpeedScaling);
    }
}

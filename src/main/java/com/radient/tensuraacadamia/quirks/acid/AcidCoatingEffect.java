package com.radient.tensuraacadamia.quirks.acid;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

public final class AcidCoatingEffect extends MobEffect {
    private final boolean sliding;
    private static final DustParticleOptions ACID_DUST = new DustParticleOptions(new Vector3f(.6F, .6F, .68F), .65F);

    public AcidCoatingEffect(boolean sliding) {
        super(MobEffectCategory.BENEFICIAL, 0xB0B1BE);
        this.sliding = sliding;
        if (sliding) addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath("tracadamia", "acid_layback_speed"), 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity owner, int amplifier) {
        if (owner.level().isClientSide) {
            var random = owner.getRandom();
            for (int i = 0; i < (sliding ? 2 : 3); i++)
                owner.level().addParticle(ACID_DUST,
                        owner.getX() + (random.nextDouble() - .5) * owner.getBbWidth() * 1.4,
                        owner.getY() + random.nextDouble() * (sliding ? .15 : owner.getBbHeight()),
                        owner.getZ() + (random.nextDouble() - .5) * owner.getBbWidth() * 1.4, 0, -.025, 0);
        } else if (owner.level() instanceof ServerLevel level) {
            if (sliding && owner.onGround() && owner.tickCount % 5 == 0 && owner.getDeltaMovement().horizontalDistanceSqr() > .0025)
                AcidProjectile.mark(level, owner, owner.position(), new net.minecraft.world.phys.Vec3(0, 1, 0));
            if (!sliding && owner.tickCount % 20 == 0)
                for (var target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(.3), enemy -> HomingQuirk.isEnemy(owner, enemy))) {
                    target.invulnerableTime = 0;
                    var damage = owner.damageSources().source(TensuraDamageTypes.CORROSION, owner);
                    ((TensuraDamageSource) damage).tensura$setSkillType(Skill.SkillType.UNIQUE);
                    target.hurt(damage, AcidQuirk.CONTACT_DAMAGE);
                }
        }
        return true;
    }
}

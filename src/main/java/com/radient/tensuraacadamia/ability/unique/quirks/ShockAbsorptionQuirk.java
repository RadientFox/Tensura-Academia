package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.DamageReduction;
import com.radient.tensuraacadamia.util.Modifiers;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public class ShockAbsorptionQuirk extends Skill {

    private static final QuirkSkillsConfig.ShockAbsorption CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ShockAbsorption;

    private static final double SHOCKWAVE_SCALE = 1.0D / 3.0D;
    private static final String REBOUND_TAG = "rebound";
    private static final ResourceLocation REBOUND = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "rebound_attack");

    public ShockAbsorptionQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    // Shock Absorption, Passive Toggle
    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, 0)) {
            instance.setToggled(false);
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        setRebound(instance, entity, 0.0D);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        setRebound(instance, entity, 0.0D);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        setRebound(instance, owner, 0.0D);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, REBOUND, getRebound(instance), AttributeModifier.Operation.ADD_VALUE);
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || source.getEntity() == null || source.getDirectEntity() != source.getEntity() || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        boolean mastered = instance.isMastered(owner);
        double reduction = mastered ? CONFIG.damageReductionMastered : CONFIG.damageReduction;
        amount.set(DamageReduction.reduce(owner, source, amount.get(), reduction));

        // Rebound Attack
        setRebound(instance, owner, amount.get() * (mastered ? CONFIG.reboundShareMastered : CONFIG.reboundShare));
        if (owner.level() instanceof ServerLevel level) {
            spawnShockwave(level, owner, source.getEntity());
        }

        if (owner.getRandom().nextInt(4) == 0) {
            instance.addMasteryPoint(owner);
        }

        return true;
    }

    public static double getRebound(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(REBOUND_TAG);
    }

    private static void setRebound(ManasSkillInstance instance, LivingEntity entity, double rebound) {
        instance.getOrCreateTag().putDouble(REBOUND_TAG, rebound);
        instance.markDirty();
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, REBOUND, rebound, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void spawnShockwave(ServerLevel level, LivingEntity owner, Entity attacker) {
        Vec3 center = owner.getBoundingBox().getCenter();
        Vec3 from = attacker.getEyePosition();
        Vec3 side = new Vec3(from.x - center.x, 0.0D, from.z - center.z);
        if (side.lengthSqr() < 1.0E-6D) {
            side = owner.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        }

        side = side.normalize().scale(owner.getBbWidth() * 0.5D + 0.1D);
        double height = Mth.clamp(from.y, owner.getY() + 0.2D, owner.getY() + owner.getBbHeight() - 0.2D);
        Vec3 hit = new Vec3(center.x + side.x, height, center.z + side.z);
        Vec3 normal = hit.subtract(from);
        normal = normal.lengthSqr() < 1.0E-6D ? side.normalize().reverse() : normal.normalize();
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, hit.x, hit.y, hit.z, 0, normal.x, normal.y, normal.z, SHOCKWAVE_SCALE);
    }

}

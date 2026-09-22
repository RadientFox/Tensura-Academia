package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ImpactRecoilQuirk extends Skill {

    private static final QuirkSkillsConfig.ImpactRecoil CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ImpactRecoil;

    public static final SimpleParticleType IMPACT_SHOCKWAVE = new SimpleParticleType(true);
    private static final ResourceLocation IMPACT_SHOCKWAVE_ID = ResourceLocation.fromNamespaceAndPath("tracadamia", "impact_shockwave");

    private static boolean recoiling = false;

    public ImpactRecoilQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, IMPACT_SHOCKWAVE_ID, () -> IMPACT_SHOCKWAVE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    // Impact Recoil Passive
    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || recoiling || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == owner || !attacker.isAlive()) {
            return true;
        }

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !TensuraDamageHelper.isPhysicalAttack(source)) {
            return true;
        }

        float damage = amount.get();
        amount.set((float) (damage * (1.0D - CONFIG.damageReduction)));
        recoil(level, owner, attacker, (float) (damage * CONFIG.recoilPercent));
        spawnShockwave(level, owner, attacker);

        if (owner.getRandom().nextBoolean()) {
            instance.addMasteryPoint(owner);
        }

        return true;
    }

    // Recoil damage
    private static void recoil(ServerLevel level, LivingEntity owner, LivingEntity attacker, float damage) {
        DamageSource source = level.damageSources().source(DamageTypes.MOB_ATTACK, null, owner);
        ((TensuraDamageSource) source).tensura$setSkillType(SkillType.UNIQUE);
        ((TensuraDamageSource) source).tensura$setResistanceBypassLevel(1.0F);

        int invulnerableTime = attacker.invulnerableTime;
        recoiling = true;
        try {
            attacker.invulnerableTime = 0;
            attacker.hurt(source, damage);
        } finally {
            recoiling = false;
            attacker.invulnerableTime = invulnerableTime;
        }
    }

    // Shockwave
    private static void spawnShockwave(ServerLevel level, LivingEntity owner, LivingEntity attacker) {
        Vec3 from = owner.position().add(0.0D, owner.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = attacker.position().add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D);
        Vec3 direction = to.subtract(from);
        double distance = direction.length();
        if (distance < 1.0E-4D) {
            return;
        }

        direction = direction.scale(1.0D / distance);
        Vec3 pos = from.add(direction.scale(Math.min(distance * 0.5D, 1.0D)));
        level.sendParticles(IMPACT_SHOCKWAVE, pos.x, pos.y, pos.z, 0, direction.x, direction.y, direction.z, 1.0D);
    }

}

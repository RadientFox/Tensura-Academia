package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import static io.github.manasmods.tensura.ability.SkillUtils.hasSkill;

public class FloatQuirk extends Skill {

    private static final float DEFAULT_FLY_SPEED = 0.05F;
    private static final float FLOAT_FLY_SPEED = 0.0125F;

    public FloatQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/float.png");
    }

    public int getMaxMastery() {
        return (int) 2500.0;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return true;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == 0 ? 1000.0D : 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
            }

            player.getAbilities().setFlyingSpeed(FLOAT_FLY_SPEED);
            player.onUpdateAbilities();
        }
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            player.getAbilities().mayfly = false;

            player.getAbilities().flying = false;

            player.getAbilities().setFlyingSpeed(DEFAULT_FLY_SPEED);

            player.onUpdateAbilities();
        }
    }

    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    public String getModeId(ManasSkillInstance instance, int mode) {
        String var10000;
        switch (mode) {
            case 0 -> var10000 = "float.airdash";
            default -> var10000 = super.getModeId(instance, mode);
        }
        return var10000;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        double auraCost = getAuraCost(entity, instance, mode);
        if (EnergyHelper.isOutOfEnergy(entity, auraCost, 0.0D)) return;
        if (entity.level().isClientSide) return;
        if (!(entity instanceof Player player)) return;

        if (mode != 0) return;

        if (player.onGround() || player.isInWaterOrBubble()) return;

        float scale = 0.0F;

        if (hasSkill(player, ExtraSkills.WIND_DOMINATION.get())) {
            scale = 2.5F;
        } else if (hasSkill(player, ExtraSkills.WIND_MANIPULATION.get())) {
            scale = 1.5F;
        }

        if (scale > 0.0F) {
            if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) return;
            SkillHelper.riptidePush(player, scale);
            TensuraParticleHelper.addServerParticlesAroundSelf(player, ParticleTypes.CLOUD, 1.5D);
            player.hurtMarked = true;
            player.resetFallDistance();
            instance.addMasteryPoint(player);
            instance.setCoolDown(1, mode);
        }
    }

}
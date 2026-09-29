package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.QueenBeamProjectile;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.QueenBeamEntities;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class QueenBeamQuirk extends Skill {
    public static final int QUEEN = 0;
    public static final int PRINCESS = 1;
    public static final int CHARM_TICKS = 600;
    public static final int MAX_RICOCHETS = 3;
    public static final double RICOCHET_RANGE = 10.0D;

    public QueenBeamQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/queenbeam.png");
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() { return 0.0D; }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) { return false; }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) { return 0.0D; }

    @Override
    public int getModes(ManasSkillInstance instance) { return 2; }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 2);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == PRINCESS ? "queen_beam.princess_beam" : "queen_beam.queen_beam";
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level) || mode < QUEEN || mode > PRINCESS) return;
        QueenBeamProjectile projectile = QueenBeamEntities.QUEEN_BEAM.get().create(level);
        if (projectile == null) return;
        Vec3 direction = entity.getLookAngle().normalize();
        projectile.setOwner(entity);
        projectile.setPrincess(mode == PRINCESS);
        projectile.setPos(entity.getEyePosition());
        projectile.shoot(direction.x, direction.y, direction.z, QueenBeamProjectile.SPEED, 0.0F);
        if (!level.addFreshEntity(projectile)) return;
        level.playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(),
                TensuraSoundEvents.CAST_LIGHT.get(), SoundSource.PLAYERS, 0.6F,
                mode == PRINCESS ? 1.5F : 1.15F);
        Vec3 heart = entity.getEyePosition().add(direction.scale(0.5D));
        level.sendParticles(MHAParticles.QUEEN_HEART.get(), heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
        instance.addMasteryPoint(entity);
        instance.markDirty();
    }
}

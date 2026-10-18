package com.radient.tensuraacadamia.quirks.bat;

import com.radient.tensuraacadamia.quirks.shared.QuirkCastCosts;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class BatQuirk extends Skill {
    public BatQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.bat.description");
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/baticon.png");
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "bat.dash";
            case 1 -> "bat.echolocation";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode == 0 ? 100 : 1000;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner instanceof ServerPlayer player) || !owner.isAlive() || instance.getMastery() < 0
                || mode < 0 || mode > 1 || instance.onCoolDown(mode)) return;
        double cost = getAuraCost(owner, instance, mode);
        if (!QuirkCastCosts.hasAura(owner, cost)) return;
        BatEvents.wake(player);
        long time = player.level().getGameTime();
        var tag = instance.getOrCreateTag();
        if (mode == 0) {
            Vec3 velocity = owner.getLookAngle().scale((instance.isMastered(owner) ? 20 : 15) / 10.0);
            tag.putLong("BatDashUntil", time + 10);
            tag.putDouble("BatDashX", velocity.x);
            tag.putDouble("BatDashY", velocity.y);
            tag.putDouble("BatDashZ", velocity.z);
            owner.setDeltaMovement(velocity);
            owner.hurtMarked = true;
            owner.fallDistance = 0;
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, .8F, 1.3F);
        } else {
            tag.putLong("BatEchoUntil", time + 40);
            player.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, owner.getX(), owner.getEyeY(), owner.getZ(),
                    1, 0, 0, 0, 0);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BAT_AMBIENT,
                    SoundSource.PLAYERS, .7F, 1.7F);
        }
        QuirkCastCosts.spendAura(owner, cost);
        QuirkCastCosts.cooldown(instance, mode, mode == 0 ? (instance.isMastered(owner) ? 2 : 3) : 30, 2);
        instance.markDirty();
        instance.addMasteryPoint(owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        BatEvents.clear(owner, instance);
        super.onForgetSkill(instance, owner);
    }
}

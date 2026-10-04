package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class TeleportationQuirk extends Skill {
    public TeleportationQuirk() { super(SkillType.UNIQUE); }

    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/teleporticon.png");
    }
    @Override public net.minecraft.network.chat.MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.teleportation.description");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 50; }
    @Override public int getModes(ManasSkillInstance instance) { return 1; }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 0 ? "teleportation.teleport" : super.getModeId(instance, mode);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (mode != 0 || !(owner.level() instanceof ServerLevel level)) return;
        if (!QuirkCastCosts.hasAura(owner, getAuraCost(owner, instance, mode))) return;
        double range = instance.isMastered(owner) ? 30 : 15;
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        Vec3 destination;
        if (hit.getType() == HitResult.Type.BLOCK) {
            Direction face = hit.getDirection();
            Vec3 surface = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.65));
            destination = face == Direction.UP ? surface : surface.subtract(0, owner.getEyeHeight(), 0);
        } else {
            destination = end.subtract(0, owner.getEyeHeight(), 0);
        }
        if (destination.y < level.getMinBuildHeight() || destination.y + owner.getBbHeight() > level.getMaxBuildHeight()) return;
        level.playSound(null, owner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        owner.teleportTo(destination.x, destination.y, destination.z);
        QuirkCastCosts.spendAura(owner, getAuraCost(owner, instance, mode));
        owner.setDeltaMovement(Vec3.ZERO);
        owner.fallDistance = 0;
        level.playSound(null, owner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        instance.addMasteryPoint(owner);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void dodgeAttack(LivingIncomingDamageEvent event) {
        LivingEntity owner = event.getEntity();
        if (!(owner.level() instanceof ServerLevel level) || event.getAmount() <= 0) return;
        var attacker = event.getSource().getEntity() != null
                ? event.getSource().getEntity() : event.getSource().getDirectEntity();
        if (attacker == null) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(owner)
                .getSkill(QuirkSkills.TELEPORTATION.get()).orElse(null);
        if (instance == null || owner.getRandom().nextFloat() >= (instance.isMastered(owner) ? 0.25F : 0.15F)) return;

        Vec3 origin = owner.position();
        Vec3 away = origin.subtract(attacker.position()).multiply(1, 0, 1);
        double angle = away.lengthSqr() > 0.01 ? Math.atan2(away.z, away.x)
                : owner.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 0; i < 16; i++) {
            double direction = angle + (i % 8) * Math.PI / 4;
            double distance = i < 8 ? 3.5 : 2;
            Vec3 destination = origin.add(Math.cos(direction) * distance, i < 8 ? 0 : 1,
                    Math.sin(direction) * distance);
            BlockPos block = BlockPos.containing(destination);
            if (destination.y < level.getMinBuildHeight()
                    || destination.y + owner.getBbHeight() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(block) || !level.hasChunkAt(block)
                    || !level.noCollision(owner, owner.getBoundingBox().move(destination.subtract(origin)))) continue;
            level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + owner.getBbHeight() / 2, origin.z,
                    18, 0.3, 0.5, 0.3, 0.05);
            owner.teleportTo(destination.x, destination.y, destination.z);
            owner.fallDistance = 0;
            level.sendParticles(ParticleTypes.PORTAL, destination.x, destination.y + owner.getBbHeight() / 2,
                    destination.z, 18, 0.3, 0.5, 0.3, 0.05);
            level.playSound(null, block, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
            event.setCanceled(true);
            return;
        }
    }
}

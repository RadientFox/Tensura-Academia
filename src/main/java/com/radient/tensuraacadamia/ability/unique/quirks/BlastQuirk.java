package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.BlastProjectile;
import com.radient.tensuraacadamia.regestry.BlastEntities;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;

public final class BlastQuirk extends Skill {
    public static final int CHARGE_TICKS = 100, RAMP_TICKS = 100, BIG_COOLDOWN_TICKS = 600;
    public static final int MINIGUN_MAX_HOLD_TICKS = 200, MINIGUN_COOLDOWN_TICKS = 200;
    public static final double NORMAL_SPREAD = 25, ACCURATE_SPREAD = 2;
    private static final String HELD = "BlastHeldMode", START = "BlastHoldStart", NEXT = "BlastNextShot",
            READY = "BlastBigReady", MINIGUN_READY = "BlastMinigunReady", VISUAL = "BlastChargeVisual", HAND = "BlastHand";
    private static final DustParticleOptions YELLOW = new DustParticleOptions(new Vector3f(1, 0.85F, 0.05F), 1);

    public BlastQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/blasticon.png");
    }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public void onToggleOn(ManasSkillInstance instance, LivingEntity owner) { sunglasses(owner, true); }
    @Override public void onToggleOff(ManasSkillInstance instance, LivingEntity owner) { sunglasses(owner, false); }
    private static void sunglasses(LivingEntity owner, boolean on) {
        if (!owner.level().isClientSide && owner instanceof Player player) player.displayClientMessage(
                Component.translatable("tracadamia.skill.blast.sunglasses." + (on ? "on" : "off")), true);
    }
    @Override public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() ? 3 : instance.getMastery() >= getMaxMastery() / 2 ? 2 : 1;
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return "blast." + switch (mode) { case 1 -> "minigun"; case 2 -> "big_ass_blast"; default -> "ratatata"; };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode == 2 ? 10000 : mode == 1 ? 40 : 50;
    }
    @Override public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner, int key, int mode) { return true; }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || !allowed(instance, owner, mode)) return;
        var held = instance.getOrCreateTag();
        if (mode != 0 && held.contains(HELD) && held.getInt(HELD) == mode) return;
        finishHold(instance, owner);
        if (!allowed(instance, owner, mode)) return;
        if (mode == 0) { fire(instance, owner, mode); return; }
        if (!hasAura(instance, owner, mode)) return;
        var tag = instance.getOrCreateTag();
        tag.putInt(HELD, mode);
        tag.putLong(START, level.getGameTime());
        tag.putLong(NEXT, level.getGameTime() + 20);
        instance.markDirty();
        if (mode == 2) level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.5F);
    }

    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (owner.level().isClientSide) return mode == 1 || mode == 2;
        var tag = instance.getOrCreateTag();
        if (!tag.contains(HELD) || tag.getInt(HELD) != mode || !allowed(instance, owner, mode)) {
            finishHold(instance, owner); return false;
        }
        long now = owner.level().getGameTime(), elapsed = now - tag.getLong(START);
        if (elapsed < 0) { finishHold(instance, owner); return false; }
        if (mode == 1) {
            if (elapsed >= MINIGUN_MAX_HOLD_TICKS) { finishHold(instance, owner); return false; }
            if (now >= tag.getLong(NEXT)) {
                if (!fire(instance, owner, mode)) { finishHold(instance, owner); return false; }
                tag.putLong(NEXT, now + minigunInterval(elapsed));
            }
        } else if (mode == 2) {
            if (!hasAura(instance, owner, mode)) { clearHold(instance); return false; }
            if (elapsed >= CHARGE_TICKS) {
                clearHold(instance);
                fire(instance, owner, mode);
                return false;
            }
            if (now >= tag.getLong(VISUAL)) {
                tag.putLong(VISUAL, now + 5);
                Vec3 point = owner.getEyePosition().add(owner.getLookAngle().scale(1.3)).add(0, -0.4, 0);
                ((ServerLevel) owner.level()).sendParticles(YELLOW, point.x, point.y, point.z, 8, 0.25, 0.25, 0.25, 0);
                if (owner instanceof Player player) player.displayClientMessage(
                        Component.translatable("tracadamia.skill.blast.charging", Math.min(100, elapsed)), true);
            }
        }
        return true;
    }
    public static int minigunInterval(long elapsedTicks) {
        return 20 - (int) (18 * Math.clamp(elapsedTicks, 0, RAMP_TICKS) / RAMP_TICKS);
    }
    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) { finishHold(instance, owner); }
    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) { finishHold(instance, owner); }
    private static void finishHold(ManasSkillInstance instance, LivingEntity owner) {
        var tag = instance.getOrCreateTag();
        if (!owner.level().isClientSide && tag.contains(HELD) && tag.getInt(HELD) == 1) {
            setCooldown(instance, owner, 1, MINIGUN_COOLDOWN_TICKS);
            owner.level().playSound(null, owner.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1);
            if (owner instanceof Player player) player.displayClientMessage(Component.translatable("tracadamia.skill.blast.cooling"), true);
        }
        clearHold(instance);
    }
    private static void setCooldown(ManasSkillInstance instance, LivingEntity owner, int mode, int ticks) {
        instance.getOrCreateTag().putLong(mode == 1 ? MINIGUN_READY : READY, owner.level().getGameTime() + ticks);
        var cooldowns = new ArrayList<>(instance.getCooldownList());
        while (cooldowns.size() < 3) cooldowns.add(0);
        cooldowns.set(mode, ticks / 20);
        instance.setCoolDownList(cooldowns);
        instance.markDirty();
    }
    private static void clearHold(ManasSkillInstance instance) {
        var tag = instance.getOrCreateTag();
        tag.remove(HELD); tag.remove(START); tag.remove(NEXT); tag.remove(VISUAL);
        instance.markDirty();
    }
    private boolean allowed(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return owner.isAlive() && mode >= 0 && mode < getModes(instance)
                && (mode != 1 || (!instance.onCoolDown(1) && instance.getOrCreateTag().getLong(MINIGUN_READY) <= owner.level().getGameTime()))
                && (mode != 2 || (!instance.onCoolDown(2) && instance.getOrCreateTag().getLong(READY) <= owner.level().getGameTime()));
    }
    private boolean hasAura(ManasSkillInstance instance, LivingEntity owner, int mode) {
        if (TensuraStorages.getExistenceFrom(owner).getAura() >= getAuraCost(owner, instance, mode)) return true;
        if (owner instanceof Player player) player.displayClientMessage(Component.translatable("tracadamia.skill.blast.need_aura"), true);
        return false;
    }
    private boolean fire(ManasSkillInstance instance, LivingEntity owner, int mode) {
        if (!allowed(instance, owner, mode) || !hasAura(instance, owner, mode)) return false;
        ServerLevel level = (ServerLevel) owner.level();
        BlastProjectile shot = BlastEntities.BULLET.get().create(level);
        if (shot == null) return false;
        shot.setOwner(owner);
        shot.setSkill(owner, instance, this, mode);
        shot.configure(mode, instance.getMastery() >= getMaxMastery());
        Vec3 look = owner.getLookAngle().normalize(), right = BeamGeometry.perpendicular(look), up = look.cross(right).normalize();
        boolean hand = instance.getOrCreateTag().getBoolean(HAND);
        Vec3 origin = owner.getEyePosition().add(0, -0.4, 0).add(look.scale(mode == 2 ? 2.5 : 0.65));
        if (mode != 2) origin = origin.add(right.scale(hand ? 0.3 : -0.3));
        double spread = Math.toRadians(instance.isToggled() ? ACCURATE_SPREAD : NORMAL_SPREAD);
        double cosine = 1 - level.random.nextDouble() * (1 - Math.cos(spread)), sine = Math.sqrt(1 - cosine * cosine);
        double angle = level.random.nextDouble() * Math.PI * 2;
        Vec3 direction = look.scale(cosine).add(right.scale(sine * Math.cos(angle))).add(up.scale(sine * Math.sin(angle)));
        shot.setPos(origin);
        shot.setDeltaMovement(direction.scale(2));
        shot.updateRotation();
        if (!level.addFreshEntity(shot)) return false;
        var existence = TensuraStorages.getExistenceFrom(owner);
        existence.setAura(existence.getAura() - getAuraCost(owner, instance, mode));
        existence.markDirty();
        instance.getOrCreateTag().putBoolean(HAND, !hand);
        if (mode == 2) {
            setCooldown(instance, owner, 2, BIG_COOLDOWN_TICKS);
        }
        instance.addMasteryPoint(owner);
        instance.markDirty();
        level.playSound(null, owner.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, mode == 1 ? 0.25F : 0.6F, mode == 2 ? 0.5F : 1.5F);
        return true;
    }
}

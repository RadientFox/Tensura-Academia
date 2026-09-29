package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.PopOffProjectile;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.PopOffEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class PopOffQuirk extends Skill {
    public static final int STICK_TICKS = 600;
    private static final String CLING_HOST = "pop_off_cling_host";

    public PopOffQuirk() { super(SkillType.UNIQUE); }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/popofficon.png");
    }

    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) { return 0; }
    @Override public int getModes(ManasSkillInstance instance) { return 3; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) { return true; }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "pop_off.grape_rush";
            case 2 -> "pop_off.super_grape_rush";
            default -> "pop_off.pop_off";
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level) || mode < 0 || mode > 2) return;
        int count = mode == 1 ? 20 : mode == 2 ? 15 : 1;
        boolean spawned = false;
        for (int i = 0; i < count; i++) {
            PopOffProjectile ball = PopOffEntities.POP_OFF.get().create(level);
            if (ball == null) continue;
            ball.setOwner(entity);
            ball.setPos(entity.getEyePosition());
            if (mode == 1) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double distance = 1 + level.random.nextDouble() * 19;
                Vec3 landing = entity.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
                HitResult floor = level.clip(new ClipContext(landing.add(0, 8, 0), landing.add(0, -32, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                if (floor.getType() != HitResult.Type.MISS) landing = floor.getLocation().add(0, 0.001, 0);
                double flightTicks = 30;
                Vec3 velocity = landing.subtract(ball.position()).scale(1 / flightTicks)
                        .add(0, PopOffProjectile.GRAVITY * (flightTicks - 1) / 2, 0);
                ball.setDeltaMovement(velocity);
            } else {
                Vec3 direction = entity.getLookAngle();
                ball.shoot(direction.x, direction.y, direction.z, 1.25F, mode == 2 ? 7.0F : 0.0F);
            }
            spawned |= level.addFreshEntity(ball);
        }
        if (spawned) { instance.addMasteryPoint(entity); instance.markDirty(); }
    }

    @SubscribeEvent
    public static void onCharmAdded(MobEffectEvent.Added event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (event.getEntity().level().isClientSide || effect.isInfiniteDuration()
                || effect.getEffect().value() != TensuraMobEffects.MIND_CONTROL.get()
                || !SkillUtils.hasSkill(event.getEntity(), QuirkSkills.POP_OFF.get())) return;
        int duration = (int) Math.min(Integer.MAX_VALUE, (long) effect.getDuration() * 2);
        effect.update(new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(),
                effect.isAmbient(), effect.isVisible(), effect.showIcon()));
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getTarget() instanceof Player target) || target == player
                || !target.hasEffect(MHAEffects.POP)) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.POP_OFF.get()).orElse(null);
        if (instance == null || !instance.isToggled() || !player.startRiding(target, true)) return;
        instance.getOrCreateTag().putUUID(CLING_HOST, target.getUUID());
        instance.markDirty();
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getOrCreateTag().hasUUID(CLING_HOST);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) return;
        var host = entity.getVehicle();
        if (host == null || !instance.isToggled() || !(host instanceof LivingEntity living)
                || !living.hasEffect(MHAEffects.POP)) detach(instance, entity);
    }

    @Override public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) { detach(instance, entity); }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        detach(instance, entity);
        super.onForgetSkill(instance, entity);
    }

    private static void detach(ManasSkillInstance instance, LivingEntity entity) {
        var tag = instance.getOrCreateTag();
        if (!tag.hasUUID(CLING_HOST)) return;
        if (entity.getVehicle() != null && entity.getVehicle().getUUID().equals(tag.getUUID(CLING_HOST))) entity.stopRiding();
        tag.remove(CLING_HOST);
        instance.markDirty();
    }
}

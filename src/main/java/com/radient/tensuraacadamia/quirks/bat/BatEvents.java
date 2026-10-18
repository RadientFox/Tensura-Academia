package com.radient.tensuraacadamia.quirks.bat;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class BatEvents {
    private BatEvents() { }

    public static boolean active(LivingEntity owner) {
        return owner.isAlive() && !owner.isSpectator() && owner.hasEffect(MHAEffects.BATLIKE);
    }

    public static boolean hanging(LivingEntity owner) {
        return active(owner) && owner.hasEffect(MHAEffects.BAT_REST);
    }

    public static boolean flying(LivingEntity owner) {
        return airborne(owner) && !owner.isShiftKeyDown();
    }

    public static boolean landing(LivingEntity owner) {
        return airborne(owner) && owner.isShiftKeyDown();
    }

    private static boolean airborne(LivingEntity owner) {
        return active(owner) && !hanging(owner) && !owner.onGround() && !owner.isInWaterOrBubble()
                && !owner.isInLava() && !owner.isPassenger()
                && (!(owner instanceof Player player) || !player.getAbilities().flying);
    }

    public static boolean echolocating(LivingEntity owner) {
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.BAT.get()).orElse(null);
        return owner.isAlive() && instance != null && instance.getMastery() >= 0 && instance.getTag() != null
                && instance.getTag().getLong("BatEchoUntil") > owner.level().getGameTime();
    }

    public static Vec3 dash(LivingEntity owner) {
        if (!active(owner)) return Vec3.ZERO;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.BAT.get()).orElse(null);
        if (instance == null || instance.getTag() == null) return Vec3.ZERO;
        var tag = instance.getTag();
        return tag.getLong("BatDashUntil") > owner.level().getGameTime()
                ? new Vec3(tag.getDouble("BatDashX"), tag.getDouble("BatDashY"), tag.getDouble("BatDashZ")) : Vec3.ZERO;
    }

    public static boolean canHang(Player player, BlockPos anchor) {
        var level = player.level();
        if (!level.hasChunkAt(anchor) || level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty()) return false;
        for (int below = 1; below <= 4; below++) {
            if (!level.isInWorldBounds(anchor.below(below)) || !level.getBlockState(anchor.below(below)).isAir()) return false;
        }
        Vec3 position = hangingPosition(player, anchor);
        return level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(position));
    }

    private static Vec3 hangingPosition(Player player, BlockPos anchor) {
        double bottom = player.level().getBlockState(anchor).getCollisionShape(player.level(), anchor).min(Direction.Axis.Y);
        return new Vec3(anchor.getX() + .5, anchor.getY() + bottom - player.getDimensions(Pose.STANDING).height() - .02,
                anchor.getZ() + .5);
    }

    public static boolean hang(ServerPlayer player, ManasSkillInstance instance, BlockPos anchor) {
        if (!active(player) || player.isPassenger() || player.isSleeping() || !canHang(player, anchor)) return false;
        var tag = instance.getOrCreateTag();
        tag.putBoolean("BatHanging", true);
        tag.putLong("BatAnchor", anchor.asLong());
        tag.putLong("BatRestStarted", player.level().getGameTime());
        tag.remove("BatDashUntil");
        instance.markDirty();
        player.addEffect(new MobEffectInstance(MHAEffects.BAT_REST, 8, 0, true, false, false));
        player.setPose(Pose.STANDING);
        Vec3 position = hangingPosition(player, anchor);
        player.teleportTo(position.x, position.y, position.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0;
        player.serverLevel().updateSleepingPlayerList();
        return true;
    }

    public static boolean sleepingForNight(ServerPlayer player) {
        return hanging(player) && player.level().dimensionType().bedWorks()
                && (!player.level().isDay() || player.level().isThundering());
    }

    public static boolean restedLongEnough(ServerPlayer player) {
        if (!sleepingForNight(player)) return false;
        var instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.BAT.get()).orElse(null);
        return instance != null && instance.getTag() != null
                && player.level().getGameTime() - instance.getTag().getLong("BatRestStarted") >= 100;
    }

    public static void wake(LivingEntity owner) {
        boolean resting = owner.hasEffect(MHAEffects.BAT_REST);
        owner.removeEffect(MHAEffects.BAT_REST);
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.BAT.get()).orElse(null);
        if (instance != null && instance.getTag() != null && instance.getTag().getBoolean("BatHanging")) {
            instance.getTag().remove("BatHanging");
            instance.getTag().remove("BatAnchor");
            instance.getTag().remove("BatRestStarted");
            instance.markDirty();
            resting = true;
        }
        if (resting && owner instanceof ServerPlayer player) {
            player.setDeltaMovement(0, -.1, 0);
            player.hurtMarked = true;
            player.serverLevel().updateSleepingPlayerList();
        }
    }

    public static void clear(LivingEntity owner, ManasSkillInstance instance) {
        wake(owner);
        owner.removeEffect(MHAEffects.BATLIKE);
        if (instance != null && instance.getTag() != null) {
            instance.getTag().remove("BatHanging");
            instance.getTag().remove("BatAnchor");
            instance.getTag().remove("BatRestStarted");
            instance.getTag().remove("BatDashUntil");
            instance.getTag().remove("BatEchoUntil");
            instance.markDirty();
        }
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        var instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.BAT.get()).orElse(null);
        if (!player.isAlive() || player.isSpectator() || instance == null || instance.getMastery() < 0
                || !QuirkSkills.BAT.get().isInSlot(player, instance)) {
            if (player.hasEffect(MHAEffects.BATLIKE) || player.hasEffect(MHAEffects.BAT_REST)) clear(player, instance);
            return;
        }
        if (!active(player) || player.tickCount % 4 == 0)
            player.addEffect(new MobEffectInstance(MHAEffects.BATLIKE, 8, 0, true, false, false));
        if (player.isUsingItem()) player.stopUsingItem();
        if (instance.getTag() != null && instance.getTag().getBoolean("BatHanging")) {
            BlockPos anchor = BlockPos.of(instance.getTag().getLong("BatAnchor"));
            if (!canHang(player, anchor) || player.isPassenger() || player.isShiftKeyDown()
                    || player.position().distanceToSqr(Vec3.atCenterOf(anchor)) > 25) {
                wake(player);
            } else {
                if (player.tickCount % 4 == 0)
                    player.addEffect(new MobEffectInstance(MHAEffects.BAT_REST, 8, 0, true, false, false));
                Vec3 position = hangingPosition(player, anchor);
                if (player.position().distanceToSqr(position) > .01) player.teleportTo(position.x, position.y, position.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
                if (player.tickCount % 20 == 0 && player instanceof ServerPlayer serverPlayer)
                    serverPlayer.serverLevel().updateSleepingPlayerList();
            }
        }
        if (flying(player)) player.fallDistance = 0;
        if (dash(player).lengthSqr() > 0 && player.tickCount % 2 == 0 && player.level() instanceof ServerLevel level)
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + .5, player.getZ(), 4, .35, .2, .35, .015);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockInteraction(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!active(player)) return;
        if (hanging(player)) {
            if (!player.level().isClientSide()) wake(player);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (event.getHitVec().getDirection() == Direction.DOWN && canHang(player, event.getPos())) {
            if (player instanceof ServerPlayer serverPlayer) {
                var instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.BAT.get()).orElse(null);
                if (instance != null) hang(serverPlayer, instance, event.getPos());
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        } else if (!event.getItemStack().isEmpty()) event.setCanceled(true);
    }

    @SubscribeEvent public static void itemInteraction(PlayerInteractEvent.RightClickItem event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void entityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (active(event.getEntity()) && (hanging(event.getEntity()) || !event.getItemStack().isEmpty())) event.setCanceled(true);
    }
    @SubscribeEvent public static void specificInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (active(event.getEntity()) && (hanging(event.getEntity()) || !event.getItemStack().isEmpty())) event.setCanceled(true);
    }
    @SubscribeEvent public static void mining(PlayerInteractEvent.LeftClickBlock event) {
        if (active(event.getEntity()) && (hanging(event.getEntity()) || !event.getItemStack().isEmpty())) event.setCanceled(true);
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (active(event.getPlayer()) && (hanging(event.getPlayer()) || !event.getPlayer().getMainHandItem().isEmpty())) event.setCanceled(true);
    }
    @SubscribeEvent public static void attacking(AttackEntityEvent event) {
        if (active(event.getEntity()) && (hanging(event.getEntity()) || !event.getEntity().getMainHandItem().isEmpty())) event.setCanceled(true);
    }
    @SubscribeEvent public static void using(LivingEntityUseItemEvent.Start event) {
        if (active(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void recovery(LivingHealEvent event) {
        if (hanging(event.getEntity())) event.setAmount(event.getAmount() * 10);
    }
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && hanging(event.getEntity())) wake(event.getEntity());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity(), SkillAPI.getSkillsFrom(event.getEntity()).getSkill(QuirkSkills.BAT.get()).orElse(null));
    }
    @SubscribeEvent public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        clear(event.getEntity(), SkillAPI.getSkillsFrom(event.getEntity()).getSkill(QuirkSkills.BAT.get()).orElse(null));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        clear(event.getEntity(), SkillAPI.getSkillsFrom(event.getEntity()).getSkill(QuirkSkills.BAT.get()).orElse(null));
    }
}

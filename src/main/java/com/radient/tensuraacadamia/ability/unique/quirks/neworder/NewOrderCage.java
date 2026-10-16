package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// The Air Will Restrain You
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderCage {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;
    private static final double CAGE_SLACK = 0.35D;

    private record Cage(LivingEntity user, LivingEntity target, Vec3 center, long end) {}

    private static final List<Cage> CAGES = new ArrayList<>();

    // What each player's client was last told
    private static final Map<UUID, Integer> HELD_SENT = new HashMap<>();

    private NewOrderCage() {
    }

    static boolean restrain(ServerLevel level, LivingEntity player, ManasSkillInstance instance, int mode) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(player, CONFIG.restrainRange, false, true);
        if (target == null || target == player) {
            NewOrderUtil.fail(player, Component.translatable("tensura.targeting.not_targeted"));
            return false;
        }
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) {
            return false;
        }

        CAGES.removeIf(cage -> cage.target() == target);
        CAGES.add(new Cage(player, target, target.position(), level.getGameTime() + CONFIG.restrainSeconds * 20L));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new NewOrderPayloads.CagePayload(target.getId(), CONFIG.restrainSeconds * 20));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        instance.addMasteryPoint(player);

        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SMALL_GUST, center.x, center.y, center.z, 3, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.0D);
        NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_INHALE, 1.2F, 1.4F);
        NewOrderUtil.playSound(level, center, TensuraSoundEvents.COERCION.get(), 0.8F, 1.2F);
        NewOrderUtil.callout(level, new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.8D, player.getZ()), player, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.AIR]);
        return true;
    }

    static boolean isCaged(LivingEntity entity) {
        return !CAGES.isEmpty() && CAGES.stream().anyMatch(cage -> cage.target() == entity);
    }

    // true once the cage is gone
    private static boolean tickCage(Cage cage, long time) {
        LivingEntity target = cage.target();
        if (time >= cage.end() || !target.isAlive() || target.isRemoved() || !cage.user().isAlive() || !NewOrderRules.hasRule(cage.user(), NewOrderRule.AIR_CONTROL)) {
            release(cage);
            return true;
        }

        Vec3 center = cage.center();
        target.setDeltaMovement(Vec3.ZERO);
        target.resetFallDistance();
        if (target.position().distanceToSqr(center) > CAGE_SLACK * CAGE_SLACK) {
            target.teleportTo(center.x, center.y, center.z);
        }
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return false;
    }

    private static void release(Cage cage) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(cage.target(), new NewOrderPayloads.CagePayload(cage.target().getId(), 0));
        if (cage.target().level() instanceof ServerLevel level) {
            Vec3 center = cage.target().getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            NewOrderUtil.playSound(level, center, SoundEvents.BREEZE_IDLE_AIR, 0.8F, 1.4F);
        }
    }

    static int getHeldFlags(LivingEntity target) {
        int seals = NewOrderSeals.sealFlags(target);
        int flags = (seals & NewOrderSeals.SEAL_NO_MOVE) != 0 ? NewOrderPayloads.HeldPayload.ROOTED : 0;
        if (isCaged(target)) {
            flags |= NewOrderPayloads.HeldPayload.CAGED;
        }
        if ((seals & NewOrderSeals.SEAL_NO_ITEMS) != 0) {
            flags |= NewOrderPayloads.HeldPayload.NO_ITEMS;
        }
        return flags;
    }

    private static void tellHeld(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            int flags = player.isAlive() ? getHeldFlags(player) : 0;
            if (flags != HELD_SENT.getOrDefault(player.getUUID(), 0)) {
                PacketDistributor.sendToPlayer(player, new NewOrderPayloads.HeldPayload(flags));
                if (flags == 0) {
                    HELD_SENT.remove(player.getUUID());
                } else {
                    HELD_SENT.put(player.getUUID(), flags);
                }
            }
        }
    }

    static void forget(LivingEntity entity) {
        CAGES.removeIf(cage -> {
            if (cage.user() == entity) {
                release(cage);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CAGES.isEmpty()) {
            long time = event.getServer().overworld().getGameTime();
            CAGES.removeIf(cage -> tickCage(cage, time));
        }
        if (!CAGES.isEmpty() || !HELD_SENT.isEmpty() || NewOrderSeals.hasSeals()) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                tellHeld(level.players());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CAGES.clear();
        HELD_SENT.clear();
    }

}

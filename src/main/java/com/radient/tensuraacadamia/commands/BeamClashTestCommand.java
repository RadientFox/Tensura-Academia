package com.radient.tensuraacadamia.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.BeamClashManager;
import com.radient.tensuraacadamia.ability.unique.quirks.BeamsFromHisEyesQuirk;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class BeamClashTestCommand {
    private static final String TEST_DUMMY_TAG = "tracadamia_beam_clash_test_dummy";
    private static final String TEST_TARGET_TAG = "tracadamia_beam_clash_test_target";
    private static final String NEXT_CAST_TAG = "tracadamia_beam_clash_test_next_cast";
    private static final String TEST_AURA_TAG = "tracadamia_beam_clash_test_aura";
    private static final double TEST_AURA = 100_000.0D;
    private static final long TEST_CAST_INTERVAL_TICKS = 10L * 20L;

    private BeamClashTestCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("beamclash_test_eld")
                .requires(source -> source.hasPermission(2))
                .executes(context -> summonDummy(context, TEST_AURA))
                .then(Commands.argument("aura", DoubleArgumentType.doubleArg(1.0D))
                        .executes(context -> summonDummy(context,
                                DoubleArgumentType.getDouble(context, "aura")))));
    }

    private static int summonDummy(CommandContext<CommandSourceStack> context, double aura)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;

        for (Entity entity : player.serverLevel().getEntities().getAll()) {
            if (entity instanceof Zombie existing
                    && existing.getPersistentData().getBoolean(TEST_DUMMY_TAG)
                    && existing.getPersistentData().hasUUID(TEST_TARGET_TAG)
                    && existing.getPersistentData().getUUID(TEST_TARGET_TAG).equals(player.getUUID())) {
                context.getSource().sendSuccess(() -> Component.literal(
                        "You already have an ELD test dummy in this dimension."), false);
                return 1;
            }
        }

        Vec3 forward = player.getLookAngle().normalize();
        Vec3 spawnPosition = player.position().add(forward.scale(10.0D));
        Zombie dummy = EntityType.ZOMBIE.create(player.serverLevel());
        if (dummy == null) return 0;
        dummy.moveTo(spawnPosition.x, spawnPosition.y, spawnPosition.z,
                player.getYRot() + 180.0F, 0.0F);
        dummy.setCustomName(Component.literal("ELD Test Dummy"));
        dummy.setCustomNameVisible(true);
        dummy.setPersistenceRequired();
        dummy.setNoAi(true);
        dummy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2_000.0D);
        dummy.setHealth(2_000.0F);
        player.serverLevel().addFreshEntity(dummy);

        if (!SkillAPI.getSkillsFrom(dummy).learnSkill(QuirkSkills.BEAMS_FROM_HIS_EYES.get())) {
            dummy.discard();
            context.getSource().sendFailure(Component.literal(
                    "Could not give Beams From His Eyes to the test dummy."));
            return 0;
        }

        ManasSkillInstance skill = SkillAPI.getSkillsFrom(dummy)
                .getSkill(QuirkSkills.BEAMS_FROM_HIS_EYES.get()).orElse(null);
        if (skill == null) {
            dummy.discard();
            context.getSource().sendFailure(Component.literal(
                    "The test dummy did not retain Beams From His Eyes."));
            return 0;
        }
        skill.addMasteryPoint(dummy, 5_000);
        skill.markDirty();
        dummy.getPersistentData().putBoolean(TEST_DUMMY_TAG, true);
        dummy.getPersistentData().putUUID(TEST_TARGET_TAG, player.getUUID());
        dummy.getPersistentData().putDouble(TEST_AURA_TAG, aura);
        var existence = TensuraStorages.getExistenceFrom(dummy);
        existence.setAura(aura);
        existence.markDirty();
        if (startBeam(dummy, player)) {
            dummy.getPersistentData().putLong(NEXT_CAST_TAG,
                    player.serverLevel().getGameTime() + TEST_CAST_INTERVAL_TICKS);
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "Summoned a 2,000 HP ELD test dummy with " + formatAura(aura)
                        + " aura. It will use ELD once every 10 seconds."), false);
        return 1;
    }

    private static String formatAura(double aura) {
        return String.format(java.util.Locale.ROOT, "%,.0f", aura);
    }

    @SubscribeEvent
    public static void tickTestDummies(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (!(entity instanceof Zombie dummy)
                        || !dummy.getPersistentData().getBoolean(TEST_DUMMY_TAG)
                        || !dummy.isAlive()) {
                    continue;
                }

                UUID targetId = dummy.getPersistentData().getUUID(TEST_TARGET_TAG);
                ServerPlayer target = event.getServer().getPlayerList().getPlayer(targetId);
                if (target == null || !target.isAlive() || target.serverLevel() != level) continue;

                Vec3 aim = target.getEyePosition().subtract(dummy.getEyePosition()).normalize();
                BeamsFromHisEyesQuirk.updateTestBeamAim(dummy.getUUID(), aim);
                if (BeamsFromHisEyesQuirk.isBeamActive(dummy.getUUID())
                        || BeamClashManager.getControl(dummy.getUUID()) != null) {
                    continue;
                }
                long now = level.getGameTime();
                if (now < dummy.getPersistentData().getLong(NEXT_CAST_TAG)) continue;

                ManasSkillInstance skill = SkillAPI.getSkillsFrom(dummy)
                        .getSkill(QuirkSkills.BEAMS_FROM_HIS_EYES.get()).orElse(null);
                if (skill == null || skill.onCoolDown(1)) continue;

                var existence = TensuraStorages.getExistenceFrom(dummy);
                existence.setAura(dummy.getPersistentData().getDouble(TEST_AURA_TAG));
                existence.markDirty();
                if (BeamsFromHisEyesQuirk.startTestEveryLastDrop(dummy, aim)) {
                    dummy.getPersistentData().putLong(NEXT_CAST_TAG, now + TEST_CAST_INTERVAL_TICKS);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof Zombie dummy && dummy.getPersistentData().getBoolean(TEST_DUMMY_TAG)) {
                    dummy.getPersistentData().remove(TEST_DUMMY_TAG);
                    dummy.getPersistentData().remove(TEST_TARGET_TAG);
                }
            }
        }
    }

    private static boolean startBeam(Zombie dummy, ServerPlayer target) {
        Vec3 aim = target.getEyePosition().subtract(dummy.getEyePosition()).normalize();
        return BeamsFromHisEyesQuirk.startTestEveryLastDrop(dummy, aim);
    }
}

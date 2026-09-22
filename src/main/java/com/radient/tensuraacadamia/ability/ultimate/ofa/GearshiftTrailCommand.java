package com.radient.tensuraacadamia.ability.ultimate.ofa;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.radient.tensuraacadamia.TensuraAcadamia;
import dev.architectury.networking.NetworkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class GearshiftTrailCommand {
    private static final int FULL_COWL_RED_LIGHTNING_TICKS = 32;
    private static final int FULL_COWL_PERCENT = 5;
    private static final double SEND_RANGE = 128.0D;

    private GearshiftTrailCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gearshifttrail")
                .requires(source -> source.hasPermission(2))
                .executes(context -> activateGearshiftTrail(context.getSource(), 200, 5))
                .then(Commands.argument("duration", IntegerArgumentType.integer(20, 2400))
                        .executes(context -> activateGearshiftTrail(context.getSource(), IntegerArgumentType.getInteger(context, "duration"), 5))
                        .then(Commands.argument("gear", IntegerArgumentType.integer(0, 5))
                                .executes(context -> activateGearshiftTrail(context.getSource(), IntegerArgumentType.getInteger(context, "duration"), IntegerArgumentType.getInteger(context, "gear"))))));

        event.getDispatcher().register(Commands.literal("fullcowl")
                .requires(source -> source.hasPermission(2))
                .executes(context -> activateFullCowl(context.getSource())));
    }

    private static int activateGearshiftTrail(CommandSourceStack source, int duration, int gear) {
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }

        com.radient.tensuraacadamia.ability.ultimate.ofa.GearshiftTrailPayload payload = new GearshiftTrailPayload(player.getId(), duration, gear);
        AABB area = player.getBoundingBox().inflate(SEND_RANGE);

        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, area)) {
            PacketDistributor.sendToPlayer(other, payload);
        }

        source.sendSuccess(() -> Component.literal("Activated Gearshift trail."), true);
        return 1;
    }

    private static int activateFullCowl(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }

        FullCowlLightningPayload payload = new FullCowlLightningPayload(player.getId(), FULL_COWL_RED_LIGHTNING_TICKS, FULL_COWL_PERCENT, false, false);
        AABB area = player.getBoundingBox().inflate(SEND_RANGE);

        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, area)) {
            NetworkManager.sendToPlayer(other, payload);
        }

        source.sendSuccess(() -> Component.literal("full cowl activate"), true);
        return 1;
    }
}

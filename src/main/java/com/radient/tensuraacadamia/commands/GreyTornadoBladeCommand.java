package com.radient.tensuraacadamia.commands;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.entity.GreyTornadoBlade;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class GreyTornadoBladeCommand {
    private GreyTornadoBladeCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("Imgoingtosleepgng")
                .requires(source -> source.hasPermission(2) && source.getEntity() instanceof ServerPlayer)
                .executes(context -> summon(context.getSource().getPlayerOrException())));
    }

    private static int summon(ServerPlayer player) {
        GreyTornadoBlade blade = new GreyTornadoBlade(player.serverLevel(), player);
        if (!player.serverLevel().addFreshEntity(blade)) {
            player.sendSystemMessage(Component.literal("Could not summon the tornado blade."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Grey Tornado Blade summoned for 20 seconds."));
        return 1;
    }
}

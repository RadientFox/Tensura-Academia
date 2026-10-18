package com.radient.tensuraacadamia.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.RewindQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.LongBinaryOperator;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class RewindCommand {

    private static final int TICKS_PER_SECOND = 20;

    private RewindCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tra")
                .then(Commands.literal("rewind")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .executes(context -> get(context.getSource(), List.of(context.getSource().getEntityOrException())))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> get(context.getSource(), EntityArgument.getEntities(context, "targets")))))
                        .then(change("set", "Set", (stock, amount) -> amount))
                        .then(change("add", "Added", (stock, amount) -> stock + amount))
                        .then(change("remove", "Removed", (stock, amount) -> stock - amount))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, String verb, LongBinaryOperator operation) {
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.0D))
                                .executes(context -> change(context.getSource(), EntityArgument.getEntities(context, "targets"),
                                        toTicks(DoubleArgumentType.getDouble(context, "seconds")), verb, operation))));
    }

    private static int toTicks(double seconds) {
        return (int) Math.min(Integer.MAX_VALUE, Math.round(seconds * TICKS_PER_SECOND));
    }

    private static String seconds(int ticks) {
        return String.format("%.1f", ticks / (float) TICKS_PER_SECOND);
    }

    private static int get(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> rewind = target instanceof LivingEntity living ? RewindQuirk.getRewind(living) : Optional.empty();
            if (rewind.isEmpty()) {
                continue;
            }

            String stored = seconds(RewindQuirk.getStock(rewind.get()));
            source.sendSuccess(() -> Component.literal(target.getName().getString() + " has " + stored + "s of time stored"), false);
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Rewind"));
        }
        return count;
    }

    private static int change(CommandSourceStack source, Collection<? extends Entity> targets, int ticks, String verb, LongBinaryOperator operation) {
        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> rewind = target instanceof LivingEntity living ? RewindQuirk.getRewind(living) : Optional.empty();
            if (rewind.isEmpty()) {
                continue;
            }

            long result = Math.max(0L, Math.min(Integer.MAX_VALUE, operation.applyAsLong(RewindQuirk.getStock(rewind.get()), ticks)));
            RewindQuirk.setStock(rewind.get(), (int) result);
            if (targets.size() == 1) {
                String stored = seconds((int) result);
                source.sendSuccess(() -> Component.literal(target.getName().getString() + " now has " + stored + "s of time stored"), true);
            }
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Rewind"));
            return 0;
        }

        if (targets.size() > 1) {
            int total = count;
            String amount = seconds(ticks);
            String suffix = verb.equals("Set") ? " stored time to " + amount + "s for " : " " + amount + "s of stored time for ";
            source.sendSuccess(() -> Component.literal(verb + suffix + total + " targets"), true);
        }
        return count;
    }
}

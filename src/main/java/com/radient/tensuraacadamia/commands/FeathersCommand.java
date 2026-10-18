package com.radient.tensuraacadamia.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.FierceWingsQuirk;
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
public final class FeathersCommand {

    private FeathersCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tra")
                .then(Commands.literal("feathers")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .executes(context -> get(context.getSource(), List.of(context.getSource().getEntityOrException())))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> get(context.getSource(), EntityArgument.getEntities(context, "targets")))))
                        .then(change("set", "Set", (feathers, amount) -> amount))
                        .then(change("add", "Added", (feathers, amount) -> feathers + amount))
                        .then(change("remove", "Removed", (feathers, amount) -> feathers - amount))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, String verb, LongBinaryOperator operation) {
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("feathers", IntegerArgumentType.integer(0))
                                .executes(context -> change(context.getSource(), EntityArgument.getEntities(context, "targets"),
                                        IntegerArgumentType.getInteger(context, "feathers"), verb, operation))));
    }

    private static Optional<ManasSkillInstance> wings(Entity entity) {
        return entity instanceof LivingEntity living ? FierceWingsQuirk.getFierceWings(living) : Optional.empty();
    }

    private static int get(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> wings = wings(target);
            if (wings.isEmpty()) {
                continue;
            }

            int feathers = FierceWingsQuirk.getFeathers(wings.get());
            int max = FierceWingsQuirk.getMaxFeathers(wings.get());
            source.sendSuccess(() -> Component.literal(target.getName().getString() + " has " + feathers + "/" + max + " feathers"), false);
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Fierce Wings"));
        }
        return count;
    }

    private static int change(CommandSourceStack source, Collection<? extends Entity> targets, int amount, String verb, LongBinaryOperator operation) {
        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> wings = wings(target);
            if (wings.isEmpty()) {
                continue;
            }

            long result = Math.max(0L, Math.min(Integer.MAX_VALUE, operation.applyAsLong(FierceWingsQuirk.getFeathers(wings.get()), amount)));
            FierceWingsQuirk.setFeathers(wings.get(), (int) result);
            if (targets.size() == 1) {
                int feathers = FierceWingsQuirk.getFeathers(wings.get());
                int max = FierceWingsQuirk.getMaxFeathers(wings.get());
                source.sendSuccess(() -> Component.literal(target.getName().getString() + " now has " + feathers + "/" + max + " feathers"), true);
            }
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Fierce Wings"));
            return 0;
        }

        if (targets.size() > 1) {
            int total = count;
            String suffix = verb.equals("Set") ? " feathers to " + amount + " for " : " " + amount + " feathers for ";
            source.sendSuccess(() -> Component.literal(verb + suffix + total + " targets"), true);
        }
        return count;
    }
}

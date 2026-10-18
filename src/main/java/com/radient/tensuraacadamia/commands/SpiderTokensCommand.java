package com.radient.tensuraacadamia.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.SpideySmartsSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
public final class SpiderTokensCommand {

    private static final List<String> TYPES = List.of(SpideySmartsSkill.COMBAT, SpideySmartsSkill.RAID, SpideySmartsSkill.RESEARCH);

    private SpiderTokensCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tra")
                .then(Commands.literal("spider")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("tokens")
                                .then(Commands.literal("get")
                                        .executes(context -> get(context.getSource(), List.of(context.getSource().getEntityOrException())))
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .executes(context -> get(context.getSource(), EntityArgument.getEntities(context, "targets")))))
                                .then(change("set", "Set", (tokens, amount) -> amount))
                                .then(change("add", "Added", (tokens, amount) -> tokens + amount))
                                .then(change("remove", "Removed", (tokens, amount) -> tokens - amount)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, String verb, LongBinaryOperator operation) {
        return Commands.literal(name)
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> change(context.getSource(), EntityArgument.getEntities(context, "targets"),
                                                StringArgumentType.getString(context, "type"), IntegerArgumentType.getInteger(context, "amount"), verb, operation)))));
    }

    private static Optional<ManasSkillInstance> smarts(Entity entity) {
        return entity instanceof LivingEntity living ? SpideySmartsSkill.getSmarts(living) : Optional.empty();
    }

    private static String describe(ManasSkillInstance instance) {
        return SpideySmartsSkill.getTokens(instance, SpideySmartsSkill.COMBAT) + " combat, "
                + SpideySmartsSkill.getTokens(instance, SpideySmartsSkill.RAID) + " raid, "
                + SpideySmartsSkill.getTokens(instance, SpideySmartsSkill.RESEARCH) + " research";
    }

    private static int get(CommandSourceStack source, Collection<? extends Entity> targets) {
        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> smarts = smarts(target);
            if (smarts.isEmpty()) {
                continue;
            }

            String text = describe(smarts.get());
            source.sendSuccess(() -> Component.literal(target.getName().getString() + " has " + text + " tokens"), false);
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Spidey Smarts"));
        }
        return count;
    }

    private static int change(CommandSourceStack source, Collection<? extends Entity> targets, String type, int amount, String verb, LongBinaryOperator operation) {
        if (!TYPES.contains(type)) {
            source.sendFailure(Component.literal("Token type must be combat, raid or research"));
            return 0;
        }

        int count = 0;
        for (Entity target : targets) {
            Optional<ManasSkillInstance> smarts = smarts(target);
            if (smarts.isEmpty()) {
                continue;
            }

            long result = Math.max(0L, Math.min(Integer.MAX_VALUE, operation.applyAsLong(SpideySmartsSkill.getTokens(smarts.get(), type), amount)));
            SpideySmartsSkill.setTokens(smarts.get(), type, (int) result);
            if (targets.size() == 1) {
                String text = describe(smarts.get());
                source.sendSuccess(() -> Component.literal(target.getName().getString() + " now has " + text + " tokens"), true);
            }
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Spidey Smarts"));
            return 0;
        }

        if (targets.size() > 1) {
            int total = count;
            String suffix = verb.equals("Set") ? " " + type + " tokens to " + amount + " for " : " " + amount + " " + type + " tokens for ";
            source.sendSuccess(() -> Component.literal(verb + suffix + total + " targets"), true);
        }
        return count;
    }
}

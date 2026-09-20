package com.radient.tensuraacadamia.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.FatAbsorptionQuirk;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
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
import java.util.Optional;

// /tra fat set <targets> <amount>
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class FatAbsorptionCommand {

    private FatAbsorptionCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tra")
                .then(Commands.literal("fat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> setFat(context.getSource(), EntityArgument.getEntities(context, "targets"), DoubleArgumentType.getDouble(context, "amount"))))))));
    }

    private static int setFat(CommandSourceStack source, Collection<? extends Entity> targets, double amount) {
        int count = 0;

        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                continue;
            }

            Optional<ManasSkillInstance> instance = SkillAPI.getSkillsFrom(living).getLearnedSkills().stream()
                    .filter(skill -> skill.getSkill() instanceof FatAbsorptionQuirk)
                    .findFirst();
            if (instance.isEmpty()) {
                continue;
            }

            FatAbsorptionQuirk.setStoredFat(instance.get(), living, amount);
            count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Target doesn't have Fat Absorption"));
            return 0;
        }

        int total = count;
        source.sendSuccess(() -> Component.literal("Set stockpiled fat to " + amount + " for " + total + " target"), true);
        return count;
    }

}

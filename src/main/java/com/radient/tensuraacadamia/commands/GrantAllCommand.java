package com.radient.tensuraacadamia.commands;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// /tra grantall [targets]
// I was tired of granting myself a shit ton of quirks and this was easy to make
// I needed this to better test new order's rebel since it used to randomly break and not take quirks
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class GrantAllCommand {

    private GrantAllCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tra")
                .then(Commands.literal("grantall")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> grantAll(context.getSource(), List.of(context.getSource().getEntityOrException())))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(context -> grantAll(context.getSource(), EntityArgument.getEntities(context, "targets"))))));
    }

    private static List<ManasSkill> getQuirks() {
        List<ManasSkill> quirks = new ArrayList<>();
        for (ResourceLocation id : SkillAPI.getSkillRegistry().getIds()) {
            ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
            if (TensuraAcadamia.MODID.equals(id.getNamespace()) && skill instanceof Skill typed
                    && (typed.getType() == Skill.SkillType.UNIQUE || typed.getType() == Skill.SkillType.ULTIMATE)) {
                quirks.add(skill);
            }
        }
        return quirks;
    }

    private static int grantAll(CommandSourceStack source, Collection<? extends Entity> targets) {
        List<ManasSkill> quirks = getQuirks();
        int granted = 0;
        int count = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                continue;
            }

            count++;
            for (ManasSkill quirk : quirks) {
                ManasSkillInstance instance = quirk.createDefaultInstance();
                instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
                if (SkillHelper.learnSkill(living, instance, -1, null)) {
                    granted++;
                }
            }
        }

        if (count == 0) {
            source.sendFailure(Component.literal("No living targets"));
            return 0;
        }

        int total = granted;
        int living = count;
        source.sendSuccess(() -> Component.literal("Granted " + total + " quirks to " + living + " target" + (living == 1 ? "" : "s")), true);
        return granted;
    }

}

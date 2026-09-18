package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AllForOneTestCommand {
    private AllForOneTestCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afo_test_steve")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    Zombie zombie = EntityType.ZOMBIE.create(player.serverLevel());
                    if (zombie == null) return 0;
                    zombie.moveTo(player.getX() + 2.0D, player.getY(), player.getZ() + 2.0D, 0.0F, 0.0F);
                    zombie.setCustomName(Component.literal("Steve"));
                    zombie.setCustomNameVisible(true);
                    player.serverLevel().addFreshEntity(zombie);
                    if (!SkillAPI.getSkillsFrom(zombie).learnSkill(QuirkSkills.GEARSHIFT.get())) {
                        zombie.discard();
                        context.getSource().sendFailure(Component.literal("Could not give Gearshift to test Steve."));
                        return 0;
                    }
                    context.getSource().sendSuccess(() -> Component.literal("Summoned Steve with Gearshift."), false);
                    return 1;
                }));
    }
}

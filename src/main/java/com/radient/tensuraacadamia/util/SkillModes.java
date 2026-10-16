package com.radient.tensuraacadamia.util;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class SkillModes {

    private static final int CHECK_TICKS = 20;

    private SkillModes() {
    }

    public static boolean padCooldowns(ManasSkillInstance instance) {
        List<Integer> cooldowns = instance.getCooldownList();
        int modes = instance.getModes();
        if (cooldowns.size() >= modes) {
            return false;
        }

        List<Integer> longer = new ArrayList<>(cooldowns);
        while (longer.size() < modes) {
            longer.add(0);
        }
        instance.setCoolDownList(longer);
        instance.markDirty();
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0) {
            return;
        }

        SkillStorage skills = SkillAPI.getSkillsFrom(player);
        boolean changed = false;
        for (ManasSkillInstance instance : skills.getLearnedSkills()) {
            if (TensuraAcadamia.MODID.equals(instance.getSkillId().getNamespace())) {
                changed |= padCooldowns(instance);
            }
        }
        if (changed) {
            skills.markDirty();
        }
    }

}

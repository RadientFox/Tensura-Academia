package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

final class QuirkCastCosts {
    private QuirkCastCosts() { }

    static boolean hasAura(LivingEntity owner, double cost) {
        if (TensuraStorages.getExistenceFrom(owner).getAura() >= cost) return true;
        if (owner instanceof Player player)
            player.displayClientMessage(Component.literal("Not enough aura: " + (int) cost + " required."), true);
        return false;
    }

    static void spendAura(LivingEntity owner, double cost) {
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setAura(energy.getAura() - cost);
        energy.markDirty();
    }

    static void cooldown(ManasSkillInstance instance, int mode, int seconds, int modes) {
        var cooldowns = new ArrayList<>(instance.getCooldownList());
        while (cooldowns.size() < modes) cooldowns.add(0);
        instance.setCoolDownList(cooldowns);
        instance.setCoolDown(seconds, mode);
        instance.markDirty();
    }
}

package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.AFOConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.magic.spiritual.SpiritualMagic.SpiritLevel;
import io.github.manasmods.tensura.registry.item.TensuraMobDropItems;
import io.github.manasmods.tensura.registry.skill.UniqueSkills;
import io.github.manasmods.tensura.storage.spirit.SpiritStorage;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AllForOneAcquisition {
    private static final int REQUIRED_ESSENCE = 100;
    private static final double REQUIRED_AP = 1_000_000.0D;

    private AllForOneAcquisition() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        if (!ConfigRegistry.getConfig(AFOConfig.class).allowUsurperEvolution) return;
        var skills = SkillAPI.getSkillsFrom(player);
        if (skills.getSkill(QuirkSkills.ALL_FOR_ONE.get().getRegistryName()).isPresent()) return;

        ManasSkillInstance usurper = skills.getSkill(UniqueSkills.USURPER.get().getRegistryName()).orElse(null);
        if (usurper == null || !usurper.isMastered(player) || usurper.isTemporarySkill()) return;
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) > 0) return;
        SpiritLevel spirit = SpiritStorage.getSpiritLevel(player, Element.DARKNESS);
        if (spirit == null || spirit.ordinal() < SpiritLevel.GREATER.ordinal()) return;
        if (EnergyHelper.getBaseMaxAura(player) < REQUIRED_AP) return;

        Item essence = TensuraMobDropItems.DAEMON_ESSENCE.get();
        int available = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(essence)) available += stack.getCount();
        }
        if (available < REQUIRED_ESSENCE) return;

        TensuraSkillInstance allForOne = new TensuraSkillInstance(QuirkSkills.ALL_FOR_ONE.get());
        allForOne.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        if (!SkillHelper.learnSkill(player, allForOne)) return;

        int remaining = REQUIRED_ESSENCE;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.is(essence)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (remaining == 0) break;
        }
        EnergyHelper.setMaxAura(player, EnergyHelper.getBaseMaxAura(player) - REQUIRED_AP);
        skills.forgetSkill(UniqueSkills.USURPER.get());
        player.sendSystemMessage(Component.literal("Usurper evolved into All For One."));
    }
}

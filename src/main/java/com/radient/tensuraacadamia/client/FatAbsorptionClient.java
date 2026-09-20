package com.radient.tensuraacadamia.client;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.FatAbsorptionQuirk;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.client.model.PlayerModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class FatAbsorptionClient {

    // fat stock torso
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        float growth = SkillAPI.getSkillsFrom(event.getEntity()).getLearnedSkills().stream()
                .filter(instance -> instance.getSkill() instanceof FatAbsorptionQuirk && instance.getMastery() >= 0.0D)
                .findFirst()
                .map(FatAbsorptionQuirk::getTorsoGrowth)
                .orElse(0.0F);

        setTorsoScale(event.getRenderer().getModel(), FatAbsorptionQuirk.getTorsoWidth(growth), FatAbsorptionQuirk.getTorsoDepth(growth));
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        setTorsoScale(event.getRenderer().getModel(), 1.0F, 1.0F);
    }

    private static void setTorsoScale(PlayerModel<?> model, float width, float depth) {
        model.body.xScale = width;
        model.body.zScale = depth;
        model.jacket.xScale = width;
        model.jacket.zScale = depth;
    }

}

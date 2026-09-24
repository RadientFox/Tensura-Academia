package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.authlib.GameProfile;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.intrinsic.CharmSkill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class LoveQuirk extends Skill {
    private static final QuirkSkillsConfig.QuirkBestowal CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).QuirkBestowal;
    public static final ResourceLocation QUIRK_BESTOWAL = ResourceLocation.fromNamespaceAndPath("tracadamia", "quirk_bestowal");

    public LoveQuirk() {
        super(SkillType.UNIQUE);
    }

    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    public double getDefaultAcquiringMagiculeCost() {
        return CONFIG.mpAcquirement;
    }

    public int getModes(ManasSkillInstance instance) {
        return 1;
    }


    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/quirk_bestowal.png");
    }

    public String getModeId(ManasSkillInstance instance, int mode) {
        String var10000;
        switch (mode) {
            case 0 -> var10000 = "love.affection";
            default -> var10000 = super.getModeId(instance, mode);
        }

        return var10000;
    }


    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {

        Minecraft mc = Minecraft.getInstance();
        GameProfile playerProfile = mc.getGameProfile();
        IExistence existence = TensuraStorages.getExistenceFrom(entity);

        UUID uuid = entity.getUUID();
        Level level = entity.level();

        if (existence.getPermanentOwner() != null) {
            UUID uuid2 = existence.getPermanentOwner();
            if (Objects.equals(existence.getPermanentOwner(), uuid)) {

                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("tracadamia.skill.love.no_buff").setStyle(Style.EMPTY.withColor(ChatFormatting.RED)), false);
                }
            }else {


            }
        }





    }

}

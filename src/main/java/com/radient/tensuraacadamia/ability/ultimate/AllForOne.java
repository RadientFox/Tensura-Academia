package com.radient.tensuraacadamia.ability.ultimate;

import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneMenu;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneTheme;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public class AllForOne extends Skill {
    public static final double TARGET_RANGE = 8.0D;

    public AllForOne() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/ultimate/all_for_one.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "all_for_one.steal";
            case 1 -> "all_for_one.transfer";
            case 2 -> "all_for_one.stock";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) AllForOneTheme.tick(player);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) AllForOneTheme.stop(player);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) AllForOneTheme.tick(player);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (mode == 2) {
            AllForOneMenu.open(player, player, 2);
            return;
        }
        if (mode != 0 && mode != 1) return;
        if (instance.onCoolDown(mode)) {
            player.displayClientMessage(Component.literal("All For One is on cooldown."), true);
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, TARGET_RANGE, false);
        if (target == null || !target.isAlive() || target == entity) {
            player.displayClientMessage(Component.literal("Look at a living target within 8 blocks."), true);
            return;
        }
        AllForOneMenu.open(player, target, mode);
    }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class DoubleQuirk extends Skill {
    public static final int DOUBLE = 0;
    public static final int STUDY = 1;
    public static final int CHOOSE_FIGHTER = 2;
    public static final int SAD_MANS_PARADE = 3;
    public static final int MAX_MASTERY = 2_500;
    public static final int PARADE_CLONES = 50;
    public static final int RESEARCH_STACK_TICKS = 40;
    public static final int NORMAL_DUPLICATE_LIMIT = 2;
    public static final int MASTERED_DUPLICATE_LIMIT = 3;
    public static final double NORMAL_HEALTH_FRACTION = 0.1D;
    public static final double MASTERED_HEALTH_FRACTION = 1.0D / 8.0D;
    public static final double PARADE_HEALTH_FRACTION = 1.0D / 64.0D;
    public static final double NORMAL_AURA_FRACTION = 0.01D;
    public static final double PARADE_AURA_FRACTION = 0.2D;
    public static final double INTERACTION_REACH_FALLBACK = 3.0D;
    public static final double MINIMUM_DUPLICATE_HEALTH = 1.0D;

    public DoubleQuirk() {
        super(SkillType.UNIQUE);
    }

    private static void summonSelected(ManasSkillInstance instance, ServerPlayer creator) {
        DoubleResearchData.ensurePlayerEntry(creator);
        UUID selectedId = DoubleResearchData.selected(creator);
        boolean self = creator.getUUID().equals(selectedId);
        if (!DoubleResearchData.isUnlocked(creator, selectedId)) {
            creator.displayClientMessage(Component.translatable("tracadamia.skill.double.no_selection"), true);
            return;
        }
        var snapshot = DoubleResearchData.snapshot(creator, selectedId);
        if (snapshot == null) {
            creator.displayClientMessage(Component.translatable("tracadamia.skill.double.no_selection"), true);
            return;
        }
        double cost = DoubleCloneManager.auraCost(creator, NORMAL_AURA_FRACTION);
        if (DoubleCloneManager.summonOne(creator, snapshot, selectedId, self, cost)) {
            instance.addMasteryPoint(creator);
            instance.markDirty();
        }
    }

    private static void openSelectionMenu(ServerPlayer player) {
        DoubleResearchData.ensurePlayerEntry(player);
        String researchName = DoubleResearchData.researchName(player);
        int stacks = DoubleResearchData.researchStacks(player);
        int percent = DoubleResearchData.progressPercent(player);
        var entries = DoubleResearchData.entries(player);
        UUID selected = DoubleResearchData.selected(player);
        int living = DoubleCloneManager.countNonSelf(player);
        player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                        new DoubleMenu(containerId, inventory, player, entries, selected,
                                researchName, stacks, percent, living),
                        Component.translatable("tracadamia.skill.double.menu.title")),
                buffer -> {
                    DoubleMenu menu = new DoubleMenu(0, player.getInventory(), player, entries,
                            selected, researchName, stacks, percent, living);
                    menu.writeOpenData(buffer);
                });
    }

    @Nullable
    private static LivingEntity lookedAtLivingEntity(ServerPlayer player) {
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        if (!Double.isFinite(reach) || reach <= 0.0D) reach = INTERACTION_REACH_FALLBACK;
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(player, reach, false);
        return target != null && target != player && target.isAlive() && !target.isSpectator()
                && player.hasLineOfSight(target) ? target : null;
    }

    public static boolean hasMastery(ServerPlayer player) {
        ManasSkillInstance instance = io.github.manasmods.manascore.skill.api.SkillAPI.getSkillsFrom(player)
                .getSkill(com.radient.tensuraacadamia.regestry.skills.QuirkSkills.DOUBLE.get().getRegistryName())
                .orElse(null);
        return instance != null && instance.isMastered(player);
    }

    public static int duplicateLimit(ServerPlayer player) {
        return hasMastery(player) ? MASTERED_DUPLICATE_LIMIT : NORMAL_DUPLICATE_LIMIT;
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.Clone event) {
        DoubleResearchData.copyOnRespawn(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DoubleResearchData.clearStudy(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DoubleResearchData.clearStudy(player);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/doubleicon.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0.0D;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return 0.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= MAX_MASTERY ? 4 : 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case DOUBLE -> "double.double";
            case STUDY -> "double.study";
            case CHOOSE_FIGHTER -> "double.choose_fighter";
            case SAD_MANS_PARADE -> "double.sad_mans_parade";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public int getMaxHeldTime(ManasSkillInstance instance, LivingEntity entity) {
        return 12_000;
    }

    @Override
    public boolean canInteractSkill(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || entity.level().isClientSide) return;
        if (mode != STUDY) DoubleResearchData.clearStudy(player);

        if (mode == DOUBLE) {
            summonSelected(instance, player);
        } else if (mode == CHOOSE_FIGHTER) {
            openSelectionMenu(player);
        } else if (mode == SAD_MANS_PARADE) {
            double cost = DoubleCloneManager.auraCost(player, PARADE_AURA_FRACTION);
            if (DoubleCloneManager.summonParade(player, cost)) {
                instance.addMasteryPoint(player);
                instance.markDirty();
            }
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != STUDY || !(entity instanceof ServerPlayer player) || entity.level().isClientSide) {
            if (entity instanceof ServerPlayer player) DoubleResearchData.clearStudy(player);
            return false;
        }

        LivingEntity target = lookedAtLivingEntity(player);
        if (target == null || target.getUUID().equals(player.getUUID())) {
            DoubleResearchData.clearStudy(player);
            return true;
        }
        if (DoubleResearchData.isUnlocked(player, target.getUUID())) {
            DoubleResearchData.clearStudy(player);
            return true;
        }

        int progressTicks = DoubleResearchData.advanceStudy(player, target.getUUID(), target.getName().getString());
        if (progressTicks >= RESEARCH_STACK_TICKS) {
            DoubleResearchData.clearStudy(player);
            DoubleResearchData.Award award = DoubleResearchData.awardStack(player, target);
            instance.addMasteryPoint(player);
            instance.markDirty();
            player.displayClientMessage(Component.literal(award.unlocked()
                    ? "Research complete: " + target.getName().getString()
                    : "Research progress: " + award.stacks() + "/3 — " + target.getName().getString()), true);
        } else if (progressTicks % 10 == 0) {
            int stacks = DoubleResearchData.stacks(player, target.getUUID());
            int percent = progressTicks * 100 / RESEARCH_STACK_TICKS;
            player.displayClientMessage(Component.translatable("tracadamia.skill.double.study.progress",
                    target.getDisplayName(), Math.min(3, stacks + 1), percent), true);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == STUDY && entity instanceof ServerPlayer player) DoubleResearchData.clearStudy(player);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) DoubleResearchData.clearStudy(player);
        super.onForgetSkill(instance, entity);
    }
}

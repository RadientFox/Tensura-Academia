package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class EngineQuirk extends Skill {
    private static final int MAX_MASTERY = 5_000;
    private static final int RECIPRO_UNLOCK = 1_250;
    private static final int MAXIMUM_UNLOCK = 2_500;
    private static final String ACTIVE_MODE = "tracadamia_engine_active_mode";
    private static final String ACTIVE_UNTIL = "tracadamia_engine_active_until";
    private static final String LOCK_UNTIL = "tracadamia_engine_lock_until";
    private static final ResourceLocation SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "engine_speed");

    public EngineQuirk() {
        super(SkillType.UNIQUE);
    }

    public static void registerSkillEvents() {
        NeoForge.EVENT_BUS.register(EngineQuirk.class);
    }

    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/engineicon.png");
    }

    @Override public int getMaxMastery() { return MAX_MASTERY; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public int getModes(ManasSkillInstance instance) { return 3; }
    @Override public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "engine.engine_boost";
            case 1 -> "engine.recipro_burst";
            case 2 -> "engine.maximum_speed";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity entity, int key, int mode) {
        if (!(entity.level() instanceof ServerLevel level) || mode < 0 || mode >= getModes(instance)) return;
        if (mode == 1 && instance.getMastery() < RECIPRO_UNLOCK) {
            if (entity instanceof Player player)
                player.displayClientMessage(Component.translatable("tracadamia.skill.engine.recipro_locked", RECIPRO_UNLOCK), true);
            return;
        }
        if (mode == 2 && instance.getMastery() < MAXIMUM_UNLOCK) {
            if (entity instanceof Player player)
                player.displayClientMessage(Component.translatable("tracadamia.skill.engine.maximum_locked", MAXIMUM_UNLOCK), true);
            return;
        }
        long now = level.getGameTime();
        var tag = instance.getOrCreateTag();
        if (tag.getLong(LOCK_UNTIL) > now || tag.getLong(ACTIVE_UNTIL) > now) return;

        int duration;
        double totalSpeedMultiplier;
        switch (mode) {
            case 0 -> {
                duration = instance.isMastered(entity) ? 6 * 60 : 3 * 60;
                totalSpeedMultiplier = 4.0D;
            }
            case 1 -> {
                duration = 60;
                totalSpeedMultiplier = 8.0D;
            }
            case 2 -> {
                duration = 10;
                totalSpeedMultiplier = 12.0D;
            }
            default -> { return; }
        }
        tag.putInt(ACTIVE_MODE, mode);
        tag.putLong(ACTIVE_UNTIL, now + duration * 20L);
        tag.putDouble("tracadamia_engine_speed_multiplier", totalSpeedMultiplier);
        instance.markDirty();
        instance.addMasteryPoint(entity);
        if (entity instanceof Player player)
            player.displayClientMessage(Component.translatable("tracadamia.skill.engine.activated",
                    Component.translatable("tracadamia.skill.mode.engine." + modeName(mode)), duration), true);
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> "engine_boost";
            case 1 -> "recipro_burst";
            default -> "maximum_speed";
        };
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Entity rawEntity : level.getEntities().getAll()) {
                if (!(rawEntity instanceof LivingEntity entity)) continue;
                ManasSkillInstance instance = SkillAPI.getSkillsFrom(entity)
                        .getSkill(QuirkSkills.ENGINE.get().getRegistryName()).orElse(null);
                AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed == null) continue;
                if (instance == null) {
                    speed.removeModifier(SPEED_MODIFIER);
                    continue;
                }

                var tag = instance.getOrCreateTag();
                long until = tag.getLong(ACTIVE_UNTIL);
                if (until > 0 && now >= until) {
                    int endedMode = tag.getInt(ACTIVE_MODE);
                    tag.remove(ACTIVE_MODE);
                    tag.remove(ACTIVE_UNTIL);
                    tag.remove("tracadamia_engine_speed_multiplier");
                    if (endedMode == 1 || endedMode == 2) {
                        int cooldown = endedMode == 1 ? 60 : 120;
                        tag.putLong(LOCK_UNTIL, now + cooldown * 20L);
                        for (int mode = 0; mode < getModeCount(instance); mode++)
                            instance.setCoolDown(cooldown, mode);
                    }
                    instance.markDirty();
                    if (entity instanceof Player player)
                        player.displayClientMessage(Component.translatable("tracadamia.skill.engine.ended"), true);
                }

                double multiplier = 2.0D;
                if (tag.getLong(ACTIVE_UNTIL) > now)
                    multiplier = tag.getDouble("tracadamia_engine_speed_multiplier");
                speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_MODIFIER,
                        multiplier - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static int getModeCount(ManasSkillInstance instance) {
        return 3;
    }

    @SubscribeEvent
    public static void onMeleeDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
                || !(event.getSource().getEntity() instanceof Player attacker)) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(attacker)
                .getSkill(QuirkSkills.ENGINE.get().getRegistryName()).orElse(null);
        if (instance == null || !TensuraStorages.getAbilityFrom(attacker)
                .isAbilityInActivePreset(QuirkSkills.ENGINE.get())) return;

        double speedMultiplier = 2.0D;
        var tag = instance.getOrCreateTag();
        if (attacker.level() instanceof ServerLevel level && tag.getLong(ACTIVE_UNTIL) > level.getGameTime())
            speedMultiplier = tag.getDouble("tracadamia_engine_speed_multiplier");
        AttributeInstance attack = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        double unarmedBaseDamage = attack == null ? 1.0D : attack.getBaseValue();
        event.setAmount((float) Math.max(1.0D, unarmedBaseDamage * speedMultiplier));
    }
}

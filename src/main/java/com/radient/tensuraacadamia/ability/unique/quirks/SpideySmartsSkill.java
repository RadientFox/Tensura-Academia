package com.radient.tensuraacadamia.ability.unique.quirks;

import com.github.hvnbael.trnightmare.racialprogression.RacialProgressionService;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.SpiderRegistry;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class SpideySmartsSkill extends Skill {

    private static final QuirkSkillsConfig.Spider CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Spider;

    public static final String COMBAT = "combat";
    public static final String RAID = "raid";
    public static final String RESEARCH = "research";

    public static final int MENU = 0;
    public static final int IRON_SPIDER_ARMS = 1;
    private static final int MODES = 2;

    public SpideySmartsSkill() {
        super(Skill.SkillType.EXTRA);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/extra/spidey_smarts.png");
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (!SpiderQuirk.hasIronSpider(entity)) {
            return MENU;
        }
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case MENU -> "spidey_smarts.menu";
            case IRON_SPIDER_ARMS -> "spidey_smarts.iron_spider_arms";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        Optional<ManasSkillInstance> spider = SpiderQuirk.getSpider(player);
        if (spider.isEmpty()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("tracadamia.skill.spidey_smarts.no_spider").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (mode == IRON_SPIDER_ARMS) {
            if (SpiderQuirk.hasIronSpider(player)) {
                SpiderQuirk.toggleWaldos(spider.get(), player);
            }
            return;
        }

        RacialProgressionService.sync(player, true, "", false);
    }

    public static Optional<ManasSkillInstance> getSmarts(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.SPIDEY_SMARTS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static @Nullable String tokenType(ResourceLocation item) {
        Item found = BuiltInRegistries.ITEM.get(item);
        if (found == SpiderRegistry.COMBAT_TOKEN.get()) {
            return COMBAT;
        }
        if (found == SpiderRegistry.RAID_TOKEN.get()) {
            return RAID;
        }
        if (found == SpiderRegistry.RESEARCH_TOKEN.get()) {
            return RESEARCH;
        }
        return null;
    }

    public static int getTokens(ManasSkillInstance instance, String type) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(type);
    }

    public static void setTokens(ManasSkillInstance instance, String type, int amount) {
        instance.getOrCreateTag().putInt(type, Math.max(0, amount));
        instance.markDirty();
    }

    public static int tokenBalance(Player player, ResourceLocation item) {
        String type = tokenType(item);
        if (type == null) {
            return -1;
        }
        return getSmarts(player).map(instance -> getTokens(instance, type)).orElse(0);
    }

    public static boolean spendTokens(Player player, ResourceLocation item, int count) {
        String type = tokenType(item);
        if (type == null) {
            return false;
        }
        getSmarts(player).ifPresent(instance -> setTokens(instance, type, getTokens(instance, type) - count));
        return true;
    }

    private static void reward(LivingEntity entity, String type, double chance) {
        if (!(entity instanceof ServerPlayer player) || player.getRandom().nextDouble() >= chance || SpiderQuirk.getSpider(player).isEmpty()) {
            return;
        }

        getSmarts(player).ifPresent(instance -> {
            setTokens(instance, type, getTokens(instance, type) + 1);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.displayClientMessage(Component.translatable("tracadamia.skill.spidey_smarts.token_" + type, getTokens(instance, type))
                    .withStyle(ChatFormatting.AQUA), true);
        });
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof Enemy && event.getSource().getEntity() instanceof ServerPlayer killer) {
            reward(killer, COMBAT, CONFIG.combatTokenChance);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().is(MobEffects.HERO_OF_THE_VILLAGE) && event.getOldEffectInstance() == null) {
            reward(event.getEntity(), RAID, CONFIG.raidTokenChance);
        }
    }

    public static void registerSkillEvents() {
        SkillEvents.UNLOCK_SKILL.register((instance, owner, message) -> {
            if (!owner.level().isClientSide && !isSpiderSkill(instance)) {
                reward(owner, RESEARCH, CONFIG.researchTokenChance);
            }
            return EventResult.pass();
        });
        SkillEvents.SKILL_MASTERY.register((instance, owner, amount) -> {
            int max = instance.getSkill().getMaxMastery();
            if (!owner.level().isClientSide && max > 0 && instance.getMastery() < max && instance.getMastery() + amount.get() >= max) {
                reward(owner, RESEARCH, CONFIG.researchTokenChance);
            }
            return EventResult.pass();
        });
    }

    private static boolean isSpiderSkill(ManasSkillInstance instance) {
        return instance.getSkill() == QuirkSkills.SPIDER.get() || instance.getSkill() == QuirkSkills.SPIDEY_SMARTS.get();
    }
}

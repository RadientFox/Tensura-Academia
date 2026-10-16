package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneStock;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// New Order Rebel
public final class NewOrderRebel {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    private NewOrderRebel() {
    }

    static boolean isRebelMaker(CompoundTag entry, LivingEntity entity) {
        return !entry.hasUUID(NewOrderRules.MAKER_TAG) || entry.getUUID(NewOrderRules.MAKER_TAG).equals(entity.getUUID());
    }

    // false once it runs out
    static boolean tickRebel(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, CompoundTag tag, CompoundTag entry) {
        if (isRebelMaker(entry, entity)) {
            return true;
        }

        int left = tag.getInt(NewOrderRules.REBEL_TAG) - 1;
        tag.putInt(NewOrderRules.REBEL_TAG, Math.max(0, left));
        if (left % 20 == 0) {
            destroyQuirk(level, entity);
            NewOrderUtil.dirty(instance, entity);
        }
        return left > 0;
    }

    // Once it's done rebelling New Order destroys itself
    static void destroySelf(ServerLevel level, LivingEntity entity) {
        SkillStorage storage = SkillAPI.getSkillsFrom(entity);
        storage.forgetSkill(QuirkSkills.NEW_ORDER.get());
        storage.markDirty();

        Vec3 center = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 30, entity.getBbWidth() * 0.5D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.5D, 0.15D);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 30, entity.getBbWidth() * 0.6D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.6D, 0.25D);
        NewOrderUtil.playSound(level, center, TensuraSoundEvents.BARRIER_BREAK.get(), 1.2F, 0.8F);
        NewOrderUtil.playSound(level, center, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.7F);
        entity.sendSystemMessage(Component.translatable("tracadamia.skill.new_order.rebel_over").withStyle(ChatFormatting.GOLD));
    }

    public static boolean isDestroyable(@Nullable ManasSkill skill) {
        if (!NewOrderSeals.isQuirk(skill) || skill == QuirkSkills.NEW_ORDER.get()) {
            return false;
        }

        ResourceLocation id = skill.getRegistryName();
        return id != null && !id.getPath().startsWith("all_for_one") && !id.getPath().startsWith("one_for_all");
    }

    private static void destroyQuirk(ServerLevel level, LivingEntity entity) {
        SkillStorage storage = SkillAPI.getSkillsFrom(entity);
        List<ResourceLocation> learned = new ArrayList<>();
        for (ManasSkillInstance skill : storage.getLearnedSkills()) {
            if (isDestroyable(skill.getSkill())) {
                learned.add(skill.getSkillId());
            }
        }

        List<ResourceLocation> quirks = new ArrayList<>(learned);
        for (ResourceLocation id : AllForOneStock.counts(entity).keySet()) {
            if (!quirks.contains(id) && isDestroyable(SkillAPI.getSkillRegistry().get(id))) {
                quirks.add(id);
            }
        }
        if (quirks.isEmpty()) {
            return;
        }

        ResourceLocation id = quirks.get(level.random.nextInt(quirks.size()));
        ManasSkill skill = SkillAPI.getSkillRegistry().get(id);
        if (learned.contains(id)) {
            storage.forgetSkill(id);
        } else {
            AllForOneStock.discardOne(entity, id);
        }
        storage.markDirty();

        Vec3 center = entity.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 30, entity.getBbWidth() * 0.6D, entity.getBbHeight() * 0.4D, entity.getBbWidth() * 0.6D, 0.25D);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 12, 0.1D, 0.1D, 0.1D, 0.15D);
        NewOrderUtil.playSound(level, center, TensuraSoundEvents.BARRIER_BREAK.get(), 1.0F, 1.3F);
        NewOrderUtil.playSound(level, center, SoundEvents.ITEM_BREAK, 1.0F, 0.6F);
        Component name = skill == null || skill.getName() == null ? Component.literal(id.toString()) : skill.getName();
        entity.sendSystemMessage(Component.translatable("tracadamia.skill.new_order.destroyed", name).withStyle(ChatFormatting.DARK_RED));
    }

}

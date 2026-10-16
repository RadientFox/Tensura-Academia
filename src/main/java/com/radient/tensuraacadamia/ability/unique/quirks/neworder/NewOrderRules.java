package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// New Order's rules
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderRules {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    static final String NAMED_TAG = "named";
    private static final String RULES_TAG = "rules";
    private static final String HOLDER_TAG = "holder";
    static final String REBEL_TAG = "rebelTicks";
    private static final String ORDER_TAG = "ruleOrder";
    static final String MAKER_TAG = "maker";
    private static final String MADE_TAG = "made";
    private static final int SEAL_REFRESH = 10;
    public static final int MAKE = 0;
    public static final int REVOKE = 1;

    public static final int MAX_RULES = 2;
    public static final int[] RULE_COLORS = {0xD8F3FF, 0x7FDBFF, 0xFF6B6B, 0xE0B070};

    private NewOrderRules() {
    }

    static ListTag getRuleList(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? new ListTag() : tag.getList(RULES_TAG, Tag.TAG_COMPOUND);
    }

    static @Nullable NewOrderRule getRule(CompoundTag entry) {
        return NewOrderRule.byId(entry.getString("rule"));
    }

    static @Nullable UUID getTarget(CompoundTag entry) {
        return entry.hasUUID("target") ? entry.getUUID("target") : null;
    }

    static @Nullable CompoundTag getEntry(ManasSkillInstance instance, NewOrderRule rule) {
        for (Tag entry : getRuleList(instance)) {
            if (getRule((CompoundTag) entry) == rule) {
                return (CompoundTag) entry;
            }
        }
        return null;
    }

    public static boolean hasRule(ManasSkillInstance instance, NewOrderRule rule) {
        for (Tag entry : getRuleList(instance)) {
            if (getRule((CompoundTag) entry) == rule) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRule(LivingEntity entity, NewOrderRule rule) {
        return NewOrderQuirk.getNewOrder(entity).filter(instance -> hasRule(instance, rule) && !NewOrderSeals.isSealed((Skill) instance.getSkill(), entity)).isPresent();
    }

    private static Component getRuleTargetName(CompoundTag entry, ServerLevel level) {
        return NewOrderUtil.readName(entry.getString("name"), level);
    }

    static @Nullable CompoundTag getNamed(ManasSkillInstance instance, long time) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(NAMED_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag named = tag.getCompound(NAMED_TAG);
        return time - named.getLong("time") > CONFIG.nameMemorySeconds * 20L ? null : named;
    }

    static void choose(ServerPlayer player, NewOrderPayloads.RuleChoicePayload choice) {
        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(player).orElse(null);
        if (instance == null || !player.isAlive()) {
            return;
        }

        if (choice.action() == REVOKE) {
            revoke(player.serverLevel(), player, instance, choice.value());
        } else {
            NewOrderRule rule = NewOrderRule.byIndex(choice.value());
            if (rule != null) {
                makeRule(player.serverLevel(), player, instance, rule);
            }
        }
        sendRules(player, instance, false);
    }

    public static boolean makeRule(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, NewOrderRule rule) {
        if (instance.onCoolDown(NewOrderQuirk.RULES)) {
            NewOrderUtil.message(entity, Component.translatable("tensura.skill.cooldown", instance.getChatDisplayName(false)).withStyle(ChatFormatting.RED));
            return false;
        }

        CompoundTag named = getNamed(instance, level.getGameTime());
        if (named == null) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.no_name"));
            return false;
        }

        if (named.getInt("kind") != rule.getKind()) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.wrong_kind", getKindName(rule.getKind())));
            return false;
        }

        if (rule == NewOrderRule.REBEL && !instance.isMastered(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_mastery"));
            return false;
        }

        LivingEntity target = null;
        if (rule.getKind() == NewOrderRule.ENTITY) {
            target = named.hasUUID("id") && level.getEntity(named.getUUID("id")) instanceof LivingEntity living && living.isAlive() ? living : null;
            if (target == null) {
                NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.target_gone"));
                return false;
            }
        }

        if (rule == NewOrderRule.WILL_DIE) {
            return willDie(level, entity, instance, target, named);
        }

        ListTag rules = getRuleList(instance);
        for (Tag tag : rules) {
            CompoundTag entry = (CompoundTag) tag;
            if (getRule(entry) == rule && (target == null || target.getUUID().equals(getTarget(entry)))) {
                NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.already", rule.getName()));
                return false;
            }
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.RULES)) {
            return false;
        }

        CompoundTag tag = instance.getOrCreateTag();
        long order = tag.getLong(ORDER_TAG) + 1L;
        tag.putLong(ORDER_TAG, order);
        CompoundTag entry = new CompoundTag();
        entry.putString("rule", rule.getId());
        entry.putString("name", named.getString("name"));
        entry.putLong("order", order);
        entry.putLong(MADE_TAG, level.getGameTime());
        if (target != null) {
            entry.putUUID("target", target.getUUID());
        }
        if (named.contains("block")) {
            entry.putString("block", named.getString("block"));
        }
        if (rule == NewOrderRule.REBEL) {
            entry.putUUID(MAKER_TAG, entity.getUUID());
        }

        Component replaced = null;
        while (rules.size() >= MAX_RULES) {
            int oldest = -1;
            for (int i = 0; i < rules.size(); i++) {
                CompoundTag other = rules.getCompound(i);
                if (getRule(other) != NewOrderRule.REBEL && (oldest < 0 || other.getLong("order") < rules.getCompound(oldest).getLong("order"))) {
                    oldest = i;
                }
            }
            if (oldest < 0) {
                break;
            }

            CompoundTag removed = (CompoundTag) rules.remove(oldest);
            NewOrderRule old = getRule(removed);
            replaced = old == null ? null : old.getName();
            endRule(level, entity, instance, removed);
        }

        rules.add(entry);
        tag.put(RULES_TAG, rules);
        tag.remove(NAMED_TAG);
        if (rule == NewOrderRule.REBEL) {
            tag.putInt(REBEL_TAG, CONFIG.rebelSeconds * 20);
        }
        instance.setCoolDown(CONFIG.ruleCooldown, NewOrderQuirk.RULES);
        instance.addMasteryPoint(entity);
        NewOrderUtil.dirty(instance, entity);
        NewOrderSeals.refreshSeals(entity, rules, level.getGameTime());
        NewOrderSelf.refreshSelf(entity, instance);
        startRule(level, entity, rule, target, NewOrderUtil.readName(entry.getString("name"), level), replaced);
        return true;
    }

    // Doesnt really take up a rule spot? You need a free spot open but because the target dies it doesnt take up a spot
    private static boolean willDie(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, @Nullable LivingEntity target, CompoundTag named) {
        if (target == null) {
            return false;
        }
        if (!instance.isMastered(entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.needs_mastery"));
            return false;
        }
        if (NewOrderUtil.isBoss(target)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.will_die_boss"));
            return false;
        }
        if (EnergyHelper.getMaxEP(entity) < EnergyHelper.getMaxEP(target) * CONFIG.willDieEpMultiplier) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.will_die_ep", NewOrderRule.number(CONFIG.willDieEpMultiplier)));
            return false;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, NewOrderQuirk.RULES)) {
            return false;
        }

        instance.getOrCreateTag().remove(NAMED_TAG);
        instance.setCoolDown(CONFIG.ruleCooldown, NewOrderQuirk.RULES);
        instance.addMasteryPoint(entity);
        NewOrderUtil.dirty(instance, entity);
        startRule(level, entity, NewOrderRule.WILL_DIE, target, NewOrderUtil.readName(named.getString("name"), level), null);

        Vec3 center = target.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.SOUL, center.x, center.y, center.z, 30, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.05D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 12, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.03D);
        NewOrderUtil.playSound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 0.6F);
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().source(DamageTypes.GENERIC_KILL, entity), Float.MAX_VALUE);
        if (target.isAlive()) {
            target.kill();
        }
        return true;
    }

    private static void revoke(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, int index) {
        ListTag rules = getRuleList(instance);
        if (index < 0 || index >= rules.size()) {
            return;
        }

        CompoundTag entry = rules.getCompound(index);
        NewOrderRule rule = getRule(entry);
        if (rule == NewOrderRule.REBEL && !NewOrderRebel.isRebelMaker(entry, entity)) {
            NewOrderUtil.fail(entity, Component.translatable("tracadamia.skill.new_order.rebel_locked"));
            return;
        }

        rules.remove(index);
        instance.getOrCreateTag().put(RULES_TAG, rules);
        NewOrderUtil.dirty(instance, entity);
        endRule(level, entity, instance, entry);
        NewOrderSeals.refreshSeals(entity, rules, level.getGameTime());
        NewOrderSelf.refreshSelf(entity, instance);
        NewOrderUtil.message(entity, Component.translatable("tracadamia.skill.new_order.revoked", rule == null ? Component.empty() : rule.getName()).withStyle(ChatFormatting.GRAY));
    }

    private static Component getKindName(int kind) {
        return Component.translatable("tracadamia.skill.new_order.kind." + switch (kind) {
            case NewOrderRule.USER -> "user";
            case NewOrderRule.ENTITY -> "entity";
            case NewOrderRule.GROUND -> "ground";
            default -> "air";
        });
    }

    private static void startRule(ServerLevel level, LivingEntity entity, NewOrderRule rule, @Nullable LivingEntity target, Component targetName, @Nullable Component replaced) {
        LivingEntity shown = target != null ? target : entity;
        Vec3 center = rule.getKind() == NewOrderRule.AIR ? entity.getEyePosition().add(entity.getViewVector(1.0F).scale(NewOrderQuirk.AIR_TOUCH)) : shown.getBoundingBox().getCenter();
        SoundEvent sound = switch (rule.getKind()) {
            case NewOrderRule.USER -> TensuraSoundEvents.BUFF_ACTIVATE.get();
            case NewOrderRule.ENTITY -> TensuraSoundEvents.DEBUFF_ACTIVATE.get();
            case NewOrderRule.GROUND -> TensuraSoundEvents.CAST_EARTH.get();
            default -> TensuraSoundEvents.CAST_WIND.get();
        };
        NewOrderUtil.playSound(level, center, sound, 1.0F, 1.0F);
        NewOrderUtil.playSound(level, entity.getEyePosition(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.5F);
        decree(level, shown, rule.getKind() == NewOrderRule.AIR ? 0.8D : Math.max(0.6D, shown.getBbWidth() * 0.8D), rule.getKind() == NewOrderRule.AIR ? center : null);
        NewOrderUtil.callout(level, rule.getKind() == NewOrderRule.AIR ? center.add(0.0D, 0.8D, 0.0D) : new Vec3(shown.getX(), shown.getY() + shown.getBbHeight() + 0.8D, shown.getZ()),
                rule.getKind() == NewOrderRule.AIR ? null : shown, rule.getName(), RULE_COLORS[rule.getKind()]);

        if (target != null) {
            NewOrderSeals.applySeal(target, rule.getSeal());
            NewOrderSeals.afflict(entity, target, rule.getSeal());
            if (target instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.new_order.ruled_you", entity.getDisplayName(), rule.getName()).withStyle(ChatFormatting.RED), true);
            }
        }

        Component made = target != null ? Component.translatable("tracadamia.skill.new_order.made_on", rule.getName(), targetName) : Component.translatable("tracadamia.skill.new_order.made", rule.getName());
        NewOrderUtil.message(entity, replaced == null ? made.copy().withStyle(ChatFormatting.GOLD)
                : Component.translatable("tracadamia.skill.new_order.replaced", made, replaced).withStyle(ChatFormatting.GOLD));
    }

    private static void decree(ServerLevel level, LivingEntity shown, double radius, @Nullable Vec3 at) {
        Vec3 base = at != null ? at.subtract(0.0D, 0.6D, 0.0D) : shown.position();
        double height = at != null ? 1.2D : shown.getBbHeight();
        for (int point = 0; point < 24; point++) {
            double angle = point * Mth.TWO_PI / 8.0D;
            double y = height * point / 24.0D;
            level.sendParticles(ParticleTypes.END_ROD, base.x + Math.cos(angle) * radius, base.y + y, base.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void endRule(ServerLevel level, LivingEntity entity, ManasSkillInstance instance, CompoundTag entry) {
        NewOrderRule rule = getRule(entry);
        if (rule == null) {
            return;
        }

        switch (rule) {
            case HARDEN -> {
                NewOrderCopy.forget(entity);
                NewOrderStrikes.forget(entity);
                NewOrderBodyMoves.forget(entity);
            }
            case AIR_CONTROL -> {
                NewOrderVacuum.forget(entity);
                NewOrderAirSkills.forget(entity, instance);
            }
            case REBEL -> instance.getOrCreateTag().remove(REBEL_TAG);
            default -> {
            }
        }
        NewOrderUtil.playSound(level, entity.position(), TensuraSoundEvents.GENERIC_UNCAST.get(), 0.8F, 1.0F);
    }

    // Everything New Order does each tick
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ManasSkillInstance instance = NewOrderQuirk.getNewOrder(player).orElse(null);
        if (instance == null) {
            return;
        }

        NewOrderQuirk.fixCooldowns(instance);
        if (player.isAlive() && !tickHolder(player.serverLevel(), player, instance)) {
            return;
        }
        NewOrderBody.tickBody(player, instance);
    }

    // false once New Order is gone
    private static boolean tickHolder(ServerLevel level, LivingEntity entity, ManasSkillInstance instance) {
        long time = level.getGameTime();
        CompoundTag tag = instance.getOrCreateTag();
        checkHolder(entity, instance, tag);
        if (NewOrderSeals.isSealed((Skill) instance.getSkill(), entity)) {
            NewOrderSelf.applySelf(entity, instance, EnumSet.noneOf(NewOrderRule.class));
            NewOrderSeals.clearSeals(entity.getUUID());
            NewOrderGround.setField(entity, 0);
            return true;
        }

        ListTag rules = getRuleList(instance);
        boolean changed = false;
        Set<NewOrderRule> self = EnumSet.noneOf(NewOrderRule.class);
        int ground = 0;
        for (int i = rules.size() - 1; i >= 0; i--) {
            CompoundTag entry = rules.getCompound(i);
            NewOrderRule rule = getRule(entry);
            if (rule == null) {
                rules.remove(i);
                changed = true;
                continue;
            }

            switch (rule) {
                case SHIELD -> NewOrderShield.tickShield(level, entity, instance, entry);
                case SLIPPERY, ELASTIC, NO_FALL -> ground |= NewOrderGround.getFlag(rule);
                case REBEL -> {
                    if (!NewOrderRebel.tickRebel(level, entity, instance, tag, entry)) {
                        NewOrderRebel.destroySelf(level, entity);
                        return false;
                    }
                }
                default -> {
                    if (rule.getKind() == NewOrderRule.USER) {
                        self.add(rule);
                    } else if (rule.getKind() == NewOrderRule.ENTITY) {
                        LivingEntity target = findTarget(level, entry);
                        boolean over = rule == NewOrderRule.NO_MOVE && entry.contains(MADE_TAG) && time - entry.getLong(MADE_TAG) >= CONFIG.noMoveSeconds * 20L;
                        if (target != null && (target.isDeadOrDying() || target.isRemoved()) || over) {
                            rules.remove(i);
                            changed = true;
                            endRule(level, entity, instance, entry);
                            NewOrderUtil.message(entity, Component.translatable("tracadamia.skill.new_order.lapsed", rule.getName(), getRuleTargetName(entry, level)).withStyle(ChatFormatting.GRAY));
                        } else if (target != null) {
                            NewOrderSeals.afflict(entity, target, rule.getSeal());
                            if (time % 20L == 0L) {
                                level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6D), target.getZ(), 4, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.4D, 0.02D);
                            }
                        }
                    }
                }
            }
        }

        NewOrderSelf.applySelf(entity, instance, self);
        NewOrderGround.setField(entity, ground);
        if (changed) {
            tag.put(RULES_TAG, rules);
            NewOrderUtil.dirty(instance, entity);
        }
        if (changed || time % SEAL_REFRESH == 0L) {
            NewOrderSeals.refreshSeals(entity, rules, time);
        }
        return true;
    }

    private static @Nullable LivingEntity findTarget(ServerLevel level, CompoundTag entry) {
        UUID id = getTarget(entry);
        return id != null && level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    // If stolen rebel is still active
    private static void checkHolder(LivingEntity entity, ManasSkillInstance instance, CompoundTag tag) {
        if (tag.hasUUID(HOLDER_TAG) && tag.getUUID(HOLDER_TAG).equals(entity.getUUID())) {
            return;
        }

        boolean moved = tag.hasUUID(HOLDER_TAG);
        tag.putUUID(HOLDER_TAG, entity.getUUID());
        if (moved) {
            ListTag kept = new ListTag();
            boolean turned = false;
            for (Tag entry : getRuleList(instance)) {
                if (getRule((CompoundTag) entry) == NewOrderRule.REBEL) {
                    kept.add(entry);
                    turned |= !NewOrderRebel.isRebelMaker((CompoundTag) entry, entity);
                }
            }
            tag.put(RULES_TAG, kept);
            tag.remove(NAMED_TAG);
            tag.remove(NewOrderSelf.FLIGHT_TAG);
            tag.remove(NewOrderBody.BODY_TAG);
            if (turned) {
                entity.sendSystemMessage(Component.translatable("tracadamia.skill.new_order.rebel_resumes").withStyle(ChatFormatting.DARK_RED));
            }
        }
        NewOrderUtil.dirty(instance, entity);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        NewOrderQuirk.getNewOrder(player).ifPresent(instance -> {
            CompoundTag tag = instance.getOrCreateTag();
            ListTag kept = new ListTag();
            for (Tag entry : getRuleList(instance)) {
                if (getRule((CompoundTag) entry) == NewOrderRule.REBEL) {
                    kept.add(entry);
                }
            }
            tag.put(RULES_TAG, kept);
            tag.remove(NAMED_TAG);
            NewOrderSelf.applySelf(player, instance, EnumSet.noneOf(NewOrderRule.class));
            NewOrderSeals.clearSeals(player.getUUID());
            NewOrderUtil.dirty(instance, player);
        });
    }

    static void sendRules(ServerPlayer player, ManasSkillInstance instance, boolean open) {
        ServerLevel level = player.serverLevel();
        CompoundTag named = getNamed(instance, level.getGameTime());
        int kind = named == null ? NewOrderRule.NONE : named.getInt("kind");
        Component name = named == null ? Component.empty() : NewOrderUtil.readName(named.getString("name"), level);
        int entityId = named != null && named.hasUUID("id") && level.getEntity(named.getUUID("id")) instanceof LivingEntity living ? living.getId() : -1;
        String block = named == null ? "" : named.getString("block");
        CompoundTag tag = instance.getTag();
        List<NewOrderPayloads.RuleEntry> entries = new ArrayList<>();
        for (Tag entry : getRuleList(instance)) {
            NewOrderRule rule = getRule((CompoundTag) entry);
            if (rule != null) {
                boolean running = rule == NewOrderRule.REBEL && tag != null && !NewOrderRebel.isRebelMaker((CompoundTag) entry, player);
                entries.add(new NewOrderPayloads.RuleEntry(rule.ordinal(), getRuleTargetName((CompoundTag) entry, level), running ? tag.getInt(REBEL_TAG) : -1));
            }
        }

        PacketDistributor.sendToPlayer(player, new NewOrderPayloads.RulesPayload(open, kind, name, entityId, block, entries, instance.isMastered(player)));
        if (open) {
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

}

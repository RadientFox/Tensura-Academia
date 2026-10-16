package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ZeroGravityQuirk;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.event.TensuraEntityEvents;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// New Order's rules on a target
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderSeals {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    static final int SEAL_QUIRKS = 1;
    static final int SEAL_UNIQUE = 2;
    static final int SEAL_ULTIMATE = 4;
    static final int SEAL_RECOVER = 8;
    static final int SEAL_WEAKEN = 16;
    static final int SEAL_CRUMBLE = 32;
    static final int SEAL_BLIND = 64;
    static final int SEAL_WEIGHTLESS = 128;
    static final int SEAL_GROUNDED = 256;
    static final int SEAL_NO_TELEPORT = 512;
    static final int SEAL_NO_MOVE = 1024;
    static final int SEAL_SILENCE = 2048;
    static final int SEAL_NO_ITEMS = 4096;
    private static final int AFFLICTIONS = SEAL_CRUMBLE | SEAL_BLIND | SEAL_WEIGHTLESS | SEAL_GROUNDED | SEAL_NO_TELEPORT | SEAL_NO_MOVE | SEAL_SILENCE | SEAL_NO_ITEMS;
    private static final long SEAL_REFRESH = 40L;
    private static final int AFFLICT_TICKS = 60;
    private static final double BLIND_SIGHT = 2.0D;
    private static final double GROUND_PULL = 0.15D;
    private static final ResourceLocation CRUMBLE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_crumble");
    private static final ResourceLocation ROOTED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_rooted");
    private record Seal(int flags, long until) {}
    private record Downgrade(LivingEntity entity, MobEffectInstance effect) {}

    // Target -> holder
    private static final Map<UUID, Map<UUID, Seal>> SEALS = new HashMap<>();
    private static final Map<UUID, Long> SEAL_WARNED = new HashMap<>();
    private static final List<Downgrade> DOWNGRADES = new ArrayList<>();

    private static final Map<LivingEntity, Integer> AFFLICTED = new HashMap<>();
    private static boolean downgrading;

    private NewOrderSeals() {
    }

    static void refreshSeals(LivingEntity holder, ListTag rules, long time) {
        UUID id = holder.getUUID();
        clearSeals(id);
        Map<UUID, Integer> flags = new HashMap<>();
        for (Tag tag : rules) {
            CompoundTag entry = (CompoundTag) tag;
            NewOrderRule rule = NewOrderRules.getRule(entry);
            UUID target = NewOrderRules.getTarget(entry);
            if (rule != null && rule.getSeal() != 0 && target != null) {
                flags.merge(target, rule.getSeal(), (first, second) -> first | second);
            }
        }
        flags.forEach((target, seal) -> SEALS.computeIfAbsent(target, key -> new HashMap<>()).put(id, new Seal(seal, time + SEAL_REFRESH)));
    }

    static void clearSeals(UUID holder) {
        if (!SEALS.isEmpty()) {
            SEALS.values().forEach(map -> map.remove(holder));
            SEALS.values().removeIf(Map::isEmpty);
        }
    }

    static boolean hasSeals() {
        return !SEALS.isEmpty();
    }

    static int sealFlags(LivingEntity entity) {
        if (entity.level().isClientSide || SEALS.isEmpty()) {
            return 0;
        }

        Map<UUID, Seal> seals = SEALS.get(entity.getUUID());
        if (seals == null) {
            return 0;
        }

        long time = entity.level().getGameTime();
        int flags = 0;
        for (Seal seal : seals.values()) {
            if (seal.until() >= time) {
                flags |= seal.flags();
            }
        }
        return flags;
    }

    // separate quirks, unique skills, ultimate skills, and OFA/AFO
    private static boolean matchesSeal(int flags, Skill skill) {
        if (isQuirk(skill)) {
            return (flags & SEAL_QUIRKS) != 0;
        }
        return (flags & SEAL_UNIQUE) != 0 && skill.getType() == Skill.SkillType.UNIQUE || (flags & SEAL_ULTIMATE) != 0 && skill.getType() == Skill.SkillType.ULTIMATE;
    }

    static boolean isQuirk(@Nullable ManasSkill skill) {
        if (!(skill instanceof Skill typed) || typed.getType() != Skill.SkillType.UNIQUE && typed.getType() != Skill.SkillType.ULTIMATE) {
            return false;
        }

        ResourceLocation id = skill.getRegistryName();
        return id != null && TensuraAcadamia.MODID.equals(id.getNamespace());
    }

    public static boolean isSealed(Skill skill, LivingEntity entity) {
        int flags = sealFlags(entity);
        return (flags & (SEAL_QUIRKS | SEAL_UNIQUE | SEAL_ULTIMATE)) != 0 && matchesSeal(flags, skill);
    }

    public static boolean cannotRecover(LivingEntity entity) {
        return (sealFlags(entity) & SEAL_RECOVER) != 0;
    }

    static void applySeal(LivingEntity target, int flags) {
        if ((flags & (SEAL_QUIRKS | SEAL_UNIQUE | SEAL_ULTIMATE)) != 0) {
            SkillStorage storage = SkillAPI.getSkillsFrom(target);
            for (ManasSkillInstance skill : List.copyOf(storage.getLearnedSkills())) {
                if (skill.isToggled() && skill.getSkill() instanceof Skill typed && matchesSeal(flags, typed)) {
                    skill.setToggled(false);
                    skill.onToggleOff(target);
                    skill.markDirty();
                }
            }
            storage.markDirty();
        }

        if ((flags & SEAL_RECOVER) != 0) {
            MobEffectInstance instant = target.getEffect(TensuraMobEffects.getReference(TensuraMobEffects.INSTANT_REGENERATION));
            MobEffectInstance self = target.getEffect(TensuraMobEffects.getReference(TensuraMobEffects.SELF_REGENERATION));
            if (instant != null) {
                target.removeEffect(instant.getEffect());
                DOWNGRADES.add(new Downgrade(target, getLowerRegeneration(instant)));
            } else if (self != null) {
                target.removeEffect(self.getEffect());
            }
        }

        Vec3 center = target.getBoundingBox().getCenter();
        ((ServerLevel) target.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 20, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.1D);
    }

    static void afflict(LivingEntity holder, LivingEntity target, int flags) {
        if ((flags & AFFLICTIONS) == 0) {
            return;
        }

        AFFLICTED.merge(target, flags & AFFLICTIONS, (first, second) -> first | second);
        if ((flags & SEAL_CRUMBLE) != 0) {
            AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
            AttributeModifier current = armor == null ? null : armor.getModifier(CRUMBLE);
            if (armor != null && (current == null || current.amount() != -CONFIG.crumbleArmorReduction)) {
                armor.addOrUpdateTransientModifier(new AttributeModifier(CRUMBLE, -CONFIG.crumbleArmorReduction, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if ((flags & SEAL_BLIND) != 0) {
            keepEffect(holder, target, MobEffects.BLINDNESS);
            if (target instanceof Mob mob && mob.getTarget() != null && mob.distanceTo(mob.getTarget()) > BLIND_SIGHT) {
                mob.setTarget(null);
            }
        }
        if ((flags & SEAL_WEIGHTLESS) != 0) {
            ZeroGravityQuirk.lendFloat(target);
        }
        if ((flags & SEAL_GROUNDED) != 0) {
            ground(target);
            keepEffect(holder, target, TensuraMobEffects.getReference(TensuraMobEffects.MAGIC_INTERFERENCE));
        }
        if ((flags & SEAL_NO_TELEPORT) != 0) {
            keepEffect(holder, target, TensuraMobEffects.getReference(TensuraMobEffects.SPATIAL_BLOCKADE));
        }
        if ((flags & SEAL_NO_MOVE) != 0 && !(target instanceof Player)) {
            root(target, Attributes.MOVEMENT_SPEED);
            root(target, Attributes.JUMP_STRENGTH);
        }
        if ((flags & SEAL_SILENCE) != 0) {
            keepEffect(holder, target, TensuraMobEffects.getReference(TensuraMobEffects.SILENCE));
        }
    }

    // root players and remove walking from mobs
    private static void root(LivingEntity target, Holder<Attribute> attribute) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null && instance.getModifier(ROOTED) == null) {
            instance.addTransientModifier(new AttributeModifier(ROOTED, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unroot(LivingEntity target, Holder<Attribute> attribute) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(ROOTED);
        }
    }

    private static void unafflict(LivingEntity target, int flags) {
        if ((flags & SEAL_CRUMBLE) != 0) {
            AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(CRUMBLE);
            }
        }
        if ((flags & SEAL_BLIND) != 0) {
            dropEffect(target, MobEffects.BLINDNESS);
        }
        if ((flags & SEAL_GROUNDED) != 0) {
            dropEffect(target, TensuraMobEffects.getReference(TensuraMobEffects.MAGIC_INTERFERENCE));
        }
        if ((flags & SEAL_NO_TELEPORT) != 0) {
            dropEffect(target, TensuraMobEffects.getReference(TensuraMobEffects.SPATIAL_BLOCKADE));
        }
        if ((flags & SEAL_NO_MOVE) != 0) {
            unroot(target, Attributes.MOVEMENT_SPEED);
            unroot(target, Attributes.JUMP_STRENGTH);
        }
        if ((flags & SEAL_SILENCE) != 0) {
            dropEffect(target, TensuraMobEffects.getReference(TensuraMobEffects.SILENCE));
        }
    }

    private static void keepEffect(LivingEntity holder, LivingEntity target, Holder<MobEffect> effect) {
        MobEffectInstance current = target.getEffect(effect);
        if (current == null || !current.isInfiniteDuration() && current.getDuration() < AFFLICT_TICKS / 2) {
            target.addEffect(new MobEffectInstance(effect, AFFLICT_TICKS, 0, false, false, true), holder);
        }
    }

    private static void dropEffect(LivingEntity target, Holder<MobEffect> effect) {
        MobEffectInstance current = target.getEffect(effect);
        if (current != null && !current.isInfiniteDuration() && current.getDuration() <= AFFLICT_TICKS) {
            target.removeEffect(effect);
        }
    }

    private static void ground(LivingEntity target) {
        if (target instanceof Player player) {
            if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            if (player.isFallFlying()) {
                player.stopFallFlying();
            }
            return;
        }

        if (!target.onGround() && !target.isInLiquid() && (target.isNoGravity() || target instanceof FlyingMob || target instanceof FlyingAnimal)) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, Math.min(motion.y, 0.0D) - GROUND_PULL, motion.z);
            target.hurtMarked = true;
        }
    }

    private static void tickAfflicted() {
        AFFLICTED.entrySet().removeIf(entry -> {
            LivingEntity target = entry.getKey();
            int kept = target.isRemoved() || !target.isAlive() ? 0 : entry.getValue() & sealFlags(target);
            int ended = entry.getValue() & ~kept;
            if (ended != 0 && !target.isRemoved()) {
                unafflict(target, ended);
            }
            entry.setValue(kept);
            return kept == 0;
        });
    }


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWeakenedDamage(LivingIncomingDamageEvent event) {
        if (!SEALS.isEmpty() && event.getSource().getEntity() instanceof LivingEntity attacker && (sealFlags(attacker) & SEAL_WEAKEN) != 0) {
            event.setAmount((float) (event.getAmount() * (1.0D - CONFIG.weaknessDamageReduction)));
        }
    }

    // stops the interaction with items like chest, doors, levers
    // Should be any right click?
    private static boolean cannotInteract(Player player) {
        return player.level().isClientSide ? (NewOrderPayloads.getClientHeld() & NewOrderPayloads.HeldPayload.NO_ITEMS) != 0 : (sealFlags(player) & SEAL_NO_ITEMS) != 0;
    }

    private static boolean blockInteraction(Player player) {
        if (player.isSpectator() || !cannotInteract(player)) {
            return false;
        }

        if (!player.level().isClientSide) {
            long time = player.level().getGameTime();
            Long warned = SEAL_WARNED.get(player.getUUID());
            if (warned == null || time - warned >= 20L || warned > time) {
                SEAL_WARNED.put(player.getUUID(), time);
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blockInteraction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (blockInteraction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (blockInteraction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseEntityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blockInteraction(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // Spatial Blockade
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (event instanceof EntityTeleportEvent.TeleportCommand || event instanceof EntityTeleportEvent.SpreadPlayersCommand || !(event.getEntity() instanceof LivingEntity living)
                || (sealFlags(living) & SEAL_NO_TELEPORT) == 0) {
            return;
        }

        event.setCanceled(true);
        if (living instanceof Player player) {
            player.displayClientMessage(Component.translatable("tensura.skill.spatial_blockade").withStyle(ChatFormatting.RED), true);
        }
    }

    private static EventResult checkSeal(@Nullable ManasSkillInstance instance, LivingEntity entity) {
        if (instance == null || entity.level().isClientSide || !(instance.getSkill() instanceof Skill skill) || !isSealed(skill, entity)) {
            return EventResult.pass();
        }

        if (entity instanceof Player player) {
            long time = entity.level().getGameTime();
            Long warned = SEAL_WARNED.get(player.getUUID());
            if (warned == null || time - warned >= 20L || warned > time) {
                SEAL_WARNED.put(player.getUUID(), time);
                player.displayClientMessage(Component.translatable("tracadamia.skill.new_order.sealed", instance.getChatDisplayName(false)).withStyle(ChatFormatting.RED), true);
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return EventResult.interruptFalse();
    }

    private static MobEffectInstance getLowerRegeneration(MobEffectInstance instant) {
        if (instant.getAmplifier() >= 1) {
            return new MobEffectInstance(instant.getEffect(), instant.getDuration(), instant.getAmplifier() - 1, instant.isAmbient(), instant.isVisible(), instant.showIcon());
        }
        return new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.SELF_REGENERATION), instant.getDuration(), 0, instant.isAmbient(), instant.isVisible(), instant.showIcon());
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (downgrading || SEALS.isEmpty()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        MobEffectInstance effect = event.getEffectInstance();
        boolean instant = effect.getEffect().value() == TensuraMobEffects.INSTANT_REGENERATION.get();
        if (!instant && effect.getEffect().value() != TensuraMobEffects.SELF_REGENERATION.get() || !cannotRecover(entity)) {
            return;
        }

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (instant) {
            DOWNGRADES.add(new Downgrade(entity, getLowerRegeneration(effect)));
        }
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SkillEvents.ACTIVATE_SKILL.register((instance, entity, keyNumber, mode) -> checkSeal(instance.get(), entity));
            SkillEvents.TOGGLE_SKILL.register((instance, entity) -> checkSeal(instance.get(), entity));
            TensuraEntityEvents.SPIRITUAL_HURT_EVENT.register((target, attacker, original, resist, amount, source) -> NewOrderSelf.guardSoul(target, amount));
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long time = event.getServer().overworld().getGameTime();
        if (!AFFLICTED.isEmpty()) {
            tickAfflicted();
        }
        if (!DOWNGRADES.isEmpty()) {
            List<Downgrade> pending = List.copyOf(DOWNGRADES);
            DOWNGRADES.clear();
            downgrading = true;
            try {
                for (Downgrade downgrade : pending) {
                    if (downgrade.entity().isAlive()) {
                        downgrade.entity().addEffect(downgrade.effect());
                    }
                }
            } finally {
                downgrading = false;
            }
        }
        if (time % 200L == 0L) {
            SEALS.values().forEach(map -> map.values().removeIf(seal -> seal.until() < time));
            SEALS.values().removeIf(Map::isEmpty);
            SEAL_WARNED.values().removeIf(warned -> time - warned > 200L);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SEALS.clear();
        SEAL_WARNED.clear();
        DOWNGRADES.clear();
        AFFLICTED.forEach(NewOrderSeals::unafflict);
        AFFLICTED.clear();
    }

}

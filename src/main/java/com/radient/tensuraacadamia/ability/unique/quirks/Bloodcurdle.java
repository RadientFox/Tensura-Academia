package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.data.TensuraEntityTags;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

public class Bloodcurdle extends Skill {
    private static final int BLEEDING_TICKS = 20 * 10;
    private static final int TYPE_A_TICKS = 20 * 15;
    private static final int TYPE_B_TICKS = 20 * 10;
    private static final int TYPE_O_NEGATIVE_TICKS = 20 * 5;
    private static final int BAD_TASTE_TICKS = 20 * 15;
    private static final int COALGULATION_TICKS = 20 * 15;
    private static final String LAST_TARGET_TAG = "bloodcurdle_last_target";
    private static final String TARGET_STREAK_TAG = "bloodcurdle_target_streak";
    private static final ArrayDeque<LivingEntity> PENDING_COALGULATION = new ArrayDeque<>();
    private static final double TARGET_RANGE = 8.0D;
    private static final TagKey<net.minecraft.world.damagesource.DamageType> BLADE_QUIRK_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("tracadamia", "blade_quirk"));
    private static final TagKey<EntityType<?>> BLADE_QUIRK_ENTITY =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("tracadamia", "blade_quirk"));
    private static final TagKey<Item> BLADE_ITEM =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("tracadamia", "blades"));

    public Bloodcurdle() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/bloodcurdle.png");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return mode == 0 ? "bloodcurdle.blood_paralysis" : super.getModeId(instance, mode);
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == 0 ? 500.0D + EnergyHelper.getMaxAura(entity) * 0.01D : 0.0D;
    }

    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.getNewDamage() < 1.0F) {
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;

        Entity direct = event.getSource().getDirectEntity();
        boolean swordHit = direct == attacker && isSword(livingAttacker.getMainHandItem());
        if (!swordHit && !event.getSource().is(BLADE_QUIRK_DAMAGE)
                && (direct == null || !direct.getType().is(BLADE_QUIRK_ENTITY))) return;
        if (target.getType().is(TensuraEntityTags.NO_BLOOD)
                || target.hasEffect(MHAEffects.COALGULATION)
                || !SkillUtils.hasSkill(livingAttacker, QuirkSkills.BLOODCURDLE.get())) return;

        if (target.addEffect(new MobEffectInstance(MHAEffects.BLEEDING, BLEEDING_TICKS, 0))) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    MHASounds.BLEED.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static boolean isSword(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(BLADE_ITEM);
    }

    @SubscribeEvent
    public static void onBloodParalysisExpired(MobEffectEvent.Expired event) {
        LivingEntity target = event.getEntity();
        if (!target.level().isClientSide && target.isAlive()
                && event.getEffectInstance().getEffect().value() == MHAEffects.BLOOD_PARALYSIS.get()) {
            PENDING_COALGULATION.add(target);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        while (!PENDING_COALGULATION.isEmpty()) {
            LivingEntity target = PENDING_COALGULATION.removeFirst();
            if (target.isAlive() && !target.hasEffect(MHAEffects.BLOOD_PARALYSIS)) {
                target.addEffect(new MobEffectInstance(MHAEffects.COALGULATION, COALGULATION_TICKS, 0));
            }
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!event.getEntity().hasEffect(MHAEffects.COALGULATION)) return;
        var effect = event.getEffectInstance().getEffect().value();
        if (effect == MHAEffects.BLEEDING.get() || effect == MHAEffects.BLOOD_PARALYSIS.get()) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != 0 || entity.level().isClientSide) return;

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, TARGET_RANGE, false);
        if (target == null || !target.isAlive() || target.hasEffect(MHAEffects.COALGULATION)
                || !target.hasEffect(MHAEffects.BLEEDING)) return;

        int duration = bloodTypeDuration(target);
        if (duration == 0) return;
        if (instance.isMastered(entity)) duration *= 2;

        if (SkillUtils.hasSkill(target, ResistanceSkills.PARALYSIS_NULLIFICATION.get())) {
            duration = Math.max(1, duration / 4);
        } else if (SkillUtils.hasSkill(target, ResistanceSkills.PARALYSIS_RESISTANCE.get())) {
            duration = Math.max(1, duration * 3 / 4);
        }

        double auraCost = getAuraCost(entity, instance, mode);
        var existence = TensuraStorages.getExistenceFrom(entity);
        if (existence.getAura() < auraCost) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.literal("You need " + (long) Math.ceil(auraCost)
                        + " aura for Blood Paralysis."), true);
            }
            return;
        }
        if (!target.addEffect(new MobEffectInstance(MHAEffects.BLOOD_PARALYSIS, duration, 0))) return;
        existence.setAura(existence.getAura() - auraCost);
        existence.markDirty();
        target.removeEffect(MHAEffects.BLEEDING);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                MHASounds.BLEED_REVERSED.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        applyBadTaste(instance, entity, target);
        instance.addMasteryPoint(entity);
    }

    private static void applyBadTaste(ManasSkillInstance instance, LivingEntity user, LivingEntity target) {
        CompoundTag data = instance.getOrCreateTag();
        boolean sameTarget = data.hasUUID(LAST_TARGET_TAG)
                && data.getUUID(LAST_TARGET_TAG).equals(target.getUUID());
        int streak = sameTarget ? Math.min(256, data.getInt(TARGET_STREAK_TAG) + 1) : 1;
        data.putUUID(LAST_TARGET_TAG, target.getUUID());
        data.putInt(TARGET_STREAK_TAG, streak);
        instance.markDirty();

        if (streak == 1) return;
        int amplifier = streak - 2; // Second use: level I.
        MobEffectInstance oldTaste = user.getEffect(MHAEffects.BAD_TASTE);
        if (oldTaste != null && oldTaste.getAmplifier() > amplifier) {
            // A new target starts a new streak, even while the earlier taste remains.
            user.removeEffect(MHAEffects.BAD_TASTE);
            user.removeEffect(MobEffects.CONFUSION);
        }
        user.addEffect(new MobEffectInstance(MHAEffects.BAD_TASTE, BAD_TASTE_TICKS, amplifier));
        user.addEffect(new MobEffectInstance(MobEffects.CONFUSION, BAD_TASTE_TICKS, amplifier));
    }

    // Each letter is converted using a phone keypad. Test divisibility as digits are read
    // so long player names cannot overflow a numeric type.
    static int bloodTypeDuration(LivingEntity target) {
        String name = target instanceof Player player
                ? player.getGameProfile().getName()
                : target.getName().getString();
        int lastDigit = -1, mod3 = 0, mod7 = 0;
        for (int i = 0; i < name.length(); i++) {
            int digit = phoneDigit(Character.toLowerCase(name.charAt(i)));
            if (digit < 0) continue;
            mod3 = (mod3 * 10 + digit) % 3;
            mod7 = (mod7 * 10 + digit) % 7;
            lastDigit = digit;
        }
        // A living target always needs a blood type. Names with no keypad digits,
        // or numbers matching none of the rules, use type A as the default.
        if (lastDigit < 0) return TYPE_A_TICKS;
        // Resolve overlapping multiples in favor of the shortest paralysis.
        if (mod7 == 0) return TYPE_O_NEGATIVE_TICKS;
        if (mod3 == 0) return TYPE_B_TICKS;
        if (lastDigit % 2 == 0) return TYPE_A_TICKS;
        return TYPE_A_TICKS; // A fallback (for example, Cow -> 269)
    }

    private static int phoneDigit(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'c') return 2;
        if (ch >= 'd' && ch <= 'f') return 3;
        if (ch >= 'g' && ch <= 'i') return 4;
        if (ch >= 'j' && ch <= 'l') return 5;
        if (ch >= 'm' && ch <= 'o') return 6;
        if (ch >= 'p' && ch <= 's') return 7;
        if (ch >= 't' && ch <= 'v') return 8;
        if (ch >= 'w' && ch <= 'z') return 9;
        return -1;
    }
}

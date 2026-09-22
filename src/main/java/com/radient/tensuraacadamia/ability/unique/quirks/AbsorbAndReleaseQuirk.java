package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public class AbsorbAndReleaseQuirk extends Skill {

    private static final QuirkSkillsConfig.AbsorbAndRelease CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).AbsorbAndRelease;

    private static final int RELEASE = 0;
    private static final int STORED = 1;

    private static final String STORED_TAG = "storedDamage";

    public AbsorbAndReleaseQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == RELEASE ? STORED : RELEASE;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case RELEASE -> "absorb_and_release.release";
            case STORED -> "absorb_and_release.stored";
            default -> super.getModeId(instance, mode);
        };
    }

    // Absorb

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (instance.getMastery() < 0.0D || owner.level().isClientSide || amount.get() <= 0.0F || !canAbsorb(source)) {
            return true;
        }

        // Capped damage
        boolean mastered = instance.isMastered(owner);
        float max = (float) (mastered ? CONFIG.maxStoredMastered : CONFIG.maxStored);
        float absorbed = Math.min((float) (amount.get() * (mastered ? CONFIG.absorbMultiplierMastered : 1.0D)), max - getStoredTotal(getStored(instance)));
        if (absorbed <= 0.0F) {
            return true;
        }

        store(instance, source, absorbed);

        if (owner.getRandom().nextBoolean()) {
            instance.addMasteryPoint(owner);
        }

        return true;
    }

    private static boolean canAbsorb(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile || TensuraDamageHelper.isTensuraMagic(source);
    }

    // Stored by damage type, element, and magic type
    private static void store(ManasSkillInstance instance, DamageSource source, float amount) {
        ResourceKey<DamageType> type = source.typeHolder().unwrapKey().orElse(null);
        if (type == null) {
            return;
        }

        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        Element element = tensuraSource.tensura$getElement();
        Magic.MagicType magicType = tensuraSource.tensura$getMagicType();
        String typeId = type.location().toString();
        String elementId = element == null ? "" : element.name();
        String magicId = magicType == null ? "" : magicType.name();

        CompoundTag tag = instance.getOrCreateTag();
        ListTag stored = tag.getList(STORED_TAG, Tag.TAG_COMPOUND);
        CompoundTag entry = null;
        for (int i = 0; i < stored.size() && entry == null; i++) {
            CompoundTag other = stored.getCompound(i);
            if (other.getString("Type").equals(typeId) && other.getString("Element").equals(elementId) && other.getString("MagicType").equals(magicId)) {
                entry = other;
            }
        }

        if (entry == null) {
            entry = new CompoundTag();
            entry.putString("Type", typeId);
            entry.putString("Element", elementId);
            entry.putString("MagicType", magicId);
            stored.add(entry);
        }

        entry.putFloat("Amount", entry.getFloat("Amount") + amount);
        tag.put(STORED_TAG, stored);
        instance.markDirty();
    }

    private static ListTag getStored(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? new ListTag() : tag.getList(STORED_TAG, Tag.TAG_COMPOUND);
    }

    private static float getStoredTotal(ListTag stored) {
        float total = 0.0F;
        for (int i = 0; i < stored.size(); i++) {
            total += stored.getCompound(i).getFloat("Amount");
        }

        return total;
    }

    // Stored damage resets on death
    @Override
    public boolean onDeath(ManasSkillInstance instance, LivingEntity owner, DamageSource source) {
        CompoundTag tag = instance.getTag();
        if (tag != null && tag.contains(STORED_TAG)) {
            tag.remove(STORED_TAG);
            instance.markDirty();
        }

        return true;
    }

    // Release

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == STORED) {
            showStored(instance, entity);
            return;
        }

        if (mode != RELEASE) {
            return;
        }

        ListTag stored = getStored(instance);
        float total = getStoredTotal(stored);
        if (total <= 0.0F) {
            fail(entity, "tracadamia.skill.absorb_and_release.nothing_stored");
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.releaseRadiusMastered : CONFIG.releaseRadius;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                target -> target != entity && target.isAlive() && !entity.isAlliedTo(target) && target.distanceTo(entity) <= radius);

        // Split between all targets
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag entry = stored.getCompound(i);
            for (LivingEntity target : targets) {
                hurtWithoutIframes(target, createSource(level, entity, entry), entry.getFloat("Amount") / targets.size());
            }
        }

        instance.getOrCreateTag().remove(STORED_TAG);
        instance.markDirty();

        // Release recoil
        double selfDamage = mastered ? CONFIG.releaseSelfDamageMastered : CONFIG.releaseSelfDamage;
        hurtWithoutIframes(entity, entity.damageSources().generic(), (float) (total * selfDamage));

        spawnBurst(level, entity, radius);
        entity.swing(InteractionHand.MAIN_HAND, true);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.absorb_and_release.released", format(total)).withStyle(ChatFormatting.GOLD), true);
        }

        instance.addMasteryPoint(entity);
    }

    private static void showStored(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            float max = (float) (instance.isMastered(entity) ? CONFIG.maxStoredMastered : CONFIG.maxStored);
            player.displayClientMessage(Component.translatable("tracadamia.skill.absorb_and_release.stored", format(getStoredTotal(getStored(instance))), format(max)).withStyle(ChatFormatting.GOLD), true);
        }
    }

    // Same type, element, and magic type as absorbed
    private static DamageSource createSource(ServerLevel level, LivingEntity owner, CompoundTag entry) {
        ResourceLocation typeId = ResourceLocation.tryParse(entry.getString("Type"));
        Holder<DamageType> type = typeId == null ? null : level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, typeId)).orElse(null);

        DamageSource source = new DamageSource(type != null ? type : level.damageSources().generic().typeHolder(), null, owner);
        TensuraDamageSource tensuraSource = (TensuraDamageSource) source;
        tensuraSource.tensura$setSkillType(SkillType.UNIQUE);

        Element element = parseEnum(Element.class, entry.getString("Element"));
        if (element != null) {
            tensuraSource.tensura$setElement(element);
        }

        Magic.MagicType magicType = parseEnum(Magic.MagicType.class, entry.getString("MagicType"));
        if (magicType != null) {
            tensuraSource.tensura$setMagicType(magicType);
        }

        return source;
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(Class<E> type, String name) {
        if (name.isEmpty()) {
            return null;
        }

        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void hurtWithoutIframes(LivingEntity target, DamageSource source, float damage) {
        int invulnerableTime = target.invulnerableTime;
        try {
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        } finally {
            target.invulnerableTime = invulnerableTime;
        }
    }

    // Release burst
    private static void spawnBurst(ServerLevel level, LivingEntity entity, double radius) {
        level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX(), entity.getY() + 0.1D, entity.getZ(), 0, 0.0D, 1.0D, 0.0D, 1.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), entity.getY(0.5D), entity.getZ(), 6, radius * 0.4D, 0.3D, radius * 0.4D, 0.0D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static String format(float amount) {
        return String.format(Locale.ROOT, "%.1f", amount);
    }

    private static void fail(LivingEntity entity, String key) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.particle.TensuraParticleUtils;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class AbsorbAndReleaseQuirk extends Skill {

    private static final QuirkSkillsConfig.AbsorbAndRelease CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).AbsorbAndRelease;

    private static final int RELEASE = 0;
    private static final int STORED = 1;

    private static final String STORED_TAG = "storedDamage";

    private static final int AMBIENT_INTERVAL = 4;
    private static final int BURST_DIRECTIONS = 16;

    public AbsorbAndReleaseQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == RELEASE ? CONFIG.auraCost : 0.0D;
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
        float absorbed = Math.min((float) (amount.get() * (mastered ? CONFIG.absorbMultiplierMastered : CONFIG.absorbMultiplier)), max - getStoredTotal(getStored(instance)));
        if (absorbed <= 0.0F) {
            return true;
        }

        int color = store(instance, source, absorbed);
        if (owner.level() instanceof ServerLevel level && isVisible(getStoredTotal(getStored(instance)), max)) {
            level.sendParticles(dust(color, 1.2F), owner.getX(), owner.getY(0.5D), owner.getZ(), 12, owner.getBbWidth() * 0.6D, owner.getBbHeight() * 0.3D, owner.getBbWidth() * 0.6D, 0.0D);
        }

        if (owner.getRandom().nextBoolean()) {
            instance.addMasteryPoint(owner);
        }

        return true;
    }

    private static boolean canAbsorb(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile || TensuraDamageHelper.isTensuraMagic(source);
    }

    // Stored by damage type, element, and magic type
    private static int store(ManasSkillInstance instance, DamageSource source, float amount) {
        ResourceKey<DamageType> type = source.typeHolder().unwrapKey().orElse(null);
        if (type == null) {
            return getColor(source.typeHolder(), null, null);
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
        return getColor(source.typeHolder(), element, magicType);
    }

    private static int getColor(@Nullable Holder<DamageType> type, @Nullable Element element, @Nullable Magic.MagicType magicType) {
        if (element != null) {
            return element.getColor() & 0xFFFFFF;
        }

        if (type == null) {
            return 0xD8D8D8;
        }

        if (type.is(DamageTypeTags.IS_FIRE)) {
            return 0xFF7A1A;
        }

        if (type.is(DamageTypeTags.IS_LIGHTNING)) {
            return 0xFFF15C;
        }

        if (type.is(DamageTypeTags.IS_FREEZING)) {
            return 0x9FE8FF;
        }

        if (type.is(DamageTypeTags.IS_DROWNING)) {
            return 0x2F6BFF;
        }

        if (type.is(DamageTypeTags.IS_EXPLOSION)) {
            return 0xB4502D;
        }

        if (magicType != null || type.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return 0xA55CFF;
        }

        return 0xD8D8D8;
    }

    private static int getColor(ServerLevel level, CompoundTag entry) {
        return getColor(getDamageType(level, entry), parseEnum(Element.class, entry.getString("Element")), parseEnum(Magic.MagicType.class, entry.getString("MagicType")));
    }

    private static int pickColor(ServerLevel level, ListTag stored, float total, float roll) {
        float remaining = roll * total;
        for (int i = 0; i < stored.size(); i++) {
            CompoundTag entry = stored.getCompound(i);
            remaining -= entry.getFloat("Amount");
            if (remaining <= 0.0F) {
                return getColor(level, entry);
            }
        }

        return getColor(level, stored.getCompound(stored.size() - 1));
    }

    private static DustParticleOptions dust(int color, float scale) {
        return new DustParticleOptions(Vec3.fromRGB24(color).toVector3f(), scale);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || player.tickCount % AMBIENT_INTERVAL != 0) {
            return;
        }

        SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.ABSORB_AND_RELEASE.get()).ifPresent(instance -> {
            ListTag stored = getStored(instance);
            float total = getStoredTotal(stored);
            if (total <= 0.0F) {
                return;
            }

            float max = (float) (instance.isMastered(player) ? CONFIG.maxStoredMastered : CONFIG.maxStored);
            if (!isVisible(total, max)) {
                return;
            }

            int count = 1 + Mth.floor(Math.min(1.0F, total / max) * 3.0F);
            double radius = player.getBbWidth() * 0.5D + 0.35D;
            for (int i = 0; i < count; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
                level.sendParticles(dust(pickColor(level, stored, total, player.getRandom().nextFloat()), 0.9F),
                        player.getX() + Math.cos(angle) * radius, player.getY() + player.getRandom().nextDouble() * player.getBbHeight(), player.getZ() + Math.sin(angle) * radius,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        });
    }

    private static boolean isVisible(float total, float max) {
        return total >= max * CONFIG.visibleThreshold;
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
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled()) {
            clearStored(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered()) {
            clearStored(event.getEntity());
        }
    }

    private static void clearStored(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ABSORB_AND_RELEASE.get()).ifPresent(instance -> {
            CompoundTag tag = instance.getTag();
            if (tag != null && tag.contains(STORED_TAG)) {
                tag.remove(STORED_TAG);
                instance.markDirty();
                SkillAPI.getSkillsFrom(entity).markDirty();
            }
        });
    }

    // Release

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == STORED) {
            showStored(instance, entity);
            instance.setCoolDown(CONFIG.storedCooldown, mode);
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

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
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

        spawnBurst(level, entity, radius, stored, total);
        entity.swing(InteractionHand.MAIN_HAND, true);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.absorb_and_release.released", format(total)).withStyle(ChatFormatting.GOLD), true);
        }

        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.releaseCooldown, mode);
    }

    private static void showStored(ManasSkillInstance instance, LivingEntity entity) {
        if (entity instanceof Player player) {
            float max = (float) (instance.isMastered(entity) ? CONFIG.maxStoredMastered : CONFIG.maxStored);
            player.displayClientMessage(Component.translatable("tracadamia.skill.absorb_and_release.stored", format(getStoredTotal(getStored(instance))), format(max)).withStyle(ChatFormatting.GOLD), true);
        }
    }

    // Same type, element, and magic type as absorbed
    private static DamageSource createSource(ServerLevel level, LivingEntity owner, CompoundTag entry) {
        Holder<DamageType> type = getDamageType(level, entry);
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

    private static @Nullable Holder<DamageType> getDamageType(ServerLevel level, CompoundTag entry) {
        ResourceLocation typeId = ResourceLocation.tryParse(entry.getString("Type"));
        return typeId == null ? null : level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, typeId)).orElse(null);
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
    private static void spawnBurst(ServerLevel level, LivingEntity entity, double radius, ListTag stored, float total) {
        double y = entity.getY(0.5D);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessWave(0.9F, (float) radius),
                entity.getX(), y, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D, true);
        TensuraParticleHelper.spawnServerParticles(level, TensuraParticleUtils.getColorlessWave(0.6F, (float) (radius * 0.5D)),
                entity.getX(), y, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D, true);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, entity.getX(), y, entity.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);

        for (int step = 0; step < BURST_DIRECTIONS; step++) {
            double angle = step * Math.PI * 2.0D / BURST_DIRECTIONS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, entity.getX() + dx, y, entity.getZ() + dz, 0, dx, 0.0D, dz, 1.0D);

            for (double distance = 2.0D; distance <= radius; distance += 2.0D) {
                level.sendParticles(ParticleTypes.EXPLOSION, entity.getX() + dx * distance, y, entity.getZ() + dz * distance, 1, 0.0D, 0.2D, 0.0D, 0.0D);
            }

            DustParticleOptions dust = dust(pickColor(level, stored, total, (step + 0.5F) / BURST_DIRECTIONS), 1.3F);
            for (double distance = 1.5D; distance <= radius; distance += 1.5D) {
                level.sendParticles(dust, entity.getX() + dx * distance, y, entity.getZ() + dz * distance, 3, 0.25D, 0.4D, 0.25D, 0.0D);
            }
        }

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
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

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.AdditiveBoost;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.entity.magic.beam.SolarBeamProjectile;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WholeBodyLensQuirk extends Skill {

    private static final QuirkSkillsConfig.WholeBodyLens CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WholeBodyLens;

    private static final int CONDENSED_LIGHT = 0;
    private static final int FLASH_CAPTURE = 1;
    private static final int LENS_ARRAY = 2;
    private static final int MODES = 3;
    private static final int LEND_SECONDS = 10;

    public static final String CHARGE_TAG = "lightCharge";
    private static final ResourceLocation LIGHT = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "whole_body_lens_light_boost");
    private static final int BEAM_TICKS = 8;
    private static final double MIN_BEAM_SIZE = 0.5D;
    private static final double MAX_BEAM_SIZE = 2.0D;

    private static final ResourceLocation REVEALED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "lens_array_revealed");

    private record Blinded(Mob mob, long until) {}

    private record Reveal(LivingEntity target, long until) {}

    private static final List<SolarBeamProjectile> FLASHES = new ArrayList<>();
    private static final List<Blinded> BLINDED = new ArrayList<>();
    private static final List<Reveal> REVEALS = new ArrayList<>();
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICK_SECONDS = 5;

    public WholeBodyLensQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CONDENSED_LIGHT -> entity.isShiftKeyDown() ? 0.0D : CONFIG.beamAuraCost;
            case FLASH_CAPTURE -> CONFIG.flashAuraCost;
            case LENS_ARRAY -> CONFIG.lensAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case CONDENSED_LIGHT -> "whole_body_lens.condensed_light";
            case FLASH_CAPTURE -> "whole_body_lens.flash_capture";
            case LENS_ARRAY -> "whole_body_lens.lens_array";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == CONDENSED_LIGHT && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getLens(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WHOLE_BODY_LENS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // temp grant light resistance

    private static void lendSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        ManasSkillInstance owned = skills.getSkill(skill).orElse(null);
        if (owned == null) {
            TensuraSkillInstance lent = skill.createLearningInstance(entity);
            lent.setMastery(0.0D);
            lent.setRemoveTime(LEND_SECONDS);
            if (SkillHelper.learnSkill(entity, lent, LEND_SECONDS)) {
                skills.getSkill(skill).filter(learned -> !learned.isToggled()).ifPresent(learned -> {
                    learned.setToggled(true);
                    learned.onToggleOn(entity);
                    skills.markDirty();
                });
            }
        } else if (owned.isTemporarySkill() && owned.getRemoveTime() < LEND_SECONDS) {
            owned.setRemoveTime(LEND_SECONDS);
            skills.markDirty();
        }
    }

    private static void takeSkill(LivingEntity entity, TensuraSkill skill) {
        SkillStorage skills = SkillAPI.getSkillsFrom(entity);
        if (skills.getSkill(skill).filter(ManasSkillInstance::isTemporarySkill).isPresent()) {
            skills.forgetSkill(skill);
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            lendSkill(entity, ResistanceSkills.LIGHT_ATTACK_RESISTANCE.get());
        }
    }

    // Light Affinity and Light Charge Up

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        updateLight(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        updateLight(entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        lendSkill(entity, ResistanceSkills.LIGHT_ATTACK_RESISTANCE.get());
        updateLight(entity);
        if (!instance.isToggled()) {
            return;
        }

        instance.addMasteryPoint(entity);
        if (!(entity instanceof Player)) {
            chargeInSun(instance, entity, TICK_SECONDS);
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, ResistanceSkills.LIGHT_ATTACK_RESISTANCE.get());
        AdditiveBoost.apply(entity, TensuraAttributes.LIGHT_BOOST, LIGHT, 0.0D);
    }

    private static void updateLight(LivingEntity entity) {
        boolean on = getLens(entity).filter(ManasSkillInstance::isToggled).isPresent();
        AdditiveBoost.apply(entity, TensuraAttributes.LIGHT_BOOST, LIGHT, on ? CONFIG.lightBoost : 0.0D);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            getLens(player).ifPresent(instance -> {
                updateLight(player);
                if (instance.isToggled() && player.tickCount % TICKS_PER_SECOND == 0) {
                    chargeInSun(instance, player, 1);
                }
            });
        }
    }

    public static boolean isInSun(LivingEntity entity) {
        Level level = entity.level();
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        return level.dimensionType().hasSkyLight() && level.isDay() && level.canSeeSky(eye) && !level.isRainingAt(eye);
    }

    private static void chargeInSun(ManasSkillInstance instance, LivingEntity entity, int seconds) {
        if (isInSun(entity)) {
            addCharge(instance, (instance.isMastered(entity) ? CONFIG.sunChargeMastered : CONFIG.sunCharge) * seconds);
        }
    }

    public static double getCharge(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(CHARGE_TAG);
    }

    private static void addCharge(ManasSkillInstance instance, double amount) {
        double charge = Math.min(CONFIG.maxCharge, getCharge(instance) + amount);
        if (charge != getCharge(instance)) {
            instance.getOrCreateTag().putDouble(CHARGE_TAG, charge);
            instance.markDirty();
        }
    }

    // Light damage taken charges up too
    @SubscribeEvent
    public static void onLightHit(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getNewDamage() <= 0.0F || !isLight(entity, event.getSource())) {
            return;
        }

        getLens(entity).filter(ManasSkillInstance::isToggled).ifPresent(instance -> addCharge(instance, event.getNewDamage() * CONFIG.lightDamageCharge));
    }

    private static boolean isLight(LivingEntity entity, DamageSource source) {
        return ((TensuraDamageSource) source).tensura$getElement() == Element.LIGHT || ResistanceSkills.LIGHT_ATTACK_RESISTANCE.get().isDamageResisted(entity, source, new TensuraSkillInstance(ResistanceSkills.LIGHT_ATTACK_RESISTANCE.get()));
    }

    // Condensed Light

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (mode == FLASH_CAPTURE) {
            flashCapture(level, instance, entity);
            return;
        }

        if (mode == LENS_ARRAY) {
            lensArray(level, instance, entity);
            return;
        }

        if (mode != CONDENSED_LIGHT) {
            return;
        }

        if (entity.isShiftKeyDown()) {
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.whole_body_lens.charge", (long) getCharge(instance)).withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }

        condensedLight(level, instance, entity);
    }

    // Im going to borrow the solar beam
    private static void condensedLight(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        double charge = getCharge(instance);
        if (charge <= 0.0D) {
            fail(entity, Component.translatable("tracadamia.skill.whole_body_lens.no_light"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, CONDENSED_LIGHT)) {
            return;
        }

        SolarBeamProjectile beam = new SolarBeamProjectile(level, entity);
        beam.setFollowingOwner(true);
        beam.setLife(BEAM_TICKS);
        beam.setDamage((float) (charge * CONFIG.beamDamagePerCharge));
        beam.setSize((float) Math.min(MAX_BEAM_SIZE, MIN_BEAM_SIZE + charge / CONFIG.maxCharge));
        beam.setRange((float) CONFIG.beamRange);
        beam.setSkill(entity, instance, (TensuraSkill) instance.getSkill(), CONDENSED_LIGHT);
        beam.setElement(Element.LIGHT);
        beam.updateAngle(entity);
        level.addFreshEntity(beam);
        FLASHES.add(beam);
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, entity.getEyePosition(), TensuraSoundEvents.CAST_LIGHT.get(), 1.0F, 0.8F);
        playSound(level, entity.getEyePosition(), SoundEvents.BEACON_ACTIVATE, 1.0F, 2.0F);

        instance.getOrCreateTag().putDouble(CHARGE_TAG, 0.0D);
        instance.markDirty();
        instance.setCoolDown(CONFIG.beamCooldown, CONDENSED_LIGHT);
        instance.addMasteryPoint(entity);
    }

    private static List<LivingEntity> getEnemies(ServerLevel level, LivingEntity entity, double radius) {
        Vec3 center = entity.getBoundingBox().getCenter();
        return level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                other -> other != entity && other.isAlive() && !other.isSpectator() && HitRules.canHit(entity, other) && other.getBoundingBox().getCenter().distanceTo(center) <= radius);
    }


    private static void flashCapture(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, FLASH_CAPTURE)) {
            return;
        }

        double radius = instance.isMastered(entity) ? CONFIG.flashRadiusMastered : CONFIG.flashRadius;
        int ticks = (int) Math.round(CONFIG.flashSeconds * 20.0D);
        long until = level.getGameTime() + ticks;
        int blinded = 0;
        for (LivingEntity target : getEnemies(level, entity, radius)) {
            if (!target.hasLineOfSight(entity)) {
                continue;
            }

            boolean flashed = TensuraMobEffect.addEffect(target, new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FLASHED_BLINDNESS), ticks, 0, false, false, true), entity, instance.getSkill(), FLASH_CAPTURE);
            boolean blind = TensuraMobEffect.addEffect(target, new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, true), entity, instance.getSkill(), FLASH_CAPTURE);
            if (!flashed && !blind) {
                continue;
            }

            // Mobs lose aggro if affected
            if (target instanceof Mob mob) {
                mob.setTarget(null);
                BLINDED.removeIf(old -> old.mob() == mob);
                BLINDED.add(new Blinded(mob, until));
            }
            blinded++;
        }

        Vec3 eye = entity.getEyePosition();
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y - 0.4D, eye.z, 40, 0.3D, 0.5D, 0.3D, radius * 0.06D);
        playSound(level, eye, SoundEvents.FIREWORK_ROCKET_BLAST, 1.5F, 1.4F);
        playSound(level, eye, TensuraSoundEvents.CAST_LIGHT.get(), 1.0F, 1.5F);
        instance.setCoolDown(CONFIG.flashCooldown, FLASH_CAPTURE);
        instance.addMasteryPoint(entity);
    }


    private static double getConcealment(LivingEntity target) {
        AttributeInstance concealment = target.getAttribute(TensuraAttributes.PRESENCE_CONCEALMENT);
        if (concealment == null) {
            return 0.0D;
        }

        AttributeModifier revealed = concealment.getModifier(REVEALED);
        return concealment.getValue() - (revealed == null ? 0.0D : revealed.amount());
    }

    private static boolean isHidden(LivingEntity target) {
        return target.isInvisible() || target.hasEffect(MobEffects.INVISIBILITY) || getConcealment(target) >= 1.0D;
    }

    private static void lensArray(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, LENS_ARRAY)) {
            return;
        }

        boolean mastered = instance.isMastered(entity);
        double radius = mastered ? CONFIG.lensRadiusMastered : CONFIG.lensRadius;
        int sees = mastered ? CONFIG.lensConcealmentMastered : CONFIG.lensConcealment;
        int ticks = (int) Math.round(CONFIG.lensSeconds * 20.0D);
        long until = level.getGameTime() + ticks;
        int revealed = 0;
        Vec3 from = entity.getBoundingBox().getCenter();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                other -> other != entity && other.isAlive() && !other.isSpectator() && other.getBoundingBox().getCenter().distanceTo(from) <= radius)) {
            if (!isHidden(target) || getConcealment(target) > sees) {
                continue;
            }

            reveal(target, sees);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, true), entity);
            REVEALS.removeIf(old -> old.target() == target);
            REVEALS.add(new Reveal(target, until));
            Vec3 center = target.getBoundingBox().getCenter();
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 12, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.02D);
            revealed++;
        }

        Vec3 center = entity.getBoundingBox().getCenter();
        for (int point = 0; point < 48; point++) {
            double angle = point * Mth.TWO_PI / 48.0D;
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 0, Math.cos(angle), 0.0D, Math.sin(angle), radius * 0.05D);
        }
        playSound(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.2F);
        playSound(level, center, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.6F);
        instance.setCoolDown(CONFIG.lensCooldown, LENS_ARRAY);
        instance.addMasteryPoint(entity);

    }

    private static void reveal(LivingEntity target, int sees) {
        AttributeInstance concealment = target.getAttribute(TensuraAttributes.PRESENCE_CONCEALMENT);
        if (concealment != null && concealment.getModifier(REVEALED) == null) {
            concealment.addTransientModifier(new AttributeModifier(REVEALED, -sees, AttributeModifier.Operation.ADD_VALUE));
        }
        target.setInvisible(false);
    }

    private static void unreveal(LivingEntity target) {
        AttributeInstance concealment = target.getAttribute(TensuraAttributes.PRESENCE_CONCEALMENT);
        if (concealment != null) {
            concealment.removeModifier(REVEALED);
        }
        target.setInvisible(target.hasEffect(MobEffects.INVISIBILITY));
    }

    private static boolean tickReveal(Reveal reveal) {
        LivingEntity target = reveal.target();
        if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= reveal.until()) {
            unreveal(target);
            return true;
        }

        if (target.isInvisible() && target.hasEffect(MobEffects.INVISIBILITY)) {
            target.setInvisible(false);
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FLASHES.isEmpty()) {
            FLASHES.removeIf(beam -> {
                if (beam.tickCount > 0) {
                    beam.setDamage(0.0F);
                }
                return beam.isRemoved() || beam.tickCount > 0;
            });
        }

        if (!BLINDED.isEmpty()) {
            BLINDED.removeIf(blinded -> {
                Mob mob = blinded.mob();
                if (!mob.isAlive() || mob.isRemoved() || mob.level().getGameTime() >= blinded.until()) {
                    return true;
                }
                mob.setTarget(null);
                return false;
            });
        }

        if (!REVEALS.isEmpty()) {
            REVEALS.removeIf(WholeBodyLensQuirk::tickReveal);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FLASHES.clear();
        BLINDED.clear();
        REVEALS.forEach(reveal -> unreveal(reveal.target()));
        REVEALS.clear();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

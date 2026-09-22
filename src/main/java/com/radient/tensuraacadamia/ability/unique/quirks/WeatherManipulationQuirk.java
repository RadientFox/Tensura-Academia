package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.Skills;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.magic.lightning.LightningBolt;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.AttributeHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WeatherManipulationQuirk extends Skill {

    private static final QuirkSkillsConfig.WeatherManipulation CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WeatherManipulation;

    private static final int LIGHTNING_STRIKE = 0;
    private static final int SUMMON_STORM = 1;
    private static final int BOLT_CHARGE = 2;

    private static final float BOLT_RADIUS = 1.5F;
    private static final int RESISTANCE_SECONDS = 10;

    private static final int STORM_VISUAL_INTERVAL = 15;
    private static final double STORM_VISUAL_MIN_DISTANCE = 8.0D;
    private static final double STORM_VISUAL_MAX_DISTANCE = 24.0D;

    private static final String RESISTANCE_TAG = "tracadamia_weather_resistance";

    private static final ResourceLocation WEATHER_DEGRADATION = ResourceLocation.fromNamespaceAndPath("tracadamia", "weather_manipulation_degradation");

    private static final List<Holder<Attribute>> BOOSTS = List.of(TensuraAttributes.WATER_BOOST, TensuraAttributes.WIND_BOOST, TensuraAttributes.LIGHTNING_BOOST);
    private static final List<Holder<Attribute>> DEGRADATIONS = List.of(TensuraAttributes.LIGHTNING_RESIST_DEGRADATION, TensuraAttributes.WIND_RESIST_DEGRADATION);

    private static final List<PendingBolt> PENDING_BOLTS = new ArrayList<>();
    private static final List<Barrage> BARRAGES = new ArrayList<>();

    private record PendingBolt(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, long strikeTime) {}

    private record Barrage(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, float damage, long startTime, long endTime) {}

    public WeatherManipulationQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == LIGHTNING_STRIKE ? BOLT_CHARGE : mode - 1;
        }

        return mode == BOLT_CHARGE ? LIGHTNING_STRIKE : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case LIGHTNING_STRIKE -> "weather_manipulation.lightning_strike";
            case SUMMON_STORM -> "weather_manipulation.summon_storm";
            case BOLT_CHARGE -> "weather_manipulation.bolt_charge";
            default -> super.getModeId(instance, mode);
        };
    }

    // Weather Domination, Passive

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        if (instance.getMastery() >= 0.0D && !instance.isTemporarySkill()) {
            grantWeatherDomination(entity);
        }
    }

    private static void grantWeatherDomination(LivingEntity entity) {
        ManasSkill weatherDomination = ExtraSkills.WEATHER_DOMINATION.get();
        if (SkillUtils.hasSkillFully(entity, weatherDomination)) {
            return;
        }

        TensuraSkillInstance instance = new TensuraSkillInstance(weatherDomination);
        instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        SkillHelper.learnSkill(entity, instance);
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!instance.isTemporarySkill()) {
            grantWeatherDomination(entity);
        }

        if (!instance.isToggled()) {
            return;
        }

        applyPassives(entity);

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }

        tag.putInt("activatedTimes", time + 1);
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        applyPassives(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        removePassives(entity);
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        if (instance.isToggled()) {
            applyPassives(owner);
        }
    }

    private static void applyPassives(LivingEntity entity) {
        // Elemental Resistance
        grantResistance(entity, ResistanceSkills.ELECTRICITY_RESISTANCE.get());
        grantResistance(entity, ResistanceSkills.WATER_ATTACK_RESISTANCE.get());
        grantResistance(entity, ResistanceSkills.WIND_ATTACK_RESISTANCE.get());

        // Elemental Damage
        for (Holder<Attribute> boost : BOOSTS) {
            AttributeHelper.multiplyElementalBoost(entity, boost, CONFIG.elementalBoost);
        }

        for (Holder<Attribute> degradation : DEGRADATIONS) {
            AttributeInstance attribute = entity.getAttribute(degradation);
            if (attribute != null && !attribute.hasModifier(WEATHER_DEGRADATION)) {
                attribute.addOrReplacePermanentModifier(new AttributeModifier(WEATHER_DEGRADATION, 1.0D, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // Flight
        if (entity instanceof Player player && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    private static void removePassives(LivingEntity entity) {
        Skills skills = SkillAPI.getSkillsFrom(entity);
        for (ManasSkill resistance : List.of(ResistanceSkills.ELECTRICITY_RESISTANCE.get(), ResistanceSkills.WATER_ATTACK_RESISTANCE.get(), ResistanceSkills.WIND_ATTACK_RESISTANCE.get())) {
            skills.getSkill(resistance).filter(WeatherManipulationQuirk::isWeatherResistance).ifPresent(instance -> skills.forgetSkill(instance, null));
        }

        for (Holder<Attribute> boost : BOOSTS) {
            AttributeHelper.removeElementalMultiplier(entity, boost, CONFIG.elementalBoost);
        }

        for (Holder<Attribute> degradation : DEGRADATIONS) {
            AttributeInstance attribute = entity.getAttribute(degradation);
            if (attribute != null) {
                attribute.removeModifier(WEATHER_DEGRADATION);
            }
        }

        if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    // Temporary resistance
    private static void grantResistance(LivingEntity entity, ManasSkill resistance) {
        Skills skills = SkillAPI.getSkillsFrom(entity);
        ManasSkillInstance owned = skills.getSkill(resistance).orElse(null);
        if (owned != null) {
            if (isWeatherResistance(owned)) {
                owned.setRemoveTime(RESISTANCE_SECONDS);
                owned.markDirty();
                skills.markDirty();
            }
            return;
        }

        TensuraSkillInstance instance = new TensuraSkillInstance(resistance);
        instance.setToggled(true);
        instance.getOrCreateTag().putBoolean(RESISTANCE_TAG, true);
        instance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        SkillHelper.learnSkill(entity, instance, RESISTANCE_SECONDS, null);
    }

    private static boolean isWeatherResistance(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return instance.isTemporarySkill() && tag != null && tag.getBoolean(RESISTANCE_TAG);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case LIGHTNING_STRIKE -> lightningStrike(level, instance, entity);
            case SUMMON_STORM -> summonStorm(level, instance, entity);
        }
    }

    // Lightning Strike
    private void lightningStrike(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!level.isRaining()) {
            fail(entity, "tracadamia.skill.weather_manipulation.needs_rain");
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.strikeRange, false, false);
        Vec3 pos = target != null ? target.position() : ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, CONFIG.strikeRange).getLocation();
        float damage = (float) (instance.isMastered(entity) ? CONFIG.strikeDamageMastered : CONFIG.strikeDamage);

        strike(instance, entity, LIGHTNING_STRIKE, pos, damage);
        entity.swing(InteractionHand.MAIN_HAND, true);
        entity.playSound(TensuraSoundEvents.CAST_LIGHTNING.get());
        instance.addMasteryPoint(entity);
    }

    // Summon Storm
    private void summonStorm(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!level.isThundering()) {
            fail(entity, "tracadamia.skill.weather_manipulation.needs_thunder");
            return;
        }

        double radius = instance.isMastered(entity) ? CONFIG.stormRadiusMastered : CONFIG.stormRadius;
        float damage = (float) (instance.isMastered(entity) ? CONFIG.stormDamageMastered : CONFIG.stormDamage);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(radius),
                target -> target != entity && target.isAlive() && !entity.isAlliedTo(target) && target.distanceTo(entity) <= radius);

        if (targets.isEmpty()) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        long time = level.getGameTime();
        for (LivingEntity target : targets) {
            strike(instance, entity, SUMMON_STORM, target.position(), damage);
            PENDING_BOLTS.add(new PendingBolt(instance, entity, target, damage, time + CONFIG.stormBoltDelay));
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        entity.playSound(TensuraSoundEvents.CAST_LIGHTNING.get());
        instance.addMasteryPoint(entity);
    }

    // Bolt Charge
    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != BOLT_CHARGE) {
            return false;
        }

        if (!entity.level().isThundering()) {
            if (heldTicks == 0) {
                fail(entity, "tracadamia.skill.weather_manipulation.needs_thunder");
            }
            return false;
        }

        int chargeTicks = getChargeTicks(instance, entity);
        if (heldTicks % 5 == 0) {
            TensuraParticleHelper.addServerParticlesAroundSelf(entity, TensuraParticleTypes.LIGHTNING_SPARK.get(), 1.0D);
        }

        // Storm visuals
        if (heldTicks % STORM_VISUAL_INTERVAL == 0 && entity.level() instanceof ServerLevel level) {
            spawnStormVisual(level, entity);
        }

        if (heldTicks == chargeTicks) {
            entity.playSound(TensuraSoundEvents.CAST_LIGHTNING.get());
        }

        // Activation time
        if (heldTicks % 20 == 0 && entity instanceof Player player) {
            int chargeSeconds = chargeTicks / 20;
            int second = Math.min(heldTicks / 20, chargeSeconds);
            player.displayClientMessage(Component.translatable("tensura.skill.time_held.max", second, chargeSeconds).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        }

        return true;
    }

    private static void spawnStormVisual(ServerLevel level, LivingEntity entity) {
        var bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }

        RandomSource random = entity.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = STORM_VISUAL_MIN_DISTANCE + random.nextDouble() * (STORM_VISUAL_MAX_DISTANCE - STORM_VISUAL_MIN_DISTANCE);
        int x = Mth.floor(entity.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(entity.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        bolt.moveTo(x + 0.5D, y, z + 0.5D);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != BOLT_CHARGE || heldTicks < getChargeTicks(instance, entity) || !(entity.level() instanceof ServerLevel level) || !level.isThundering()) {
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.chargeRange, false, false);
        if (target == null) {
            fail(entity, "tensura.targeting.not_targeted");
            return;
        }

        boolean mastered = instance.isMastered(entity);
        float damage = (float) (mastered ? CONFIG.barrageDamageMastered : CONFIG.barrageDamage);
        long time = level.getGameTime();
        long length = (mastered ? CONFIG.barrageSecondsMastered : CONFIG.barrageSeconds) * 20L;

        BARRAGES.add(new Barrage(instance, entity, target, damage, time, time + length));
        entity.swing(InteractionHand.MAIN_HAND, true);
        instance.addMasteryPoint(entity);
    }

    private static int getChargeTicks(ManasSkillInstance instance, LivingEntity entity) {
        return (instance.isMastered(entity) ? CONFIG.chargeSecondsMastered : CONFIG.chargeSeconds) * 20;
    }

    // Delayed storm bolts
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!PENDING_BOLTS.isEmpty()) {
            List<PendingBolt> ready = PENDING_BOLTS.stream().filter(bolt -> bolt.owner().level().getGameTime() >= bolt.strikeTime()).toList();
            PENDING_BOLTS.removeAll(ready);

            for (PendingBolt bolt : ready) {
                if (canStrike(bolt.owner(), bolt.target())) {
                    strike(bolt.instance(), bolt.owner(), SUMMON_STORM, bolt.target().position(), bolt.damage());
                }
            }
        }

        for (Barrage barrage : List.copyOf(BARRAGES)) {
            long time = barrage.owner().level().getGameTime();
            if (time >= barrage.endTime() || !canStrike(barrage.owner(), barrage.target())) {
                BARRAGES.remove(barrage);
                continue;
            }

            if ((time - barrage.startTime()) % CONFIG.barrageInterval == 0) {
                strike(barrage.instance(), barrage.owner(), BOLT_CHARGE, barrage.target().position(), barrage.damage());
            }
        }
    }

    private static boolean canStrike(LivingEntity owner, LivingEntity target) {
        return owner.isAlive() && target.isAlive() && !owner.isRemoved() && !target.isRemoved() && owner.level() == target.level();
    }

    private static void strike(ManasSkillInstance instance, LivingEntity owner, int mode, Vec3 pos, float damage) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }

        LightningBolt bolt = new LightningBolt(level, owner);
        bolt.setCause(owner instanceof ServerPlayer player ? player : null);
        bolt.setSkill(owner, instance, (TensuraSkill) instance.getSkill(), mode);
        bolt.setTensuraDamage(damage);
        bolt.setRadius(BOLT_RADIUS);
        bolt.setAdditionalVisual(4);
        bolt.setSkill(instance);
        bolt.setPos(pos);
        level.addFreshEntity(bolt);
    }

    private static void fail(LivingEntity entity, String key) {
        Level level = entity.level();
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.StormBoltEntity;
import com.radient.tensuraacadamia.entity.TornadoEntity;
import com.radient.tensuraacadamia.util.DamageReduction;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkill;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.magic.lightning.LightningBolt;
import io.github.manasmods.tensura.particle.TensuraParticleHelper;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.skill.ExtraSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.AttributeHelper;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WeatherManipulationQuirk extends Skill {

    private static final QuirkSkillsConfig.WeatherManipulation CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).WeatherManipulation;

    private static final int LIGHTNING_STRIKE = 0;
    private static final int SUMMON_STORM = 1;
    private static final int BOLT_CHARGE = 2;
    private static final int SUMMON_TORNADO = 3;

    private static final float BOLT_RADIUS = 1.5F;
    private static final SoundSource ABILITY_SOUND = Arrays.stream(SoundSource.values()).filter(source -> source.getName().equals("ability")).findFirst().orElse(SoundSource.PLAYERS);

    private static final int STORM_VISUAL_INTERVAL = 15;
    private static final double STORM_VISUAL_MIN_DISTANCE = 8.0D;
    private static final double STORM_VISUAL_MAX_DISTANCE = 24.0D;
    private static final double STORM_BOLT_REACH = 3.0D;

    private static final ResourceLocation WEATHER_DEGRADATION = ResourceLocation.fromNamespaceAndPath("tracadamia", "weather_manipulation_degradation");
    private static final ResourceLocation WEATHER_RESISTANCE = ResourceLocation.fromNamespaceAndPath("tracadamia", "weather_manipulation_resistance");

    private static final List<Holder<Attribute>> BOOSTS = List.of(TensuraAttributes.WATER_BOOST, TensuraAttributes.WIND_BOOST, TensuraAttributes.LIGHTNING_BOOST);
    private static final List<Holder<Attribute>> DEGRADATIONS = List.of(TensuraAttributes.LIGHTNING_RESIST_DEGRADATION, TensuraAttributes.WIND_RESIST_DEGRADATION);
    private static final List<Holder<Attribute>> RESISTANCES = List.of(TensuraAttributes.WATER_RESISTANCE, TensuraAttributes.WIND_RESISTANCE);

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
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return CONFIG.auraCost;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 4;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (reverse) {
            return mode == LIGHTNING_STRIKE ? SUMMON_TORNADO : mode - 1;
        }

        return mode == SUMMON_TORNADO ? LIGHTNING_STRIKE : mode + 1;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case LIGHTNING_STRIKE -> "weather_manipulation.lightning_strike";
            case SUMMON_STORM -> "weather_manipulation.summon_storm";
            case BOLT_CHARGE -> "weather_manipulation.bolt_charge";
            case SUMMON_TORNADO -> "weather_manipulation.summon_tornado";
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
        for (Holder<Attribute> resistance : RESISTANCES) {
            AttributeInstance attribute = entity.getAttribute(resistance);
            AttributeModifier current = attribute == null ? null : attribute.getModifier(WEATHER_RESISTANCE);
            if (attribute != null && (current == null || current.amount() != CONFIG.elementalResistance)) {
                attribute.addOrReplacePermanentModifier(new AttributeModifier(WEATHER_RESISTANCE, CONFIG.elementalResistance, AttributeModifier.Operation.ADD_VALUE));
            }
        }

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
        for (Holder<Attribute> boost : BOOSTS) {
            AttributeHelper.removeElementalMultiplier(entity, boost, CONFIG.elementalBoost);
        }

        for (Holder<Attribute> resistance : RESISTANCES) {
            AttributeInstance attribute = entity.getAttribute(resistance);
            if (attribute != null) {
                attribute.removeModifier(WEATHER_RESISTANCE);
            }
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

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (instance.isToggled() && TensuraDamageHelper.isLightningDamage(source) && ((TensuraDamageSource) source).tensura$getElement() != Element.WIND) {
            amount.set(DamageReduction.reduce(owner, source, amount.get(), CONFIG.elementalResistance));
        }

        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case LIGHTNING_STRIKE -> lightningStrike(level, instance, entity);
            case SUMMON_STORM -> summonStorm(level, instance, entity);
            case SUMMON_TORNADO -> summonTornado(level, instance, entity);
        }
    }

    // Lightning Strike
    private void lightningStrike(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!level.isRaining()) {
            fail(entity, "tracadamia.skill.weather_manipulation.needs_rain");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, LIGHTNING_STRIKE)) {
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.strikeRange, false, false);
        Vec3 pos = target != null ? target.position() : ObjectSelectionHelper.getPlayerPOVHitResult(level, entity, ClipContext.Fluid.NONE, CONFIG.strikeRange).getLocation();
        float damage = (float) (instance.isMastered(entity) ? CONFIG.strikeDamageMastered : CONFIG.strikeDamage);

        strike(instance, entity, LIGHTNING_STRIKE, pos, damage);
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(entity, TensuraSoundEvents.CAST_LIGHTNING.get(), 1.0F, 1.0F);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.strikeCooldown, LIGHTNING_STRIKE);
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

        if (EnergyHelper.isOutOfEnergy(entity, instance, SUMMON_STORM)) {
            return;
        }

        long time = level.getGameTime();
        for (LivingEntity target : targets) {
            strike(instance, entity, SUMMON_STORM, target.position(), damage);
            PENDING_BOLTS.add(new PendingBolt(instance, entity, target, damage, time + CONFIG.stormBoltDelay));
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(entity, TensuraSoundEvents.CAST_LIGHTNING.get(), 1.0F, 1.0F);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.stormCooldown, SUMMON_STORM);
    }

    private void summonTornado(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (EnergyHelper.isOutOfEnergy(entity, instance, SUMMON_TORNADO)) {
            return;
        }

        Vec3 forward = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 drift = forward.scale(CONFIG.tornadoSpeed);
        boolean mastered = instance.isMastered(entity);
        float damage = (float) (mastered ? CONFIG.tornadoDamageMastered : CONFIG.tornadoDamage);
        int count = Math.max(1, mastered ? CONFIG.tornadoCountMastered : CONFIG.tornadoCount);
        for (int i = 0; i < count; i++) {
            Vec3 spot = entity.position().add(forward.scale(CONFIG.tornadoDistance)).add(side.scale((i - (count - 1) * 0.5D) * CONFIG.tornadoSpacing));
            TornadoEntity tornado = TornadoEntity.create(level, entity, instance, SUMMON_TORNADO, spot, drift, CONFIG.tornadoSeconds * 20, (float) CONFIG.tornadoSize);
            tornado.setHarm(damage, CONFIG.tornadoPullRadius, CONFIG.tornadoCoreRadius, CONFIG.tornadoPull);
            level.addFreshEntity(tornado);
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(entity, TensuraSoundEvents.CAST_WIND.get(), 1.0F, 0.8F);
        playSound(entity, SoundEvents.ELYTRA_FLYING, 0.6F, 0.6F);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.tornadoCooldown, SUMMON_TORNADO);
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

        // Storm bolts
        if (heldTicks % STORM_VISUAL_INTERVAL == 0 && entity.level() instanceof ServerLevel level) {
            spawnStormBolt(level, instance, entity);
        }

        if (heldTicks == chargeTicks) {
            playSound(entity, TensuraSoundEvents.CAST_LIGHTNING.get(), 1.0F, 1.0F);
        }

        // Activation time
        if (heldTicks % 20 == 0 && entity instanceof Player player) {
            int chargeSeconds = chargeTicks / 20;
            int second = Math.min(heldTicks / 20, chargeSeconds);
            player.displayClientMessage(Component.translatable("tensura.skill.time_held.max", second, chargeSeconds).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        }

        return true;
    }

    private static void spawnStormBolt(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        RandomSource random = entity.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = STORM_VISUAL_MIN_DISTANCE + random.nextDouble() * (STORM_VISUAL_MAX_DISTANCE - STORM_VISUAL_MIN_DISTANCE);
        int x = Mth.floor(entity.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(entity.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        StormBoltEntity bolt = StormBoltEntity.TYPE.get().create(level);
        if (bolt == null) {
            return;
        }

        Vec3 pos = new Vec3(x + 0.5D, y, z + 0.5D);
        bolt.moveTo(pos);
        level.addFreshEntity(bolt);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, ABILITY_SOUND, 4.0F, 0.8F + random.nextFloat() * 0.2F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, ABILITY_SOUND, 2.0F, 0.5F + random.nextFloat() * 0.2F);

        float damage = (float) (instance.isMastered(entity) ? CONFIG.chargeStrikeDamageMastered : CONFIG.chargeStrikeDamage);
        AABB area = new AABB(pos.x - STORM_BOLT_REACH, pos.y - STORM_BOLT_REACH, pos.z - STORM_BOLT_REACH, pos.x + STORM_BOLT_REACH, pos.y + 6.0D + STORM_BOLT_REACH, pos.z + STORM_BOLT_REACH);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != entity && target.isAlive() && !entity.isAlliedTo(target))) {
            target.invulnerableTime = 0;
            target.hurt(((Skill) instance.getSkill()).createSource(instance, entity, TensuraDamageTypes.LIGHTNING_ELEMENTAL, BOLT_CHARGE), damage);
        }
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode != BOLT_CHARGE || heldTicks < getChargeTicks(instance, entity) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.isThundering()) {
            fail(entity, "tracadamia.skill.weather_manipulation.needs_thunder");
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

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        BARRAGES.add(new Barrage(instance, entity, target, damage, time, time + length));
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(entity, TensuraSoundEvents.CAST_LIGHTNING.get(), 1.0F, 1.0F);
        instance.addMasteryPoint(entity);
        instance.setCoolDown(CONFIG.boltChargeCooldown, BOLT_CHARGE);
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

    private static void playSound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, ABILITY_SOUND, volume, pitch);
    }

    private static void fail(LivingEntity entity, String key) {
        playSound(entity, TensuraSoundEvents.GENERIC_CAST_FAIL.get(), 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
    }

}

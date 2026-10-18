package com.radient.tensuraacadamia.ability.unique.quirks;

import com.github.hvnbael.trnightmare.util.CosmeticColor;
import com.github.hvnbael.trnightmare.util.racecosmetic.CosmeticDef;
import com.github.hvnbael.trnightmare.util.racecosmetic.RaceCosmeticRegistry;
import com.github.hvnbael.trnightmare.util.racecosmetic.RaceCosmetics;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.FeatherProjectile;
import com.radient.tensuraacadamia.network.QuirkAnimPayload;
import com.radient.tensuraacadamia.regestry.FierceWingsItems;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class FierceWingsQuirk extends Skill {

    private static final QuirkSkillsConfig.FierceWings CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).FierceWings;

    public static final int TAKE_FLIGHT = 0;
    public static final int FEATHER_CANNON = 1;
    public static final int FEATHER_BLADES = 2;
    public static final int FEATHER_BOOST = 3;
    private static final int MODES = 4;

    private static final String FEATHERS_TAG = "feathers";
    private static final String REGROW_TAG = "regrowTicks";
    private static final String CRUISE_TAG = "cruise";
    private static final String LAUNCH_TAG = "launchAt";
    private static final String LAST_TICK_TAG = "lastTick";
    private static final long MAX_ELAPSED = 100L;

    private static final ResourceLocation BOOST_ATTACK = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "feather_boost_attack");
    private static final ResourceLocation BOOST_MOVE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "feather_boost_move");

    public static final int DEFAULT_COLOR = 0xB3242C;
    private static final ResourceLocation WINGS_COSMETIC = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "quirk/fierce_wings");
    private static final int LAUNCH_DELAY = 3;
    private static final int LAUNCH_WINDOW = 15;
    private static final int FLAME_COOLDOWN = 10;
    private static final int VOLLEY_PER_TICK = 2;

    private static final class Volley {
        private final LivingEntity owner;
        private final @Nullable LivingEntity target;
        private final float damage;
        private int remaining;
        private int fired;

        private Volley(LivingEntity owner, @Nullable LivingEntity target, float damage, int count) {
            this.owner = owner;
            this.target = target;
            this.damage = damage;
            this.remaining = count;
        }
    }

    private record Boost(LivingEntity entity, long until) {}

    private static final List<Volley> VOLLEYS = new ArrayList<>();
    private static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_SCORCH = new HashMap<>();

    public FierceWingsQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "textures/skill/unique/fierce_wings.png");
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
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
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case TAKE_FLIGHT -> "fierce_wings.take_flight";
            case FEATHER_CANNON -> "fierce_wings.feather_cannon";
            case FEATHER_BLADES -> "fierce_wings.feather_blades";
            case FEATHER_BOOST -> "fierce_wings.feather_boost";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getFierceWings(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.FIERCE_WINGS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double masteryShare(ManasSkillInstance instance) {
        return Mth.clamp(instance.getMastery() / CONFIG.masteryPoints, 0.0D, 1.0D);
    }

    public static int getMaxFeathers(ManasSkillInstance instance) {
        double share = masteryShare(instance);
        return share >= 1.0D ? CONFIG.feathersMastered : share >= 0.5D ? CONFIG.feathersHalfMastered : CONFIG.feathers;
    }

    public static int getFeathers(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0 : tag.getInt(FEATHERS_TAG);
    }

    public static void setFeathers(ManasSkillInstance instance, int feathers) {
        int clamped = Mth.clamp(feathers, 0, getMaxFeathers(instance));
        CompoundTag tag = instance.getOrCreateTag();
        tag.putInt(FEATHERS_TAG, clamped);
        double fill = clamped / (double) Math.max(1, CONFIG.feathersMastered);
        tag.putDouble(CRUISE_TAG, CONFIG.minCruiseSpeed + (CONFIG.maxCruiseSpeed - CONFIG.minCruiseSpeed) * fill);
        instance.markDirty();
    }

    public static double getCruiseSpeed(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? CONFIG.minCruiseSpeed : tag.getDouble(CRUISE_TAG);
    }

    public static boolean canWingFly(LivingEntity entity) {
        return entity instanceof Player && !entity.isInWater() && getFierceWings(entity).filter(instance -> getFeathers(instance) > CONFIG.minFlightFeathers).isPresent();
    }

    private static boolean spend(LivingEntity entity, ManasSkillInstance instance, int cost, int minimum) {
        int feathers = getFeathers(instance);
        if (feathers <= minimum || feathers < cost) {
            fail(entity, Component.translatable("tracadamia.skill.fierce_wings.no_feathers", feathers));
            return false;
        }

        setFeathers(instance, feathers - cost);
        return true;
    }

    private static void showFeathers(LivingEntity entity, ManasSkillInstance instance) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.fierce_wings.feathers", getFeathers(instance), getMaxFeathers(instance))
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onLearnSkill(instance, entity);
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.contains(FEATHERS_TAG)) {
            setFeathers(instance, getMaxFeathers(instance));
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        if (entity instanceof Player player && player.isFallFlying() && !player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player)) {
            player.stopFallFlying();
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D && !(entity instanceof Player);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        serverTick(instance, entity);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) {
            getFierceWings(event.getEntity()).ifPresent(instance -> serverTick(instance, event.getEntity()));
        }
    }

    private static void serverTick(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        CompoundTag tag = instance.getOrCreateTag();
        if (!tag.contains(FEATHERS_TAG)) {
            setFeathers(instance, getMaxFeathers(instance));
        }

        long now = level.getGameTime();
        long elapsed = tag.contains(LAST_TICK_TAG) ? Mth.clamp(now - tag.getLong(LAST_TICK_TAG), 0L, MAX_ELAPSED) : 0L;
        tag.putLong(LAST_TICK_TAG, now);

        int feathers = getFeathers(instance);
        int max = getMaxFeathers(instance);
        if (feathers > max) {
            setFeathers(instance, feathers);
        } else if (feathers < max) {
            long regrow = tag.getInt(REGROW_TAG) + elapsed;
            int interval = Math.max(1, CONFIG.regrowTicks);
            if (regrow >= interval) {
                setFeathers(instance, feathers + (int) (regrow / interval));
                regrow %= interval;
            }
            tag.putInt(REGROW_TAG, (int) regrow);
        } else {
            tag.remove(REGROW_TAG);
        }

        long launchAt = tag.getLong(LAUNCH_TAG);
        if (launchAt > 0L && entity instanceof Player player) {
            if (now >= launchAt && !player.onGround() && canWingFly(player)) {
                player.startFallFlying();
                tag.remove(LAUNCH_TAG);
            } else if (now > launchAt + LAUNCH_WINDOW) {
                tag.remove(LAUNCH_TAG);
            }
        }

        if (entity.isFallFlying() && entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case TAKE_FLIGHT -> takeFlight(level, instance, entity);
            case FEATHER_CANNON -> featherCannon(level, instance, entity);
            case FEATHER_BLADES -> featherBlade(instance, entity);
            case FEATHER_BOOST -> featherBoost(level, instance, entity);
        }
    }

    private static void takeFlight(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (getFeathers(instance) <= CONFIG.minFlightFeathers) {
            fail(player, Component.translatable("tracadamia.skill.fierce_wings.too_few_to_fly", getFeathers(instance)));
            return;
        }

        Vec3 look = player.getLookAngle();
        if (player.isFallFlying()) {
            if (!spend(player, instance, CONFIG.boostFeatherCost, CONFIG.minBoostFeathers)) {
                return;
            }

            double fill = getFeathers(instance) / (double) Math.max(1, CONFIG.feathersMastered);
            double power = CONFIG.minBoost + (CONFIG.maxBoost - CONFIG.minBoost) * fill;
            player.setDeltaMovement(player.getDeltaMovement().add(look.scale(power)));
            player.hurtMarked = true;
            featherBurst(level, player, 10);
            QuirkAnimPayload.play(player, QuirkAnimPayload.WINGS_BOOST);
            playSound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.4F);
            instance.addMasteryPoint(player);
            showFeathers(player, instance);
            return;
        }

        if (player.onGround()) {
            player.setDeltaMovement(look.x * 0.4D, 0.9D, look.z * 0.4D);
            player.hurtMarked = true;
            instance.getOrCreateTag().putLong(LAUNCH_TAG, level.getGameTime() + LAUNCH_DELAY);
        } else {
            player.startFallFlying();
        }

        featherBurst(level, player, 6);
        QuirkAnimPayload.play(player, QuirkAnimPayload.WINGS_LAUNCH);
        playSound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.2F);
        instance.addMasteryPoint(player);
    }

    private static void featherCannon(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!spend(entity, instance, CONFIG.cannonFeathers, CONFIG.cannonFeathers - 1)) {
            return;
        }

        double share = masteryShare(instance);
        double damage = share >= 1.0D ? CONFIG.cannonDamageMastered : share >= 0.5D ? CONFIG.cannonDamageHalfMastered : CONFIG.cannonDamage;
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.cannonRange);
        VOLLEYS.add(new Volley(entity, target, (float) damage, CONFIG.cannonFeathers));
        QuirkAnimPayload.play(entity, QuirkAnimPayload.WINGS_CANNON);
        playSound(entity, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 0.9F);
        instance.addMasteryPoint(entity);
        showFeathers(entity, instance);
    }

    private static void fireVolley(Volley volley) {
        LivingEntity owner = volley.owner;
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive()) {
            volley.remaining = 0;
            return;
        }

        Vec3 look = owner.getLookAngle();
        double yaw = Math.toRadians(owner.yBodyRot);
        Vec3 back = new Vec3(Math.sin(yaw), 0.0D, -Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        for (int i = 0; i < VOLLEY_PER_TICK && volley.remaining > 0; i++) {
            int side = volley.fired % 2 == 0 ? 1 : -1;
            double spread = 0.35D + (volley.fired / 2 % 4) * 0.2D;
            Vec3 from = owner.position().add(0.0D, owner.getBbHeight() * (0.6D + level.random.nextDouble() * 0.3D), 0.0D)
                    .add(back.scale(0.35D)).add(right.scale(side * spread));
            Vec3 aim = volley.target != null && volley.target.isAlive() ? volley.target.getBoundingBox().getCenter().subtract(from).normalize() : look;
            Vec3 launch = aim.add(right.scale(side * 0.35D)).add(0.0D, 0.25D, 0.0D).normalize().scale(1.4D);
            FeatherProjectile feather = new FeatherProjectile(level, owner, volley.damage, volley.target, wingColor(owner));
            feather.setPos(from.x, from.y, from.z);
            feather.setDeltaMovement(launch);
            level.addFreshEntity(feather);
            volley.fired++;
            volley.remaining--;
        }

        playSound(owner, SoundEvents.ARROW_SHOOT, 0.6F, 1.6F + level.random.nextFloat() * 0.3F);
    }

    public static void featherHit(LivingEntity owner, LivingEntity target, float damage) {
        DamageSource source = getFierceWings(owner)
                .map(instance -> ((Skill) instance.getSkill()).createSource(instance, owner, DamageTypes.MOB_PROJECTILE, FEATHER_CANNON))
                .orElseGet(() -> owner.damageSources().mobProjectile(owner, owner));
        target.invulnerableTime = 0;
        target.hurt(source, damage);
    }

    private static void featherBlade(ManasSkillInstance instance, LivingEntity entity) {
        if (!(entity instanceof Player player) || !spend(player, instance, CONFIG.bladeFeatherCost, CONFIG.bladeFeatherCost - 1)) {
            return;
        }

        ItemStack blade = new ItemStack(FierceWingsItems.FEATHER_BLADE.get());
        blade.set(DataComponents.DYED_COLOR, new DyedItemColor(wingColor(player), false));
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        } else if (!player.getInventory().add(blade)) {
            player.drop(blade, false);
        }

        QuirkAnimPayload.play(player, QuirkAnimPayload.WINGS_BLADE);
        playSound(player, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 1.0F, 1.3F);
        instance.addMasteryPoint(player);
        showFeathers(player, instance);
    }

    private static void featherBoost(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = entity;
        if (entity.isShiftKeyDown()) {
            target = MultiArms.getTarget(entity, CONFIG.boostRange);
            if (target == null) {
                fail(entity, Component.translatable("tensura.targeting.not_targeted"));
                return;
            }
        }

        if (!spend(entity, instance, CONFIG.boostFeathers, CONFIG.boostFeathers - 1)) {
            return;
        }

        BOOSTS.put(target.getUUID(), new Boost(target, level.getGameTime() + CONFIG.boostTicks));
        applyBoost(target, true);
        featherBurst(level, target, 12);
        QuirkAnimPayload.play(entity, QuirkAnimPayload.WINGS_FEATHER_BOOST);
        playSound(target, SoundEvents.ENDER_DRAGON_FLAP, 0.8F, 1.7F);
        instance.addMasteryPoint(entity);
        showFeathers(entity, instance);
    }

    private static void applyBoost(LivingEntity entity, boolean on) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_SPEED);
        AttributeInstance move = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attack != null) {
            attack.removeModifier(BOOST_ATTACK);
            if (on) {
                attack.addTransientModifier(new AttributeModifier(BOOST_ATTACK, CONFIG.boostAttackSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (move != null) {
            move.removeModifier(BOOST_MOVE);
            if (on) {
                move.addTransientModifier(new AttributeModifier(BOOST_MOVE, CONFIG.boostMovementSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        if (!(owner.level() instanceof ServerLevel level) || source.getEntity() == null || source.getEntity() == owner) {
            return true;
        }

        if (!source.is(DamageTypeTags.IS_FIRE) && !TensuraDamageHelper.isFireDamage(source)) {
            return true;
        }

        long now = level.getGameTime();
        Long last = LAST_SCORCH.get(owner.getUUID());
        if (last != null && now - last < FLAME_COOLDOWN) {
            return true;
        }
        LAST_SCORCH.put(owner.getUUID(), now);

        SkillStorage skills = SkillAPI.getSkillsFrom(owner);
        double chance = skills.getSkill(ResistanceSkills.FLAME_ATTACK_NULLIFICATION.get()).isPresent() ? CONFIG.flameNullificationChance
                : skills.getSkill(ResistanceSkills.FLAME_ATTACK_RESISTANCE.get()).isPresent() ? CONFIG.flameResistanceChance : CONFIG.flameChance;
        if (owner.getRandom().nextDouble() >= chance) {
            return true;
        }

        int before = getFeathers(instance);
        setFeathers(instance, before - CONFIG.flameFeatherLoss);
        level.sendParticles(ParticleTypes.FLAME, owner.getX(), owner.getY(0.7D), owner.getZ(), 10, 0.4D, 0.3D, 0.4D, 0.02D);
        level.sendParticles(ParticleTypes.SMOKE, owner.getX(), owner.getY(0.7D), owner.getZ(), 8, 0.4D, 0.3D, 0.4D, 0.02D);
        if (owner instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.fierce_wings.scorched", before - getFeathers(instance), getFeathers(instance))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return true;
    }

    private static void featherBurst(ServerLevel level, LivingEntity entity, int count) {
        int rgb = wingColor(entity);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F), 1.2F);
        level.sendParticles(dust, entity.getX(), entity.getY(0.6D), entity.getZ(), count, 0.5D, 0.4D, 0.5D, 0.05D);
    }

    public static int wingColor(LivingEntity entity) {
        CosmeticDef def = RaceCosmeticRegistry.INSTANCE.get(WINGS_COSMETIC);
        int option = def == null ? -1 : def.optionIndex("color");
        if (option < 0) {
            return DEFAULT_COLOR;
        }

        int value = RaceCosmetics.values(entity, def)[option];
        if (CosmeticColor.custom(value)) {
            return CosmeticColor.rgb(value);
        }
        int[] colors = def.options().get(option).colors();
        return colors == null || colors.length == 0 ? DEFAULT_COLOR : colors[Math.floorMod(value, colors.length)];
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!VOLLEYS.isEmpty()) {
            for (Volley volley : List.copyOf(VOLLEYS)) {
                fireVolley(volley);
            }
            VOLLEYS.removeIf(volley -> volley.remaining <= 0);
        }

        if (!BOOSTS.isEmpty()) {
            long now = event.getServer().overworld().getGameTime();
            Iterator<Boost> boosts = BOOSTS.values().iterator();
            while (boosts.hasNext()) {
                Boost boost = boosts.next();
                if (now >= boost.until() || !boost.entity().isAlive() || boost.entity().isRemoved()) {
                    applyBoost(boost.entity(), false);
                    boosts.remove();
                } else if (now % 5 == 0 && boost.entity().level() instanceof ServerLevel level) {
                    featherBurst(level, boost.entity(), 2);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        VOLLEYS.clear();
        BOOSTS.clear();
        LAST_SCORCH.clear();
    }

    private static void playSound(LivingEntity entity, SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

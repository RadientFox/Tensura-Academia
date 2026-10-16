package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.VineConstruct;
import com.radient.tensuraacadamia.regestry.VinesEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VinesQuirk extends Skill {
    public static final double TARGET_RANGE = 20, VINE_RANGE = 32, VOLLEY_RANGE = 50;
    public static final int SHIELD_PANELS = 5, VOLLEY_SIZE = 20;
    private static final String READY = "VinesReady", COOLDOWN_ACTIVE = "VinesCooldownActive";

    public VinesQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/vineicon.png"); }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public int getModes(ManasSkillInstance instance) {
        return 1 + Math.min(3, (int) (instance.getMastery() * 4 / getMaxMastery()));
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return "vines." + switch (mode) {
            case 1 -> "faiths_shield";
            case 2 -> "crucifixion";
            case 3 -> "via_dolorosa";
            default -> "vine_snare";
        };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return switch (mode) { case 0 -> 100; case 1 -> 1000; case 2 -> 10000; case 3 -> 50000; default -> 0; };
    }
    public int cooldownSeconds(ManasSkillInstance instance, int mode) {
        boolean mastered = instance.getMastery() >= getMaxMastery();
        return switch (mode) { case 0 -> mastered ? 3 : 5; case 1 -> mastered ? 10 : 20; default -> mastered ? 40 : 60; };
    }
    public float sunlightBonus(LivingEntity owner, ManasSkillInstance instance) {
        return owner.level().isDay() && !owner.level().isRainingAt(owner.blockPosition())
                && owner.level().getHeight(Heightmap.Types.MOTION_BLOCKING, owner.getBlockX(), owner.getBlockZ()) <= owner.getEyeY()
                ? instance.getMastery() >= getMaxMastery() ? 1.5F : 1.25F : 1;
    }
    public float shieldHealth(ManasSkillInstance instance) {
        return 200 + 400 * Math.clamp((float) instance.getMastery() / getMaxMastery(), 0, 1);
    }

    private static ManasSkillInstance resistanceInstance() {
        var instance = new TensuraSkillInstance(ResistanceSkills.ELECTRICITY_RESISTANCE.get());
        instance.setMastery(0);
        instance.setToggled(true);
        return instance;
    }
    private static boolean alreadyResistant(LivingEntity owner) {
        return SkillUtils.isSkillToggled(owner, ResistanceSkills.ELECTRICITY_RESISTANCE.get())
                || SkillUtils.isSkillToggled(owner, ResistanceSkills.ELECTRICITY_NULLIFICATION.get());
    }
    @Override public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        return alreadyResistant(owner) || ResistanceSkills.ELECTRICITY_RESISTANCE.get()
                .onBeingDamaged(resistanceInstance(), owner, source, amount);
    }
    @Override public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        return alreadyResistant(owner) || ResistanceSkills.ELECTRICITY_RESISTANCE.get()
                .onTakenDamage(resistanceInstance(), owner, source, amount);
    }
    @Override public boolean onEffectAdded(ManasSkillInstance instance, LivingEntity owner, Entity source, Changeable<MobEffectInstance> effect) {
        return alreadyResistant(owner) || ResistanceSkills.ELECTRICITY_RESISTANCE.get()
                .onEffectAdded(resistanceInstance(), owner, source, effect);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode >= getModes(instance)
                || instance.onCoolDown(mode) || instance.getOrCreateTag().getLong(READY + mode) > level.getGameTime()) return;
        var existence = TensuraStorages.getExistenceFrom(owner);
        double cost = getAuraCost(owner, instance, mode);
        if (existence.getAura() < cost) { message(owner, "need_aura"); return; }
        float bonus = sunlightBonus(owner, instance);
        boolean mastered = instance.getMastery() >= getMaxMastery();
        LivingEntity target = null;
        if (mode == 2) {
            Vec3 start = owner.getEyePosition(), end = start.add(owner.getLookAngle().scale(TARGET_RANGE * bonus));
            var hit = ProjectileUtil.getEntityHitResult(owner, start, end, new AABB(start, end).inflate(1),
                    entity -> entity instanceof LivingEntity living && validTarget(owner, living), Math.pow(TARGET_RANGE * bonus, 2));
            if (hit == null || !owner.hasLineOfSight(hit.getEntity())) { message(owner, "no_target"); return; }
            target = (LivingEntity) hit.getEntity();
        }
        List<VineConstruct> vines = new ArrayList<>();
        List<LivingEntity> volleyTargets = mode == 3 ? nearbyTargets(owner) : List.of();
        int count = mode == 1 ? SHIELD_PANELS : mode == 3 ? VOLLEY_SIZE : 1;
        for (int i = 0; i < count; i++) {
            VineConstruct vine = VinesEntities.VINE.get().create(level);
            if (vine == null) return;
            vine.configure(owner, mode, mastered, bonus, mode == 1 ? shieldHealth(instance) * bonus : (mastered ? 40 : 20) * bonus);
            if (mode == 1) {
                double angle = Math.toRadians(owner.getYRot() - 60 + i * 30);
                Vec3 normal = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
                vine.configurePanel(owner.position().add(normal.scale(3 * bonus)), normal,
                        i == 0 ? vine.getUUID() : vines.getFirst().getUUID());
            } else if (mode == 2) vine.bind(target);
            else {
                Vec3 direction = owner.getLookAngle();
                if (mode == 3 && !volleyTargets.isEmpty()) vine.seek(volleyTargets.get(i % volleyTargets.size()));
                else vine.setDeltaMovement(direction.scale(1.2));
            }
            vines.add(vine);
        }
        if (vines.stream().anyMatch(vine -> !level.hasChunkAt(vine.blockPosition()))) {
            vines.forEach(VineConstruct::discard);
            return;
        }
        for (VineConstruct vine : vines) if (!level.addFreshEntity(vine)) {
            vines.forEach(VineConstruct::discard);
            return;
        }
        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        var cooldowns = new ArrayList<>(instance.getCooldownList());
        while (cooldowns.size() < 4) cooldowns.add(0);
        cooldowns.set(mode, cooldownSeconds(instance, mode));
        instance.setCoolDownList(cooldowns);
        instance.getOrCreateTag().putLong(READY + mode, level.getGameTime() + cooldownSeconds(instance, mode) * 20L);
        owner.getPersistentData().putBoolean(COOLDOWN_ACTIVE, true);
        instance.addMasteryPoint(owner);
        instance.markDirty();
        level.playSound(null, owner.blockPosition(), SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 1, 0.65F);
    }
    public static boolean validTarget(LivingEntity owner, LivingEntity target) {
        return !(target instanceof VineConstruct) && !(target instanceof com.radient.tensuraacadamia.entity.SolidAirWall)
                && HomingQuirk.isEnemy(owner, target) && VineConstruct.binding(target) == null;
    }
    public static List<LivingEntity> nearbyTargets(LivingEntity owner) {
        return owner.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(VOLLEY_RANGE),
                        target -> validTarget(owner, target) && owner.distanceToSqr(target) <= VOLLEY_RANGE * VOLLEY_RANGE
                                && owner.hasLineOfSight(target))
                .stream().sorted(Comparator.comparingDouble(owner::distanceToSqr)).toList();
    }
    private static void message(LivingEntity owner, String key) {
        if (owner instanceof Player player) player.displayClientMessage(Component.translatable("tracadamia.skill.vines." + key), true);
    }
    public static void registerSkillEvents() {
        SkillEvents.ACTIVATE_SKILL.register((change, owner, key, mode) -> VineConstruct.crucified(owner) ? EventResult.interruptFalse() : EventResult.pass());
        SkillEvents.TOGGLE_SKILL.register((change, owner) -> VineConstruct.crucified(owner) ? EventResult.interruptFalse() : EventResult.pass());
    }
    @SubscribeEvent public static void blockMelee(AttackEntityEvent event) {
        if (VineConstruct.crucified(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && VineConstruct.crucified(attacker)) {
            event.setCanceled(true);
            return;
        }
        if (VineConstruct.intercept(event.getEntity(), event.getSource(), event.getAmount())) {
            event.setCanceled(true);
            return;
        }
        if ((event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(TensuraDamageTypes.HEAT_WAVE)
                || ((TensuraDamageSource) event.getSource()).tensura$getElement() == Element.FLAME)
                && SkillAPI.getSkillsFrom(event.getEntity()).getSkill(QuirkSkills.VINES.get()).isPresent())
            event.setAmount(event.getAmount() * 1.5F);
    }
    @SubscribeEvent public static void onCooldownTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || owner.level().isClientSide
                || !owner.getPersistentData().getBoolean(COOLDOWN_ACTIVE)) return;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.VINES.get()).orElse(null);
        boolean active = false;
        if (instance != null) for (int mode = 0; mode < 4; mode++) {
            long deadline = instance.getOrCreateTag().getLong(READY + mode);
            if (deadline == 0) continue;
            long remaining = Math.max(0, deadline - owner.level().getGameTime());
            int seconds = (int) ((remaining + 19) / 20);
            if (instance.getCoolDown(mode) != seconds) instance.setCoolDown(seconds, mode);
            if (remaining == 0) { instance.getOrCreateTag().remove(READY + mode); instance.markDirty(); }
            else active = true;
        }
        if (!active) owner.getPersistentData().remove(COOLDOWN_ACTIVE);
    }
}

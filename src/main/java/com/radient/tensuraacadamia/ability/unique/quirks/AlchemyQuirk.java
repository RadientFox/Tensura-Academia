package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.AlchemyCoin;
import com.radient.tensuraacadamia.entity.MoltenShield;
import com.radient.tensuraacadamia.effects.IronLockEffect;
import com.radient.tensuraacadamia.regestry.AlchemyEntities;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.TickingSkill;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.joml.Vector3f;

import java.util.List;

public final class AlchemyQuirk extends Skill {
    public static final int MAX_POINTS = 1000, CHARGE_TICKS = 200, SMASH_COST = 50, LOCK_COST = 5;
    public static final int MIN_WALL_SIZE = 3, MAX_WALL_SIZE = 15, WALL_LIFETIME = 1200;
    public static final int DASH_TICKS = 10;
    public static final double DASH_SPEED = 2;
    private static final double PUNCH_REACH = 0.25;
    public static final String POINTS = "AlchemyPoints", FORM = "AlchemyForm", CHARGE = "AlchemyChargeStart";
    private static final String REGEN = "AlchemyRegenAt", SIZE = "AlchemyWallSize", DASH = "AlchemyDashUntil";
    private static final ResourceLocation FORM_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "alchemy_form");
    private static final ResourceLocation SLOW_MODIFIER = ResourceLocation.fromNamespaceAndPath("tracadamia", "alchemy_charge_slow");
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1, 0.7F, 0.04F), 1.2F);

    public AlchemyQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/alchemyicon.png"); }
    @Override public int getModes(ManasSkillInstance instance) { return 4; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 4);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return "alchemy." + switch (mode) { case 1 -> "hero_mimicry"; case 2 -> "bologna_smash"; case 3 -> "iron_lock"; default -> "molten_shield"; };
    }
    @Override public List<Integer> getModeLearningList(ManasSkillInstance instance) { return List.of(3); }
    @Override public boolean canScroll(ManasSkillInstance instance, LivingEntity owner, int mode) { return mode == 0; }
    @Override public void onScroll(ManasSkillInstance instance, LivingEntity owner, double scroll, int mode) {
        if (owner.level().isClientSide || mode != 0 || scroll == 0) return;
        instance.getOrCreateTag().putInt(SIZE, Math.clamp(wallSize(instance) + (scroll > 0 ? 2 : -2), MIN_WALL_SIZE, MAX_WALL_SIZE));
        instance.markDirty();
        message(owner, "wall_size", wallSize(instance), wallSize(instance), wallCost(instance));
    }
    public static int points(ManasSkillInstance instance) { return Math.clamp(instance.getOrCreateTag().getInt(POINTS), 0, MAX_POINTS); }
    public static int wallSize(ManasSkillInstance instance) {
        int size = instance.getOrCreateTag().getInt(SIZE);
        return size == 0 ? 5 : Math.clamp(size, MIN_WALL_SIZE, MAX_WALL_SIZE);
    }
    public static int wallCost(ManasSkillInstance instance) { return wallSize(instance) * wallSize(instance); }
    public static int formDrain(int form, boolean mastered) {
        return switch (form) { case 1 -> mastered ? 0 : 1; case 2 -> mastered ? 10 : 15; case 3 -> mastered ? 30 : 50; default -> 0; };
    }
    public static double damageReduction(int form) { return switch (form) { case 1 -> 0.1; case 2 -> 0.3; case 3 -> 0.5; default -> 0; }; }
    public static float smashDamage(double meleeDamage, double charge) {
        return (float) (Math.min(250, Math.max(0, meleeDamage) * 25) * Math.clamp(charge, 0, 1));
    }
    public static ManasSkillInstance instance(LivingEntity owner) { return SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ALCHEMY.get()).orElse(null); }
    private boolean slotted(LivingEntity owner) { return TensuraStorages.getAbilityFrom(owner).isAbilityInActivePreset(this); }
    public static boolean spend(ManasSkillInstance instance, LivingEntity owner, int cost) {
        if (points(instance) < cost) { message(owner, "not_enough_points", cost); return false; }
        instance.getOrCreateTag().putInt(POINTS, points(instance) - cost); instance.markDirty(); return true;
    }
    private void tickState(ManasSkillInstance instance, LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        var tag = instance.getOrCreateTag();
        if (!owner.isAlive() || !slotted(owner)) { clearActive(instance, owner); tag.remove(REGEN); return; }
        if (tag.contains(CHARGE) && !TickingSkill.isTickingSkill(owner, this, 2)) clearCharge(instance, owner);
        int form = tag.getInt(FORM);
        var scale = owner.getAttribute(Attributes.SCALE);
        if (form != 0 && scale != null && scale.getModifier(FORM_MODIFIER) == null) applyForm(instance, owner, form);
        long now = level.getGameTime();
        if (!tag.contains(REGEN) || tag.getLong(REGEN) > now + 20) tag.putLong(REGEN, now + 20);
        if (now >= tag.getLong(REGEN)) {
            boolean mastered = instance.getMastery() >= getMaxMastery();
            int regenerated = Math.min(MAX_POINTS, points(instance) + (mastered ? 10 : 5));
            int drain = formDrain(form, mastered);
            tag.putInt(POINTS, regenerated);
            if (regenerated < drain) { applyForm(instance, owner, 0); message(owner, "form_ended"); }
            else tag.putInt(POINTS, regenerated - drain);
            tag.putLong(REGEN, now + 20); instance.markDirty();
            if (!tag.contains(CHARGE)) message(owner, "points", points(instance), MAX_POINTS);
        }
        if (tag.contains(DASH)) tickDash(instance, owner);
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || owner.level().isClientSide) return;
        var instance = instance(owner);
        if (instance != null) QuirkSkills.ALCHEMY.get().tickState(instance, owner);
    }
    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode >= 4 || !owner.isAlive() || !slotted(owner)) return;
        var tag = instance.getOrCreateTag();
        if (tag.contains(CHARGE) || tag.contains(DASH)) return;
        switch (mode) {
            case 0 -> {
                if (points(instance) < wallCost(instance)) { message(owner, "not_enough_points", wallCost(instance)); return; }
                MoltenShield wall = AlchemyEntities.SHIELD.get().create(level);
                if (wall == null) return;
                Vec3 look = owner.getLookAngle();
                Direction normal = Direction.getNearest(look.x, look.y, look.z);
                Vec3 center = owner.getEyePosition().add(Vec3.atLowerCornerOf(normal.getNormal()).scale(3));
                if (normal.getAxis() != Direction.Axis.Y) center = new Vec3(center.x, owner.getY() + wallSize(instance) / 2.0, center.z);
                wall.configure(owner, center, normal, wallSize(instance), instance.getMastery() >= getMaxMastery());
                if (!level.hasChunkAt(wall.blockPosition()) || !level.addFreshEntity(wall)) return;
                spend(instance, owner, wallCost(instance));
                level.playSound(null, wall.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.3F);
            }
            case 1 -> {
                int next = Math.floorMod(tag.getInt(FORM) + 1, 4);
                if (points(instance) < formDrain(next, instance.getMastery() >= getMaxMastery())) { message(owner, "not_enough_points", formDrain(next, instance.getMastery() >= getMaxMastery())); return; }
                applyForm(instance, owner, next);
                message(owner, "form." + next);
                level.playSound(null, owner.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.9F, 0.8F);
            }
            case 2 -> {
                if (!spend(instance, owner, SMASH_COST)) return;
                tag.putLong(CHARGE, level.getGameTime()); instance.markDirty();
                return;
            }
            case 3 -> {
                if (instance.onCoolDown(mode)) return;
                if (learnMode(instance, owner, mode)) { instance.markDirty(); return; }
                if (points(instance) < LOCK_COST) { message(owner, "not_enough_points", LOCK_COST); return; }
                AlchemyCoin coin = AlchemyEntities.COIN.get().create(level);
                if (coin == null) return;
                coin.configure(owner, instance.getMastery() >= getMaxMastery());
                if (!level.addFreshEntity(coin)) return;
                spend(instance, owner, LOCK_COST);
                level.playSound(null, owner.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 1.5F);
            }
        }
        instance.addMasteryPoint(owner); instance.markDirty();
    }
    @Override public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner, int key, int mode) { return true; }
    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (owner.level().isClientSide) return mode == 2;
        var tag = instance.getOrCreateTag();
        if (mode != 2 || !tag.contains(CHARGE)) return false;
        if (!owner.isAlive() || !slotted(owner)) { clearCharge(instance, owner); return false; }
        long elapsed = Math.max(0, owner.level().getGameTime() - tag.getLong(CHARGE));
        double charge = Math.clamp(elapsed / (double) CHARGE_TICKS, 0, 1);
        var speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_MODIFIER, -0.75 * charge, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (heldTicks % 5 == 0) {
            Vec3 hand = owner.getEyePosition().add(owner.getLookAngle().scale(0.8)).add(0, -0.35, 0);
            ((ServerLevel) owner.level()).sendParticles(GOLD, hand.x, hand.y, hand.z, 3 + (int) (charge * 7), 0.12, 0.12, 0.12, 0);
            message(owner, "charging", String.format(java.util.Locale.ROOT, "%.1f", charge * 10));
        }
        if (elapsed >= CHARGE_TICKS) { launchSmash(instance, owner); return false; }
        return true;
    }
    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) {
        if (owner.level().isClientSide || mode != 2 || !instance.getOrCreateTag().contains(CHARGE)) return;
        if (!owner.isAlive() || !slotted(owner) || !instance.canInteractSkill(owner)) clearCharge(instance, owner);
        else launchSmash(instance, owner);
    }
    private void launchSmash(ManasSkillInstance instance, LivingEntity owner) {
        var tag = instance.getOrCreateTag();
        double charge = Math.clamp((owner.level().getGameTime() - tag.getLong(CHARGE)) / (double) CHARGE_TICKS, 0, 1);
        clearCharge(instance, owner);
        if (charge <= 0) return;
        Vec3 direction = owner.getLookAngle().normalize();
        tag.putLong(DASH, owner.level().getGameTime() + DASH_TICKS);
        tag.putDouble("AlchemyDashX", direction.x); tag.putDouble("AlchemyDashY", direction.y); tag.putDouble("AlchemyDashZ", direction.z);
        tag.putDouble("AlchemySmashCharge", charge);
        instance.markDirty(); instance.addMasteryPoint(owner);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1, 0.6F);
    }
    private void tickDash(ManasSkillInstance instance, LivingEntity owner) {
        var tag = instance.getOrCreateTag();
        if (owner.level().getGameTime() >= tag.getLong(DASH) || !instance.canInteractSkill(owner)) { clearDash(instance, owner); return; }
        Vec3 direction = new Vec3(tag.getDouble("AlchemyDashX"), tag.getDouble("AlchemyDashY"), tag.getDouble("AlchemyDashZ"));
        AABB body = owner.getBoundingBox();
        Vec3 start = body.getCenter();
        owner.move(MoverType.SELF, direction.scale(DASH_SPEED));
        Vec3 end = owner.getBoundingBox().getCenter();
        if (owner instanceof ServerPlayer player) player.connection.teleport(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
        owner.setDeltaMovement(Vec3.ZERO); owner.hurtMarked = true; owner.fallDistance = 0;
        LivingEntity target = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (LivingEntity candidate : owner.level().getEntitiesOfClass(LivingEntity.class,
                body.expandTowards(end.subtract(start)).inflate(PUNCH_REACH),
                enemy -> !(enemy instanceof MoltenShield) && enemy.isAttackable() && HomingQuirk.isEnemy(owner, enemy))) {
            AABB contact = candidate.getBoundingBox().inflate(body.getXsize() / 2 + PUNCH_REACH,
                    body.getYsize() / 2 + PUNCH_REACH, body.getZsize() / 2 + PUNCH_REACH);
            Vec3 impact = contact.contains(start) ? start : contact.clip(start, end).orElse(null);
            if (impact == null) continue;
            double distance = impact.distanceToSqr(start);
            if (distance < nearest) { target = candidate; nearest = distance; }
        }
        if (target != null) {
            owner.swing(InteractionHand.MAIN_HAND, true);
            var source = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);
            var typed = (TensuraDamageSource) source;
            typed.tensura$setAbilityInstance(instance); typed.tensura$setAbilityMode(2); typed.tensura$setSkillType(SkillType.UNIQUE);
            double charge = tag.getDouble("AlchemySmashCharge");
            target.invulnerableTime = 0;
            if (target.hurt(source, smashDamage(owner.getAttributeValue(Attributes.ATTACK_DAMAGE), charge))) {
                target.setDeltaMovement(direction.scale(1 + charge * 2).add(0, 0.3 + charge * 0.6, 0)); target.hurtMarked = true;
            }
            ((ServerLevel) owner.level()).sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 1, 0, 0, 0, 0);
            owner.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5F, 0.5F);
            clearDash(instance, owner);
        } else if (start.distanceToSqr(end) < 0.01) clearDash(instance, owner);
    }
    private static void clearCharge(ManasSkillInstance instance, LivingEntity owner) {
        instance.getOrCreateTag().remove(CHARGE);
        var speed = owner.getAttribute(Attributes.MOVEMENT_SPEED); if (speed != null) speed.removeModifier(SLOW_MODIFIER);
        instance.markDirty();
    }
    private static void clearDash(ManasSkillInstance instance, LivingEntity owner) {
        var tag = instance.getOrCreateTag();
        for (String key : List.of(DASH, "AlchemyDashX", "AlchemyDashY", "AlchemyDashZ", "AlchemySmashCharge")) tag.remove(key);
        owner.setDeltaMovement(Vec3.ZERO); instance.markDirty();
    }
    private static void applyForm(ManasSkillInstance instance, LivingEntity owner, int form) {
        float ratio = owner.getMaxHealth() > 0 ? owner.getHealth() / owner.getMaxHealth() : 1;
        for (var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var value = owner.getAttribute(attribute); if (value == null) continue;
            value.removeModifier(FORM_MODIFIER);
            if (form == 0 || attribute.equals(Attributes.SCALE) || attribute.equals(Attributes.GRAVITY)
                    || attribute.equals(TensuraAttributes.WIDTH_MULTIPLIER) || attribute.equals(TensuraAttributes.HEIGHT_MULTIPLIER)) continue;
            double multiplier = form == 1 ? 0.25 : attribute.equals(Attributes.ATTACK_DAMAGE) || attribute.equals(Attributes.ARMOR)
                    || attribute.equals(Attributes.MOVEMENT_SPEED) || attribute.equals(Attributes.ENTITY_INTERACTION_RANGE) ? form - 1 : 0;
            if (multiplier != 0) value.addTransientModifier(new AttributeModifier(FORM_MODIFIER, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var scale = owner.getAttribute(Attributes.SCALE);
        if (scale != null && form != 0) scale.addTransientModifier(new AttributeModifier(FORM_MODIFIER, form == 1 ? 1 : form + 1, AttributeModifier.Operation.ADD_VALUE));
        owner.setHealth(Math.clamp(ratio, 0, 1) * owner.getMaxHealth());
        var energy = TensuraStorages.getExistenceFrom(owner);
        energy.setAura(Math.min(energy.getAura(), EnergyHelper.getMaxAura(owner)));
        energy.setMagicule(Math.min(energy.getMagicule(), EnergyHelper.getMaxMagicule(owner))); energy.markDirty();
        instance.getOrCreateTag().putInt(FORM, form); instance.markDirty();
    }
    private static void clearActive(ManasSkillInstance instance, LivingEntity owner) {
        if (instance.getOrCreateTag().getInt(FORM) != 0) applyForm(instance, owner, 0);
        if (instance.getOrCreateTag().contains(CHARGE)) clearCharge(instance, owner);
        if (instance.getOrCreateTag().contains(DASH)) clearDash(instance, owner);
    }
    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) { if (!owner.level().isClientSide) clearActive(instance, owner); }
    @Override public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) { clearActive(instance, owner); instance.getOrCreateTag().remove(REGEN); }
    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        var instance = instance(event.getEntity());
        if (instance != null) clearActive(instance, event.getEntity());
    }
    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        var instance = instance(event.getEntity()); if (instance != null) clearActive(instance, event.getEntity());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void protect(LivingIncomingDamageEvent event) {
        var victim = event.getEntity(); if (victim instanceof MoltenShield || victim.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var source = event.getSource();
        Entity attacker = source.getEntity();
        Vec3 from = source.getDirectEntity() == null ? source.getSourcePosition() : source.getDirectEntity().getBoundingBox().getCenter();
        if (from != null && MoltenShield.blocksDamage((ServerLevel) victim.level(), from, victim.getBoundingBox().getCenter(), attacker, source, event.getAmount())) { event.setCanceled(true); return; }
        var instance = instance(victim);
        if (instance != null) event.setAmount((float) (event.getAmount() * (1 - damageReduction(instance.getOrCreateTag().getInt(FORM)))));
    }
    @SubscribeEvent public static void projectiles(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Projectile projectile && projectile.level() instanceof ServerLevel level
                && MoltenShield.blocksProjectile(level, projectile)) { projectile.discard(); event.setCanceled(true); }
    }
    public static boolean ironLocked(Entity entity) { return entity instanceof LivingEntity target && target.hasEffect(MHAEffects.IRON_LOCK); }
    public static void lockTarget(LivingEntity target, boolean mastered) {
        var data = target.getPersistentData();
        data.putDouble("AlchemyLockX", target.getX()); data.putDouble("AlchemyLockY", target.getY()); data.putDouble("AlchemyLockZ", target.getZ());
        target.addEffect(new MobEffectInstance(MHAEffects.IRON_LOCK, mastered ? 80 : 40, 0, false, false, false));
        IronLockEffect.syncIndicator(target);
        target.setDeltaMovement(Vec3.ZERO); target.hurtMarked = true;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void lockRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().equals(MHAEffects.IRON_LOCK)) clearLockIndicator(event.getEntity());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void lockExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance().is(MHAEffects.IRON_LOCK)) clearLockIndicator(event.getEntity());
    }
    private static void clearLockIndicator(LivingEntity target) {
        if (target.level() instanceof ServerLevel level)
            level.getChunkSource().broadcast(target, new ClientboundRemoveMobEffectPacket(target.getId(), MHAEffects.IRON_LOCK));
    }
    private static void message(LivingEntity owner, String id, Object... args) {
        if (owner instanceof Player player) player.displayClientMessage(Component.translatable("tracadamia.skill.alchemy." + id, args), true);
    }
}

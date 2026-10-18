package com.radient.tensuraacadamia.quirks.acid;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.quirks.shared.QuirkCastCosts;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.entity.TensuraProjectile;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AcidQuirk extends Skill {
    public static final int MAX_STACKS = 30, ACID_TICKS = 200, CHARGE_STEP = 10;
    public static final int SHOT_INTERVAL_TICKS = 5;
    public static final float DAMAGE_PER_STACK = 3, CONTACT_DAMAGE = 10;
    public static final java.util.List<Integer> COSTS = java.util.List.of(100, 500, 1000, 5000, 10000);
    public static final java.util.List<Integer> COOLDOWNS = java.util.List.of(3, 8, 25, 60, 90);
    private static final String VOLLEY = "AcidVolley", NEXT = "AcidNextShot", START = "AlmaStart",
            STACKS = "AlmaStacks", HELD = "AlmaHeldUntil", SHOT_STACKS = "AcidShotStacks", SPEED = "AcidShotSpeed";
    public static final ResourceKey<DamageType> ACID_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("tracadamia", "acid"));

    public AcidQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/acidicon.png");
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.acid.description");
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        double mastery = instance.getMastery() / (double) getMaxMastery();
        return mastery >= 1 ? 5 : mastery >= .6 ? 4 : mastery >= .3 ? 3 : mastery >= .15 ? 2 : 1;
    }

    @Override
    public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "acid." + switch (mode) {
            case 1 -> "veil";
            case 2 -> "layback";
            case 3 -> "acidman";
            case 4 -> "alma";
            default -> "shot";
        };
    }

    @Override
    public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode < 0 || mode >= COSTS.size() || mode == 4 && instance.getOrCreateTag().contains(START) ? 0 : COSTS.get(mode);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity owner) {
        return instance.getMastery() >= 0;
    }

    @Override
    public void onLearnSkill(ManasSkillInstance instance, LivingEntity owner) {
        super.onLearnSkill(instance, owner);
        grantResistance(instance, owner);
    }

    private static void grantResistance(ManasSkillInstance instance, LivingEntity owner) {
        if (owner.level().isClientSide || instance.isTemporarySkill()
                || instance.getMastery() < 0 || SkillAPI.getSkillsFrom(owner).getSkill(ResistanceSkills.CORROSION_NULLIFICATION.get())
                .filter(resistance -> resistance.getMastery() >= 0).isPresent()
                || SkillAPI.getSkillsFrom(owner).getSkill(ResistanceSkills.CORROSION_RESISTANCE.get())
                .filter(resistance -> resistance.getMastery() >= 0).isPresent()) return;
        var resistance = new TensuraSkillInstance(ResistanceSkills.CORROSION_RESISTANCE.get());
        resistance.getOrCreateTag().putBoolean("NoMagiculeCost", true);
        SkillHelper.learnSkill(owner, resistance);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || instance.getMastery() < 0 || mode < 0 || mode >= getModes(instance)
                || instance.onCoolDown(mode) || mode == 4 && instance.getOrCreateTag().contains(START)
                || !QuirkCastCosts.hasAura(owner, COSTS.get(mode))) return;
        boolean mastered = instance.getMastery() >= getMaxMastery();
        var tag = instance.getOrCreateTag();
        if (mode == 0) {
            int stacks = shotStacks(instance.getMastery(), owner.hasEffect(MHAEffects.ACID_LAYBACK));
            double speed = owner.hasEffect(MHAEffects.ACID_LAYBACK) ? 3.6 : 1.8;
            if (!fire(owner, false, stacks, speed)) return;
            tag.putInt(VOLLEY, 2);
            tag.putLong(NEXT, level.getGameTime() + SHOT_INTERVAL_TICKS);
            tag.putInt(SHOT_STACKS, stacks);
            tag.putDouble(SPEED, speed);
            tag.putString("AcidCastDimension", level.dimension().location().toString());
        } else if (mode == 1) {
            var veil = new AcidProjectile(level, owner, AcidProjectile.VEIL, 0);
            veil.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(2)));
            veil.setFacing(owner.getLookAngle());
            if (!level.addFreshEntity(veil)) return;
        } else if (mode == 2) owner.addEffect(new MobEffectInstance(MHAEffects.ACID_LAYBACK, mastered ? 600 : 400));
        else if (mode == 3) owner.addEffect(new MobEffectInstance(MHAEffects.ACIDMAN, mastered ? 1200 : 600));
        else {
            tag.putLong(START, level.getGameTime());
            tag.putInt(STACKS, 0);
            tag.putLong(HELD, level.getGameTime() + 10);
            tag.putString("AcidCastDimension", level.dimension().location().toString());
        }
        QuirkCastCosts.spendAura(owner, COSTS.get(mode));
        if (mode != 4) QuirkCastCosts.cooldown(instance, mode, COOLDOWNS.get(mode), 5);
        instance.addMasteryPoint(owner);
        instance.markDirty();
        level.playSound(null, owner.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, .7F, mode == 4 ? .5F : 1.2F);
    }

    public static int shotStacks(double mastery, boolean sliding) {
        int stacks = 1 + (int) (Math.clamp(mastery, 0, 2500) / 500);
        return sliding ? (int) Math.ceil(stacks * 1.5) : stacks;
    }

    private static boolean fire(LivingEntity owner, boolean alma, int stacks, double speed) {
        var shot = new AcidProjectile(owner.level(), owner, alma ? AcidProjectile.ALMA : AcidProjectile.SHOT, stacks);
        var hand = AcidProjectile.handPosition(owner, 1, .8);
        var obstruction = owner.level().clip(new ClipContext(owner.getEyePosition(), hand, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (obstruction.getType() != HitResult.Type.MISS)
            hand = obstruction.getLocation().subtract(owner.getLookAngle().scale(.06));
        shot.setPos(hand);
        var eye = owner.getEyePosition();
        var aim = owner.level().clip(new ClipContext(eye, eye.add(owner.getLookAngle().scale(200)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        //aim
        var targetHit = ProjectileUtil.getEntityHitResult(owner.level(), owner, eye, aim.getLocation(), new AABB(eye, aim.getLocation()).inflate(1),
                entity -> entity instanceof LivingEntity target && HomingQuirk.isEnemy(owner, target), 0);
        var aimPoint = targetHit != null ? targetHit.getEntity().getBoundingBox().clip(eye, aim.getLocation()).orElse(targetHit.getLocation()) : aim.getLocation();
        var direction = aimPoint.subtract(hand).normalize();
        shot.setDeltaMovement((direction.lengthSqr() < 1e-8 ? owner.getLookAngle() : direction).scale(speed));
        shot.updateRotation();
        boolean spawned = owner.level().addFreshEntity(shot);
        if (spawned && owner.level() instanceof ServerLevel level) AcidProjectile.splash(level, hand, alma ? .5 : .2);
        return spawned;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity owner) {
        grantResistance(instance, owner);
    }

    @SubscribeEvent
    public static void activeTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || !(owner.level() instanceof ServerLevel)) return;
        SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ACID.get()).ifPresent(instance -> tickActive(instance, owner));
    }

    private static void tickActive(ManasSkillInstance instance, LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        var tag = instance.getOrCreateTag();
        if (tag.getInt(VOLLEY) == 0 && !tag.contains(START)) return;
        if (!owner.isAlive() || !instance.canInteractSkill(owner)
                || !tag.getString("AcidCastDimension").equals(level.dimension().location().toString())) {
            clear(instance);
            tag.remove(VOLLEY);
            instance.markDirty();
            return;
        }
        if (tag.getInt(VOLLEY) > 0 && level.getGameTime() > tag.getLong(NEXT) + 20) {
            tag.remove(VOLLEY);
            instance.markDirty();
        }
        if (tag.getInt(VOLLEY) > 0 && level.getGameTime() >= tag.getLong(NEXT)) {
            fire(owner, false, tag.getInt(SHOT_STACKS), tag.getDouble(SPEED));
            tag.putInt(VOLLEY, tag.getInt(VOLLEY) - 1);
            tag.putLong(NEXT, level.getGameTime() + SHOT_INTERVAL_TICKS);
            instance.markDirty();
        }
        // charge doesnt fire itself after major change in area
        if (tag.contains(START) && level.getGameTime() > tag.getLong(HELD)) clear(instance);
    }

    @Override
    public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        return mode == 4;
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (mode != 4) return false;
        if (owner.level().isClientSide) return true;
        var tag = instance.getOrCreateTag();
        if (!owner.isAlive() || !tag.contains(START) || instance.getMastery() < getMaxMastery()) {
            clear(instance);
            return false;
        }
        tag.putLong(HELD, owner.level().getGameTime() + 10);
        int stacks = Math.clamp((int) ((owner.level().getGameTime() - tag.getLong(START)) / CHARGE_STEP), 0, MAX_STACKS);
        if (stacks > tag.getInt(STACKS)) {
            tag.putInt(STACKS, stacks);

            int current = owner.hasEffect(MHAEffects.ACID) ? owner.getEffect(MHAEffects.ACID).getAmplifier() + 1 : 0;
            applyAcid(owner, owner, Math.max(0, stacks - current));
            instance.markDirty();
        }
        if (owner.level().getGameTime() >= tag.getLong("AlmaNextVisual")) {
            tag.putLong("AlmaNextVisual", owner.level().getGameTime() + 5);
            var hand = AcidProjectile.handPosition(owner, 1, 1.2);
            ((ServerLevel) owner.level()).sendParticles(MHAParticles.ACID_CHARGE.get(), hand.x, hand.y, hand.z, 0, .15 + stacks * .018, owner.getId(), 0, 1);
        }
        if (stacks >= MAX_STACKS) {
            release(instance, owner);
            return false;
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) {
        if (!owner.level().isClientSide && mode == 4) release(instance, owner);
    }

    private static void release(ManasSkillInstance instance, LivingEntity owner) {
        var tag = instance.getOrCreateTag();
        if (!tag.contains(START)) return;
        int stacks = tag.getInt(STACKS);
        clear(instance);
        QuirkCastCosts.cooldown(instance, 4, COOLDOWNS.get(4), 5);
        if (owner.isAlive() && stacks > 0) fire(owner, true, stacks, 2);
    }

    private static void clear(ManasSkillInstance instance) {
        var tag = instance.getOrCreateTag();
        tag.remove(START);
        tag.remove(STACKS);
        tag.remove(HELD);
        tag.remove("AlmaNextVisual");
        instance.markDirty();
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        clear(instance);
        instance.getOrCreateTag().remove(VOLLEY);
        owner.removeEffect(MHAEffects.ACIDMAN);
        owner.removeEffect(MHAEffects.ACID_LAYBACK);
    }

    public static void applyAcid(LivingEntity target, LivingEntity owner, int added) {
        if (target.level().isClientSide || !target.isAlive() || added <= 0) return;
        int old = target.hasEffect(MHAEffects.ACID) ? target.getEffect(MHAEffects.ACID).getAmplifier() + 1 : 0;
        target.addEffect(new MobEffectInstance(MHAEffects.ACID, ACID_TICKS, Math.min(MAX_STACKS, old + added) - 1), owner);
        target.getPersistentData().putUUID("AcidCreator", owner.getUUID());
    }

    public static DamageSource acidDamage(LivingEntity target) {
        var tag = target.getPersistentData();
        var creator = tag.hasUUID("AcidCreator") && target.level() instanceof ServerLevel server ? server.getEntity(tag.getUUID("AcidCreator")) : null;
        var source = new DamageSource(target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ACID_DAMAGE), creator);
        var nativeSource = (TensuraDamageSource) source;
        nativeSource.tensura$setSkillType(SkillType.UNIQUE);
        nativeSource.tensura$setResistanceBypassLevel(2);
        nativeSource.tensura$setBarrierBypassLevel(2);
        nativeSource.tensura$setDodgeBypass();
        return source;
    }

    @SubscribeEvent
    public static void coatAttacks(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && !event.getEntity().level().isClientSide && !event.getSource().is(ACID_DAMAGE)
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker.hasEffect(MHAEffects.ACIDMAN)
                && HomingQuirk.isEnemy(attacker, event.getEntity())) applyAcid(event.getEntity(), attacker, 1);
    }

    public static double projectileDamage(Projectile projectile) {
        if (projectile instanceof TensuraProjectile nativeShot)
            return nativeShot.getDamage() + nativeShot.getSecondaryDamage();
        if (projectile instanceof AbstractArrow arrow)
            return Math.ceil(arrow.getBaseDamage() * arrow.getDeltaMovement().length());
        return 5;
    }

    public static boolean dissolve(LivingEntity target, Projectile shot) {
        if (target.level().isClientSide || !target.hasEffect(MHAEffects.ACIDMAN) || shot.getOwner() == target
                || projectileDamage(shot) >= target.getMaxHealth() * .1) return false;
        AcidProjectile.splash((ServerLevel) target.level(), shot.position(), .3);
        shot.discard();
        return true;
    }

    @SubscribeEvent
    public static void projectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target
                && dissolve(target, event.getProjectile())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void projectileTravel(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Projectile shot) || !(shot.level() instanceof ServerLevel level)
                || shot instanceof AcidProjectile acid && acid.form() >= AcidProjectile.VEIL) return;
        var path = new AABB(shot.position(), shot.position().add(shot.getDeltaMovement())).inflate(3);
        // make sure projectiles cant go through acid wall
        for (var veil : level.getEntitiesOfClass(AcidProjectile.class, path, acid -> acid.form() == AcidProjectile.VEIL
                && acid.getOwner() != shot.getOwner()))
            if (veil.crossesVeil(shot.position(), shot.position().add(shot.getDeltaMovement()))) {
                AcidProjectile.splash(level, shot.position(), .3);
                shot.discard();
                return;
            }
    }

    public static void registerSkillEvents() {
        EntityEvents.PROJECTILE_HIT.register((hit, projectile, deflection, result) -> {
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target
                    && dissolve(target, projectile)) result.set(EntityEvents.ProjectileHitResult.PASS);
        });
    }
}

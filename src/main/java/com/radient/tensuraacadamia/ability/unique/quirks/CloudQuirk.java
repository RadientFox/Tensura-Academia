package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.QuirkCloud;
import com.radient.tensuraacadamia.regestry.CloudEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

public final class CloudQuirk extends Skill {
    public static final String DISMISS_VERSION = "TracadamiaCloudDismissVersion";
    private static final int MAX_MASTERY = 2500;
    private static final String PACIFIED_UNTIL = "TracadamiaCloudPacifiedUntil";
    private static final ResourceLocation MAGIC_SENSE = ResourceLocation.fromNamespaceAndPath("tensura", "magic_sense");

    public CloudQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return MAX_MASTERY; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/cloudicon.png");
    }
    @Override public int getModes(ManasSkillInstance instance) { return 3; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 1 -> "cloud.cloud_blind";
            case 2 -> "cloud.cumulonimbus";
            default -> "cloud.cloud";
        };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode == 0 && owner.isShiftKeyDown() ? 0 : mode == 1 ? 100 : mode == 2 ? 10000 : 50;
    }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }

    public static boolean mastered(LivingEntity owner) {
        return speedTier(owner) == 20;
    }

    public static int speedTier(LivingEntity owner) {
        return SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.CLOUD.get())
                .map(instance -> Math.clamp((int) (instance.getMastery() / (MAX_MASTERY / 20.0)), 0, 20))
                .orElse(0);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        if (mode == 0 && owner.isShiftKeyDown()) {
            owner.getPersistentData().putLong(DISMISS_VERSION, owner.getPersistentData().getLong(DISMISS_VERSION) + 1);
            for (ServerLevel world : level.getServer().getAllLevels()) {
                var clouds = new java.util.ArrayList<QuirkCloud>();
                for (var entity : world.getAllEntities())
                    if (entity instanceof QuirkCloud cloud && owner.getUUID().equals(cloud.creator())) clouds.add(cloud);
                clouds.forEach(QuirkCloud::discard);
            }
            return;
        }
        if (mode < 0 || mode > 2 || instance.onCoolDown(mode)) return;
        double cost = getAuraCost(owner, instance, mode);
        if (!QuirkCastCosts.hasAura(owner, cost)) return;
        boolean success = switch (mode) {
            case 0 -> cloud(level, owner, instance);
            case 1 -> blind(level, owner, instance);
            case 2 -> storm(level, owner);
            default -> false;
        };
        if (success) {
            QuirkCastCosts.spendAura(owner, cost);
            QuirkCastCosts.cooldown(instance, mode, mode == 1 ? 5 : mode == 2 ? 30 : 0, 3);
            instance.addMasteryPoint(owner);
            instance.markDirty();
            level.playSound(null, owner.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1, 0.7F);
        }
    }

    private static boolean cloud(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        if (!(owner instanceof Player player)) return false;
        int max = instance.getMastery() >= MAX_MASTERY ? 3 : instance.getMastery() >= MAX_MASTERY / 2.0 ? 2 : 1;
        List<QuirkCloud> existing = level.getEntitiesOfClass(QuirkCloud.class, owner.getBoundingBox().inflate(210),
                entity -> entity.kind() == QuirkCloud.RIDE && owner.getUUID().equals(entity.creator()));
        if (existing.size() >= max) {
            player.displayClientMessage(Component.literal("Cloud limit: " + max), true);
            return false;
        }
        QuirkCloud cloud = CloudEntities.CLOUD.get().create(level);
        if (cloud == null) return false;
        cloud.makeRide(owner, existing.size(), instance.getMastery() >= MAX_MASTERY);
        Vec3 horizontal = owner.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 position = owner.position().add(horizontal.scale(existing.isEmpty() ? 0 : 3.8))
                .add(0, owner.getVehicle() instanceof QuirkCloud ? -0.55 : 0, 0);
        cloud.setPos(position.x, position.y, position.z);
        if (level.getBlockCollisions(cloud, cloud.getBoundingBox()).iterator().hasNext()) {
            player.displayClientMessage(Component.literal("No room for a cloud here."), true);
            return false;
        }
        if (!level.addFreshEntity(cloud)) return false;
        if (!(owner.getVehicle() instanceof QuirkCloud)) owner.startRiding(cloud, true);
        return true;
    }

    private static boolean blind(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        Vec3 start = owner.getEyePosition();
        Vec3 end = start.add(owner.getLookAngle().scale(30));
        Vec3 stop = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getLocation();
        var hit = ProjectileUtil.getEntityHitResult(owner, start, stop, new AABB(start, stop).inflate(1),
                entity -> entity instanceof LivingEntity living && HomingQuirk.isEnemy(owner, living), start.distanceToSqr(stop));
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return false;
        if (instance.getMastery() < MAX_MASTERY
                && SkillAPI.getSkillsFrom(target).getSkill(MAGIC_SENSE).isPresent()) return false;
        QuirkCloud cloud = CloudEntities.CLOUD.get().create(level);
        if (cloud == null) return false;
        cloud.makeBlind(owner, target, instance.getMastery() >= MAX_MASTERY);
        return level.addFreshEntity(cloud);
    }

    private static boolean storm(ServerLevel level, LivingEntity owner) {
        QuirkCloud cloud = CloudEntities.CLOUD.get().create(level);
        if (cloud == null) return false;
        Vec3 start = owner.position().add(0, 0.1, 0);
        Vec3 below = new Vec3(start.x, level.getMinBuildHeight(), start.z);
        var ground = level.clip(new ClipContext(start, below, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner));
        cloud.makeStorm(owner, ground.getType() == HitResult.Type.BLOCK
                ? ground.getLocation().y : owner.getY());
        return level.addFreshEntity(cloud);
    }

    @SubscribeEvent public static void softLanding(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(event.getEntity())
                .getSkill(QuirkSkills.CLOUD.get()).orElse(null);
        if (instance != null && instance.isToggled()) event.setCanceled(true);
    }

    public static void pacify(LivingEntity target) {
        if (!(target instanceof Mob mob)) return;
        mob.getPersistentData().putLong(PACIFIED_UNTIL, target.level().getGameTime() + 60);
        clearAggro(mob);
    }

    private static void clearAggro(Mob mob) {
        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
    }

    @SubscribeEvent public static void preventRetarget(LivingChangeTargetEvent event) {
        if (!event.getEntity().level().isClientSide
                && event.getEntity().getPersistentData().getLong(PACIFIED_UNTIL) > event.getEntity().level().getGameTime())
            event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent public static void pacifiedTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide
                || !mob.getPersistentData().contains(PACIFIED_UNTIL)) return;
        if (mob.getPersistentData().getLong(PACIFIED_UNTIL) > mob.level().getGameTime()) clearAggro(mob);
        else mob.getPersistentData().remove(PACIFIED_UNTIL);
    }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.impl.SkillStorage;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.TensuraSkill;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.entity.template.GreaterSpiritEntity;
import io.github.manasmods.tensura.entity.template.subclass.IArenaBoss;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class ZombieVirusQuirk extends Skill {

    private static final QuirkSkillsConfig.ZombieVirus CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).ZombieVirus;

    private static final int ZOMBIE_CREATION = 0;
    private static final int ZOMBIFICATION_END = 1;
    private static final int VIRUS_CLOUD = 2;
    private static final int HORDE_CALL = 3;
    private static final int MODES = 4;
    public static final String CLOUD_TAG = "virusCloud";
    private static final long CLOUD_GRACE = 10L;
    private static final long CLOUD_RENEW = 5L;
    private static final double GUARD_FOLLOW = 6.0D;
    private static final int LEND_SECONDS = 10;

    private static final String ZOMBIE_TAG = "tracadamia_zombified";
    private static final String OWNER_KEY = "owner";
    private static final String UNTIL_KEY = "until";
    private static final String MASTERED_KEY = "mastered";
    private static final String CREATED_KEY = "created";
    private static final String GUARD_KEY = "guard";
    private static final String COMMANDED_KEY = "commanded";
    private static final DustParticleOptions VIRUS = new DustParticleOptions(new Vector3f(0.35F, 0.6F, 0.2F), 1.0F);
    private static final double ZOMBIE_HEIGHT = 1.95D;
    private static final double MIN_SCALE = 0.3D;
    private static final double MAX_SCALE = 6.0D;
    private static final int MARKER_TICKS = 20;

    private record Zombified(UUID owner, long until, boolean mastered) {}

    private static final Map<UUID, Zombified> PLAYERS = new HashMap<>();
    private static final Set<Mob> ZOMBIES = new HashSet<>();
    private static final Map<UUID, Long> ENDED = new HashMap<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/zombievirus.png");
    }

    public ZombieVirusQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ZOMBIE_CREATION -> CONFIG.creationAuraCost;
            case ZOMBIFICATION_END -> CONFIG.endAuraCost;
            case VIRUS_CLOUD -> CONFIG.cloudAuraCost;
            case HORDE_CALL -> CONFIG.hordeAuraCost;
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
            case ZOMBIE_CREATION -> "zombie_virus.zombie_creation";
            case ZOMBIFICATION_END -> "zombie_virus.zombification_end";
            case VIRUS_CLOUD -> "zombie_virus.virus_cloud";
            case HORDE_CALL -> "zombie_virus.horde_call";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getZombieVirus(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ZOMBIE_VIRUS.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    // Abnormality Immunity

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
            lendSkill(entity, ResistanceSkills.ABNORMAL_CONDITION_NULLIFICATION.get());
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        takeSkill(entity, ResistanceSkills.ABNORMAL_CONDITION_NULLIFICATION.get());
    }

    // Zombie Immunity

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
        lendSkill(entity, ResistanceSkills.ABNORMAL_CONDITION_NULLIFICATION.get());
        if (instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    private static boolean isImmune(LivingEntity entity) {
        return getZombieVirus(entity).filter(ManasSkillInstance::isToggled).isPresent();
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null || mob.level().isClientSide) {
            return;
        }

        // Stops the zombies from targetting things they shouldnt
        UUID owner = getZombieOwner(mob);
        if (owner != null && (target.getUUID().equals(owner) || isZombie(target) || isOwnedBy(target, owner))) {
            event.setCanceled(true);
            return;
        }
        if (owner != null && mob instanceof Mob zombie && isGuard(zombie) && !isGuardTarget(zombie, owner, target)) {
            event.setCanceled(true);
            return;
        }

        // Undead only go after a zombie virus user or a zombie when it hits them first
        if (mob.getType().is(EntityTypeTags.UNDEAD) && mob.getLastHurtByMob() != target && (isImmune(target) || PLAYERS.containsKey(target.getUUID()))) {
            event.setCanceled(true);
        }
    }

    // Zombie Creation

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case ZOMBIE_CREATION -> zombieCreation(level, instance, entity);
            case ZOMBIFICATION_END -> endZombification(level, instance, entity);
            case HORDE_CALL -> {
                if (entity.isShiftKeyDown()) {
                    hordeCalm(level, instance, entity);
                } else {
                    hordeCall(level, instance, entity);
                }
            }
        }
    }

    public static boolean isClouding(ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(CLOUD_TAG) >= entity.level().getGameTime();
    }

    private static void setClouding(ManasSkillInstance instance, LivingEntity entity, boolean clouding) {
        long now = entity.level().getGameTime();
        long until = instance.getOrCreateTag().getLong(CLOUD_TAG);
        if (clouding ? until - now < CLOUD_RENEW : until != 0L) {
            instance.getOrCreateTag().putLong(CLOUD_TAG, clouding ? now + CLOUD_GRACE : 0L);
            instance.markDirty();
            SkillAPI.getSkillsFrom(entity).markDirty();
        }
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode != VIRUS_CLOUD || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (heldTicks % 20 == 0 && EnergyHelper.isOutOfEnergy(entity, instance, VIRUS_CLOUD)) {
            stopCloud(instance, entity);
            return false;
        }

        if (heldTicks == 0) {
            playSound(level, entity.position(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.2F, 0.6F);
        }
        setClouding(instance, entity, true);
        entity.setDeltaMovement(0.0D, Math.min(0.0D, entity.getDeltaMovement().y), 0.0D);
        if (!(entity instanceof Player)) {
            entity.hurtMarked = true;
        }

        boolean mastered = instance.isMastered(entity);
        double half = (mastered ? CONFIG.cloudSizeMastered : CONFIG.cloudSize) * 0.5D;
        AABB area = new AABB(entity.position(), entity.position()).inflate(half, 1.0D, half).expandTowards(0.0D, entity.getBbHeight() + 1.0D, 0.0D);
        if (heldTicks % 3 == 0) {
            for (int i = 0; i < 10; i++) {
                double x = area.minX + level.random.nextDouble() * area.getXsize();
                double y = area.minY + level.random.nextDouble() * Math.min(area.getYsize(), 3.0D);
                double z = area.minZ + level.random.nextDouble() * area.getZsize();
                level.sendParticles(VIRUS, x, y, z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            level.sendParticles(ParticleTypes.SNEEZE, entity.getX(), entity.getY() + 0.3D, entity.getZ(), 4, half * 0.5D, 0.3D, half * 0.5D, 0.01D);
        }

        if (heldTicks > 0 && heldTicks % (CONFIG.cloudInfectSeconds * 20) == 0) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, target -> target != entity && target.isAlive() && canZombify(target, entity.getUUID())
                    && HitRules.canHit(entity, target))) {
                if (countZombies(entity.getUUID()) >= CONFIG.maxZombies) {
                    break;
                }
                if (EnergyHelper.getMaxEP(entity) >= EnergyHelper.getMaxEP(target) * CONFIG.epMultiplier && rolls(level, target, mastered)) {
                    zombify(level, entity.getUUID(), mastered, target, true);
                }
            }
        }
        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == VIRUS_CLOUD && !entity.level().isClientSide && isClouding(instance, entity)) {
            stopCloud(instance, entity);
        }
    }

    private static void stopCloud(ManasSkillInstance instance, LivingEntity entity) {
        setClouding(instance, entity, false);
        instance.setCoolDown(CONFIG.cloudCooldown, VIRUS_CLOUD);
    }

    private static boolean isGuard(Mob zombie) {
        CompoundTag data = zombie.getPersistentData().getCompound(ZOMBIE_TAG);
        return data.getBoolean(GUARD_KEY);
    }

    private static @Nullable UUID getCommanded(Mob zombie) {
        CompoundTag data = zombie.getPersistentData().getCompound(ZOMBIE_TAG);
        return data.hasUUID(COMMANDED_KEY) ? data.getUUID(COMMANDED_KEY) : null;
    }

    private static @Nullable LivingEntity findOwner(Mob zombie, UUID owner) {
        return zombie.level() instanceof ServerLevel level && level.getEntity(owner) instanceof LivingEntity living ? living : null;
    }

    private static boolean isGuardTarget(Mob zombie, UUID owner, LivingEntity target) {
        if (target.getUUID().equals(getCommanded(zombie))) {
            return true;
        }

        LivingEntity master = findOwner(zombie, owner);
        return master != null && (master.getLastHurtByMob() == target || target instanceof Mob mob && mob.getTarget() == master);
    }

    private static List<Mob> getNearbyZombies(LivingEntity owner) {
        double range = CONFIG.hordeRange;
        return ZOMBIES.stream().filter(zombie -> owner.getUUID().equals(getZombieOwner(zombie)) && zombie.level() == owner.level() && zombie.distanceToSqr(owner) <= range * range).toList();
    }

    private static void hordeCall(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, CONFIG.hordeRange, false, true);
        if (target == null || target == entity || isZombie(target)) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        List<Mob> zombies = getNearbyZombies(entity);
        if (zombies.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.zombie_virus.no_zombies"));
            return;
        }
        if (EnergyHelper.isOutOfEnergy(entity, instance, HORDE_CALL)) {
            return;
        }

        for (Mob zombie : zombies) {
            zombie.getPersistentData().getCompound(ZOMBIE_TAG).putUUID(COMMANDED_KEY, target.getUUID());
            zombie.setTarget(target);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, zombie.getX(), zombie.getY() + zombie.getBbHeight() + 0.3D, zombie.getZ(), 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
        Vec3 point = target.getBoundingBox().getCenter();
        level.sendParticles(VIRUS, point.x, point.y, point.z, 20, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.0D);
        playSound(level, entity.position(), SoundEvents.ZOMBIE_AMBIENT, 2.0F, 0.6F);
        playSound(level, point, SoundEvents.RAID_HORN.value(), 0.4F, 1.4F);
        instance.setCoolDown(CONFIG.hordeCooldown, HORDE_CALL);
        instance.addMasteryPoint(entity);
    }

    private static void hordeCalm(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        List<Mob> zombies = getNearbyZombies(entity);
        if (zombies.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.zombie_virus.no_zombies"));
            return;
        }

        for (Mob zombie : zombies) {
            zombie.getPersistentData().getCompound(ZOMBIE_TAG).remove(COMMANDED_KEY);
            zombie.setTarget(null);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, zombie.getX(), zombie.getY() + zombie.getBbHeight() + 0.3D, zombie.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
        }
        playSound(level, entity.position(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT, 1.5F, 0.8F);
        instance.setCoolDown(CONFIG.hordeCooldown, HORDE_CALL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOwnerHurt(LivingIncomingDamageEvent event) {
        LivingEntity owner = event.getEntity();
        if (ZOMBIES.isEmpty() || owner.level().isClientSide || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == owner || isZombie(attacker)
                || isOwnedBy(attacker, owner.getUUID())) {
            return;
        }

        for (Mob zombie : getNearbyZombies(owner)) {
            if (isGuard(zombie) && getCommanded(zombie) == null && (zombie.getTarget() == null || !zombie.getTarget().isAlive())) {
                zombie.setTarget(attacker);
            }
        }
    }

    private static void zombieCreation(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, getReach(entity));
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.getMaxEP(entity) < EnergyHelper.getMaxEP(target) * CONFIG.epMultiplier) {
            fail(entity, Component.translatable("tracadamia.skill.zombie_virus.ep_too_low", CONFIG.epMultiplier));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ZOMBIE_CREATION)) {
            return;
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        target.invulnerableTime = 0;
        boolean hit = target.hurt(((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, ZOMBIE_CREATION), (float) getMeleeDamage(entity));
        if (hit && target.isAlive()) {
            if (!canZombify(target, entity.getUUID())) {
                message(entity, Component.translatable("tracadamia.skill.zombie_virus.immune"));
            } else if (countZombies(entity.getUUID()) >= CONFIG.maxZombies) {
                message(entity, Component.translatable("tracadamia.skill.zombie_virus.max_zombies", CONFIG.maxZombies));
            } else if (!rolls(level, target, instance.isMastered(entity)) || !zombify(level, entity.getUUID(), instance.isMastered(entity), target)) {
                message(entity, Component.translatable("tracadamia.skill.zombie_virus.resisted"));
            }
        }

        instance.setCoolDown(CONFIG.creationCooldown, ZOMBIE_CREATION);
        instance.addMasteryPoint(entity);
    }

    private static double getReach(LivingEntity entity) {
        return entity instanceof Player player ? player.entityInteractionRange() : 3.0D;
    }

    private static boolean canZombify(LivingEntity target, UUID owner) {
        return !target.isRemoved() && !target.getUUID().equals(owner) && !isZombie(target) && !target.getType().is(EntityTypeTags.UNDEAD) && !isBoss(target) && !isBlacklisted(target)
                && (target instanceof Mob || target instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator());
    }

    private static boolean isBlacklisted(LivingEntity target) {
        ResourceLocation type = EntityType.getKey(target.getType());
        return CONFIG.blacklist.contains(type.toString()) || CONFIG.modBlacklist.contains(type.getNamespace());
    }

    private static boolean isBoss(LivingEntity target) {
        return !MultiArms.canGrab(target) || target instanceof IArenaBoss || target instanceof GreaterSpiritEntity;
    }

    private static boolean rolls(ServerLevel level, LivingEntity target, boolean mastered) {
        return level.random.nextDouble() < getChance(target, mastered);
    }

    private static double getChance(LivingEntity target, boolean mastered) {
        if (SkillUtils.hasSkill(target, ResistanceSkills.ABNORMAL_CONDITION_NULLIFICATION.get()) || getZombieVirus(target).isPresent()) {
            return mastered ? CONFIG.nullifiedChanceMastered : CONFIG.nullifiedChance;
        }

        if (SkillUtils.hasSkill(target, ResistanceSkills.ABNORMAL_CONDITION_RESISTANCE.get())) {
            return mastered ? CONFIG.resistantChanceMastered : CONFIG.resistantChance;
        }

        return mastered ? CONFIG.chanceMastered : CONFIG.chance;
    }

    private static boolean zombify(ServerLevel level, UUID owner, boolean mastered, LivingEntity target) {
        return zombify(level, owner, mastered, target, false);
    }

    private static boolean zombify(ServerLevel level, UUID owner, boolean mastered, LivingEntity target, boolean guard) {
        if (!canZombify(target, owner) || countZombies(owner) >= CONFIG.maxZombies) {
            return false;
        }

        long until = level.getGameTime() + CONFIG.zombieSeconds * 20L;
        if (target instanceof ServerPlayer player) {
            PLAYERS.put(player.getUUID(), new Zombified(owner, until, mastered));
            player.displayClientMessage(Component.translatable("tracadamia.skill.zombie_virus.zombified").withStyle(ChatFormatting.DARK_GREEN), true);
        } else if (!(target instanceof Mob mob) || turn(level, mob, owner, until, mastered, guard) == null) {
            return false;
        }

        Vec3 point = target.getBoundingBox().getCenter();
        level.sendParticles(VIRUS, point.x, point.y, point.z, 30, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.0D);
        playSound(level, point, SoundEvents.ZOMBIE_INFECT, 1.0F, 0.9F);
        return true;
    }

    private static int countZombies(UUID owner) {
        return (int) (ZOMBIES.stream().filter(zombie -> owner.equals(getZombieOwner(zombie))).count() + PLAYERS.values().stream().filter(zombified -> zombified.owner().equals(owner)).count());
    }

    private static @Nullable Zombie turn(ServerLevel level, Mob mob, UUID owner, long until, boolean mastered, boolean guard) {
        EntityType<? extends Zombie> type = mob instanceof Villager ? EntityType.ZOMBIE_VILLAGER : mob instanceof AbstractPiglin ? EntityType.ZOMBIFIED_PIGLIN : EntityType.ZOMBIE;
        Zombie zombie = type.create(level);
        if (zombie == null) {
            return null;
        }

        mob.stopRiding();
        mob.ejectPassengers();

        zombie.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        zombie.setYHeadRot(mob.getYHeadRot());
        zombie.setYBodyRot(mob.yBodyRot);
        if (mob.hasCustomName()) {
            zombie.setCustomName(mob.getCustomName());
        }
        if (mob instanceof Villager villager && zombie instanceof ZombieVillager zombieVillager) {
            zombieVillager.setVillagerData(villager.getVillagerData());
        }

        zombie.setPersistenceRequired();
        zombie.setCanPickUpLoot(false);
        setBase(zombie, Attributes.MAX_HEALTH, Math.max(1.0D, mob.getMaxHealth()));
        setBase(zombie, Attributes.ATTACK_DAMAGE, Math.max(getBase(zombie, Attributes.ATTACK_DAMAGE), getMeleeDamage(mob)));
        setBase(zombie, Attributes.ARMOR, Math.max(getBase(zombie, Attributes.ARMOR), mob.getArmorValue()));
        setBase(zombie, Attributes.SCALE, Math.max(MIN_SCALE, Math.min(MAX_SCALE, mob.getBbHeight() / ZOMBIE_HEIGHT)));
        setBase(zombie, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
        zombie.setHealth(mob.getHealth());

        CompoundTag data = new CompoundTag();
        data.putUUID(OWNER_KEY, owner);
        data.putLong(UNTIL_KEY, until);
        data.putBoolean(MASTERED_KEY, mastered);
        data.putLong(CREATED_KEY, level.getGameTime());
        data.putBoolean(GUARD_KEY, guard);
        zombie.getPersistentData().put(ZOMBIE_TAG, data);
        IExistence existence = TensuraStorages.getExistenceFrom(zombie);
        existence.setTemporaryOwner(owner);
        existence.markDirty();

        mob.discard();
        level.addFreshEntity(zombie);
        return zombie;
    }

    // Kil them after the timer
    private static void perish(Mob zombie) {
        ZOMBIES.remove(zombie);
        if (zombie.level() instanceof ServerLevel level) {
            Vec3 point = zombie.getBoundingBox().getCenter();
            level.sendParticles(VIRUS, point.x, point.y, point.z, 30, zombie.getBbWidth() * 0.5D, zombie.getBbHeight() * 0.4D, zombie.getBbWidth() * 0.5D, 0.0D);
            playSound(level, point, SoundEvents.ZOMBIE_DEATH, 1.0F, 0.8F);
        }

        zombie.kill();
        if (zombie.isAlive()) {
            zombie.discard();
        }
    }

    public static boolean isZombie(LivingEntity entity) {
        return entity.getPersistentData().contains(ZOMBIE_TAG) || PLAYERS.containsKey(entity.getUUID());
    }

    private static @Nullable UUID getZombieOwner(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData().getCompound(ZOMBIE_TAG);
        if (data.hasUUID(OWNER_KEY)) {
            return data.getUUID(OWNER_KEY);
        }

        Zombified zombified = PLAYERS.get(entity.getUUID());
        return zombified == null ? null : zombified.owner();
    }

    private static boolean isMastered(LivingEntity entity) {
        Zombified zombified = PLAYERS.get(entity.getUUID());
        return zombified != null ? zombified.mastered() : entity.getPersistentData().getCompound(ZOMBIE_TAG).getBoolean(MASTERED_KEY);
    }

    private static boolean isOwnedBy(LivingEntity target, UUID owner) {
        if (target instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID())) {
            return true;
        }

        IExistence existence = TensuraStorages.getExistenceFrom(target);
        return owner.equals(existence.getTemporaryOwner()) || owner.equals(existence.getPermanentOwner());
    }

    // Just hostile towards anything that isnt them or the owner
    private static boolean isPrey(Mob zombie, LivingEntity target) {
        UUID owner = getZombieOwner(zombie);
        if (owner != null && isGuard(zombie) && !isGuardTarget(zombie, owner, target)) {
            return false;
        }
        return (target instanceof Mob || target instanceof Player) && !isZombie(target) && !target.getType().is(EntityTypeTags.UNDEAD)
                && (owner == null || !target.getUUID().equals(owner) && !isOwnedBy(target, owner));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !mob.getPersistentData().contains(ZOMBIE_TAG)) {
            return;
        }

        CompoundTag data = mob.getPersistentData().getCompound(ZOMBIE_TAG);
        Long ended = data.hasUUID(OWNER_KEY) ? ENDED.get(data.getUUID(OWNER_KEY)) : null;
        if (ended != null && data.getLong(CREATED_KEY) <= ended) {
            event.setCanceled(true);
            return;
        }

        mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, target -> isPrey(mob, target)));
        ZOMBIES.add(mob);
    }


    private static void endZombification(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        UUID owner = entity.getUUID();
        List<Mob> zombies = ZOMBIES.stream().filter(zombie -> owner.equals(getZombieOwner(zombie))).toList();
        List<UUID> players = PLAYERS.entrySet().stream().filter(entry -> entry.getValue().owner().equals(owner)).map(Map.Entry::getKey).toList();
        if (zombies.isEmpty() && players.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.zombie_virus.no_zombies"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ZOMBIFICATION_END)) {
            return;
        }

        ENDED.put(owner, level.getGameTime());
        zombies.forEach(ZombieVirusQuirk::perish);

        for (UUID uuid : players) {
            PLAYERS.remove(uuid);
            Player player = findPlayer(level.getServer(), uuid);
            if (player != null) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.zombie_virus.cured").withStyle(ChatFormatting.GRAY), true);
            }
        }

        playSound(level, entity.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 1.0F, 0.6F);
        instance.setCoolDown(CONFIG.endCooldown, ZOMBIFICATION_END);
        instance.addMasteryPoint(entity);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            ZOMBIES.remove(mob);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onZombieKill(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof LivingEntity killer)) {
            return;
        }

        UUID owner = getZombieOwner(killer);
        if (owner == null || !canZombify(target, owner) || countZombies(owner) >= CONFIG.maxZombies || !rolls(level, target, isMastered(killer))) {
            return;
        }

        event.setCanceled(true);
        target.setHealth(target.getMaxHealth());
        target.clearFire();
        zombify(level, owner, isMastered(killer), target);
    }

    // Players that become zombies cant hurt the person who is in charge of them/ the zombie army
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onZombieAttack(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker && PLAYERS.containsKey(attacker.getUUID()) && event.getEntity().getUUID().equals(PLAYERS.get(attacker.getUUID()).owner())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();
        if (!ZOMBIES.isEmpty()) {
            for (Mob zombie : List.copyOf(ZOMBIES)) {
                if (!(zombie.level() instanceof ServerLevel level) || !zombie.isAlive()) {
                    ZOMBIES.remove(zombie);
                    continue;
                }

                if (level.getGameTime() > zombie.getPersistentData().getCompound(ZOMBIE_TAG).getLong(UNTIL_KEY)) {
                    perish(zombie);
                    continue;
                }

                if (zombie.getRemainingFireTicks() > 0 && !zombie.isInLava() && isInSun(level, zombie)) {
                    zombie.clearFire();
                }

                if (time % 20L == 0L && isGuard(zombie) && (zombie.getTarget() == null || !zombie.getTarget().isAlive())) {
                    UUID owner = getZombieOwner(zombie);
                    LivingEntity master = owner == null ? null : findOwner(zombie, owner);
                    if (master != null && zombie.distanceToSqr(master) > GUARD_FOLLOW * GUARD_FOLLOW) {
                        zombie.getNavigation().moveTo(master, 1.15D);
                    }
                }
            }
        }

        if (!PLAYERS.isEmpty()) {
            PLAYERS.entrySet().removeIf(entry -> {
                Player player = findPlayer(server, entry.getKey());
                if (player == null || !player.isAlive()) {
                    return true;
                }

                if (player.level().getGameTime() > entry.getValue().until()) {
                    player.displayClientMessage(Component.translatable("tracadamia.skill.zombie_virus.cured").withStyle(ChatFormatting.GRAY), true);
                    return true;
                }

                if (time % MARKER_TICKS == 0L && player.level() instanceof ServerLevel level) {
                    level.sendParticles(VIRUS, player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(), 4, 0.3D, 0.4D, 0.3D, 0.0D);
                }
                return false;
            });
        }
    }

    private static @Nullable Player findPlayer(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Player player = level.getPlayerByUUID(uuid);
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    private static boolean isInSun(Level level, Entity entity) {
        BlockPos eye = BlockPos.containing(entity.getEyePosition());
        return level.isDay() && level.canSeeSky(eye) && !level.isRainingAt(eye);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PLAYERS.clear();
        ZOMBIES.clear();
        ENDED.clear();
    }

    private static double getBase(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getBaseValue();
    }

    private static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static double getMeleeDamage(LivingEntity entity) {
        AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        return attack == null ? 1.0D : attack.getValue();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void message(LivingEntity entity, Component message) {
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

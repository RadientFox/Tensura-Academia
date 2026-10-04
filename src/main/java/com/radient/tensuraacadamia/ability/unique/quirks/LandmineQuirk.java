package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class LandmineQuirk extends Skill {

    private static final QuirkSkillsConfig.Landmine CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Landmine;

    private static final int LANDMINE = 0;
    private static final int DETONATION = 1;
    private static final int REMOTE_DETONATION = 2;
    private static final int MODES = 3;

    private static final int MARKER_TICKS = 10;
    private static final double MINE_TRIGGER_HEIGHT = 0.25D;
    private static final DustParticleOptions MINE_MARKER = new DustParticleOptions(new Vector3f(1.0F, 0.15F, 0.1F), 0.8F);
    private static final DustParticleOptions BOMB_MARKER = new DustParticleOptions(new Vector3f(0.9F, 0.1F, 0.1F), 1.0F);
    private static final DustParticleOptions REMOTE_MARKER = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.05F), 1.0F);

    private static final ExplosionDamageCalculator BLOCKS_ONLY = new ExplosionDamageCalculator() {
        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    private record Mine(UUID owner, ResourceKey<Level> dimension, BlockPos pos) {}

    private record Bomb(UUID owner, boolean remote) {}

    private record Blast(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {}

    private static final List<Mine> MINES = new ArrayList<>();
    private static final Map<LivingEntity, Bomb> BOMBS = new HashMap<>();
    private static @Nullable Blast blasting;

    public LandmineQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (entity.isShiftKeyDown()) {
            return 0.0D;
        }

        return switch (mode) {
            case LANDMINE -> CONFIG.mineAuraCost;
            case DETONATION -> CONFIG.bombAuraCost;
            case REMOTE_DETONATION -> CONFIG.remoteAuraCost;
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
            case LANDMINE -> "landmine.landmine";
            case DETONATION -> "landmine.detonation";
            case REMOTE_DETONATION -> "landmine.remote_detonation";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getLandmine(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.LANDMINE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case LANDMINE -> {
                if (entity.isShiftKeyDown()) {
                    removeMine(level, entity);
                } else {
                    placeMine(level, instance, entity);
                }
            }
            case DETONATION -> {
                if (entity.isShiftKeyDown()) {
                    removeBomb(level, entity);
                } else {
                    placeBomb(level, instance, entity, false);
                }
            }
            case REMOTE_DETONATION -> {
                if (entity.isShiftKeyDown()) {
                    detonateRemote(level, instance, entity);
                } else {
                    placeBomb(level, instance, entity, true);
                }
            }
        }
    }

    // Projectile Bombs

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        instance.addMasteryPoint(entity);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.level() instanceof ServerLevel level) || !(projectile.getOwner() instanceof LivingEntity owner) || event.getRayTraceResult().getType() == HitResult.Type.MISS) {
            return;
        }

        getLandmine(owner).filter(ManasSkillInstance::isToggled).ifPresent(instance -> projectileBomb(level, instance, owner, event.getRayTraceResult().getLocation()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMagicHit(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (blasting != null || !(event.getEntity().level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity owner)
                || source.getDirectEntity() instanceof Projectile || ((TensuraDamageSource) source).tensura$getMagicType() == null) {
            return;
        }

        getLandmine(owner).filter(ManasSkillInstance::isToggled).ifPresent(instance -> projectileBomb(level, instance, owner, event.getEntity().getBoundingBox().getCenter()));
    }

    private static void projectileBomb(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 point) {
        boolean mastered = instance.isMastered(owner);
        explode(level, instance, owner, point, mastered ? CONFIG.projectileSizeMastered : CONFIG.projectileSize, (float) (mastered ? CONFIG.projectileDamageMastered : CONFIG.projectileDamage), LANDMINE, false);
    }

    // Landmine

    private static BlockHitResult getLookedAtBlock(ServerLevel level, LivingEntity entity) {
        Vec3 eye = entity.getEyePosition();
        return level.clip(new ClipContext(eye, eye.add(entity.getLookAngle().scale(CONFIG.mineRange)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
    }

    private static void removeMine(ServerLevel level, LivingEntity entity) {
        BlockHitResult hit = getLookedAtBlock(level, entity);
        Mine mine = hit.getType() != HitResult.Type.BLOCK ? null : MINES.stream()
                .filter(placed -> placed.pos().equals(hit.getBlockPos()) && placed.dimension() == level.dimension() && placed.owner().equals(entity.getUUID())).findFirst().orElse(null);
        if (mine == null) {
            fail(entity, "tracadamia.skill.landmine.no_mine");
            return;
        }

        MINES.remove(mine);
        playSound(level, Vec3.atBottomCenterOf(mine.pos().above()), SoundEvents.TRIPWIRE_DETACH, 1.0F, 1.2F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.landmine.mine_removed").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void placeMine(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        BlockHitResult hit = getLookedAtBlock(level, entity);
        if (hit.getType() != HitResult.Type.BLOCK) {
            fail(entity, "tracadamia.skill.landmine.no_block");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            fail(entity, "tracadamia.skill.landmine.blocked");
            return;
        }

        if (MINES.stream().anyMatch(mine -> mine.pos().equals(pos) && mine.dimension() == level.dimension())) {
            fail(entity, "tracadamia.skill.landmine.mine_here");
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, LANDMINE)) {
            return;
        }

        // Past the limit the oldest one goes away
        List<Mine> owned = MINES.stream().filter(mine -> mine.owner().equals(entity.getUUID())).toList();
        for (int oldest = 0; oldest < owned.size() + 1 - Math.max(1, CONFIG.maxMines); oldest++) {
            MINES.remove(owned.get(oldest));
        }

        MINES.add(new Mine(entity.getUUID(), level.dimension(), pos.immutable()));
        Vec3 top = Vec3.atBottomCenterOf(pos.above());
        playSound(level, top, SoundEvents.TRIPWIRE_ATTACH, 1.0F, 0.6F);
        showMarker(level, entity, top.add(0.0D, 0.05D, 0.0D), MINE_MARKER, 6);
        instance.setCoolDown(CONFIG.mineCooldown, LANDMINE);
        instance.addMasteryPoint(entity);
    }

    // true once the mine goes off or is gone
    private static boolean tickMine(MinecraftServer server, Mine mine, long time) {
        ServerLevel level = server.getLevel(mine.dimension());
        if (level == null || !level.isLoaded(mine.pos())) {
            return false;
        }

        if (level.getBlockState(mine.pos()).getCollisionShape(level, mine.pos()).isEmpty()) {
            return true;
        }

        LivingEntity owner = findOwner(server, level, mine.owner());
        ManasSkillInstance instance = owner == null ? null : getLandmine(owner).orElse(null);
        if (owner == null || instance == null) {
            return false;
        }

        Vec3 top = Vec3.atBottomCenterOf(mine.pos().above());
        if (time % MARKER_TICKS == 0L) {
            showMarker(level, owner, top.add(0.0D, 0.05D, 0.0D), MINE_MARKER, 1);
        }

        AABB trigger = new AABB(mine.pos().above()).setMaxY(mine.pos().getY() + 1.0D + MINE_TRIGGER_HEIGHT);
        boolean stepped = !level.getEntitiesOfClass(LivingEntity.class, trigger, target -> target != owner && target.isAlive() && !target.isSpectator() && !isAlly(owner, target)).isEmpty();
        if (!stepped) {
            return false;
        }

        boolean mastered = instance.isMastered(owner);
        explode(level, instance, owner, top, mastered ? CONFIG.mineSizeMastered : CONFIG.mineSize, (float) (mastered ? CONFIG.mineDamageMastered : CONFIG.mineDamage), LANDMINE, true);
        return true;
    }

    // Detonation and Remote Detonation

    private static void placeBomb(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, boolean remote) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.bombRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (BOMBS.containsKey(target)) {
            fail(entity, "tracadamia.skill.landmine.has_bomb");
            return;
        }

        int max = remote ? CONFIG.maxRemoteBombs : instance.isMastered(entity) ? CONFIG.maxBombsMastered : CONFIG.maxBombs;
        if (BOMBS.values().stream().filter(bomb -> bomb.owner().equals(entity.getUUID()) && bomb.remote() == remote).count() >= max) {
            fail(entity, Component.translatable("tracadamia.skill.landmine.max_bombs", max));
            return;
        }

        int mode = remote ? REMOTE_DETONATION : DETONATION;
        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        BOMBS.put(target, new Bomb(entity.getUUID(), remote));
        entity.swing(InteractionHand.MAIN_HAND, true);
        playSound(level, target.position(), SoundEvents.TNT_PRIMED, 0.4F, 1.8F);
        showMarker(level, entity, getBombSpot(target), remote ? REMOTE_MARKER : BOMB_MARKER, 6);
        instance.setCoolDown(remote ? CONFIG.remoteCooldown : CONFIG.bombCooldown, mode);
        instance.addMasteryPoint(entity);
    }

    private static void removeBomb(ServerLevel level, LivingEntity entity) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.bombRange);
        Bomb bomb = target == null ? null : BOMBS.get(target);
        if (bomb == null || !bomb.owner().equals(entity.getUUID())) {
            fail(entity, "tracadamia.skill.landmine.no_bomb");
            return;
        }

        BOMBS.remove(target);
        playSound(level, target.position(), SoundEvents.TRIPWIRE_DETACH, 1.0F, 1.2F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.landmine.bomb_removed").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void detonateRemote(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        List<LivingEntity> targets = BOMBS.entrySet().stream().filter(entry -> entry.getValue().remote() && entry.getValue().owner().equals(entity.getUUID())).map(Map.Entry::getKey).toList();
        if (targets.isEmpty()) {
            fail(entity, "tracadamia.skill.landmine.no_remote");
            return;
        }

        boolean mastered = instance.isMastered(entity);
        playSound(level, entity.position(), SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 1.6F);
        for (LivingEntity target : targets) {
            BOMBS.remove(target);
            if (target.isAlive() && target.level() instanceof ServerLevel targetLevel) {
                explode(targetLevel, instance, entity, target.getBoundingBox().getCenter(), mastered ? CONFIG.remoteSizeMastered : CONFIG.remoteSize,
                        (float) (mastered ? CONFIG.remoteDamageMastered : CONFIG.remoteDamage), REMOTE_DETONATION, true);
            }
        }

        instance.addMasteryPoint(entity);
    }

    // A bombed target that dies blows up
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }

        Bomb bomb = BOMBS.remove(target);
        if (bomb == null || bomb.remote()) {
            return;
        }

        LivingEntity owner = findOwner(level.getServer(), level, bomb.owner());
        ManasSkillInstance instance = owner == null ? null : getLandmine(owner).orElse(null);
        if (instance == null) {
            return;
        }

        boolean mastered = instance.isMastered(owner);
        float damage = (float) ((mastered ? CONFIG.bombDamageMastered : CONFIG.bombDamage) + target.getMaxHealth() * (mastered ? CONFIG.bombHealthPercentMastered : CONFIG.bombHealthPercent));
        explode(level, instance, owner, target.getBoundingBox().getCenter(), mastered ? CONFIG.bombSizeMastered : CONFIG.bombSize, damage, DETONATION, true, target);
    }

    private static Vec3 getBombSpot(LivingEntity target) {
        return target.position().add(0.0D, target.getBbHeight() + 0.3D, 0.0D);
    }

    // Explosions

    private static void explode(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 center, double size, float damage, int mode, boolean breakBlocks) {
        explode(level, instance, owner, center, size, damage, mode, breakBlocks, null);
    }

    // Hurts everything in the blast
    private static void explode(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 center, double size, float damage, int mode, boolean breakBlocks, @Nullable LivingEntity skip) {
        double radius = size * 0.5D;
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, mode);
        blasting = new Blast(instance, owner, level);
        try {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    target -> target != owner && target != skip && target.isAlive() && !target.isSpectator() && !isAlly(owner, target))) {
                target.invulnerableTime = 0;
                target.hurt(source, damage);
                Vec3 away = target.position().subtract(center);
                target.knockback(0.4D + radius * 0.1D, -away.x, -away.z);
                target.hurtMarked = true;
            }

            if (breakBlocks && CONFIG.explosionsBreakBlocks && GroundBlocks.canGrief(level, owner)) {
                level.explode(owner, null, BLOCKS_ONLY, center.x, center.y, center.z, (float) radius, false, Level.ExplosionInteraction.TNT);
                return;
            }
        } finally {
            blasting = null;
        }

        level.sendParticles(radius >= 2.0D ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 6 + (int) (radius * 4.0D), radius * 0.4D, radius * 0.3D, radius * 0.4D, 0.02D);
        playSound(level, center, SoundEvents.GENERIC_EXPLODE.value(), (float) Math.min(4.0D, 0.6D + radius * 0.3D), (float) Math.max(0.6D, 1.4D - radius * 0.1D));
    }

    // grief rules
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Blast blast = blasting;
        if (blast == null || event.getLevel() != blast.level()) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> !GroundBlocks.canBreak(blast.level(), blast.instance(), blast.owner(), pos));
        event.getAffectedBlocks().forEach(pos -> GroundBlocks.griefed(blast.level(), blast.instance(), blast.owner(), pos));
    }

    private static boolean isAlly(LivingEntity owner, LivingEntity target) {
        return owner.isAlliedTo(target) || target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID());
    }

    private static @Nullable LivingEntity findOwner(MinecraftServer server, ServerLevel level, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.isAlive() ? player : null;
        }

        return level.getEntity(uuid) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static void showMarker(ServerLevel level, LivingEntity owner, Vec3 spot, DustParticleOptions marker, int count) {
        if (owner instanceof ServerPlayer player && player.level() == level) {
            level.sendParticles(player, marker, false, spot.x, spot.y, spot.z, count, 0.15D, 0.05D, 0.15D, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long time = server.overworld().getGameTime();
        if (!MINES.isEmpty()) {
            MINES.removeIf(mine -> tickMine(server, mine, time));
        }

        if (!BOMBS.isEmpty()) {
            BOMBS.keySet().removeIf(Entity::isRemoved);
            if (time % MARKER_TICKS == 0L) {
                BOMBS.forEach((target, bomb) -> {
                    if (target.level() instanceof ServerLevel level && findOwner(server, level, bomb.owner()) instanceof LivingEntity owner) {
                        showMarker(level, owner, getBombSpot(target), bomb.remote() ? REMOTE_MARKER : BOMB_MARKER, 1);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MINES.clear();
        BOMBS.clear();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, String key) {
        fail(entity, Component.translatable(key));
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

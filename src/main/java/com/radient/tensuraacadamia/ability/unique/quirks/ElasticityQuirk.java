package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ElasticityQuirk extends Skill {
    private static final int MAX_MASTERY = 2_500;
    private static final int SANDWICH_UNLOCK_MASTERY = 1_250;
    private static final int SURFACE_TICKS = 100;
    private static final int BOUNCE_TICKS = 1_200;
    private static final int WALL_TICKS = 100;
    private static final int SANDWICH_LIFETIME = 15;
    private static final int BOUNCE_PAD_TICKS = 60;
    private static final int STUN_TICKS = 300;
    private static final float SANDWICH_DAMAGE = 20.0F;
    private static final float MASTERED_SANDWICH_DAMAGE = 30.0F;
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/elasticityplaceholder.png");
    private static final Map<UUID, ElasticGround> GROUNDS = new HashMap<>();
    private static final Map<UUID, ElasticWall> WALLS = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>> SURFACE_CONTACTS = new HashMap<>();
    private static final Map<UUID, ReflectedProjectile> REFLECTED = new HashMap<>();
    private static final Map<UUID, SandwichCast> SANDWICH_CASTS = new HashMap<>();
    private static final Map<UUID, BounceDash> BOUNCE_DASHES = new HashMap<>();
    private static final Map<UUID, DirectionalPad> BOUNCE_PADS = new HashMap<>();

    private record ElasticGround(ServerLevel level, AABB bounds, int layers, long expiresAt) { }
    private record ElasticWall(UUID ownerId, ServerLevel level, Vec3 center, Vec3 forward, Vec3 right, long expiresAt) { }
    private record DirectionalPad(ServerLevel level, Vec3 center, Vec3 direction, Vec3 right, Vec3 up, long expiresAt) { }
    private record ReflectedProjectile(UUID ownerId, long expiresAt) { }
    private static final class BounceDash {
        final ServerPlayer owner;
        final float damage;
        final long expiresAt;
        final Map<UUID, Boolean> hitTargets = new HashMap<>();

        BounceDash(ServerPlayer owner, float damage, long expiresAt) {
            this.owner = owner;
            this.damage = damage;
            this.expiresAt = expiresAt;
        }
    }
    private static final class SandwichCast {
        final ServerPlayer owner;
        final boolean mastered;
        final UUID targetId;
        final Vec3 center;
        final long expiresAt;

        SandwichCast(ServerPlayer owner, boolean mastered, UUID targetId, Vec3 center, long expiresAt) {
            this.owner = owner;
            this.mastered = mastered;
            this.targetId = targetId;
            this.center = center;
            this.expiresAt = expiresAt;
        }
    }

    public ElasticityQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override public @Nullable ResourceLocation getSkillIcon() { return ICON; }
    @Override public int getMaxMastery() { return MAX_MASTERY; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 300_000.0D; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) { return true; }
    @Override public boolean canTick(ManasSkillInstance instance, LivingEntity entity) { return true; }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= SANDWICH_UNLOCK_MASTERY ? 3 : 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "elasticity.gently_rebound";
            case 1 -> "elasticity.gently_trampoline";
            case 2 -> "elasticity.gently_sandwich";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> 250.0D;
            case 1 -> 100.0D;
            case 2 -> 500.0D;
            default -> 0.0D;
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode < 0 || mode >= getModes(instance)
                || instance.onCoolDown(mode)) return;
        double cost = getAuraCost(player, instance, mode);
        if (!payAura(player, cost)) return;
        boolean used = switch (mode) {
            case 0 -> gentlyRebound(player, instance.isMastered(player));
            case 1 -> gentlyTrampoline(player, instance.isMastered(player));
            case 2 -> gentlySandwich(player, instance.isMastered(player));
            default -> false;
        };
        if (!used) {
            refundAura(player, cost);
            return;
        }
        instance.addMasteryPoint(player);
        instance.setCoolDown(switch (mode) {
            case 0 -> 5;
            case 1 -> 3;
            default -> 10;
        }, mode);
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        return true;
    }

    private static boolean gentlyRebound(ServerPlayer player, boolean mastered) {
        ServerLevel level = player.serverLevel();
        Vec3 forward = horizontal(player.getLookAngle());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 center = player.getEyePosition().add(forward.scale(2.0D));
        ElasticWall wall = new ElasticWall(player.getUUID(), level, center, forward, right,
                level.getGameTime() + WALL_TICKS);
        WALLS.put(player.getUUID(), wall);
        emitWall(wall);
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.9F, 1.4F);
        return true;
    }

    private static boolean bounceDash(ServerPlayer player) {
        if (!player.hasEffect(MHAEffects.BOUNCE)) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.elasticity.no_bounce"), true);
            return false;
        }
        Vec3 direction = player.getLookAngle().normalize();
        double speed = Math.max(1.4D, player.getDeltaMovement().length() + 1.2D);
        Vec3 origin = player.position().add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
        player.setDeltaMovement(direction.scale(speed));
        player.hurtMarked = true;
        player.resetFallDistance();
        consumeBounce(player);
        UUID padId = createDirectionalPad(player.serverLevel(), origin, direction);
        markSurfaceUse(padId, player, player.serverLevel().getGameTime() + 4L);
        BOUNCE_DASHES.put(player.getUUID(), new BounceDash(player, (float) (speed * 10.0D),
                player.serverLevel().getGameTime() + 60L));
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ(), 10,
                0.5D, 0.7D, 0.5D, 0.08D);
        return true;
    }

    private static boolean gentlyTrampoline(ServerPlayer player, boolean mastered) {
        createGround(player);
        grantBounce(player, 2);
        double launch = 0.42D * (mastered ? 4.0D : 3.0D);
        player.setDeltaMovement(player.getDeltaMovement().x, launch, player.getDeltaMovement().z);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_FALL,
                SoundSource.PLAYERS, 1.0F, mastered ? 1.25F : 1.0F);
        return true;
    }

    private static boolean gentlySandwich(ServerPlayer player, boolean mastered) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = lookedAtTarget(player, 30.0D);
        Vec3 center = target == null ? player.getEyePosition().add(player.getLookAngle().normalize().scale(12.0D))
                : target.position();
        SANDWICH_CASTS.put(player.getUUID(), new SandwichCast(player, mastered,
                target == null ? null : target.getUUID(), center, level.getGameTime() + SANDWICH_LIFETIME));
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 1.35F);
        return true;
    }

    private static void createGround(LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        createGroundAt(level, owner.blockPosition().below(), 1);
    }

    private static void createGroundAt(ServerLevel level, BlockPos below, int layers) {
        AABB bounds = new AABB(below.getX() - 2.0D, below.getY() + 0.88D, below.getZ() - 2.0D,
                below.getX() + 3.0D, below.getY() + 1.16D, below.getZ() + 3.0D);
        ElasticGround ground = new ElasticGround(level, bounds, layers, level.getGameTime() + SURFACE_TICKS);
        GROUNDS.put(UUID.randomUUID(), ground);
        emitGround(ground);
    }

    private static UUID createDirectionalPad(ServerLevel level, Vec3 center, Vec3 direction) {
        Vec3 normalizedDirection = direction.normalize();
        Vec3 right = normalizedDirection.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-5D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up = right.cross(normalizedDirection).normalize();
        DirectionalPad pad = new DirectionalPad(level, center, normalizedDirection, right, up,
                level.getGameTime() + BOUNCE_PAD_TICKS);
        UUID padId = UUID.randomUUID();
        BOUNCE_PADS.put(padId, pad);
        emitDirectionalPad(pad);
        return padId;
    }

    private static boolean payAura(ServerPlayer player, double cost) {
        var existence = TensuraStorages.getExistenceFrom(player);
        if (existence.getAura() < cost) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.elasticity.not_enough_aura"), true);
            return false;
        }
        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        return true;
    }

    private static void refundAura(ServerPlayer player, double cost) {
        var existence = TensuraStorages.getExistenceFrom(player);
        existence.setAura(existence.getAura() + cost);
        existence.markDirty();
    }

    private static Vec3 horizontal(Vec3 vector) {
        Vec3 horizontal = new Vec3(vector.x, 0.0D, vector.z);
        return horizontal.lengthSqr() < 1.0E-5D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
    }

    private static boolean slottedElasticity(LivingEntity entity) {
        return SkillUtils.hasSkill(entity, QuirkSkills.ELASTICITY.get())
                && TensuraStorages.getAbilityFrom(entity).isAbilityInActivePreset(QuirkSkills.ELASTICITY.get());
    }

    private static void grantBounce(LivingEntity entity, int stacks) {
        MobEffectInstance current = entity.getEffect(MHAEffects.BOUNCE);
        int existingStacks = current == null ? 0 : current.getAmplifier() + 1;
        int totalStacks = Math.max(existingStacks, Math.clamp(stacks, 1, 2));
        entity.addEffect(new MobEffectInstance(MHAEffects.BOUNCE, BOUNCE_TICKS, totalStacks - 1,
                false, true, true));
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), 8,
                    0.45D, 0.65D, 0.45D, 0.03D);
        }
    }

    private static void consumeBounce(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(MHAEffects.BOUNCE);
        if (effect == null) return;
        int remainingStacks = effect.getAmplifier();
        int remainingDuration = effect.getDuration();
        entity.removeEffect(MHAEffects.BOUNCE);
        if (remainingStacks > 0) entity.addEffect(new MobEffectInstance(MHAEffects.BOUNCE, remainingDuration,
                remainingStacks - 1, false, true, true));
    }

    public static void tryAerialBounce(ServerPlayer player) {
        if (player.onGround() || player.isInWaterOrBubble() || player.getAbilities().flying) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(player)
                .getSkill(QuirkSkills.ELASTICITY.get().getRegistryName()).orElse(null);
        if (instance == null || !instance.isToggled()) return;
        bounceDash(player);
    }

    private static void damage(ServerLevel level, LivingEntity owner, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().source(DamageTypes.WIND_CHARGE, owner), amount);
    }

    @SubscribeEvent
    public static void onJump(LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !SkillUtils.hasSkill(entity, QuirkSkills.ELASTICITY.get())) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(entity)
                .getSkill(QuirkSkills.ELASTICITY.get().getRegistryName()).orElse(null);
        if (instance == null || !instance.isToggled()) return;
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.x, Math.max(motion.y, 0.84D), motion.z);
        entity.hurtMarked = true;
        createGround(entity);
        grantBounce(entity, 2);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide || !slottedElasticity(defender)) return;
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile) {
            Entity attacker = source.getEntity();
            if (attacker == null || attacker == defender) return;
            Vec3 direction = attacker.getEyePosition().subtract(projectile.position()).normalize();
            projectile.setDeltaMovement(direction.scale(Math.max(0.8D, projectile.getDeltaMovement().length() * 2.0D)));
            projectile.setOwner(defender);
            projectile.hurtMarked = true;
            REFLECTED.put(projectile.getUUID(), new ReflectedProjectile(defender.getUUID(), defender.level().getGameTime() + 100));
            event.setCanceled(true);
            return;
        }
        if (source.getEntity() instanceof LivingEntity attacker && attacker != defender
                && TensuraDamageHelper.isPhysicalAttack(source) && defender.getRandom().nextBoolean()) {
            Vec3 push = attacker.position().subtract(defender.position()).normalize();
            attacker.push(push.x * 2.0D, 0.5D, push.z * 2.0D);
            attacker.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void doubleReflectedProjectileDamage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Projectile projectile) || !REFLECTED.containsKey(projectile.getUUID())) return;
        event.setAmount(event.getAmount() * 2.0F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        Iterator<Map.Entry<UUID, ElasticGround>> groundIterator = GROUNDS.entrySet().iterator();
        while (groundIterator.hasNext()) {
            Map.Entry<UUID, ElasticGround> groundEntry = groundIterator.next();
            UUID groundId = groundEntry.getKey();
            ElasticGround ground = groundEntry.getValue();
            if (now >= ground.expiresAt()) {
                groundIterator.remove();
                continue;
            }
            if (now % 5L == 0L) emitGround(ground);
            for (LivingEntity entity : ground.level().getEntitiesOfClass(LivingEntity.class, ground.bounds().inflate(5.0D),
                    target -> target.isAlive() && hitsGround(ground, target))) {
                if (canUseSurface(groundId, entity, now)) {
                    grantBounce(entity, 1);
                    markSurfaceUse(groundId, entity, now + 10L);
                }
            }
        }
        Iterator<ElasticWall> wallIterator = WALLS.values().iterator();
        while (wallIterator.hasNext()) {
            ElasticWall wall = wallIterator.next();
            if (now >= wall.expiresAt()) {
                wallIterator.remove();
                continue;
            }
            if (now % 4L == 0L) emitWall(wall);
            AABB search = new AABB(wall.center().subtract(6.0D, 4.0D, 6.0D), wall.center().add(6.0D, 4.0D, 6.0D));
            for (Entity entity : wall.level().getEntitiesOfClass(Entity.class, search, Entity::isAlive)) {
                if (!hitsWall(wall, entity) || !canUseSurface(wall.ownerId(), entity, now)) continue;
                Vec3 velocity = entity.getDeltaMovement();
                if (velocity.lengthSqr() < 1.0E-4D) continue;
                Vec3 previous = entity.position().subtract(velocity);
                double previousSide = previous.subtract(wall.center()).dot(wall.forward());
                Vec3 reflected = velocity.subtract(wall.forward().scale(2.0D * velocity.dot(wall.forward())));
                if (reflected.lengthSqr() < 1.0E-4D) {
                    reflected = wall.forward().scale(previousSide >= 0.0D ? 1.0D : -1.0D);
                }
                reflected = reflected.normalize().scale(Math.max(2.5D, velocity.length() * 2.4D));
                double currentSide = entity.position().subtract(wall.center()).dot(wall.forward());
                double reboundSide = previousSide >= 0.0D ? 1.1D : -1.1D;
                entity.setPos(entity.getX() - wall.forward().x * (currentSide - reboundSide), entity.getY(),
                        entity.getZ() - wall.forward().z * (currentSide - reboundSide));
                entity.setDeltaMovement(reflected);
                entity.hurtMarked = true;
                markSurfaceUse(wall.ownerId(), entity, now + 5L);
                if (entity instanceof LivingEntity living) grantBounce(living, 1);
            }
        }
        Iterator<Map.Entry<UUID, DirectionalPad>> padIterator = BOUNCE_PADS.entrySet().iterator();
        while (padIterator.hasNext()) {
            Map.Entry<UUID, DirectionalPad> padEntry = padIterator.next();
            UUID padId = padEntry.getKey();
            DirectionalPad pad = padEntry.getValue();
            if (now >= pad.expiresAt()) {
                padIterator.remove();
                continue;
            }
            if (now % 3L == 0L) emitDirectionalPad(pad);
            AABB search = new AABB(pad.center().subtract(5.0D, 5.0D, 5.0D), pad.center().add(5.0D, 5.0D, 5.0D));
            for (LivingEntity entity : pad.level().getEntitiesOfClass(LivingEntity.class, search,
                    target -> target.isAlive() && hitsDirectionalPad(pad, target))) {
                if (!canUseSurface(padId, entity, now)) continue;
                double speed = Math.max(1.8D, entity.getDeltaMovement().length() + 1.0D);
                entity.setDeltaMovement(pad.direction().scale(speed));
                entity.hurtMarked = true;
                entity.resetFallDistance();
                grantBounce(entity, 1);
                markSurfaceUse(padId, entity, now + 10L);
            }
        }
        Iterator<BounceDash> dashIterator = BOUNCE_DASHES.values().iterator();
        while (dashIterator.hasNext()) {
            BounceDash dash = dashIterator.next();
            if (!dash.owner.isAlive() || now >= dash.expiresAt) {
                dashIterator.remove();
                continue;
            }
            AABB hitbox = dash.owner.getBoundingBox().inflate(1.0D).expandTowards(dash.owner.getDeltaMovement());
            for (LivingEntity target : dash.owner.serverLevel().getEntitiesOfClass(LivingEntity.class, hitbox,
                    entity -> entity != dash.owner && entity.isAlive() && !dash.hitTargets.containsKey(entity.getUUID()))) {
                damage(dash.owner.serverLevel(), dash.owner, target, dash.damage);
                Vec3 push = target.position().subtract(dash.owner.position()).normalize();
                target.push(push.x * 1.5D, 0.35D, push.z * 1.5D);
                target.hurtMarked = true;
                dash.hitTargets.put(target.getUUID(), Boolean.TRUE);
            }
        }
        Iterator<SandwichCast> sandwichIterator = SANDWICH_CASTS.values().iterator();
        while (sandwichIterator.hasNext()) {
            SandwichCast cast = sandwichIterator.next();
            if (!cast.owner.isAlive() || cast.owner.serverLevel() != cast.owner.level()) {
                sandwichIterator.remove();
                continue;
            }
            Vec3 center = sandwichCenter(cast);
            if (now < cast.expiresAt) {
                emitSandwichLayers(cast.owner.serverLevel(), center, now, cast.expiresAt);
                continue;
            }
            ServerLevel level = cast.owner.serverLevel();
            emitFilledDisc(level, center.add(0.0D, 1.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, 1.0D), 2.5D);
            level.playSound(null, BlockPos.containing(center), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.5F, 0.65F);
            boolean hitEnemy = false;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center, center).inflate(2.5D, 2.0D, 2.5D), entity -> entity != cast.owner && entity.isAlive())) {
                hitEnemy = true;
                float damage = cast.mastered ? MASTERED_SANDWICH_DAMAGE : SANDWICH_DAMAGE;
                damage(level, cast.owner, target, damage);
                damage(level, cast.owner, target, damage);
                damage(level, cast.owner, target, damage);
                target.addEffect(new MobEffectInstance(io.github.manasmods.tensura.registry.effect.TensuraMobEffects
                        .getReference(io.github.manasmods.tensura.registry.effect.TensuraMobEffects.PARALYSIS), STUN_TICKS, 0));
            }
            if (hitEnemy) createGroundAt(level, BlockPos.containing(center).below(), 3);
            sandwichIterator.remove();
        }
        REFLECTED.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt());
        SURFACE_CONTACTS.values().forEach(contacts -> contacts.entrySet().removeIf(entry -> now >= entry.getValue()));
        SURFACE_CONTACTS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static LivingEntity lookedAtTarget(ServerPlayer player, double range) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), entity -> entity != player && entity.isAlive())) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(origin);
            double distance = offset.length();
            if (distance > range || distance == 0.0D || offset.scale(1.0D / distance).dot(look) < 0.96D) continue;
            if (distance < bestDistance) {
                best = target;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static Vec3 sandwichCenter(SandwichCast cast) {
        Entity target = cast.targetId == null ? null : cast.owner.serverLevel().getEntity(cast.targetId);
        return target instanceof LivingEntity living && living.isAlive() ? living.position() : cast.center;
    }

    private static void emitGround(ElasticGround ground) {
        Vec3 center = ground.bounds().getCenter();
        for (int layer = 0; layer < ground.layers(); layer++) {
            emitFilledDisc(ground.level(), center.add(0.0D, layer * 0.12D, 0.0D),
                    new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D), 2.35D);
        }
    }

    private static void emitWall(ElasticWall wall) {
        emitFilledDisc(wall.level(), wall.center(), wall.right(), new Vec3(0.0D, 1.0D, 0.0D), 2.35D);
    }

    private static void emitDirectionalPad(DirectionalPad pad) {
        emitFilledDisc(pad.level(), pad.center(), pad.right(), pad.up(), 2.25D);
    }

    private static void emitSandwichLayers(ServerLevel level, Vec3 center, long now, long expiresAt) {
        double progress = 1.0D - Math.clamp((expiresAt - now) / (double) SANDWICH_LIFETIME, 0.0D, 1.0D);
        double fall = progress < 0.45D
                ? 0.15D * Math.pow(progress / 0.45D, 2.0D)
                : 0.15D + 0.85D * Math.pow((progress - 0.45D) / 0.55D, 0.55D);
        for (int layer = 0; layer < 3; layer++) {
            double height = (7.0D - layer * 2.0D) * (1.0D - fall);
            emitFilledDisc(level, center.add(0.0D, height, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
                    new Vec3(0.0D, 0.0D, 1.0D), 2.4D);
        }
    }

    private static void emitFilledDisc(ServerLevel level, Vec3 center, Vec3 right, Vec3 up, double radius) {
        for (int point = 0; point < 12; point++) {
            double angle = Math.PI * 2.0D * point / 12.0D;
            sendAirParticle(level, center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius)));
        }
        for (int point = 0; point < 6; point++) {
            double angle = Math.PI * 2.0D * point / 6.0D + Math.PI / 6.0D;
            sendAirParticle(level, center.add(right.scale(Math.cos(angle) * radius * 0.5D))
                    .add(up.scale(Math.sin(angle) * radius * 0.5D)));
        }
        sendAirParticle(level, center);
    }

    private static void sendAirParticle(ServerLevel level, Vec3 position) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                position.x, position.y, position.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static boolean insideWall(ElasticWall wall, Vec3 point) {
        Vec3 relative = point.subtract(wall.center());
        return Math.abs(relative.dot(wall.forward())) <= 1.1D
                && Math.abs(relative.dot(wall.right())) <= 2.7D
                && Math.abs(relative.y) <= 2.7D;
    }

    private static boolean hitsWall(ElasticWall wall, Entity entity) {
        Vec3 current = entity.position();
        if (insideWall(wall, current)) return true;
        Vec3 previous = current.subtract(entity.getDeltaMovement());
        Vec3 middle = previous.lerp(current, 0.5D);
        double previousSide = previous.subtract(wall.center()).dot(wall.forward());
        double currentSide = current.subtract(wall.center()).dot(wall.forward());
        return previousSide * currentSide <= 0.0D && Math.abs(middle.subtract(wall.center()).dot(wall.right())) <= 2.9D
                && Math.abs(middle.y - wall.center().y) <= 2.9D;
    }

    private static boolean hitsGround(ElasticGround ground, LivingEntity entity) {
        if (entity.getBoundingBox().intersects(ground.bounds())) return true;
        Vec3 current = entity.position();
        Vec3 previous = current.subtract(entity.getDeltaMovement());
        AABB travel = new AABB(Math.min(previous.x, current.x), Math.min(previous.y, current.y),
                Math.min(previous.z, current.z), Math.max(previous.x, current.x), Math.max(previous.y, current.y),
                Math.max(previous.z, current.z)).inflate(entity.getBbWidth() * 0.5D, entity.getBbHeight(),
                entity.getBbWidth() * 0.5D);
        return travel.intersects(ground.bounds());
    }

    private static boolean hitsDirectionalPad(DirectionalPad pad, LivingEntity entity) {
        Vec3 current = entity.position();
        if (insideDirectionalPad(pad, current)) return true;
        Vec3 previous = current.subtract(entity.getDeltaMovement());
        Vec3 previousRelative = previous.subtract(pad.center());
        Vec3 currentRelative = current.subtract(pad.center());
        double previousDepth = previousRelative.dot(pad.direction());
        double currentDepth = currentRelative.dot(pad.direction());
        if (previousDepth * currentDepth > 0.0D) return false;
        double travelledDepth = currentDepth - previousDepth;
        if (Math.abs(travelledDepth) < 1.0E-5D) return false;
        Vec3 contact = previous.lerp(current, Math.clamp(-previousDepth / travelledDepth, 0.0D, 1.0D));
        Vec3 relative = contact.subtract(pad.center());
        double horizontalAllowance = 2.4D + entity.getBbWidth() * 0.5D;
        return Math.abs(relative.dot(pad.right())) <= horizontalAllowance
                && Math.abs(relative.dot(pad.up())) <= 2.4D + entity.getBbHeight() * 0.5D;
    }

    private static boolean canUseSurface(UUID surfaceId, Entity entity, long now) {
        return SURFACE_CONTACTS.getOrDefault(surfaceId, Map.of()).getOrDefault(entity.getUUID(), 0L) <= now;
    }

    private static void markSurfaceUse(UUID surfaceId, Entity entity, long expiresAt) {
        SURFACE_CONTACTS.computeIfAbsent(surfaceId, ignored -> new HashMap<>()).put(entity.getUUID(), expiresAt);
    }

    private static boolean insideDirectionalPad(DirectionalPad pad, Vec3 point) {
        Vec3 relative = point.subtract(pad.center());
        return Math.abs(relative.dot(pad.direction())) <= 0.85D
                && Math.abs(relative.dot(pad.right())) <= 2.4D
                && Math.abs(relative.dot(pad.up())) <= 2.4D;
    }
}

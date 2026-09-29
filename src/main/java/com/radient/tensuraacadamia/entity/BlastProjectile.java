package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.ability.unique.quirks.HomingQuirk;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.projectile.magic.AuraBulletProjectile;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

public final class BlastProjectile extends AuraBulletProjectile {
    private boolean mastered;
    private final HashSet<UUID> hitTargets = new HashSet<>();

    public BlastProjectile(EntityType<? extends BlastProjectile> type, Level level) {
        super(type, level);
        setLife(100);
        setSpeed(2);
        setColor(0xFFE83B);
        setElement(Element.FLAME);
        setElementalAttack(true);
        setExplosionRadius(0);
        setHitRadius(0);
        setIgnoreInvulnerabilityOnHit(true);
        setNoGravity(true);
        configure(0, false);
    }
    public void configure(int mode, boolean mastered) {
        this.mastered = mastered;
        setMode(mode);
        setSize(mode == 2 ? 4 : mode == 1 ? 0.15F : 0.3F);
        setDamage(mode == 2 ? 500 : mastered ? 15 : 10);
        setPiercingEntity(mode == 2);
    }
    public double impactSize() { return getMode() == 2 ? 4 : getMode() == 1 ? 1 : mastered ? 3 : 2; }
    @Override public ResourceKey<DamageType> getDamageType() { return TensuraDamageTypes.HEAT_WAVE; }
    @Override protected AABB makeBoundingBox() {
        double size = getDimensions(getPose()).width();
        return AABB.ofSize(position(), size, size, size);
    }
    @Override protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target instanceof LivingEntity living
                && getOwner() instanceof LivingEntity owner && HomingQuirk.isEnemy(owner, living)
                && !hitTargets.contains(target.getUUID());
    }
    @Override protected void onHitEntity(EntityHitResult hit, EntityEvents.ProjectileHitResult result) {
        if (result == EntityEvents.ProjectileHitResult.PASS || level().isClientSide) return;
        if (result != EntityEvents.ProjectileHitResult.HIT_NO_DAMAGE) {
            damageOnce(hit.getEntity(), result);
            impactArea(hit.getEntity().getBoundingBox().getCenter());
        }
        impact(hit.getEntity().getBoundingBox().getCenter());
        discard();
    }
    @Override protected void onHitBlock(BlockHitResult hit) {
        if (level().isClientSide) return;
        impactArea(hit.getLocation());
        impact(hit.getLocation());
        discard();
    }
    private void impactArea(Vec3 point) {
        double size = impactSize();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(point, size, size, size), this::canHitEntity))
            damageOnce(target, EntityEvents.ProjectileHitResult.DEFAULT);
    }
    private void damageOnce(Entity target, EntityEvents.ProjectileHitResult result) {
        if (result == EntityEvents.ProjectileHitResult.PASS || !hitTargets.add(target.getUUID())) return;
        if (result != EntityEvents.ProjectileHitResult.HIT_NO_DAMAGE) target.invulnerableTime = 0;
        hitEntity(target, result);
    }
    private void impact(Vec3 point) {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLASH, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            server.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, getMode() == 1 ? 3 : 12, 0.3, 0.3, 0.3, 0.03);
            server.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    getMode() == 1 ? 0.2F : 0.6F, getMode() == 2 ? 0.65F : 1.8F);
        }
    }

    @Override public void tickHandler() {
        if (getMode() != 2) { super.tickHandler(); return; }
        if (!(level() instanceof ServerLevel server)) { updateRotation(); updateMovement(); return; }
        Vec3 start = position(), velocity = getDeltaMovement();
        int steps = Math.max(1, Mth.ceil(velocity.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = start.add(velocity.scale((double) i / steps));
            AABB area = AABB.ofSize(point, 4, 4, 4);
            if (!clearPath(server, area)) { setPos(point); impact(point); discard(); return; }
            for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, this::canHitEntity)) {
                var result = Changeable.of(EntityEvents.ProjectileHitResult.DEFAULT);
                var deflection = Changeable.of(ProjectileDeflection.NONE);
                EntityEvents.PROJECTILE_HIT.invoker().hit(new EntityHitResult(target), this, deflection, result);
                if (deflection.get() != ProjectileDeflection.NONE) {
                    setPos(point);
                    deflect(deflection.get(), target, target, true);
                    return;
                }
                damageOnce(target, result.get());
            }
        }
        setPos(start.add(velocity));
        updateRotation();
    }
    private boolean clearPath(ServerLevel server, AABB area) {
        var breakable = new ArrayList<BlockPos>();
        double hardnessLimit = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Blast.maxBlockHardness;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(area.minX), Mth.floor(area.minY), Mth.floor(area.minZ),
                Mth.ceil(area.maxX) - 1, Mth.ceil(area.maxY) - 1, Mth.ceil(area.maxZ) - 1)) {
            if (!server.hasChunkAt(pos)) return false;
            var state = server.getBlockState(pos);
            if (state.isAir() || state.getCollisionShape(server, pos).isEmpty()) continue;
            if (state.getCollisionShape(server, pos).toAabbs().stream().noneMatch(box -> box.move(pos).intersects(area))) continue;
            float hardness = state.getDestroySpeed(server, pos);
            if (hardness < 0 || hardness > hardnessLimit || !shouldGrief()
                    || TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(getSkill(), server, getOwner(), pos.getX(), pos.getY(), pos.getZ()).isFalse())
                return false;
            breakable.add(pos.immutable());
        }
        for (BlockPos pos : breakable) if (!server.destroyBlock(pos, false, getOwner())) return false;
        return true;
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("BlastMastered", mastered);
        ListTag hits = new ListTag();
        hitTargets.forEach(id -> hits.add(NbtUtils.createUUID(id)));
        tag.put("BlastHits", hits);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        mastered = tag.getBoolean("BlastMastered");
        hitTargets.clear();
        for (var hit : tag.getList("BlastHits", IntArrayTag.TAG_INT_ARRAY)) hitTargets.add(NbtUtils.loadUUID(hit));
    }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ElectrificationQuirk extends Skill {
    private static final int DISCHARGE_TICKS = 40;
    private static final int TARGETED_BURST_TICKS = 12;
    private static final int WATTAGE_TICKS = 1200;
    private static final int CHARGE_LIFETIME_TICKS = 600;
    private static final float DISCHARGE_CAP = 40.0F;
    private static final float MASTERED_DISCHARGE_CAP = 60.0F;
    private static final float TARGETED_CAP = 30.0F;
    private static final float MASTERED_TARGETED_CAP = 40.0F;
    private static final ResourceKey<DamageType> DISCHARGE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("tracadamia", "electric_discharge"));
    private static final ResourceKey<DamageType> TARGETED_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("tracadamia", "electric_targeted"));
    private static final Map<UUID, Burst> BURSTS = new HashMap<>();
    private static final Map<UUID, ElectricCharge> CHARGES = new HashMap<>();

    private static final class ElectricCharge {
        final LivingEntity owner;
        Snowball projectile;
        Vec3 anchor;
        Entity attached;
        Vec3 attachmentOffset;
        int flightTicks;
        int stuckTicks;

        ElectricCharge(LivingEntity owner, Snowball projectile) {
            this.owner = owner;
            this.projectile = projectile;
        }

        Vec3 position() {
            return attached != null && attached.isAlive()
                    ? attached.position().add(attachmentOffset) : anchor;
        }
    }

    private static final class Burst {
        final LivingEntity owner;
        final Vec3 origin;
        final Vec3 target;
        final Vec3 forward;
        final Vec3 side;
        final boolean targeted;
        final boolean mastered;
        Vec3 previousPosition;
        int ticks;

        Burst(LivingEntity owner, Vec3 target, boolean targeted, boolean mastered) {
            this.owner = owner;
            this.origin = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
            this.target = target;
            this.forward = targeted ? target.subtract(this.origin) : Vec3.ZERO;
            this.side = targeted ? new Vec3(-forward.z, 0, forward.x).normalize() : Vec3.ZERO;
            this.targeted = targeted;
            this.mastered = mastered;
            this.previousPosition = this.origin;
        }
    }

    public ElectrificationQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/electrification.png");
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 50000.0;
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled() && entity.tickCount % 8 == 0 && entity.level() instanceof ServerLevel level) {
            level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), entity.getX(),
                    entity.getY() + 0.15, entity.getZ(), 1, 0.3, 0.1, 0.3, 0);
        }
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 2);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "electrification.discharge";
            case 1 -> "electrification.targeted_burst";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        if (!owner.level().isClientSide && instance.isToggled()
                && source.getDirectEntity() instanceof LivingEntity attacker && attacker != owner) {
            MobEffectInstance old = attacker.getEffect(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS));
            int amplifier = old == null ? 0 : Math.min(4, old.getAmplifier() + 1);
            attacker.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 40, amplifier));
            spark((ServerLevel) owner.level(), attacker.position().add(0, attacker.getBbHeight() * 0.5, 0), 12);
            electricSound(owner, 0.45F, 1.25F);
        }
        return true;
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                  DamageSource source, Changeable<Float> amount) {
        if (owner.hasEffect(MHAEffects.ELECTRIC_BOOST)
                && !source.is(DISCHARGE_DAMAGE) && !source.is(TARGETED_DAMAGE)) {
            amount.set(amount.get() * 1.5F);
        }
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (entity.level().isClientSide || mode < 0 || mode > 1) return;
        if (entity.hasEffect(TensuraMobEffects.getReference(TensuraMobEffects.ANTI_SKILL))) return;
        UUID id = entity.getUUID();
        ElectricCharge charge = CHARGES.get(id);
        if (mode == 1 && charge != null && charge.owner == entity
                && (charge.projectile != null && charge.projectile.isAlive() || charge.anchor != null)) {
            if (!payAura(entity) || !addWattage(entity, instance.isMastered(entity))) return;
            CHARGES.remove(id);
            Vec3 destination = charge.projectile != null ? charge.projectile.position() : charge.position();
            if (charge.projectile != null) charge.projectile.discard();
            BURSTS.put(id, new Burst(entity, destination, true, instance.isMastered(entity)));
            if (entity.level() instanceof ServerLevel level) electricBurst(level, destination, 18);
        } else {
            if (!payAura(entity) || (mode == 0 && !addWattage(entity, instance.isMastered(entity)))) return;
            if (mode == 0) {
                BURSTS.put(id, new Burst(entity, entity.position(), false, instance.isMastered(entity)));
            } else {
                Snowball launched = new Snowball(entity.level(), entity);
                launched.setInvisible(true);
                launched.setPos(entity.getX(), entity.getEyeY() - 0.2, entity.getZ());
                launched.shootFromRotation(entity, entity.getXRot(), entity.getYRot(), 0, 1.7F, 0);
                entity.level().addFreshEntity(launched);
                CHARGES.put(id, new ElectricCharge(entity, launched));
                if (entity.level() instanceof ServerLevel level) electricBurst(level, launched.position(), 8);
            }
        }
        electricSound(entity, 0.8F, mode == 0 ? 0.95F : 1.15F);
        instance.addMasteryPoint(entity);
    }

    private static boolean payAura(LivingEntity entity) {
        double cost = Math.max(1.0, Math.ceil(EnergyHelper.getBaseMaxAura(entity) * 0.03));
        var existence = TensuraStorages.getExistenceFrom(entity);
        double available = existence.getAura();
        if (available < cost) {
            if (entity instanceof Player player) player.displayClientMessage(Component.literal("Not enough aura."), true);
            return false;
        }
        existence.setAura(available - cost);
        existence.markDirty();
        return true;
    }

    private static boolean addWattage(LivingEntity entity, boolean mastered) {
        MobEffectInstance current = entity.getEffect(MHAEffects.WATTAGE);
        int stacks = current == null ? 1 : current.getAmplifier() + 2;
        if (stacks > (mastered ? 15 : 10)) {
            entity.removeEffect(MHAEffects.WATTAGE);
            entity.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.ANTI_SKILL), 2400, 0));
            entity.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS), 1200, 0));
            return false;
        }
        entity.addEffect(new MobEffectInstance(MHAEffects.WATTAGE, WATTAGE_TICKS, stacks - 1));
        return true;
    }

    @SubscribeEvent
    public static void onLightningHit(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (victim.level().isClientSide || !SkillUtils.hasSkill(victim, QuirkSkills.ELECTRIFICATION.get())) return;
        if (!source.is(DamageTypeTags.IS_LIGHTNING) && !source.is(DamageTypes.LIGHTNING_BOLT)
                && !source.is(TensuraDamageTypes.LIGHTNING)
                && !source.is(TensuraDamageTypes.LIGHTNING_ELEMENTAL)
                && !source.is(TensuraDamageTypes.BLACK_LIGHTNING)
                && !source.is(TensuraDamageTypes.THUNDER_BREATH)) return;
        event.setCanceled(true);
        victim.addEffect(new MobEffectInstance(MHAEffects.ELECTRIC_BOOST, 200, 0));
        if (victim.level() instanceof ServerLevel level) spark(level, victim.position().add(0, 1, 0), 18);
    }

    @SubscribeEvent
    public static void capElectricDamage(LivingDamageEvent.Pre event) {
        DamageSource source = event.getSource();
        boolean discharge = source.is(DISCHARGE_DAMAGE);
        if (!discharge && !source.is(TARGETED_DAMAGE)) return;
        LivingEntity attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        ManasSkillInstance skill = attacker == null ? null : SkillAPI.getSkillsFrom(attacker)
                .getSkill(QuirkSkills.ELECTRIFICATION.get().getRegistryName()).orElse(null);
        boolean mastered = skill != null && skill.isMastered(attacker);
        float baseCap = damageCap(discharge, mastered);
        // Tensura applies Lightning Domination/Manipulation through LIGHTNING_BOOST.
        // Preserve that boost while still capping this skill's own damage.
        float lightningBoost = attacker == null ? 1.0F
                : Math.max(1.0F, (float) attacker.getAttributeValue(TensuraAttributes.LIGHTNING_BOOST));
        float cap = baseCap * lightningBoost;
        if (event.getNewDamage() > cap) event.setNewDamage(cap);
    }

    private static float damageCap(boolean discharge, boolean mastered) {
        return discharge ? (mastered ? MASTERED_DISCHARGE_CAP : DISCHARGE_CAP)
                : (mastered ? MASTERED_TARGETED_CAP : TARGETED_CAP);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball snowball) || snowball.level().isClientSide) return;
        for (ElectricCharge charge : CHARGES.values()) {
            if (charge.projectile != snowball) continue;
            HitResult hit = event.getRayTraceResult();
            charge.anchor = hit.getLocation();
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() != charge.owner) {
                charge.attached = entityHit.getEntity();
                charge.attachmentOffset = charge.anchor.subtract(charge.attached.position());
            }
            charge.projectile = null;
            charge.stuckTicks = 0;
            snowball.discard();
            event.setCanceled(true);
            if (snowball.level() instanceof ServerLevel level) {
                electricBurst(level, charge.anchor, 14);
                electricSoundAt(level, charge.anchor, 0.5F, 1.3F);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, ElectricCharge>> chargeIterator = CHARGES.entrySet().iterator();
        while (chargeIterator.hasNext()) {
            ElectricCharge charge = chargeIterator.next().getValue();
            if (!charge.owner.isAlive() || !(charge.owner.level() instanceof ServerLevel level)) {
                if (charge.projectile != null) charge.projectile.discard();
                chargeIterator.remove();
                continue;
            }
            if (charge.projectile != null) {
                if (!charge.projectile.isAlive()) {
                    chargeIterator.remove();
                    continue;
                }
                if (++charge.flightTicks > 100) {
                    charge.anchor = charge.projectile.position();
                    charge.projectile.discard();
                    charge.projectile = null;
                } else {
                    Vec3 point = charge.projectile.position();
                    spark(level, point, 3);
                    if (charge.flightTicks % 2 == 0) {
                        level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), point.x, point.y, point.z,
                                1, 0.12, 0.12, 0.12, 0);
                    }
                }
            }
            if (charge.projectile == null) {
                if (++charge.stuckTicks > CHARGE_LIFETIME_TICKS) {
                    chargeIterator.remove();
                    continue;
                }
                Vec3 point = charge.position();
                if (charge.stuckTicks % 3 == 0) {
                    level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), point.x, point.y, point.z,
                            2, 0.18, 0.18, 0.18, 0);
                    spark(level, point, 4);
                }
                if (charge.stuckTicks % 20 == 0) {
                    level.sendParticles(MHAParticles.ELECTRIC_FIELD.get(), point.x, point.y, point.z,
                            1, 0.0, 0.0, 0.0, 0);
                }
            }
        }
        Iterator<Map.Entry<UUID, Burst>> iterator = BURSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Burst burst = iterator.next().getValue();
            int duration = burst.targeted ? TARGETED_BURST_TICKS : DISCHARGE_TICKS;
            if (!burst.owner.isAlive() || burst.owner.hasEffect(TensuraMobEffects.getReference(TensuraMobEffects.ANTI_SKILL))
                    || burst.ticks >= duration) {
                iterator.remove();
                continue;
            }
            burst.ticks++;
            if (!(burst.owner.level() instanceof ServerLevel level)) continue;
            Vec3 center;
            double radius;
            double damage;
            double aura = TensuraStorages.getExistenceFrom(burst.owner).getAura();
            if (burst.targeted) {
                double travel = burst.ticks / (double) TARGETED_BURST_TICKS;
                center = burst.ticks == TARGETED_BURST_TICKS ? burst.target
                        : burst.origin.add(burst.forward.scale(travel)).add(burst.side.scale(Math.sin(burst.ticks * 2.1) * 0.4));
                radius = 0.7;
                damage = Math.max(0, Math.min(damageCap(false, burst.mastered), aura / 375.0));
                trail(level, burst.previousPosition, center);
                burst.previousPosition = center;
                if (burst.ticks == TARGETED_BURST_TICKS) {
                    electricBurst(level, center, 6);
                    electricSoundAt(level, center, 0.35F, 1.4F);
                }
            } else {
                center = burst.owner.position().add(0, burst.owner.getBbHeight() * 0.5, 0);
                radius = burst.mastered ? 10.0 : 7.5;
                damage = Math.max(0, Math.min(damageCap(true, burst.mastered), aura / 2500.0));
                dischargeVisuals(level, center, radius, burst.ticks);
            }
            if (damage <= 0) continue;
            AABB area = burst.targeted ? new AABB(center, center).inflate(radius)
                    : new AABB(center.x - radius, burst.owner.getY() - 3, center.z - radius,
                    center.x + radius, burst.owner.getY() + burst.owner.getBbHeight() + 3, center.z + radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    area, target -> target != burst.owner && target.isAlive())) {
                target.invulnerableTime = 0;
                ResourceKey<DamageType> type = burst.targeted ? TARGETED_DAMAGE : DISCHARGE_DAMAGE;
                DamageSource source = level.damageSources().source(type, burst.owner);
                if (burst.targeted) ((TensuraDamageSource) source).tensura$setResistanceBypassLevel(2.0F);
                if (target.hurt(source, (float) damage)) {
                    disruptSkills(target);
                }
            }
        }
    }

    private static void disruptSkills(LivingEntity target) {
        for (ManasSkillInstance skill : SkillAPI.getSkillsFrom(target).getLearnedSkills()) {
            boolean changed = false;
            for (int mode = 0; mode < skill.getCooldownList().size(); mode++) {
                if (skill.getCoolDown(mode) < 1) {
                    skill.setCoolDown(1, mode);
                    changed = true;
                }
            }
            if (changed) skill.markDirty();
        }
    }

    private static void spark(ServerLevel level, Vec3 pos, int count) {
        level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), pos.x, pos.y, pos.z,
                Math.max(1, (count + 3) / 4), 0.8, 0.8, 0.8, 0.12);
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        int points = Math.min(24, Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.35)));
        for (int i = 1; i <= points; i++) {
            Vec3 point = from.lerp(to, i / (double) points);
            level.sendParticles(MHAParticles.ELECTRIC_TRAIL.get(), point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
        }
    }

    private static void dischargeVisuals(ServerLevel level, Vec3 center, double radius, int tick) {
        if (tick % 5 != 1) return;
        int steps = (int) Math.ceil(radius * 2.0 / 2.5);
        int phase = tick / 5;
        BlockPos.MutableBlockPos groundPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x <= steps; x++) {
            for (int z = 0; z <= steps; z++) {
                if ((x + z + phase) % 2 != 0) continue;
                double px = center.x - radius + 2.0 * radius * x / steps;
                double pz = center.z - radius + 2.0 * radius * z / steps;
                level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), px, groundY(level, groundPos, px, pz, center.y), pz,
                        1, 0, 0, 0, 0);
            }
        }
        for (int ring = 1; ring <= 3; ring++) {
            double fraction = ring / 3.0;
            for (int point = 0; point < 8; point++) {
                double angle = 2.0 * Math.PI * point / 8.0 + phase * 0.13;
                double cosine = Math.cos(angle);
                double sine = Math.sin(angle);
                double scale = radius * fraction / Math.max(Math.abs(cosine), Math.abs(sine));
                double px = center.x + cosine * scale;
                double pz = center.z + sine * scale;
                double py = groundY(level, groundPos, px, pz, center.y)
                        + radius * 0.7 * Math.sqrt(1.0 - fraction * fraction);
                level.sendParticles(MHAParticles.ELECTRIC_FIELD.get(), px, py, pz,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private static double groundY(ServerLevel level, BlockPos.MutableBlockPos pos, double x, double z, double centerY) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int top = (int) Math.floor(centerY);
        for (int y = top; y >= top - 3; y--) {
            if (level.getBlockState(pos.set(blockX, y, blockZ)).blocksMotion()) return y + 1.05;
        }
        return top - 2.95;
    }

    private static void electricBurst(ServerLevel level, Vec3 point, int sparks) {
        spark(level, point, sparks);
        level.sendParticles(MHAParticles.ELECTRIC_FIELD.get(), point.x, point.y, point.z,
                2, 0.3, 0.3, 0.3, 0);
        level.sendParticles(MHAParticles.ELECTRIC_ARC.get(), point.x, point.y, point.z,
                5, 0.6, 0.6, 0.6, 0);
    }

    private static void electricSound(LivingEntity entity, float volume, float pitch) {
        entity.level().playSound(null, entity.blockPosition(), MHASounds.ELECTRIFICATION.get(),
                SoundSource.PLAYERS, volume * 0.5F, pitch);
    }

    private static void electricSoundAt(ServerLevel level, Vec3 point, float volume, float pitch) {
        level.playSound(null, point.x, point.y, point.z, MHASounds.ELECTRIFICATION.get(),
                SoundSource.PLAYERS, volume * 0.5F, pitch);
    }
}

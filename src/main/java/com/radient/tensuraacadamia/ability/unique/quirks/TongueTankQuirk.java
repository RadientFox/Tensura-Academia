package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Limbs;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.magic.barrier.BarrierEntity;
import io.github.manasmods.tensura.entity.template.TensuraPartEntity;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class TongueTankQuirk extends Skill {

    private static final QuirkSkillsConfig.TongueTank CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).TongueTank;

    private static final int TONGUE_TANK = 0;
    private static final int BARRAGE_SHOT = 1;
    private static final int POINT_BLANK = 2;
    private static final int MODES = 3;

    public static final String CHARGE_TAG = "tankCharge";
    private static final String POINT_BLANK_TAG = "pointBlankTarget";
    private static final int AMMO_TICKS = 4;
    private static final double MOUTH_DROP = 0.2D;
    private static final int BAR_TICKS = 4;
    private static final double DIVE_PITCH = 0.3D;
    private static final double MAX_DIVE_SPEED = 4.0D;
    private static final int DIVE_TIMEOUT = 200;
    private static final int SMOKE_TICKS = 20;
    private static final double BARRIER_SEARCH = 32.0D;
    private static final double POINT_BLANK_REACH = 6.0D;
    private static final double HAND_SIDE = 0.35D;
    private static final double SHOULDER_DROP = 0.3D;
    private static final double NECK = 0.15D;
    private static final double ARM_REACH = 0.55D;

    public static final SimpleParticleType FIERY_SHOCKWAVE = new SimpleParticleType(true);
    private static final ResourceLocation FIERY_SHOCKWAVE_ID = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "tongue_tank_shockwave");

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

    private record Blast(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {}

    private static @Nullable Blast blasting;

    private static final class Barrage {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private int shotsLeft;
        private long next;

        private Barrage(ManasSkillInstance instance, LivingEntity owner, int shotsLeft, long next) {
            this.instance = instance;
            this.owner = owner;
            this.shotsLeft = shotsLeft;
            this.next = next;
        }
    }

    private static final class Dive {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final ServerLevel level;
        private final long start;
        private Vec3 lastPos;
        private double speed;

        private Dive(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
            this.instance = instance;
            this.owner = owner;
            this.level = level;
            this.start = level.getGameTime();
            this.lastPos = owner.position();
        }
    }

    private record Smoke(ServerLevel level, Vec3 pos, double radius, long until) {}

    private static final List<Barrage> BARRAGES = new ArrayList<>();
    private static final List<Dive> DIVES = new ArrayList<>();
    private static final List<Smoke> SMOKES = new ArrayList<>();
    private static final Map<LivingEntity, Long> CHARGING = new WeakHashMap<>();

    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/tonguetank.png");
    }

    public TongueTankQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterEvent event) {
        event.register(Registries.PARTICLE_TYPE, FIERY_SHOCKWAVE_ID, () -> FIERY_SHOCKWAVE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case TONGUE_TANK -> CONFIG.auraCost;
            case BARRAGE_SHOT -> CONFIG.barrageAuraCost;
            case POINT_BLANK -> CONFIG.pointBlankAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case TONGUE_TANK -> "tongue_tank.tongue_tank";
            case BARRAGE_SHOT -> "tongue_tank.barrage_shot";
            case POINT_BLANK -> "tongue_tank.point_blank";
            default -> super.getModeId(instance, mode);
        };
    }

    public static Optional<ManasSkillInstance> getTongueTank(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.TONGUE_TANK.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    public static double getCharge(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : tag.getDouble(CHARGE_TAG);
    }

    private static void setCharge(ManasSkillInstance instance, double charge) {
        if (getCharge(instance) != charge) {
            instance.getOrCreateTag().putDouble(CHARGE_TAG, charge);
            instance.markDirty();
        }
    }

    private static double getMaxCharge(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isMastered(entity) ? CONFIG.maxChargeMastered : CONFIG.maxCharge;
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        BARRAGES.removeIf(barrage -> barrage.owner == entity);
        DIVES.removeIf(dive -> dive.owner == entity);
        MultiArms.clearHeld(instance, entity, POINT_BLANK_TAG);
    }


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
        if (!(entity instanceof Player) && isRegenerating(instance, entity)) {
            regenerate(instance, entity, 100);
        }

        CompoundTag tag = instance.getOrCreateTag();
        int time = tag.getInt("activatedTimes");
        if (time % BASE_CONFIG.Mastery.masteryActivateTime == 0) {
            instance.addMasteryPoint(entity);
        }
        tag.putInt("activatedTimes", time + 1);
    }

    // Ammo Regeneration
    // charges up on its own with mastery
    private static boolean isRegenerating(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled() && instance.isMastered(entity);
    }

    private static void regenerate(ManasSkillInstance instance, LivingEntity entity, int ticks) {
        double cap = Math.min(CONFIG.ammoCap, getMaxCharge(instance, entity));
        double charge = getCharge(instance);
        if (charge < cap) {
            setCharge(instance, Math.min(cap, charge + CONFIG.chargePerSecond * ticks / 20.0D));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % AMMO_TICKS != 0 || !player.isAlive()) {
            return;
        }

        Long held = CHARGING.get(player);
        if (held == null || player.level().getGameTime() - held > 1L) {
            getTongueTank(player).filter(instance -> isRegenerating(instance, player)).ifPresent(instance -> regenerate(instance, player, AMMO_TICKS));
        }
    }

    private static DamageSource pierce(ManasSkillInstance instance, DamageSource source) {
        if (instance.isToggled()) {
            TensuraDamageSource tensura = (TensuraDamageSource) source;
            tensura.tensura$setBarrierBypassLevel(Math.max(1.0F, tensura.tensura$getBarrierBypassLevel()));
        }
        return source;
    }

    public static boolean isBunkerBreaker(DamageSource source) {
        TensuraDamageSource tensura = (TensuraDamageSource) source;
        ManasSkillInstance instance = tensura.tensura$getAbilityInstance();
        return instance != null && instance.getSkill() == QuirkSkills.TONGUE_TANK.get() && tensura.tensura$getBarrierBypassLevel() >= 1.0F;
    }

    private static void breakBarriers(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
        Set<BarrierEntity> broken = new HashSet<>();
        for (BarrierEntity barrier : level.getEntitiesOfClass(BarrierEntity.class, new AABB(from, to).inflate(BARRIER_SEARCH))) {
            if (barrier.isRemoved() || broken.contains(barrier) || barrier.getOwner() instanceof LivingEntity caster && (caster == owner || isAlly(owner, caster))) {
                continue;
            }

            for (TensuraPartEntity part : barrier.getParts()) {
                if (part.getBoundingBox().clip(from, to).isPresent()) {
                    broken.add(barrier);
                    barrier.hurt(owner.damageSources().genericKill(), barrier.getHealth());
                    break;
                }
            }
        }
    }

    // Charging

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        if (mode == POINT_BLANK) {
            return holdPointBlank(level, instance, entity, heldTicks);
        }

        if (mode != TONGUE_TANK) {
            return false;
        }

        if (heldTicks == 0) {
            if (!isRegenerating(instance, entity)) {
                setCharge(instance, 0.0D);
            }
            if (EnergyHelper.isOutOfEnergy(entity, instance, TONGUE_TANK)) {
                return false;
            }
        }

        CHARGING.put(entity, level.getGameTime());

        double max = getMaxCharge(instance, entity);
        double charge = Math.min(max, getCharge(instance) + CONFIG.chargePerSecond / 20.0D);
        setCharge(instance, charge);

        if (heldTicks % BAR_TICKS == 0) {
            Vec3 mouth = getMouth(entity);
            level.sendParticles(ParticleTypes.SMALL_FLAME, mouth.x, mouth.y, mouth.z, 1 + (int) (charge / 20.0D), 0.1D, 0.1D, 0.1D, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 1, 0.1D, 0.1D, 0.1D, 0.01D);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.tongue_tank.charge", (int) charge, (int) max).withStyle(charge >= CONFIG.explosionCharge ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            }
        }

        if (heldTicks % 10 == 0 && charge < max) {
            playSound(level, entity.position(), SoundEvents.PUFFER_FISH_BLOW_UP, 0.8F, 0.6F + (float) (charge / max) * 0.8F);
        }

        if (heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == POINT_BLANK && entity.level() instanceof ServerLevel level) {
            releasePointBlank(level, instance, entity);
            return;
        }

        if (mode != TONGUE_TANK || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        Long held = CHARGING.remove(entity);
        if (held == null || level.getGameTime() - held > 2L) {
            return;
        }

        double charge = getCharge(instance);
        setCharge(instance, 0.0D);
        if (charge > 0.0D) {
            fire(level, instance, entity, charge);
            instance.setCoolDown(CONFIG.cooldown, TONGUE_TANK);
        }
    }

    // Barrage Shot

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode == POINT_BLANK && entity.level() instanceof ServerLevel level) {
            grab(level, instance, entity);
            return;
        }

        if (mode != BARRAGE_SHOT || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, BARRAGE_SHOT)) {
            return;
        }

        BARRAGES.removeIf(barrage -> barrage.owner == entity);
        fire(level, instance, entity, CONFIG.barrageCharge);
        if (CONFIG.barrageShots > 1) {
            BARRAGES.add(new Barrage(instance, entity, CONFIG.barrageShots - 1, level.getGameTime() + Math.max(1, CONFIG.barrageShotTicks)));
        }

        instance.setCoolDown(CONFIG.barrageCooldown, BARRAGE_SHOT);
    }

    private static boolean tickBarrage(Barrage barrage) {
        LivingEntity owner = barrage.owner;
        if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        if (level.getGameTime() < barrage.next) {
            return false;
        }

        fire(level, barrage.instance, owner, CONFIG.barrageCharge);
        barrage.next = level.getGameTime() + Math.max(1, CONFIG.barrageShotTicks);
        return --barrage.shotsLeft <= 0;
    }

    private static Vec3 getMouth(LivingEntity entity) {
        return entity.getEyePosition().add(0.0D, -MOUTH_DROP * entity.getScale(), 0.0D).add(entity.getLookAngle().scale(0.3D * entity.getScale()));
    }

    // Recoil, could be good movement

    private static void fire(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, double charge) {
        Vec3 look = entity.getLookAngle();
        Vec3 from = getMouth(entity);
        Vec3 end = from.add(look.scale(CONFIG.range));
        HitResult wall = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }

        Vec3 to = end;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(entity, from, to, new AABB(from, to).inflate(1.0D),
                other -> other instanceof LivingEntity living && other != entity && living.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(entity, living) && HitRules.canHit(entity, living), from.distanceToSqr(to));
        LivingEntity target = hit == null ? null : (LivingEntity) hit.getEntity();
        Vec3 impact = target == null ? to : target.getBoundingBox().getCenter();

        float damage = (float) (charge * CONFIG.damagePerCharge);
        if (instance.isToggled()) {
            breakBarriers(level, entity, from, impact);
        }

        if (target != null) {
            target.invulnerableTime = 0;
            DamageSource blast = pierce(instance, ((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, TONGUE_TANK));
            TensuraDamageHelper.hurtSplit(target, blast, (float) (1.0D - CONFIG.fireDamagePercent), pierce(instance, createFireSource(instance, entity)), damage);
            burn(target);
            SkillHelper.knockBack(target, entity, instance, look, 0.5D + charge * 0.01D, 0.0D, 0.2D);
            target.hurtMarked = true;
        }

        boolean exploded = charge >= CONFIG.explosionCharge;
        if (exploded) {
            explode(level, instance, entity, impact, getExplosionSize(charge), damage, target);
        }

        trail(level, from, look, from.distanceTo(impact), charge);
        level.sendParticles(FIERY_SHOCKWAVE, from.x, from.y, from.z, 0, look.x, look.y, look.z, 1.0D + charge / 50.0D);
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 6 + (int) (charge / 5.0D), 0.15D, 0.15D, 0.15D, 0.08D);
        level.sendParticles(MHAParticles.SMALL_ORANGE_EXPLOSION.get(), from.x, from.y, from.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        SMOKES.add(new Smoke(level, from, 0.3D, level.getGameTime() + SMOKE_TICKS / 2));

        if (!exploded) {
            double radius = 0.6D + charge / 100.0D;
            level.sendParticles(MHAParticles.ORANGE_EXPLOSION.get(), impact.x, impact.y, impact.z, 1 + (int) (charge / 25.0D), radius * 0.3D, radius * 0.3D, radius * 0.3D, 0.0D);
            debris(level, impact, getDebrisState(level, target == null ? wall : null, impact), radius, 8 + (int) (charge / 4.0D));
            SMOKES.add(new Smoke(level, impact, radius, level.getGameTime() + SMOKE_TICKS));
        }

        playSound(level, from, SoundEvents.GENERIC_EXPLODE.value(), (float) Math.min(2.0D, 0.5D + charge / 100.0D), (float) Math.max(0.7D, 1.6D - charge / 100.0D));
        playSound(level, from, SoundEvents.FIRECHARGE_USE, 1.2F, 0.7F);

        Vec3 recoil = look.reverse().scale(charge * CONFIG.recoilPerCharge);
        BruiserQuirk.launch(level, instance, entity, recoil, false);
        if (!entity.onGround() && recoil.y < -DIVE_PITCH * recoil.length()) {
            DIVES.removeIf(dive -> dive.owner == entity);
            DIVES.add(new Dive(instance, entity, level));
        }

        instance.addMasteryPoint(entity);
    }

    private static DamageSource createFireSource(ManasSkillInstance instance, LivingEntity owner) {
        DamageSource source = ((Skill) instance.getSkill()).createSource(instance, owner, TensuraDamageTypes.BURN, TONGUE_TANK);
        return ((TensuraDamageSource) source).tensura$setElement(Element.FLAME);
    }

    private static void burn(LivingEntity target) {
        if (CONFIG.burnSeconds > 0.0D && !target.fireImmune()) {
            target.igniteForSeconds((float) CONFIG.burnSeconds);
        }
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 look, double length, double charge) {
        double spread = 0.05D + charge / 400.0D;
        int flames = 1 + (int) (charge / 25.0D);
        for (double along = 0.5D; along < length; along += 0.5D) {
            Vec3 spot = from.add(look.scale(along));
            level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y, spot.z, flames, spread, spread, spread, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, spot.x, spot.y, spot.z, 1, spread, spread, spread, 0.01D);
        }

        for (double along = 1.0D; along < length; along += 2.0D) {
            Vec3 spot = from.add(look.scale(along));
            level.sendParticles(MHAParticles.SMALL_ORANGE_EXPLOSION.get(), spot.x, spot.y, spot.z, 1, spread * 0.5D, spread * 0.5D, spread * 0.5D, 0.0D);
        }
    }

    public static double getExplosionSize(double charge) {
        return CONFIG.explosionSize + Math.floor(Math.max(0.0D, charge - CONFIG.explosionCharge) / Math.max(0.01D, CONFIG.chargePerExplosionSize));
    }

    private static void explode(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, Vec3 center, double size, float damage, @Nullable LivingEntity skip) {
        double radius = size * 0.5D;
        DamageSource source = pierce(instance, ((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, TONGUE_TANK));
        blasting = new Blast(instance, owner, level);
        try {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    target -> target != owner && target != skip && target.isAlive() && !target.isSpectator() && HitRules.canHit(owner, target))) {
                target.invulnerableTime = 0;
                target.hurt(source, damage);
                burn(target);
                SkillHelper.knockBack(target, owner, instance, target.position().subtract(center), 0.4D + radius * 0.1D, 0.0D, 0.3D);
                target.hurtMarked = true;
            }

            if (CONFIG.explosionBreaksBlocks && GroundBlocks.canGrief(level, owner)) {
                level.explode(owner, null, BLOCKS_ONLY, center.x, center.y, center.z, (float) radius, false, Level.ExplosionInteraction.TNT);
            }
        } finally {
            blasting = null;
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(MHAParticles.ORANGE_EXPLOSION.get(), center.x, center.y, center.z, (int) size * 2, radius * 0.4D, radius * 0.3D, radius * 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, (int) size * 6, radius * 0.4D, radius * 0.3D, radius * 0.4D, 0.1D);
        debris(level, center, getDebrisState(level, null, center), radius, 10 + (int) size * 3);
        SMOKES.add(new Smoke(level, center, radius, level.getGameTime() + SMOKE_TICKS));
        playSound(level, center, SoundEvents.GENERIC_EXPLODE.value(), (float) Math.min(4.0D, 0.6D + radius * 0.3D), (float) Math.max(0.6D, 1.2D - radius * 0.05D));
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Blast blast = blasting;
        if (blast == null || event.getLevel() != blast.level()) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> !GroundBlocks.canBreak(blast.level(), blast.instance(), blast.owner(), pos));
        event.getAffectedBlocks().forEach(pos -> GroundBlocks.griefed(blast.level(), blast.instance(), blast.owner(), pos));
    }

    // You can shoot yourself down to make a shockwave

    private static boolean tickDive(Dive dive) {
        LivingEntity owner = dive.owner;
        long age = dive.level.getGameTime() - dive.start;
        if (!owner.isAlive() || owner.isRemoved() || owner.level() != dive.level || age > DIVE_TIMEOUT || owner.isInLiquid() || owner.isFallFlying()
                || owner instanceof Player player && player.getAbilities().flying) {
            return true;
        }

        Vec3 pos = owner.position();
        dive.speed = Math.max(dive.speed, Math.min(MAX_DIVE_SPEED, pos.distanceTo(dive.lastPos)));
        dive.lastPos = pos;
        if (age <= 0L || !owner.onGround()) {
            return false;
        }

        if (dive.speed >= CONFIG.diveMinSpeed) {
            land(dive.level, dive.instance, owner, dive.speed);
        }
        return true;
    }

    private static void land(ServerLevel level, ManasSkillInstance instance, LivingEntity owner, double speed) {
        Vec3 feet = owner.position();
        BlockHitResult floor = level.clip(new ClipContext(feet.add(0.0D, 0.5D, 0.0D), feet.subtract(0.0D, 2.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        double groundY = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation().y : feet.y;
        double radius = Math.min(CONFIG.diveMaxRadius, speed * CONFIG.diveRadiusPerSpeed);
        float damage = (float) (speed * CONFIG.diveDamagePerSpeed);
        DamageSource source = pierce(instance, ((Skill) instance.getSkill()).createSource(instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, TONGUE_TANK));
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(radius, 2.0D, radius),
                other -> other != owner && other.isAlive() && !other.isSpectator() && !MultiArms.isHoldPair(owner, other) && HitRules.canHit(owner, other) && other.position().subtract(feet).horizontalDistance() <= radius)) {
            other.invulnerableTime = 0;
            other.hurt(source, damage);
            SkillHelper.knockBack(other, owner, instance, other.position().subtract(feet), 0.5D + radius * 0.08D, 0.0D, 0.4D);
            other.hurtMarked = true;
        }

        BlockPos below = BlockPos.containing(feet.x, groundY - 0.5D, feet.z);
        BlockState state = level.getBlockState(below);
        level.sendParticles(FIERY_SHOCKWAVE, feet.x, groundY + 0.1D, feet.z, 0, 0.0D, 1.0D, 0.0D, radius / 1.5D);
        level.sendParticles(MHAParticles.SMALL_ORANGE_EXPLOSION.get(), feet.x, groundY + 0.3D, feet.z, 3 + (int) radius, radius * 0.4D, 0.1D, radius * 0.4D, 0.0D);
        GroundBlocks.dust(level, below, state, 12 + (int) (radius * 4.0D));
        debris(level, new Vec3(feet.x, groundY + 0.2D, feet.z), state, radius, 10 + (int) (radius * 3.0D));
        SMOKES.add(new Smoke(level, new Vec3(feet.x, groundY + 0.3D, feet.z), radius * 0.6D, level.getGameTime() + SMOKE_TICKS));
        playSound(level, feet, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.5F, 0.8F);
        playSound(level, feet, SoundEvents.GENERIC_EXPLODE.value(), (float) Math.min(2.0D, 0.4D + radius * 0.15D), 1.1F);
        instance.addMasteryPoint(owner);
    }


    private static void grab(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        MultiArms.clearHeld(instance, entity, POINT_BLANK_TAG);
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.pointBlankRange * Math.max(1.0D, entity.getScale()));
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (!MultiArms.canGrab(entity, target)) {
            fail(entity, Component.translatable(MultiArms.getGrabFail(target)));
            return;
        }

        if (!MultiArms.isNoLarger(target, entity)) {
            fail(entity, Component.translatable("tracadamia.skill.tongue_tank.too_big"));
            return;
        }

        if (Limbs.isArmless(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.tongue_tank.no_arms"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, POINT_BLANK)) {
            return;
        }

        instance.getOrCreateTag().putUUID(POINT_BLANK_TAG, target.getUUID());
        instance.markDirty();
        SkillAPI.getSkillsFrom(entity).markDirty();
        entity.swing(getGrabArm(entity) == Limbs.getArm(entity, true) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        playSound(level, entity.position(), SoundEvents.FROG_TONGUE, 1.0F, 0.8F);
    }

    private static boolean holdPointBlank(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        LivingEntity target = MultiArms.getHeldTarget(instance, entity, POINT_BLANK_TAG);
        if (target == null) {
            releasePointBlank(level, instance, entity);
            return false;
        }

        double scale = entity.getScale();
        double side = getGrabArm(entity) == Limbs.LEFT_ARM ? -HAND_SIDE : HAND_SIDE;
        MultiArms.holdAt(entity, target, new Vec3(side * scale, -SHOULDER_DROP * scale - target.getEyeHeight() + NECK * target.getScale(),
                ARM_REACH * scale + target.getBbWidth() * 0.5D), MultiArms.HOLD_LOOK);
        if (heldTicks % BAR_TICKS == 0) {
            Vec3 mouth = getMouth(entity);
            level.sendParticles(ParticleTypes.SMALL_FLAME, mouth.x, mouth.y, mouth.z, 2, 0.1D, 0.1D, 0.1D, 0.01D);
            level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 1, 0.1D, 0.1D, 0.1D, 0.01D);
        }

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    private static void releasePointBlank(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        CompoundTag tag = instance.getTag();
        if (tag == null || !tag.hasUUID(POINT_BLANK_TAG)) {
            return;
        }

        Entity held = level.getEntity(tag.getUUID(POINT_BLANK_TAG));
        MultiArms.clearHeld(instance, entity, POINT_BLANK_TAG);
        instance.setCoolDown(CONFIG.pointBlankCooldown, POINT_BLANK);
        if (held instanceof LivingEntity target && target.isAlive() && target.distanceTo(entity) <= POINT_BLANK_REACH * Math.max(1.0D, entity.getScale())) {
            pointBlank(level, instance, entity, target);
        }
    }

    //some limb functionality
    public static int getGrabArm(LivingEntity entity) {
        int offhand = Limbs.getArm(entity, false);
        return Limbs.hasLimb(entity, offhand) ? offhand : Limbs.getArm(entity, true);
    }

    public static boolean isGrabbing(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.hasUUID(POINT_BLANK_TAG);
    }

    private static void pointBlank(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, LivingEntity target) {
        double charge = instance.isMastered(entity) ? CONFIG.pointBlankChargeMastered : CONFIG.pointBlankCharge;
        float damage = (float) (charge * CONFIG.damagePerCharge);
        Vec3 look = entity.getLookAngle();
        Vec3 mouth = getMouth(entity);
        Vec3 face = target.getEyePosition();

        target.invulnerableTime = 0;
        DamageSource blast = pierce(instance, ((Skill) instance.getSkill()).createSource(instance, entity, entity instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, POINT_BLANK));
        TensuraDamageHelper.hurtSplit(target, blast, (float) (1.0D - CONFIG.fireDamagePercent), pierce(instance, createFireSource(instance, entity)), damage);
        burn(target);
        SkillHelper.knockBack(target, entity, instance, look, 1.0D + charge * 0.02D, 0.0D, 0.3D);
        target.hurtMarked = true;
        if (charge >= CONFIG.explosionCharge) {
            explode(level, instance, entity, face, getExplosionSize(charge), damage, target);
        }

        level.sendParticles(FIERY_SHOCKWAVE, mouth.x, mouth.y, mouth.z, 0, look.x, look.y, look.z, 1.0D + charge / 50.0D);
        level.sendParticles(ParticleTypes.FLAME, face.x, face.y, face.z, 12 + (int) (charge / 4.0D), 0.25D, 0.25D, 0.25D, 0.12D);
        level.sendParticles(MHAParticles.ORANGE_EXPLOSION.get(), face.x, face.y, face.z, 2 + (int) (charge / 25.0D), 0.3D, 0.3D, 0.3D, 0.0D);
        SMOKES.add(new Smoke(level, face, 0.6D + charge / 100.0D, level.getGameTime() + SMOKE_TICKS));
        playSound(level, face, SoundEvents.GENERIC_EXPLODE.value(), (float) Math.min(2.0D, 0.6D + charge / 100.0D), 1.0F);
        playSound(level, mouth, SoundEvents.FIRECHARGE_USE, 1.2F, 0.6F);
        BruiserQuirk.launch(level, instance, entity, look.reverse().scale(charge * CONFIG.recoilPerCharge * 0.5D), false);
        instance.addMasteryPoint(entity);
    }

    // Smoke and debris

    private static BlockState getDebrisState(ServerLevel level, @Nullable HitResult wall, Vec3 impact) {
        if (wall instanceof BlockHitResult blockHit && wall.getType() == HitResult.Type.BLOCK) {
            return level.getBlockState(blockHit.getBlockPos());
        }

        BlockPos pos = BlockPos.containing(impact);
        for (int down = 0; down < 4; down++) {
            BlockState state = level.getBlockState(pos.below(down));
            if (!state.isAir()) {
                return state;
            }
        }
        return level.getBlockState(pos);
    }

    private static void debris(ServerLevel level, Vec3 pos, BlockState state, double radius, int count) {
        if (!state.isAir() && state.getFluidState().isEmpty()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, count, radius * 0.3D, radius * 0.2D, radius * 0.3D, 0.15D);
        }
    }

    private static boolean tickSmoke(Smoke smoke) {
        long left = smoke.until() - smoke.level().getGameTime();
        if (left <= 0L) {
            return true;
        }

        if (left % 2L == 0L) {
            Vec3 pos = smoke.pos();
            double radius = smoke.radius();
            smoke.level().sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 1 + (int) radius, radius * 0.4D, radius * 0.25D, radius * 0.4D, 0.01D);
            smoke.level().sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 2 + (int) radius, radius * 0.4D, radius * 0.25D, radius * 0.4D, 0.02D);
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!BARRAGES.isEmpty()) {
            BARRAGES.removeIf(TongueTankQuirk::tickBarrage);
        }

        if (!DIVES.isEmpty()) {
            DIVES.removeIf(TongueTankQuirk::tickDive);
        }

        if (!SMOKES.isEmpty()) {
            SMOKES.removeIf(TongueTankQuirk::tickSmoke);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BARRAGES.clear();
        DIVES.clear();
        SMOKES.clear();
    }

    private static boolean isAlly(LivingEntity owner, LivingEntity target) {
        return owner.isAlliedTo(target) || target instanceof OwnableEntity ownable && owner.getUUID().equals(ownable.getOwnerUUID()) || SubordinateHelper.isAlly(owner, target);
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

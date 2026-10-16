package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.entity.TelekinesisBlockEntity;
import com.radient.tensuraacadamia.mixin.ServerGamePacketListenerAccessor;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.DamageReduction;
import com.radient.tensuraacadamia.util.GroundBlocks;
import com.radient.tensuraacadamia.util.HitRules;
import com.radient.tensuraacadamia.util.Modifiers;
import com.radient.tensuraacadamia.util.SizeReach;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.SkillHelper;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class EnduranceQuirk extends Skill {

    private static final QuirkSkillsConfig.Endurance CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Endurance;

    private static final int ENHANCE = 0;
    private static final int CHARGE = 1;
    private static final int MAX_DEBRIS = 60;
    private static final int DEBRIS_PER_TICK = 8;
    private static final double BREAK_AHEAD = 4.0D;
    private static final int DEBRIS_TICKS = 100;
    private static final double DEBRIS_GRAVITY = 0.04D;
    private static final String GAINED_TAG = "gained";
    private static final double SAME = 1.0E-6D;
    private static final int GROW_EFFECT_TICKS = 5;
    private static final int GROW_SOUND_TICKS = 10;
    private static final ResourceLocation ENDURANCE = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "endurance");

    private static final class Charge {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final float damage;
        private final Set<LivingEntity> hit = new HashSet<>();
        private Vec3 direction;
        private Vec3 lastPos;

        private Charge(ManasSkillInstance instance, LivingEntity owner, float damage) {
            this.instance = instance;
            this.owner = owner;
            this.damage = damage;
            this.direction = Vec3.directionFromRotation(0.0F, owner.getYRot());
            this.lastPos = owner.position();
        }
    }

    private static final class Debris {
        private final ManasSkillInstance instance;
        private final LivingEntity owner;
        private final TelekinesisBlockEntity block;
        private final BlockState state;
        private final float damage;
        private final Set<LivingEntity> hit = new HashSet<>();
        private Vec3 velocity;
        private int age;

        private Debris(ManasSkillInstance instance, LivingEntity owner, TelekinesisBlockEntity block, BlockState state, float damage, Vec3 velocity) {
            this.instance = instance;
            this.owner = owner;
            this.block = block;
            this.state = state;
            this.damage = damage;
            this.velocity = velocity;
        }
    }

    private static final List<Charge> CHARGES = new ArrayList<>();
    private static final List<Debris> DEBRIS = new ArrayList<>();

    public EnduranceQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        return mode == CHARGE ? CONFIG.chargeAuraCost : 0.0D;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return mode == ENHANCE ? CHARGE : ENHANCE;
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case ENHANCE -> "endurance.enhance";
            case CHARGE -> "endurance.charge";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == ENHANCE && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getEndurance(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.ENDURANCE.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    private static double getGained(ManasSkillInstance instance) {
        CompoundTag tag = instance.getTag();
        return tag == null ? 0.0D : Math.max(0.0D, tag.getDouble(GAINED_TAG));
    }

    private static void setGained(ManasSkillInstance instance, double gained) {
        instance.getOrCreateTag().putDouble(GAINED_TAG, gained);
        instance.markDirty();
    }

    private static double getMaxGained(ManasSkillInstance instance, LivingEntity entity) {
        return Math.max(0.0D, (instance.isMastered(entity) ? CONFIG.maxSizeMastered : CONFIG.maxSize) - 1.0D);
    }

    private static void update(ManasSkillInstance instance, LivingEntity entity) {
        double gained = getGained(instance);
        Modifiers.set(entity, Attributes.MOVEMENT_SPEED, ENDURANCE, -CONFIG.speedLostPerSize * gained, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Modifiers.set(entity, Attributes.ARMOR, ENDURANCE, CONFIG.armorPerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.ATTACK_DAMAGE, ENDURANCE, CONFIG.damagePerSize * gained, AttributeModifier.Operation.ADD_VALUE);
        Modifiers.set(entity, Attributes.SCALE, ENDURANCE, gained, AttributeModifier.Operation.ADD_VALUE);
        SizeReach.update(entity);
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity living) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        if (!instance.isToggled() || owner.getMaxHealth() <= 0.0F) {
            return true;
        }

        double missing = Math.max(0.0D, 1.0D - owner.getHealth() / owner.getMaxHealth()) * 100.0D;
        amount.set(DamageReduction.reduce(owner, source, amount.get(), missing * CONFIG.toughenDamageReduction));
        return true;
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (mode != ENHANCE || !(entity.level() instanceof ServerLevel level) || !entity.isShiftKeyDown() || getGained(instance) <= 0.0D) {
            return;
        }

        setGained(instance, 0.0D);
        update(instance, entity);
        level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(0.5D), entity.getZ(), 16, entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.02D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    @Override
    public boolean onHeld(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int mode) {
        if (mode == CHARGE) {
            return entity.level() instanceof ServerLevel level && holdCharge(level, instance, entity, heldTicks);
        }

        if (mode != ENHANCE || entity.isShiftKeyDown() || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }

        double gained = getGained(instance);
        double max = getMaxGained(instance, entity);
        if (gained >= max - SAME) {
            if (heldTicks == 0) {
                fail(entity, Component.translatable("tracadamia.skill.endurance.max"));
            }
            return false;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ENHANCE)) {
            return false;
        }

        if (entity.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }

        gained = Math.min(max, gained + Math.max(SAME, CONFIG.growthPerTick));
        setGained(instance, gained);
        update(instance, entity);
        if (heldTicks % GROW_EFFECT_TICKS == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), entity.getX(), entity.getY(0.5D), entity.getZ(), 4,
                    entity.getBbWidth() * 0.4D, entity.getBbHeight() * 0.3D, entity.getBbWidth() * 0.4D, 0.05D);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("tracadamia.skill.endurance.speed_taken", Math.round(gained * CONFIG.speedLostPerSize * 100.0D)).withStyle(ChatFormatting.GRAY), true);
            }
        }

        if (heldTicks % GROW_SOUND_TICKS == 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, (float) Math.max(0.5D, 1.2D - gained * 0.16D));
        }

        if (gained >= max - SAME) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.7F);
            instance.setCoolDown(CONFIG.cooldown, ENHANCE);
            return false;
        }

        return true;
    }

    @Override
    public void onRelease(ManasSkillInstance instance, LivingEntity entity, int heldTicks, int keyNumber, int mode) {
        if (mode == ENHANCE && heldTicks > 0) {
            instance.setCoolDown(CONFIG.cooldown, ENHANCE);
        }

        if (mode == CHARGE) {
            endCharge(instance, entity);
        }
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.getMastery() >= 0.0D;
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        update(instance, entity);
        if (getGained(instance) > 0.0D || instance.isToggled()) {
            instance.addMasteryPoint(entity);
        }
    }

    @Override
    public void onRespawn(ManasSkillInstance instance, ServerPlayer owner, boolean conqueredEnd) {
        update(instance, owner);
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        setGained(instance, 0.0D);
        update(instance, entity);
        SizeReach.update(entity, this);
        CHARGES.removeIf(charge -> charge.owner == entity);
    }


    private static boolean holdCharge(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int heldTicks) {
        Charge charge = CHARGES.stream().filter(held -> held.owner == entity).findFirst().orElse(null);
        if (heldTicks == 0 || charge == null) {
            if (heldTicks > 0 || EnergyHelper.isOutOfEnergy(entity, instance, CHARGE)) {
                return false;
            }

            CHARGES.removeIf(held -> held.owner == entity);
            charge = new Charge(instance, entity, (float) (entity.getArmorValue() * CONFIG.chargeDamagePerArmor));
            CHARGES.add(charge);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 1.2F);
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.2D, entity.getZ(), 12, entity.getBbWidth() * 0.4D, 0.1D, entity.getBbWidth() * 0.4D, 0.05D);
            instance.addMasteryPoint(entity);
        }

        long most = Math.round((instance.isMastered(entity) ? CONFIG.chargeHoldSecondsMastered : CONFIG.chargeHoldSeconds) * 20.0D);
        if (heldTicks >= most || !tickCharge(level, charge, heldTicks)) {
            endCharge(instance, entity);
            return false;
        }

        if (heldTicks > 0 && heldTicks % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            instance.addMasteryPoint(entity);
        }
        return true;
    }

    private static void endCharge(ManasSkillInstance instance, LivingEntity entity) {
        if (CHARGES.removeIf(charge -> charge.owner == entity)) {
            instance.setCoolDown(CONFIG.chargeCooldown, CHARGE);
        }
    }

    private static boolean tickCharge(ServerLevel level, Charge charge, int age) {
        LivingEntity owner = charge.owner;
        if (!owner.isAlive() || owner.isRemoved() || owner.level() != level || owner.isPassenger()) {
            return false;
        }

        charge.direction = Vec3.directionFromRotation(0.0F, owner.getYRot());
        double speed = CONFIG.chargeSpeed / 20.0D;
        Vec3 step = charge.direction.scale(speed);
        breakThrough(level, charge, owner.getBoundingBox().inflate(0.1D, 0.0D, 0.1D).expandTowards(step.scale(BREAK_AHEAD)));

        DamageSource source = ((Skill) charge.instance.getSkill()).createSource(charge.instance, owner, owner instanceof Player ? DamageTypes.PLAYER_ATTACK : DamageTypes.MOB_ATTACK, CHARGE);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().expandTowards(step).inflate(0.6D),
                target -> target != owner && target.isAlive() && !target.isSpectator() && !charge.hit.contains(target) && !MultiArms.isHoldPair(owner, target) && HitRules.canHit(owner, target))) {
            charge.hit.add(target);
            target.invulnerableTime = 0;
            if (charge.damage > 0.0F) {
                target.hurt(source, charge.damage);
            }

            SkillHelper.knockBack(target, owner, charge.instance, charge.direction, 1.4D, 0.0D, 0.4D);
            target.hurtMarked = true;
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
        }

        double fall = owner.getY() - charge.lastPos.y;
        double vertical = owner.onGround() ? -0.08D : (fall - 0.08D) * 0.98D;
        charge.lastPos = owner.position();
        Vec3 velocity = new Vec3(step.x, owner instanceof ServerPlayer ? vertical : owner.getDeltaMovement().y, step.z);
        owner.setDeltaMovement(velocity);
        owner.resetFallDistance();
        if (owner instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new BruiserQuirk.LeapPayload(velocity.toVector3f()));
            ((ServerGamePacketListenerAccessor) player.connection).tracadamia$setAboveGroundTickCount(0);
        } else {
            owner.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.CLOUD, owner.getX(), owner.getY() + 0.2D, owner.getZ(), 2, owner.getBbWidth() * 0.3D, 0.05D, owner.getBbWidth() * 0.3D, 0.02D);
        BlockState ground = owner.getBlockStateOn();
        if (!ground.isAir() && age % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), owner.getX(), owner.getY() + 0.1D, owner.getZ(), 6, owner.getBbWidth() * 0.4D, 0.05D, owner.getBbWidth() * 0.4D, 0.1D);
        }
        return true;
    }

    private static boolean canSmash(ServerLevel level, Charge charge, BlockPos pos, BlockState state) {
        LivingEntity owner = charge.owner;
        if (state.getDestroySpeed(level, pos) < 0.0F || !GroundBlocks.canGrief(level, owner) || owner instanceof Player player && !level.mayInteract(player, pos)) {
            return false;
        }
        return !TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(charge.instance, level, owner, pos.getX(), pos.getY(), pos.getZ()).isFalse();
    }

    private static void breakThrough(ServerLevel level, Charge charge, AABB area) {
        LivingEntity owner = charge.owner;
        int feet = Mth.floor(owner.getY() + 0.05D);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(area.minX), feet, Mth.floor(area.minZ), Mth.floor(area.maxX), Mth.floor(area.maxY - 1.0E-3D), Mth.floor(area.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && canSmash(level, charge, pos, state)) {
                blocks.add(pos.immutable());
            }
        }

        RandomSource random = level.random;
        int flying = 0;
        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity()) {
                level.destroyBlock(pos, true, owner);
                GroundBlocks.griefed(level, charge.instance, owner, pos);
                continue;
            }

            level.removeBlock(pos, false);
            GroundBlocks.griefed(level, charge.instance, owner, pos);
            level.levelEvent(2001, pos, Block.getId(state));
            if (DEBRIS.size() < MAX_DEBRIS && flying < DEBRIS_PER_TICK) {
                Vec3 velocity = charge.direction.scale(CONFIG.chargeSpeed / 20.0D * (0.8D + random.nextDouble() * 0.5D))
                        .add((random.nextDouble() - 0.5D) * 0.5D, 0.2D + random.nextDouble() * 0.3D, (random.nextDouble() - 0.5D) * 0.5D);
                TelekinesisBlockEntity block = TelekinesisBlockEntity.create(level, state, 1.0F, Vec3.atCenterOf(pos));
                level.addFreshEntity(block);
                DEBRIS.add(new Debris(charge.instance, owner, block, state, (float) (charge.damage * CONFIG.chargeDebrisDamage), velocity));
                flying++;
            }
        }

        if (!blocks.isEmpty()) {
            Vec3 front = owner.getBoundingBox().getCenter().add(charge.direction.scale(owner.getBbWidth() * 0.5D + 0.5D));
            level.sendParticles(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, front.x, front.y, front.z, 0, charge.direction.x, 0.0D, charge.direction.z, Math.max(1.0D, owner.getBbHeight() / 3.0D));
            level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }

    private static boolean tickDebris(Debris debris) {
        TelekinesisBlockEntity block = debris.block;
        if (block.isRemoved() || !(block.level() instanceof ServerLevel level)) {
            return true;
        }

        block.keepAlive();
        debris.velocity = debris.velocity.subtract(0.0D, DEBRIS_GRAVITY, 0.0D);
        Vec3 from = block.position().add(0.0D, block.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = from.add(debris.velocity);
        AABB sweep = block.getBoundingBox().expandTowards(debris.velocity).inflate(0.1D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep, target -> target != debris.owner && target.isAlive() && !target.isSpectator() && !debris.hit.contains(target) && HitRules.canHit(debris.owner, target))) {
            debris.hit.add(target);
            if (debris.damage > 0.0F) {
                target.invulnerableTime = 0;
                target.hurt(((Skill) debris.instance.getSkill()).createSource(debris.instance, debris.owner, DamageTypes.FALLING_BLOCK, CHARGE), debris.damage);
            }

            target.push(debris.velocity.x * 0.3D, 0.1D, debris.velocity.z * 0.3D);
            target.hurtMarked = true;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris.state), target.getX(), target.getY(0.5D), target.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.1D);
            settle(level, debris, target.position());
            return true;
        }

        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, block));
        if (hit.getType() == HitResult.Type.BLOCK || ++debris.age > DEBRIS_TICKS || to.y < level.getMinBuildHeight()) {
            settle(level, debris, hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to);
            return true;
        }

        block.setPos(block.position().add(debris.velocity));
        return false;
    }

    private static void settle(ServerLevel level, Debris debris, Vec3 point) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris.state), point.x, point.y + 0.5D, point.z, 8, 0.3D, 0.2D, 0.3D, 0.1D);
        debris.block.discard();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!DEBRIS.isEmpty()) {
            DEBRIS.removeIf(EnduranceQuirk::tickDebris);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CHARGES.clear();
        DEBRIS.forEach(debris -> debris.block.discard());
        DEBRIS.clear();
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

}

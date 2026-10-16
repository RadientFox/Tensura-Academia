package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.entity.LeafProjectile;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LeafipulationQuirk extends Skill {
    private static final String LP = "LeafPoints";
    private static final String RUN_NEXT = "LeafRunNext";
    private static final String BLOOM_UNTIL = "TracadamiaLeafBloomUntil";
    private static final String BLOOM_NEXT = "TracadamiaLeafBloomNext";
    private static final String BLOOM_OWNER = "TracadamiaLeafBloomOwner";
    private static final String BLOOM_DAMAGE = "TracadamiaLeafBloomDamage";
    private static final int BASE_MAX_LP = 10_000;
    private static final int BLOOM_TICKS = 200;
    private static final List<LeafStep> STEPS = new ArrayList<>();

    private record LeafStep(ServerLevel level, Vec3 position, long expires) {}

    public LeafipulationQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/leafipulationicon.png");
    }
    @Override public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.leafipulation.description");
    }
    @Override public int getModes(ManasSkillInstance instance) {
        return Math.min(4, 1 + (int) (instance.getMastery() * 4 / getMaxMastery()));
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "leafipulation.needle";
            case 1 -> "leafipulation.hemlock";
            case 2 -> "leafipulation.leaf_it_to_me";
            case 3 -> "leafipulation.flowers_bloom";
            default -> super.getModeId(instance, mode);
        };
    }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (mode < 0 || mode >= getModes(instance) || !(owner.level() instanceof ServerLevel level)) return;
        switch (mode) {
            case 0 -> fireNeedles(instance, owner, level);
            case 1 -> fireHemlock(instance, owner, level);
            case 3 -> bloom(instance, owner, level);
            default -> { }
        }
    }

    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (mode != 2 || mode >= getModes(instance)) return false;
        if (!(owner.level() instanceof ServerLevel level)) return true;
        long now = level.getGameTime();
        CompoundTag tag = instance.getOrCreateTag();
        if (now >= tag.getLong(RUN_NEXT)) {
            if (!spendLp(owner, instance, 25)) { message(owner, "Not enough leaves for Leaf it to Me!"); return false; }
            tag.putLong(RUN_NEXT, now + 20);
            instance.markDirty();
        }
        Vec3 forward = owner.getLookAngle().multiply(1, 0, 1).normalize();
        if (forward.lengthSqr() < 0.01) return true;
        owner.setDeltaMovement(forward.x * 1.08, Math.max(0, owner.getDeltaMovement().y), forward.z * 1.08);
        owner.fallDistance = 0;
        owner.hurtMarked = true;
        if (now % 5 == 0) STEPS.add(new LeafStep(level, owner.position().add(0, -0.2, 0), now + 40));
        return true;
    }

    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) {
        if (mode == 2 && !owner.level().isClientSide) {
            instance.getOrCreateTag().remove(RUN_NEXT);
            instance.markDirty();
        }
    }

    private static void fireNeedles(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
        if (!hasLp(owner, instance, 30)) { message(owner, "Need 30 LP for Leaf Needle."); return; }
        boolean poison = instance.getMastery() >= 625;
        boolean mastered = instance.isMastered(owner);
        Vec3 look = owner.getLookAngle();
        int launched = 0;
        for (int i = 0; i < 30; i++) {
            LeafProjectile needle = new LeafProjectile(level, owner, LeafProjectile.NEEDLE);
            needle.setNeedleEffects(poison, mastered);
            Vec3 direction = look.add(level.random.nextGaussian() * 0.055,
                    level.random.nextGaussian() * 0.055, level.random.nextGaussian() * 0.055).normalize();
            needle.shoot(direction.x, direction.y, direction.z, 2.4F, 0);
            if (level.addFreshEntity(needle)) launched++;
        }
        if (launched == 0) return;
        spendLp(owner, instance, launched);
        instance.addMasteryPoint(owner);
        level.playSound(null, owner.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK,
                SoundSource.PLAYERS, 1, 1.25F);
    }

    private static void fireHemlock(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
        if (!hasLp(owner, instance, 100)) { message(owner, "Need 100 LP for Hemlock."); return; }
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(20),
                target -> HomingQuirk.isEnemy(owner, target) && owner.hasLineOfSight(target));
        if (enemies.isEmpty()) { message(owner, "No nearby enemies for Hemlock."); return; }
        List<LeafProjectile> launched = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            LivingEntity target = enemies.get(level.random.nextInt(enemies.size()));
            LeafProjectile leaf = new LeafProjectile(level, owner, LeafProjectile.HEMLOCK);
            leaf.setHemlockTarget(i, target);
            if (level.addFreshEntity(leaf)) launched.add(leaf);
        }
        if (launched.size() != 4) {
            launched.forEach(Entity::discard);
            return;
        }
        spendLp(owner, instance, 100);
        instance.addMasteryPoint(owner);
        level.playSound(null, owner.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK,
                SoundSource.PLAYERS, 1, 0.65F);
    }

    private static void bloom(ManasSkillInstance instance, LivingEntity owner, ServerLevel level) {
        Vec3 start = owner.getEyePosition(), end = start.add(owner.getLookAngle().scale(20));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, start, end, new AABB(start, end).inflate(0.7),
                entity -> entity instanceof LivingEntity living && HomingQuirk.isEnemy(owner, living), 400);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target) || !owner.hasLineOfSight(target)) {
            message(owner, "Look at an enemy within 20 blocks.");
            return;
        }
        if (!spendLp(owner, instance, 500)) { message(owner, "Need 500 LP for Flowers Bloom in Your Hearts."); return; }
        long now = level.getGameTime();
        CompoundTag tag = target.getPersistentData();
        tag.putLong(BLOOM_UNTIL, now + BLOOM_TICKS);
        tag.putLong(BLOOM_NEXT, now + 20);
        tag.putUUID(BLOOM_OWNER, owner.getUUID());
        tag.putFloat(BLOOM_DAMAGE, instance.isMastered(owner) ? 31.25F : 25F);
        target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.PARALYSIS),
                BLOOM_TICKS, 4));
        target.addEffect(new MobEffectInstance(MHAEffects.LEAF_ANTI_HEALING, BLOOM_TICKS, 0, false, false, false));
        level.sendParticles(MHAParticles.GREEN_LEAF.get(), target.getX(), target.getY() + target.getBbHeight() / 2,
                target.getZ(), 55, 0.7, 0.8, 0.7, 0.03);
        level.playSound(null, target.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK,
                SoundSource.PLAYERS, 1.2F, 0.5F);
        instance.addMasteryPoint(owner);
    }

    private static int storedLp(ManasSkillInstance instance) {
        return Math.clamp(instance.getOrCreateTag().getInt(LP), 0, maxLp(instance));
    }

    private static int maxLp(ManasSkillInstance instance) {
        return instance.getMastery() >= 2500 ? 12_500 : BASE_MAX_LP;
    }

    private static boolean hasLp(LivingEntity owner, ManasSkillInstance instance, int cost) {
        int stored = storedLp(instance);
        return stored >= cost || ambientLp(owner, cost - stored) >= cost - stored;
    }

    private static boolean spendLp(LivingEntity owner, ManasSkillInstance instance, int cost) {
        if (!hasLp(owner, instance, cost)) return false;
        int remaining = Math.max(0, storedLp(instance) - cost);
        instance.getOrCreateTag().putInt(LP, remaining);
        instance.markDirty();
        if (owner instanceof Player player) player.displayClientMessage(Component.literal("LP: " + remaining + "/" + maxLp(instance)), true);
        return true;
    }

    private static int ambientLp(LivingEntity owner, int needed) {
        if (needed <= 0) return 0;
        BlockPos center = owner.blockPosition();
        int available = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            var state = owner.level().getBlockState(pos);
            if (state.is(BlockTags.LEAVES)) available += 100;
            else if (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
                    || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                    || state.is(Blocks.GRASS_BLOCK)) available += 10;
            if (available >= needed) return available;
        }
        return available;
    }

    private static void message(LivingEntity owner, String text) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(text), true);
    }

    @SubscribeEvent public static void eatLeaves(PlayerInteractEvent.RightClickItem event) {
        if (!eat(event.getEntity(), event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getEntity().level().isClientSide));
    }

    @SubscribeEvent public static void eatLeavesFromBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!eat(event.getEntity(), event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getEntity().level().isClientSide));
    }

    private static boolean eat(Player player, ItemStack stack) {
        int gain = stack.is(ItemTags.LEAVES) ? 100 : stack.is(Tags.Items.SEEDS) ? 10 : 0;
        if (gain == 0) return false;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(player)
                .getSkill(QuirkSkills.LEAFIPULATION.get()).orElse(null);
        if (instance == null || storedLp(instance) >= maxLp(instance)) return false;
        if (player.level().isClientSide) return true;
        int total = Math.min(maxLp(instance), storedLp(instance) + gain);
        if (!player.isCreative()) stack.shrink(1);
        instance.getOrCreateTag().putInt(LP, total);
        instance.markDirty();
        player.displayClientMessage(Component.literal("LP: " + total + "/" + maxLp(instance)), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.7F, 1.3F);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH) public static void leafBarrier(LivingIncomingDamageEvent event) {
        LivingEntity owner = event.getEntity();
        if (!(owner.level() instanceof ServerLevel level) || event.getAmount() <= 0
                || event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null
                || event.getSource().is(DamageTypeTags.IS_FIRE)
                || event.getSource().is(TensuraDamageTypes.HEAT_WAVE)
                || event.getSource() instanceof TensuraDamageSource source && source.tensura$getElement() == Element.FLAME) return;
        ManasSkillInstance instance = SkillAPI.getSkillsFrom(owner)
                .getSkill(QuirkSkills.LEAFIPULATION.get()).orElse(null);
        if (instance == null || !instance.isToggled()) return;
        int cost = Math.max(1, (int) Math.ceil(event.getAmount() * 5));
        if (!spendLp(owner, instance, cost)) return;
        event.setCanceled(true);
        level.sendParticles(MHAParticles.GREEN_LEAF.get(), owner.getX(), owner.getY() + owner.getBbHeight() / 2,
                owner.getZ(), 30, 0.75, 0.8, 0.75, 0.04);
        level.playSound(null, owner.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK,
                SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!(entity instanceof LivingEntity target)) return;
        CompoundTag tag = target.getPersistentData();
        if (!tag.contains(BLOOM_UNTIL)) return;
        long now = level.getGameTime();
        if (!target.isAlive() || now >= tag.getLong(BLOOM_UNTIL)) {
            tag.remove(BLOOM_UNTIL);
            tag.remove(BLOOM_NEXT);
            tag.remove(BLOOM_OWNER);
            tag.remove(BLOOM_DAMAGE);
            return;
        }
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (now % 4 == 0) level.sendParticles(MHAParticles.GREEN_LEAF.get(),
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                8, 0.5, 0.7, 0.5, 0);
        if (now < tag.getLong(BLOOM_NEXT)) return;
        tag.putLong(BLOOM_NEXT, now + 20);
        target.invulnerableTime = 0;
        Entity attacker = tag.hasUUID(BLOOM_OWNER) ? level.getEntity(tag.getUUID(BLOOM_OWNER)) : null;
        target.hurt(attacker instanceof LivingEntity living
                ? target.damageSources().indirectMagic(living, living) : target.damageSources().magic(),
                tag.getFloat(BLOOM_DAMAGE));
    }

    @SubscribeEvent public static void preventHealing(LivingHealEvent event) {
        if (event.getEntity().hasEffect(MHAEffects.LEAF_ANTI_HEALING)) event.setCanceled(true);
    }

    @SubscribeEvent public static void renderSteps(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        STEPS.removeIf(step -> step.expires <= now);
        if (now % 4 != 0) return;
        for (LeafStep step : STEPS) step.level.sendParticles(MHAParticles.GREEN_LEAF.get(),
                step.position.x, step.position.y, step.position.z, 5, 0.55, 0.04, 0.55, 0);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { STEPS.clear(); }
}

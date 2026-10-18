package com.radient.tensuraacadamia.quirks.arbor;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.quirks.shared.QuirkCastCosts;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.util.GroundBlocks;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class ArborQuirk extends Skill {
    public static final double BRANCH_RANGE = 32, TREE_RADIUS = 75, ALLY_REGEN = .01;
    public static final int TREE_TICKS = 1200, LINGER_TICKS = 60;
    public static final String HELD_UNTIL = "ArborHeldUntil";
    private static final String BRANCHES = "ArborBranches";

    public ArborQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/arboricon.png"); }
    @Override public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() ? 3 : instance.getMastery() >= getMaxMastery() * .3 ? 2 : 1;
    }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        return "arbor." + switch (mode) { case 1 -> "chain_prison"; case 2 -> "yggdrasil"; default -> "branch"; };
    }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode < 0 || mode > 2 || mode < 2 && holding(instance, owner) ? 0 : auraCost(mode);
    }
    public static int auraCost(int mode) { return mode == 2 ? 10000 : mode == 1 ? 1000 : 100; }
    @Override public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return mode < 2 && holding(instance, owner) || super.canIgnoreCoolDown(instance, owner, mode);
    }
    @Override public boolean canScroll(ManasSkillInstance instance, LivingEntity owner, int mode) {
        return mode == 0 && holding(instance, owner);
    }
    public static boolean holding(ManasSkillInstance instance, LivingEntity owner) {
        return owner.level().isClientSide ? !instance.getOrCreateTag().getList(BRANCHES, Tag.TAG_COMPOUND).isEmpty()
                : owner.level().getGameTime() < instance.getOrCreateTag().getLong(HELD_UNTIL);
    }
    @Override public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) { return mode < 2; }
    private static List<ArborConstruct> branches(ManasSkillInstance instance, ServerLevel level) {
        var result = new ArrayList<ArborConstruct>();
        for (var entry : instance.getOrCreateTag().getList(BRANCHES, Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) entry;
            if (tag.hasUUID("Id") && level.getEntity(tag.getUUID("Id")) instanceof ArborConstruct branch && branch.isAlive()) result.add(branch);
        }
        return result;
    }
    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || mode < 0 || mode >= getModes(instance)
                || instance.onCoolDown(mode) || mode < 2 && holding(instance, owner)
                || !QuirkCastCosts.hasAura(owner, auraCost(mode))) return;
        var made = new ArrayList<ArborConstruct>();
        if (mode == 2) {
            if (!owner.onGround()) { message(owner, "Yggdrasil requires standing on the ground."); return; }
            Vec3 forward = owner.getLookAngle().multiply(1, 0, 1).normalize();
            if (forward.lengthSqr() < .01) forward = Vec3.directionFromRotation(0, owner.getYRot());
            BlockPos column = BlockPos.containing(owner.position().add(forward.scale(20)));
            if (!level.hasChunkAt(column)) return;
            var ground = GroundBlocks.findSurface(level, column);
            if (ground == null) { message(owner, "No ground for Yggdrasil."); return; }
            var tree = ArborEntities.CONSTRUCT.get().create(level);
            if (tree == null) return;
            tree.configureTree(owner, Vec3.atBottomCenterOf(ground.above()));
            made.add(tree);
        } else {
            List<LivingEntity> targets = mode == 1 ? level.getEntitiesOfClass(LivingEntity.class,
                            owner.getBoundingBox().inflate(BRANCH_RANGE), target -> ArborConstruct.canBind(owner, target)
                                    && HomingQuirk.isEnemy(owner, target) && owner.distanceToSqr(target) <= BRANCH_RANGE * BRANCH_RANGE
                                    && owner.hasLineOfSight(target)).stream().sorted(Comparator.comparingDouble(owner::distanceToSqr))
                    .limit(instance.getMastery() >= getMaxMastery() ? 8 : 4).toList() : List.of();
            if (mode == 1 && targets.isEmpty()) { message(owner, "No nearby enemies."); return; }
            int count = mode == 0 ? 1 : targets.size();
            for (int i = 0; i < count; i++) {
                var branch = ArborEntities.CONSTRUCT.get().create(level);
                if (branch == null) return;
                branch.configureBranch(owner, mode == 1 ? targets.get(i) : null);
                made.add(branch);
            }
        }
        for (var construct : made) if (!level.addFreshEntity(construct)) { made.forEach(ArborConstruct::discard); return; }
        if (mode < 2) {
            var ids = new ListTag();
            for (var construct : made) { var tag = new CompoundTag(); tag.putUUID("Id", construct.getUUID()); ids.add(tag); }
            instance.getOrCreateTag().put(BRANCHES, ids);
            instance.getOrCreateTag().putLong(HELD_UNTIL, level.getGameTime() + 6);
        }
        QuirkCastCosts.spendAura(owner, auraCost(mode));
        QuirkCastCosts.cooldown(instance, mode, mode == 2 ? 240 : mode == 1 ? 15 : 0, 3);
        instance.addMasteryPoint(owner); instance.markDirty();
        owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, owner.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1, .6F);
    }
    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        if (mode < 0 || mode > 1 || mode >= getModes(instance)) return false;
        if (!(owner.level() instanceof ServerLevel level)) return true;
        var active = branches(instance, level);
        if (active.stream().noneMatch(ArborConstruct::isAttachedOrFlying)) return false;
        instance.getOrCreateTag().putLong(HELD_UNTIL, level.getGameTime() + 6);
        return true;
    }
    @Override public void onScroll(ManasSkillInstance instance, LivingEntity owner, double scroll, int mode) {
        if (mode == 0 && Double.isFinite(scroll) && holding(instance, owner) && owner.level() instanceof ServerLevel level)
            for (var branch : branches(instance, level)) branch.scroll(scroll);
    }
    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int key, int mode) {
        if (mode < 2 && owner.level() instanceof ServerLevel level) {
            branches(instance, level).forEach(ArborConstruct::release);
            instance.getOrCreateTag().remove(BRANCHES); instance.getOrCreateTag().remove(HELD_UNTIL); instance.markDirty();
        }
    }
    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) { onRelease(instance, owner, 0, 0, 0); }
    public static boolean sunlight(LivingEntity owner) {
        var level = owner.level();
        return level.isDay() && level.dimensionType().hasSkyLight() && !level.isRainingAt(owner.blockPosition())
                && level.canSeeSky(BlockPos.containing(owner.getEyePosition()));
    }
    public static boolean hasArbor(LivingEntity owner) {
        return SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ARBOR.get()).filter(instance -> instance.getMastery() >= 0).isPresent();
    }
    public static boolean fire(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(TensuraDamageTypes.HEAT_WAVE)
                || ((TensuraDamageSource) source).tensura$getElement() == Element.FLAME;
    }
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent event) {
        var owner = event.getEntity();
        if (!owner.level().isClientSide && hasArbor(owner)) {
            boolean sun = sunlight(owner);
            event.setAmount(event.getAmount() * (fire(event.getSource()) ? sun ? 2 : 1.5F : sun ? .5F : 1));
        }
    }
    @SubscribeEvent public static void burnEffect(MobEffectEvent.Added event) {
        var current = event.getEffectInstance();
        var id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(current.getEffect().value());
        if (!event.getEntity().level().isClientSide && hasArbor(event.getEntity()) && id != null
                && List.of("black_burn", "white_burn", "freezing_burn").contains(id.getPath()) && current.getDuration() > 0)
            current.update(new MobEffectInstance(current.getEffect(),
                (int) Math.min(Integer.MAX_VALUE, current.getDuration() * 3L), current.getAmplifier(), current.isAmbient(), current.isVisible(), current.showIcon()));
    }
    public static int burnTicks(LivingEntity owner, int requested) {
        int previous = Math.max(0, owner.getRemainingFireTicks());
        return requested > previous && hasArbor(owner) ? (int) Math.min(Integer.MAX_VALUE, previous + 3L * (requested - previous)) : requested;
    }
    private static void message(LivingEntity owner, String message) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(message), true);
    }
}

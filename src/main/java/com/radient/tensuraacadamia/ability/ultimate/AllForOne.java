package com.radient.tensuraacadamia.ability.ultimate;

import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneMenu;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneStock;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneTheme;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AllForOne extends Skill {
    public static final double TARGET_RANGE = 8.0D;
    private static final int MAX_MASTERY = 2_500;
    private static final int BASE_COOLDOWN_TICKS = 200;

    public AllForOne() {
        super(SkillType.ULTIMATE);
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/ultimate/all_for_one.png");
    }

    @Override
    public int getMaxMastery() {
        return MAX_MASTERY;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 3;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), 3);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "all_for_one.steal";
            case 1 -> "all_for_one.transfer";
            case 2 -> "all_for_one.stockpile_attack";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity entity) {
        return true;
    }

    @Override
    public void onToggleOn(ManasSkillInstance instance, LivingEntity entity) {
        AllForOneTheme.tick(entity);
    }

    @Override
    public void onToggleOff(ManasSkillInstance instance, LivingEntity entity) {
        AllForOneTheme.stop(entity);
    }

    @Override
    public boolean canTick(ManasSkillInstance instance, LivingEntity entity) {
        return instance.isToggled();
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        AllForOneTheme.tick(entity);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (mode == 2) {
            AllForOneMenu.open(player, player, 2);
            return;
        }
        if (mode != 0 && mode != 1) return;
        if (instance.onCoolDown(mode)) {
            player.displayClientMessage(Component.literal("All For One is on cooldown."), true);
            return;
        }

        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(entity, TARGET_RANGE, false);
        if (target == null || !target.isAlive() || target == entity) {
            player.displayClientMessage(Component.literal("Look at a living target within 8 blocks."), true);
            return;
        }
        AllForOneMenu.open(player, target, mode);
    }

    /** Mastered All For One cuts all of its ability cooldowns by 40%. */
    public static int cooldown(ManasSkillInstance instance, LivingEntity owner, int baseTicks) {
        return instance.isMastered(owner) ? (int) Math.ceil(baseTicks * 0.60D) : baseTicks;
    }

    public static boolean stockpileAttack(ManasSkillInstance allForOne, ServerPlayer player,
                                         List<ResourceLocation> selectedIds) {
        if (allForOne.onCoolDown(2)) {
            player.displayClientMessage(Component.literal("Stockpile Attack is on cooldown."), true);
            return false;
        }

        List<ManasSkillInstance> sacrificed = AllForOneStock.sacrifice(player, selectedIds);
        if (sacrificed.isEmpty()) {
            player.displayClientMessage(Component.literal("Select at least one stored ability for Stockpile Attack."), true);
            return false;
        }

        int skillCount = sacrificed.size();
        int quirkCount = (int) sacrificed.stream().filter(AllForOne::isQuirk).count();
        List<ManasSkillInstance> magic = sacrificed.stream()
                .filter(ability -> ability.getSkill() instanceof Magic).toList();
        float damage = skillCount * 10.0F + quirkCount * 100.0F;
        double sideLength = skillCount;
        LivingEntity aimedTarget = ObjectSelectionHelper.getTargetingEntity(player, 32.0D, false);
        Vec3 center = aimedTarget == null
                ? player.getEyePosition().add(player.getLookAngle().normalize().scale(12.0D))
                : aimedTarget.getBoundingBox().getCenter();
        AABB area = new AABB(center, center).inflate(sideLength * 0.5D);

        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != player && candidate.isAlive())) {
            damageStockpileTarget(player, target, damage, magic);
            Vec3 push = target.position().subtract(center).normalize();
            target.push(push.x * 1.5D, 0.5D, push.z * 1.5D);
            target.hurtMarked = true;
        }
        player.serverLevel().sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                1, sideLength * 0.15D, sideLength * 0.15D, sideLength * 0.15D, 0.0D);
        player.serverLevel().playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, Math.min(4.0F, 1.0F + skillCount * 0.05F), 0.7F);
        allForOne.addMasteryPoint(player, skillCount);
        allForOne.setCoolDown(cooldown(allForOne, player, BASE_COOLDOWN_TICKS), 2);
        allForOne.markDirty();
        player.sendSystemMessage(Component.literal("Stockpile Attack consumed " + skillCount + " abilities: "
                + (int) damage + " damage, " + skillCount + "x" + skillCount + "x" + skillCount + " area."));
        return true;
    }

    private static boolean isQuirk(ManasSkillInstance ability) {
        return ability.getSkill() instanceof Skill skill
                && skill.getType() == SkillType.UNIQUE
                && "tracadamia".equals(ability.getSkillId().getNamespace());
    }

    private static void damageStockpileTarget(ServerPlayer owner, LivingEntity target, float damage,
                                               List<ManasSkillInstance> magic) {
        if (magic.isEmpty()) {
            DamageSource source = owner.serverLevel().damageSources().source(DamageTypes.MAGIC, owner);
            ((TensuraDamageSource) source).tensura$setSkillType(SkillType.ULTIMATE);
            target.invulnerableTime = 0;
            target.hurt(source, damage);
            return;
        }
        float share = damage / magic.size();
        for (ManasSkillInstance ability : magic) {
            Magic spell = (Magic) ability.getSkill();
            DamageSource source = spell.createSource(ability, owner, DamageTypes.MAGIC, 0);
            target.invulnerableTime = 0;
            target.hurt(source, share);
        }
    }
}

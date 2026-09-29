package com.radient.tensuraacadamia.ability.ultimate;

import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneMenu;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneStock;
import com.radient.tensuraacadamia.ability.ultimate.afo.AllForOneTheme;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.magic.Magic;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AllForOne extends Skill {
    public static final double TARGET_RANGE = 2.0D;
    private static final int MAX_MASTERY = 2_500;
    private static final String STOCKPILE_LEVEL = "StockpileOutputLevel";

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
        return 4;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        if (mode == 2 && entity.isShiftKeyDown()) {
            cycleStockpileOutput(instance, entity, reverse);
            return mode;
        }
        return Math.floorMod(mode + (reverse ? -1 : 1), 4);
    }

    public static int stockpileLevel(ManasSkillInstance instance) {
        return Math.clamp(instance.getOrCreateTag().getInt(STOCKPILE_LEVEL), 0, 4);
    }

    public static double stockpileOutput(ManasSkillInstance instance) { return (stockpileLevel(instance) + 1) / 5.0D; }
    public static double stockpileAuraFraction(ManasSkillInstance instance) { return (4 + stockpileLevel(instance) * 9) / 80.0D; }
    public static int stockpileCooldownSeconds(ManasSkillInstance instance) { return 300 + stockpileLevel(instance) * 225; }

    private static void cycleStockpileOutput(ManasSkillInstance instance, LivingEntity owner, boolean reverse) {
        instance.getOrCreateTag().putInt(STOCKPILE_LEVEL, Math.floorMod(stockpileLevel(instance) + (reverse ? -1 : 1), 5));
        instance.markDirty();
        if (!owner.level().isClientSide) message(owner, "Stockpile Attack output: " + (stockpileLevel(instance) + 1) * 20 + "%");
    }

    @Override public Component getModeName(ManasSkillInstance instance, int mode) {
        Component name = super.getModeName(instance, mode);
        return mode == 2 ? name.copy().append(" (" + (stockpileLevel(instance) + 1) * 20 + "%)") : name;
    }

    @Override public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) { return 0.0D; }

    @Override public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return mode == 2 && entity.isShiftKeyDown() || super.canIgnoreCoolDown(instance, entity, mode);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case 0 -> "all_for_one.steal";
            case 1 -> "all_for_one.transfer";
            case 2 -> "all_for_one.stockpile_attack";
            case 3 -> "all_for_one.combine";
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
        return instance.isToggled() || isInSlot(entity, instance);
    }

    @Override
    public void onTick(ManasSkillInstance instance, LivingEntity entity) {
        if (instance.isToggled()) AllForOneTheme.tick(entity);
        // The skill framework invokes onTick once every five seconds.
        if (!entity.level().isClientSide && entity.isAlive() && isInSlot(entity, instance)) instance.addMasteryPoint(entity, 1);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (mode == 3) {
            AllForOneMenu.open(player, player, 3);
            return;
        }
        if (mode == 2) {
            if (player.isShiftKeyDown()) cycleStockpileOutput(instance, player, false);
            else stockpileAttack(instance, player);
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
    public static int cooldown(ManasSkillInstance instance, LivingEntity owner, int baseSeconds) {
        return instance.isMastered(owner) ? (int) Math.ceil(baseSeconds * 0.60D) : baseSeconds;
    }

    public static boolean stockpileAttack(ManasSkillInstance allForOne, LivingEntity player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (allForOne.onCoolDown(2)) {
            message(player, "Stockpile Attack is on cooldown.");
            return false;
        }

        List<ManasSkillInstance> stock = AllForOneStock.instances(player);
        if (stock.isEmpty()) {
            message(player, "You need at least one stored ability for Stockpile Attack.");
            return false;
        }
        double cost = EnergyHelper.getMaxAura(player) * stockpileAuraFraction(allForOne);
        var existence = TensuraStorages.getExistenceFrom(player);
        if (!Double.isFinite(cost) || !Double.isFinite(existence.getAura()) || existence.getAura() < cost) {
            message(player, "Not enough aura for Stockpile Attack.");
            return false;
        }

        int skillCount = stock.size();
        int quirkCount = (int) stock.stream().filter(AllForOne::isQuirk).count();
        List<ManasSkillInstance> magic = stock.stream()
                .filter(ability -> ability.getSkill() instanceof Magic).toList();
        double output = stockpileOutput(allForOne);
        float damage = (float) ((skillCount * 10.0D + quirkCount * 100.0D) * output);
        double sideLength = skillCount * output;
        LivingEntity aimedTarget = ObjectSelectionHelper.getTargetingEntity(player, 32.0D, false);
        Vec3 center = aimedTarget == null
                ? player.getEyePosition().add(player.getLookAngle().normalize().scale(12.0D))
                : aimedTarget.getBoundingBox().getCenter();
        AABB area = new AABB(center, center).inflate(sideLength * 0.5D);

        existence.setAura(existence.getAura() - cost);
        existence.markDirty();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != player && candidate.isAlive())) {
            damageStockpileTarget(player, target, damage, magic);
            Vec3 push = target.position().subtract(center).normalize();
            target.push(push.x * 1.5D, 0.5D, push.z * 1.5D);
            target.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                1, sideLength * 0.15D, sideLength * 0.15D, sideLength * 0.15D, 0.0D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, Math.min(4.0F, 1.0F + (float) sideLength * 0.05F), 0.7F);
        allForOne.addMasteryPoint(player, skillCount);
        allForOne.setCoolDown(cooldown(allForOne, player, stockpileCooldownSeconds(allForOne)), 2);
        allForOne.markDirty();
        message(player, "Stockpile Attack at " + Math.round(output * 100) + "%: " + damage + " damage, "
                + sideLength + "x" + sideLength + "x" + sideLength + " area. No abilities consumed.");
        return true;
    }

    private static void message(LivingEntity owner, String text) {
        if (owner instanceof Player player) player.displayClientMessage(Component.literal(text), true);
    }

    private static boolean isQuirk(ManasSkillInstance ability) {
        return ability.getSkill() instanceof Skill skill
                && skill.getType() == SkillType.UNIQUE
                && "tracadamia".equals(ability.getSkillId().getNamespace());
    }

    private static void damageStockpileTarget(LivingEntity owner, LivingEntity target, float damage,
                                               List<ManasSkillInstance> magic) {
        if (magic.isEmpty()) {
            DamageSource source = owner.level().damageSources().source(DamageTypes.MAGIC, owner);
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

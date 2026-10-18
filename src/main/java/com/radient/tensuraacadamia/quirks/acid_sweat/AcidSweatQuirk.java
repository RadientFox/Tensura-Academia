package com.radient.tensuraacadamia.quirks.acid_sweat;

import com.radient.tensuraacadamia.quirks.homing.HomingQuirk;
import com.radient.tensuraacadamia.quirks.shared.QuirkCastCosts;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAParticles;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.util.SubordinateHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AcidSweatQuirk extends Skill {
    public static final int CLAP_AURA = 1000, CLAP_COOLDOWN = 10, BARRAGE_AURA = 5000, BARRAGE_COOLDOWN = 30;
    public static final int CLAP_STARTUP = 12, BARRAGE_STARTUP = 8, PUNCH_COUNT = 30, PUNCH_INTERVAL = 2;
    public static final double PUNCH_REACH = 3, BOOM_HALF_SIZE = 1;

    public AcidSweatQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return 2500;
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public MutableComponent getSkillDescription() {
        return Component.translatable("tracadamia.skill.acid_sweat.description");
    }

    @Override
    public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/acidsweaticon.png");
    }

    @Override
    public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) {
        return true;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return instance.getMastery() >= getMaxMastery() ? 2 : instance.getMastery() >= getMaxMastery() / 2 ? 1 : 0;
    }

    @Override
    public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        int modes = getModes(instance);
        return modes == 0 ? 0 : Math.floorMod(mode + (reverse ? -1 : 1), modes);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return "acid_sweat." + (mode == 1 ? "consecutive_punches" : "clap");
    }

    @Override
    public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        return mode == 1 ? BARRAGE_AURA : CLAP_AURA;
    }

    public static float masteryPercent(ManasSkillInstance instance) {
        return (float) Math.clamp(instance.getMastery() * 100 / 2500, 0, 100);
    }

    public static float boomDamage(ManasSkillInstance instance) {
        return Math.max(1, masteryPercent(instance) * (instance.getMastery() >= 2500 ? 3 : 2));
    }

    public static double boomChance(ManasSkillInstance instance) {
        return instance.getMastery() >= 2500 ? .5 : .25;
    }

    public static boolean validMelee(LivingEntity attacker, DamageSource source) {
        return attacker.getMainHandItem().isEmpty() && source.getDirectEntity() == attacker
                && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))
                && !(((TensuraDamageSource) source).tensura$getAbilityInstance() != null
                && ((TensuraDamageSource) source).tensura$getAbilityInstance().getSkill() == QuirkSkills.ACID_SWEAT.get());
    }

    @SubscribeEvent
    public static void meleeHit(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || !(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof LivingEntity owner) || !validMelee(owner, event.getSource()))
            return;
        var instance = SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.ACID_SWEAT.get()).orElse(null);
        if (instance == null || instance.getMastery() < 0 || !instance.isToggled() || owner == event.getEntity()
                || owner.isAlliedTo(event.getEntity()) || SubordinateHelper.isAlly(owner, event.getEntity())
                || owner.getRandom().nextDouble() >= boomChance(instance)) return;
        explode(level, owner, instance, event.getEntity().getBoundingBox().getCenter(), BOOM_HALF_SIZE, boomDamage(instance), false);
        instance.addMasteryPoint(owner);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || mode < 0 || mode >= getModes(instance) || instance.onCoolDown(mode))
            return;
        if (!level.getEntitiesOfClass(AcidSweatAction.class, owner.getBoundingBox().inflate(4), action -> action.creatorId().equals(owner.getUUID())).isEmpty())
            return;
        double cost = getAuraCost(owner, instance, mode);
        if (!QuirkCastCosts.hasAura(owner, cost)) return;
        var action = new AcidSweatAction(level, owner, mode);
        if (!level.addFreshEntity(action)) return;
        QuirkCastCosts.spendAura(owner, cost);
        QuirkCastCosts.cooldown(instance, mode, mode == 1 ? BARRAGE_COOLDOWN : CLAP_COOLDOWN, 2);
        instance.addMasteryPoint(owner);
        level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, .45F, .8F);
    }

    public static void clap(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        double halfSize = instance.getMastery() >= 2500 ? 5 : 2.5;
        explode(level, owner, instance, owner.getBoundingBox().getCenter(), halfSize, masteryPercent(instance), true);
    }

    public static void punch(ServerLevel level, LivingEntity owner, ManasSkillInstance instance, int index) {
        Vec3 start = owner.getEyePosition().add(0, -.25, 0), end = start.add(owner.getLookAngle().scale(PUNCH_REACH));
        var block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(level, owner, start, end, new AABB(start, end).inflate(1),
                entity -> entity instanceof LivingEntity target && HomingQuirk.isEnemy(owner, target), .6F);
        Vec3 impact = end;
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            impact = target.getBoundingBox().getCenter();
            var damage = owner instanceof Player player ? owner.damageSources().playerAttack(player) : owner.damageSources().mobAttack(owner);
            tagDamage(damage, instance, 1);
            target.invulnerableTime = 0;
            var attack = owner.getAttribute(Attributes.ATTACK_DAMAGE);
            target.hurt(damage, attack == null ? 1 : (float) attack.getValue());
        }
        // each barrage punch explodes
        explode(level, owner, instance, impact, BOOM_HALF_SIZE, boomDamage(instance), false);
        if (index % 3 == 0)
            level.playSound(null, owner.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, .3F, 1.3F);
    }

    public static void explode(ServerLevel level, LivingEntity owner, ManasSkillInstance instance, Vec3 center, double halfSize, float damage, boolean clap) {
        var source = owner.damageSources().source(DamageTypes.EXPLOSION, owner);
        tagDamage(source, instance, clap ? 0 : 1);
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(halfSize), enemy -> HomingQuirk.isEnemy(owner, enemy))) {
            if (level.clip(new ClipContext(center, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() != HitResult.Type.MISS)
                continue;
            target.invulnerableTime = 0;
            target.hurt(source, damage);
        }
        level.sendParticles(MHAParticles.ACID_SWEAT_BLAST.get(), center.x, center.y, center.z, 0, clap ? halfSize : .6, 0, 0, 1);
        if (clap)
            level.playSound(null, owner.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .9F, 1);
        else
            level.playSound(null, net.minecraft.core.BlockPos.containing(center), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, .25F, 1.6F);
    }

    private static void tagDamage(DamageSource source, ManasSkillInstance instance, int mode) {
        var nativeSource = (TensuraDamageSource) source;
        nativeSource.tensura$setAbilityInstance(instance);
        nativeSource.tensura$setAbilityMode(mode);
        nativeSource.tensura$setSkillType(SkillType.UNIQUE);
    }
}

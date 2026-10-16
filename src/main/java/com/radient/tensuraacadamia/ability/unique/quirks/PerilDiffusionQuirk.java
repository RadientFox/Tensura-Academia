package com.radient.tensuraacadamia.ability.unique.quirks;

import io.github.manasmods.tensura.ability.skill.Skill;
import net.minecraft.resources.ResourceLocation;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

public final class PerilDiffusionQuirk extends Skill {
    public static final int PHASE_TICKS = 10;
    public PerilDiffusionQuirk() { super(SkillType.UNIQUE); }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/peril_diffusion.png");
    }
    @Override public boolean canBeSlotted(io.github.manasmods.manascore.skill.api.ManasSkillInstance instance, net.minecraft.world.entity.LivingEntity owner, int mode) { return false; }

    public static boolean trigger(Entity target, Entity attacker) {
        if (!(target instanceof ServerPlayer owner) || attacker == owner || !owner.isAlive()
                || PermeationQuirk.isPhasing(owner)
                || SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.PERIL_DIFFUSION.get()).isEmpty()) return false;
        return PermeationQuirk.startPhase(owner, false, owner.serverLevel().getGameTime() + PHASE_TICKS, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        if (attacker != null || direct instanceof Projectile) {
            trigger(event.getEntity(), attacker != null ? attacker : direct);
            if (PermeationQuirk.isPhasing(event.getEntity())) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMelee(AttackEntityEvent event) {
        trigger(event.getTarget(), event.getEntity());
        if (event.getTarget() instanceof LivingEntity living && PermeationQuirk.isPhasing(living)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        trigger(hit.getEntity(), event.getProjectile().getOwner());
        if (hit.getEntity() instanceof LivingEntity living && PermeationQuirk.isPhasing(living)) event.setCanceled(true);
    }

    public static void registerSkillEvents() {
        EntityEvents.PROJECTILE_HIT.register((hit, projectile, deflection, result) -> {
            if (!(hit instanceof EntityHitResult entityHit)) return;
            trigger(entityHit.getEntity(), projectile.getOwner());
            if (entityHit.getEntity() instanceof LivingEntity living && PermeationQuirk.isPhasing(living)) result.set(EntityEvents.ProjectileHitResult.PASS);
        });
    }
}

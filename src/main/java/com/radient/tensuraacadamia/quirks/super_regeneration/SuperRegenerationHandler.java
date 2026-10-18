package com.radient.tensuraacadamia.quirks.super_regeneration;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class SuperRegenerationHandler {
    private static final String WEAK_UNTIL = "SuperRegenerationWeakUntil", REVIVE_READY = "SuperRegenerationReviveReady";

    private SuperRegenerationHandler() { }

    private static long now(LivingEntity owner) { return owner.level().getServer().overworld().getGameTime(); }
    public static boolean weakened(LivingEntity owner) {
        return !owner.level().isClientSide && now(owner) < owner.getPersistentData().getLong(WEAK_UNTIL);
    }

    public static boolean suppressingDamage(DamageSource source) {
        return TensuraDamageHelper.isFireDamage(source) || TensuraDamageHelper.isHeat(source) || TensuraDamageHelper.isLightDamage(source);
    }

    @SubscribeEvent public static void damaged(LivingDamageEvent.Post event) {
        var owner = event.getEntity();
        if (owner.level().isClientSide || event.getNewDamage() <= 0 || !suppressingDamage(event.getSource())) return;
        var instance = SuperRegenerationQuirk.instance(owner);
        if (instance != null && instance.getMastery() >= 0) owner.getPersistentData().putLong(WEAK_UNTIL, now(owner) + 100);
    }

    public static boolean canRevive(LivingEntity owner, DamageSource source) {
        var energy = TensuraStorages.getExistenceFrom(owner);
        return !owner.level().isClientSide && SuperRegenerationQuirk.enabled(SuperRegenerationQuirk.instance(owner), 1)
                && now(owner) >= owner.getPersistentData().getLong(REVIVE_READY)
                && !source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(TensuraDamageTypes.SOUL_SCATTER) && !source.is(TensuraDamageTypes.SOUL_CONSUMED)
                && (owner.getAttributeValue(TensuraAttributes.MAX_SPIRITUAL_HEALTH) <= 0 || energy.getSpiritualHealth() > 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.isCanceled() || !canRevive(event.getEntity(), event.getSource())) return;
        var owner = event.getEntity();
        if (owner.getRandom().nextInt(25) != 0) return;
        owner.getPersistentData().putLong(REVIVE_READY, now(owner) + 6000);
        event.setCanceled(true);
        owner.setHealth(Math.max(1, owner.getMaxHealth() * .25f));
        owner.deathTime = 0;
        owner.invulnerableTime = 20;
        owner.clearFire();
        // revive the body without restoring shp
        var level = (ServerLevel) owner.level();
        level.sendParticles(ParticleTypes.COMPOSTER, owner.getX(), owner.getY() + 1, owner.getZ(), 24, .4, .7, .4, .03);
        level.playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, .7f, 1.2f);
    }

    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var previous = event.getOriginal().getPersistentData();
        for (String key : new String[]{WEAK_UNTIL, REVIVE_READY})
            if (previous.contains(key)) event.getEntity().getPersistentData().putLong(key, previous.getLong(key));
    }
}

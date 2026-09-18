package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHAEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class QuirkSickness {
    private static final int DURATION_TICKS = 20 * 60 * 20;
    private static final String LOSS_ON_RESPAWN = "tracadamia_quirk_sickness_loss";

    private QuirkSickness() {
    }

    public static void apply(LivingEntity recipient) {
        MobEffectInstance current = recipient.getEffect(MHAEffects.QUIRK_SICKNESS);
        int nextAmplifier = current == null ? 0 : current.getAmplifier() + 1;
        if (nextAmplifier <= 2) {
            recipient.addEffect(new MobEffectInstance(MHAEffects.QUIRK_SICKNESS, DURATION_TICKS, nextAmplifier));
            return;
        }
        if (recipient instanceof ServerPlayer player) {
            player.getPersistentData().putBoolean(LOSS_ON_RESPAWN, true);
        }
        recipient.level().explode(null, recipient.getX(), recipient.getY(), recipient.getZ(),
                4.0F, Level.ExplosionInteraction.MOB);
        if (recipient.isAlive()) {
            recipient.hurt(recipient.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal().getPersistentData().getBoolean(LOSS_ON_RESPAWN)) {
            event.getEntity().getPersistentData().putBoolean(LOSS_ON_RESPAWN, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.isAlive()
                || !player.getPersistentData().getBoolean(LOSS_ON_RESPAWN)) return;
        player.getPersistentData().remove(LOSS_ON_RESPAWN);
        List<ResourceLocation> options = AllForOneStock.orderedIds(player);
        if (options.isEmpty()) return;
        ResourceLocation removed = options.get(player.getRandom().nextInt(options.size()));
        if (AllForOneStock.discardOne(player, removed)) {
            player.sendSystemMessage(Component.literal("Quirk Sickness took ")
                    .append(AllForOneStock.displayName(removed)));
        }
    }
}

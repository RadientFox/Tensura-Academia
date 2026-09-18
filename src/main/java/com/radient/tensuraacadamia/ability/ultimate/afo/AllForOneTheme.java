package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHASounds;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class AllForOneTheme {
    private static final double RANGE_SQUARED = 32.0D * 32.0D;
    private static final int LOOP_TICKS = 467; // Source duration: 23.35 seconds.
    private static final Map<UUID, Map<UUID, Long>> LISTENERS = new HashMap<>();

    private AllForOneTheme() {
    }

    public static void tick(ServerPlayer source) {
        Map<UUID, Long> playing = LISTENERS.computeIfAbsent(source.getUUID(), ignored -> new HashMap<>());
        Set<UUID> inRange = new HashSet<>();
        long now = source.serverLevel().getGameTime();
        for (ServerPlayer listener : source.serverLevel().players()) {
            if (listener.distanceToSqr(source) > RANGE_SQUARED) continue;
            inRange.add(listener.getUUID());
            Long replayAt = playing.get(listener.getUUID());
            if (replayAt == null || now >= replayAt) {
                listener.connection.send(new ClientboundSoundPacket(MHASounds.AFO_THEME, SoundSource.RECORDS,
                        source.getX(), source.getY(), source.getZ(), 2.1F, 1.0F, source.getRandom().nextLong()));
                playing.put(listener.getUUID(), now + LOOP_TICKS);
            }
        }
        Iterator<UUID> iterator = playing.keySet().iterator();
        while (iterator.hasNext()) {
            UUID listenerId = iterator.next();
            if (inRange.contains(listenerId)) continue;
            ServerPlayer listener = source.serverLevel().getServer().getPlayerList().getPlayer(listenerId);
            iterator.remove();
            stopIfNoOtherSource(listener, source.getUUID());
        }
    }

    public static void stop(ServerPlayer source) {
        Map<UUID, Long> playing = LISTENERS.remove(source.getUUID());
        if (playing == null) return;
        for (UUID listenerId : playing.keySet()) {
            ServerPlayer listener = source.serverLevel().getServer().getPlayerList().getPlayer(listenerId);
            stopIfNoOtherSource(listener, source.getUUID());
        }
    }

    private static void stopIfNoOtherSource(ServerPlayer listener, UUID excludedSource) {
        if (listener == null) return;
        boolean hearingAnother = LISTENERS.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(excludedSource)
                        && entry.getValue().containsKey(listener.getUUID()));
        if (!hearingAnother) {
            listener.connection.send(new ClientboundStopSoundPacket(MHASounds.AFO_THEME_ID, SoundSource.RECORDS));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !LISTENERS.containsKey(player.getUUID())) return;
        var instance = SkillAPI.getSkillsFrom(player).getSkill(QuirkSkills.ALL_FOR_ONE.get().getRegistryName());
        if (instance.isEmpty() || !instance.get().isToggled()) stop(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
}

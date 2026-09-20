package com.radient.tensuraacadamia.ability.ultimate.afo;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.regestry.MHASounds;
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
public final class ButterflyEffectTheme {
    private static final double RANGE_SQUARED = 32.0D * 32.0D;
    // butterfly_effect.ogg is 9,193,920 samples at 44,100 Hz: 208.479 seconds.
    // Play it once for its complete runtime. Replaying it was starting extra client
    // sound instances at 25 and 50 seconds, which made the track overlap with itself.
    public static final int DURATION_TICKS = 4_170;
    private static final Map<UUID, Map<UUID, Long>> LISTENERS = new HashMap<>();
    private static final Map<UUID, Long> EXPIRATION_TIMES = new HashMap<>();

    private ButterflyEffectTheme() {
    }

    public static void play(ServerPlayer source) {
        play(source, DURATION_TICKS);
    }

    public static void play(ServerPlayer source, int durationTicks) {
        // A fresh Final Boss activation replaces this source's old playback instead
        // of layering another copy of the same streamed sound on top of it.
        if (LISTENERS.containsKey(source.getUUID())) stop(source);
        Map<UUID, Long> playing = LISTENERS.computeIfAbsent(source.getUUID(), ignored -> new HashMap<>());
        Set<UUID> inRange = new HashSet<>();
        long now = source.serverLevel().getGameTime();
        EXPIRATION_TIMES.put(source.getUUID(), now + durationTicks);
        
        for (ServerPlayer listener : source.serverLevel().players()) {
            if (listener.distanceToSqr(source) > RANGE_SQUARED) continue;
            inRange.add(listener.getUUID());
            if (!playing.containsKey(listener.getUUID())) {
                listener.connection.send(new ClientboundSoundPacket(MHASounds.BUTTERFLY_EFFECT, SoundSource.RECORDS,
                        source.getX(), source.getY(), source.getZ(), 2.1F, 1.0F, source.getRandom().nextLong()));
                playing.put(listener.getUUID(), now);
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
        EXPIRATION_TIMES.remove(source.getUUID());
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
            listener.connection.send(new ClientboundStopSoundPacket(MHASounds.BUTTERFLY_EFFECT_ID, SoundSource.RECORDS));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        
        long now = player.serverLevel().getGameTime();
        
        // Check if this player's Butterfly Effect theme should expire
        Long expiration = EXPIRATION_TIMES.get(player.getUUID());
        if (expiration != null && now >= expiration) {
            stop(player);
            return;
        }
        
        // Continue playing if still active
        if (LISTENERS.containsKey(player.getUUID())) {
            Map<UUID, Long> playing = LISTENERS.get(player.getUUID());
            Set<UUID> inRange = new HashSet<>();
            
            for (ServerPlayer listener : player.serverLevel().players()) {
                if (listener.distanceToSqr(player) > RANGE_SQUARED) continue;
                inRange.add(listener.getUUID());
                // Listeners who enter the radius hear one complete track from the
                // beginning; existing listeners are never restarted mid-song.
                if (!playing.containsKey(listener.getUUID())) {
                    listener.connection.send(new ClientboundSoundPacket(MHASounds.BUTTERFLY_EFFECT, SoundSource.RECORDS,
                            player.getX(), player.getY(), player.getZ(), 2.1F, 1.0F, player.getRandom().nextLong()));
                    playing.put(listener.getUUID(), now);
                }
            }
            
            Iterator<UUID> iterator = playing.keySet().iterator();
            while (iterator.hasNext()) {
                UUID listenerId = iterator.next();
                if (inRange.contains(listenerId)) continue;
                ServerPlayer listener = player.serverLevel().getServer().getPlayerList().getPlayer(listenerId);
                iterator.remove();
                stopIfNoOtherSource(listener, player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player);
    }
    
    public static boolean isPlaying(UUID playerId) {
        return LISTENERS.containsKey(playerId);
    }
}

package com.radient.tensuraacadamia.ability.unique.quirks;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DoubleCloneLedger extends SavedData {
    private static final String DATA_NAME = "tracadamia_double_clones";
    private static final Factory<DoubleCloneLedger> FACTORY = new Factory<>(DoubleCloneLedger::new,
            DoubleCloneLedger::load);

    private final Map<UUID, CloneRecord> clones = new LinkedHashMap<>();
    private final Map<UUID, Long> creatorDeaths = new LinkedHashMap<>();

    public record CloneRecord(UUID cloneId, UUID creatorId, boolean countsTowardLimit) {
    }

    private DoubleCloneLedger() {
    }

    public static DoubleCloneLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public int countNonSelf(UUID creatorId) {
        int count = 0;
        for (CloneRecord record : clones.values()) {
            if (record.countsTowardLimit() && record.creatorId().equals(creatorId)) count++;
        }
        return count;
    }

    public void register(UUID cloneId, UUID creatorId, boolean countsTowardLimit) {
        if (clones.putIfAbsent(cloneId, new CloneRecord(cloneId, creatorId, countsTowardLimit)) == null) {
            setDirty();
        }
    }

    public void remove(UUID cloneId) {
        if (clones.remove(cloneId) != null) setDirty();
    }

    public long creatorDeathGeneration(UUID creatorId) {
        return creatorDeaths.getOrDefault(creatorId, 0L);
    }

    public void recordCreatorDeath(UUID creatorId) {
        creatorDeaths.put(creatorId, creatorDeathGeneration(creatorId) + 1L);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (CloneRecord record : clones.values()) {
            CompoundTag clone = new CompoundTag();
            clone.putUUID("Clone", record.cloneId());
            clone.putUUID("Creator", record.creatorId());
            clone.putBoolean("Counted", record.countsTowardLimit());
            list.add(clone);
        }
        tag.put("Clones", list);
        ListTag deaths = new ListTag();
        creatorDeaths.forEach((creatorId, generation) -> {
            CompoundTag death = new CompoundTag();
            death.putUUID("Creator", creatorId);
            death.putLong("Generation", generation);
            deaths.add(death);
        });
        tag.put("CreatorDeaths", deaths);
        return tag;
    }

    private static DoubleCloneLedger load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        DoubleCloneLedger ledger = new DoubleCloneLedger();
        ListTag list = tag.getList("Clones", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag clone = list.getCompound(i);
            if (clone.hasUUID("Clone") && clone.hasUUID("Creator")) {
                UUID id = clone.getUUID("Clone");
                ledger.clones.put(id, new CloneRecord(id, clone.getUUID("Creator"), clone.getBoolean("Counted")));
            }
        }
        ListTag deaths = tag.getList("CreatorDeaths", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < deaths.size(); i++) {
            CompoundTag death = deaths.getCompound(i);
            if (death.hasUUID("Creator")) {
                ledger.creatorDeaths.put(death.getUUID("Creator"), death.getLong("Generation"));
            }
        }
        return ledger;
    }
}

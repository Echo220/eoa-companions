package com.echoesofaegis.companions.data;

import com.echoesofaegis.companions.EchoesCompanionsMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public final class CompanionDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path path;
    private CompanionStorage storage = new CompanionStorage();

    public CompanionDataStore(Path path) {
        this.path = path;
    }

    public void load() {
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                CompanionStorage loaded = GSON.fromJson(reader, CompanionStorage.class);
                storage = loaded == null ? new CompanionStorage() : loaded;
            }
            normalize();
        } catch (Exception e) {
            EchoesCompanionsMod.LOGGER.warn("Failed to load Echoes Companions data. Using empty storage.", e);
            storage = new CompanionStorage();
        }
    }

    public CompanionRecord record(UUID entityUuid) {
        String key = entityUuid.toString();
        CompanionRecord record = storage.companions.computeIfAbsent(key, ignored -> {
            CompanionRecord created = new CompanionRecord();
            created.entityUuid = key;
            return created;
        });
        record.entityUuid = key;
        return record.normalized();
    }

    public CompanionRecord get(UUID entityUuid) {
        CompanionRecord record = storage.companions.get(entityUuid.toString());
        return record == null ? null : record.normalized();
    }

    public void remove(UUID entityUuid) {
        storage.companions.remove(entityUuid.toString());
    }

    public Collection<CompanionRecord> all() {
        normalize();
        return storage.companions.values();
    }

    public CompanionPlayerStats playerStats(UUID playerUuid) {
        String key = playerUuid.toString();
        CompanionPlayerStats stats = storage.playerStats.computeIfAbsent(key, ignored -> new CompanionPlayerStats());
        return stats.normalized();
    }

    public void recordDuelWin(UUID playerUuid) {
        CompanionPlayerStats stats = playerStats(playerUuid);
        stats.duelWins++;
    }

    public void recordDuelLoss(UUID playerUuid) {
        CompanionPlayerStats stats = playerStats(playerUuid);
        stats.duelLosses++;
    }

    public int duelWins(UUID playerUuid) {
        return playerStats(playerUuid).duelWins;
    }

    public int duelLosses(UUID playerUuid) {
        return playerStats(playerUuid).duelLosses;
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(storage, writer);
            }
        } catch (Exception e) {
            EchoesCompanionsMod.LOGGER.warn("Failed to save Echoes Companions data.", e);
        }
    }

    private void normalize() {
        if (storage.companions == null) {
            storage.companions = new java.util.LinkedHashMap<>();
        }
        if (storage.playerStats == null) {
            storage.playerStats = new java.util.LinkedHashMap<>();
        }
        storage.companions.values().forEach(CompanionRecord::normalized);
        for (Map.Entry<String, CompanionPlayerStats> entry : storage.playerStats.entrySet()) {
            if (entry.getValue() == null) {
                entry.setValue(new CompanionPlayerStats());
            }
            entry.getValue().normalized();
        }
    }
}
